# redis-code-0531 完整演示手册

> 从零开始，一步步把 8 道 Redis Sentinel & Cluster 面试题全部跑一遍。

---

## 0. 你需要准备什么

| 组件 | 版本 | 地址 | 用途 |
|------|------|------|------|
| JDK | 1.8 | 本机 | 编译运行 |
| Maven | 3.6+ | 本机 | 构建 |
| MySQL | 5.7+ | 192.168.3.100:3306 root/123456 | Q4 SAGA 日志、Q8 迁移状态 |
| Redis 单机 | 任意 | 192.168.3.100:6379 密码123456 | 默认模式，Q2/Q3/Q5 的部分演示 |
| Redis Sentinel | — | 主6379 从6380-6381 哨兵26379-26381 密码123456 | Q1/Q2（需切换 profile） |
| Redis Cluster | 7.2.5 | 7000-7005 密码123456 | Q3/Q4/Q8（需切换 profile） |

### 0.1 确认 MySQL 可达

```bash
mysql -h 192.168.3.100 -u root -p123456 -e "SELECT 1"
# → 输出 1 即正常
```

如果连不上：检查防火墙、MySQL 是否绑定了 `0.0.0.0`、用户是否允许远程登录。

### 0.2 初始化 MySQL 表

```bash
mysql -h 192.168.3.100 -u root -p123456 < src/main/resources/init.sql
```

验证：
```bash
mysql -h 192.168.3.100 -u root -p123456 redis_demo -e "SHOW TABLES"
# → saga_transaction, reshard_task 两张表
```

### 0.3 确认 Redis 可达

```bash
# 单机
redis-cli -h 192.168.3.100 -p 6379 -a 123456 PING
# → PONG

# Sentinel（查看主节点信息）
redis-cli -h 192.168.3.100 -p 26379 -a 123456 SENTINEL MASTER mymaster
# → 应返回主节点信息

# Cluster
redis-cli -c -h 192.168.3.100 -p 7000 -a 123456 CLUSTER NODES
# → 应返回 6 个节点的列表
```

### 0.4 导入项目 & 编译

```bash
# 在项目根目录
mvn clean compile
# → BUILD SUCCESS
```

---

## 1. 启动项目

打开第一个终端：

```bash
# 默认以 standalone 单机模式启动（最简单）
mvn spring-boot:run
```

看到 `Started DemoApplication` 即启动成功。

验证：
```bash
curl http://localhost:8080/api/sentinel/status
# → 返回 JSON，包含可用端点列表
```

> **三种启动模式**：
> - `mvn spring-boot:run` → 单机 Redis（默认，大部分演示不用连真实 Sentinel/Cluster）
> - `mvn spring-boot:run -Dspring-boot.run.profiles=sentinel` → Sentinel 模式
> - `mvn spring-boot:run -Dspring-boot.run.profiles=cluster` → Cluster 模式
>
> 建议先用默认模式跑通所有"模拟类"接口（Q1 全流程/Q3 槽位计算/Q4 SAGA/Q5/Q7/Q8），
> 需要连真实 Sentinel/Cluster 时再切换 profile。

---

## 2. 逐题演示

### Q1 Sentinel 故障转移 & 脑裂

> **不需要真实 Sentinel**，全部由 Java 多线程真实模拟。
>
> **架构**：FailoverSimulator 启动 N 个 Sentinel 线程，每个线程独立 PING master，
> 通过 `CountDownLatch` 同步 SDOWN/ODOWN 阶段，通过 `ConcurrentHashMap` 投票选举 Leader。
> 整个过程约 5~12 秒（取决于 `downAfterMs` 参数），请求会阻塞直到故障转移完成。

#### 步骤 1：配置故障转移参数

```bash
curl -X POST http://localhost:8080/api/sentinel/failover/configure \
  -H 'Content-Type: application/json' \
  -d '{"sentinelCount":3, "quorum":2, "downAfterMs":3000, "slaveCount":3, "failoverTimeoutMs":30000}'
```

**你看到的**：
```json
{
  "status": "configured",
  "sentinelCount": 3,
  "quorum": 2,
  "downAfterMs": "3000ms",
  "slaveCount": 3,
  "tip": "已配置完成。POST /api/sentinel/failover/trigger 开始模拟"
}
```

