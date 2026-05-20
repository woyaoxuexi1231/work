package com.example.dynamicds.sync.business;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.dynamicds.datasource.RoutingMybatisExecutor;
import com.example.dynamicds.entity.BrokerStockQuote;
import com.example.dynamicds.entity.CleanStock;
import com.example.dynamicds.entity.OmsStockSnapshot;
import com.example.dynamicds.mapper.BrokerStockQuoteMapper;
import com.example.dynamicds.mapper.CleanStockMapper;
import com.example.dynamicds.mapper.OmsStockSnapshotMapper;
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
public class StockBusinessSyncTemplate extends AbstractBusinessSyncTemplate<StockBusinessSyncTemplate.StockRow, CleanStock> {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CleanStockMapper cleanStockMapper;
    private final OmsStockSnapshotMapper omsStockSnapshotMapper;
    private final BrokerStockQuoteMapper brokerStockQuoteMapper;

    public StockBusinessSyncTemplate(RoutingMybatisExecutor routingMybatisExecutor,
                                     LeafSegmentService leafSegmentService,
                                     MessageOutboxService messageOutboxService,
                                     @Qualifier("stockPairExecutor") ThreadPoolExecutor pairExecutor,
                                     CleanStockMapper cleanStockMapper,
                                     OmsStockSnapshotMapper omsStockSnapshotMapper,
                                     BrokerStockQuoteMapper brokerStockQuoteMapper) {
        super(routingMybatisExecutor, leafSegmentService, messageOutboxService, pairExecutor);
        this.cleanStockMapper = cleanStockMapper;
        this.omsStockSnapshotMapper = omsStockSnapshotMapper;
        this.brokerStockQuoteMapper = brokerStockQuoteMapper;
    }

    @Override
    public String businessCode() {
        return "STOCK";
    }

    @Override
    protected List<StockRow> fetchPage(BusinessSyncContext context, long lastId, int pageSize) {
        return switch (context.getDatasourceType()) {
            case PlatformBootstrapService.TYPE_TRADE_OMS -> routingMybatisExecutor.query(context.getDataSourceKey(),
                    () -> omsStockSnapshotMapper.selectList(new LambdaQueryWrapper<OmsStockSnapshot>()
                            .eq(OmsStockSnapshot::getSyncFlag, 0)
                            .gt(OmsStockSnapshot::getId, lastId)
                            .orderByAsc(OmsStockSnapshot::getId)
                            .last("limit " + pageSize))).stream().map(row -> new StockRow(
                            row.getId(), row.getSymbol(), row.getExchangeCode(), row.getMarketDay(), row.getOpenPrice(),
                            row.getHighPrice(), row.getLowPrice(), row.getClosePrice(), row.getVolumeQty(), row.getTurnoverAmount())).toList();
            case PlatformBootstrapService.TYPE_TRADE_BROKER -> routingMybatisExecutor.query(context.getDataSourceKey(),
                    () -> brokerStockQuoteMapper.selectList(new LambdaQueryWrapper<BrokerStockQuote>()
                            .eq(BrokerStockQuote::getSyncFlag, 0)
                            .gt(BrokerStockQuote::getId, lastId)
                            .orderByAsc(BrokerStockQuote::getId)
                            .last("limit " + pageSize))).stream().map(row -> new StockRow(
                            row.getId(), row.getSecuCode(), row.getExchangeName(), row.getTradeDay(), row.getOpenPx(),
                            row.getHighPx(), row.getLowPx(), row.getClosePx(), row.getVolNum(), row.getTurnoverAmt())).toList();
            default -> throw new IllegalArgumentException("不支持的数据源类型: " + context.getDatasourceType());
        };
    }

    @Override
    protected long sourceRowId(StockRow row) {
        return row.getId();
    }

    @Override
    protected CleanStock transform(BusinessSyncContext context, StockRow row) {
        CleanStock cleanStock = new CleanStock();
        cleanStock.setGlobalId(leafSegmentService.nextId("clean_stock"));
        cleanStock.setSourceSystem(context.getDataSourceKey());
        cleanStock.setSourceType(context.getDatasourceType());
        cleanStock.setSourceRowId(row.getId());
        cleanStock.setStockCode(row.getStockCode());
        cleanStock.setExchangeCode(row.getExchangeCode());
        cleanStock.setMarketDay(row.getMarketDay());
        cleanStock.setOpenPrice(row.getOpenPrice());
        cleanStock.setHighPrice(row.getHighPrice());
        cleanStock.setLowPrice(row.getLowPrice());
        cleanStock.setClosePrice(row.getClosePrice());
        cleanStock.setVolumeQty(row.getVolumeQty());
        cleanStock.setTurnoverAmount(row.getTurnoverAmount());
        cleanStock.setCleanBatch(context.getBatchNo());
        cleanStock.setCreatedAt(LocalDateTime.now().format(FORMATTER));
        return cleanStock;
    }

    @Override
    protected void save(CleanStock target) {
        routingMybatisExecutor.run(PlatformBootstrapService.DS_HUB, () -> cleanStockMapper.insert(target));
    }

    @Override
    protected void markSourceRowSynced(BusinessSyncContext context, long rowId) {
        switch (context.getDatasourceType()) {
            case PlatformBootstrapService.TYPE_TRADE_OMS -> routingMybatisExecutor.run(context.getDataSourceKey(), () ->
                    omsStockSnapshotMapper.update(null, new LambdaUpdateWrapper<OmsStockSnapshot>()
                            .set(OmsStockSnapshot::getSyncFlag, 1)
                            .eq(OmsStockSnapshot::getId, rowId)));
            case PlatformBootstrapService.TYPE_TRADE_BROKER -> routingMybatisExecutor.run(context.getDataSourceKey(), () ->
                    brokerStockQuoteMapper.update(null, new LambdaUpdateWrapper<BrokerStockQuote>()
                            .set(BrokerStockQuote::getSyncFlag, 1)
                            .eq(BrokerStockQuote::getId, rowId)));
            default -> throw new IllegalArgumentException("不支持的数据源类型: " + context.getDatasourceType());
        }
    }

    @Data
    @AllArgsConstructor
    static class StockRow {
        private Long id;
        private String stockCode;
        private String exchangeCode;
        private String marketDay;
        private BigDecimal openPrice;
        private BigDecimal highPrice;
        private BigDecimal lowPrice;
        private BigDecimal closePrice;
        private Long volumeQty;
        private BigDecimal turnoverAmount;
    }
}
