package com.example.dynamicds.service;

import com.example.dynamicds.datasource.DynamicDataSourceManager;
import com.example.dynamicds.datasource.RoutingJdbcExecutor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OverviewService {

    private final DynamicDataSourceManager manager;
    private final RoutingJdbcExecutor jdbcExecutor;
    private final PlatformBootstrapService bootstrapService;
    private final LeafSegmentService leafSegmentService;

    public OverviewService(DynamicDataSourceManager manager,
                           RoutingJdbcExecutor jdbcExecutor,
                           PlatformBootstrapService bootstrapService,
                           LeafSegmentService leafSegmentService) {
        this.manager = manager;
        this.jdbcExecutor = jdbcExecutor;
        this.bootstrapService = bootstrapService;
        this.leafSegmentService = leafSegmentService;
    }

    public Map<String, Object> overview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("project", "金融交易数据中台实验室");
        result.put("summary", "整合多个交易系统的数据，统一清洗、持久化，并向风控系统提供标准化数据底座。");
        result.put("topology", bootstrapService.currentTopology());
        result.put("datasourceCount", manager.keys().size());
        result.put("cleanTradeCount", countFromWarehouse("select count(1) from clean_trade"));
        result.put("eventCount", countFromWarehouse("select count(1) from event_message"));
        result.put("architectureAnswers", List.of(
                "AbstractRoutingDataSource 的局限在于 resolvedDataSources 只在 afterPropertiesSet 时初始化一次，默认并不支持运行时增删；这里通过外部 ConcurrentHashMap 托管数据源、写锁刷新 targetDataSources，并暴露 manager.register/remove 做热更新。",
                "跨多数据源事务没有天然的本地事务一致性，我这里保留了你之前的做法：双线程各自持有本地连接，先 prepare，再由总控线程通过信号量统一放行 commit / rollback。",
                "数据清洗线程池按业务类型拆分，一个交易品类一个线程，初始化清洗和交易中实时清洗都共用这套模型，既隔离慢任务也便于看日志。",
                "Leaf-segment 的瓶颈在中心库那一行 leaf_alloc 的 for update 行锁；优化点是增大 step、在号段剩余 20% 时异步预加载 next buffer，用双 Buffer 把大多数请求挡在内存里。"
        ));
        result.put("leafState", leafSegmentService.state("clean_trade"));
        return result;
    }

    private Integer countFromWarehouse(String sql) {
        return jdbcExecutor.query(PlatformBootstrapService.DS_WAREHOUSE, jdbc ->
                jdbc.queryForObject(sql, Integer.class));
    }
}
