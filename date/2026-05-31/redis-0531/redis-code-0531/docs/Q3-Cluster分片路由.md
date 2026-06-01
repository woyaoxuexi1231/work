# Q3: Redis Cluster 分片路由——代码演示

> 先搭 6 节点 Cluster，再手算 CRC16 和 Redis 对比，故意发错节点看 MOVED。

---

## 启动

```bash
# 1. 搭 Cluster（6 节点，3 主 3 从）
sudo bash scripts/redis_cluster_start.sh

# 2. 验证
redis-cli -c -h 192.168.3.100 -p 7000 -a 123456 CLUSTER NODES
# → 6 行节点信息，每个有 slot 范围

# 3. 启动 Spring Boot
mvn spring-boot:run -Dspring-boot.run.profiles=cluster
```

---

## 实验 1：手算 slot → Redis 验证

```bash
curl 'http://localhost:8080/api/q3/slot?key=order:123' | python3 -m json.tool
```

返回：

```json
{
  "key": "order:123",
  "hashTarget": "(整 key)",
  "javaCrc16": "0x1757",
  "javaSlot": 5975,
  "redisSlot": 5975,
  "match": "✓ 一致"
}
```

**做了什么**：Java 端用 CRC-16-CCITT 查找表（和 Redis 源码 `crc16.c` 逐位对齐）算出 CRC16，再 `& 0x3FFF` 取低 14 位得到 0-16383 之间的 slot。然后用 `CLUSTER KEYSLOT` 让 Redis 也算一遍——两个结果必须一致。

**实锤了什么**：答案里说"16384 个槽，CRC16 取低 14 位"——你亲手算出来了。`javaCrc16: 0x1757` 和 `redisSlot: 5975` 就是证明。

### 再试试 Hash Tag

```bash
curl 'http://localhost:8080/api/q3/hash-tag?tag=order:123' | python3 -m json.tool
```

返回里 `withoutHashTag` 三个 key 的 slot 各不相同，`withHashTag` 三个 key 全部同一 slot。这就是 `{tag}` 强制同槽的效果。

---

## 实验 2：看集群槽位分布

```bash
curl http://localhost:8080/api/q3/topology | python3 -m json.tool
```

```json
{
  "nodes": [
    {"range": "0-5460",    "count": 5461, "node": "192.168.3.100:7000"},
    {"range": "5461-10922", "count": 5462, "node": "192.168.3.100:7001"},
    {"range": "10923-16383","count": 5461, "node": "192.168.3.100:7002"}
  ],
  "totalSlots": 16384,
  "totalNodes": 3
}
```

16384 个槽均分给 3 个主节点。`CLUSTER SLOTS` 命令的原始输出被解析成了表格。

---

## 实验 3：故意发错节点，看 MOVED

```bash
curl -X POST 'http://localhost:8080/api/q3/moved-demo?key=testkey' | python3 -m json.tool
```

```json
{
  "key": "testkey",
  "slot": 12539,
  "correctNode": "192.168.3.100:7002",
  "sentTo": "192.168.3.100:7000（故意发到错误节点）",
  "MOVED_response": "MOVED 12539 192.168.3.100:7002",
  "type": "MOVED（永久重定向）",
  "explanation": "Redis 说: '槽 12539 归 192.168.3.100:7002 管，你去那里。请更新你的槽位映射表，下次直接来找我。'",
  "vs_ASK": "MOVED 是永久重定向——客户端必须更新本地槽表。ASK 是临时重定向——槽正在迁移中，客户端不更新槽表，但要带 ASKING 命令。"
}
```

**做了什么**：算出 `testkey` 的 slot 是 12539，正确节点是 7002。故意把 `GET testkey` 发到 7000——Redis 返回 `MOVED 12539 192.168.3.100:7002`。

**实锤了什么**：答案里说的"MOVED 是永久重定向，表示槽已迁移，客户端应更新本地 slot 映射"——你亲手触发了这个错误。`MOVED_response` 字段就是 Redis 原始返回。

---

## 实验 4：为什么是 16384

```bash
curl http://localhost:8080/api/q3/why-16384 | python3 -m json.tool
```

返回三个原因：心跳包 2KB、CRC16 低 14 位、千节点粒度。还有一个对比表——如果 8192、如果 65536 各有什么问题。

**实锤了什么**：答案里说"心跳消息用位图结构，2KB 刚好满足 16384 位，在消息大小和网络开销间平衡"——`reasons` 里的第一条就是。

---

## 面试话术

> "我在本地用 Docker 搭了 6 节点 Redis 7.2.5 Cluster，写了 CRC-16-CCITT 的 Java 实现和 Redis 的 CLUSTER KEYSLOT 逐位对比，结果一致。还故意把 GET 发错节点，亲眼看到了 `MOVED 12539 192.168.3.100:7002`——Redis 告诉你'槽不归我管，去那边'。16384 这个数字我也算过：16384 bits = 2KB，正好一个以太网帧，CRC16 输出 16 位取低 14 位。源码在 `cluster.h` 里写死了 `#define CLUSTER_SLOTS 16384`。"
