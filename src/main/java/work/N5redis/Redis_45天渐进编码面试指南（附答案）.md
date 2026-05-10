# Redis 45天渐进编码面试冲刺指南（含标准答案）

## 总纲

### 45天知识图谱

| 天数 | 阶段 | 知识点 | 覆盖原理项 | 有性能题 |
|------|------|--------|-----------|----------|
| 1 | 使用期 | String 基本操作 | 无 | 否 |
| 2 | 使用期 | Hash 数据结构与应用 | 无 | 否 |
| 3 | 使用期 | List 数据结构与应用 | 无 | 否 |
| 4 | 使用期 | Set 数据结构与应用 | 无 | 否 |
| 5 | 使用期 | ZSet 数据结构与应用 | 无 | 否 |
| 6 | 使用期 | 键过期与淘汰策略 | 无 | 否 |
| 7 | 使用期 | 发布订阅模式 | 无 | 否 |
| 8 | 使用期 | 事务与管道 | 无 | 否 |
| 9 | 使用期 | 位图BitMap操作 | 无 | 否 |
| 10 | 使用期 | HyperLogLog基数统计 | 无 | 否 |
| 11 | 使用期 | Geo地理空间操作 | 无 | 否 |
| 12 | 使用期 | Stream数据结构 | 无 | 否 |
| 13 | 使用期 | Redis命令高级组合 | 无 | 否 |
| 14 | 使用期 | Spring Data Redis使用 | 无 | 否 |
| 15 | 使用期 | 综合实战缓存+计数器+排行榜 | 无 | 否 |
| 16 | 原理期 | 内存模型与数据结构底层 | P1,P2 | 是 |
| 17 | 原理期 | 持久化机制RDB | P3,P4 | 是 |
| 18 | 原理期 | 持久化机制AOF | P5,P6 | 是 |
| 19 | 原理期 | 主从复制原理 | P7,P8 | 是 |
| 20 | 原理期 | 哨兵模式与高可用 | P9,P10 | 是 |
| 21 | 原理期 | 集群模式原理 | P11,P12 | 是 |
| 22 | 原理期 | 网络模型与单线程架构 | P13,P14 | 是 |
| 23 | 原理期 | 内存淘汰策略与LRU | P15 | 是 |
| 24 | 原理期 | 命令执行流程与事件循环 | P16,P17 | 是 |
| 25 | 原理期 | 慢查询日志与性能分析 | P18,P19 | 是 |
| 26 | 原理期 | Lua脚本原子执行原理 | P20,P21 | 是 |
| 27 | 原理期 | 大Key与热Key问题 | P22,P23 | 是 |
| 28 | 原理期 | Redis协议RESP | P24 | 是 |
| 29 | 原理期 | 事务与Pipeline实现原理 | P25,P26 | 是 |
| 30 | 原理期 | 综合实战性能调优实验 | P27,P28 | 是 |
| 31 | 大厂期 | 缓存穿透击穿雪崩 | P29,P30 | 是 |
| 32 | 大厂期 | 缓存一致性设计 | P31,P32 | 是 |
| 33 | 大厂期 | 分布式锁深度实战 | P33,P34 | 是 |
| 34 | 大厂期 | 限流器设计与实现 | P35,P36 | 是 |
| 35 | 大厂期 | 排行榜与实时统计 | P37,P38 | 是 |
| 36 | 大厂期 | 会话共享与分布式Session | P39,P40 | 是 |
| 37 | 大厂期 | 延迟队列与任务调度 | P41,P42 | 是 |
| 38 | 大厂期 | 布隆过滤器实战 | P43,P44 | 是 |
| 39 | 大厂期 | 实时消息系统架构 | P45,P46 | 是 |
| 40 | 大厂期 | 全链路缓存架构设计 | P47,P48 | 是 |
| 41 | 大厂期 | 多级缓存与CDN协同 | P49,P50 | 是 |
| 42 | 大厂期 | Redis监控与告警系统 | P51,P52 | 是 |
| 43 | 大厂期 | 数据迁移与扩容方案 | P53,P54 | 是 |
| 44 | 大厂期 | 云Redis选型与对比 | P55,P56 | 是 |
| 45 | 大厂期 | 万级并发短URL服务 | P57,P58 | 是 |

### 面试必考原理总清单

