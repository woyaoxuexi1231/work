package com.riskdatahub.sync;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.riskdatahub.common.constant.HubConstants;
import com.riskdatahub.common.util.TimeUtils;
import com.riskdatahub.datasource.DataSourceManager;
import com.riskdatahub.datasource.RoutingMybatisExecutor;
import com.riskdatahub.datasource.dto.DataSourceConfigDTO;
import com.riskdatahub.id.LeafSegmentService;
import com.riskdatahub.message.RabbitMqSender;
import com.riskdatahub.sync.entity.CleanAsset;
import com.riskdatahub.sync.entity.CleanPosition;
import com.riskdatahub.sync.entity.CleanStock;
import com.riskdatahub.sync.entity.CleanTrade;
import com.riskdatahub.sync.mapper.CleanAssetMapper;
import com.riskdatahub.sync.mapper.CleanPositionMapper;
import com.riskdatahub.sync.mapper.CleanStockMapper;
import com.riskdatahub.sync.mapper.CleanTradeMapper;
import com.riskdatahub.sync.model.BusinessSyncContext;
import com.riskdatahub.sync.model.SyncResultDTO;
import com.riskdatahub.sync.model.SyncSupport.BusinessSyncResult;
import com.riskdatahub.sync.template.BusinessSyncTemplate;
import com.riskdatahub.sync.task.SyncTaskService;
import com.riskdatahub.sync.task.entity.SyncBusinessRecord;
import com.riskdatahub.sync.task.entity.SyncTask;
import com.riskdatahub.sync.task.mapper.SyncBusinessRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 同步编排引擎 — ETL 同步的核心入口。
 * <p>
 * <b>策略模式：</b>{@code businessSyncTemplates} 为 Spring 自动注入的所有 {@link BusinessSyncTemplate} 实现类
 * （Stock / Trade / Position / Asset）。
 * </p>
 *
 * @author risk-data-hub
 */
@Slf4j
@Service
public class SyncEngine {

    private static final String LOCK_KEY = "risk-hub:sync:task:lock";
    private static final String TAG_SYNC_BUSINESS_RECORD = "sync_business_record";

    private final DataSourceManager dataSourceManager;
    private final RoutingMybatisExecutor routingMybatisExecutor;
    private final SyncBusinessRecordMapper syncBusinessRecordMapper;
    private final List<BusinessSyncTemplate> businessSyncTemplates;
    private final ThreadPoolExecutor syncBusinessExecutor;
    private final SyncTaskService syncTaskService;
    private final RedissonClient redissonClient;
    private final LeafSegmentService leafSegmentService;
    private final RabbitMqSender rabbitMqSender;
    private final CleanStockMapper cleanStockMapper;
    private final CleanTradeMapper cleanTradeMapper;
    private final CleanPositionMapper cleanPositionMapper;
    private final CleanAssetMapper cleanAssetMapper;

    public SyncEngine(DataSourceManager dataSourceManager,
                      RoutingMybatisExecutor routingMybatisExecutor,
                      SyncBusinessRecordMapper syncBusinessRecordMapper,
                      List<BusinessSyncTemplate> businessSyncTemplates,
                      @Qualifier("syncBusinessExecutor") ThreadPoolExecutor syncBusinessExecutor,
                      SyncTaskService syncTaskService,
                      RedissonClient redissonClient,
                      LeafSegmentService leafSegmentService,
                      RabbitMqSender rabbitMqSender,
                      CleanStockMapper cleanStockMapper,
                      CleanTradeMapper cleanTradeMapper,
                      CleanPositionMapper cleanPositionMapper,
                      CleanAssetMapper cleanAssetMapper) {
        this.dataSourceManager = dataSourceManager;
        this.routingMybatisExecutor = routingMybatisExecutor;
        this.syncBusinessRecordMapper = syncBusinessRecordMapper;
        this.businessSyncTemplates = businessSyncTemplates;
        this.syncBusinessExecutor = syncBusinessExecutor;
        this.syncTaskService = syncTaskService;
        this.redissonClient = redissonClient;
        this.leafSegmentService = leafSegmentService;
        this.rabbitMqSender = rabbitMqSender;
        this.cleanStockMapper = cleanStockMapper;
        this.cleanTradeMapper = cleanTradeMapper;
        this.cleanPositionMapper = cleanPositionMapper;
        this.cleanAssetMapper = cleanAssetMapper;
    }

