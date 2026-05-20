package com.example.dynamicds.sync.business;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.dynamicds.datasource.RoutingMybatisExecutor;
import com.example.dynamicds.entity.BrokerFundAccount;
import com.example.dynamicds.entity.CleanAsset;
import com.example.dynamicds.entity.OmsCashAsset;
import com.example.dynamicds.mapper.BrokerFundAccountMapper;
import com.example.dynamicds.mapper.CleanAssetMapper;
import com.example.dynamicds.mapper.OmsCashAssetMapper;
import com.example.dynamicds.service.LeafSegmentService;
import com.example.dynamicds.service.MessageOutboxService;
import com.example.dynamicds.service.PlatformBootstrapService;
import com.example.dynamicds.sync.AbstractBusinessSyncTemplate;
import com.example.dynamicds.sync.BusinessSyncContext;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

@Service
public class AssetBusinessSyncTemplate extends AbstractBusinessSyncTemplate<AssetBusinessSyncTemplate.AssetRow, CleanAsset> {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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

    @Override
    protected List<AssetRow> fetchPage(BusinessSyncContext context, long lastId, int pageSize) {
        return switch (context.getDatasourceType()) {
            case PlatformBootstrapService.TYPE_TRADE_OMS -> routingMybatisExecutor.query(context.getDataSourceKey(),
                    () -> omsCashAssetMapper.selectList(new LambdaQueryWrapper<OmsCashAsset>()
                            .eq(OmsCashAsset::getSyncFlag, 0)
                            .gt(OmsCashAsset::getId, lastId)
                            .orderByAsc(OmsCashAsset::getId)
                            .last("limit " + pageSize))).stream().map(row -> new AssetRow(
                            row.getId(), row.getInvestorName(), row.getAccountNo(), row.getCashBalance(), row.getFrozenBalance(), row.getTotalAsset(), row.getStatDay())).toList();
            case PlatformBootstrapService.TYPE_TRADE_BROKER -> routingMybatisExecutor.query(context.getDataSourceKey(),
                    () -> brokerFundAccountMapper.selectList(new LambdaQueryWrapper<BrokerFundAccount>()
                            .eq(BrokerFundAccount::getSyncFlag, 0)
                            .gt(BrokerFundAccount::getId, lastId)
                            .orderByAsc(BrokerFundAccount::getId)
                            .last("limit " + pageSize))).stream().map(row -> new AssetRow(
                            row.getId(), row.getClientFullName(), row.getFundAccountNo(), row.getCurrentBalance(), row.getFrozenCapital(), row.getTotalAsset(), row.getBizDate())).toList();
            default -> throw new IllegalArgumentException("不支持的数据源类型: " + context.getDatasourceType());
        };
    }

    @Override
    protected long sourceRowId(AssetRow row) {
        return row.getId();
    }

    @Override
    protected CleanAsset transform(BusinessSyncContext context, AssetRow row) {
        CleanAsset cleanAsset = new CleanAsset();
        cleanAsset.setGlobalId(leafSegmentService.nextId("clean_asset"));
        cleanAsset.setSourceSystem(context.getDataSourceKey());
        cleanAsset.setSourceType(context.getDatasourceType());
        cleanAsset.setSourceRowId(row.getId());
        cleanAsset.setAccountName(row.getAccountName());
        cleanAsset.setAccountNo(row.getAccountNo());
        cleanAsset.setCashBalance(row.getCashBalance());
        cleanAsset.setFrozenBalance(row.getFrozenBalance());
        cleanAsset.setTotalAsset(row.getTotalAsset());
        cleanAsset.setStatDay(row.getStatDay());
        cleanAsset.setCleanBatch(context.getBatchNo());
        cleanAsset.setCreatedAt(LocalDateTime.now().format(FORMATTER));
        return cleanAsset;
    }

    @Override
    protected void save(CleanAsset target) {
        routingMybatisExecutor.run(PlatformBootstrapService.DS_HUB, () -> cleanAssetMapper.insert(target));
    }

    @Override
    protected void markSourceRowSynced(BusinessSyncContext context, long rowId) {
        switch (context.getDatasourceType()) {
            case PlatformBootstrapService.TYPE_TRADE_OMS -> routingMybatisExecutor.run(context.getDataSourceKey(), () ->
                    omsCashAssetMapper.update(null, new LambdaUpdateWrapper<OmsCashAsset>()
                            .set(OmsCashAsset::getSyncFlag, 1)
                            .eq(OmsCashAsset::getId, rowId)));
            case PlatformBootstrapService.TYPE_TRADE_BROKER -> routingMybatisExecutor.run(context.getDataSourceKey(), () ->
                    brokerFundAccountMapper.update(null, new LambdaUpdateWrapper<BrokerFundAccount>()
                            .set(BrokerFundAccount::getSyncFlag, 1)
                            .eq(BrokerFundAccount::getId, rowId)));
            default -> throw new IllegalArgumentException("不支持的数据源类型: " + context.getDatasourceType());
        }
    }

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
