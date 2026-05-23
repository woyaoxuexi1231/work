package com.example.dynamicds.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.dynamicds.datasource.DynamicDataSourceManager;
import com.example.dynamicds.datasource.RoutingMybatisExecutor;
import com.example.dynamicds.dto.DataSourceConfigDTO;
import com.example.dynamicds.dto.SyncResultDTO;
import com.example.dynamicds.entity.CleanTrade;
import com.example.dynamicds.mapper.CleanTradeMapper;
import com.example.dynamicds.sync.BusinessSyncContext;
import com.example.dynamicds.sync.BusinessSyncTemplate;
import com.example.dynamicds.sync.SyncSupport.BusinessSyncResult;
import com.example.dynamicds.sync.SyncSupport.SyncProgressListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * ETL 同步编排入口 —— 数据中台的核心同步引擎。
 * <p>
 * <b>策略模式（Strategy Pattern）</b><br>
 * {@code businessSyncTemplates} 是一个 {@code List&lt;BusinessSyncTemplate&gt;}，
 * Spring 会自动注入所有实现了 BusinessSyncTemplate 接口的 Bean（Stock/Trade/Position/Asset）。
 * 这有几个好处：
 * <ul>
 *   <li><b>开闭原则</b> — 新增一种业务同步（比如"分红同步"）只需新建一个 @Service 实现类，
 *       无需修改 TradeEtlService 的代码。</li>
 *   <li><b>遍历派发</b> — for 循环提交所有模板到线程池，天然并发。</li>
 *   <li><b>故障隔离</b> — 如果某个模板执行失败，不影响其他模板（各自有独立的拉取-落库线程对）。</li>
 * </ul>
 * <p>
 * <b>为什么用 syncBusinessExecutor（4 线程）而不是虚拟线程？</b>
 * <ul>
 *   <li>4 类业务各自占用 2 个线程（拉取 + 落库），固定 4 线程保证
 *       STOCK 的拉取线程不会抢占 ASSET 的落库线程。</li>
 *   <li>每类业务内部的双线程通过阻塞队列协调，不需要额外的调度。</li>
 *   <li>线程数固定，排查问题时可快速定位到对应线程名（risk-hub-business-）。</li>
 * </ul>
 * <p>
 * <b>流程</b>
 * <ol>
 *   <li>校验数据源类型（中台库不能作为同步来源）</li>
 *   <li>创建同步上下文（数据源key、类型、分页大小、批次号）</li>
 *   <li>并发派发 4 类业务（STOCK / TRADE / POSITION / ASSET）</li>
 *   <li>每类业务内部使用生产者-消费者双线程：拉取上游 → 转换 → 落库中台</li>
 *   <li>合并所有业务结果并返回摘要</li>
 * </ol>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TradeEtlService {

    private final DynamicDataSourceManager dataSourceManager;
    private final RoutingMybatisExecutor routingMybatisExecutor;
    private final CleanTradeMapper cleanTradeMapper;
    /** Spring 自动注入所有 BusinessSyncTemplate 实现类（策略模式） */
    private final List<BusinessSyncTemplate> businessSyncTemplates;
    /** 4 线程池，每类业务一个 Future */
    @Qualifier("syncBusinessExecutor")
    private final ThreadPoolExecutor syncBusinessExecutor;

    public SyncResultDTO syncByDataSource(String dataSourceKey, int pageSize) {
        return syncByDataSource(dataSourceKey, pageSize, progress -> {
        });
    }

    /**
     * 执行同步：校验 → 并发派发 4 类业务 → 合并结果。
     * 每类业务使用独立的生产者-消费者线程对，不同业务类型之间通过 syncBusinessExecutor 并发执行。
     */
    public SyncResultDTO syncByDataSource(String dataSourceKey,
                                         int pageSize,
                                         SyncProgressListener progressListener) {
        DataSourceConfigDTO config = requireSyncableConfig(dataSourceKey);
        int safePageSize = sanitizePageSize(pageSize);
        BusinessSyncContext context = buildContext(dataSourceKey, config, safePageSize);

        log.info("[同步编排] 开始同步 dataSourceKey={}, 数据源类型={}, 分页大小={}, 批次号={}, 业务模板数={}",
                dataSourceKey, config.getDatasourceType(), safePageSize, context.getBatchNo(), businessSyncTemplates.size());

        try {
            List<CompletableFuture<BusinessSyncResult>> futures = submitBusinessTemplates(context, progressListener);
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

    public List<CleanTrade> cleanedTrades() {
        return routingMybatisExecutor.query(PlatformBootstrapService.DS_HUB,
                () -> cleanTradeMapper.selectList(new LambdaQueryWrapper<CleanTrade>()
                        .orderByDesc(CleanTrade::getGlobalId)
                        .last("limit 30")));
    }

    private DataSourceConfigDTO requireSyncableConfig(String dataSourceKey) {
        DataSourceConfigDTO config = dataSourceManager.getConfig(dataSourceKey);
        if (config == null) {
            throw new IllegalArgumentException("数据源不存在: " + dataSourceKey);
        }
        if (PlatformBootstrapService.TYPE_HUB.equalsIgnoreCase(config.getDatasourceType())) {
            throw new IllegalArgumentException("中台库不能作为同步来源: " + dataSourceKey);
        }
        return config;
    }

    private int sanitizePageSize(int pageSize) {
        return Math.max(1, Math.min(pageSize, 500));
    }

    private BusinessSyncContext buildContext(String dataSourceKey, DataSourceConfigDTO config, int pageSize) {
        return BusinessSyncContext.builder()
                .dataSourceKey(dataSourceKey)
                .datasourceType(config.getDatasourceType())
                .pageSize(pageSize)
                .batchNo("SYNC-" + System.currentTimeMillis())
                .build();
    }

    private List<CompletableFuture<BusinessSyncResult>> submitBusinessTemplates(BusinessSyncContext context,
                                                                                 SyncProgressListener progressListener) {
        List<CompletableFuture<BusinessSyncResult>> futures = new ArrayList<>();
        for (BusinessSyncTemplate template : businessSyncTemplates) {
            futures.add(CompletableFuture.supplyAsync(() -> executeTemplate(template, context, progressListener), syncBusinessExecutor));
        }
        return futures;
    }

    private BusinessSyncResult executeTemplate(BusinessSyncTemplate template,
                                               BusinessSyncContext context,
                                               SyncProgressListener progressListener) {
        try {
            return template.execute(context, progressListener);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

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
                context.getDataSourceKey(),
                config.getName(),
                config.getDatasourceType(),
                pageSize,
                context.getBatchNo(),
                maxPageCount,
                totalPulled,
                totalSaved,
                businessResults
        );
    }
}
