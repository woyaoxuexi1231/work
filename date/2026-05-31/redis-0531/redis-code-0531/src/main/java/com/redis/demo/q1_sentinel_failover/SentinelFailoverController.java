package com.redis.demo.q1_sentinel_failover;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import redis.clients.jedis.Jedis;

import java.util.*;

/**
 * Q1: Sentinel 故障转移 —— 真实拓扑查询.
 *
 * <h3>不模拟，只观察。</h3>
 * 故障转移本身由真实 Sentinel 集群完成。
 * 你手动关停 master，然后通过这些接口观察 Sentinel 状态变化。
 *
 * <h3>演练步骤</h3>
 * <ol>
 *   <li>开 3 个终端，分别 tail -f 三个 Sentinel 的日志</li>
 *   <li>调用 GET /api/sentinel/topology 查看当前拓扑</li>
 *   <li>手动 kill master：redis-cli -h 192.168.3.100 -p 6379 -a 123456 SHUTDOWN</li>
 *   <li>观察 Sentinel 日志中 SDOWN → ODOWN → failover 的过程</li>
 *   <li>调用 GET /api/sentinel/topology 确认新主已上线</li>
 *   <li>重启旧主 → 观察日志中它变成 slave 的过程</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/sentinel")
public class SentinelFailoverController {

    private static final Logger log = LoggerFactory.getLogger(SentinelFailoverController.class);

    @Value("${spring.redis.sentinel.nodes:192.168.3.100:26379,192.168.3.100:26380,192.168.3.100:26381}")
    private String sentinelNodes;

    @Value("${spring.redis.sentinel.master:mymaster}")
    private String masterName;

    @Value("${spring.redis.password:123456}")
    private String password;

    /**
     * 【核心】查询真实 Sentinel 集群的当前拓扑.
     *
     * 每个 Sentinel 节点执行:
     *   SENTINEL MASTER mymaster    → 当前主节点信息
     *   SENTINEL SLAVES mymaster    → 所有从节点
     *   SENTINEL SENTINELS mymaster → 所有哨兵节点
     *
     * 故障转移前后各调一次，对比输出。
     */
    @GetMapping("/topology")
    public Map<String, Object> topology() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("masterName", masterName);
        result.put("queryTime", new Date().toString());

        List<Map<String, Object>> sentinelViews = new ArrayList<>();
        boolean masterFound = false;

        for (String node : sentinelNodes.split(",")) {
            String[] parts = node.trim().split(":");
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);

            Map<String, Object> view = new LinkedHashMap<>();
            view.put("sentinel", host + ":" + port);

            try (Jedis jedis = new Jedis(host, port, 3000)) {
                jedis.auth(password);

                // SENTINEL MASTER
                Map<String, String> masterMap = jedis.sentinelMaster(masterName);
                if (masterMap != null && !masterMap.isEmpty()) {
                    masterFound = true;
                    Map<String, Object> master = new LinkedHashMap<>();
                    master.put("name", masterMap.get("name"));
                    master.put("ip", masterMap.get("ip"));
                    master.put("port", masterMap.get("port"));
                    master.put("flags", masterMap.get("flags"));
                    master.put("num-slaves", masterMap.get("num-slaves"));
                    master.put("num-sentinels", masterMap.get("num-sentinels"));
                    master.put("role-reported", masterMap.get("role-reported"));
                    view.put("master", master);

                    result.put("currentMaster",
                            masterMap.get("ip") + ":" + masterMap.get("port"));
                    result.put("masterFlags", masterMap.get("flags"));
                }

                // SENTINEL SLAVES
                List<Map<String, String>> slavesRaw = jedis.sentinelSlaves(masterName);
                List<Map<String, Object>> slaves = new ArrayList<>();
                for (Map<String, String> s : slavesRaw) {
                    Map<String, Object> slave = new LinkedHashMap<>();
                    slave.put("name", s.get("name"));
                    slave.put("ip", s.get("ip"));
                    slave.put("port", s.get("port"));
                    slave.put("flags", s.get("flags"));
                    slave.put("master-host", s.get("master-host"));
                    slave.put("master-port", s.get("master-port"));
                    slave.put("slave-priority", s.get("slave-priority"));
                    slave.put("slave-repl-offset", s.get("slave-repl-offset"));
                    slaves.add(slave);
                }
                view.put("slaves", slaves);

                // SENTINEL SENTINELS
                List<Map<String, String>> sentsRaw = jedis.sentinelSentinels(masterName);
                List<Map<String, Object>> sents = new ArrayList<>();
                for (Map<String, String> s : sentsRaw) {
                    Map<String, Object> sent = new LinkedHashMap<>();
                    sent.put("name", s.get("name"));
                    sent.put("ip", s.get("ip"));
                    sent.put("port", s.get("port"));
                    sent.put("flags", s.get("flags"));
                    sents.add(sent);
                }
                view.put("sentinels", sents);
                view.put("status", "OK");

            } catch (Exception e) {
                view.put("status", "UNREACHABLE");
                view.put("error", e.getMessage());
            }
            sentinelViews.add(view);
        }

        result.put("sentinelViews", sentinelViews);
        result.put("masterFound", masterFound);
        result.put("drillHint", masterFound
                ? "当前集群正常。下一步：手动 kill master（redis-cli -h 192.168.3.100 -p 6379 -a 123456 SHUTDOWN），然后重新调用此接口"
                : "⚠ 主节点不可达！可能正在故障转移中，观察 Sentinel 日志查看选举过程");

        return result;
    }

    /**
     * 快速检查：当前谁是 master？
     */
    @GetMapping("/who-is-master")
    public Map<String, Object> whoIsMaster() {
        Map<String, Object> result = new LinkedHashMap<>();

        for (String node : sentinelNodes.split(",")) {
            String[] parts = node.trim().split(":");
            try (Jedis jedis = new Jedis(parts[0], Integer.parseInt(parts[1]), 3000)) {
                jedis.auth(password);
                List<String> info = jedis.sentinelGetMasterAddrByName(masterName);
                if (info != null && info.size() == 2) {
                    result.put("master", info.get(0) + ":" + info.get(1));
                    result.put("sourceSentinel", node.trim());
                    result.put("masterName", masterName);
                    return result;
                }
            } catch (Exception ignored) {}
        }

        result.put("master", "NONE");
        result.put("note", "所有 Sentinel 均不可达或 master 不存在——可能正在故障转移");
        return result;
    }

    /**
     * 【重点】如何看 Sentinel 日志？
     *
     * 返回各 Sentinel 的关键配置参数 + 日志查看命令。
     */
    @GetMapping("/log-guide")
    public Map<String, Object> logGuide() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", "Sentinel 日志观察指南");

        result.put("logLocations", new String[]{
                "/var/log/redis/sentinel.log",
                "/var/log/redis/sentinel_*.log",
                "docker logs <sentinel-container>  （如果是 Docker）",
                "redis-cli -h <sentinel-host> -p <sentinel-port> -a <password> INFO sentinel"
        });

        result.put("tailCommand", new LinkedHashMap<String, String>() {{
            put("方式1: tail 日志", "tail -f /var/log/redis/sentinel.log");
            put("方式2: 三个终端", "ssh 到 192.168.3.100 后：\n"
                    + "  终端1: tail -f /path/to/sentinel-26379.log\n"
                    + "  终端2: tail -f /path/to/sentinel-26380.log\n"
                    + "  终端3: tail -f /path/to/sentinel-26381.log");
            put("方式3: redis-cli", "redis-cli -h 192.168.3.100 -p 26379 -a 123456 INFO sentinel");
        }});

        result.put("keywordsToWatch", new LinkedHashMap<String, String>() {{
            put("+sdown", "主观下线：某个 Sentinel 判定 master 不可达");
            put("+odown", "客观下线：quorum 个 Sentinel 都认为 master 挂了");
            put("+failover-state", "开始故障转移");
            put("+ elected-leader", "Leader Sentinel 当选");
            put("+selected-slave", "选定了新主");
            put("+promoted-slave", "Slave 被提升为新 Master");
            put("+switch-master", "切换完成，广播新主地址");
            put("+slave", "有 Slave 重新连接");
            put("+sdown 后面又消失了", "旧主复活，降级为 Slave 重新加入");
        }});

        result.put("sentinelConfigKeys", new LinkedHashMap<String, String>() {{
            put("sentinel monitor mymaster", "监视的主节点定义");
            put("sentinel down-after-milliseconds", "SDOWN 判定时间窗口");
            put("sentinel failover-timeout", "故障转移总超时");
            put("sentinel parallel-syncs", "故障转移后并行同步的 Slave 数");
        }});

        return result;
    }

    /**
     * 演练参数速查.
     */
    @GetMapping("/drill-config")
    public Map<String, Object> drillConfig() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 从任意一个 Sentinel 读取配置
        String[] parts = sentinelNodes.split(",")[0].trim().split(":");
        try (Jedis jedis = new Jedis(parts[0], Integer.parseInt(parts[1]), 3000)) {
            jedis.auth(password);
            Map<String, String> map = jedis.sentinelMaster(masterName);
            if (map != null) {
                result.put("downAfterMs", map.get("down-after-milliseconds"));
                result.put("failoverTimeoutMs", map.get("failover-timeout"));
                result.put("parallelSyncs", map.get("parallel-syncs"));
                result.put("quorum", map.get("quorum"));
            }
        } catch (Exception e) {
            result.put("error", "无法连接 Sentinel: " + e.getMessage());
        }

        result.put("sentinelNodes", sentinelNodes);
        result.put("masterName", masterName);
        result.put("killMasterCmd",
                "redis-cli -h 192.168.3.100 -p 6379 -a 123456 SHUTDOWN");
        result.put("reviveMasterCmd",
                "redis-server /path/to/redis-6379.conf  （或 Docker start）");

        return result;
    }

}
