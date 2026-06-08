package com.riskdatahub.sync.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
 * 每次提交创建新任务，同一时间只允许一个 RUNNING 任务。
 * 由 {@link com.riskdatahub.sync.SyncTaskScheduler} 定时扫描并调度执行。
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

    /** 提交增量同步任务（断点续传）。同一时间只允许一个 RUNNING 任务。 */
    public SyncTask startTask(String dataSourceKey, int pageSize) {
        return createTask(dataSourceKey, pageSize, "INCREMENTAL", "增量同步已提交");
    }

    /** 提交全量同步任务（从头开始）。同一时间只允许一个 RUNNING 任务。 */
    public SyncTask fullSync(String dataSourceKey, int pageSize) {
        return createTask(dataSourceKey, pageSize, "FULL", "全量同步已提交");
    }

    /** 创建新任务，检查无 RUNNING 任务。 */
    private SyncTask createTask(String dataSourceKey, int pageSize, String syncType, String message) {
        DataSourceConfigDTO config = validateDataSource(dataSourceKey);
        int safePageSize = Math.max(1, Math.min(pageSize, 100000));

        // 检查是否有正在运行的任务
        boolean hasRunning = routingMybatisExecutor.query(HubConstants.DS_HUB, () ->
                syncTaskMapper.selectCount(new LambdaQueryWrapper<SyncTask>()
                        .eq(SyncTask::getStatus, "RUNNING")) > 0);
        if (hasRunning) {
            throw new IllegalStateException("已有同步任务正在运行中，请等待完成后再试");
        }

        // 创建新任务
        long taskId = leafSegmentService.nextId(TAG_SYNC_TASK);
        LocalDateTime now = TimeUtils.now();

        SyncTask task = new SyncTask();
        task.setId(taskId);
        task.setSyncType(syncType);
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

        log.info("[SyncTask] createTask id={}, type={}, dataSourceKey={}, pageSize={}",
                task.getId(), syncType, dataSourceKey, safePageSize);
        return task;
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

    /**
     * 查询增量同步的断点续传播入游标。
     * <p>查找上一次 SUCCESS/FAILED 任务中各业务的 lastRowId，用于继续同步。</p>
     *
     * @return businessCode → lastRowId，没有上一轮数据时返回空 Map
     */
    public java.util.Map<String, Long> getResumeCursors(String dataSourceKey) {
        java.util.Map<String, Long> cursors = new java.util.HashMap<>();
        routingMybatisExecutor.run(HubConstants.DS_HUB, () -> {
            SyncTask last = syncTaskMapper.selectOne(new LambdaQueryWrapper<SyncTask>()
                    .eq(SyncTask::getDataSourceKey, dataSourceKey)
                    .in(SyncTask::getStatus, "SUCCESS", "FAILED")
                    .orderByDesc(SyncTask::getId)
                    .last("limit 1"));
            if (last == null) return;

            syncBusinessRecordMapper.selectList(new LambdaQueryWrapper<SyncBusinessRecord>()
                            .eq(SyncBusinessRecord::getTaskId, last.getId())
                            .isNotNull(SyncBusinessRecord::getLastRowId)
                            .gt(SyncBusinessRecord::getLastRowId, 0))
                    .forEach(r -> cursors.put(r.getBusinessCode(), r.getLastRowId()));
        });
        return cursors;
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

    /** 根据 ID 查询单个同步任务。 */
    public SyncTask getTaskById(Long taskId) {
        return routingMybatisExecutor.query(HubConstants.DS_HUB, () ->
                syncTaskMapper.selectById(taskId));
    }

    /** 分页查询同步任务列表，按提交时间倒序。 */
    public IPage<SyncTask> listTasks(int page, int size) {
        return routingMybatisExecutor.query(HubConstants.DS_HUB, () ->
                syncTaskMapper.selectPage(new Page<>(page, size),
                        new LambdaQueryWrapper<SyncTask>()
                                .orderByDesc(SyncTask::getId)));
    }

    /** 查询最近一条同步任务。无任务时返回 IDLE 空任务。 */
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
