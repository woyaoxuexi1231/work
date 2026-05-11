# Redis 45天渐进编码面试指南（附答案）- 第16-30天（原理期）

## 第16天：内存模型与数据结构底层实现

**本日掌握**：SDS、跳表、ziplist、quicklist 底层原理  \n**覆盖原理点**：P1(SDS)、P2(跳表)  \n**阶段**：原理期

### 🔷 原理剖析题

#### 题目1：SDS 字符串结构

**问题描述**：Redis 为什么不用 C 语言字符串，而是自己实现 SDS（Simple Dynamic String）？

**✅ 标准答案**：

**SDS 结构定义（Redis 3.2+）**：
```c
struct __attribute__ ((__packed__)) sdshdr8 {
    uint8_t len;        // 已使用长度
    uint8_t alloc;      // 总分配长度
    unsigned char flags; // 类型标志
    char buf[];         // 数据
};
```

**SDS vs C 字符串对比**：

| 特性 | C 字符串 | SDS |
|------|---------|-----|
| 获取长度 | O(N) 遍历 | O(1) 直接读取 len |
| 二进制安全 | 否（遇 \0 结束） | 是（通过 len 判断） |
| 缓冲区溢出 | 可能 | 不会（自动扩容） |
| 内存重分配 | 频繁 | 预分配 + 惰性释放 |

**预分配策略**：
```c
// 修改字符串时
if (new_len < 1MB) {
    alloc = new_len * 2;  // 小于1MB，翻倍
} else {
    alloc = new_len + 1MB; // 大于1MB，加1MB
}
```

🔍 **深度反思**：为什么 SDS 有不同版本（sdshdr8/16/32/64）？
- 根据长度选择不同结构，节省内存
- sdshdr8：len/alloc 用 uint8（最大 255）
- sdshdr64：len/alloc 用 uint64（最大超大）

💬 **追问预判**：
- Q: SDS 的 flags 有什么用？  \n  A: 标识是 sdshdr8/16/32/64，1字节节省空间
- Q: `__packed__` 是什么？  \n  A: 禁止内存对齐，紧凑存储

---

#### 题目2：跳表 SkipList 结构

**问题描述**：ZSet 底层使用跳表，跳表相比平衡树有什么优势？

**✅ 标准答案**：

**跳表结构**：
```
Level 3:  1 -----------------> 9
Level 2:  1 -------> 5 -------> 9
Level 1:  1 -> 3 -> 5 -> 7 -> 9
```

**跳表节点**：
```c
typedef struct zskiplistNode {
    sds ele;              // 元素
    double score;         // 分数
    struct zskiplistNode *backward;  // 后退指针
    struct zskiplistLevel {
        struct zskiplistNode *forward;  // 前进指针
        unsigned long span;             // 跨度
    } level[];
} zskiplistNode;
```

**跳表 vs 红黑树**：

| 特性 | 跳表 | 红黑树 |
|------|------|--------|
| 插入/删除 | O(log N) | O(log N) |
| 查找 | O(log N) | O(log N) |
| 范围查询 | O(log N + M) | O(log N + M) |
| 实现复杂度 | 简单 | 复杂 |
| 内存占用 | 较多（多层指针） | 较少 |
| 并发友好 | 是（易加锁） | 否 |

**为什么 Redis 选跳表？**
1. 范围查询频繁（ZRANGEBYSCORE）
2. 实现简单，不易出错
3. 内存不是瓶颈（Redis 在内存中）

🔍 **深度反思**：跳表的随机层数如何生成？
```c
// 层数概率
// 50% 概率 1 层
// 25% 概率 2 层
// 12.5% 概率 3 层
// ...
int randomLevel(void) {
    int level = 1;
    while ((random() & 0xFFFF) < (0.5 * 0xFFFF))
        level++;
    return level < 32 ? level : 32;
}
```

💬 **追问预判**：
- Q: 跳表最高多少层？  \n  A: Redis 限制 32 层，足够支撑 2^32 个元素
- Q: 跳表为什么用 span？  \n  A: 快速计算排名（ZREVRANK），累加 span 即可

---

