package com.example.redis;

import com.example.redis.c01_core.CoreConceptsDemo;
import com.example.redis.c02_datatypes.DataTypeDemo;
import com.example.redis.c03_keyops.KeyOpsDemo;
import com.example.redis.c04_typecmds.*;
import com.example.redis.c05_persistence.PersistenceDemo;
import com.example.redis.c06_replication.ReplicationDemo;
import com.example.redis.c07_sentinel.SentinelDemo;
import com.example.redis.c08_cluster.ClusterDemo;
import com.example.redis.c09_transaction.TransactionDemo;
import com.example.redis.c10_lua.LuaDemo;
import com.example.redis.c11_pubsub.PubSubDemo;
import com.example.redis.c12_stream.StreamDemo;
import com.example.redis.c13_pipeline.PipelineDemo;
import com.example.redis.c14_memory.MemoryDemo;
import com.example.redis.c15_monitor.MonitorDemo;
import com.example.redis.c16_security.SecurityDemo;
import com.example.redis.c17_performance.PerformanceDemo;
import com.example.redis.c18_cache.CacheDemo;
import com.example.redis.c19_modules.ModulesDemo;
import com.example.redis.c20_protocol.ProtocolDemo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Redis 全知识点演示控制器
 * <p>
 * 通过 HTTP 接口触发各个知识点的演示。
 * 启动应用后访问: http://localhost:8080/demo/{section}/{topic}
 */
@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
public class DemoController {

    private final CoreConceptsDemo coreConceptsDemo;
    private final DataTypeDemo dataTypeDemo;
    private final KeyOpsDemo keyOpsDemo;
    private final StringCmdDemo stringCmdDemo;
    private final HashCmdDemo hashCmdDemo;
    private final ListCmdDemo listCmdDemo;
    private final SetCmdDemo setCmdDemo;
    private final ZSetCmdDemo zSetCmdDemo;
    private final BitCmdDemo bitCmdDemo;
    private final HyperLogLogDemo hyperLogLogDemo;
    private final GeoCmdDemo geoCmdDemo;
    private final StreamCmdDemo streamCmdDemo;
    private final TransactionDemo transactionDemo;
    private final LuaDemo luaDemo;
    private final PubSubDemo pubSubDemo;
    private final StreamDemo streamDemo;
    private final PipelineDemo pipelineDemo;
    private final PersistenceDemo persistenceDemo;
    private final ReplicationDemo replicationDemo;
    private final SentinelDemo sentinelDemo;
    private final ClusterDemo clusterDemo;
    private final MemoryDemo memoryDemo;
    private final MonitorDemo monitorDemo;
    private final SecurityDemo securityDemo;
    private final PerformanceDemo performanceDemo;
    private final CacheDemo cacheDemo;
    private final ModulesDemo modulesDemo;
    private final ProtocolDemo protocolDemo;

    // ==================== 1. 核心概念 ====================
    @GetMapping("/core/ping")
    public String corePing() { return coreConceptsDemo.ping(); }

    @GetMapping("/core/db")
    public String coreDb() { return coreConceptsDemo.databaseSelect(); }

    @GetMapping("/core/info")
    public String coreInfo() { return coreConceptsDemo.serverInfo(); }

    // ==================== 2. 数据类型 ====================
    @GetMapping("/datatype/string")
    public String dtString() { return dataTypeDemo.stringEncoding(); }

    @GetMapping("/datatype/hash")
    public String dtHash() { return dataTypeDemo.hashEncoding(); }

    @GetMapping("/datatype/set")
    public String dtSet() { return dataTypeDemo.setEncoding(); }

    @GetMapping("/datatype/zset")
    public String dtZset() { return dataTypeDemo.zsetEncoding(); }

    @GetMapping("/datatype/list")
    public String dtList() { return dataTypeDemo.listEncoding(); }

    // ==================== 3. 键操作 ====================
    @GetMapping("/key/basic")
    public String keyBasic() { return keyOpsDemo.basicKeyOps(); }

    @GetMapping("/key/scan")
    public String keyScan() { return keyOpsDemo.scanDemo(); }

    @GetMapping("/key/expire")
    public String keyExpire() { return keyOpsDemo.expireDemo(); }

    @GetMapping("/key/rename")
    public String keyRename() { return keyOpsDemo.renameCopyDemo(); }

    @GetMapping("/key/object")
    public String keyObject() { return keyOpsDemo.objectInfoDemo(); }

    @GetMapping("/key/flush")
    public String keyFlush() { return keyOpsDemo.flushDbDemo(); }

    // ==================== 4. 各类型命令 ====================
    @GetMapping("/cmd/string/basic")
    public String cmdStringBasic() { return stringCmdDemo.basicOps(); }

    @GetMapping("/cmd/string/counter")
    public String cmdStringCounter() { return stringCmdDemo.counterOps(); }

