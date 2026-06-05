package com.riskdatahub.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.riskdatahub.common.constant.HubConstants;
import com.riskdatahub.common.util.DistributedLockTemplate;
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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 同步任务服务 — 管理同步任务的提交、执行和状态查询。
 * <p>
 * 通过 Redisson 分布式锁保证同一时间只有一个同步任务在运行。
 * 使用 {@link DistributedLockTemplate} 统一管理锁的获取和释放。
 * </p>
 *
 * @author risk-data-hub
 */
@Slf4j
@Service
public class SyncTaskService {

    // ----- 常量 -----
    private static final String LOCK_KEY = "risk-hub:sync:task:lock";   // Redisson 分布式锁 key
    private static final String TAG_SYNC_TASK = "sync_task";              // 同步任务 Leaf 号段标签
    private static final String TAG_SYNC_BUSINESS_RECORD = "sync_business_record"; // 业务记录 Leaf 号段标签

    // ----- 注入的依赖 -----
    private final RedissonClient redissonClient;
    private final DistributedLockTemplate lockTemplate;
    private final LeafSegmentService leafSegmentService;
    private final SyncOrchestrator syncOrchestrator;
    private final DataSourceManager dataSourceManager;
    private final RoutingMybatisExecutor routingMybatisExecutor;
    private final SyncTaskMapper syncTaskMapper;
    private final SyncBusinessRecordMapper syncBusinessRecordMapper;
    private final RabbitMqSender rabbitMqSender;
    private final ThreadPoolExecutor syncTaskExecutor;

    public SyncTaskService(RedissonClient redissonClient,
                           DistributedLockTemplate lockTemplate,
                           LeafSegmentService leafSegmentService,
                           SyncOrchestrator syncOrchestrator,
                           DataSourceManager dataSourceManager,
                           RoutingMybatisExecutor routingMybatisExecutor,
                           SyncTaskMapper syncTaskMapper,
                           SyncBusinessRecordMapper syncBusinessRecordMapper,
                           RabbitMqSender rabbitMqSender,
                           @Qualifier("syncTaskExecutor") ThreadPoolExecutor syncTaskExecutor) {
        this.redissonClient = redissonClient;
        this.lockTemplate = lockTemplate;
        this.leafSegmentService = leafSegmentService;
        this.syncOrchestrator = syncOrchestrator;
        this.dataSourceManager = dataSourceManager;
        this.routingMybatisExecutor = routingMybatisExecutor;
        this.syncTaskMapper = syncTaskMapper;
        this.syncBusinessRecordMapper = syncBusinessRecordMapper;
        this.rabbitMqSender = rabbitMqSender;
        this.syncTaskExecutor = syncTaskExecutor;
    }

    // ============================================================
    // 1. 提交同步任务（入口）
    // 流程：校验数据源 → 获取分布式锁 → 持久化 QUEUED 状态 → 提交到线程池
    // ============================================================
    public SyncTask startTask(String dataSourceKey, int pageSize) {
        // ----- 1a. 校验数据源存在且不是中台库（自己不能同步自己） -----
        DataSourceConfigDTO config = dataSourceManager.getConfig(dataSourceKey);
        if (config == null) {
            throw new IllegalArgumentException("数据源不存在: " + dataSourceKey);
        }
        if (HubConstants.TYPE_HUB.equalsIgnoreCase(config.getDatasourceType())) {
            throw new IllegalArgumentException("中台库不能作为同步来源: " + dataSourceKey);
        }

        // ----- 1b. 使用 Leaf 号段生成任务 ID，获取分布式锁 -----
        long taskId = leafSegmentService.nextId(TAG_SYNC_TASK);
        RLock lock = redissonClient.getLock(LOCK_KEY);

        // ----- 1c. 在锁保护下创建任务记录并提交到线程池 -----
        return lockTemplate.tryLock(lock, taskId, () -> {
            String now = TimeUtils.now();
            int safePageSize = Math.max(1, Math.min(pageSize, 500));

            // 构建同步任务实体，状态 QUEUED 表示已提交待执行
            SyncTask task = new SyncTask();
            task.setId(taskId);
            task.setStatus("QUEUED");
            task.setProgress(0);
            task.setDataSourceKey(dataSourceKey);
            task.setDataSourceName(config.getName());
            task.setDatasourceType(config.getDatasourceType());
            task.setPageSize(safePageSize);
            task.setSubmittedAt(now);
            task.setMessage("同步任务已提交");
            task.setRunning(true);
            // 切换到中台库数据源，写入任务记录
            routingMybatisExecutor.run(HubConstants.DS_HUB, () -> syncTaskMapper.insert(task));

            log.info("[SyncTask] submit id={}, dataSourceKey={}, pageSize={}", task.getId(), dataSourceKey, safePageSize);
            // 提交到异步线程池执行，不阻塞当前请求
            syncTaskExecutor.submit(() -> runTask(task.getId(), dataSourceKey, safePageSize));
            return task;
        });
    }

    // ============================================================
    // 2. 查询当前任务状态
    // 返回最近一条任务记录，无任务时返回 IDLE 状态的空任务。
    // running 字段由 status 动态计算（QUEUED/RUNNING → true）
    // ============================================================
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

    // ============================================================
    // 3. 异步执行同步任务（在新线程中运行）
    // 流程：更新 RUNNING → 编排同步（含实时进度回调）→ 记录每类业务结果 → SUCCESS/FAILED
    // ============================================================
    private void runTask(Long id, String dataSourceKey, int pageSize) {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        try {
            // ----- 3a. 更新状态为 RUNNING -----
            updateTaskFields(id, task -> {
                task.setStatus("RUNNING");
                task.setStartedAt(TimeUtils.now());
            });

            // ----- 3b. 执行同步编排（含实时进度回调） -----
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

            // ----- 3c. 记录每个业务类型的同步结果明细 -----
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

            // ----- 3d. 更新任务为 SUCCESS 状态 -----
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
            // ----- 3e. 异常处理：更新为 FAILED 状态 -----
            updateTaskFields(id, task -> {
                task.setStatus("FAILED");
                task.setMessage("同步任务失败");
                task.setErrorMessage(e.getMessage());
                task.setFinishedAt(TimeUtils.now());
            });
            log.error("[SyncTask] failed id={}", id, e);
        } finally {
            // 释放分布式锁
            lockTemplate.releaseQuietly(lock);
        }
    }

    // ============================================================
    // 4. 更新任务字段（通用方法）
    // 先 selectById 查询 → 回调修改 → updateById 写回
    // 只修改回调中 set 的字段，其余字段保持不变
    // ============================================================
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