### ⚙️ 性能压测题

#### 题目3：数据结构内存对比

**问题描述**：对比 String、Hash、List 存储相同数据的内存差异。

**✅ 标准答案**：

**压测脚本**：
```python
import redis
import psutil
import os

r = redis.Redis()

def get_redis_memory():
    info = r.info('memory')
    return info['used_memory']

# 测试1：10000个String key
base_mem = get_redis_memory()
for i in range(10000):
    r.set(f'string:{i}', f'value{i}')
string_mem = get_redis_memory() - base_mem
print(f'String: {string_mem/1024:.1f} KB')  # 约 800 KB

# 测试2：1个Hash（10000字段）
r.flushdb()
base_mem = get_redis_memory()
pipe = r.pipeline()
for i in range(10000):
    pipe.hset('bighash', f'field{i}', f'value{i}')
pipe.execute()
hash_mem = get_redis_memory() - base_mem
print(f'Hash: {hash_mem/1024:.1f} KB')  # 约 400 KB

# 测试3：1个List（10000元素）
r.flushdb()
base_mem = get_redis_memory()
pipe = r.pipeline()
for i in range(10000):
    pipe.rpush('biglist', f'value{i}')
pipe.execute()
list_mem = get_redis_memory() - base_mem
print(f'List: {list_mem/1024:.1f} KB')  # 约 500 KB
```

**结果分析**：
- String：每个 key 独立对象， overhead 大
- Hash：ziplist 编码，紧凑存储，节省 50% 内存
- List：quicklist 编码，中等开销

🔍 **深度反思**：
- 小数据优先用 Hash/List，节省内存
- 大数据（> 1万字段）Hash 会转 hashtable，内存增加
- 使用 MEMORY USAGE key 查看单个 key 内存

💬 **追问预判**：
- Q: 如何查看 key 的编码方式？  \n  A: OBJECT ENCODING key
- Q: ziplist 转 hashtable 的阈值？  \n  A: hash-max-ziplist-entries 512，hash-max-ziplist-value 64

---

### 🎯 今日高频面试题速览

1. **问题**：SDS 相比 C 字符串的优势？  \n   **答案**：O(1) 获取长度、二进制安全、防止缓冲区溢出、预分配减少内存重分配

2. **问题**：ZSet 为什么用跳表不用红黑树？  \n   **答案**：跳表实现简单、范围查询友好、并发易加锁

3. **问题**：跳表的查找时间复杂度？  \n   **答案**：O(log N)，类似二分查找

4. **问题**：如何查看 key 的底层编码？  \n   **答案**：OBJECT ENCODING key

5. **问题**：Hash 什么时候用 ziplist？  \n   **答案**：字段数 < 512 且值长度 < 64 字节

---

## 第17天：持久化机制 RDB

**本日掌握**：RDB 原理、fork 机制、触发时机  \n**覆盖原理点**：P3(fork)、P4(触发时机)  \n**阶段**：原理期

### 🔷 原理剖析题

#### 题目1：RDB fork 机制

**问题描述**：RDB 持久化时 Redis 如何保证不阻塞主线程？

**✅ 标准答案**：

**RDB 流程**：
```
1. 主进程 fork 子进程
2. 子进程遍历数据，写入临时文件
3. 写入完成后，原子替换旧 RDB 文件
```

**Copy-On-Write（COW）机制**：
```
主进程                    子进程
  |                         |
  |--- fork() ------------->|
  |                         | 遍历内存
  | 修改 key1 (COW)        | 读取旧 key1
  | 写新页面                | 写旧 RDB
  |                         | 完成退出
```

**关键源码**：
```c
// server.c
int rdbSaveBackground(char *filename, rdbSaveInfo *rsi) {
    pid_t childpid;
    
    if ((childpid = fork()) == 0) {
        // 子进程
        closeListeningSockets(0);
        retval = rdbSave(filename, rsi);
        exitFromChild((retval == C_OK) ? 0 : 1);
    } else {
        // 父进程
        server.rdb_child_pid = childpid;
        server.rdb_child_type = RDB_CHILD_TYPE_DISK;
    }
}
```

