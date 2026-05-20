package com.example.dynamicds.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.dynamicds.datasource.DynamicDataSourceManager;
import com.example.dynamicds.datasource.RoutingMybatisExecutor;
import com.example.dynamicds.dto.DataSourceConfigDTO;
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
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * ETL orchestration entry.
 * It validates the source datasource, dispatches business templates in parallel,
 * and finally merges every business result into one summary object.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TradeEtlService {

    private final DynamicDataSourceManager dataSourceManager;
    private final RoutingMybatisExecutor routingMybatisExecutor;
    private final CleanTradeMapper cleanTradeMapper;
    private final List<BusinessSyncTemplate> businessSyncTemplates;
    @Qualifier("syncBusinessExecutor")
    private final ThreadPoolExecutor syncBusinessExecutor;

    public Map<String, Object> syncByDataSource(String dataSourceKey, int pageSize) {
        return syncByDataSource(dataSourceKey, pageSize, progress -> {
        });
    }

    public Map<String, Object> syncByDataSource(String dataSourceKey,
                                                int pageSize,
                                                SyncProgressListener progressListener) {
        DataSourceConfigDTO config = requireSyncableConfig(dataSourceKey);
        int safePageSize = Math.max(1, Math.min(pageSize, 500));
        String batchNo = "SYNC-" + System.currentTimeMillis();
        BusinessSyncContext context = BusinessSyncContext.builder()
                .dataSourceKey(dataSourceKey)
                .datasourceType(config.getDatasourceType())
                .pageSize(safePageSize)
                .batchNo(batchNo)
                .build();

        log.info("[SyncOrchestrator] start dataSourceKey={}, datasourceType={}, pageSize={}, batchNo={}, businessCount={}",
                dataSourceKey, config.getDatasourceType(), safePageSize, batchNo, businessSyncTemplates.size());

        try {
            List<Future<BusinessSyncResult>> futures = new ArrayList<>();
            for (BusinessSyncTemplate template : businessSyncTemplates) {
                futures.add(syncBusinessExecutor.submit(() -> template.execute(context, progressListener)));
            }

            Map<String, Object> businessResults = new LinkedHashMap<>();
            int totalPulled = 0;
            int totalSaved = 0;
            int maxPageCount = 0;
            for (Future<BusinessSyncResult> future : futures) {
                BusinessSyncResult result = future.get();
                businessResults.put(result.getBusinessCode(), result.toMap());
                totalPulled += result.getPulledCount();
                totalSaved += result.getSavedCount();
                maxPageCount = Math.max(maxPageCount, result.getPageCount());
            }

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("dataSourceKey", dataSourceKey);
            summary.put("dataSourceName", config.getName());
            summary.put("datasourceType", config.getDatasourceType());
            summary.put("pageSize", safePageSize);
            summary.put("batchNo", batchNo);
            summary.put("pageCount", maxPageCount);
            summary.put("pulledCount", totalPulled);
            summary.put("savedCount", totalSaved);
            summary.put("businessResults", businessResults);
            log.info("[SyncOrchestrator] done dataSourceKey={}, batchNo={}, pulledCount={}, savedCount={}",
                    dataSourceKey, batchNo, totalPulled, totalSaved);
            return summary;
        } catch (Exception e) {
            log.error("[SyncOrchestrator] failed dataSourceKey={}, message={}", dataSourceKey, e.getMessage(), e);
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
}