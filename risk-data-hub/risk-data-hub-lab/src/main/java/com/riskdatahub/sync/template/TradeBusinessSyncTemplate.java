package com.riskdatahub.sync.template;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riskdatahub.common.constant.HubConstants;
import com.riskdatahub.datasource.RoutingMybatisExecutor;
import com.riskdatahub.dictionary.DictionaryService;
import com.riskdatahub.id.LeafSegmentService;
import com.riskdatahub.message.MessageOutboxService;
import com.riskdatahub.sync.cache.ExistingIdsCache;
import com.riskdatahub.sync.model.SyncSupport.SyncMetrics;
import com.riskdatahub.sync.entity.BrokerTradeDeal;
import com.riskdatahub.sync.entity.CleanTrade;
import com.riskdatahub.sync.entity.OmsTradeOrder;
import com.riskdatahub.sync.mapper.BrokerTradeDealMapper;
import com.riskdatahub.sync.mapper.CleanTradeMapper;
import com.riskdatahub.sync.mapper.OmsTradeOrderMapper;
import com.riskdatahub.sync.model.BusinessSyncContext;
import com.riskdatahub.sync.model.CleanRecordContext;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

/**
 * 交易（订单/成交）同步模板。
 * <p>
 * 将 OMS 的 {@link OmsTradeOrder} 或 Broker 的 {@link BrokerTradeDeal}
 * 清洗后写入中台 {@link CleanTrade} 表。
 * 清洗过程中使用 {@link DictionaryService} 将原始状态码翻译为中文名称。
 * </p>
 *
 * @author risk-data-hub
 */