    @GetMapping("/cmd/string/ops")
    public String cmdStringOps() { return stringCmdDemo.stringManipulation(); }

    @GetMapping("/cmd/string/lock")
    public String cmdStringLock() { return stringCmdDemo.distributedLock(); }

    @GetMapping("/cmd/hash/basic")
    public String cmdHashBasic() { return hashCmdDemo.basicOps(); }

    @GetMapping("/cmd/hash/counter")
    public String cmdHashCounter() { return hashCmdDemo.counterAndScan(); }

    @GetMapping("/cmd/hash/object")
    public String cmdHashObject() { return hashCmdDemo.objectStorage(); }

    @GetMapping("/cmd/list/basic")
    public String cmdListBasic() { return listCmdDemo.basicOps(); }

    @GetMapping("/cmd/list/blocking")
    public String cmdListBlocking() { return listCmdDemo.blockingPop(); }

    @GetMapping("/cmd/list/advanced")
    public String cmdListAdvanced() { return listCmdDemo.advancedOps(); }

    @GetMapping("/cmd/set/basic")
    public String cmdSetBasic() { return setCmdDemo.basicOps(); }

    @GetMapping("/cmd/set/ops")
    public String cmdSetOps() { return setCmdDemo.setOperations(); }

    @GetMapping("/cmd/set/lottery")
    public String cmdSetLottery() { return setCmdDemo.lotteryDemo(); }

    @GetMapping("/cmd/zset/basic")
    public String cmdZsetBasic() { return zSetCmdDemo.basicOps(); }

    @GetMapping("/cmd/zset/range")
    public String cmdZsetRange() { return zSetCmdDemo.rangeQuery(); }

    @GetMapping("/cmd/zset/delay")
    public String cmdZsetDelay() { return zSetCmdDemo.delayQueue(); }

    @GetMapping("/cmd/zset/ratelimit")
    public String cmdZsetRateLimit() { return zSetCmdDemo.slidingWindowRateLimit(); }

    @GetMapping("/cmd/bitmap/basic")
    public String cmdBitmapBasic() { return bitCmdDemo.basicOps(); }

    @GetMapping("/cmd/bitmap/signin")
    public String cmdBitmapSignin() { return bitCmdDemo.userSignIn(); }

    @GetMapping("/cmd/bitmap/active")
    public String cmdBitmapActive() { return bitCmdDemo.activeUserStats(); }

    @GetMapping("/cmd/hll/basic")
    public String cmdHllBasic() { return hyperLogLogDemo.basicOps(); }

    @GetMapping("/cmd/hll/uv")
    public String cmdHllUv() { return hyperLogLogDemo.uvStats(); }

    @GetMapping("/cmd/geo/basic")
    public String cmdGeoBasic() { return geoCmdDemo.basicOps(); }

    @GetMapping("/cmd/geo/nearby")
    public String cmdGeoNearby() { return geoCmdDemo.nearbySearch(); }

    @GetMapping("/cmd/stream/basic")
    public String cmdStreamBasic() { return streamCmdDemo.basicOps(); }

    @GetMapping("/cmd/stream/group")
    public String cmdStreamGroup() { return streamCmdDemo.consumerGroup(); }

    @GetMapping("/cmd/stream/trim")
    public String cmdStreamTrim() { return streamCmdDemo.streamTrim(); }

    // ==================== 5. 持久化 ====================
    @GetMapping("/persist/info")
    public String persistInfo() { return persistenceDemo.persistenceInfo(); }

    @GetMapping("/persist/rdb")
    public String persistRdb() { return persistenceDemo.triggerRdb(); }

    @GetMapping("/persist/aof")
    public String persistAof() { return persistenceDemo.aofOperations(); }

    // ==================== 6. 复制 ====================
    @GetMapping("/repl/info")
    public String replInfo() { return replicationDemo.replicationInfo(); }

    @GetMapping("/repl/config")
    public String replConfig() { return replicationDemo.replicationConfig(); }

    @GetMapping("/repl/failover")
    public String replFailover() { return replicationDemo.failoverNotes(); }

    // ==================== 7. 哨兵 ====================
    @GetMapping("/sentinel/info")
    public String sentinelInfo() { return sentinelDemo.sentinelInfo(); }

    @GetMapping("/sentinel/cmd")
    public String sentinelCmd() { return sentinelDemo.sentinelCommands(); }

    // ==================== 8. 集群 ====================
    @GetMapping("/cluster/info")
    public String clusterInfo() { return clusterDemo.clusterInfo(); }

    @GetMapping("/cluster/hashtag")
    public String clusterHashTag() { return clusterDemo.hashTag(); }

    @GetMapping("/cluster/manage")
    public String clusterManage() { return clusterDemo.clusterManagement(); }