1. P1: SDS字符串结构（第16天）
2. P2: 跳表SkipList结构（第16天）
3. P3: RDB fork机制（第17天）
4. P4: RDB触发时机（第17天）
5. P5: AOF rewrite机制（第18天）
6. P6: AOF重写与RDB选择（第18天）
7. P7: 全量同步流程（第19天）
8. P8: 部分同步与复制积压缓冲区（第19天）
9. P9: 哨兵选主算法（第20天）
10. P10: 哨兵主观下线与客观下线（第20天）
11. P11: 哈希槽分配（第21天）
12. P12: 集群脑裂问题（第21天）
13. P13: IO多路复用epoll（第22天）
14. P14: Redis 6.0多线程（第22天）
15. P15: LRU/LFU近似算法（第23天）
16. P16: 事件循环机制（第24天）
17. P17: 命令执行流程（第24天）
18. P18: 慢查询日志阈值（第25天）
19. P19: 延迟分析工具（第25天）
20. P20: Lua脚本原子性（第26天）
21. P21: EVALSHA缓存机制（第26天）
22. P22: 大Key危害与拆分（第27天）
23. P23: 热Key探测方案（第27天）
24. P24: RESP协议格式（第28天）
25. P25: Pipeline批量发送（第29天）
26. P26: 事务WATCH/MULTI/EXEC（第29天）
27. P27: 内存碎片率（第30天）
28. P28: 延迟监控工具（第30天）
29. P29: 布隆过滤器防穿透（第31天）
30. P30: 互斥锁防击穿（第31天）
31. P31: 双写一致性策略（第32天）
32. P32: 延迟双删机制（第32天）
33. P33: Redlock算法（第33天）
34. P34: 锁续期机制（第33天）
35. P35: 漏桶限流（第34天）
36. P36: 令牌桶限流（第34天）
37. P37: ZSet排行榜实现（第35天）
38. P38: 实时统计HyperLogLog（第35天）
39. P39: Session共享方案（第36天）
40. P40: 分布式Session超时（第36天）
41. P41: 延迟队列实现（第37天）
42. P42: 任务调度设计（第37天）
43. P43: 布隆过滤器原理（第38天）
44. P44: 误判率控制（第38天）
45. P45: Stream消息系统（第39天）
46. P46: 消费者组设计（第39天）
47. P47: 全链路缓存架构（第40天）
48. P48: 缓存更新策略（第40天）
49. P49: 多级缓存设计（第41天）
50. P50: CDN缓存策略（第41天）
51. P51: Redis监控指标（第42天）
52. P52: 告警规则设计（第42天）
53. P53: 数据迁移方案（第43天）
54. P54: 在线扩容方案（第43天）
55. P55: 云Redis对比（第44天）
56. P56: 成本优化（第44天）
57. P57: 万级并发架构（第45天）
58. P58: 短URL系统设计（第45天）

---

## 第1天：String 基本操作

**本日掌握**：String 数据类型增删改查、批量操作、原子递增  
**覆盖原理点**：无  
**阶段**：使用期

### 🟢 基础用法题

#### 题目1：用户信息缓存

**问题描述**：你需要为电商系统实现用户信息缓存。用户登录后，将用户基本信息（用户名、邮箱、手机号）缓存到 Redis 中，设置 30 分钟过期时间。实现以下功能：
1. 缓存用户信息
2. 查询用户信息
3. 更新用户信息
4. 删除用户缓存

**✅ 标准答案**：

```redis
# 方案1：使用多个String key
SET user:1001:username "张三"
SET user:1001:email "zhangsan@example.com"
SET user:1001:phone "13800138000"
EXPIRE user:1001:username 1800
EXPIRE user:1001:email 1800
EXPIRE user:1001:phone 1800

# 方案2：更优方案 - 使用Hash结构
HMSET user:1001 username "张三" email "zhangsan@example.com" phone "13800138000"
EXPIRE user:1001 1800

# 查询用户信息
GET user:1001:username
GET user:1001:email
GET user:1001:phone

# 或Hash查询
HGETALL user:1001
# 输出:
# 1) "username"
# 2) "张三"
# 3) "email"
# 4) "zhangsan@example.com"
# 5) "phone"
# 6) "13800138000"

# 更新用户信息
SET user:1001:phone "13900139000"
EXPIRE user:1001:phone 1800

# 或Hash更新
HSET user:1001 phone "13900139000"

# 删除用户缓存
DEL user:1001:username user:1001:email user:1001:phone
# 或Hash删除
DEL user:1001
```

