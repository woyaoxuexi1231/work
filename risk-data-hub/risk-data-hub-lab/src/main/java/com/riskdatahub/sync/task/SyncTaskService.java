package com.riskdatahub.sync.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.riskdatahub.common.constant.HubConstants;
import com.riskdatahub.common.util.TimeUtils;
import com.riskdatahub.config.SyncThreadPoolConfig;
import com.riskdatahub.datasource.DataSourceManager;
import com.riskdatahub.datasource.RoutingMybatisExecutor;
import com.riskdatahub.datasource.dto.DataSourceConfigDTO;
import com.riskdatahub.id.LeafSegmentService;
import com.riskdatahub.message.RabbitMqSender;
import com.riskdatahub.sync.SyncEngine;
import com.riskdatahub.sync.entity.CleanAsset;
import com.riskdatahub.sync.entity.CleanPosition;
import com.riskdatahub.sync.entity.CleanStock;
import com.riskdatahub.sync.entity.CleanTrade;
import com.riskdatahub.sync.entity.SyncBatchMetrics;
import com.riskdatahub.sync.mapper.CleanAssetMapper;
import com.riskdatahub.sync.mapper.CleanPositionMapper;
import com.riskdatahub.sync.mapper.CleanStockMapper;
import com.riskdatahub.sync.mapper.CleanTradeMapper;
import com.riskdatahub.sync.mapper.SyncBatchMetricsMapper;
import com.riskdatahub.sync.model.SyncResultDTO;
import com.riskdatahub.sync.model.SyncSupport.BusinessSyncResult;
import com.riskdatahub.sync.task.entity.SyncBusinessRecord;
import com.riskdatahub.sync.task.entity.SyncTask;
import com.riskdatahub.sync.task.mapper.SyncBusinessRecordMapper;
import com.riskdatahub.sync.task.mapper.SyncTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

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

    private static final String LOCK_KEY = "risk-hub:sync:task:lock";
    private static final String TAG_SYNC_TASK = "sync_task";
    private static final String TAG_SYNC_BUSINESS_RECORD = "sync_business_record";

    private final RedissonClient redissonClient;
    private final LeafSegmentService leafSegmentService;
    private final SyncEngine syncEngine;
    private final DataSourceManager dataSourceManager;
    private final RoutingMybatisExecutor routingMybatisExecutor;
    private final SyncTaskMapper syncTaskMapper;
    private final SyncBusinessRecordMapper syncBusinessRecordMapper;
    private final RabbitMqSender rabbitMqSender;
    private final ThreadPoolExecutor syncTaskExecutor;
    private final CleanStockMapper cleanStockMapper;
    private final CleanTradeMapper cleanTradeMapper;
    private final CleanPositionMapper cleanPositionMapper;
    private final CleanAssetMapper cleanAssetMapper;

    @org.springframework.beans.factory.annotation.Autowired
    private SyncBatchMetricsMapper batchMetricsMapper;

    public SyncTaskService(RedissonClient redissonClient,
                           LeafSegmentService leafSegmentService,
                           SyncEngine syncEngine,
                           DataSourceManager dataSourceManager,
                           RoutingMybatisExecutor routingMybatisExecutor,
                           SyncTaskMapper syncTaskMapper,
                           SyncBusinessRecordMapper syncBusinessRecordMapper,
                           RabbitMqSender rabbitMqSender,
                           @Qualifier("syncTaskExecutor") ThreadPoolExecutor syncTaskExecutor,
                           CleanStockMapper cleanStockMapper,
                           CleanTradeMapper cleanTradeMapper,
                           CleanPositionMapper cleanPositionMapper,
                           CleanAssetMapper cleanAssetMapper,
                           SyncThreadPoolConfig syncThreadPoolConfig) {
        this.redissonClient = redissonClient;
        this.leafSegmentService = leafSegmentService;
        this.syncEngine = syncEngine;
        this.dataSourceManager = dataSourceManager;
        this.routingMybatisExecutor = routingMybatisExecutor;
        this.syncTaskMapper = syncTaskMapper;
        this.syncBusinessRecordMapper = syncBusinessRecordMapper;
        this.rabbitMqSender = rabbitMqSender;
        this.syncTaskExecutor = syncTaskExecutor;
        this.cleanStockMapper = cleanStockMapper;
        this.cleanTradeMapper = cleanTradeMapper;
        this.cleanPositionMapper = cleanPositionMapper;
        this.cleanAssetMapper = cleanAssetMapper;
    }

    // ==================== Task 提交 ====================

    /**
     * 提交异步同步任务。
     * <p>每天只保留一条同步任务记录。如果当天已有任务（非 RUNNING 状态），重置为 QUEUED 重新执行；
     * 如果任务正在运行则拒绝提交。无当天任务时创建新记录。</p>
     */
    public SyncTask startTask(String dataSourceKey, int pageSize) {
        DataSourceConfigDTO config = validateDataSource(dataSourceKey);
        return createOrResetTask(dataSourceKey, config, pageSize, "同步任务已提交，等待执行");
    }

    /**
     * 强制刷新 — 清除 risk_hub 全部清洗数据和 Redis 缓存，然后重新全量同步。
     * <p>不清除 sync_task / sync_business_record 记录，仅重置当天任务状态为 QUEUED。</p>
     */
    public SyncTask forceRefresh(String dataSourceKey, int pageSize) {
        DataSourceConfigDTO config = validateDataSource(dataSourceKey);
        log.warn("[SyncTask] 强制刷新已提交，将在后台清除数据 dataSourceKey={}", dataSourceKey);
        return createOrResetTask(dataSourceKey, config, pageSize, "强制刷新-清除数据中");
    }

    /** 创建或重置当天的同步任务——每天只保留一条记录。 */
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
            log.info("[SyncTask] resetTask id={}, dataSourceKey={}, status={}->QUEUED", taskId, dataSourceKey, existing.getStatus());
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

    /** 创建 QUEUED 状态的同步任务记录。 */
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

    // ==================== 任务调度执行 ====================

    /**
     * 定时扫描并调度 QUEUED 任务（每 3 秒执行一次）。
     */
    @Scheduled(fixedDelay = 3000)
    public void scanAndExecute() {
        try {
            doScanAndExecute();
        } catch (Exception e) {
            log.error("[SyncTask] scanAndExecute 执行异常，下次调度继续尝试", e);
        }
    }

    private void doScanAndExecute() {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        boolean lockHeld = lock.isLocked();

        routingMybatisExecutor.run(HubConstants.DS_HUB, () -> {
            List<SyncTask> runningTasks = syncTaskMapper.selectList(
                    new LambdaQueryWrapper<SyncTask>().eq(SyncTask::getStatus, "RUNNING"));

            if (!runningTasks.isEmpty()) {
                if (lockHeld) {
                    return;
                }
                for (SyncTask task : runningTasks) {
                    updateTaskFields(task.getId(), t -> {
                        t.setStatus("FAILED");
                        t.setMessage("同步任务异常终止（Redisson 锁已释放）");
                        t.setErrorMessage("Process crashed or watchdog expired");
                        t.setFinishedAt(TimeUtils.now());
                    });
                    log.warn("[SyncTask] 检测到异常终止任务 id={}（锁已释放），已标记 FAILED", task.getId());
                }
            }

            SyncTask queued = syncTaskMapper.selectOne(new LambdaQueryWrapper<SyncTask>()
                    .eq(SyncTask::getStatus, "QUEUED")
                    .orderByAsc(SyncTask::getId)
                    .last("limit 1"));
            if (queued == null) {
                return;
            }

            Long taskId = queued.getId();
            String dsKey = queued.getDataSourceKey();
            int ps = queued.getPageSize();
            updateTaskFields(taskId, task -> {
                task.setStatus("RUNNING");
                task.setStartedAt(TimeUtils.now());
            });

            syncTaskExecutor.submit(() -> runTask(taskId, dsKey, ps));
            log.info("[SyncTask] scanAndExecute 调度任务 id={}, dataSourceKey={}", taskId, dsKey);
        });
    }

    /**
     * 异步执行同步任务（在线程池中运行）。
     */
    private void runTask(Long id, String dataSourceKey, int pageSize) {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        try {
            if (!lock.tryLock(0, 30, TimeUnit.MINUTES)) {
                log.warn("[SyncTask] 无法获取分布式锁，任务 id={} 跳过", id);
                return;
            }

            doForceCleanIfNeeded(id);

            List<String> businessCodes = syncEngine.getBusinessCodes();
            for (String bizCode : businessCodes) {
                routingMybatisExecutor.run(HubConstants.DS_HUB, () -> {
                    SyncBusinessRecord existing = syncBusinessRecordMapper.selectOne(
                            new LambdaQueryWrapper<SyncBusinessRecord>()
                                    .eq(SyncBusinessRecord::getTaskId, id)
                                    .eq(SyncBusinessRecord::getBusinessCode, bizCode)
                                    .last("limit 1"));
                    if (existing != null) {
                        existing.setStatus("RUNNING");
                        existing.setPulledCount(0);
                        existing.setSavedCount(0);
                        existing.setErrorMessage(null);
                        existing.setStartedAt(TimeUtils.now());
                        existing.setFinishedAt(null);
                        syncBusinessRecordMapper.updateById(existing);
                    } else {
                        SyncBusinessRecord record = new SyncBusinessRecord();
                        record.setId(leafSegmentService.nextId(TAG_SYNC_BUSINESS_RECORD));
                        record.setTaskId(id);
                        record.setBusinessCode(bizCode);
                        record.setStatus("RUNNING");
                        record.setPulledCount(0);
                        record.setSavedCount(0);
                        record.setStartedAt(TimeUtils.now());
                        syncBusinessRecordMapper.insert(record);
                    }
                });
            }

            Map<String, Long> initialCursors = new HashMap<>();
            for (String bizCode : businessCodes) {
                Long cursor = routingMybatisExecutor.query(HubConstants.DS_HUB, () -> {
                    List<Object> result;
                    switch (bizCode) {
                        case "STOCK":
                            result = cleanStockMapper.selectObjs(
                                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CleanStock>()
                                            .select("MAX(source_row_id)"));
                            break;
                        case "TRADE":
                            result = cleanTradeMapper.selectObjs(
                                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CleanTrade>()
                                            .select("MAX(source_row_id)"));
                            break;
                        case "POSITION":
                            result = cleanPositionMapper.selectObjs(
                                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CleanPosition>()
                                            .select("MAX(source_row_id)"));
                            break;
                        case "ASSET":
                            result = cleanAssetMapper.selectObjs(
                                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CleanAsset>()
                                            .select("MAX(source_row_id)"));
                            break;
                        default:
                            result = java.util.Collections.emptyList();
                    }
                    return result.isEmpty() || result.get(0) == null ? 0L : Long.valueOf(result.get(0).toString());
                });
                if (cursor > 0) {
                    initialCursors.put(bizCode, cursor);
                    log.info("[SyncTask] 业务 {} 断点续传，从游标 {} 开始", bizCode, cursor);
                }
            }

            Map<String, Long> businessRecordIds = new HashMap<>();
            routingMybatisExecutor.run(HubConstants.DS_HUB, () -> {
                for (SyncBusinessRecord rec : syncBusinessRecordMapper.selectList(
                        new LambdaQueryWrapper<SyncBusinessRecord>()
                                .eq(SyncBusinessRecord::getTaskId, id))) {
                    businessRecordIds.put(rec.getBusinessCode(), rec.getId());
                }
            });

            SyncResultDTO result = syncEngine.syncByDataSource(dataSourceKey, pageSize, id, initialCursors, businessRecordIds);

            int totalPulled = 0;
            int totalSaved = 0;
            for (Map.Entry<String, BusinessSyncResult> entry : result.getBusinessResults().entrySet()) {
                BusinessSyncResult bizResult = entry.getValue();
                totalPulled += bizResult.getPulledCount();
                totalSaved += bizResult.getSavedCount();
            }

            int finalPulled = totalPulled;
            int finalSaved = totalSaved;
            updateTaskFields(id, task -> {
                task.setStatus("SUCCESS");
                task.setMessage("同步任务完成");
                task.setFinishedAt(TimeUtils.now());
                task.setTotalPulledCount(finalPulled);
                task.setTotalSavedCount(finalSaved);
                task.setProgress(100);
                task.setRunning(false);
            });

            updateTaskFields(id, task -> {
                if (task.getMessage() != null && task.getMessage().startsWith("强制刷新")) {
                    task.setMessage("同步任务完成");
                }
            });

            log.info("[SyncTask] done id={}, pulled={}, saved={}", id, totalPulled, totalSaved);
            rabbitMqSender.sendSyncCompleted(id, dataSourceKey, result.getDatasourceType(),
                    totalPulled, totalSaved);
        } catch (Exception e) {
            updateTaskFields(id, task -> {
                task.setStatus("FAILED");
                task.setMessage("同步任务失败");
                task.setErrorMessage(e.getMessage());
                task.setFinishedAt(TimeUtils.now());
                task.setRunning(false);
            });
            log.error("[SyncTask] failed id={}", id, e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 更新同步任务的部分字段。 */
    private void updateTaskFields(Long taskId, Consumer<SyncTask> updater) {
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

    /** 检查是否是强制刷新任务，是则清除全部清洗数据和 Redis 缓存。 */
    private void doForceCleanIfNeeded(Long taskId) {
        try {
            routingMybatisExecutor.run(HubConstants.DS_HUB, () -> {
                SyncTask task = syncTaskMapper.selectById(taskId);
                if (task == null || task.getMessage() == null
                        || !task.getMessage().startsWith("强制刷新")) {
                    return;
                }
                log.warn("[SyncTask] 强制刷新清除数据中 taskId={}", taskId);
                cleanStockMapper.delete(new LambdaQueryWrapper<CleanStock>().apply("1=1"));
                cleanTradeMapper.delete(new LambdaQueryWrapper<CleanTrade>().apply("1=1"));
                cleanPositionMapper.delete(new LambdaQueryWrapper<CleanPosition>().apply("1=1"));
                cleanAssetMapper.delete(new LambdaQueryWrapper<CleanAsset>().apply("1=1"));
                task.setMessage("强制刷新-同步执行中");
                task.setStatus("RUNNING");
                syncTaskMapper.updateById(task);
                log.warn("[SyncTask] 强制刷新数据清除完成 taskId={}", taskId);
            });
        } catch (Exception e) {
            log.error("[SyncTask] 强制刷新清除数据失败 taskId={}", taskId, e);
        }
    }

    // ==================== 查询接口 ====================

    /**
     * 查询当前同步任务（最近一条）。
     * <p>返回最近一条任务记录，无任务时返回 IDLE 状态的空任务。</p>
     */
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

    /**
     * 查询最近 30 条清洗交易记录。
     * <p>按 globalId 降序排列，取前 30 条。</p>
     */
    public List<CleanTrade> cleanedTrades() {
        return routingMybatisExecutor.query(HubConstants.DS_HUB,
                () -> cleanTradeMapper.selectList(new LambdaQueryWrapper<CleanTrade>()
                        .orderByDesc(CleanTrade::getGlobalId)
                        .last("limit 30")));
    }

    /**
     * 查询指定业务记录的批次耗时明细。
     */
    public IPage<SyncBatchMetrics> getBatchMetrics(Long recordId, int page, int size) {
        return routingMybatisExecutor.query(HubConstants.DS_HUB, () ->
                batchMetricsMapper.selectPage(
                        new Page<>(page, size),
                        new LambdaQueryWrapper<SyncBatchMetrics>()
                                .eq(SyncBatchMetrics::getRecordId, recordId)
                                .orderByAsc(SyncBatchMetrics::getBatchNo)));
    }

    /**
     * 查询指定同步任务的各业务执行详情。
     */
    public List<SyncBusinessRecord> getBusinessRecords(Long taskId) {
        return routingMybatisExecutor.query(HubConstants.DS_HUB, () ->
                syncBusinessRecordMapper.selectList(new LambdaQueryWrapper<SyncBusinessRecord>()
                        .eq(SyncBusinessRecord::getTaskId, taskId)
                        .orderByAsc(SyncBusinessRecord::getBusinessCode)));
    }
}
