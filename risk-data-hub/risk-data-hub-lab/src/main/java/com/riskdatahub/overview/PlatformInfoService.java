package com.riskdatahub.overview;

import com.riskdatahub.common.constant.HubConstants;
import com.riskdatahub.datasource.DataSourceManager;
import com.riskdatahub.datasource.RoutingMybatisExecutor;
import com.riskdatahub.datasource.dto.DataSourceConfigDTO;
import com.riskdatahub.mapper.DynamicSqlMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台信息统计服务 — 提供系统拓扑、表统计等概览数据。
 *
 * @author risk-data-hub
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformInfoService {

    private final DataSourceManager dataSourceManager;
    private final RoutingMybatisExecutor routingMybatisExecutor;
    private final DynamicSqlMapper dynamicSqlMapper;

    /**
     * 获取当前系统拓扑（上游业务系统 → 中台库）。
     *
     * @return 系统拓扑 Map
     */
    public Map<String, List<String>> currentTopology() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        List<String> sources = new ArrayList<>(dataSourceManager.keys());
        sources.replaceAll(k -> {
            DataSourceConfigDTO c = dataSourceManager.getConfig(k);
            return k + " (" + (c != null ? c.getName() : "?") + ")";
        });
        map.put("上游业务系统", sources);
        map.put("中台库", Arrays.asList(HubConstants.DS_HUB));
        return map;
    }

    /**
     * 获取各上游业务系统的表记录统计。
     *
     * @return 业务表统计 Map
     */
    public Map<String, Object> currentBusinessTableStats() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put(HubConstants.DS_TRADE_OMS,
                countTables(HubConstants.DS_TRADE_OMS,
                        Arrays.asList("oms_stock_snapshot", "oms_trade_order",
                                "oms_position_holding", "oms_cash_asset")));
        r.put(HubConstants.DS_TRADE_BROKER,
                countTables(HubConstants.DS_TRADE_BROKER,
                        Arrays.asList("broker_stock_quote", "broker_trade_deal",
                                "broker_position_balance", "broker_fund_account")));
        return r;
    }

    /**
     * 获取中台库各表的记录统计。
     *
     * @return 中台表统计 Map
     */
    public Map<String, Integer> currentHubTableStats() {
        return countTables(HubConstants.DS_HUB,
                Arrays.asList("clean_stock", "clean_trade", "clean_position", "clean_asset",
                        "event_message", "sync_task", "sync_business_record"));
    }

    /** 在指定数据源上统计多个表的记录数，路由失败时返回空 Map。 */
    private Map<String, Integer> countTables(String dsKey, List<String> tables) {
        try {
            return routingMybatisExecutor.query(dsKey, () -> {
                Map<String, Integer> r = new LinkedHashMap<>();
                for (String t : tables) {
                    r.put(t, dynamicSqlMapper.countTable(t));
                }
                return r;
            });
        } catch (Exception e) {
            log.warn("[表统计] 数据源 '{}' 不可用: {}", dsKey, e.getMessage());
            return Collections.emptyMap();
        }
    }
}
