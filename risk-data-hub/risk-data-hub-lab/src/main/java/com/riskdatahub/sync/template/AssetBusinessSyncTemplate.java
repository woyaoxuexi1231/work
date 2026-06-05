package com.riskdatahub.sync.template;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.riskdatahub.common.constant.HubConstants;
import com.riskdatahub.datasource.RoutingMybatisExecutor;
import com.riskdatahub.id.LeafSegmentService;
import com.riskdatahub.message.MessageOutboxService;
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
import java.util.List;
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

    private final CleanAssetMapper cleanAssetMapper;
    private final OmsCashAssetMapper omsCashAssetMapper;
    private final BrokerFundAccountMapper brokerFundAccountMapper;

    public AssetBusinessSyncTemplate(RoutingMybatisExecutor routingMybatisExecutor,
                                     LeafSegmentService leafSegmentService,
                                     MessageOutboxService messageOutboxService,
                                     @Qualifier("assetPairExecutor") ThreadPoolExecutor pairExecutor,
                                     CleanAssetMapper cleanAssetMapper,
                                     OmsCashAssetMapper omsCashAssetMapper,
                                     BrokerFundAccountMapper brokerFundAccountMapper) {
        super(routingMybatisExecutor, leafSegmentService, messageOutboxService, pairExecutor);
        this.cleanAssetMapper = cleanAssetMapper;
        this.omsCashAssetMapper = omsCashAssetMapper;
        this.brokerFundAccountMapper = brokerFundAccountMapper;
    }

    @Override
    public String businessCode() {
        return "ASSET";
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
                                        .eq(OmsCashAsset::getSyncFlag, 0)
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
                                        .eq(BrokerFundAccount::getSyncFlag, 0)
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
    protected void saveBatch(BusinessSyncContext context, List<CleanAsset> targets) {
        if (targets.isEmpty()) return;
        routingMybatisExecutor.run(context.getDataSourceKey(), () -> cleanAssetMapper.insert(targets));
    }

    /**
     * 将源数据行的 sync_flag 标记为 1（已同步）。
     */
    @Override
    protected void markSourceRowSynced(BusinessSyncContext context, long rowId) {
        switch (context.getDatasourceType()) {
            case HubConstants.TYPE_TRADE_OMS:
                routingMybatisExecutor.run(context.getDataSourceKey(), () ->
                        omsCashAssetMapper.update(null,
                                new LambdaUpdateWrapper<OmsCashAsset>()
                                        .set(OmsCashAsset::getSyncFlag, 1)
                                        .eq(OmsCashAsset::getId, rowId)));
                break;
            case HubConstants.TYPE_TRADE_BROKER:
                routingMybatisExecutor.run(context.getDataSourceKey(), () ->
                        brokerFundAccountMapper.update(null,
                                new LambdaUpdateWrapper<BrokerFundAccount>()
                                        .set(BrokerFundAccount::getSyncFlag, 1)
                                        .eq(BrokerFundAccount::getId, rowId)));
                break;
            default:
                throw new IllegalArgumentException("不支持的数据源类型: " + context.getDatasourceType());
        }
    }

    /**
     * 构造清洗记录上下文
     */
    private CleanRecordContext cleanRecordContext(BusinessSyncContext context, Long sourceRowId) {
        return new CleanRecordContext(
                leafSegmentService.nextId("clean_asset"),
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
