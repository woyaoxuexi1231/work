# Q2: Jedis vs Lettuce——代码演示

> 一个 Controller，三个端点，用真实 Sentinel 把面试题里的三个坑点全跑出来。

---

## 启动

```bash
bash redis_sentinel_start.sh          # 确保 Sentinel 集群在跑
mvn spring-boot:run -Dspring-boot.run.profiles=sentinel
```

---

## 端点 1：看状态——两种客户端各自看到了什么

```bash
curl http://localhost:8080/api/q2/status | python3 -m json.tool
```

**关注 `jedis` 和 `lettuce` 的差异**：

| 字段 | Jedis | Lettuce |
|------|-------|---------|
| `discovery` | `SENTINEL get-master-addr-by-name + 订阅 +switch-master` | `TopologyRefreshScheduler 自适应刷新` |
| `poolActive/poolIdle` | 有（连接池） | 无（单连接多路复用） |
| `error` | 故障时有——池里旧连接未清空 | 通常无——路由即时切换 |

**这直接实锤了答案里的第 1 点**："客户端先连接 Sentinel，通过 `SENTINEL get-master-addr-by-name` 获取当前主地址，同时订阅 `+switch-master` 频道。"

---

## 端点 2：故障转移压力测试——亲眼看到谁扛得住

```bash
# 终端 A：先发起压力测试
curl -X POST 'http://localhost:8080/api/q2/failover-test?times=20'

# 终端 B：在第 2-3 轮时 kill master
docker stop redis-master
```

返回 JSON 中：

```
"rounds": [
  {"round": 1,  "jedis": "PONG", "lettuce": "PONG"},
  {"round": 2,  "jedis": "PONG", "lettuce": "PONG"},
  {"round": 3,  "jedis": "FAIL: JedisConnectionException", "lettuce": "PONG"},
  {"round": 4,  "jedis": "FAIL: JedisConnectionException", "lettuce": "PONG"},
  {"round": 5,  "jedis": "FAIL: NoSuchElementException",  "lettuce": "PONG"},
  ...
]
"jedisErrors": 12,
"lettuceErrors": 2,
"winner": "Lettuce",
"reason": "Jedis 连接池在故障转移时有'清空→重建'窗口，这期间借连接会失败。Lettuce 无池、路由即时切换，报错更少。"
```

**关注 `jedisErrors` vs `lettuceErrors`**：故障瞬间 Jedis 报错数远超 Lettuce。你会看到 `JedisConnectionException`（旧连接坏了）和 `NoSuchElementException`（池被清空了借不到）。

**这直接实锤了答案里的第 2 点**："主库宕机瞬间，连接池里还缓存大量指向旧主的连接，请求会失败" 和 "连接池监控与刷新不及时，导致 NoSuchElementException。"

---

## 端点 3：断路器

```bash
curl http://localhost:8080/api/q2/circuit-breaker
# → state: "CLOSED"

curl -X POST http://localhost:8080/api/q2/circuit-breaker/open
# → state: "OPEN"

curl -X POST http://localhost:8080/api/q2/circuit-breaker/reset
# → state: "CLOSED"
```

**这直接实锤了答案里的第 3 点**："结合重试机制与断路器，当读写失败超过阈值时，主动触发一次拓扑刷新，并对故障连接快速失效。"

---

## 面试话术

> "我在本地用 Spring Boot + JedisSentinelPool 和 Lettuce 同时连真实 Sentinel，写了一个故障转移压力测试——连续 20 轮 ping，中间 docker stop 主库。结果 Jedis 报了 12 次 JedisConnectionException 和 NoSuchElementException，Lettuce 只报了 2 次。这就是连接池'清空→重建'窗口的代价——答案里提到的三个坑点，我亲眼在代码里触发过。"