🔍 **深度反思**：为什么推荐使用 Hash 而不是多个 String？
- Hash 结构将相关字段组织在一起，节省内存（ziplist 优化）
- 批量操作更简洁，减少网络往返
- 过期时间统一管理，避免部分字段过期部分未过期的问题

💬 **追问预判**：
- Q: Hash 底层什么时候使用 ziplist？  
  A: 当字段数小于 512 且所有值长度小于 64 字节时使用 ziplist，超过则转为 hashtable
- Q: 如果要单独控制每个字段的过期时间怎么办？  
  A: 此时只能用多个 String key 分别设置过期时间

---

### 🟡 中级用法题

#### 题目2：计数器与原子递增

**问题描述**：实现一个文章阅读量统计系统，要求：
1. 每篇文章有一个独立的阅读量计数器
2. 支持原子递增（并发安全）
3. 支持获取当前阅读量
4. 支持重置计数器

**✅ 标准答案**：

```redis
# 原子递增（每次访问+1）
INCR article:1001:views
# 输出: 1

# 再次递增
INCR article:1001:views
# 输出: 2

# 递增指定数值（批量导入历史数据）
INCRBY article:1001:views 1000
# 输出: 1002

# 获取当前阅读量
GET article:1001:views
# 输出: "1002"

# 重置计数器（删除后重新从0开始）
DEL article:1001:views
INCR article:1001:views
# 输出: 1

# 或使用SET重置为特定值
SET article:1001:views 0
```

🔍 **深度反思**：INCR 为什么是原子操作？
- Redis 是单线程处理命令（命令执行阶段），INCR 不会被其他命令打断
- 即使多个客户端同时发送 INCR，Redis 也会排队逐个执行
- 这与数据库的 UPDATE count = count + 1 不同，后者需要显式加锁

💬 **追问预判**：
- Q: 如果 Redis 宕机，计数器会丢失吗？  
  A: 取决于持久化配置。AOF 每秒同步最多丢失1秒数据，RDB 可能丢失较长时间数据
- Q: 如何保证计数器不丢失？  
  A: 启用 AOF + appendfsync everysec，或结合数据库定期同步

---

### 🔴 高级用法题

#### 题目3：批量操作与 Pipeline

**问题描述**：你需要初始化 10000 个商品的库存数据，要求：
1. 使用最高效的方式批量设置
2. 对比普通设置与 Pipeline 的性能差异
3. 实现批量获取

**✅ 标准答案**：

```redis
# 方案1：普通设置（10000次网络往返，极慢）
SET product:1:stock 100
SET product:2:stock 200
...
SET product:10000:stock 150

# 方案2：Pipeline（一次网络往返，极快）
# 在客户端代码中使用
```

**Python 客户端 Pipeline 示例**：

```python
import redis
import time

r = redis.Redis(host='localhost', port=6379)

# Pipeline 批量设置
start = time.time()
pipe = r.pipeline()
for i in range(1, 10001):
    pipe.set(f'product:{i}:stock', i * 10)
pipe.execute()
end = time.time()
print(f'Pipeline设置耗时: {end-start:.3f}秒')  # 约0.1秒

# 普通方式对比
start = time.time()
for i in range(1, 10001):
    r.set(f'product:{i}:stock', i * 10)
end = time.time()
print(f'普通设置耗时: {end-start:.3f}秒')  # 约10秒

# Pipeline 批量获取
pipe = r.pipeline()
for i in range(1, 101):  # 分批获取100个
    pipe.get(f'product:{i}:stock')
results = pipe.execute()
print(results[:5])  # ['10', '20', '30', '40', '50']
```

🔍 **深度反思**：Pipeline 为什么快？
- 普通方式：10000次请求 × (网络延迟 + Redis处理) ≈ 10000 × 1ms = 10s
- Pipeline：1次请求 × (网络延迟 + Redis处理10000条) ≈ 1ms + 100ms = 101ms
- 性能提升约 100 倍，核心是减少了网络往返次数

