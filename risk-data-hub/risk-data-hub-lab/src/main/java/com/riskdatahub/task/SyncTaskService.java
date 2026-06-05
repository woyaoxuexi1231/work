package com.riskdatahub.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riskdatahub.common.constant.HubConstants;
import com.riskdatahub.common.util.TimeUtils;
import com.riskdatahub.datasource.DataSourceManager;
import com.riskdatahub.datasource.RoutingMybatisExecutor;
import com.riskdatahub.datasource.dto.DataSourceConfigDTO;
import com.riskdatahub.id.LeafSegmentService;
import com.riskdatahub.message.RabbitMqSender;
import com.riskdatahub.sync.SyncOrchestrator;
import com.riskdatahub.sync.cache.SyncCacheHelper;
import com.riskdatahub.sync.entity.CleanAsset;
import com.riskdatahub.sync.entity.CleanPosition;
import com.riskdatahub.sync.entity.CleanStock;
import com.riskdatahub.sync.entity.CleanTrade;
import com.riskdatahub.sync.mapper.CleanAssetMapper;
import com.riskdatahub.sync.mapper.CleanPositionMapper;
import com.riskdatahub.sync.mapper.CleanStockMapper;
import com.riskdatahub.sync.mapper.CleanTradeMapper;
import com.riskdatahub.sync.model.SyncResultDTO;
import com.riskdatahub.sync.model.SyncSupport.BusinessSyncResult;
import com.riskdatahub.task.entity.SyncBusinessRecord;
import com.riskdatahub.task.entity.SyncTask;
import com.riskdatahub.task.mapper.SyncBusinessRecordMapper;
import com.riskdatahub.task.mapper.SyncTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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

    private static final String LOCK_KEY = "risk-hub:sync:task:lock";    // Redisson 分布式锁 key
    private static final String TAG_SYNC_TASK = "sync_task";              // 同步任务 Leaf 号段标签
    private static final String TAG_SYNC_BUSINESS_RECORD = "sync_business_record"; // 业务记录 Leaf 号段标签

    private final RedissonClient redissonClient;
    private final LeafSegmentService leafSegmentService;
    private final SyncOrchestrator syncOrchestrator;
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
    private final SyncCacheHelper syncCacheHelper;

    public SyncTaskService(RedissonClient redissonClient,
                           LeafSegmentService leafSegmentService,
                           SyncOrchestrator syncOrchestrator,
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
                           SyncCacheHelper syncCacheHelper) {
        this.redissonClient = redissonClient;
        this.leafSegmentService = leafSegmentService;
        this.syncOrchestrator = syncOrchestrator;
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
        this.syncCacheHelper = syncCacheHelper;
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
        DataSourceConfigDTO config = validateDataSource(dataSourceKey);

        // 检查当天是否已有成功的同步任务
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        Long successCount = routingMybatisExecutor.query(HubConstants.DS_HUB, () ->
                syncTaskMapper.selectCount(new LambdaQueryWrapper<SyncTask>()
                        .eq(SyncTask::getStatus, "SUCCESS")
                        .apply("DATE_FORMAT(submitted_at, '%Y-%m-%d') = {0}", today)));
        if (successCount > 0) {
            throw new IllegalStateException("今天已存在成功的同步任务，请勿重复提交");
        }

        return createQueuedTask(dataSourceKey, config, pageSize, "同步任务已提交，等待执行");
    }

    /**
     * 强制刷新 — 清除 risk_hub 全部业务数据和任务记录，然后重新全量同步。
     * <p>
     * 执行步骤：
     * <ol>
     *   <li>清空 4 张清洗表（clean_stock / clean_trade / clean_position / clean_asset）</li>
     *   <li>清空同步任务记录（sync_business_record / sync_task）</li>
     *   <li>清除 Redis 中所有已存在 ID 的缓存（sync:existing:*）</li>
     *   <li>创建新的 QUEUED 任务，跳过"当天已有成功任务"的检查</li>
     * </ol>
     * </p>
     *
     * @param dataSourceKey 数据源标识
     * @param pageSize      每页大小
     * @return 刚创建的同步任务对象
     */
    public SyncTask forceRefresh(String dataSourceKey, int pageSize) {
        DataSourceConfigDTO config = validateDataSource(dataSourceKey);

        log.warn("[SyncTask] 强制刷新开始，将清除全部业务数据 dataSourceKey={}", dataSourceKey);

        // 1. 清空 4 张清洗表
        routingMybatisExecutor.run(HubConstants.DS_HUB, () -> {
            cleanStockMapper.delete(new LambdaQueryWrapper<CleanStock>().apply("1=1"));
            cleanTradeMapper.delete(new LambdaQueryWrapper<CleanTrade>().apply("1=1"));
            cleanPositionMapper.delete(new LambdaQueryWrapper<CleanPosition>().apply("1=1"));
            cleanAssetMapper.delete(new LambdaQueryWrapper<CleanAsset>().apply("1=1"));
        });

        // 2. 清空同步任务记录
        routingMybatisExecutor.run(HubConstants.DS_HUB, () -> {
            syncBusinessRecordMapper.delete(new LambdaQueryWrapper<SyncBusinessRecord>().apply("1=1"));
            syncTaskMapper.delete(new LambdaQueryWrapper<SyncTask>().apply("1=1"));
        });

        // 3. 清除 Redis 缓存
        syncCacheHelper.clearByPattern("sync:existing:*");

        // 4. 创建新的 QUEUED 任务（跳过当天已有成功任务的检查）
        SyncTask task = createQueuedTask(dataSourceKey, config, pageSize, "强制刷新任务已提交，等待执行");
        log.warn("[SyncTask] 强制刷新完成，新任务 id={}", task.getId());
        return task;
    }

    /**
     * 校验数据源存在且不是中台库。
     */
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
     * 创建 QUEUED 状态的同步任务记录。
     *
     * @param dataSourceKey 数据源标识
     * @param config        数据源配置
     * @param pageSize      分页大小
     * @param message       任务描述信息
     * @return 刚创建的同步任务对象
     */
    private SyncTask createQueuedTask(String dataSourceKey, DataSourceConfigDTO config, int pageSize, String message) {
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
        task.setMessage(message);
        task.setRunning(true);
        routingMybatisExecutor.run(HubConstants.DS_HUB, () -> syncTaskMapper.insert(task));

        log.info("[SyncTask] createQueuedTask id={}, dataSourceKey={}, pageSize={}", task.getId(), dataSourceKey, safePageSize);
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
     * <p>流程：检查 Redis 锁 → 锁已释放但 DB 为 RUNNING 则标记 FAILED → 取出最旧 QUEUED →
     * 更新为 RUNNING → 提交线程池（锁在 runTask 中获取）。</p>
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
            // 1. 检查 DB 中所有 RUNNING 任务
            List<SyncTask> runningTasks = syncTaskMapper.selectList(
                    new LambdaQueryWrapper<SyncTask>().eq(SyncTask::getStatus, "RUNNING"));

            if (!runningTasks.isEmpty()) {
                if (lockHeld) {
                    // 锁存在且 DB 有 RUNNING → 任务正常执行中
                    return;
                }
                // 锁已释放但 DB 还是 RUNNING → 任务进程已崩溃，标记 FAILED
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

            // 2. 取出最旧的 QUEUED 任务（按 ID 升序取第一条）
            SyncTask queued = syncTaskMapper.selectOne(new LambdaQueryWrapper<SyncTask>()
                    .eq(SyncTask::getStatus, "QUEUED")
                    .orderByAsc(SyncTask::getId)
                    .last("limit 1"));
            if (queued == null) {
                return;
            }

            // 3. 更新为 RUNNING
            Long taskId = queued.getId();
            String dsKey = queued.getDataSourceKey();
            int ps = queued.getPageSize();
            updateTaskFields(taskId, task -> {
                task.setStatus("RUNNING");
                task.setStartedAt(TimeUtils.now());
            });

            // 4. 提交到线程池（锁在 runTask 中获取）
            syncTaskExecutor.submit(() -> runTask(taskId, dsKey, ps));
            log.info("[SyncTask] scanAndExecute 调度任务 id={}, dataSourceKey={}", taskId, dsKey);
        });
    }

    /**
     * 异步执行同步任务（在线程池中运行）。
     * <p>流程：获取分布式锁（非阻塞）→ 编排同步 → 记录每类业务结果 → SUCCESS/FAILED → 释放锁。
     * 锁由 Redisson 看门狗自动续期，进程崩溃后自动释放。</p>
     *
     * @param id             任务 ID
     * @param dataSourceKey  数据源标识
     * @param pageSize       分页大小
     */
    private void runTask(Long id, String dataSourceKey, int pageSize) {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        try {
            // 非阻塞获取锁，获取不到说明另一个任务已在执行
            if (!lock.tryLock(0, 30, TimeUnit.MINUTES)) {
                log.warn("[SyncTask] 无法获取分布式锁，任务 id={} 跳过", id);
                return;
            }

            // 预创建每个业务的 SyncBusinessRecord 记录（status=RUNNING），用于实时进度展示
            List<String> businessCodes = syncOrchestrator.getBusinessCodes();
            for (String bizCode : businessCodes) {
                SyncBusinessRecord record = new SyncBusinessRecord();
                record.setId(leafSegmentService.nextId(TAG_SYNC_BUSINESS_RECORD));
                record.setTaskId(id);
                record.setBusinessCode(bizCode);
                record.setStatus("RUNNING");
                record.setPulledCount(0);
                record.setSavedCount(0);
                record.setStartedAt(TimeUtils.now());
                routingMybatisExecutor.run(HubConstants.DS_HUB,
                        () -> syncBusinessRecordMapper.insert(record));
            }

            // 查询每个业务上一次成功同步的游标，用于断点续传
            Map<String, Long> initialCursors = new HashMap<>();
            for (String bizCode : businessCodes) {
                SyncBusinessRecord last = routingMybatisExecutor.query(HubConstants.DS_HUB, () ->
                        syncBusinessRecordMapper.selectOne(new LambdaQueryWrapper<SyncBusinessRecord>()
                                .eq(SyncBusinessRecord::getBusinessCode, bizCode)
                                .orderByDesc(SyncBusinessRecord::getId)
                                .last("limit 1")));
                if (last != null && last.getLastRowId() != null && last.getLastRowId() > 0) {
                    initialCursors.put(bizCode, last.getLastRowId());
                    log.info("[SyncTask] 业务 {} 断点续传，从游标 {} 开始", bizCode, last.getLastRowId());
                }
            }

            // 执行同步编排（进度事件由 SyncProgressEventListener 异步处理，实时更新 SyncBusinessRecord）
            SyncResultDTO result = syncOrchestrator.syncByDataSource(dataSourceKey, pageSize, id, initialCursors);

            // 同步完成后更新每个业务记录为 SUCCESS
            int totalPulled = 0;
            int totalSaved = 0;

            for (Map.Entry<String, BusinessSyncResult> entry : result.getBusinessResults().entrySet()) {
                String bizCode = entry.getKey();
                BusinessSyncResult bizResult = entry.getValue();
                totalPulled += bizResult.getPulledCount();
                totalSaved += bizResult.getSavedCount();

                routingMybatisExecutor.run(HubConstants.DS_HUB, () ->
                        syncBusinessRecordMapper.update(null, new LambdaUpdateWrapper<SyncBusinessRecord>()
                                .eq(SyncBusinessRecord::getTaskId, id)
                                .eq(SyncBusinessRecord::getBusinessCode, bizCode)
                                .set(SyncBusinessRecord::getStatus, "SUCCESS")
                                .set(SyncBusinessRecord::getPageCount, bizResult.getPageCount())
                                .set(SyncBusinessRecord::getPulledCount, bizResult.getPulledCount())
                                .set(SyncBusinessRecord::getSavedCount, bizResult.getSavedCount())
                                .set(SyncBusinessRecord::getLastRowId, bizResult.getLastRowId())
                                .set(SyncBusinessRecord::getFinishedAt, TimeUtils.now())));
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
            });

            log.info("[SyncTask] done id={}, pulled={}, saved={}", id, totalPulled, totalSaved);
            // 发送同步完成消息到 RabbitMQ
            rabbitMqSender.sendSyncCompleted(id, dataSourceKey, result.getDatasourceType(),
                    totalPulled, totalSaved);
        } catch (Exception e) {
            // 优先标记任务为 FAILED（这是最重要的恢复操作，先执行）
            updateTaskFields(id, task -> {
                task.setStatus("FAILED");
                task.setMessage("同步任务失败");
                task.setErrorMessage(e.getMessage());
                task.setFinishedAt(TimeUtils.now());
            });

            // 然后尝试更新业务的 SyncBusinessRecord 为 FAILED（可能无记录，忽略失败）
            try {
                routingMybatisExecutor.run(HubConstants.DS_HUB, () -> {
                    List<SyncBusinessRecord> records = syncBusinessRecordMapper.selectList(
                            new LambdaQueryWrapper<SyncBusinessRecord>()
                                    .eq(SyncBusinessRecord::getTaskId, id));
                    for (SyncBusinessRecord rec : records) {
                        rec.setStatus("FAILED");
                        rec.setErrorMessage(e.getMessage());
                        rec.setFinishedAt(TimeUtils.now());
                        syncBusinessRecordMapper.updateById(rec);
                    }
                });
            } catch (Exception ignored) {
                log.warn("[SyncTask] 更新业务记录状态失败（可忽略）id={}", id, ignored);
            }

            log.error("[SyncTask] failed id={}", id, e);
        } finally {
            // 释放分布式锁（仅当持有锁时才释放）
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
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

    /**
     * 查询指定同步任务的各业务执行详情。
     *
     * @param taskId 同步任务 ID
     * @return 业务执行记录列表（按 businessCode 排序）
     */
    public List<SyncBusinessRecord> getBusinessRecords(Long taskId) {
        return routingMybatisExecutor.query(HubConstants.DS_HUB, () ->
                syncBusinessRecordMapper.selectList(new LambdaQueryWrapper<SyncBusinessRecord>()
                        .eq(SyncBusinessRecord::getTaskId, taskId)
                        .orderByAsc(SyncBusinessRecord::getBusinessCode)));
    }
}