#### 步骤 2：触发故障转移（多线程！注意请求会阻塞 5~12 秒）

```bash
curl -X POST http://localhost:8080/api/sentinel/failover/trigger
# ⏳ 这个请求会等 5~12 秒才返回——因为后台 3 个 Sentinel 线程在真跑！
```

**看什么**：返回的 `timeline` 数组，约 30+ 个事件。逐行看 `phase` 和 `description`。

**线程模型**（返回 JSON 中的 `threadModel` 字段）：
> "多个 Sentinel 线程各自独立 PING（每 1 秒），通过 CountDownLatch + ConcurrentHashMap 协调 SDOWN/ODOWN/选举"

**完整时间线阶段**：

| phase | 含义 | 面试对应点 |
|-------|------|-----------|
| `SENTINEL_START` | 每个 Sentinel 线程启动 | 多线程架构 |
| `PING` | 每 1 秒 PING master → PONG | 心跳检测 |
| `MASTER_KILL` | Master 被 kill（模拟宕机） | 故障发生 |
| `PING_FAIL` | PING 无响应 | 故障检测开始 |
| `SDOWN` | 单个 Sentinel 判定主观下线 | down-after-milliseconds 窗口 |
| `SDOWN_ASK` | 询问其他 Sentinel | 分布式共识 |
| `ODOWN_CHECK` | 统计 SDOWN 数量 | quorum 判定 |
| `ODOWN` | 客观下线达成 | quorum ≥ N |
| `LEADER_ELECT` | 发起选举，epoch 自增 | Raft 协议子集 |
| `LEADER_VOTE` | 其他 Sentinel 投票 | 一任内只投一次 |
| `LEADER_ELECTED` | Leader 当选（过半） | 多数派 |
| `SLAVE_SCAN` | 扫描所有 Slave | 优先级→偏移量排序 |
| `PROMOTE` | SLAVEOF NO ONE | 新主诞生 |
| `REPLICA_RECONFIG` | 通知其他 Slave 追随新主 | SLAVEOF 新主 |
| `SWITCH_MASTER` | Pub/Sub 广播 | +switch-master 频道 |
| **`OLD_MASTER_REVIVE`** | **旧主复活重新上线** | **完整闭环** |
| **`OLD_MASTER_SLAVEOF`** | **旧主执行 SLAVEOF 新主** | **降级为 Slave** |
| `COMPLETE` | 全流程结束 | 旧主→Slave + 新主→Master |

#### 步骤 3：脑裂——无防护

```bash
curl -X POST 'http://localhost:8080/api/sentinel/split-brain/simulate?protection=false'
```

**看什么**：`timeline` 中搜索 `DISASTER` 和 `CONFLICT`——两个 Master 同时写入，数据错乱。

#### 步骤 4：脑裂——有防护

```bash
curl -X POST 'http://localhost:8080/api/sentinel/split-brain/simulate?protection=true'
```

**看什么**：搜索 `PROTECTION_ON` 和 `AVOIDED`——旧主发现联系不到 Slave，自觉拒绝写入。

#### 步骤 5（作业）：让选举失败

```bash
# 把 sentinelCount 和 quorum 都设成 2
curl -X POST http://localhost:8080/api/sentinel/failover/configure \
  -H 'Content-Type: application/json' \
  -d '{"sentinelCount":2, "quorum":2, "downAfterMs":3000, "slaveCount":3}'

curl -X POST http://localhost:8080/api/sentinel/failover/trigger
```

**看什么**：`timeline` 中会出现 `ODOWN_FAILED`——只剩 1 个 Sentinel 时无法达到 quorum=2，故障转移彻底失灵。这就是为什么 Sentinel 必须至少 3 个奇数节点。

---

### Q2 Sentinel 客户端集成

> 需要 Redis 单机可达（Jedis/Lettuce 需要连真实的 Sentinel），但模拟类接口不需要。

#### 步骤 1：初始化 JedisSentinelPool

```bash
curl -X POST http://localhost:8080/api/sentinel/client/jedis-init
```

**看什么**：`currentMaster` 字段——Jedis 通过 `SENTINEL get-master-addr-by-name` 找到了当前主。

#### 步骤 2：对比 Jedis vs Lettuce

```bash
curl http://localhost:8080/api/sentinel/client/compare
```

