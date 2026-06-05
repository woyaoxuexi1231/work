package com.riskdatahub.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.riskdatahub.common.constant.HubConstants;
import com.riskdatahub.common.util.TimeUtils;
import com.riskdatahub.datasource.DataSourceManager;
import com.riskdatahub.datasource.RoutingMybatisExecutor;
import com.riskdatahub.datasource.dto.DataSourceConfigDTO;
import com.riskdatahub.id.LeafSegmentService;
import com.riskdatahub.message.RabbitMqSender;
import com.riskdatahub.sync.SyncOrchestrator;
import com.riskdatahub.sync.model.SyncResultDTO;
import com.riskdatahub.sync.model.SyncSupport.BusinessSyncResult;
import com.riskdatahub.task.entity.SyncBusinessRecord;
import com.riskdatahub.task.entity.SyncTask;
import com.riskdatahub.task.mapper.SyncBusinessRecordMapper;
import com.riskdatahub.task.mapper.SyncTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 同步任务服务 — 管理同步任务的提交、执行和状态查询。
 * <p>
 * 提交任务时仅创建 QUEUED 状态记录，由 {@link #scanAndExecute()} 定时扫描并调度执行。
 * 保证同一时间只有一个同步任务在运行，当天已有成功任务则拒绝新提交。
 * </p>
 *
 * @author risk-data-hub
 */
@Slf4j
@Service
public class SyncTaskService {

    private static final String TAG_SYNC_TASK = "sync_task";              // 同步任务 Leaf 号段标签
    private static final String TAG_SYNC_BUSINESS_RECORD = "sync_business_record"; // 业务记录 Leaf 号段标签

    private final LeafSegmentService leafSegmentService;
    private final SyncOrchestrator syncOrchestrator;
    private final DataSourceManager dataSourceManager;
    private final RoutingMybatisExecutor routingMybatisExecutor;
    private final SyncTaskMapper syncTaskMapper;
    private final SyncBusinessRecordMapper syncBusinessRecordMapper;
    private final RabbitMqSender rabbitMqSender;
    private final ThreadPoolExecutor syncTaskExecutor;

    public SyncTaskService(LeafSegmentService leafSegmentService,
                           SyncOrchestrator syncOrchestrator,
                           DataSourceManager dataSourceManager,
                           RoutingMybatisExecutor routingMybatisExecutor,
                           SyncTaskMapper syncTaskMapper,
                           SyncBusinessRecordMapper syncBusinessRecordMapper,
                           RabbitMqSender rabbitMqSender,
                           @Qualifier("syncTaskExecutor") ThreadPoolExecutor syncTaskExecutor) {
        this.leafSegmentService = leafSegmentService;
        this.syncOrchestrator = syncOrchestrator;
        this.dataSourceManager = dataSourceManager;
        this.routingMybatisExecutor = routingMybatisExecutor;
        this.syncTaskMapper = syncTaskMapper;
        this.syncBusinessRecordMapper = syncBusinessRecordMapper;
        this.rabbitMqSender = rabbitMqSender;
        this.syncTaskExecutor = syncTaskExecutor;
    }

    /**
     * 提交异步同步任务。
     * <p>流程：校验数据源 → 检查当天是否已有成功任务（有则拒绝） → 持久化 QUEUED 状态。
     * 任务不会立即执行，而是由 {@link #scanAndExecute()} 定时扫描后调度。</p>
     *
     * @param dataSourceKey 数据源标识
     * @param pageSize      每页大小
     * @return 刚创建的同步任务对象
     * @throws IllegalArgumentException 数据源不存在或为中台库
     * @throws IllegalStateException    当天已有成功同步任务
     */
    public SyncTask startTask(String dataSourceKey, int pageSize) {
        // 1. 校验数据源存在且不是中台库（自己不能同步自己）
        DataSourceConfigDTO config = dataSourceManager.getConfig(dataSourceKey);
        if (config == null) {
            throw new IllegalArgumentException("数据源不存在: " + dataSourceKey);
        }
        if (HubConstants.TYPE_HUB.equalsIgnoreCase(config.getDatasourceType())) {
            throw new IllegalArgumentException("中台库不能作为同步来源: " + dataSourceKey);
        }

        // 2. 检查当天是否已有成功的同步任务
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        Long successCount = routingMybatisExecutor.query(HubConstants.DS_HUB, () ->
                syncTaskMapper.selectCount(new LambdaQueryWrapper<SyncTask>()
                        .eq(SyncTask::getStatus, "SUCCESS")
                        .apply("DATE_FORMAT(submitted_at, '%Y-%m-%d') = {0}", today)));
        if (successCount > 0) {
            throw new IllegalStateException("今天已存在成功的同步任务，请勿重复提交");
        }

        // 3. 创建 QUEUED 任务记录，等待定时调度执行
        long taskId = leafSegmentService.nextId(TAG_SYNC_TASK);
        String now = TimeUtils.now();
        int safePageSize = Math.max(1, Math.min(pageSize, 500));

        SyncTask task = new SyncTask();
        task.setId(taskId);
        task.setStatus("QUEUED");
        task.setProgress(0);
        task.setDataSourceKey(dataSourceKey);
        task.setDataSourceName(config.getName());
        task.setDatasourceType(config.getDatasourceType());
        task.setPageSize(safePageSize);
        task.setSubmittedAt(now);
        task.setMessage("同步任务已提交，等待执行");
        task.setRunning(true);
        routingMybatisExecutor.run(HubConstants.DS_HUB, () -> syncTaskMapper.insert(task));

        log.info("[SyncTask] submit id={}, dataSourceKey={}, pageSize={}", task.getId(), dataSourceKey, safePageSize);
        return task;
    }

    /**
     * 查询当前同步任务（最近一条）。
     * <p>返回最近一条任务记录，无任务时返回 IDLE 状态的空任务。</p>
     *
     * @return 当前任务，无任务时返回 status=IDLE 的空任务（非 null）
     */
    public SyncTask currentTask() {
        // 查询最近一条任务记录（按 ID 降序取第一条）
        SyncTask task = routingMybatisExecutor.query(HubConstants.DS_HUB, () ->
                syncTaskMapper.selectOne(new LambdaQueryWrapper<SyncTask>()
                        .orderByDesc(SyncTask::getId)
                        .last("limit 1")));
        // 没有任何任务记录时返回 IDLE 空任务，避免前端判空
        if (task == null) {
            SyncTask idle = new SyncTask();
            idle.setStatus("IDLE");
            idle.setProgress(0);
            idle.setMessage("暂无同步任务");
            idle.setRunning(false);
            return idle;
        }
        // 动态计算 running 状态：QUEUED 和 RUNNING 都算"运行中"
        task.setRunning("QUEUED".equals(task.getStatus()) || "RUNNING".equals(task.getStatus()));
        return task;
    }

    /**
     * 定时扫描并调度 QUEUED 任务（每 3 秒执行一次）。
     * <p>流程：检查是否有 RUNNING 任务 → 若无则取出最旧的 QUEUED 任务 → 更新为 RUNNING → 提交线程池执行。</p>
     */
    @Scheduled(fixedDelay = 3000)
    public void scanAndExecute() {
        routingMybatisExecutor.run(HubConstants.DS_HUB, () -> {
            // 1. 检查是否已有正在运行的任务
            Long runningCount = syncTaskMapper.selectCount(
                    new LambdaQueryWrapper<SyncTask>().eq(SyncTask::getStatus, "RUNNING"));
            if (runningCount > 0) {
                return; // 已有任务在运行，跳过本次扫描
            }

            // 2. 取出最旧的 QUEUED 任务（按 ID 升序取第一条）
            SyncTask queued = syncTaskMapper.selectOne(new LambdaQueryWrapper<SyncTask>()
                    .eq(SyncTask::getStatus, "QUEUED")
                    .orderByAsc(SyncTask::getId)
                    .last("limit 1"));
            if (queued == null) {
                return; // 无待执行任务
            }

            // 3. 更新为 RUNNING
            Long taskId = queued.getId();
            String dsKey = queued.getDataSourceKey();
            int ps = queued.getPageSize();
            updateTaskFields(taskId, task -> {
                task.setStatus("RUNNING");
                task.setStartedAt(TimeUtils.now());
            });

            // 4. 提交到线程池异步执行
            syncTaskExecutor.submit(() -> runTask(taskId, dsKey, ps));
            log.info("[SyncTask] scanAndExecute 调度任务 id={}, dataSourceKey={}", taskId, dsKey);
        });
    }

    /**
     * 异步执行同步任务（在线程池中运行）。
     * <p>流程：编排同步（含实时进度回调）→ 记录每类业务结果 → SUCCESS/FAILED。
     * 由 {@link #scanAndExecute()} 调度，无需分布式锁。</p>
     *
     * @param id             任务 ID
     * @param dataSourceKey  数据源标识
     * @param pageSize       分页大小
     */
    private void runTask(Long id, String dataSourceKey, int pageSize) {
        try {
            // 执行同步编排（含实时进度回调）
            // bizProgress：线程安全地聚合每个业务类型的实时拉取/落库计数
            // key=businessCode, value=[pulledCount, savedCount]
            final ConcurrentHashMap<String, int[]> bizProgress = new ConcurrentHashMap<>();
            // lastDbWrite：节流控制，最多每秒写一次 DB
            final long[] lastDbWrite = {0};

            SyncResultDTO result = syncOrchestrator.syncByDataSource(dataSourceKey, pageSize, progress -> {
                // 每个业务类型完成一页拉取或落库时，此回调被调用
                bizProgress.put(progress.getBusinessCode(),
                        new int[]{progress.getPulledCount(), progress.getSavedCount()});

                // 节流：每秒最多写一次，频繁写 DB 无意义且浪费连接池
                long now = System.currentTimeMillis();
                if (now - lastDbWrite[0] < 1000) return;
                lastDbWrite[0] = now;

                // 聚合所有业务类型的总计数
                int aggPulled = 0, aggSaved = 0;
                for (int[] v : bizProgress.values()) {
                    aggPulled += v[0];
                    aggSaved += v[1];
                }
                // 复制为 effectively final 变量供 lambda 捕获
                int capturedPulled = aggPulled;
                int capturedSaved = aggSaved;

                // 轻量更新同步任务进度（只写 message 和计数，不用 select-before-update）
                routingMybatisExecutor.run(HubConstants.DS_HUB, () ->
                        syncTaskMapper.update(null, new LambdaUpdateWrapper<SyncTask>()
                                .eq(SyncTask::getId, id)
                                .set(SyncTask::getMessage,
                                        "正在同步 " + progress.getBusinessCode()
                                                + ": 已拉取 " + progress.getPulledCount()
                                                + ", 已落库 " + progress.getSavedCount())
                                .set(SyncTask::getTotalPulledCount, capturedPulled)
                                .set(SyncTask::getTotalSavedCount, capturedSaved)));
            });

            // 记录每个业务类型的同步结果明细
            int totalPulled = 0;
            int totalSaved = 0;

            for (Map.Entry<String, BusinessSyncResult> entry : result.getBusinessResults().entrySet()) {
                String bizCode = entry.getKey();
                BusinessSyncResult bizResult = entry.getValue();

                SyncBusinessRecord record = new SyncBusinessRecord();
                record.setId(leafSegmentService.nextId(TAG_SYNC_BUSINESS_RECORD));
                record.setTaskId(id);
                record.setBusinessCode(bizCode);
                record.setStatus("SUCCESS");
                record.setPageCount(bizResult.getPageCount());
                record.setPulledCount(bizResult.getPulledCount());
                record.setSavedCount(bizResult.getSavedCount());
                record.setLastRowId(bizResult.getLastRowId());
                record.setStartedAt(TimeUtils.now());
                record.setFinishedAt(TimeUtils.now());
                routingMybatisExecutor.run(HubConstants.DS_HUB,
                        () -> syncBusinessRecordMapper.insert(record));

                totalPulled += bizResult.getPulledCount();
                totalSaved += bizResult.getSavedCount();
            }

            // 更新任务为 SUCCESS 状态
            int finalPulled = totalPulled;
            int finalSaved = totalSaved;
            updateTaskFields(id, task -> {
                task.setStatus("SUCCESS");
                task.setMessage("同步任务完成");
                task.setFinishedAt(TimeUtils.now());
                task.setTotalPulledCount(finalPulled);
                task.setTotalSavedCount(finalSaved);
                task.setProgress(100);
            });

            log.info("[SyncTask] done id={}, pulled={}, saved={}", id, totalPulled, totalSaved);
            // 发送同步完成消息到 RabbitMQ
            rabbitMqSender.sendSyncCompleted(id, dataSourceKey, result.getDatasourceType(),
                    totalPulled, totalSaved);
        } catch (Exception e) {
            // 异常处理：更新为 FAILED 状态
            updateTaskFields(id, task -> {
                task.setStatus("FAILED");
                task.setMessage("同步任务失败");
                task.setErrorMessage(e.getMessage());
                task.setFinishedAt(TimeUtils.now());
            });
            log.error("[SyncTask] failed id={}", id, e);
        }
    }

    /**
     * 更新同步任务的部分字段。
     * <p>先 selectById 查询 → 回调修改 → updateById 写回。
     * 只修改回调中 set 的字段，其余字段保持不变。</p>
     *
     * @param taskId  任务 ID
     * @param updater 字段修改回调，接收当前任务实体，修改需要的字段
     */
    private void updateTaskFields(Long taskId, java.util.function.Consumer<SyncTask> updater) {
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
}
