package com.example.dynamicds.bootstrap;

import com.example.dynamicds.datasource.RoutingMybatisExecutor;
import com.example.dynamicds.entity.BrokerFundAccount;
import com.example.dynamicds.service.LeafSegmentService;
import com.example.dynamicds.entity.BrokerPositionBalance;
import com.example.dynamicds.entity.BrokerStockQuote;
import com.example.dynamicds.entity.BrokerTradeDeal;
import com.example.dynamicds.entity.OmsCashAsset;
import com.example.dynamicds.entity.OmsPositionHolding;
import com.example.dynamicds.entity.OmsStockSnapshot;
import com.example.dynamicds.entity.OmsTradeOrder;
import com.example.dynamicds.mapper.BrokerFundAccountMapper;
import com.example.dynamicds.mapper.BrokerPositionBalanceMapper;
import com.example.dynamicds.mapper.BrokerStockQuoteMapper;
import com.example.dynamicds.mapper.BrokerTradeDealMapper;
import com.example.dynamicds.mapper.DynamicSqlMapper;
import com.example.dynamicds.mapper.OmsCashAssetMapper;
import com.example.dynamicds.mapper.OmsPositionHoldingMapper;
import com.example.dynamicds.mapper.OmsStockSnapshotMapper;
import com.example.dynamicds.mapper.OmsTradeOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * 演示数据播种器 — 清空业务数据后灌入 mock 数据。
 * 由 InitDataTaskService 调用，不参与启动流程。
 */
@Slf4j
@Service
public class DemoDataSeeder {

    private static final List<String> OMS_STATUSES = java.util.Arrays.asList("NEW", "DONE", "CANCEL");
    private static final List<String> BROKER_STATUSES = java.util.Arrays.asList("A", "S", "X");
    private static final List<String> OMS_ACCOUNTS = java.util.Arrays.asList("量化一号", "量化二号", "多因子策略", "中性策略", "高频策略");
    private static final List<String> BROKER_CLIENTS = java.util.Arrays.asList("华泰资管账户", "中信机构账户", "国君量化账户", "招商自营账户", "东方财富量化户");
    private static final int OMS_ORDER_REPEAT = 3;
    private static final int BROKER_DEAL_REPEAT = 4;
    private static final int BOOTSTRAP_PROGRESS_STEP = 200;

    private static final String TAG_OMS_SNAPSHOT = "oms_stock_snapshot";
    private static final String TAG_OMS_ORDER = "oms_trade_order";
    private static final String TAG_OMS_POSITION = "oms_position_holding";
    private static final String TAG_OMS_CASH = "oms_cash_asset";
    private static final String TAG_BROKER_QUOTE = "broker_stock_quote";
    private static final String TAG_BROKER_DEAL = "broker_trade_deal";
    private static final String TAG_BROKER_POSITION = "broker_position_balance";
    private static final String TAG_BROKER_FUND = "broker_fund_account";

    private final RoutingMybatisExecutor routingMybatisExecutor;
    private final LeafSegmentService leafSegmentService;
    private final DynamicSqlMapper dynamicSqlMapper;
    private final OmsStockSnapshotMapper omsStockSnapshotMapper;
    private final OmsTradeOrderMapper omsTradeOrderMapper;
    private final OmsPositionHoldingMapper omsPositionHoldingMapper;
    private final OmsCashAssetMapper omsCashAssetMapper;
    private final BrokerStockQuoteMapper brokerStockQuoteMapper;
    private final BrokerTradeDealMapper brokerTradeDealMapper;
    private final BrokerPositionBalanceMapper brokerPositionBalanceMapper;
    private final BrokerFundAccountMapper brokerFundAccountMapper;