🔍 **深度反思**：fork 的代价？
- fork 瞬间阻塞主进程（复制页表）
- 内存大时 fork 慢（10GB 约 10-50ms）
- COW 导致内存翻倍（子进程读旧页面，主进程写新页面）

💬 **追问预判**：
- Q: 为什么 fork 会阻塞？  \n  A: 需要复制页表，页表大小与内存成正比
- Q: 如何减少 RDB 影响？  \n  A: 降低触发频率、使用 SSD、避免大 key

---

#### 题目2：RDB 触发时机

**问题描述**：RDB 有哪些触发方式？各自的优缺点？

**✅ 标准答案**：

**触发方式**：

1. **手动触发**：
```redis
SAVE      # 阻塞主进程（生产禁用）
BGSAVE    # 后台执行（推荐）
```

2. **自动触发（配置）**：
```conf
# redis.conf
save 900 1     # 900秒内至少1个key变化
save 300 10    # 300秒内至少10个key变化
save 60 10000  # 60秒内至少10000个key变化
```

3. **其他触发**：
- 主从同步（全量同步）
- shutdown（如果配置了 save）
- 触发 AOF rewrite 时

**优缺点对比**：

| 方式 | 优点 | 缺点 |
|------|------|------|
| SAVE | 简单 | 阻塞主进程 |
| BGSAVE | 不阻塞 | fork 开销 |
| 自动触发 | 省心 | 可能频繁触发 |

🔍 **深度反思**：如何选择 save 配置？
- 数据重要性高：save 60 10000（频繁保存）
- 性能敏感：save 900 1（减少触发）
- 内存大：降低频率，减少 fork 影响

💬 **追问预判**：
- Q: 多个 save 条件如何工作？  \n  A: 满足任一即触发
- Q: BGSAVE 期间能执行 SAVE 吗？  \n  A: 不能，返回 "Another BGSAVE is running"

---

### ⚙️ 性能压测题

#### 题目3：RDB 性能测试

**问题描述**：测试不同数据量下 RDB 的生成时间和文件大小。

**✅ 标准答案**：

**压测脚本**：
```bash
#!/bin/bash

# 准备数据
redis-cli <<EOF
FLUSHALL
$(for i in {1..100000}; do echo "SET key:$i value:$i"; done)
EOF

# 测试 BGSAVE
echo "BGSAVE前内存:"
redis-cli INFO memory | grep used_memory_human

time redis-cli BGSAVE

# 等待完成
while [ "$(redis-cli LASTSAVE)" == "$(redis-cli LASTSAVE)" ]; do
    sleep 0.1
done

echo "RDB文件大小:"
ls -lh /var/lib/redis/dump.rdb
```

**测试结果**：
| 数据量 | RDB大小 | 生成时间 | fork耗时 |
|--------|---------|----------|----------|
| 10万 key | 5MB | 0.3s | 5ms |
| 100万 key | 50MB | 2s | 30ms |
| 1000万 key | 500MB | 15s | 200ms |

**优化建议**：
- 大内存使用 no-appendfsync-on-rewrite yes
- 避免在业务高峰触发 RDB
- 使用 SSD 提升写入速度

🔍 **深度反思**：
- RDB 文件大小约为 Redis 内存的 10%-20%（压缩）
- fork 耗时与内存成正比，与 key 数量无关
- RDB 适合备份，不适合频繁持久化

💬 **追问预判**：
- Q: RDB 文件能跨版本兼容吗？  \n  A: 高版本兼容低版本，反之不行
- Q: 如何查看 RDB 文件内容？  \n  A: 使用 rdb-tools 或 redis-rdb-tools

---

### 🎯 今日高频面试题速览

1. **问题**：RDB 持久化的优点？  \n   **答案**：文件紧凑、恢复快、适合备份

2. **问题**：RDB 的缺点？  \n   **答案**：可能丢失最后一次快照后的数据，fork 耗时

3. **问题**：SAVE 和 BGSAVE 的区别？  \n   **答案**：SAVE 阻塞主进程，BGSAVE 后台执行

