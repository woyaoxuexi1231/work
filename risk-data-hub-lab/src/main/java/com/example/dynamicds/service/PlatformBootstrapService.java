package com.example.dynamicds.service;

import com.example.dynamicds.bootstrap.HubConstants;
import com.example.dynamicds.datasource.DynamicDataSourceManager;
import com.example.dynamicds.datasource.RoutingMybatisExecutor;
import com.example.dynamicds.dto.DataSourceConfigDTO;
import com.example.dynamicds.mapper.DynamicSqlMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台统计服务 — 提供拓扑、表统计等概览数据。
 * 保留旧常量名作为转发以兼容其他服务引用。
 */
@Service
@RequiredArgsConstructor
public class PlatformBootstrapService {

    @Deprecated public static final String DS_HUB = HubConstants.DS_HUB;
    @Deprecated public static final String DS_TRADE_OMS = HubConstants.DS_TRADE_OMS;
    @Deprecated public static final String DS_TRADE_BROKER = HubConstants.DS_TRADE_BROKER;
    @Deprecated public static final String TYPE_HUB = HubConstants.TYPE_HUB;
    @Deprecated public static final String TYPE_TRADE_OMS = HubConstants.TYPE_TRADE_OMS;
    @Deprecated public static final String TYPE_TRADE_BROKER = HubConstants.TYPE_TRADE_BROKER;

    private final DynamicDataSourceManager manager;
    private final RoutingMybatisExecutor routingMybatisExecutor;
    private final DynamicSqlMapper dynamicSqlMapper;

    public Map<String, List<String>> currentTopology() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        List<String> sources = new ArrayList<>(manager.keys());
        sources.replaceAll(k -> {
            DataSourceConfigDTO c = manager.getConfig(k);
            return k + " (" + (c != null ? c.getName() : "?") + ")";
        });
        map.put("上游业务系统", sources);
        map.put("中台库", List.of(HubConstants.DS_HUB));
        return map;
    }

    public Map<String, Object> currentBusinessTableStats() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put(HubConstants.DS_TRADE_OMS, countTables(HubConstants.DS_TRADE_OMS, List.of("oms_stock_snapshot", "oms_trade_order", "oms_position_holding", "oms_cash_asset")));
        r.put(HubConstants.DS_TRADE_BROKER, countTables(HubConstants.DS_TRADE_BROKER, List.of("broker_stock_quote", "broker_trade_deal", "broker_position_balance", "broker_fund_account")));
        return r;
    }

    public Map<String, Integer> currentHubTableStats() {
        return countTables(HubConstants.DS_HUB, List.of("clean_stock", "clean_trade", "clean_position", "clean_asset", "event_message", "init_task", "sync_task", "sync_business_record"));
    }

    private Map<String, Integer> countTables(String dsKey, List<String> tables) {
        return routingMybatisExecutor.query(dsKey, () -> {
            Map<String, Integer> r = new LinkedHashMap<>();
            for (String t : tables) r.put(t, dynamicSqlMapper.countTable(t));
            return r;
        });
    }
}