    public DemoDataSeeder(RoutingMybatisExecutor routingMybatisExecutor,
                          LeafSegmentService leafSegmentService,
                          DynamicSqlMapper dynamicSqlMapper,
                          OmsStockSnapshotMapper omsStockSnapshotMapper,
                          OmsTradeOrderMapper omsTradeOrderMapper,
                          OmsPositionHoldingMapper omsPositionHoldingMapper,
                          OmsCashAssetMapper omsCashAssetMapper,
                          BrokerStockQuoteMapper brokerStockQuoteMapper,
                          BrokerTradeDealMapper brokerTradeDealMapper,
                          BrokerPositionBalanceMapper brokerPositionBalanceMapper,
                          BrokerFundAccountMapper brokerFundAccountMapper) {
        this.routingMybatisExecutor = routingMybatisExecutor;
        this.leafSegmentService = leafSegmentService;
        this.dynamicSqlMapper = dynamicSqlMapper;
        this.omsStockSnapshotMapper = omsStockSnapshotMapper;
        this.omsTradeOrderMapper = omsTradeOrderMapper;
        this.omsPositionHoldingMapper = omsPositionHoldingMapper;
        this.omsCashAssetMapper = omsCashAssetMapper;
        this.brokerStockQuoteMapper = brokerStockQuoteMapper;
        this.brokerTradeDealMapper = brokerTradeDealMapper;
        this.brokerPositionBalanceMapper = brokerPositionBalanceMapper;
        this.brokerFundAccountMapper = brokerFundAccountMapper;
    }

    public synchronized Map<String, Object> initDemoData() {
        log.info("[DemoSeeder] 开始初始化演示数据");
        clearBusinessDataTables();
        leafSegmentService.clearLocalCache();
        List<StockSnapshot> snapshots = generateFallbackStocks();
        seedTradeSystemsFromMarketData(snapshots);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("snapshotCount", snapshots.size());
        log.info("[DemoSeeder] 完成, count={}", snapshots.size());
        return result;
    }

    public synchronized Map<String, Object> initDemoDataWithProgress(IntConsumer progressCallback) {
        log.info("[DemoSeeder] 开始初始化演示数据（带进度）");
        progressCallback.accept(0);
        clearBusinessDataTables();
        progressCallback.accept(3);
        leafSegmentService.clearLocalCache();
        progressCallback.accept(5);
        List<StockSnapshot> snapshots = generateFallbackStocks();
        progressCallback.accept(15);
        seedOmsSystemsWithProgress(snapshots, progressCallback, 15, 60);
        seedBrokerSystemsWithProgress(snapshots, progressCallback, 60, 95);
        progressCallback.accept(98);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("snapshotCount", snapshots.size());
        progressCallback.accept(100);
        log.info("[DemoSeeder] 完成, count={}", snapshots.size());
        return result;
    }

    // ============ 清空 ============

    private void clearBusinessDataTables() {
        clearOmsTradeTables();
        clearBrokerTradeTables();
        clearCleanTables();
    }

    private void clearOmsTradeTables() {
        routingMybatisExecutor.run(HubConstants.DS_TRADE_OMS, () -> {
            executeSql("truncate table oms_cash_asset");
            executeSql("truncate table oms_position_holding");
            executeSql("truncate table oms_trade_order");
            executeSql("truncate table oms_stock_snapshot");
        });
    }

    private void clearBrokerTradeTables() {
        routingMybatisExecutor.run(HubConstants.DS_TRADE_BROKER, () -> {
            executeSql("truncate table broker_fund_account");
            executeSql("truncate table broker_position_balance");
            executeSql("truncate table broker_trade_deal");
            executeSql("truncate table broker_stock_quote");
        });
    }

    private void clearCleanTables() {
        routingMybatisExecutor.run(HubConstants.DS_HUB, () -> {
            executeSql("truncate table clean_asset");
            executeSql("truncate table clean_position");
            executeSql("truncate table clean_trade");
            executeSql("truncate table clean_stock");
            executeSql("truncate table event_message");
            executeSql("truncate table tx_coordination_log");
        });
    }

    // ============ 播种 ============

    private void seedTradeSystemsFromMarketData(List<StockSnapshot> snapshots) {
        log.info("[DemoSeeder] 写入OMS, size={}", snapshots.size());
        routingMybatisExecutor.run(HubConstants.DS_TRADE_OMS, () -> {
            for (int i = 0; i < snapshots.size(); i++) {
                StockSnapshot s = snapshots.get(i);
                insertOmsStockSnapshot(s);
                for (int j = 0; j < OMS_ORDER_REPEAT; j++) insertOmsTradeSeed(s, j);
                insertOmsPositionSeed(s);
                if (i % 3 == 0) insertOmsCashSeed(s);
                logSeedProgress("OMS", i + 1, snapshots.size());
            }
        });
        log.info("[DemoSeeder] 写入Broker, size={}", snapshots.size());
        routingMybatisExecutor.run(HubConstants.DS_TRADE_BROKER, () -> {
            for (int i = 0; i < snapshots.size(); i++) {
                StockSnapshot s = snapshots.get(i);
                insertBrokerStockQuote(s);
                for (int j = 0; j < BROKER_DEAL_REPEAT; j++) insertBrokerTradeSeed(s, j);
                insertBrokerPositionSeed(s);
                if (i % 3 == 0) insertBrokerFundSeed(s);
                logSeedProgress("Broker", i + 1, snapshots.size());
            }
        });
    }

