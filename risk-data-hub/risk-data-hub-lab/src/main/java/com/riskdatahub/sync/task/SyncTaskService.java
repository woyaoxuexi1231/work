package com.riskdatahub.sync.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.riskdatahub.common.constant.HubConstants;
import com.riskdatahub.common.util.TimeUtils;
import com.riskdatahub.datasource.DataSourceManager;
import com.riskdatahub.datasource.RoutingMybatisExecutor;
import com.riskdatahub.datasource.dto.DataSourceConfigDTO;
import com.riskdatahub.id.LeafSegmentService;
import com.riskdatahub.sync.entity.CleanTrade;
import com.riskdatahub.sync.entity.SyncBatchMetrics;
import com.riskdatahub.sync.mapper.CleanTradeMapper;
import com.riskdatahub.sync.mapper.SyncBatchMetricsMapper;
import com.riskdatahub.sync.task.entity.SyncBusinessRecord;
import com.riskdatahub.sync.task.entity.SyncTask;
import com.riskdatahub.sync.task.mapper.SyncBusinessRecordMapper;
import com.riskdatahub.sync.task.mapper.SyncTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

/**
 * 同步任务服务 — 只操作任务记录（CRUD），不涉及同步执行。
 * <p>
 * 提交任务时创建 QUEUED 状态记录，由 {@link com.riskdatahub.sync.SyncTaskScheduler} 定时扫描并调度执行。
 * </p>
 *
 * @author risk-data-hub
 */
@Slf4j
@Service
public class SyncTaskService {

    private static final String TAG_SYNC_TASK = "sync_task";

    private final LeafSegmentService leafSegmentService;
    private final DataSourceManager dataSourceManager;
    private final RoutingMybatisExecutor routingMybatisExecutor;
    private final SyncTaskMapper syncTaskMapper;
    private final SyncBusinessRecordMapper syncBusinessRecordMapper;
    private final CleanTradeMapper cleanTradeMapper;

    @org.springframework.beans.factory.annotation.Autowired
    private SyncBatchMetricsMapper batchMetricsMapper;

    public SyncTaskService(LeafSegmentService leafSegmentService,
                           DataSourceManager dataSourceManager,
                           RoutingMybatisExecutor routingMybatisExecutor,
                           SyncTaskMapper syncTaskMapper,
                           SyncBusinessRecordMapper syncBusinessRecordMapper,
                           CleanTradeMapper cleanTradeMapper) {
        this.leafSegmentService = leafSegmentService;
        this.dataSourceManager = dataSourceManager;
        this.routingMybatisExecutor = routingMybatisExecutor;
        this.syncTaskMapper = syncTaskMapper;
        this.syncBusinessRecordMapper = syncBusinessRecordMapper;
        this.cleanTradeMapper = cleanTradeMapper;
    }

    // ==================== Task 提交 ====================

    /**
     * 提交异步同步任务。每天只保留一条任务记录。
     */
    public SyncTask startTask(String dataSourceKey, int pageSize) {
        DataSourceConfigDTO config = validateDataSource(dataSourceKey);
        return createOrResetTask(dataSourceKey, config, pageSize, "同步任务已提交，等待执行");
    }

    /**
     * 强制刷新 — 清除全部清洗数据后重新全量同步。
     */
    public SyncTask forceRefresh(String dataSourceKey, int pageSize) {
        DataSourceConfigDTO config = validateDataSource(dataSourceKey);
        log.warn("[SyncTask] 强制刷新已提交 dataSourceKey={}", dataSourceKey);
        return createOrResetTask(dataSourceKey, config, pageSize, "强制刷新-清除数据中");
    }

    /** 创建或重置当天的同步任务。 */
    private SyncTask createOrResetTask(String dataSourceKey, DataSourceConfigDTO config, int pageSize, String message) {
        int safePageSize = Math.max(1, Math.min(pageSize, 100000));

        SyncTask existing = routingMybatisExecutor.query(HubConstants.DS_HUB, () ->
                syncTaskMapper.selectOne(new LambdaQueryWrapper<SyncTask>()
                        .apply("DATE(submitted_at) = CURDATE()")
                        .orderByDesc(SyncTask::getId)
                        .last("limit 1")));

        if (existing != null) {
            if ("RUNNING".equals(existing.getStatus())) {
                throw new IllegalStateException("当天同步任务正在运行中，请等待完成后再试");
            }
            Long taskId = existing.getId();
            routingMybatisExecutor.run(HubConstants.DS_HUB, () -> {
                existing.setStatus("QUEUED");
                existing.setProgress(0);
                existing.setDataSourceKey(dataSourceKey);
                existing.setDataSourceName(config.getName());
                existing.setDatasourceType(config.getDatasourceType());
                existing.setPageSize(safePageSize);
                existing.setMessage(message);
                existing.setStartedAt(null);
                existing.setFinishedAt(null);
                existing.setTotalPulledCount(0);
                existing.setTotalSavedCount(0);
                existing.setErrorMessage(null);
                existing.setRunning(true);
                syncTaskMapper.updateById(existing);
            });
            routingMybatisExecutor.run(HubConstants.DS_HUB, () ->
                    syncBusinessRecordMapper.update(null, new LambdaUpdateWrapper<SyncBusinessRecord>()
                            .eq(SyncBusinessRecord::getTaskId, taskId)
                            .set(SyncBusinessRecord::getErrorMessage, (String) null)));
            log.info("[SyncTask] resetTask id={}, dataSourceKey={}", taskId, dataSourceKey);
            return existing;
        }
        return createQueuedTask(dataSourceKey, config, safePageSize, message);
    }

