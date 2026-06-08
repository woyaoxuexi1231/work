package com.riskdatahub.sync;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riskdatahub.common.constant.HubConstants;
import com.riskdatahub.datasource.DataSourceManager;
import com.riskdatahub.datasource.RoutingMybatisExecutor;
import com.riskdatahub.datasource.dto.DataSourceConfigDTO;
import com.riskdatahub.sync.entity.CleanTrade;
import com.riskdatahub.sync.mapper.CleanTradeMapper;
import com.riskdatahub.sync.model.BusinessSyncContext;
import com.riskdatahub.sync.model.SyncResultDTO;
import com.riskdatahub.sync.model.SyncSupport.BusinessSyncResult;
import com.riskdatahub.sync.template.BusinessSyncTemplate;
import com.riskdatahub.sync.task.entity.SyncBusinessRecord;
import com.riskdatahub.sync.task.mapper.SyncBusinessRecordMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

/**
 * 同步编排引擎 — ETL 同步的核心入口。
 * <p>
 * <b>策略模式（Strategy Pattern）：</b>
 * {@code businessSyncTemplates} 为 Spring 自动注入的所有 {@link BusinessSyncTemplate} 实现类
 * （Stock / Trade / Position / Asset）。新增业务类型只需新建实现类，无需修改本类。
 * </p>
 * <p>
 * <b>执行流程：</b>
 * <ol>
 *   <li>校验数据源类型（中台库不能作为同步来源）</li>
 *   <li>创建同步上下文（数据源 key、类型、分页大小、批次号）</li>
 *   <li>并发派发 4 类业务（STOCK / TRADE / POSITION / ASSET）</li>
 *   <li>每类业务内部使用生产者-消费者双线程：拉取上游 → 转换 → 落库中台</li>
 *   <li>合并所有业务结果并返回摘要</li>
 * </ol>
 * </p>
 *
 * @author risk-data-hub
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncOrchestrator {

    private final DataSourceManager dataSourceManager;
    private final RoutingMybatisExecutor routingMybatisExecutor;
    private final CleanTradeMapper cleanTradeMapper;
    private final SyncBusinessRecordMapper syncBusinessRecordMapper;

    /** Spring 自动注入所有 BusinessSyncTemplate 实现类（策略模式） */
    private final List<BusinessSyncTemplate> businessSyncTemplates;

    /** 4 线程池，每类业务一个 Future */
    @Qualifier("syncBusinessExecutor")
    private final ThreadPoolExecutor syncBusinessExecutor;

    /**
     * 执行同步。
     * <p>主流程：校验数据源 → 构建上下文 → 并发派发 4 类业务 → 等待全部完成 → 汇总结果。
     * 进度事件由模板内 {@link org.springframework.context.ApplicationEventPublisher} 发布。</p>
     *
     * @param dataSourceKey 数据源标识
     * @param pageSize      分页大小
     * @param taskId        同步任务 ID（用于进度事件关联）
     * @param initialCursors 每个业务的上一次成功游标（businessCode → lastRowId），用于断点续传
     * @return 同步结果摘要
     */
    public SyncResultDTO syncByDataSource(String dataSourceKey, int pageSize, Long taskId,
                                          Map<String, Long> initialCursors,
                                          Map<String, Long> businessRecordIds) {
        DataSourceConfigDTO config = requireSyncableConfig(dataSourceKey);
        int safePageSize = sanitizePageSize(pageSize);
        BusinessSyncContext context = buildContext(dataSourceKey, config, safePageSize, taskId, initialCursors, businessRecordIds);

        log.info("[同步编排] 开始同步 dataSourceKey={}, 数据源类型={}, 分页大小={}, 批次号={}, 任务ID={}, 业务模板数={}",
                dataSourceKey, config.getDatasourceType(), safePageSize,
                context.getBatchNo(), taskId, businessSyncTemplates.size());

        try {
            List<CompletableFuture<BusinessSyncResult>> futures = submitBusinessTemplates(context);
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
            SyncResultDTO summary = summarizeResults(context, config, safePageSize, futures);
            log.info("[同步编排] 同步完成 dataSourceKey={}, 批次号={}, 拉取总数={}, 落库总数={}",
                    dataSourceKey, context.getBatchNo(), summary.getPulledCount(), summary.getSavedCount());
            return summary;
        } catch (Exception e) {
            log.error("[同步编排] 同步失败 dataSourceKey={}, 错误={}", dataSourceKey, e.getMessage(), e);
            throw new IllegalStateException("同步任务执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 查询最近 30 条清洗交易记录。
     * <p>按 globalId 降序排列，取前 30 条。</p>
     *
     * @return 清洗交易记录列表
     */
    public List<CleanTrade> cleanedTrades() {
        return routingMybatisExecutor.query(HubConstants.DS_HUB,
                () -> cleanTradeMapper.selectList(new LambdaQueryWrapper<CleanTrade>()
                        .orderByDesc(CleanTrade::getGlobalId)
                        .last("limit 30")));
    }

    /**
     * 获取所有业务编码列表。
     * <p>用于 SyncTaskService 在同步开始前预创建 SyncBusinessRecord 记录。</p>
     *
     * @return 业务编码列表（STOCK / TRADE / POSITION / ASSET）
     */
    public List<String> getBusinessCodes() {
        return businessSyncTemplates.stream()
                .map(BusinessSyncTemplate::businessCode)
                .collect(Collectors.toList());
    }

    /** 校验数据源存在且不是中台库 */
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

    /** 限制分页大小在 [1, 100000] 区间 */
    private int sanitizePageSize(int pageSize) {
        return Math.max(1, Math.min(pageSize, 100000));
    }

    /** 构建同步上下文（含唯一批次号、任务 ID、断点续传游标和业务记录 ID 映射） */
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

    /** 并发提交所有业务模板到线程池 */
    private List<CompletableFuture<BusinessSyncResult>> submitBusinessTemplates(BusinessSyncContext context) {
        List<CompletableFuture<BusinessSyncResult>> futures = new ArrayList<>();
        for (BusinessSyncTemplate template : businessSyncTemplates) {
            futures.add(CompletableFuture.supplyAsync(
                    () -> executeTemplate(template, context), syncBusinessExecutor));
        }
        return futures;
    }

    /** 执行单个模板，每个业务独立更新自己的 SyncBusinessRecord 状态 */
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
            // 状态更新失败不影响同步主流程
        }
    }

    /** 汇总所有模板的执行结果 */
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