**看什么**：返回的 `jedis` 和 `lettuce` 两个对象。关键差异：
- Jedis：BIO 连接池，有切换窗口
- Lettuce：NIO 多路复用，自适应拓扑刷新

#### 步骤 3：READONLY 错误推演

```bash
curl http://localhost:8080/api/sentinel/client/readonly-sim
```

**看什么**：`steps` 数组中 step 3——旧连接写旧主（已降为 Slave）→ 收到 `READONLY`。

#### 步骤 4：断路器

```bash
# 查看当前状态（CLOSED）
curl http://localhost:8080/api/sentinel/client/circuit-breaker

# 手动熔断
curl -X POST http://localhost:8080/api/sentinel/client/circuit-breaker/open

# 再次查看（OPEN，倒计时 10 秒）
curl http://localhost:8080/api/sentinel/client/circuit-breaker

# 等 10 秒后再查 → HALF_OPEN
sleep 10
curl http://localhost:8080/api/sentinel/client/circuit-breaker
```

**看什么**：状态从 CLOSED → OPEN → HALF_OPEN 的流转。OPEN 期间所有 Redis 请求被直接拒绝。

---

### Q3 Cluster 槽位路由

> 完全不需要真实 Redis，纯 Java 计算。

#### 步骤 1：手算一个 key 的 slot

```bash
curl 'http://localhost:8080/api/cluster/slot/calc?key=order:123'
```

**看什么**：
```json
{
  "key": "order:123",
  "hashTag": "(无 Hash Tag)",
  "slot": 5975,
  "crc16Hex": "0x1757",
  "node": "7002 (192.168.3.100:7002)",
  "slotRange": "5460-8189"
}
```
每一步都对应 Redis 源码中的逻辑：提取 Hash Tag → CRC-16-CCITT → `& 0x3FFF`。

#### 步骤 2：批量计算——看散列分布

```bash
curl -X POST http://localhost:8080/api/cluster/slot/batch \
  -H 'Content-Type: application/json' \
  -d '["order:1","order:2","inventory:1","user:session:abc"]'
```

**看什么**：`distribution` 字段——4 个 key 散到了几个不同节点。

#### 步骤 3：MOVED vs ASK

```bash
# MOVED 模拟
curl 'http://localhost:8080/api/cluster/slot/moved?key=order:999'

# ASK 模拟
curl 'http://localhost:8080/api/cluster/slot/ask?key=order:999'

# 对比表
curl http://localhost:8080/api/cluster/slot/moved-vs-ask
```

**看什么**：ASK 返回中有 `clientAction` 数组——"不要更新槽位映射"、"必须先发 ASKING"——这是面试的区分点。

#### 步骤 4：Hash Tag 强制同槽

```bash
curl 'http://localhost:8080/api/cluster/slot/hash-tag-demo?tag=order:123'
```

**看什么**：
- `withoutHashTag`：3 个 key 的 slot 各不相同
- `withHashTag`：3 个 key 全部同一 slot
- `risks`："热点风险"、"数据倾斜"、"扩展性丧失"

#### 步骤 5：为什么是 16384

```bash
curl http://localhost:8080/api/cluster/slot/topology
```

**看什么**：`why16384` 数组——心跳位图 2KB、CRC16 低 14 位、千节点够用。

---

### Q4 跨槽事务 & SAGA 补偿

> 需要 MySQL（SAGA 日志持久化），不需要真实 Redis。

#### 步骤 1：跨槽 Lua 被拒

```bash
curl 'http://localhost:8080/api/cluster/transaction/cross-slot?orderId=phone-123'
```

**看什么**：`sameSlot: false`——库存、订单状态、已购列表三个 key 散到了不同 slot。`result` 字段显示 `CROSSSLOT` 错误。

#### 步骤 2：Hash Tag 方案——有风险

```bash
curl 'http://localhost:8080/api/cluster/transaction/hash-tag?orderId=phone-123'
```

**看什么**：`sameSlot: true` 说明 Lua 可以跑了，但 `risks` 里的四个风险是面试核心论据。

#### 步骤 3：SAGA 成功路径

```bash
curl -X POST 'http://localhost:8080/api/cluster/transaction/saga/execute?orderId=order-001'
```