    // ==================== 任务执行（总入口） ====================

    /**
     * 在后台线程中执行同步任务。
     * <p>获取分布式锁 → 预处理业务记录 → 断点续传 → 编排同步 → 更新任务状态 → 发完成消息。</p>
     */
    public void executeTask(Long id, String dataSourceKey, int pageSize) {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        try {
            if (!lock.tryLock(0, 30, TimeUnit.MINUTES)) {
                log.warn("[SyncEngine] ⚠️ 无法获取分布式锁，任务 id={} 跳过", id);
                return;
            }
            log.info("[SyncEngine] 1️⃣ 获取锁 ✅ taskId={}", id);

            doForceCleanIfNeeded(id);
            log.info("[SyncEngine] 2️⃣ 前置清理完成 ✅");

            List<String> businessCodes = businessSyncTemplates.stream()
                    .map(BusinessSyncTemplate::businessCode)
                    .collect(Collectors.toList());
            List<SyncBusinessRecord> syncBusinessRecords = prepareBusinessRecords(id, businessCodes);
            log.info("[SyncEngine] 3️⃣ 业务记录初始化完成 ✅ businessCodes={}", businessCodes);

            Map<String, Long> initialCursors = loadInitialCursors(businessCodes);
            log.info("[SyncEngine] 4️⃣ 断点续传游标加载完成 ✅");

            log.info("[SyncEngine] 5️⃣ 开始执行同步编排...");
            Map<String, Long> businessRecordIds = syncBusinessRecords.stream().collect(Collectors.toMap(SyncBusinessRecord::getBusinessCode, SyncBusinessRecord::getId));
            SyncResultDTO result = syncByDataSource(dataSourceKey, pageSize, id, initialCursors, businessRecordIds);
            log.info("[SyncEngine] 5️⃣ 同步编排完成 ✅");

            int totalPulled = result.getBusinessResults().values().stream()
                    .mapToInt(BusinessSyncResult::getPulledCount).sum();
            int totalSaved = result.getBusinessResults().values().stream()
                    .mapToInt(BusinessSyncResult::getSavedCount).sum();

            syncTaskService.updateTaskFields(id, task -> {
                task.setStatus("SUCCESS");
                task.setMessage("同步任务完成");
                task.setFinishedAt(TimeUtils.now());
                task.setTotalPulledCount(totalPulled);
                task.setTotalSavedCount(totalSaved);
                task.setProgress(100);
                task.setRunning(false);
            });
            syncTaskService.updateTaskFields(id, task -> {
                if (task.getMessage() != null && task.getMessage().startsWith("强制刷新")) {
                    task.setMessage("同步任务完成");
                }
            });
            log.info("[SyncEngine] 6️⃣ 任务状态更新 SUCCESS ✅ pulled={}, saved={}", totalPulled, totalSaved);

            Map<String, Object> msg = new HashMap<>();
            msg.put("taskId", id);
            msg.put("dataSourceKey", dataSourceKey);
            msg.put("datasourceType", result.getDatasourceType());
            msg.put("totalPulledCount", totalPulled);
            msg.put("totalSavedCount", totalSaved);
            msg.put("status", "SUCCESS");
            msg.put("timestamp", System.currentTimeMillis());
            rabbitMqSender.sendMessage("risk.sync.completed", msg);
            log.info("[SyncEngine] 7️⃣ 完成消息已发送 ✅");
        } catch (Exception e) {
            syncTaskService.updateTaskFields(id, task -> {
                task.setStatus("FAILED");
                task.setMessage("同步任务失败");
                task.setErrorMessage(e.getMessage());
                task.setFinishedAt(TimeUtils.now());
                task.setRunning(false);
            });
            log.error("[SyncEngine] ❌ 任务执行失败 id={}", id, e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("[SyncEngine] 🔓 锁已释放 taskId={}", id);
            }
        }
    }

    // ---------- 执行子步骤 ----------