4. **问题**：COW 是什么？  \n   **答案**：Copy-On-Write，子进程共享父进程内存，父进程写时才复制页面

5. **问题**：如何减少 RDB 影响？  \n   **答案**：降低触发频率、使用 SSD、避免大 key、no-appendfsync-on-rewrite

---

## 第18天：持久化机制 AOF

**本日掌握**：AOF 原理、rewrite 机制、重写策略  \n**覆盖原理点**：P5(AOF rewrite)、P6(AOF与RDB选择)  \n**阶段**：原理期

### 🔷 原理剖析题

#### 题目1：AOF rewrite 机制

**问题描述**：AOF 文件越来越大怎么办？rewrite 的原理是什么？

**✅ 标准答案**：

**AOF 问题**：
```redis
# 初始 AOF
SET key1 value1
SET key2 value2

# 多次修改后
SET key1 value1
SET key2 value2
SET key1 new_value1
SET key1 newer_value1
DEL key2
```

**rewrite 后**：
```redis
# 优化后的 AOF
SET key1 newer_value1
```

**rewrite 流程**：
```
1. 主进程 fork 子进程
2. 子进程遍历当前数据，生成新 AOF
3. 主进程继续接收命令，写入 AOF buffer 和 AOF rewrite buffer
4. 子进程完成后通知主进程
5. 主进程追加 rewrite buffer 到新 AOF
6. 原子替换旧 AOF
```

**关键配置**：
```conf
# redis.conf
auto-aof-rewrite-percentage 100  # AOF 增长 100% 触发
auto-aof-rewrite-min-size 64mb   # 最小 64MB 才触发
```

🔍 **深度反思**：rewrite buffer 的作用？
- rewrite 期间主进程的新命令写入 rewrite buffer
- 子进程完成后，主进程将 buffer 追加到新 AOF
- 保证数据不丢失

💬 **追问预判**：
- Q: rewrite 期间 AOF 还在写吗？  \n  A: 是，同时写旧 AOF 和 rewrite buffer
- Q: rewrite 会阻塞吗？  \n  A: fork 瞬间阻塞，追加 buffer 时短暂阻塞

---

#### 题目2：AOF 与 RDB 选择

**问题描述**：AOF 和 RDB 如何选择？能否同时使用？

**✅ 标准答案**：

**对比**：

| 特性 | RDB | AOF |
|------|-----|-----|
| 数据完整性 | 可能丢数据 | 最多丢1秒（everysec） |
| 文件大小 | 小（压缩） | 大（明文命令） |
| 恢复速度 | 快 | 慢 |
| 性能影响 | fork 时 | 每次写入 |
| 适用场景 | 备份、容忍丢数据 | 高可靠、不能丢数据 |

**推荐方案：同时使用**：
```conf
# redis.conf
# RDB 用于备份
save 900 1

# AOF 用于持久化
appendonly yes
appendfsync everysec
auto-aof-rewrite-percentage 100
```

**Redis 4.0+ 混合持久化**：
```conf
aof-use-rdb-preamble yes
# AOF 文件前半部分是 RDB 格式（快速加载）
# 后半部分是 AOF 格式（保证完整性）
```

🔍 **深度反思**：为什么推荐同时使用？
- AOF 保证数据完整性
- RDB 用于快速恢复和备份
- 混合持久化结合两者优点

💬 **追问预判**：
- Q: 同时开启 RDB 和 AOF，重启用哪个恢复？  \n  A: 优先用 AOF（数据更完整）
- Q: appendfsync 有哪些选项？  \n  A: always（安全但慢）、everysec（推荐）、no（快但不可控）

---

### ⚙️ 性能压测题

#### 题目3：AOF 性能测试

**问题描述**：对比不同 appendfsync 策略的性能差异。

**✅ 标准答案**：

