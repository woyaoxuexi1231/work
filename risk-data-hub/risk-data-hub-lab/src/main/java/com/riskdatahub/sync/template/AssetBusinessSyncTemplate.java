package com.riskdatahub.sync.template;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riskdatahub.common.constant.HubConstants;
import com.riskdatahub.datasource.RoutingMybatisExecutor;
import com.riskdatahub.id.LeafSegmentService;
import com.riskdatahub.message.MessageOutboxService;
import com.riskdatahub.sync.cache.ExistingIdsCache;
import com.riskdatahub.sync.model.SyncSupport.SyncMetrics;
import com.riskdatahub.sync.entity.BrokerFundAccount;
import com.riskdatahub.sync.entity.CleanAsset;
import com.riskdatahub.sync.entity.OmsCashAsset;
import com.riskdatahub.sync.mapper.BrokerFundAccountMapper;
import com.riskdatahub.sync.mapper.CleanAssetMapper;
import com.riskdatahub.sync.mapper.OmsCashAssetMapper;
import com.riskdatahub.sync.model.BusinessSyncContext;
import com.riskdatahub.sync.model.CleanRecordContext;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

/**
 * 资金/资产同步模板。
 * <p>
 * 将 OMS 的 {@link OmsCashAsset} 或 Broker 的 {@link BrokerFundAccount}
 * 清洗后写入中台 {@link CleanAsset} 表。
 * </p>
 *
 * @author risk-data-hub
 */
