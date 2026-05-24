package com.example.dynamicds.sync.business;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.dynamicds.datasource.RoutingMybatisExecutor;
import com.example.dynamicds.entity.BrokerTradeDeal;
import com.example.dynamicds.entity.CleanTrade;
import com.example.dynamicds.entity.OmsTradeOrder;
import com.example.dynamicds.mapper.BrokerTradeDealMapper;
import com.example.dynamicds.bootstrap.HubConstants;
import com.example.dynamicds.mapper.CleanTradeMapper;
import com.example.dynamicds.mapper.OmsTradeOrderMapper;
import com.example.dynamicds.service.DictionaryService;
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
 * 交易（订单/成交）同步模板。
 * 将 OMS 的 oms_trade_order 或 Broker 的 broker_trade_deal 清洗后写入中台 clean_trade 表。
 * 清洗过程中使用 DictionaryService 将原始状态码翻译为中文名称。
 * - OMS: sideCode(B/S) + tradeStatus(NEW/DONE/CANCEL)
 * - Broker: bsFlag(1/2) + statusMark(A/S/X)
 */
@Service
public class TradeBusinessSyncTemplate extends AbstractBusinessSyncTemplate<TradeBusinessSyncTemplate.TradeRow, CleanTrade> {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DictionaryService dictionaryService;
    private final CleanTradeMapper cleanTradeMapper;
    private final OmsTradeOrderMapper omsTradeOrderMapper;
    private final BrokerTradeDealMapper brokerTradeDealMapper;

    public TradeBusinessSyncTemplate(RoutingMybatisExecutor routingMybatisExecutor,
                                     LeafSegmentService leafSegmentService,
                                     MessageOutboxService messageOutboxService,
                                     @Qualifier("tradePairExecutor") ThreadPoolExecutor pairExecutor,
                                     DictionaryService dictionaryService,
                                     CleanTradeMapper cleanTradeMapper,
                                     OmsTradeOrderMapper omsTradeOrderMapper,
                                     BrokerTradeDealMapper brokerTradeDealMapper) {
        super(routingMybatisExecutor, leafSegmentService, messageOutboxService, pairExecutor);
        this.dictionaryService = dictionaryService;
        this.cleanTradeMapper = cleanTradeMapper;
        this.omsTradeOrderMapper = omsTradeOrderMapper;
        this.brokerTradeDealMapper = brokerTradeDealMapper;
    }

    @Override
    public String businessCode() {
        return "TRADE";
    }

    @Override
    protected List<TradeRow> fetchPage(BusinessSyncContext context, long lastId, int pageSize) {
        return switch (context.getDatasourceType()) {
            case HubConstants.TYPE_TRADE_OMS -> routingMybatisExecutor.query(context.getDataSourceKey(),
                    () -> omsTradeOrderMapper.selectList(new LambdaQueryWrapper<OmsTradeOrder>()
                            .eq(OmsTradeOrder::getSyncFlag, 0)
                            .gt(OmsTradeOrder::getId, lastId)
                            .orderByAsc(OmsTradeOrder::getId)
                            .last("limit " + pageSize))).stream().map(row -> new TradeRow(
                            row.getId(), row.getOrderNo(), row.getInvestorName(), row.getSideCode(), row.getOrderAmount(), row.getTradeStatus(), row.getTradeTime())).toList();
            case HubConstants.TYPE_TRADE_BROKER -> routingMybatisExecutor.query(context.getDataSourceKey(),
                    () -> brokerTradeDealMapper.selectList(new LambdaQueryWrapper<BrokerTradeDeal>()
                            .eq(BrokerTradeDeal::getSyncFlag, 0)
                            .gt(BrokerTradeDeal::getId, lastId)
                            .orderByAsc(BrokerTradeDeal::getId)
                            .last("limit " + pageSize))).stream().map(row -> new TradeRow(
                            row.getId(), row.getDealCode(), row.getClientFullName(), row.getBsFlag(), row.getTurnoverAmount(), row.getStatusMark(), row.getDealAt())).toList();
            default -> throw new IllegalArgumentException("不支持的数据源类型: " + context.getDatasourceType());
        };
    }

    @Override
    protected long sourceRowId(TradeRow row) {
        return row.getId();
    }

    @Override
    protected CleanTrade transform(BusinessSyncContext context, TradeRow row) {
        return CleanTrade.create(
                cleanRecordContext(context, row.getId()),
                row.getBizNo(),
                "股票交易",
                resolveTradeDirection(context.getDatasourceType(), row.getDirectionCode()),
                row.getAmount(),
                resolveTradeStatus(context.getDatasourceType(), row.getStatusCode()),
                row.getCounterpartyName(),
                "MANUAL_SYNC",
                row.getTradeTime()
        );
    }

    @Override
    protected void save(CleanTrade target) {
        routingMybatisExecutor.run(HubConstants.DS_HUB, () -> cleanTradeMapper.insert(target));
    }

    @Override
    protected void markSourceRowSynced(BusinessSyncContext context, long rowId) {
        switch (context.getDatasourceType()) {
            case HubConstants.TYPE_TRADE_OMS -> routingMybatisExecutor.run(context.getDataSourceKey(), () ->
                    omsTradeOrderMapper.update(null, new LambdaUpdateWrapper<OmsTradeOrder>()
                            .set(OmsTradeOrder::getSyncFlag, 1)
                            .eq(OmsTradeOrder::getId, rowId)));
            case HubConstants.TYPE_TRADE_BROKER -> routingMybatisExecutor.run(context.getDataSourceKey(), () ->
                    brokerTradeDealMapper.update(null, new LambdaUpdateWrapper<BrokerTradeDeal>()
                            .set(BrokerTradeDeal::getSyncFlag, 1)
                            .eq(BrokerTradeDeal::getId, rowId)));
            default -> throw new IllegalArgumentException("不支持的数据源类型: " + context.getDatasourceType());
        }
    }

    private String resolveTradeDirection(String datasourceType, String rawDirection) {
        if (HubConstants.TYPE_TRADE_OMS.equals(datasourceType)) {
            return "B".equalsIgnoreCase(rawDirection) ? "BUY" : "SELL";
        }
        return "1".equals(rawDirection) ? "BUY" : "SELL";
    }

    private String resolveTradeStatus(String datasourceType, String rawStatus) {
        if (HubConstants.TYPE_TRADE_OMS.equals(datasourceType)) {
            return dictionaryService.translate("trade_status_oms", rawStatus);
        }
        return dictionaryService.translate("trade_status_broker", rawStatus);
    }

    private CleanRecordContext cleanRecordContext(BusinessSyncContext context, Long sourceRowId) {
        return new CleanRecordContext(
                leafSegmentService.nextId("clean_trade"),
                context.getDataSourceKey(),
                context.getDatasourceType(),
                sourceRowId,
                context.getBatchNo(),
                LocalDateTime.now().format(FORMATTER)
        );
    }

    @Data
    @AllArgsConstructor
    static class TradeRow {
        private Long id;
        private String bizNo;
        private String counterpartyName;
        private String directionCode;
        private BigDecimal amount;
        private String statusCode;
        private String tradeTime;
    }
}