    /** 校验数据源存在且不是中台库。 */
    private DataSourceConfigDTO validateDataSource(String dataSourceKey) {
        DataSourceConfigDTO config = dataSourceManager.getConfig(dataSourceKey);
        if (config == null) {
            throw new IllegalArgumentException("数据源不存在: " + dataSourceKey);
        }
        if (HubConstants.TYPE_HUB.equalsIgnoreCase(config.getDatasourceType())) {
            throw new IllegalArgumentException("中台库不能作为同步来源: " + dataSourceKey);
        }
        return config;
    }

    /** 创建 QUEUED 状态的任务记录。 */
    private SyncTask createQueuedTask(String dataSourceKey, DataSourceConfigDTO config, int pageSize, String message) {
        long taskId = leafSegmentService.nextId(TAG_SYNC_TASK);
        LocalDateTime now = TimeUtils.now();
        int safePageSize = Math.max(1, Math.min(pageSize, 100000));

        SyncTask task = new SyncTask();
        task.setId(taskId);
        task.setStatus("QUEUED");
        task.setProgress(0);
        task.setDataSourceKey(dataSourceKey);
        task.setDataSourceName(config.getName());
        task.setDatasourceType(config.getDatasourceType());
        task.setPageSize(safePageSize);
        task.setSubmittedAt(now);
        task.setMessage(message);
        task.setRunning(true);
        routingMybatisExecutor.run(HubConstants.DS_HUB, () -> syncTaskMapper.insert(task));

        log.info("[SyncTask] createQueuedTask id={}, dataSourceKey={}, pageSize={}", task.getId(), dataSourceKey, safePageSize);
        return task;
    }

    /** 更新同步任务的部分字段（供 SyncEngine / SyncTaskScheduler 调用）。 */
    public void updateTaskFields(Long taskId, Consumer<SyncTask> updater) {
        routingMybatisExecutor.run(HubConstants.DS_HUB, () -> {
            SyncTask task = syncTaskMapper.selectById(taskId);
            if (task == null) {
                log.warn("[SyncTask] 更新失败：任务 {} 不存在", taskId);
                return;
            }
            updater.accept(task);
            syncTaskMapper.updateById(task);
        });
    }

    // ==================== 查询接口 ====================

    /** 查询当前同步任务（最近一条）。无任务时返回 IDLE 空任务。 */
    public SyncTask currentTask() {
        SyncTask task = routingMybatisExecutor.query(HubConstants.DS_HUB, () ->
                syncTaskMapper.selectOne(new LambdaQueryWrapper<SyncTask>()
                        .orderByDesc(SyncTask::getId)
                        .last("limit 1")));
        if (task == null) {
            SyncTask idle = new SyncTask();
            idle.setStatus("IDLE");
            idle.setProgress(0);
            idle.setMessage("暂无同步任务");
            idle.setRunning(false);
            return idle;
        }
        task.setRunning("QUEUED".equals(task.getStatus()) || "RUNNING".equals(task.getStatus()));
        return task;
    }

    /** 查询最近 30 条清洗交易记录。 */
    public List<CleanTrade> cleanedTrades() {
        return routingMybatisExecutor.query(HubConstants.DS_HUB,
                () -> cleanTradeMapper.selectList(new LambdaQueryWrapper<CleanTrade>()
                        .orderByDesc(CleanTrade::getGlobalId)
                        .last("limit 30")));
    }

    /** 查询指定业务记录的批次耗时明细。 */
    public IPage<SyncBatchMetrics> getBatchMetrics(Long recordId, int page, int size) {
        return routingMybatisExecutor.query(HubConstants.DS_HUB, () ->
                batchMetricsMapper.selectPage(
                        new Page<>(page, size),
                        new LambdaQueryWrapper<SyncBatchMetrics>()
                                .eq(SyncBatchMetrics::getRecordId, recordId)
                                .orderByAsc(SyncBatchMetrics::getBatchNo)));
    }

    /** 查询指定同步任务的各业务执行详情。 */
    public List<SyncBusinessRecord> getBusinessRecords(Long taskId) {
        return routingMybatisExecutor.query(HubConstants.DS_HUB, () ->
                syncBusinessRecordMapper.selectList(new LambdaQueryWrapper<SyncBusinessRecord>()
                        .eq(SyncBusinessRecord::getTaskId, taskId)
                        .orderByAsc(SyncBusinessRecord::getBusinessCode)));
    }
}
