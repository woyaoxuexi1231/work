package com.riskdatahub.sync.template;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.riskdatahub.common.constant.HubConstants;
import com.riskdatahub.datasource.RoutingMybatisExecutor;
import com.riskdatahub.id.LeafSegmentService;
import com.riskdatahub.message.MessageOutboxService;
import com.riskdatahub.sync.entity.BrokerPositionBalance;
import com.riskdatahub.sync.entity.CleanPosition;
import com.riskdatahub.sync.entity.OmsPositionHolding;
import com.riskdatahub.sync.mapper.BrokerPositionBalanceMapper;
import com.riskdatahub.sync.mapper.CleanPositionMapper;
import com.riskdatahub.sync.mapper.OmsPositionHoldingMapper;
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
 * 持仓同步模板。
 * <p>
 * 将 OMS 的 {@link OmsPositionHolding} 或 Broker 的 {@link BrokerPositionBalance}
 * 清洗后写入中台 {@link CleanPosition} 表。
 * </p>
 *
 * @author risk-data-hub
 */
@Service
public class PositionBusinessSyncTemplate
        extends AbstractBusinessSyncTemplate<PositionBusinessSyncTemplate.PositionRow, CleanPosition> {

    private final CleanPositionMapper cleanPositionMapper;
    private final OmsPositionHoldingMapper omsPositionHoldingMapper;
    private final BrokerPositionBalanceMapper brokerPositionBalanceMapper;

    public PositionBusinessSyncTemplate(RoutingMybatisExecutor routingMybatisExecutor,
                                        LeafSegmentService leafSegmentService,
                                        MessageOutboxService messageOutboxService,
                                        @Qualifier("positionPairExecutor") ThreadPoolExecutor pairExecutor,
                                        CleanPositionMapper cleanPositionMapper,
                                        OmsPositionHoldingMapper omsPositionHoldingMapper,
                                        BrokerPositionBalanceMapper brokerPositionBalanceMapper) {
        super(routingMybatisExecutor, leafSegmentService, messageOutboxService, pairExecutor);
        this.cleanPositionMapper = cleanPositionMapper;
        this.omsPositionHoldingMapper = omsPositionHoldingMapper;
        this.brokerPositionBalanceMapper = brokerPositionBalanceMapper;
    }

    @Override
    public String businessCode() {
        return "POSITION";
    }

    /**
     * 根据数据源类型分页拉取未同步的持仓数据（sync_flag = 0）。
     */
    @Override
    protected List<PositionRow> fetchPage(BusinessSyncContext context, long lastId, int pageSize) {
        switch (context.getDatasourceType()) {
            case HubConstants.TYPE_TRADE_OMS:
                return routingMybatisExecutor.query(context.getDataSourceKey(),
                        () -> omsPositionHoldingMapper.selectList(new LambdaQueryWrapper<OmsPositionHolding>()
                                .eq(OmsPositionHolding::getSyncFlag, 0)
                                .gt(OmsPositionHolding::getId, lastId)
                                .orderByAsc(OmsPositionHolding::getId)
                                .last("limit " + pageSize)))
                        .stream().map(row -> new PositionRow(
                                row.getId(), row.getInvestorName(), row.getStockCode(),
                                row.getHoldingQty(), row.getAvailableQty(), row.getCostPrice(),
                                row.getMarketValue(), row.getStatDay()))
                        .collect(Collectors.toList());
            case HubConstants.TYPE_TRADE_BROKER:
                return routingMybatisExecutor.query(context.getDataSourceKey(),
                        () -> brokerPositionBalanceMapper.selectList(new LambdaQueryWrapper<BrokerPositionBalance>()
                                .eq(BrokerPositionBalance::getSyncFlag, 0)
                                .gt(BrokerPositionBalance::getId, lastId)
                                .orderByAsc(BrokerPositionBalance::getId)
                                .last("limit " + pageSize)))
                        .stream().map(row -> new PositionRow(
                                row.getId(), row.getClientFullName(), row.getSecuCode(),
                                row.getCurrentVolume(), row.getEnableVolume(), row.getCostPx(),
                                row.getMarketAmt(), row.getBizDate()))
                        .collect(Collectors.toList());
            default:
                throw new IllegalArgumentException("不支持的数据源类型: " + context.getDatasourceType());
        }
    }

    @Override
    protected long sourceRowId(PositionRow row) {
        return row.getId();
    }

    /**
     * 将源数据行转换为中台清洗后实体。
     */
    @Override
    protected CleanPosition transform(BusinessSyncContext context, PositionRow row) {
        return CleanPosition.create(
                cleanRecordContext(context, row.getId()),
                row.getAccountName(), row.getStockCode(), row.getHoldingQty(),
                row.getAvailableQty(), row.getCostPrice(), row.getMarketValue(),
                row.getStatDay());
    }

    @Override
    protected void saveBatch(List<CleanPosition> targets) {
        routingMybatisExecutor.run(HubConstants.DS_HUB, () -> {
            for (CleanPosition target : targets) {
                cleanPositionMapper.insert(target);
            }
        });
    }

    /**
     * 将源数据行的 sync_flag 标记为 1（已同步）。
     */
    @Override
    protected void markSourceRowSynced(BusinessSyncContext context, long rowId) {
        switch (context.getDatasourceType()) {
            case HubConstants.TYPE_TRADE_OMS:
                routingMybatisExecutor.run(context.getDataSourceKey(), () ->
                        omsPositionHoldingMapper.update(null,
                                new LambdaUpdateWrapper<OmsPositionHolding>()
                                        .set(OmsPositionHolding::getSyncFlag, 1)
                                        .eq(OmsPositionHolding::getId, rowId)));
                break;
            case HubConstants.TYPE_TRADE_BROKER:
                routingMybatisExecutor.run(context.getDataSourceKey(), () ->
                        brokerPositionBalanceMapper.update(null,
                                new LambdaUpdateWrapper<BrokerPositionBalance>()
                                        .set(BrokerPositionBalance::getSyncFlag, 1)
                                        .eq(BrokerPositionBalance::getId, rowId)));
                break;
            default:
                throw new IllegalArgumentException("不支持的数据源类型: " + context.getDatasourceType());
        }
    }

    /** 构造清洗记录上下文 */
    private CleanRecordContext cleanRecordContext(BusinessSyncContext context, Long sourceRowId) {
        return new CleanRecordContext(
                leafSegmentService.nextId("clean_position"),
                context.getDataSourceKey(), context.getDatasourceType(),
                sourceRowId, context.getBatchNo(), now());
    }

    /** 持仓中间行（统一 OMS/Broker 的字段差异） */
    @Data
    @AllArgsConstructor
    static class PositionRow {
        private Long id;
        private String accountName;
        private String stockCode;
        private Long holdingQty;
        private Long availableQty;
        private BigDecimal costPrice;
        private BigDecimal marketValue;
        private String statDay;
    }
}