**看什么**：`timeline` 中依次出现：
```
PREPARE → STOCK_CHECK → STATUS_UPDATE → LIST_APPEND
```
全部 `SUCCESS`，`overallSuccess: true`。

#### 步骤 4：SAGA 失败 + 补偿回滚

```bash
curl -X POST 'http://localhost:8080/api/cluster/transaction/saga/execute?orderId=fail-order-002'
```

**看什么**：`timeline` 中出现 `COMPENSATE` 步骤——库存扣减失败 → 回滚预占。`overallSuccess: false`。

#### 步骤 5：查看 MySQL 中的 SAGA 日志

```bash
curl 'http://localhost:8080/api/cluster/transaction/saga/log?orderId=order-001'
curl 'http://localhost:8080/api/cluster/transaction/saga/log?orderId=fail-order-002'
```

**看什么**：每条记录有 `step`、`status`、`error_msg`。失败订单的 `STOCK_CHECK` 那行会有错误信息。

---

### Q5 热 Key / 大 Key 排查 & 多级缓存

> 多级缓存写入需要 Redis 单机可达。慢日志/大 Key/热 Key 检测是模拟数据，不需要 Redis。

#### 步骤 1：慢查询分析

```bash
curl 'http://localhost:8080/api/cluster/troubleshoot/slowlog?topN=3'
```

**看什么**：3 条模拟慢日志——`KEYS *`、`HGETALL` 大 Key、`SORT`——每条都有 `advice` 告诉你为什么这是灾难。

#### 步骤 2：大 Key 检测

```bash
curl http://localhost:8080/api/cluster/troubleshoot/bigkeys
```

**看什么**：`analytics:2024 → 500 MB → CRITICAL`——这就是 Q8 的迁移噩梦主角。

#### 步骤 3：热 Key 检测

```bash
curl http://localhost:8080/api/cluster/troubleshoot/hotkeys
```

**看什么**：`seckill:phone-123 → 5,000,000 hits → CRITICAL`。`emergency` 和 `monitoring` 字段是面试时的标准答案。

#### 步骤 4：多级缓存——亲眼看到 L2→L1 跃迁

```bash
# 先写一条数据到 Redis（需要单机 Redis 可达）
curl -X POST 'http://localhost:8080/api/cluster/troubleshoot/cache/set?key=hot:item&value=99'

# 清空本地 Caffeine
curl -X POST http://localhost:8080/api/cluster/troubleshoot/cache/clear

# 第 1 次读取 → Redis 命中（L2）
curl -X POST 'http://localhost:8080/api/cluster/troubleshoot/cache/get?key=hot:item'

# 第 2 次读取 → Caffeine 命中（L1）——微秒级！
curl -X POST 'http://localhost:8080/api/cluster/troubleshoot/cache/get?key=hot:item'

# 第 3 次——仍然是 L1
curl -X POST 'http://localhost:8080/api/cluster/troubleshoot/cache/get?key=hot:item'

# 看命中率
curl http://localhost:8080/api/cluster/troubleshoot/cache/stats
```

**观察**：三次调用，`cacheLevel` 的变化：
1. `L2-Redis`（latency ~1ms）→ Caffeine miss，Redis hit，回填 Caffeine
2. `L1-Caffeine`（latency < 1μs）→ 直接命中本地缓存
3. `L1-Caffeine`（latency < 1μs）→ 仍然命中

#### 步骤 5（作业）：让缓存失效

修改 `q5_hotkey_troubleshoot/HotKeyDetector.java` 第 72 行：
```java
// 把这行
.expireAfterWrite(Duration.ofSeconds(5))
// 改成
.expireAfterWrite(Duration.ofSeconds(1))
```
重启 → 等 1 秒 → 再调 `/cache/get` → `cacheLevel: "L2-Redis"`（本地缓存过期了，又回到 Redis）。

---

### Q7 统一客户端工厂

> 需要至少单机 Redis 可达（execute 接口要真连 Redis）。

#### 步骤 1：初始化 + 查看模式

```bash
curl -X POST 'http://localhost:8080/api/factory/init?mode=SENTINEL'
curl http://localhost:8080/api/factory/mode
```

**看什么**：`mode: "SENTINEL"`, `executor: "SentinelCommandExecutor[...]"`

#### 步骤 2：执行 Redis 命令

