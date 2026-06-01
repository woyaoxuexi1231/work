# Q5: 热 Key 排查——操作 + 代码混合

> 操作部分：redis-cli 排查四步法。代码部分：Caffeine 多级缓存亲眼看到 L2→L1 跃迁。

---

## 启动

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=cluster   # 或 standalone
```

---

## 操作部分：排查四步法

### 步骤 1：SLOWLOG——定位慢命令

```bash
redis-cli -h 192.168.3.100 -p 7000 -a 123456 SLOWLOG GET 5
```

输出示例：

```
1) 1) (integer) 127
   2) (integer) 1685000000
   3) (integer) 152000          ← 152ms！远超 10ms 阈值
   4) 1) "KEYS"
      2) "user:session:*"       ← 元凶：KEYS * 遍历全库
   5) "192.168.3.50:54321"
```

**对应面试点**："先看慢日志——`SLOWLOG GET` 定位慢命令，分析是否由大 key、不恰当命令引起"。

### 步骤 2：--bigkeys——找大 Key

```bash
redis-cli -h 192.168.3.100 -p 7000 -a 123456 --bigkeys
```

输出会显示每种类型最大的 Key。关注 `Biggest string` 和 `Biggest hash`。

**对应面试点**：大 Key 会导致 `HGETALL` 阻塞主线程、`MIGRATE` 时主线程卡死。

### 步骤 3：MEMORY STATS——内存碎片

```bash
redis-cli -h 192.168.3.100 -p 7000 -a 123456 MEMORY STATS | grep fragmentation
```

`mem_fragmentation_ratio > 1.5` → 内存碎片严重。

**对应面试点**："`MEMORY USAGE` 和 `MEMORY STATS` 诊断内存碎片"。

### 步骤 4：一键脚本

```bash
bash scripts/q5-scan.sh 192.168.3.100 7000
```

---

## 代码部分：多级缓存——亲眼看到 L2→L1

### 实验：连续读 3 次同一个 key

```bash
# 0. 先写入 Redis
curl -X POST 'http://localhost:8080/api/q5/cache/set?key=hot:item&value=99'

# 1. 清空 Caffeine
curl -X POST http://localhost:8080/api/q5/cache/clear

# 2. 第 1 次读——L2-Redis
curl -X POST 'http://localhost:8080/api/q5/cache/get?key=hot:item'
# → {"level": "L2-Redis", "latency": "~1ms", "note": "已回填 L1——下次请求将命中 Caffeine"}
```

```bash
# 3. 第 2 次读——L1-Caffeine（微秒级！）
curl -X POST 'http://localhost:8080/api/q5/cache/get?key=hot:item'
# → {"level": "L1-Caffeine", "latency": "< 1μs"}
```

```bash
# 4. 第 3 次读——还是 L1
curl -X POST 'http://localhost:8080/api/q5/cache/get?key=hot:item'
# → {"level": "L1-Caffeine", "latency": "< 1μs"}
```

```bash
# 5. 看命中率
curl http://localhost:8080/api/q5/cache/stats
# → {"l1Hits": 2, "l2Hits": 1, "hitRate": "100.0%"}
```

**三次请求的变化**：

| 次数 | 命中层级 | 延迟 | 发生了什么 |
|------|---------|------|-----------|
| 第 1 次 | L2-Redis | ~1ms（网络） | Caffeine miss → Redis hit → 回填 Caffeine |
| 第 2 次 | L1-Caffeine | <1μs（内存） | Caffeine 直接命中 |
| 第 3 次 | L1-Caffeine | <1μs | 仍然命中 |

**实锤了答案里的**："热 Key 读——用本地 Caffeine 缓存，卸载 99% 读流量。微秒级 vs 毫秒级——这就是多级缓存的价值。"

---

## 排查四步法总结

| 步骤 | 命令 | 对应面试答案 |
|------|------|------------|
| 1. 监控定界 | Grafana CPU/QPS | "先看监控确认是否为单节点热点" |
| 2. 慢日志 | `SLOWLOG GET` | "定位慢命令，分析大 key 或不恰当命令" |
| 3. 大/热 Key | `--bigkeys` / `--hotkeys` | "找出 top key 及内存占用" |
| 4. 长效方案 | Caffeine 多级缓存 | "本地缓存卸载读流量 + key 拆分 + 前端限流" |

---

## 面试话术

> "排查链路：Grafana 看 CPU 单节点 90% → SLOWLOG 发现 `KEYS *` 和 `HGETALL` 大 key → `--bigkeys` 确认 15MB 购物车 key → 业务代码定位。应急：对该热 Key 的读流量，我在应用层加了一层 Caffeine 本地缓存，第 2 次请求就从毫秒级降到微秒级——`/api/q5/cache/stats` 接口能看到命中率从 0 到 100% 的跃迁。这就是答案里说的'多级缓存是热 Key 的长效方案'。"