💬 **追问预判**：
- Q: Pipeline 和事务有什么区别？  
  A: Pipeline 只是批量发送命令，不保证原子性；事务（MULTI/EXEC）保证原子性但不减少网络往返
- Q: Pipeline 过大会有什么问题？  
  A: 单次 Pipeline 过大可能阻塞 Redis（Redis 单线程处理），建议每批 1000-5000 条

---

### 🏢 大厂面试场景实战

**问题描述**：某电商平台大促期间，商品详情页 QPS 达到 50000，数据库无法承受。请设计一个基于 Redis 的商品详情缓存方案，要求：
1. 缓存商品基本信息、库存、价格
2. 支持高并发读取
3. 库存扣减保证一致性
4. 缓存更新策略合理

**✅ 标准答案**：

**架构设计**：
```
客户端 → Nginx → 应用层 → Redis Cluster → MySQL
                     ↓（缓存未命中）
                   MySQL（回源）
```

**核心代码**：

```redis
# 1. 缓存结构（Hash）
HMSET product:1001 name "iPhone 15" price 5999 stock 100 desc "..."
EXPIRE product:1001 3600
```

**Python 应用层实现**：

```python
import redis
import json

r = redis.Redis(host='localhost', port=6379)

def get_product(product_id):
    # 尝试从缓存获取
    product = r.hgetall(f'product:{product_id}')
    if product:
        # 解码字节
        return {k.decode(): v.decode() for k, v in product.items()}
    
    # 缓存未命中，加分布式锁防止缓存击穿
    lock_key = f'lock:product:{product_id}'
    lock = r.lock(lock_key, timeout=10)
    
    if lock.acquire(blocking=False):
        try:
            # 双重检查
            product = r.hgetall(f'product:{product_id}')
            if product:
                return {k.decode(): v.decode() for k, v in product.items()}
            
            # 从数据库查询
            product = db_query_product(product_id)  # 伪代码
            
            # 写入缓存
            if product:
                r.hmset(f'product:{product_id}', product)
                r.expire(f'product:{product_id}', 3600)
            else:
                # 缓存空值防止穿透
                r.set(f'product:{product_id}:null', '1', ex=300)
            
            return product
        finally:
            lock.release()
    else:
        # 未获取到锁，短暂等待后重试
        import time
        time.sleep(0.1)
        return get_product(product_id)

# 库存扣减（Lua脚本保证原子性）
lua_script = """
local stock = redis.call('HGET', KEYS[1], 'stock')
if stock and tonumber(stock) >= tonumber(ARGV[1]) then
    redis.call('HINCRBY', KEYS[1], 'stock', -ARGV[1])
    return 1
end
return 0
"""

def decrease_stock(product_id, quantity):
    result = r.eval(lua_script, 1, f'product:{product_id}', quantity)
    return result == 1
```

🔍 **深度反思**：
- 缓存穿透：缓存空值 + 布隆过滤器
- 缓存击穿：分布式锁 + 双重检查
- 缓存雪崩：过期时间加随机值（3600 ± 300）
- 库存一致性：Lua 脚本原子扣减 + 数据库异步同步

💬 **追问预判**：
- Q: 如果 Redis 集群宕机怎么办？  
  A: 降级到数据库，限流保护；或启用本地缓存（Caffeine）
- Q: 缓存和数据库如何保持一致？  
  A: 采用延迟双删策略，或监听 binlog 异步更新缓存

---

### 🎯 今日高频面试题速览

1. **问题**：Redis 为什么快？  
   **答案**：① 纯内存操作；② 单线程避免上下文切换和锁竞争；③ IO 多路复用 epoll；④ 高效数据结构（SDS、跳表等）

2. **问题**：String 和 Hash 如何选择？  
   **答案**：存储多个相关字段时用 Hash（节省内存、操作方便），单个值或需要单独过期用 String

3. **问题**：INCR 是原子操作吗？  
   **答案**：是，Redis 单线程执行命令，INCR 不会被其他命令打断，天然原子性

4. **问题**：Pipeline 和事务的区别？  
   **答案**：Pipeline 只减少网络往返，不保证原子性；事务（MULTI/EXEC）保证原子性但不减少网络往返

5. **问题**：如何批量设置 10000 个 key？  
   **答案**：使用 Pipeline 批量发送命令，每批 1000-5000 条，避免单次过大阻塞 Redis