```bash
curl 'http://localhost:8080/api/factory/execute?command=PING'
curl -X POST 'http://localhost:8080/api/factory/execute?command=SET&key=hello&value=world'
curl 'http://localhost:8080/api/factory/execute?command=GET&key=hello'
```

#### 步骤 3：无中断模式切换

```bash
curl -X POST 'http://localhost:8080/api/factory/switch?mode=CLUSTER'
```

**看什么**：返回中 `principle` 字段——"AtomicReference 原子替换——新请求走新执行器，旧连接缓冲期后关闭"。`from: "SENTINEL"`, `to: "CLUSTER"`。

```bash
# 切回来
curl -X POST 'http://localhost:8080/api/factory/switch?mode=SENTINEL'
```

#### 步骤 4：重试 + 断路器

```bash
# 重试测试
curl -X POST 'http://localhost:8080/api/factory/retry-test?command=PING&maxRetries=3'

# 查看断路器
curl http://localhost:8080/api/factory/circuit-status

# 手动复位
curl -X POST http://localhost:8080/api/factory/circuit-reset
```

**看什么**：`retry-test` 返回中 `attemptsDetail` 数组——每次重试的 error、backoff、retriable 判断。

---

### Q8 平滑扩缩容

> 迁移状态机需要 MySQL。大 Key 演示不需要 Redis。

#### 步骤 1：生成扩容计划

```bash
curl 'http://localhost:8080/api/ops/reshard/plan?newNodeCount=20'
```

**看什么**：`algorithm: "贪心均衡"`，`plan` 数组里每个 slot 的迁移计划，`bigKeyCheck` 标注了大 Key 风险。

#### 步骤 2：执行单槽迁移

```bash
curl -X POST 'http://localhost:8080/api/ops/reshard/migrate?slot=5000&source=7000&target=8000'
```

**看什么**：`timeline` 中依次出现：
```
IMPORTING → MIGRATING → MIGRATE_KEY(×3) → COMPLETED
```
这就是 `redis-cli --cluster reshard` 背后的完整流程。

#### 步骤 3：暂停 → 查看 → 恢复

```bash
curl -X POST 'http://localhost:8080/api/ops/reshard/pause?reason=P99%E5%BB%B6%E8%BF%9F%E8%B6%85%E8%BF%8710ms'
curl http://localhost:8080/api/ops/reshard/status
curl -X POST http://localhost:8080/api/ops/reshard/resume
```

**看什么**：`status` 返回中 `migrationPaused: true` 和 `pauseReason`。

#### 步骤 4：回滚

```bash
curl -X POST 'http://localhost:8080/api/ops/reshard/rollback?slot=5000'
```

**看什么**：`rollbackSteps`——清除 MIGRATING、清除 IMPORTING、MySQL 状态更新。

#### 步骤 5：500MB 大 Key 迁移灾难

```bash
curl http://localhost:8080/api/ops/reshard/bigkey-risk
```

**看什么**：`timeline` 中 `BLOCKING` 阶段——"MIGRATE 是同步操作，Redis 主线程被阻塞"、"Sentinel 误判主节点下线"。这就是 Q8 追问"超大 key 迁移会发生什么"的完整答案。

---

## 3. 常见问题

**Q: MySQL 连不上，很多接口报错怎么办？**

A: Q1/Q3 全部不需要 MySQL。Q4 SAGA 和 Q8 迁移状态机会在 MySQL 不可达时降级——日志写 `warn` 但不影响核心流程返回。可以先跑通 Q1/Q3/Q5（模拟部分），再解决 MySQL。

**Q: Redis 连不上？**

A: Q1（故障转移模拟）、Q3（槽位计算）、Q4（SAGA）、Q8（扩缩容）都不需要真实 Redis。只有 Q2 的部分接口和 Q5/Q7 的 `execute` 接口需要。默认启动用的单机 Redis `192.168.3.100:6379`。

**Q: 想用 Sentinel 模式启动？**

A: 改 `application.yml` 中 `spring.profiles.active: standalone` 为 `sentinel`，或启动时加参数：
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=sentinel
```
但 Q1/Q2 的大部分模拟接口在 standalone 模式下也能跑。

**Q: 端口 8080 被占用？**

A: 改 `application.yml` 中 `server.port: 8080` 为其他端口。
