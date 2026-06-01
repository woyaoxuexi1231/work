# Q2: Java 客户端与 Sentinel 集成——Jedis vs Lettuce

> 启动 Spring Boot 连真实 Sentinel，亲眼看到两种客户端在故障转移时的不同表现。

---

## 0. 前置条件

```bash
# Sentinel 集群必须已启动
bash redis_sentinel_start.sh

# 确认
redis-cli -h localhost -p 26379 SENTINEL MASTER mymaster | grep -E "ip|port|flags"
# → flags: master, ip: 192.168.3.100, port: 6379
```

---

## 1. 启动 Spring Boot（Sentinel 模式）

```bash
cd redis-code-0531
mvn spring-boot:run -Dspring-boot.run.profiles=sentinel
```

看到 `Started DemoApplication` 即就绪。Spring Boot 用 **Lettuce** 作为默认客户端连接 Sentinel——后面会解释为什么。

---

## 2. 两种客户端的原理

### JedisSentinelPool 怎么发现主？

```
Jedis 启动
  │
  ├─→ 连任意 Sentinel: "SENTINEL get-master-addr-by-name mymaster"
  │     Sentinel 回答: "192.168.3.100 6379"
  │
  ├─→ 建立到 192.168.3.100:6379 的连接池（8 个 Jedis 实例）
  │
  └─→ 订阅 Sentinel 的 +switch-master 频道
        切换发生时 Sentinel 推送: "新主是 192.168.3.100:6380"
        → JedisSentinelPool 清空旧连接池 → 重建到新主的池
```

**问题**：清空池→重建池之间有窗口——旧连接还没清干净，新连接还没建好。

### Lettuce 怎么发现主？

```
Lettuce 启动
  │
  ├─→ 连任意 Sentinel: "SENTINEL get-master-addr-by-name mymaster"
  │
  ├─→ 建立到 master 的 Netty NIO 连接（1 个连接就够，多路复用）
  │
  └─→ TopologyRefreshScheduler 后台线程
        ├─ 订阅 +switch-master 频道
        └─ 定期轮询 Sentinel 验证拓扑
        切换发生时: 立即切换路由到新主（不重建连接池——根本没池）
```

**优势**：没有池，所以没有"清空→重建"窗口。

---

## 3. 动手：初始化 + 对比

### 3.1 初始化 JedisSentinelPool

```bash
curl -X POST http://localhost:8080/api/sentinel/client/jedis-init | python3 -m json.tool
```

关键字段：
- `currentMaster`: `"192.168.3.100:6379"` — Jedis 通过 SENTINEL get-master-addr-by-name 找到的
- `role`: `"master"` — 确认它确实是主
- `poolConfig`: `"maxTotal=8, testOnBorrow=true"` — 8 个连接的池，借出时校验

### 3.2 初始化 Lettuce

```bash
curl -X POST http://localhost:8080/api/sentinel/client/lettuce-init | python3 -m json.tool
```

关键字段：
- `advantages[0]`: `"NIO 多路复用——单连接高并发"` — Lettuce 不需要池
- `advantages[1]`: `"自适应拓扑刷新——收到 +switch-master 即时切换"`

### 3.3 对比表

```bash
curl http://localhost:8080/api/sentinel/client/compare | python3 -m json.tool
```

输出对比了 6 个维度：模型、拓扑发现、切换行为、切换窗口、常见异常、线程安全。

---

## 4. 核心实验：故障转移中观察客户端行为

### 4.1 开启实时观测

**终端 A（Spring Boot 观测）：**

```bash
# 每 1 秒问 Sentinel "现在谁是主？"
# 这个 HTTP 请求会持续 60 秒，每秒输出一行
curl -N http://localhost:8080/api/sentinel/client/watch-failover
```

输出大约长这样：

```
=== Q2: 故障转移实时观测 ===
在另一个终端执行: docker stop redis-master
观察 master 地址如何变化

[ 0s] master = 192.168.3.100:6379
[ 1s] master = 192.168.3.100:6379
[ 2s] master = 192.168.3.100:6379
[ 3s] master = 192.168.3.100:6379
[ 4s] master = 192.168.3.100:6379
[ 5s] master = NONE（所有 Sentinel 不可达或 master 不存在——可能正在切换中）
[ 6s] master = NONE（所有 Sentinel 不可达或 master 不存在——可能正在切换中）
[ 7s] master = 192.168.3.100:6380 ★★★ 变了！
  ↑ 故障转移完成！新主上线
[ 8s] master = 192.168.3.100:6380
```

**终端 B（操作）：**

```bash
# 等终端 A 跑到第 3 秒左右时，执行
docker stop redis-master
```

**观察重点**：
- 第 0-4 秒：master 是 6379
- 停了之后 5 秒左右：出现 `NONE`——**故障转移正在进行中**
- 再过 1-2 秒：`★★★ 变了！`——新主 6380 上线
- 之后一直稳定在 6380

**这就是你面试时可以描述的现场**："我亲眼看到从 kill master 到新主上线，中间有大约 2 秒的 `NONE` 窗口——这期间任何客户端都无法写入。"

### 4.2 故障转移期间 JedisSentinelPool 的表现

```bash
# 先确保 Jedis 池已初始化
curl -X POST http://localhost:8080/api/sentinel/client/jedis-init

# 重建环境（把旧主拉回来恢复初始状态）
docker start redis-master
# 等 10 秒让它恢复为 slave，然后手动 failover 让它重新变主：
redis-cli -h localhost -p 26379 SENTINEL FAILOVER mymaster
```