    private void seedOmsSystemsWithProgress(List<StockSnapshot> snapshots, IntConsumer pc, int ps, int pe) {
        int total = snapshots.size();
        routingMybatisExecutor.run(HubConstants.DS_TRADE_OMS, () -> {
            for (int i = 0; i < total; i++) {
                StockSnapshot s = snapshots.get(i);
                insertOmsStockSnapshot(s);
                for (int j = 0; j < OMS_ORDER_REPEAT; j++) insertOmsTradeSeed(s, j);
                insertOmsPositionSeed(s);
                if (i % 3 == 0) insertOmsCashSeed(s);
                pc.accept(ps + (int) ((i + 1) / (double) total * (pe - ps)));
            }
        });
    }

    private void seedBrokerSystemsWithProgress(List<StockSnapshot> snapshots, IntConsumer pc, int ps, int pe) {
        int total = snapshots.size();
        routingMybatisExecutor.run(HubConstants.DS_TRADE_BROKER, () -> {
            for (int i = 0; i < total; i++) {
                StockSnapshot s = snapshots.get(i);
                insertBrokerStockQuote(s);
                for (int j = 0; j < BROKER_DEAL_REPEAT; j++) insertBrokerTradeSeed(s, j);
                insertBrokerPositionSeed(s);
                if (i % 3 == 0) insertBrokerFundSeed(s);
                pc.accept(ps + (int) ((i + 1) / (double) total * (pe - ps)));
            }
        });
    }

    // ============ Insert ============

    private void insertOmsStockSnapshot(StockSnapshot snapshot) {
        OmsStockSnapshot e = new OmsStockSnapshot();
        e.setId(leafSegmentService.nextId(TAG_OMS_SNAPSHOT));
        e.setSymbol(snapshot.getSymbol());
        e.setExchangeCode(defaultExchange(snapshot));
        e.setMarketDay(normalizeTradeDay(snapshot.getDate()));
        e.setOpenPrice(resolvePrice(snapshot.getOpen()));
        e.setHighPrice(resolvePrice(snapshot.getHigh()));
        e.setLowPrice(resolvePrice(snapshot.getLow()));
        e.setClosePrice(resolvePrice(snapshot.getClose()));
        e.setVolumeQty(resolveVolume(snapshot, 0));
        e.setTurnoverAmount(resolveTurnover(snapshot, resolveVolume(snapshot, 0)));
        e.setSyncFlag(0);
        omsStockSnapshotMapper.insert(e);
    }