@Service
public class TradeBusinessSyncTemplate
        extends AbstractBusinessSyncTemplate<TradeBusinessSyncTemplate.TradeRow, CleanTrade> {

    private final DictionaryService dictionaryService;
    private final ExistingIdsCache existingIdsCache;
    private final CleanTradeMapper cleanTradeMapper;
    private final OmsTradeOrderMapper omsTradeOrderMapper;
    private final BrokerTradeDealMapper brokerTradeDealMapper;

    public TradeBusinessSyncTemplate(RoutingMybatisExecutor routingMybatisExecutor,
                                     LeafSegmentService leafSegmentService,
                                     MessageOutboxService messageOutboxService,
                                     @Qualifier("tradePairExecutor") ThreadPoolExecutor pairExecutor,
                                     DictionaryService dictionaryService,
                                     ExistingIdsCache existingIdsCache,
                                     CleanTradeMapper cleanTradeMapper,
                                     OmsTradeOrderMapper omsTradeOrderMapper,
                                     BrokerTradeDealMapper brokerTradeDealMapper) {
        super(routingMybatisExecutor, leafSegmentService, messageOutboxService, pairExecutor);
        this.dictionaryService = dictionaryService;
        this.existingIdsCache = existingIdsCache;
        this.cleanTradeMapper = cleanTradeMapper;
        this.omsTradeOrderMapper = omsTradeOrderMapper;
        this.brokerTradeDealMapper = brokerTradeDealMapper;
    }

    @Override
    public String businessCode() {
        return "TRADE";
    }

    @Override
    protected String getIdTag() {
        return "clean_trade";
    }

    /**
     * 根据数据源类型分页拉取未同步的交易数据（sync_flag = 0）。
     */
    @Override
    protected List<TradeRow> fetchPage(BusinessSyncContext context, long lastId, int pageSize) {
        switch (context.getDatasourceType()) {
            case HubConstants.TYPE_TRADE_OMS:
                return routingMybatisExecutor.query(context.getDataSourceKey(),
                                () -> omsTradeOrderMapper.selectList(new LambdaQueryWrapper<OmsTradeOrder>()
                                        .gt(OmsTradeOrder::getId, lastId)
                                        .orderByAsc(OmsTradeOrder::getId)
                                        .last("limit " + pageSize)))
                        .stream().map(row -> new TradeRow(
                                row.getId(), row.getOrderNo(), row.getInvestorName(),
                                row.getSideCode(), row.getOrderAmount(), row.getTradeStatus(),
                                row.getTradeTime()))
                        .collect(Collectors.toList());
            case HubConstants.TYPE_TRADE_BROKER:
                return routingMybatisExecutor.query(context.getDataSourceKey(),
                                () -> brokerTradeDealMapper.selectList(new LambdaQueryWrapper<BrokerTradeDeal>()
                                        .gt(BrokerTradeDeal::getId, lastId)
                                        .orderByAsc(BrokerTradeDeal::getId)
                                        .last("limit " + pageSize)))
                        .stream().map(row -> new TradeRow(
                                row.getId(), row.getDealCode(), row.getClientFullName(),
                                row.getBsFlag(), row.getTurnoverAmount(), row.getStatusMark(),
                                row.getDealAt()))
                        .collect(Collectors.toList());
            default:
                throw new IllegalArgumentException("不支持的数据源类型: " + context.getDatasourceType());
        }
    }

    @Override
    protected long sourceRowId(TradeRow row) {
        return row.getId();
    }

    /**
     * 将源数据行转换为中台清洗后实体，翻译状态码和方向码为中文。
     */
    @Override
    protected CleanTrade transform(BusinessSyncContext context, TradeRow row) {
        return CleanTrade.create(
                cleanRecordContext(context, row.getId()),
                row.getBizNo(), "股票交易",
                resolveTradeDirection(context.getDatasourceType(), row.getDirectionCode()),
                row.getAmount(),
                resolveTradeStatus(context.getDatasourceType(), row.getStatusCode()),
                row.getCounterpartyName(), "MANUAL_SYNC", row.getTradeTime());
    }

    @Override
    protected void saveBatch(BusinessSyncContext context, List<CleanTrade> targets, SyncMetrics metrics) {
        if (targets.isEmpty()) return;

        String cacheKey = "sync:existing:clean_trade:" + context.getDataSourceKey();

        metrics.stampCacheLookupStarted();
        Set<Long> existingIds = existingIdsCache.getExistingIds(cacheKey, () ->
                cleanTradeMapper.selectList(new LambdaQueryWrapper<CleanTrade>()
                                .select(CleanTrade::getSourceRowId)
                                .eq(CleanTrade::getSourceSystem, context.getDataSourceKey()))
                        .stream().map(CleanTrade::getSourceRowId).collect(Collectors.toSet()));
        metrics.stampCacheLookupFinished();
        List<CleanTrade> toInsert = new ArrayList<>();
        List<CleanTrade> toUpdate = new ArrayList<>();
        for (CleanTrade target : targets) {
            if (existingIds.contains(target.getSourceRowId())) {
                toUpdate.add(target);
            } else {
                toInsert.add(target);
            }
        }


        if (!toInsert.isEmpty()) {
            metrics.stampInsertStarted();
            cleanTradeMapper.insert(toInsert);
            metrics.stampInsertFinished(toInsert.size());
            metrics.stampCacheAddStarted();
            existingIdsCache.addNewIds(cacheKey,
                    toInsert.stream().map(CleanTrade::getSourceRowId).collect(Collectors.toList()));
            metrics.stampCacheAddFinished();
        }

        if (!toUpdate.isEmpty()) {
            Map<Long, Long> idMap = new HashMap<>();
            metrics.stampGlobalIdQueryStarted();
            cleanTradeMapper.selectList(new LambdaQueryWrapper<CleanTrade>()
                            .select(CleanTrade::getGlobalId, CleanTrade::getSourceRowId)
                            .eq(CleanTrade::getSourceSystem, context.getDataSourceKey())
                            .in(CleanTrade::getSourceRowId,
                                    toUpdate.stream().map(CleanTrade::getSourceRowId).collect(Collectors.toList())))
                    .forEach(e -> idMap.put(e.getSourceRowId(), e.getGlobalId()));
            metrics.stampGlobalIdQueryFinished();
            metrics.stampSetIdStarted();
            for (CleanTrade target : toUpdate) {
                target.setGlobalId(idMap.get(target.getSourceRowId()));
            }
            metrics.stampSetIdFinished();
            metrics.stampUpdateStarted();
            cleanTradeMapper.updateById(toUpdate);
            metrics.stampUpdateFinished(toUpdate.size());
        }
    }

    /**
     * 解析交易方向：OMS 用 B/S，Broker 用 1/2
     */
    private String resolveTradeDirection(String datasourceType, String rawDirection) {
        if (HubConstants.TYPE_TRADE_OMS.equals(datasourceType)) {
            return "B".equalsIgnoreCase(rawDirection) ? "BUY" : "SELL";
        }
        return "1".equals(rawDirection) ? "BUY" : "SELL";
    }

    /**
     * 通过字典服务翻译交易状态码
     */
    private String resolveTradeStatus(String datasourceType, String rawStatus) {
        if (HubConstants.TYPE_TRADE_OMS.equals(datasourceType)) {
            return dictionaryService.translate("trade_status_oms", rawStatus);
        }
        return dictionaryService.translate("trade_status_broker", rawStatus);
    }

    /**
     * 构造清洗记录上下文
     */
    private CleanRecordContext cleanRecordContext(BusinessSyncContext context, Long sourceRowId) {
        return new CleanRecordContext(
                nextId("clean_trade"),
                context.getDataSourceKey(), context.getDatasourceType(),
                sourceRowId, context.getBatchNo(), now());
    }

    /**
     * 交易中间行（统一 OMS/Broker 的字段差异）
     */
    @Data
    @AllArgsConstructor
    static class TradeRow {
        private Long id;
        private String bizNo;
        private String counterpartyName;
        private String directionCode;
        private BigDecimal amount;
        private String statusCode;
        private LocalDateTime tradeTime;
    }
}