```bash
# 现在再跑连接池耗尽测试——模拟高并发下主库突然宕机
# 在 docker stop redis-master 之后立刻执行：
curl -X POST 'http://localhost:8080/api/sentinel/client/jedis-exhaust?count=50' | python3 -m json.tool
```

**你会看到**：
- `errors` 数量 > 0——部分请求拿到了指向旧主的连接，报错
- `verdict` 字段直接告诉你"这正是主从切换后连接池未及时刷新的典型症状"

### 4.3 READONLY 错误场景

```bash
curl http://localhost:8080/api/sentinel/client/readonly-sim | python3 -m json.tool
```

5 步推演：连接指向旧主→旧主复活变 Slave→写入→`READONLY`→拓扑刷新→重试成功。

---

## 5. 断路器——防止雪崩

```bash
# 查看断路器状态（初始 CLOSED）
curl http://localhost:8080/api/sentinel/client/circuit-breaker | python3 -m json.tool

# 手动熔断——模拟 Redis 彻底不可达
curl -X POST http://localhost:8080/api/sentinel/client/circuit-breaker/open | python3 -m json.tool

# 再次查看——状态变成 OPEN
curl http://localhost:8080/api/sentinel/client/circuit-breaker | python3 -m json.tool

# 等 10 秒——自动进入 HALF_OPEN（下一个请求试探性放行）
sleep 10
curl http://localhost:8080/api/sentinel/client/circuit-breaker | python3 -m json.tool

# 复位
curl -X POST http://localhost:8080/api/sentinel/client/circuit-breaker/reset | python3 -m json.tool
```

**断路器状态机**：
```
CLOSED（正常）──连续失败5次──→ OPEN（拒绝所有请求，10秒）
OPEN ──10秒超时──→ HALF_OPEN（试探性放行1个）
HALF_OPEN ──成功──→ CLOSED
HALF_OPEN ──失败──→ OPEN（重新计时10秒）
```

---

## 6. Spring Boot 为什么选 Lettuce？

这是面试高频题。Spring Boot 2.x 把默认 Redis 客户端从 Jedis 换成了 Lettuce。原因：

### 6.1 连接模型对比

| | Jedis | Lettuce |
|------|------|------|
| I/O 模型 | **BIO**（阻塞 I/O） | **NIO**（Netty 非阻塞） |
| 连接与线程 | **1 连接 = 1 线程**，多线程必须池化 | **1 连接服务 N 个线程**，天然多路复用 |
| 线程安全 | Jedis 实例**非线程安全**，需池化隔离 | **线程安全**，StatefulRedisConnection 可共享 |
| 池化 | 必须用 JedisPool/JedisSentinelPool | 不需要，单连接就够（也可配连接池做隔离） |

### 6.2 故障转移时发生了什么

**JedisSentinelPool**：
```
订阅收到 +switch-master
  → 标记当前池内所有连接为"脏"
  → 逐个关闭旧连接
  → 向新主建立新连接池
  → 这期间 borrowObject() 可能失败 → NoSuchElementException
```

**Lettuce**：
```
TopologyRefreshScheduler 收到 +switch-master
  → 更新内部拓扑表（新主 IP:Port）
  → 下一个命令自动路由到新主
  → 无缝
```

### 6.3 为什么 2.x 才换？

Spring Boot 1.x 时代 Lettuce 还不成熟——有连接泄漏、拓扑刷新 bug。到了 2.x，Lettuce 已经足够稳定，而且在响应式编程（Spring WebFlux）上 Jedis 完全不行——BIO 模型天然无法支持 Reactive Streams。

### 6.4 Jedis 还有用吗？

有。Jedis 在以下场景仍有优势：
- **简单、调试方便**：BIO 模型的调用栈干净，排查问题比 Netty 异步模型容易
- **性能足够**：大部分项目 QPS 几千级别，Jedis 池化完全够用
- **兼容老旧系统**：有些内部框架只适配了 Jedis

但**新项目默认用 Lettuce**——Spring Boot 的选择就是行业共识。

---

## 7. 面试话术

> "我们线上 Spring Boot 2.7，默认用 Lettuce。它的 TopologyRefreshScheduler 在故障转移时能做到接近无缝切换，因为它是基于 Netty NIO 的，单连接多路复用——不存在 JedisSentinelPool 那种'清空旧池→建新池'的切换窗口。我在本地搭了 1 主 2 从 + 3 Sentinel，用 `curl -N` 实时观测，kill 主库后约 2 秒出现了 NONE 窗口，然后新主上线。这个 NONE 窗口就是 down-after-milliseconds(5s) + 选举时间。我还手写了一个断路器——连续 5 次失败熔断 10 秒，防止 Redis 不可用时业务线程全部阻塞。"

---

## 8. Q2 接口速查

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/sentinel/client/jedis-init` | 初始化 JedisSentinelPool |
| POST | `/api/sentinel/client/jedis-exhaust?count=50` | 压力测试——看池在故障时表现 |
| GET | `/api/sentinel/client/readonly-sim` | READONLY 错误推演 |
| POST | `/api/sentinel/client/lettuce-init` | 初始化 Lettuce Sentinel 连接 |
| GET | `/api/sentinel/client/compare` | Jedis vs Lettuce 6 维对比 |
| **GET** | **`/api/sentinel/client/watch-failover`** | **实时故障转移观测（持续 60 秒）** |
| GET | `/api/sentinel/client/circuit-breaker` | 断路器状态 |
| POST | `/api/sentinel/client/circuit-breaker/open` | 手动熔断 |
| POST | `/api/sentinel/client/circuit-breaker/reset` | 复位 |