    /**
     * 初始化/重置每个业务的 SyncBusinessRecord 记录。
     */
    private List<SyncBusinessRecord> prepareBusinessRecords(Long taskId, List<String> businessCodes) {
        List<SyncBusinessRecord> records = new ArrayList<>();
        for (String bizCode : businessCodes) {
            routingMybatisExecutor.run(HubConstants.DS_HUB, () -> {
                SyncBusinessRecord existing = syncBusinessRecordMapper.selectOne(
                        new LambdaQueryWrapper<SyncBusinessRecord>()
                                .eq(SyncBusinessRecord::getTaskId, taskId)
                                .eq(SyncBusinessRecord::getBusinessCode, bizCode)
                                .last("limit 1"));
                if (existing != null) {
                    existing.setStatus("RUNNING");
                    existing.setErrorMessage(null);
                    existing.setStartedAt(TimeUtils.now());
                    existing.setFinishedAt(null);
                    syncBusinessRecordMapper.updateById(existing);
                    records.add(existing);
                } else {
                    SyncBusinessRecord record = new SyncBusinessRecord();
                    record.setId(leafSegmentService.nextId(TAG_SYNC_BUSINESS_RECORD));
                    record.setTaskId(taskId);
                    record.setBusinessCode(bizCode);
                    record.setStatus("RUNNING");
                    record.setPulledCount(0);
                    record.setSavedCount(0);
                    record.setStartedAt(TimeUtils.now());
                    syncBusinessRecordMapper.insert(record);
                    records.add(record);
                }
            });
        }
        return records;
    }