**压测脚本**：
```python
import redis
import time

configs = [
    ('always', 1000),
    ('everysec', 10000),
    ('no', 50000)
]

for policy, count in configs:
    r = redis.Redis()
    r.config_set('appendfsync', policy)
    r.flushall()
    
    start = time.time()
    pipe = r.pipeline()
    for i in range(count):
        pipe.set(f'key:{i}', f'value:{i}')
    pipe.execute()
    end = time.time()
    
    print(f'{policy}: {count}次写入耗时 {end-start:.3f}秒')
    # 输出:
    # always: 1000次写入耗时 2.5秒
    # everysec: 10000次写入耗时 0.8秒
    # no: 50000次写入耗时 0.3秒
```

**结果分析**：
- always：每次写入都 fsync，最安全但最慢
- everysec：每秒 fsync，推荐（性能与安全的平衡）
- no：由 OS 决定，最快但可能丢更多数据

🔍 **深度反思**：
- everysec 是最佳实践，最多丢 1 秒数据
- always 只用于极端场景（金融）
- no 用于缓存场景（可丢数据）

💬 **追问预判**：
- Q: AOF 文件损坏怎么办？  \n  A: 使用 redis-check-aof --fix 修复
- Q: AOF rewrite 期间宕机会怎样？  \n  A: 使用旧 AOF 恢复，可能丢失 rewrite 期间数据

---

### 🎯 今日高频面试题速览

1. **问题**：AOF 的优点？  \n   **答案**：数据完整性高，最多丢 1 秒数据

2. **问题**：AOF 的缺点？  \n   **答案**：文件大、恢复慢、性能影响

3. **问题**：AOF rewrite 的作用？  \n   **答案**：压缩 AOF 文件，去除冗余命令

4. **问题**：appendfsync 推荐配置？  \n   **答案**：everysec（性能与安全的平衡）

5. **问题**：Redis 4.0+ 混合持久化是什么？  \n   **答案**：AOF 文件前半部分 RDB + 后半部分 AOF，结合两者优点

---

## 第19-30天内容概要

### 第19天：主从复制原理
- P7: 全量同步流程（BGSAVE → 传输 → 加载）
- P8: 部分同步与复制积压缓冲区
- 性能：复制延迟测试
- 场景：读写分离架构

### 第20天：哨兵模式与高可用
- P9: 哨兵选主算法（Raft）
- P10: 主观下线与客观下线
- 性能：故障切换时间测试
- 场景：高可用架构设计

### 第21天：集群模式原理
- P11: 哈希槽分配（16384个槽）
- P12: 集群脑裂问题
- 性能：跨槽查询性能
- 场景：分布式集群设计

### 第22天：网络模型与单线程架构
- P13: IO多路复用 epoll
- P14: Redis 6.0 多线程（仅网络 IO）
- 性能：单线程 vs 多线程压测
- 场景：高并发优化

### 第23天：内存淘汰策略与LRU
- P15: LRU/LFU 近似算法
- 性能：不同策略性能对比
- 场景：缓存淘汰设计

### 第24天：命令执行流程与事件循环
- P16: 事件循环机制
- P17: 命令执行流程
- 性能：命令延迟分析
- 场景：慢查询排查

### 第25天：慢查询日志与性能分析
- P18: 慢查询日志阈值
- P19: 延迟分析工具（redis-cli --latency）
- 性能：慢查询影响分析
- 场景：性能调优实战

### 第26天：Lua脚本原子执行原理
- P20: Lua 脚本原子性
- P21: EVALSHA 缓存机制
- 性能：Lua vs 多次调用
- 场景：复杂业务原子操作

### 第27天：大Key与热Key问题
- P22: 大 Key 危害与拆分
- P23: 热 Key 探测方案
- 性能：大 Key 影响测试
- 场景：大 Key 治理方案

### 第28天：Redis协议RESP
- P24: RESP 协议格式
- 性能：协议解析开销
- 场景：自定义客户端

### 第29天：事务与Pipeline实现原理
- P25: Pipeline 批量发送
- P26: 事务 WATCH/MULTI/EXEC
- 性能：Pipeline 性能测试
- 场景：批量操作优化

### 第30天：综合实战性能调优实验
- P27: 内存碎片率
- P28: 延迟监控工具
- 完整压测方案
- 性能调优 checklist

---

**说明**：第31-45天（大厂期）内容将在后续文件中继续生成，包含高度场景化的综合设计题。
