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
     * 获取当前系统拓扑。
     * <p>返回 Map：{ "上游业务系统": [...], "中台库": [...] }。
     * 每个数据源显示为 "key (名称)" 格式。</p>
     *
     * @return 系统拓扑 Map
     */
    public Map<String, List<String>> currentTopology() {
        // ----- 获取所有已注册数据源（含中台库），格式化为 "key (名称)" -----
        Map<String, List<String>> map = new LinkedHashMap<>();
        List<String> sources = new ArrayList<>(dataSourceManager.keys());
        sources.replaceAll(k -> {
            DataSourceConfigDTO c = dataSourceManager.getConfig(k);
            return k + " (" + (c != null ? c.getName() : "?") + ")";
        });
        map.put("上游业务系统", sources);
        // 中台库固定只有一个 risk_hub
        map.put("中台库", Arrays.asList(HubConstants.DS_HUB));
        return map;
    }

    /**
     * 获取上游业务系统的表记录统计。
     * <p>目前统计 trade_oms 和 trade_broker 两个数据源的关键表。</p>
     *
     * @return 数据源名称到各表行数的 Map
     */
    public Map<String, Object> currentBusinessTableStats() {
        Map<String, Object> r = new LinkedHashMap<>();
        // trade_oms 数据源：4 张核心表（库存快照、交易订单、持仓、资金）
        r.put(HubConstants.DS_TRADE_OMS,
                countTables(HubConstants.DS_TRADE_OMS,
                        Arrays.asList("oms_stock_snapshot", "oms_trade_order",
                                "oms_position_holding", "oms_cash_asset")));
        // trade_broker 数据源：4 张核心表（行情、成交、持仓余额、资金账户）
        r.put(HubConstants.DS_TRADE_BROKER,
                countTables(HubConstants.DS_TRADE_BROKER,
                        Arrays.asList("broker_stock_quote", "broker_trade_deal",
                                "broker_position_balance", "broker_fund_account")));
        return r;
    }

    /**
     * 获取中台库各表的记录统计。
     * <p>统计中台核心表：清洗表（stock/trade/position/asset）+ 事件消息 + 同步任务 + 同步记录。</p>
     *
     * @return 表名到行数的 Map
     */
    public Map<String, Integer> currentHubTableStats() {
        return countTables(HubConstants.DS_HUB,
                Arrays.asList("clean_stock", "clean_trade", "clean_position", "clean_asset",
                        "event_message", "sync_task", "sync_business_record"));
    }

    // ============================================================
    // 4. 在指定数据源上统计多个表的记录数
    // 路由到目标数据源执行 SELECT COUNT(*)，失败时返回空 Map（不阻塞页面）
    // ============================================================
    private Map<String, Integer> countTables(String dsKey, List<String> tables) {
        try {
            // 切换到目标数据源执行 count 查询
            return routingMybatisExecutor.query(dsKey, () -> {
                Map<String, Integer> r = new LinkedHashMap<>();
                for (String t : tables) {
                    r.put(t, dynamicSqlMapper.countTable(t));
                }
                return r;
            });
        } catch (Exception e) {
            // 数据源不可用时（如未注册），只打日志不抛异常，页面其他区域正常显示
            log.warn("[表统计] 数据源 '{}' 不可用: {}", dsKey, e.getMessage());
            return Collections.emptyMap();
        }
    }
}