    // ==================== 9. 事务 ====================
    @GetMapping("/tx/basic")
    public String txBasic() { return transactionDemo.basicTransaction(); }

    @GetMapping("/tx/watch")
    public String txWatch() { return transactionDemo.watchOptimisticLock(); }

    // ==================== 10. Lua ====================
    @GetMapping("/lua/lock")
    public String luaLock() { return luaDemo.distributedLockLua(); }

    @GetMapping("/lua/stock")
    public String luaStock() { return luaDemo.atomicStockDeduct(); }

    @GetMapping("/lua/ratelimit")
    public String luaRateLimit() { return luaDemo.rateLimiterLua(); }

    @GetMapping("/lua/hash")
    public String luaHash() { return luaDemo.atomicHashIncrement(); }

    // ==================== 11. Pub/Sub ====================
    @GetMapping("/pubsub/basic")
    public String pubsubBasic() throws InterruptedException { return pubSubDemo.basicPubSub(); }

    @GetMapping("/pubsub/multi")
    public String pubsubMulti() throws InterruptedException { return pubSubDemo.multiChannel(); }

    // ==================== 12. Stream ====================
    @GetMapping("/stream/full")
    public String streamFull() { return streamDemo.fullFlow(); }

    @GetMapping("/stream/pending")
    public String streamPending() { return streamDemo.pendingRedelivery(); }

    @GetMapping("/stream/trim")
    public String streamTrimDemo() { return streamDemo.streamTrim(); }

    // ==================== 13. Pipeline ====================
    @GetMapping("/pipeline/perf")
    public String pipelinePerf() { return pipelineDemo.pipelinePerformance(); }

    @GetMapping("/pipeline/read")
    public String pipelineRead() { return pipelineDemo.pipelineBatchRead(); }

    @GetMapping("/pipeline/mixed")
    public String pipelineMixed() { return pipelineDemo.pipelineMixedOps(); }

    // ==================== 14. 内存 ====================
    @GetMapping("/memory/info")
    public String memoryInfo() { return memoryDemo.memoryInfo(); }

    @GetMapping("/memory/cmd")
    public String memoryCmd() { return memoryDemo.memoryCommands(); }

    @GetMapping("/memory/policy")
    public String memoryPolicy() { return memoryDemo.evictionPolicyGuide(); }

    // ==================== 15. 监控 ====================
    @GetMapping("/monitor/info")
    public String monitorInfo() { return monitorDemo.infoCommand(); }

    @GetMapping("/monitor/slowlog")
    public String monitorSlowLog() { return monitorDemo.slowLog(); }

    @GetMapping("/monitor/client")
    public String monitorClient() { return monitorDemo.clientInfo(); }

    // ==================== 16. 安全 ====================
    @GetMapping("/security/acl")
    public String securityAcl() { return securityDemo.aclCommands(); }

    @GetMapping("/security/dangerous")
    public String securityDangerous() { return securityDemo.dangerousCommands(); }

    @GetMapping("/security/network")
    public String securityNetwork() { return securityDemo.networkSecurity(); }

    // ==================== 17. 性能 ====================
    @GetMapping("/perf/bigkey")
    public String perfBigKey() { return performanceDemo.bigKeyDetection(); }

    @GetMapping("/perf/tips")
    public String perfTips() { return performanceDemo.performanceTips(); }

    @GetMapping("/perf/benchmark")
    public String perfBenchmark() { return performanceDemo.benchmarkGuide(); }

    // ==================== 18. 缓存 ====================
    @GetMapping("/cache/penetration")
    public String cachePenetration() { return cacheDemo.cachePenetration(); }

    @GetMapping("/cache/breakdown")
    public String cacheBreakdown() { return cacheDemo.cacheBreakdown(); }

    @GetMapping("/cache/avalanche")
    public String cacheAvalanche() { return cacheDemo.cacheAvalanche(); }

    @GetMapping("/cache/spring/{userId}")
    public String cacheSpring(@PathVariable String userId) { return cacheDemo.getUserById(userId); }

    // ==================== 19. 模块 ====================
    @GetMapping("/module/json")
    public String moduleJson() { return modulesDemo.redisJson(); }

    @GetMapping("/module/search")
    public String moduleSearch() { return modulesDemo.redisSearch(); }

    @GetMapping("/module/bloom")
    public String moduleBloom() { return modulesDemo.redisBloom(); }

    @GetMapping("/module/ts")
    public String moduleTs() { return modulesDemo.redisTimeSeries(); }

    // ==================== 20. 协议 ====================
    @GetMapping("/protocol/resp")
    public String protocolResp() { return protocolDemo.respProtocol(); }

    @GetMapping("/protocol/caching")
    public String protocolCaching() { return protocolDemo.clientCaching(); }

    @GetMapping("/protocol/conn")
    public String protocolConn() { return protocolDemo.connectionManagement(); }
}