    /**
     * 查询各业务表的断点续传游标（最大 source_row_id）。
     */
    private Map<String, Long> loadInitialCursors(List<String> businessCodes) {
        Map<String, Long> cursors = new HashMap<>();
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
                cursors.put(bizCode, cursor);
            }
        }
        return cursors;
    }

    /**
     * 检查是否是强制刷新任务，是则清除全部清洗数据。
     */
    private void doForceCleanIfNeeded(Long taskId) {
        try {
            routingMybatisExecutor.run(HubConstants.DS_HUB, () -> {
                SyncTask task = syncTaskService.currentTask();
                if (task == null || task.getMessage() == null
                        || !task.getMessage().startsWith("强制刷新")) {
                    return;
                }
                log.warn("[SyncEngine] 强制刷新清除数据中 taskId={}", taskId);
                cleanStockMapper.delete(new LambdaQueryWrapper<CleanStock>().apply("1=1"));
                cleanTradeMapper.delete(new LambdaQueryWrapper<CleanTrade>().apply("1=1"));
                cleanPositionMapper.delete(new LambdaQueryWrapper<CleanPosition>().apply("1=1"));
                cleanAssetMapper.delete(new LambdaQueryWrapper<CleanAsset>().apply("1=1"));
                syncTaskService.updateTaskFields(taskId, t -> t.setMessage("强制刷新-同步执行中"));
                log.warn("[SyncEngine] 强制刷新数据清除完成 taskId={}", taskId);
            });
        } catch (Exception e) {
            log.error("[SyncEngine] 强制刷新清除数据失败 taskId={}", taskId, e);
        }
    }

    // ==================== ETL 编排 ====================

    /**
     * 执行同步编排：校验数据源 → 构建上下文 → 并发派发 4 类业务 → 汇总结果。
     * <p>由 {@link #executeTask} 在持有锁后调用。</p>
     */
    public SyncResultDTO syncByDataSource(String dataSourceKey, int pageSize, Long taskId,
                                          Map<String, Long> initialCursors,
                                          Map<String, Long> businessRecordIds) {
        DataSourceConfigDTO config = requireSyncableConfig(dataSourceKey);
        int safePageSize = Math.max(1, Math.min(pageSize, 100000));
        BusinessSyncContext context = buildContext(dataSourceKey, config, safePageSize, taskId, initialCursors, businessRecordIds);

        log.info("[SyncEngine] 开始 ETL 编排 dataSourceKey={}, 类型={}, 分页={}, 批次={}, 任务ID={}",
                dataSourceKey, config.getDatasourceType(), safePageSize,
                context.getBatchNo(), taskId);

        try {
            List<CompletableFuture<BusinessSyncResult>> futures = new ArrayList<>();
            for (BusinessSyncTemplate template : businessSyncTemplates) {
                futures.add(CompletableFuture.supplyAsync(
                        () -> executeTemplate(template, context), syncBusinessExecutor));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
            SyncResultDTO summary = summarizeResults(context, config, safePageSize, futures);
            log.info("[SyncEngine] ETL 编排完成 ✅ 拉取={}, 落库={}",
                    summary.getPulledCount(), summary.getSavedCount());
            return summary;
        } catch (Exception e) {
            log.error("[SyncEngine] ETL 编排失败 ❌ {}", e.getMessage());
            throw new IllegalStateException("同步任务执行失败: " + e.getMessage(), e);
        }
    }

    // ---------- 私有辅助：校验 ----------

    private DataSourceConfigDTO requireSyncableConfig(String dataSourceKey) {
        DataSourceConfigDTO config = dataSourceManager.getConfig(dataSourceKey);
        if (config == null) {
            throw new IllegalArgumentException("数据源不存在: " + dataSourceKey);
        }
        if (HubConstants.TYPE_HUB.equalsIgnoreCase(config.getDatasourceType())) {
            throw new IllegalArgumentException("中台库不能作为同步来源: " + dataSourceKey);
        }
        return config;
    }

    // ---------- 私有辅助：上下文构建 ----------

    private BusinessSyncContext buildContext(String dataSourceKey, DataSourceConfigDTO config, int pageSize,
                                             Long taskId, Map<String, Long> initialCursors,
                                             Map<String, Long> businessRecordIds) {
        return BusinessSyncContext.builder()
                .dataSourceKey(dataSourceKey)
                .datasourceType(config.getDatasourceType())
                .pageSize(pageSize)
                .batchNo("SYNC-" + System.currentTimeMillis())
                .taskId(taskId)
                .initialCursors(initialCursors == null ? Collections.emptyMap() : initialCursors)
                .businessRecordIds(businessRecordIds == null ? Collections.emptyMap() : businessRecordIds)
                .build();
    }

    // ---------- 私有辅助：模板执行 ----------

    private BusinessSyncResult executeTemplate(BusinessSyncTemplate template, BusinessSyncContext context) {
        Long taskId = context.getTaskId();
        String bizCode = template.businessCode();
        try {
            BusinessSyncResult result = template.execute(context);
            if (taskId != null) {
                updateRecordStatus(taskId, bizCode, "SUCCESS", result);
            }
            return result;
        } catch (Exception e) {
            if (taskId != null) {
                updateRecordStatus(taskId, bizCode, "FAILED", null);
            }
            throw new RuntimeException(e);
        }
    }

    private void updateRecordStatus(Long taskId, String bizCode, String status, BusinessSyncResult result) {
        try {
            routingMybatisExecutor.run(HubConstants.DS_HUB, () -> {
                LambdaUpdateWrapper<SyncBusinessRecord> wrapper = new LambdaUpdateWrapper<SyncBusinessRecord>()
                        .eq(SyncBusinessRecord::getTaskId, taskId)
                        .eq(SyncBusinessRecord::getBusinessCode, bizCode)
                        .set(SyncBusinessRecord::getFinishedAt, java.time.LocalDateTime.now());
                if ("SUCCESS".equals(status) && result != null) {
                    wrapper.set(SyncBusinessRecord::getPageCount, result.getPageCount())
                            .set(SyncBusinessRecord::getPulledCount, result.getPulledCount())
                            .set(SyncBusinessRecord::getSavedCount, result.getSavedCount())
                            .set(SyncBusinessRecord::getLastRowId, result.getLastRowId());
                    syncBusinessRecordMapper.update(null, wrapper.set(SyncBusinessRecord::getStatus, "SUCCESS"));
                } else {
                    syncBusinessRecordMapper.update(null, wrapper.set(SyncBusinessRecord::getStatus, "FAILED")
                            .set(SyncBusinessRecord::getErrorMessage, "同步异常"));
                }
            });
        } catch (Exception ignored) {
        }
    }

    // ---------- 私有辅助：汇总 ----------

    private SyncResultDTO summarizeResults(BusinessSyncContext context,
                                           DataSourceConfigDTO config,
                                           int pageSize,
                                           List<CompletableFuture<BusinessSyncResult>> futures) throws Exception {
        Map<String, BusinessSyncResult> businessResults = new LinkedHashMap<>();
        int totalPulled = 0;
        int totalSaved = 0;
        int maxPageCount = 0;
        for (CompletableFuture<BusinessSyncResult> future : futures) {
            BusinessSyncResult result = future.get();
            businessResults.put(result.getBusinessCode(), result);
            totalPulled += result.getPulledCount();
            totalSaved += result.getSavedCount();
            maxPageCount = Math.max(maxPageCount, result.getPageCount());
        }
        return new SyncResultDTO(
                context.getDataSourceKey(), config.getName(), config.getDatasourceType(),
                pageSize, context.getBatchNo(), maxPageCount,
                totalPulled, totalSaved, businessResults);
    }
}
