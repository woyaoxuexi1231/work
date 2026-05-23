package com.example.dynamicds.sync.business;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.dynamicds.datasource.RoutingMybatisExecutor;
import com.example.dynamicds.entity.BrokerPositionBalance;
import com.example.dynamicds.entity.CleanPosition;
import com.example.dynamicds.entity.OmsPositionHolding;
import com.example.dynamicds.mapper.BrokerPositionBalanceMapper;
import com.example.dynamicds.mapper.CleanPositionMapper;
import com.example.dynamicds.mapper.OmsPositionHoldingMapper;
import com.example.dynamicds.service.LeafSegmentService;
import com.example.dynamicds.service.MessageOutboxService;
import com.example.dynamicds.service.PlatformBootstrapService;
import com.example.dynamicds.sync.AbstractBusinessSyncTemplate;
import com.example.dynamicds.sync.BusinessSyncContext;
import com.example.dynamicds.sync.CleanRecordContext;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 持仓同步模板。
 * 将 OMS 的 oms_position_holding 或 Broker 的 broker_position_balance 清洗后写入中台 clean_position 表。
 */
@Service
public class PositionBusinessSyncTemplate extends AbstractBusinessSyncTemplate<PositionBusinessSyncTemplate.PositionRow, CleanPosition> {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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

    @Override
    protected List<PositionRow> fetchPage(BusinessSyncContext context, long lastId, int pageSize) {
        return switch (context.getDatasourceType()) {
            case PlatformBootstrapService.TYPE_TRADE_OMS -> routingMybatisExecutor.query(context.getDataSourceKey(),
                    () -> omsPositionHoldingMapper.selectList(new LambdaQueryWrapper<OmsPositionHolding>()
                            .eq(OmsPositionHolding::getSyncFlag, 0)
                            .gt(OmsPositionHolding::getId, lastId)
                            .orderByAsc(OmsPositionHolding::getId)
                            .last("limit " + pageSize))).stream().map(row -> new PositionRow(
                            row.getId(), row.getInvestorName(), row.getStockCode(), row.getHoldingQty(), row.getAvailableQty(), row.getCostPrice(), row.getMarketValue(), row.getStatDay())).toList();
            case PlatformBootstrapService.TYPE_TRADE_BROKER -> routingMybatisExecutor.query(context.getDataSourceKey(),
                    () -> brokerPositionBalanceMapper.selectList(new LambdaQueryWrapper<BrokerPositionBalance>()
                            .eq(BrokerPositionBalance::getSyncFlag, 0)
                            .gt(BrokerPositionBalance::getId, lastId)
                            .orderByAsc(BrokerPositionBalance::getId)
                            .last("limit " + pageSize))).stream().map(row -> new PositionRow(
                            row.getId(), row.getClientFullName(), row.getSecuCode(), row.getCurrentVolume(), row.getEnableVolume(), row.getCostPx(), row.getMarketAmt(), row.getBizDate())).toList();
            default -> throw new IllegalArgumentException("不支持的数据源类型: " + context.getDatasourceType());
        };
    }

    @Override
    protected long sourceRowId(PositionRow row) {
        return row.getId();
    }

    @Override
    protected CleanPosition transform(BusinessSyncContext context, PositionRow row) {
        return CleanPosition.create(
                cleanRecordContext(context, row.getId()),
                row.getAccountName(),
                row.getStockCode(),
                row.getHoldingQty(),
                row.getAvailableQty(),
                row.getCostPrice(),
                row.getMarketValue(),
                row.getStatDay()
        );
    }

    @Override
    protected void save(CleanPosition target) {
        routingMybatisExecutor.run(PlatformBootstrapService.DS_HUB, () -> cleanPositionMapper.insert(target));
    }

    @Override
    protected void markSourceRowSynced(BusinessSyncContext context, long rowId) {
        switch (context.getDatasourceType()) {
            case PlatformBootstrapService.TYPE_TRADE_OMS -> routingMybatisExecutor.run(context.getDataSourceKey(), () ->
                    omsPositionHoldingMapper.update(null, new LambdaUpdateWrapper<OmsPositionHolding>()
                            .set(OmsPositionHolding::getSyncFlag, 1)
                            .eq(OmsPositionHolding::getId, rowId)));
            case PlatformBootstrapService.TYPE_TRADE_BROKER -> routingMybatisExecutor.run(context.getDataSourceKey(), () ->
                    brokerPositionBalanceMapper.update(null, new LambdaUpdateWrapper<BrokerPositionBalance>()
                            .set(BrokerPositionBalance::getSyncFlag, 1)
                            .eq(BrokerPositionBalance::getId, rowId)));
            default -> throw new IllegalArgumentException("不支持的数据源类型: " + context.getDatasourceType());
        }
    }

    private CleanRecordContext cleanRecordContext(BusinessSyncContext context, Long sourceRowId) {
        return new CleanRecordContext(
                leafSegmentService.nextId("clean_position"),
                context.getDataSourceKey(),
                context.getDatasourceType(),
                sourceRowId,
                context.getBatchNo(),
                LocalDateTime.now().format(FORMATTER)
        );
    }

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