@Service
public class AssetBusinessSyncTemplate
        extends AbstractBusinessSyncTemplate<AssetBusinessSyncTemplate.AssetRow, CleanAsset> {

    private final ExistingIdsCache existingIdsCache;
    private final CleanAssetMapper cleanAssetMapper;
    private final OmsCashAssetMapper omsCashAssetMapper;
    private final BrokerFundAccountMapper brokerFundAccountMapper;

    public AssetBusinessSyncTemplate(RoutingMybatisExecutor routingMybatisExecutor,
                                     LeafSegmentService leafSegmentService,
                                     MessageOutboxService messageOutboxService,
                                     @Qualifier("assetPairExecutor") ThreadPoolExecutor pairExecutor,
                                     ExistingIdsCache existingIdsCache,
                                     CleanAssetMapper cleanAssetMapper,
                                     OmsCashAssetMapper omsCashAssetMapper,
                                     BrokerFundAccountMapper brokerFundAccountMapper) {
        super(routingMybatisExecutor, leafSegmentService, messageOutboxService, pairExecutor);
        this.existingIdsCache = existingIdsCache;
        this.cleanAssetMapper = cleanAssetMapper;
        this.omsCashAssetMapper = omsCashAssetMapper;
        this.brokerFundAccountMapper = brokerFundAccountMapper;
    }

    @Override
    public String businessCode() {
        return "ASSET";
    }

    @Override
    protected String getIdTag() {
        return "clean_asset";
    }

    /**
     * 根据数据源类型分页拉取未同步的资金数据（sync_flag = 0）。
     */
    @Override
    protected List<AssetRow> fetchPage(BusinessSyncContext context, long lastId, int pageSize) {
        switch (context.getDatasourceType()) {
            case HubConstants.TYPE_TRADE_OMS:
                return routingMybatisExecutor.query(context.getDataSourceKey(),
                                () -> omsCashAssetMapper.selectList(new LambdaQueryWrapper<OmsCashAsset>()
                                        .gt(OmsCashAsset::getId, lastId)
                                        .orderByAsc(OmsCashAsset::getId)
                                        .last("limit " + pageSize)))
                        .stream().map(row -> new AssetRow(
                                row.getId(), row.getInvestorName(), row.getAccountNo(),
                                row.getCashBalance(), row.getFrozenBalance(), row.getTotalAsset(),
                                row.getStatDay()))
                        .collect(Collectors.toList());
            case HubConstants.TYPE_TRADE_BROKER:
                return routingMybatisExecutor.query(context.getDataSourceKey(),
                                () -> brokerFundAccountMapper.selectList(new LambdaQueryWrapper<BrokerFundAccount>()
                                        .gt(BrokerFundAccount::getId, lastId)
                                        .orderByAsc(BrokerFundAccount::getId)
                                        .last("limit " + pageSize)))
                        .stream().map(row -> new AssetRow(
                                row.getId(), row.getClientFullName(), row.getFundAccountNo(),
                                row.getCurrentBalance(), row.getFrozenCapital(), row.getTotalAsset(),
                                row.getBizDate()))
                        .collect(Collectors.toList());
            default:
                throw new IllegalArgumentException("不支持的数据源类型: " + context.getDatasourceType());
        }
    }

    @Override
    protected long sourceRowId(AssetRow row) {
        return row.getId();
    }

    /**
     * 将源数据行转换为中台清洗后实体。
     */
    @Override
    protected CleanAsset transform(BusinessSyncContext context, AssetRow row) {
        return CleanAsset.create(
                cleanRecordContext(context, row.getId()),
                row.getAccountName(), row.getAccountNo(), row.getCashBalance(),
                row.getFrozenBalance(), row.getTotalAsset(), row.getStatDay());
    }

    @Override
    protected void saveBatch(BusinessSyncContext context, List<CleanAsset> targets, SyncMetrics metrics) {
        if (targets.isEmpty()) return;

        String cacheKey = "sync:existing:clean_asset:" + context.getDataSourceKey();

        // 查询已存在的 sourceRowId → globalId 映射
        metrics.stampCacheLookupStarted();
        Map<Long, Long> existingMap = existingIdsCache.getExistingIds(cacheKey, () ->
                cleanAssetMapper.selectList(new LambdaQueryWrapper<CleanAsset>()
                                .select(CleanAsset::getSourceRowId, CleanAsset::getGlobalId)
                                .eq(CleanAsset::getSourceSystem, context.getDataSourceKey()))
                        .stream().collect(Collectors.toMap(CleanAsset::getSourceRowId, CleanAsset::getGlobalId)));
        metrics.stampCacheLookupFinished();

        // 筛选出待插入和待更新的数据
        List<CleanAsset> toInsert = new ArrayList<>();
        List<CleanAsset> toUpdate = new ArrayList<>();
        for (CleanAsset target : targets) {
            Long globalId = existingMap.get(target.getSourceRowId());
            if (globalId != null) {
                target.setGlobalId(globalId);
                toUpdate.add(target);
            } else {
                toInsert.add(target);
            }
        }



        if (!toInsert.isEmpty()) {
            // 批量插入需要新增的数据
            metrics.stampInsertStarted();
            cleanAssetMapper.insert(toInsert);
            metrics.stampInsertFinished(toInsert.size());

            // 缓存新增的 ID
            metrics.stampCacheAddStarted();
            existingIdsCache.addNewIds(cacheKey,
                    toInsert.stream().collect(Collectors.toMap(CleanAsset::getSourceRowId, CleanAsset::getGlobalId)));
            metrics.stampCacheAddFinished();
        }

        if (!toUpdate.isEmpty()) {
            metrics.stampUpdateStarted();
            cleanAssetMapper.updateById(toUpdate);
            metrics.stampUpdateFinished(toUpdate.size());
        }
    }

    /**
     * 构造清洗记录上下文
     */
    private CleanRecordContext cleanRecordContext(BusinessSyncContext context, Long sourceRowId) {
        return new CleanRecordContext(
                nextId("clean_asset"),
                context.getDataSourceKey(), context.getDatasourceType(),
                sourceRowId, context.getBatchNo(), now());
    }

    /**
     * 资金中间行（统一 OMS/Broker 的字段差异）
     */
    @Data
    @AllArgsConstructor
    static class AssetRow {
        private Long id;
        private String accountName;
        private String accountNo;
        private BigDecimal cashBalance;
        private BigDecimal frozenBalance;
        private BigDecimal totalAsset;
        private String statDay;
    }
}
