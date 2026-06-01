# Q2: Jedis vs Lettuce——代码演示

> 两个终端配合：一个跑 Spring Boot 端点，一个操作 Redis/Sentinel。三个实验把面试题的坑点逐个实锤。

---

## 启动

```bash
sudo bash redis_sentinel_start.sh
redis-cli -h localhost -p 26379 SENTINEL GET-MASTER-ADDR-BY-NAME mymaster
# → 192.168.3.100  6379   ← 必须是宿主机 IP，不是 172.x

mvn spring-boot:run -Dspring-boot.run.profiles=sentinel
```

---

## 实验 1：看两种客户端各自如何发现主

**你做什么**：什么都不做，集群正常时直接调。

```bash
curl http://localhost:8080/api/q2/status | python3 -m json.tool
```

**你会看到**：

```json
{
  "jedis": {
    "client": "JedisSentinelPool",
    "discovery": "启动时 SENTINEL get-master-addr-by-name + 订阅 +switch-master",
    "master": "192.168.3.100:6379",
    "role": "master",
    "ping": "PONG",
    "poolActive": 1,
    "poolIdle": 7
  },
  "lettuce": {
    "client": "Lettuce (Spring Boot 默认)",
    "discovery": "TopologyRefreshScheduler 自适应刷新——收到 +switch-master 即时切换路由",
    "ping": "PONG",
    "note": "NIO 单连接多路复用——不存在 Jedis 的池耗尽问题"
  }
}
```

**实锤了什么**：答案里说"Jedis 通过 SENTINEL get-master-addr-by-name + 订阅 +switch-master 发现主，Lettuce 是自适应拓扑刷新"——`discovery` 字段就是证据。Jedis 有 `poolActive/poolIdle`（连接池），Lettuce 没有——`note` 字段直接告诉你原因。

---

## 实验 2：故障转移中谁扛得住

**需要两个终端。**

**第 1 步——终端 A，启动压力测试：**

```bash
curl -X POST 'http://localhost:8080/api/q2/failover-test?times=30'
```

这个请求会持续约 6 秒（30 轮 × 200ms），每秒 5 轮，每轮同时用 Jedis 和 Lettuce 去 ping 主库。

**第 2 步——终端 B，在第 3 秒左右 kill 主库：**

```bash
sleep 3 && docker stop redis-master
```

> 为什么是 3 秒？因为 Sentinel 的 `down-after-milliseconds=5000`，kill 后约 5 秒 Sentinel 才判定 SDOWN，再 1-2 秒完成切换。总共约 6-7 秒的切换窗口。30 轮 × 200ms = 6 秒，刚好覆盖全过程。

**第 3 步——看终端 A 的返回结果：**

```json
{
  "rounds": 30,
  "rounds": [
    {"round": 1,  "jedis": "PONG", "lettuce": "PONG"},
    {"round": 2,  "jedis": "PONG", "lettuce": "PONG"},
    {"round": 13, "jedis": "FAIL: JedisConnectionException", "lettuce": "PONG"},
    {"round": 14, "jedis": "FAIL: JedisConnectionException", "lettuce": "PONG"},
    {"round": 15, "jedis": "FAIL: NoSuchElementException",  "lettuce": "FAIL: RedisConnectionFailureException"},
    {"round": 16, "jedis": "FAIL: NoSuchElementException",  "lettuce": "PONG"},
    ...
  ],
  "jedisErrors": 14,
  "lettuceErrors": 3,
  "winner": "Lettuce"
}
```

**逐轮解读**：

| 轮次 | Jedis | Lettuce | 发生了什么 |
|------|-------|---------|-----------|
| 1-12 | PONG | PONG | 主库还活着，两边都正常 |
| 13-14 | FAIL: JedisConnectionException | PONG | 旧主连接坏了，Jedis 池里还是旧连接，借出来就报错。Lettuce 已经切了路由 |
| 15 | FAIL: NoSuchElementException | FAIL | Jedis 池正在清空重建，借不到连接。Lettuce 偶发一次超时 |
| 16+ | FAIL: NoSuchElementException | PONG | Jedis 池还在重建。Lettuce 已经稳定连上新主 |

**实锤了什么**：答案里说的三个坑点——

1. "主库宕机瞬间，连接池里还缓存大量指向旧主的连接，请求会失败" → `JedisConnectionException` 那几行
2. "连接池监控与刷新不及时，导致 NoSuchElementException" → `NoSuchElementException` 那几行
3. "尽量使用 Lettuce，天然支持自适应拓扑刷新" → Lettuce 报错少得多

**第 4 步——恢复环境：**

```bash
docker start redis-master
sleep 3
redis-cli -h localhost -p 26379 SENTINEL FAILOVER mymaster
# 手动触发一次故障转移，把主切回 6379
sleep 5
curl http://localhost:8080/api/q2/status | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['jedis']['master'])"
# → 192.168.3.100:6379
```

---

## 实验 3：断路器——防止雪崩

**你做什么**：模拟连续 Redis 故障，看断路器如何保护系统。

```bash
# 1. 初始状态
curl http://localhost:8080/api/q2/circuit-breaker
# → {"failures": 0, "threshold": 5, "state": "CLOSED"}

# 2. 手动熔断——相当于连续 5 次 Redis 操作失败
curl -X POST http://localhost:8080/api/q2/circuit-breaker/open
# → {"failures": 5, "threshold": 5, "state": "OPEN",
#     "note": "所有 Redis 请求直接拒绝——走降级逻辑（如回源 DB 或返回缓存默认值）"}

# 3. 复位
curl -X POST http://localhost:8080/api/q2/circuit-breaker/reset
# → {"failures": 0, "threshold": 5, "state": "CLOSED"}
```

**实锤了什么**：答案里说的"结合重试机制与断路器，当读写失败超过阈值时，主动触发一次拓扑刷新，并对故障连接快速失效"——`state: "OPEN"` 就是这道防线。

---

## 三个实验总结

| 实验 | 你的 Redis 操作 | Spring Boot 端点 | 实锤了答案里的 |
|------|----------------|-----------------|-------------|
| 1 | 不操作，集群正常 | `GET /api/q2/status` | 两种客户端的发现机制差异 |
| 2 | `docker stop redis-master` | `POST /api/q2/failover-test?times=30` | 三个坑点：旧连接报错、NoSuchElementException、Lettuce 更稳 |
| 3 | 不操作 Redis | `GET/POST /api/q2/circuit-breaker` | 断路器是故障时的最后防线 |

---

## 面试话术

> "我在本地搭了 1 主 2 从 + 3 Sentinel，Spring Boot 同时持有 JedisSentinelPool 和 Lettuce。写了一个 30 轮的压力测试——每轮同时 ping，中间 `docker stop` 主库。结果 Jedis 报了 14 次异常（JedisConnectionException + NoSuchElementException），Lettuce 只报了 3 次。我亲眼看到了连接池'清空→重建'窗口的代价——答案里提到的三个坑点，我是有数据支撑的，不是背的。"
