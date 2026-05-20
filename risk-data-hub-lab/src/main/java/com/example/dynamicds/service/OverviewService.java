package com.example.dynamicds.service;

import com.example.dynamicds.datasource.RoutingMybatisExecutor;
import com.example.dynamicds.mapper.CleanTradeMapper;
import com.example.dynamicds.datasource.DynamicDataSourceManager;
import com.example.dynamicds.mapper.EventMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OverviewService {

    private final DynamicDataSourceManager manager;
    private final RoutingMybatisExecutor routingMybatisExecutor;
    private final PlatformBootstrapService bootstrapService;
    private final LeafSegmentService leafSegmentService;
    private final CleanTradeMapper cleanTradeMapper;
    private final EventMessageMapper eventMessageMapper;

    public Map<String, Object> overview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("project", "精简版数据中台同步实验室");
        result.put("summary", "对接多个交易系统库，启动时先灌大量股票基础数据，再派生多张业务表，便于后续多线程压测。");
        result.put("topology", bootstrapService.currentTopology());
        result.put("businessTableStats", bootstrapService.currentBusinessTableStats());
        result.put("datasourceCount", manager.keys().size());
        result.put("cleanTradeCount", countFromHub("clean_trade"));
        result.put("eventCount", countFromHub("event_message"));
        result.put("architectureAnswers", List.of(
                "动态数据源只保留最核心的维护能力：查看、注册、删除，并在注册时带上 datasourceType。",
                "ETL 主流程固定为：指定数据源 -> 分页拉取未同步数据 -> 按 datasourceType 转换 -> 落中台库 -> 回写同步标记。",
                "两个上游库本质上都是交易系统，只是字段名和状态码不一致，所以中台必须按来源类型分别做字段映射和状态转换。",
                "启动灌数不再只塞一张交易表，而是同时生成股票主表、交易表、持仓表、资金表，方便你测试多表并发。",
                "Leaf 依然留在中台库里给 clean_trade 和 event_message 发全局 ID，方便你后面继续扩展。"
        ));
        result.put("leafState", leafSegmentService.state("clean_trade"));
        return result;
    }

    private Integer countFromHub(String tableName) {
        return routingMybatisExecutor.query(PlatformBootstrapService.DS_HUB, () -> {
            if ("clean_trade".equals(tableName)) {
                return Math.toIntExact(cleanTradeMapper.selectCount(null));
            }
            return Math.toIntExact(eventMessageMapper.selectCount(null));
        });
    }
}