    private void insertOmsTradeSeed(StockSnapshot snapshot, int repeat) {
        long qty = resolveTradeQty(snapshot, 0, repeat);
        BigDecimal price = resolveTradePrice(snapshot, repeat);
        OmsTradeOrder e = new OmsTradeOrder();
        e.setId(leafSegmentService.nextId(TAG_OMS_ORDER));
        e.setOrderNo("OMS-" + snapshot.getSymbol() + "-" + String.format("%05d", repeat));
        e.setStockCode(snapshot.getSymbol());
        e.setInvestorName(OMS_ACCOUNTS.get(repeat % OMS_ACCOUNTS.size()));
        e.setSideCode(repeat % 2 == 0 ? "S" : "B");
        e.setTradeQty(qty);
        e.setTradePrice(price);
        e.setOrderAmount(price.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP));
        e.setTradeStatus(OMS_STATUSES.get(repeat % OMS_STATUSES.size()));
        e.setTradeTime(normalizeTradeTime(snapshot.getDate()));
        e.setSyncFlag(0);
        omsTradeOrderMapper.insert(e);
    }

    private void insertOmsPositionSeed(StockSnapshot snapshot) {
        long qty = resolvePositionQty(snapshot, 0);
        BigDecimal price = resolveTradePrice(snapshot, 1);
        OmsPositionHolding e = new OmsPositionHolding();
        e.setId(leafSegmentService.nextId(TAG_OMS_POSITION));
        e.setInvestorName(OMS_ACCOUNTS.get(0));
        e.setStockCode(snapshot.getSymbol());
        e.setHoldingQty(qty);
        e.setAvailableQty(Math.max(100L, qty - 100L));
        e.setCostPrice(price);
        e.setMarketValue(price.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP));
        e.setStatDay(normalizeTradeDay(snapshot.getDate()));
        e.setSyncFlag(0);
        omsPositionHoldingMapper.insert(e);
    }

    private void insertOmsCashSeed(StockSnapshot snapshot) {
        BigDecimal base = resolveTradePrice(snapshot, 2).multiply(BigDecimal.valueOf(10000));
        OmsCashAsset e = new OmsCashAsset();
        e.setId(leafSegmentService.nextId(TAG_OMS_CASH));
        e.setInvestorName(OMS_ACCOUNTS.get(0));
        e.setAccountNo("OMS-ACCT-0001");
        e.setCashBalance(base.setScale(2, RoundingMode.HALF_UP));
        e.setFrozenBalance(base.multiply(BigDecimal.valueOf(0.08)).setScale(2, RoundingMode.HALF_UP));
        e.setTotalAsset(base.multiply(BigDecimal.valueOf(1.75)).setScale(2, RoundingMode.HALF_UP));
        e.setStatDay(normalizeTradeDay(snapshot.getDate()));
        e.setSyncFlag(0);
        omsCashAssetMapper.insert(e);
    }

    private void insertBrokerStockQuote(StockSnapshot snapshot) {
        long volume = resolveVolume(snapshot, 17);
        BrokerStockQuote e = new BrokerStockQuote();
        e.setId(leafSegmentService.nextId(TAG_BROKER_QUOTE));
        e.setQuoteCode(snapshot.getSymbol() + "-" + normalizeTradeDay(snapshot.getDate()));
        e.setSecuCode(snapshot.getSymbol());
        e.setTradeDay(normalizeTradeDay(snapshot.getDate()));
        e.setExchangeName(defaultExchange(snapshot));
        e.setOpenPx(resolvePrice(snapshot.getOpen()));
        e.setHighPx(resolvePrice(snapshot.getHigh()));
        e.setLowPx(resolvePrice(snapshot.getLow()));
        e.setClosePx(resolvePrice(snapshot.getClose()));
        e.setVolNum(volume);
        e.setTurnoverAmt(resolveTurnover(snapshot, volume));
        e.setSyncFlag(0);
        brokerStockQuoteMapper.insert(e);
    }

    private void insertBrokerTradeSeed(StockSnapshot snapshot, int repeat) {
        long qty = resolveTradeQty(snapshot, 11, repeat + 1);
        BigDecimal price = resolveTradePrice(snapshot, repeat + 1);
        BrokerTradeDeal e = new BrokerTradeDeal();
        e.setId(leafSegmentService.nextId(TAG_BROKER_DEAL));
        e.setDealCode("BRK-" + snapshot.getSymbol() + "-" + String.format("%05d", repeat));
        e.setSecuCode(snapshot.getSymbol());
        e.setClientFullName(BROKER_CLIENTS.get(repeat % BROKER_CLIENTS.size()));
        e.setBsFlag(repeat % 2 == 0 ? "2" : "1");
        e.setDealVolume(qty);
        e.setDealPrice(price);
        e.setTurnoverAmount(price.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP));
        e.setStatusMark(BROKER_STATUSES.get(repeat % BROKER_STATUSES.size()));
        e.setDealAt(normalizeTradeTime(snapshot.getDate()));
        e.setSyncFlag(0);
        brokerTradeDealMapper.insert(e);
    }

    private void insertBrokerPositionSeed(StockSnapshot snapshot) {
        long qty = resolvePositionQty(snapshot, 5);
        BigDecimal price = resolveTradePrice(snapshot, 2);
        BrokerPositionBalance e = new BrokerPositionBalance();
        e.setId(leafSegmentService.nextId(TAG_BROKER_POSITION));
        e.setClientFullName(BROKER_CLIENTS.get(0));
        e.setSecuCode(snapshot.getSymbol());
        e.setCurrentVolume(qty);
        e.setEnableVolume(Math.max(100L, qty - 200L));
        e.setCostPx(price);
        e.setMarketAmt(price.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP));
        e.setBizDate(normalizeTradeDay(snapshot.getDate()));
        e.setSyncFlag(0);
        brokerPositionBalanceMapper.insert(e);
    }

    private void insertBrokerFundSeed(StockSnapshot snapshot) {
        BigDecimal base = resolveTradePrice(snapshot, 3).multiply(BigDecimal.valueOf(12000));
        BrokerFundAccount e = new BrokerFundAccount();
        e.setId(leafSegmentService.nextId(TAG_BROKER_FUND));
        e.setClientFullName(BROKER_CLIENTS.get(0));
        e.setFundAccountNo("BRK-ACCT-0001");
        e.setCurrentBalance(base.setScale(2, RoundingMode.HALF_UP));
        e.setFrozenCapital(base.multiply(BigDecimal.valueOf(0.10)).setScale(2, RoundingMode.HALF_UP));
        e.setTotalAsset(base.multiply(BigDecimal.valueOf(1.65)).setScale(2, RoundingMode.HALF_UP));
        e.setBizDate(normalizeTradeDay(snapshot.getDate()));
        e.setSyncFlag(0);
        brokerFundAccountMapper.insert(e);
    }

    private void logSeedProgress(String sys, int cur, int total) {
        if (cur == total || cur % BOOTSTRAP_PROGRESS_STEP == 0) {
            log.info("[DemoSeeder] {} {}/{}", sys, cur, total);
        }
    }

    // ============ 工具 ============

    private void executeSql(String sql) { dynamicSqlMapper.executeSql(sql); }

    static final Map<String, StockSnapshot> FALLBACK_MAP = new LinkedHashMap<>();
    static {
        FALLBACK_MAP.put("AAPL", new StockSnapshot("AAPL", "NASDAQ", LocalDate.of(2024, 12, 16), "150.00", "152.00", "149.00", "151.50", "10000000"));
        FALLBACK_MAP.put("GOOGL", new StockSnapshot("GOOGL", "NASDAQ", LocalDate.of(2024, 12, 16), "130.00", "132.00", "129.00", "131.50", "8000000"));
    }

    private List<StockSnapshot> generateFallbackStocks() { return new ArrayList<>(FALLBACK_MAP.values()); }

    private BigDecimal resolvePrice(Object val) {
        try { return new BigDecimal(String.valueOf(val)); } catch (Exception e) { return new BigDecimal("100.00"); }
    }

    private long resolveVolume(StockSnapshot s, int offset) {
        try { return Long.parseLong(s.getVolume()) + offset; } catch (Exception e) { return 1000000 + offset; }
    }

    private long resolveTradeQty(StockSnapshot s, int offset, int repeat) { return resolveVolume(s, offset) / (repeat + 1) / 100; }
    private long resolvePositionQty(StockSnapshot s, int offset) { return resolveVolume(s, offset) / 10; }

    private BigDecimal resolveTradePrice(StockSnapshot s, int offset) {
        BigDecimal p = resolvePrice(s.getClose());
        return p.add(BigDecimal.valueOf(offset * 0.01)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveTurnover(StockSnapshot s, long volume) {
        return resolvePrice(s.getClose()).multiply(BigDecimal.valueOf(volume)).setScale(2, RoundingMode.HALF_UP);
    }

    private String defaultExchange(StockSnapshot s) { return s.getExchange() != null ? s.getExchange() : "SSE"; }
    private String normalizeTradeDay(LocalDate d) { return d.format(DateTimeFormatter.ofPattern("yyyyMMdd")); }
    private String normalizeTradeTime(LocalDate d) { return d.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + " 09:30:00"; }

    static class StockSnapshot {
        private final String symbol, exchange, open, high, low, close, volume;
        private final LocalDate date;

        StockSnapshot(String symbol, String exchange, LocalDate date, String open, String high, String low, String close, String volume) {
            this.symbol = symbol; this.exchange = exchange; this.date = date;
            this.open = open; this.high = high; this.low = low; this.close = close; this.volume = volume;
        }
        String getSymbol() { return symbol; }
        String getExchange() { return exchange; }
        LocalDate getDate() { return date; }
        String getOpen() { return open; }
        String getHigh() { return high; }
        String getLow() { return low; }
        String getClose() { return close; }
        String getVolume() { return volume; }
    }
}
