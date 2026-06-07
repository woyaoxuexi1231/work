package com.riskdatahub.sync.template;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riskdatahub.common.constant.HubConstants;
import com.riskdatahub.datasource.RoutingMybatisExecutor;
import com.riskdatahub.id.LeafSegmentService;
import com.riskdatahub.message.MessageOutboxService;
import com.riskdatahub.sync.model.SyncSupport.SyncMetrics;
import com.riskdatahub.sync.entity.BrokerStockQuote;
import com.riskdatahub.sync.entity.CleanStock;
import com.riskdatahub.sync.entity.OmsStockSnapshot;
import com.riskdatahub.sync.mapper.BrokerStockQuoteMapper;
import com.riskdatahub.sync.mapper.CleanStockMapper;
import com.riskdatahub.sync.mapper.OmsStockSnapshotMapper;
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
 * 股票（行情/快照）同步模板。
 * <p>
 * 将 OMS 的 {@link OmsStockSnapshot} 或 Broker 的 {@link BrokerStockQuote}
 * 清洗后写入中台 {@link CleanStock} 表。
 * 两个上游系统的字段命名不同但含义对应：
 * <ul>
 *   <li>OMS: stock_code / openPrice / closePrice</li>
 *   <li>Broker: secuCode / openPx / closePx</li>
 * </ul>
 * </p>
 *
 * @author risk-data-hub
 */
@Service
public class StockBusinessSyncTemplate
        extends AbstractBusinessSyncTemplate<StockBusinessSyncTemplate.StockRow, CleanStock> {

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
    protected String getIdTag() {
        return "clean_stock";
    }

    /**
     * 根据数据源类型分页拉取未同步的股票行情数据（sync_flag = 0）。
     */
    @Override
    protected List<StockRow> fetchPage(BusinessSyncContext context, long lastId, int pageSize) {
        switch (context.getDatasourceType()) {
            case HubConstants.TYPE_TRADE_OMS:
                return routingMybatisExecutor.query(context.getDataSourceKey(),
                                () -> omsStockSnapshotMapper.selectList(new LambdaQueryWrapper<OmsStockSnapshot>()
                                        .gt(OmsStockSnapshot::getId, lastId)
                                        .orderByAsc(OmsStockSnapshot::getId)
                                        .last("limit " + pageSize)))
                        .stream().map(row -> new StockRow(
                                row.getId(), row.getSymbol(), row.getExchangeCode(),
                                row.getMarketDay(), row.getOpenPrice(), row.getHighPrice(),
                                row.getLowPrice(), row.getClosePrice(), row.getVolumeQty(),
                                row.getTurnoverAmount()))
                        .collect(Collectors.toList());
            case HubConstants.TYPE_TRADE_BROKER:
                return routingMybatisExecutor.query(context.getDataSourceKey(),
                                () -> brokerStockQuoteMapper.selectList(new LambdaQueryWrapper<BrokerStockQuote>()
                                        .gt(BrokerStockQuote::getId, lastId)
                                        .orderByAsc(BrokerStockQuote::getId)
                                        .last("limit " + pageSize)))
                        .stream().map(row -> new StockRow(
                                row.getId(), row.getSecuCode(), row.getExchangeName(),
                                row.getTradeDay(), row.getOpenPx(), row.getHighPx(),
                                row.getLowPx(), row.getClosePx(), row.getVolNum(),
                                row.getTurnoverAmt()))
                        .collect(Collectors.toList());
            default:
                throw new IllegalArgumentException("不支持的数据源类型: " + context.getDatasourceType());
        }
    }

    @Override
    protected long sourceRowId(StockRow row) {
        return row.getId();
    }

    /**
     * 将源数据行转换为中台清洗后实体。
     */
    @Override
    protected CleanStock transform(BusinessSyncContext context, StockRow row) {
        return CleanStock.create(
                cleanRecordContext(context, row.getId()),
                row.getStockCode(), row.getExchangeCode(), row.getMarketDay(),
                row.getOpenPrice(), row.getHighPrice(), row.getLowPrice(),
                row.getClosePrice(), row.getVolumeQty(), row.getTurnoverAmount());
    }

    @Override
    protected void saveBatch(BusinessSyncContext context, List<CleanStock> targets, SyncMetrics metrics) {
        if (targets.isEmpty()) return;

        // 查询本页数据中已存在的记录 → sourceRowId → globalId
        Set<Long> batchIds = targets.stream().map(CleanStock::getSourceRowId).collect(Collectors.toSet());
        metrics.stampExistingQueryStarted();
        Map<Long, Long> existingMap = cleanStockMapper.selectList(new LambdaQueryWrapper<CleanStock>()
                        .select(CleanStock::getSourceRowId, CleanStock::getGlobalId)
                        .in(CleanStock::getSourceRowId, batchIds)
                        .eq(CleanStock::getSourceSystem, context.getDataSourceKey()))
                .stream().collect(Collectors.toMap(CleanStock::getSourceRowId, CleanStock::getGlobalId));
        metrics.stampExistingQueryFinished();

        metrics.stampSplitStarted();
        List<CleanStock> toInsert = new ArrayList<>();
        List<CleanStock> toUpdate = new ArrayList<>();
        for (CleanStock target : targets) {
            Long globalId = existingMap.get(target.getSourceRowId());
            if (globalId != null) {
                target.setGlobalId(globalId);
                toUpdate.add(target);
            } else {
                toInsert.add(target);
            }
        }
        metrics.stampSplitFinished();
        if (!toInsert.isEmpty()) {
            metrics.stampInsertStarted();
            cleanStockMapper.insert(toInsert);
            metrics.stampInsertFinished(toInsert.size());
        }

        if (!toUpdate.isEmpty()) {
            metrics.stampUpdateStarted();
            cleanStockMapper.updateById(toUpdate);
            metrics.stampUpdateFinished(toUpdate.size());
        }
    }

    /**
     * 构造清洗记录上下文
     */
    private CleanRecordContext cleanRecordContext(BusinessSyncContext context, Long sourceRowId) {
        return new CleanRecordContext(
                nextId("clean_stock"),
                context.getDataSourceKey(), context.getDatasourceType(),
                sourceRowId, context.getBatchNo(), now());
    }

    /**
     * 股票行情中间行（统一 OMS/Broker 的字段差异）
     */
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
