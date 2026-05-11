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

---

## 第2天：Hash 数据结构与应用

**本日掌握**：Hash 数据结构操作、嵌套对象存储、字段级操作  
**覆盖原理点**：无  
**阶段**：使用期

### 🟢 基础用法题

#### 题目1：购物车实现

**问题描述**：使用 Redis Hash 实现购物车功能，要求：
1. 添加商品到购物车（指定数量）
2. 更新商品数量
3. 删除购物车中的商品
4. 获取购物车所有商品

**✅ 标准答案**：

```redis
# 1. 添加商品（用户1001的购物车，商品2001数量3）
HSET cart:1001 2001 3

# 2. 添加多个商品
HMSET cart:1001 2001 3 2002 1 2003 5

# 3. 更新商品数量
HSET cart:1001 2001 10

# 4. 增加数量（原子操作）
HINCRBY cart:1001 2001 2
# 输出: 12

# 5. 删除商品
HDEL cart:1001 2002

# 6. 获取购物车所有商品
HGETALL cart:1001
# 输出:
# 1) "2001"
# 2) "12"
# 3) "2003"
# 4) "5"

# 7. 获取单个商品数量
HGET cart:1001 2001
# 输出: "12"

# 8. 获取购物车商品种类数
HLEN cart:1001
# 输出: 2
```

🔍 **深度反思**：为什么购物车适合用 Hash？
- 一个用户一个 Hash key，字段是商品 ID，值是数量
- 天然支持字段级操作（增删改查单个商品）
- 内存紧凑（ziplist 优化）

💬 **追问预判**：
- Q: 如果要存储商品详细信息（名称、价格）怎么办？  
  A: 购物车只存数量和商品 ID，详细信息从商品缓存获取，避免冗余

---

### 🟡 中级用法题

#### 题目2：用户配置管理

**问题描述**：设计一个用户个性化配置系统，支持：
1. 存储用户的多项配置（主题、语言、通知开关等）
2. 支持批量更新部分配置
3. 支持配置项存在性检查
4. 支持配置导出

**✅ 标准答案**：

```redis
# 1. 初始化用户配置
HMSET user:1001:config theme "dark" language "zh-CN" notify_email 1 notify_sms 0

# 2. 批量更新部分配置
HMSET user:1001:config theme "light" notify_push 1

# 3. 检查配置项是否存在
HEXISTS user:1001:config notify_push
# 输出: 1 (存在)

HEXISTS user:1001:config notify_wechat
# 输出: 0 (不存在)

# 4. 获取所有配置键
HKEYS user:1001:config
# 输出: theme, language, notify_email, notify_sms, notify_push

# 5. 获取所有配置值
HVALS user:1001:config

# 6. 渐进式扫描大 Hash（字段很多时）
HSCAN user:1001:config 0 COUNT 100
```

🔍 **深度反思**：HGETALL 的性能陷阱？
- 当 Hash 字段数超过 1000 时，HGETALL 会阻塞 Redis（单线程）
- 应使用 HSCAN 渐进式遍历，每次返回少量字段
- 但 HSCAN 是增量迭代，可能在迭代过程中数据发生变化

💬 **追问预判**：
- Q: HSCAN 返回的结果可能重复吗？  
  A: 可能，因为底层 hashtable 在 rehash 时会导致元素位置变化
- Q: 如何保证配置更新的原子性？  
  A: 使用 HSET 单条更新是原子的，批量 HSET 也是原子执行

---

### 🔴 高级用法题

#### 题目3：Hash 字段级过期模拟

**问题描述**：Redis 的 Hash 不支持字段级过期，但业务需要实现"某些配置项 10 分钟过期，其他配置项永久有效"。请设计方案。

**✅ 标准答案**：

```redis
# 方案1：独立 key 存储（牺牲内存换取灵活性）
SET user:1001:config:theme "dark"
SET user:1001:config:language "zh-CN"
SET user:1001:config:token "abc123" EX 600  # 10分钟过期

# 方案2：版本号 + 定时清理
HMSET user:1001:config theme "dark" language "zh-CN" token "abc123" token_expire 1712345678
# 应用层定期检查 token_expire，过期则 HDEL token

# 方案3：双 Hash 结构
HMSET user:1001:config:permanent theme "dark" language "zh-CN"
HMSET user:1001:config:temp token "abc123" session_id "xyz789"
EXPIRE user:1001:config:temp 600
```

**应用层方案2实现**：
```python
import redis
import time

r = redis.Redis()

def check_and_clean_expired_fields(user_id):
    config = r.hgetall(f'user:{user_id}:config')
    expire_time = config.get(b'token_expire')
    
    if expire_time and time.time() > int(expire_time):
        pipe = r.pipeline()
        pipe.hdel(f'user:{user_id}:config', 'token', 'token_expire')
        pipe.execute()
```

🔍 **深度反思**：为什么 Redis 不支持字段级过期？
- Hash 底层是一个对象，过期时间绑定在 key 上
- 如果支持字段级过期，需要为每个字段维护过期时间，增加内存开销和复杂度
- Redis 设计哲学：保持简单和高效

💬 **追问预判**：
- Q: 方案1和方案3哪个更好？  
  A: 方案1简单但 key 数量多；方案3结构清晰但需要维护两个 Hash，根据业务选择
- Q: 字段很多时，HGETALL 性能差怎么办？  
  A: 使用 HSCAN 或只获取需要的字段 HGET

---

### 🏢 大厂面试场景实战

**问题描述**：设计一个用户画像标签系统，支持：
1. 存储用户的基础属性（年龄、性别、地区）
2. 存储用户的行为标签（最近购买、浏览品类）
3. 支持标签的快速查询和更新
4. 支持标签的批量导出用于推荐系统

**✅ 标准答案**：

**数据结构设计**：
```redis
# 基础属性（Hash，变化频率低）
HMSET user:1001:profile age 25 gender male region beijing

# 行为标签（Hash，变化频率高）
HMSET user:1001:tags last_purchase "electronics" browse_category "phone,computer,laptop" visit_count 156

# 标签权重（ZSet，支持排序）
ZADD user:1001:tag_weights 0.9 electronics 0.7 phone 0.5 computer 0.3 laptop
```

**核心操作**：
```redis
# 1. 更新标签
HSET user:1001:tags last_purchase "clothing"

# 2. 增加标签权重
ZINCRBY user:1001:tag_weights 0.1 electronics

# 3. 查询 Top 5 标签
ZREVRANGE user:1001:tag_weights 0 4 WITHSCORES

# 4. 批量导出（应用层）
def export_user_profile(user_id):
    profile = r.hgetall(f'user:{user_id}:profile')
    tags = r.hgetall(f'user:{user_id}:tags')
    tag_weights = r.zrevrange(f'user:{user_id}:tag_weights', 0, -1, withscores=True)
    
    return {
        'profile': profile,
        'tags': tags,
        'top_tags': tag_weights[:10]
    }
```

🔍 **深度反思**：
- 分离基础属性和行为标签，避免频繁更新影响稳定数据
- 使用 ZSet 存储标签权重，天然支持排序查询
- 批量导出使用 Pipeline 减少网络往返

💬 **追问预判**：
- Q: 用户量达到百万时，这种设计有问题吗？  
  A: 单个用户的 key 数量可控（3-5个），百万用户约 500 万个 key，Redis 轻松支撑
- Q: 如何实时更新标签？  
  A: 使用 Redis Stream 记录行为事件，定时聚合更新标签

---

### 🎯 今日高频面试题速览

1. **问题**：Hash 底层实现是什么？  
   **答案**：ziplist（紧凑）或 hashtable（标准），字段少且值小时用 ziplist，否则转为 hashtable

2. **问题**：HGETALL 和 HSCAN 的区别？  
   **答案**：HGETALL 一次性返回所有字段，字段多时阻塞；HSCAN 渐进式遍历，不会阻塞但结果可能重复

3. **问题**：Hash 支持字段级过期吗？  
   **答案**：不支持，过期时间绑定在 key 上，可通过独立 key 或应用层定时清理模拟

4. **问题**：什么场景适合用 Hash？  
   **答案**：对象存储（用户信息、商品详情）、购物车、配置管理等需要字段级操作的场景

5. **问题**：HINCRBY 是原子操作吗？  
   **答案**：是，Redis 单线程执行，HINCRBY 不会被其他命令打断

---

## 第3天：List 数据结构与应用

**本日掌握**：List 数据结构操作、队列/栈实现、阻塞操作  
**覆盖原理点**：无  
**阶段**：使用期

### 🟢 基础用法题

#### 题目1：消息队列基础

**问题描述**：使用 Redis List 实现一个简单的消息队列，支持：
1. 生产者发送消息
2. 消费者获取消息
3. 查看队列长度
4. 清空队列

**✅ 标准答案**：

```redis
# 1. 生产者发送消息（从右侧推入）
RPUSH queue:orders "order_1001"
RPUSH queue:orders "order_1002" "order_1003"

# 2. 消费者获取消息（从左侧弹出，FIFO）
LPOP queue:orders
# 输出: "order_1001"

# 3. 查看队列长度
LLEN queue:orders
# 输出: 2

# 4. 查看队列内容（不删除）
LRANGE queue:orders 0 -1
# 输出: "order_1002", "order_1003"

# 5. 清空队列
DEL queue:orders

# 6. 从左侧推入（实现栈 LIFO）
LPUSH queue:orders "order_1004"
```

🔍 **深度反思**：为什么 List 适合做队列？
- List 底层是双向链表（quicklist），LPUSH/RPOP 或 RPUSH/LPOP 都是 O(1)
- 天然支持 FIFO 或 LIFO
- 但 List 不支持优先级、延迟、重试等高级特性

💬 **追问预判**：
- Q: LPOP 时队列为空会怎样？  
  A: 返回 nil，不会阻塞
- Q: 如何实现阻塞队列？  
  A: 使用 BLPOP/BRPOP，队列为空时阻塞等待

---

### 🟡 中级用法题

#### 题目2：最近浏览记录

**问题描述**：实现用户最近浏览的 50 个商品记录，要求：
1. 新的浏览记录添加到最前面
2. 最多保留 50 条记录
3. 如果商品已存在，先删除旧的再添加新的（保证不重复且最新）
4. 支持查询所有浏览记录

**✅ 标准答案**：

```redis
# 1. 添加浏览记录（先删除旧记录，再添加到头部）
LREM user:1001:history 0 "product_2001"
LPUSH user:1001:history "product_2001"
LTRIM user:1001:history 0 49

# 2. 查询浏览记录
LRANGE user:1001:history 0 49
```

**Lua 脚本保证原子性**：
```redis
EVAL "local key=KEYS[1]; local product=ARGV[1]; local max_len=tonumber(ARGV[2]); redis.call('LREM',key,0,product); redis.call('LPUSH',key,product); redis.call('LTRIM',key,0,max_len-1); return 1" 1 user:1001:history product_2001 50
```

🔍 **深度反思**：为什么使用 Lua 脚本？
- LREM + LPUSH + LTRIM 三条命令需要原子执行
- 如果不使用 Lua，并发情况下可能导致重复或超过 50 条
- Lua 脚本在 Redis 中是原子执行的

💬 **追问预判**：
- Q: LREM 的时间复杂度是多少？  
  A: O(N)，N 是列表长度，需要遍历查找
- Q: 如果用户量很大，这种设计有问题吗？  
  A: 每个用户一个 List key，百万用户约百万个 key，内存占用可控

---

### 🔴 高级用法题

#### 题目3：阻塞队列与超时处理

**问题描述**：实现一个任务处理系统，要求：
1. 生产者批量添加任务
2. 消费者阻塞等待任务（最多等待 30 秒）
3. 超时后优雅退出
4. 支持多消费者并发

**✅ 标准答案**：

```redis
# 1. 生产者添加任务
RPUSH queue:tasks "task_1" "task_2" "task_3"

# 2. 消费者阻塞等待（阻塞 30 秒）
BLPOP queue:tasks 30
# 有任务时输出: queue:tasks, task_1
# 超时输出: nil

# 3. 多消费者并发（多个客户端同时执行 BLPOP）
# 客户端1: BLPOP queue:tasks 30 → 获取 task_1
# 客户端2: BLPOP queue:tasks 30 → 获取 task_2
# 自动负载均衡
```

**Python 消费者示例**：
```python
import redis

r = redis.Redis()
while True:
    result = r.blpop('queue:tasks', timeout=30)
    if result:
        queue_name, task = result
        print(f'处理任务: {task.decode()}')
        process_task(task)  # 处理任务
    else:
        print('超时退出')
        break
```

🔍 **深度反思**：BLPOP 的底层实现？
- 当 List 为空时，客户端连接会被加入等待列表
- 当有新元素推入时，Redis 唤醒等待的客户端
- 使用 IO 多路复用，不会占用额外线程

💬 **追问预判**：
- Q: 多个消费者同时 BLPOP 会怎样？  
  A: 只有一个消费者能获取到任务，其他继续阻塞
- Q: 如何保证任务不丢失？  
  A: 消费者处理完成后才 LPOP，或使用 BRPOPLPUSH 实现可靠队列

---

### 🏢 大厂面试场景实战

**问题描述**：设计一个订单异步处理系统，支持：
1. 用户下单后，订单信息进入队列
2. 多个 worker 并发处理订单（库存扣减、发送通知等）
3. 保证订单处理不丢失、不重复
4. 支持失败重试和死信队列

**✅ 标准答案**：

**架构设计**：
```
下单服务 → RPUSH queue:orders → Worker1/Worker2/Worker3 (BRPOPLPUSH)
                                          ↓ 成功
                                    订单完成
                                          ↓ 失败（重试3次）
                                    queue:orders:retry
                                          ↓ 重试失败
                                    queue:orders:deadletter
```

**核心代码**：
```redis
# 1. 下单服务：订单入队
RPUSH queue:orders "order_1001:product_1:2"

# 2. Worker：可靠消费（BRPOPLPUSH）
# 从 queue:orders 弹出，推入 queue:orders:processing（处理中队列）
BRPOPLPUSH queue:orders queue:orders:processing 30

# 3. 处理成功后，从 processing 队列删除
LREM queue:orders:processing 1 "order_1001:product_1:2"

# 4. 处理失败，重试逻辑
# 检查重试次数
HGET order:1001:meta retry_count
# 如果重试次数 < 3，推入重试队列
RPUSH queue:orders:retry "order_1001:product_1:2"
HINCRBY order:1001:meta retry_count 1
# 否则推入死信队列
RPUSH queue:orders:deadletter "order_1001:product_1:2"

# 5. 定时任务：重试队列重新入主队列
# 每分钟执行
LLEN queue:orders:retry
RPOPLPUSH queue:orders:retry queue:orders
```

🔍 **深度反思**：
- 使用 BRPOPLPUSH 而不是 BLPOP + RPUSH，避免消费者崩溃导致任务丢失
- processing 队列用于追踪正在处理的任务
- 死信队列用于人工介入处理异常订单

💬 **追问预判**：
- Q: 为什么不用 Redis Stream？  
  A: Stream 更强大（消费者组、ACK 机制），但 List 更简单轻量，适合简单场景
- Q: 如何保证幂等性？  
  A: 订单号作为唯一标识，数据库层使用 INSERT ... ON DUPLICATE KEY UPDATE

---

### 🎯 今日高频面试题速览

1. **问题**：List 底层实现是什么？  
   **答案**：quicklist（Redis 3.2+），由多个 ziplist 组成的双向链表，平衡内存和性能

2. **问题**：LPOP 和 BLPOP 的区别？  
   **答案**：LPOP 立即返回（空则 nil），BLPOP 阻塞等待直到有元素或超时

3. **问题**：如何实现最近 N 条记录？  
   **答案**：LPUSH + LTRIM，每次添加后裁剪到固定长度

4. **问题**：List 适合做优先级队列吗？  
   **答案**：不适合，List 是有序的但不支持按优先级排序，应使用 ZSet

5. **问题**：BRPOPLPUSH 的作用？  
   **答案**：阻塞弹出元素并推入另一个列表，用于实现可靠队列，避免消费者崩溃导致任务丢失

---

## 第4天：Set 数据结构与应用

**本日掌握**：Set 数据结构操作、集合运算、随机抽取  
**覆盖原理点**：无  
**阶段**：使用期

### 🟢 基础用法题

#### 题目1：用户标签系统

**问题描述**：实现用户兴趣标签系统，支持：
1. 为用户添加多个标签
2. 查询用户的所有标签
3. 检查用户是否有某个标签
4. 删除用户的某个标签

**✅ 标准答案**：

```redis
# 1. 添加标签
SADD user:1001:tags "sports" "music" "travel" "coding"

# 2. 查询所有标签
SMEMBERS user:1001:tags
# 输出: sports, music, travel, coding（无序）

# 3. 检查是否有标签
SISMEMBER user:1001:tags "sports"
# 输出: 1 (有)

SISMEMBER user:1001:tags "cooking"
# 输出: 0 (没有)

# 4. 删除标签
SREM user:1001:tags "travel"

# 5. 获取标签数量
SCARD user:1001:tags
# 输出: 3
```

🔍 **深度反思**：为什么用 Set 而不是 List？
- Set 自动去重，List 允许重复
- Set 支持集合运算（交集、并集、差集）
- Set 的 SISMEMBER 是 O(1)，List 的查找是 O(N)

💬 **追问预判**：
- Q: Set 底层实现是什么？  
  A: intset（整数集合）或 hashtable，元素都是整数且数量少时用 intset，否则用 hashtable
- Q: Set 存储有序吗？  
  A: 无序，如果需要有序应使用 ZSet

---

### 🟡 中级用法题

#### 题目2：共同好友与好友推荐

**问题描述**：实现社交网络的好友系统，支持：
1. 查询两个用户的共同好友
2. 推荐好友（好友的好友，且不是自己的好友）
3. 计算用户之间的相似度

**✅ 标准答案**：

```redis
# 1. 添加好友关系
SADD user:1001:friends 1002 1003 1004 1005
SADD user:1002:friends 1001 1003 1006 1007
SADD user:1003:friends 1001 1002 1008

# 2. 查询共同好友（交集）
SINTER user:1001:friends user:1002:friends
# 输出: 1003

# 3. 推荐好友（好友的好友 - 自己的好友 - 自己）
# 获取好友的好友（并集）
SUNION user:1002:friends user:1003:friends user:1004:friends user:1005:friends

# 排除自己的好友（差集）
SDIFFSTORE user:1001:recommend user:1002:friends user:1001:friends

# 排除自己
SREM user:1001:recommend 1001

# 4. 查看推荐结果
SMEMBERS user:1001:recommend
```

🔍 **深度反思**：集合运算的性能？
- SINTER/SUNION/SDIFF 时间复杂度 O(N*M)，N 和 M 是集合大小
- 大集合运算可能阻塞 Redis，建议限制集合大小或使用异步计算
- SDIFFSTORE/SINTERSTORE 可将结果存储到新 key，避免阻塞客户端

💬 **追问预判**：
- Q: 如果用户好友数达到上万，运算会慢吗？  
  A: 会，万级集合运算可能耗时数百毫秒，建议异步计算或限制推荐范围
- Q: 如何实现"可能认识的人"排序？  
  A: 使用 ZSet 存储推荐用户，权重为共同好友数量

---

### 🔴 高级用法题

#### 题目3：抽奖系统（去重随机）

**问题描述**：实现一个抽奖系统，要求：
1. 从参与用户中随机抽取 N 个中奖者
2. 每个用户只能中奖一次
3. 支持多轮抽奖
4. 保证公平性

**✅ 标准答案**：

```redis
# 1. 添加参与用户
SADD lottery:round1:participants 1001 1002 1003 1004 1005 1006 1007 1008 1009 1010

# 2. 随机抽取 3 个中奖者（去重，不删除）
SRANDMEMBER lottery:round1:participants 3
# 输出: 1003 1007 1005

# 3. 随机弹出一个中奖者（从集合中删除）
SPOP lottery:round1:participants
# 输出: 1005

# 4. 弹出多个中奖者
SPOP lottery:round1:participants 3
# 输出: 1002 1008 1001

# 5. 多轮抽奖（复制参与者集合）
SUNIONSTORE lottery:round2:participants lottery:round1:participants
```

**应用层完整抽奖流程**：
```python
import redis

r = redis.Redis()

def lottery(round_id, winner_count):
    participants_key = f'lottery:{round_id}:participants'
    winners_key = f'lottery:{round_id}:winners'
    
    # 检查参与人数
    count = r.scard(participants_key)
    if count < winner_count:
        return {'error': '参与人数不足'}
    
    # 随机弹出中奖者
    winners = r.spop(participants_key, winner_count)
    
    # 保存中奖者
    if winners:
        r.sadd(winners_key, *[w.decode() for w in winners])
    
    return {'winners': winners}
```

🔍 **深度反思**：SRANDMEMBER vs SPOP？
- SRANDMEMBER：随机返回但不删除，适合“随机展示”
- SPOP：随机返回并删除，适合“抽奖/发牌”
- 两者都保证均匀分布，但 SPOP 性能略优（不需要检查重复）

💬 **追问预判**：
- Q: 如何保证抽奖的公平性？  
  A: Redis 的随机算法是均匀的，但应记录抽奖日志供审计
- Q: 如果抽奖过程中有用户退出怎么办？  
  A: 抽奖开始前冻结参与者集合，或记录退出日志在应用层过滤

---

### 🏢 大厂面试场景实战

**问题描述**：设计一个内容平台的点赞系统，支持：
1. 用户对内容点赞/取消点赞
2. 查询内容的点赞数
3. 查询用户是否点赞过某内容
4. 查询用户点赞过的所有内容
5. 高并发场景下的性能优化

**✅ 标准答案**：

**数据结构设计**：
```redis
# 内容点赞集合（查询谁点了赞、点赞数）
SADD content:1001:likes 1002 1003 1004

# 用户点赞记录（查询用户赞过什么、防止重复点赞）
SADD user:1002:likes 1001 1005 1008

# 点赞数缓存（快速查询）
INCR content:1001:like_count
```

**核心操作（Lua 脚本保证一致性）**：
```redis
EVAL "local content_key=KEYS[1]; local user_key=KEYS[2]; local count_key=KEYS[3]; local user_id=ARGV[1]; local content_id=ARGV[2]; if redis.call('SISMEMBER',user_key,content_id)==0 then redis.call('SADD',content_key,user_id); redis.call('SADD',user_key,content_id); redis.call('INCR',count_key); return 1 end return 0" 3 content:1001:likes user:1002:likes content:1001:like_count 1002 1001
```

**Python 实现**：
```python
lua_like = """
local content_key = KEYS[1]
local user_key = KEYS[2]
local count_key = KEYS[3]
local user_id = ARGV[1]
local content_id = ARGV[2]

if redis.call('SISMEMBER', user_key, content_id) == 0 then
    redis.call('SADD', content_key, user_id)
    redis.call('SADD', user_key, content_id)
    redis.call('INCR', count_key)
    return 1
end
return 0
"""

def like_content(user_id, content_id):
    result = r.eval(lua_like, 3, 
                   f'content:{content_id}:likes',
                   f'user:{user_id}:likes',
                   f'content:{content_id}:like_count',
                   user_id, content_id)
    return result == 1

# 取消点赞
def unlike_content(user_id, content_id):
    pipe = r.pipeline()
    pipe.srem(f'content:{content_id}:likes', user_id)
    pipe.srem(f'user:{user_id}:likes', content_id)
    pipe.decr(f'content:{content_id}:like_count')
    pipe.execute()

# 查询点赞数
def get_like_count(content_id):
    count = r.get(f'content:{content_id}:like_count')
    return int(count) if count else 0

# 查询用户是否点赞
def is_liked(user_id, content_id):
    return r.sismember(f'user:{user_id}:likes', content_id) == 1
```

🔍 **深度反思**：
- 双 Set 结构（内容视角 + 用户视角），支持双向查询
- 点赞数单独缓存，避免 SCARD 计算（O(1) vs O(N)）
- Lua 脚本保证点赞操作的原子性，防止重复点赞
- 大集合使用 SSCAN 避免阻塞

💬 **追问预判**：
- Q: 点赞数缓存和内容 Set 不一致怎么办？  
  A: 点赞数由 Lua 脚本维护，保证一致性；或定时校准 SCARD 修正缓存
- Q: 热点内容（百万点赞）的 Set 会很大怎么办？  
  A: 分页查询或使用 ZSet（带时间戳）支持按时间排序

---

### 🎯 今日高频面试题速览

1. **问题**：Set 和 List 的区别？  
   **答案**：Set 无序且去重，支持集合运算；List 有序可重复，适合队列/栈

2. **问题**：SISMEMBER 的时间复杂度？  
   **答案**：O(1)，底层是 hashtable 查找

3. **问题**：如何实现共同好友？  
   **答案**：使用 SINTER 求交集

4. **问题**：SRANDMEMBER 和 SPOP 的区别？  
   **答案**：SRANDMEMBER 随机返回不删除，SPOP 随机返回并删除

5. **问题**：Set 底层实现？  
   **答案**：intset（全是整数且数量少）或 hashtable，超过阈值自动转换

---

## 第5天：ZSet 数据结构与应用

**本日掌握**：ZSet 数据结构操作、排序、范围查询、排行榜实现  
**覆盖原理点**：无  
**阶段**：使用期

### 🟢 基础用法题

#### 题目1：商品销量排行榜

**问题描述**：实现商品销量排行榜，支持：
1. 添加商品销量
2. 查询 Top 10 商品
3. 查询某个商品的排名
4. 更新商品销量

**✅ 标准答案**：

```redis
# 1. 添加商品销量（score 是销量，member 是商品 ID）
ZADD rank:sales 1000 product:101
ZADD rank:sales 2500 product:102
ZADD rank:sales 1800 product:103
ZADD rank:sales 3200 product:104
ZADD rank:sales 900 product:105

# 2. 查询 Top 10 商品（从高到低）
ZREVRANGE rank:sales 0 9 WITHSCORES
# 输出:
# 1) "product:104"
# 2) "3200"
# 3) "product:102"
# 4) "2500"
# 5) "product:103"
# 6) "1800"
# 7) "product:101"
# 8) "1000"
# 9) "product:105"
# 10) "900"

# 3. 查询商品排名（从 0 开始）
ZREVRANK rank:sales product:102
# 输出: 1 (第2名)

# 4. 查询商品销量
ZSCORE rank:sales product:102
# 输出: "2500"

# 5. 增加销量
ZINCRBY rank:sales 500 product:102
# product:102 销量变为 3000

# 6. 查询排名在 1000-2000 之间的商品
ZRANGEBYSCORE rank:sales 1000 2000 WITHSCORES
```

🔍 **深度反思**：为什么用 ZSet 而不是 List？
- ZSet 天然支持排序和范围查询
- ZINCRBY 原子递增，适合实时更新
- List 需要每次重新排序（O(N log N)），ZSet 插入是 O(log N)

💬 **追问预判**：
- Q: ZSet 底层实现是什么？  
  A: ziplist（元素少且值小）或 skiplist + hashtable，大部分情况用 skiplist
- Q: 排名从 0 开始还是从 1 开始？  
  A: ZREVRANK 从 0 开始，展示给用户时需要 +1

---

### 🟡 中级用法题

#### 题目2：实时积分榜（带并列排名）

**问题描述**：实现游戏积分榜，支持：
1. 玩家分数实时更新
2. 查询 Top 100 玩家
3. 查询玩家排名（支持并列）
4. 查询玩家在某个分数段的排名

**✅ 标准答案**：

```redis
# 1. 初始化玩家分数
ZADD game:leaderboard 1500 player:1001
ZADD game:leaderboard 2300 player:1002
ZADD game:leaderboard 1500 player:1003  # 与 player:1001 同分
ZADD game:leaderboard 2800 player:1004

# 2. 查询 Top 10（含分数）
ZREVRANGE game:leaderboard 0 9 WITHSCORES

# 3. 查询玩家排名（ZREVRANK 不处理并列）
ZREVRANK game:leaderboard player:1001
# 输出: 2 或 3（取决于 member 字典序）
```

**应用层计算并列排名**：
```python
import redis

r = redis.Redis()

def get_rank_with_ties(leaderboard_key, player_id):
    score = r.zscore(leaderboard_key, player_id)
    if score is None:
        return None
    
    # 查询分数大于该玩家的人数
    higher_count = r.zcount(leaderboard_key, score + 0.0001, '+inf')
    rank = higher_count + 1
    return rank

# 查询分数段玩家
def get_players_in_score_range(leaderboard_key, min_score, max_score):
    return r.zrangebyscore(leaderboard_key, min_score, max_score, withscores=True)
```

🔍 **深度反思**：为什么 ZREVRANK 不处理并列？
- ZREVRANK 按 score 降序，score 相同时按 member 字典序
- 这是 ZSet 的设计决策，保持简单高效
- 并列排名需要在应用层通过 ZCOUNT 计算

💬 **追问预判**：
- Q: 为什么计算并列排名时用 score + 0.0001？  
  A: ZCOUNT 是闭区间 [min, max]，加一个极小值排除同分玩家
- Q: 如果分数是整数，能不能直接用 score + 1？  
  A: 可以，但 0.0001 更通用（支持小数分数）

---

### 🔴 高级用法题

#### 题目3：时间窗口排行榜

**问题描述**：实现一个“本周热门内容”排行榜，要求：
1. 内容热度 = 点赞数 × 1 + 评论数 × 2 + 分享数 × 3
2. 每周一零点重置排行榜
3. 支持实时查询 Top 50

**✅ 标准答案**：

**Lua 脚本计算热度**：
```redis
EVAL "local content_key=KEYS[1]; local action_type=ARGV[1]; local content_id=ARGV[2]; local weight=0; if action_type=='like' then weight=1 elseif action_type=='comment' then weight=2 elseif action_type=='share' then weight=3 end; redis.call('ZINCRBY',content_key,weight,content_id); return 1" 1 rank:weekly:hot comment content:1001
```

**Python 实现**：
```python
lua_hot = """
local content_key = KEYS[1]
local action_type = ARGV[1]
local content_id = ARGV[2]

local weight = 0
if action_type == 'like' then weight = 1
elseif action_type == 'comment' then weight = 2
elseif action_type == 'share' then weight = 3
end

redis.call('ZINCRBY', content_key, weight, content_id)
return 1
"""

def add_hot_action(action_type, content_id):
    r.eval(lua_hot, 1, 'rank:weekly:hot', action_type, content_id)

# 查询本周 Top 50
def get_weekly_top():
    return r.zrevrange('rank:weekly:hot', 0, 49, withscores=True)
```

**定时重置（应用层）**：
```python
from apscheduler.schedulers.blocking import BlockingScheduler
import redis

r = redis.Redis()

def reset_weekly_leaderboard():
    # 原子切换，DEL 异步清理
    r.rename('rank:weekly:hot', 'rank:weekly:hot:backup')
    r.delete('rank:weekly:hot:backup')  # 异步删除

scheduler = BlockingScheduler()
scheduler.add_job(reset_weekly_leaderboard, 'cron', day_of_week='mon', hour=0, minute=0)
scheduler.start()
```

🔍 **深度反思**：为什么不用定时任务直接 DEL？
- DEL 是阻塞操作，大 ZSet 可能耗时较长
- 使用 RENAME 原子切换，DEL 异步清理旧 key
- 保留备份用于数据分析

💬 **追问预判**：
- Q: ZINCRBY 的 score 可以是负数吗？  
  A: 可以，负数会减少 score
- Q: 如果热度计算很复杂，会影响性能吗？  
  A: 复杂计算应在应用层完成，Redis 只执行 ZINCRBY

---

### 🏢 大厂面试场景实战

**问题描述**：设计一个实时热搜榜系统，支持：
1. 搜索关键词实时排名
2. 支持多时间维度（实时、今日、本周、本月）
3. 防止刷榜（同一用户短时间内重复搜索不累加）
4. 高并发查询

**✅ 标准答案**：

**数据结构设计**：
```redis
# 实时热搜（5分钟窗口）
ZADD search:trending:realtime 15 keyword_1001

# 今日热搜
ZADD search:trending:today 1500 keyword_1001

# 本周热搜
ZADD search:trending:week 8000 keyword_1001

# 防刷限制（Set，5分钟过期）
SADD search:limit:user:1002 keyword_1001
EXPIRE search:limit:user:1002 300
```

**防刷 + 计数（Lua 脚本）**：
```python
lua_search = """
local user_limit_key = KEYS[1]
local trending_key = KEYS[2]
local keyword = ARGV[1]

if redis.call('SISMEMBER', user_limit_key, keyword) == 0 then
    redis.call('SADD', user_limit_key, keyword)
    redis.call('EXPIRE', user_limit_key, 300)
    redis.call('ZINCRBY', trending_key, 1, keyword)
    return 1
end
return 0
"""

def search_keyword(user_id, keyword):
    result = r.eval(lua_search, 2,
                   f'search:limit:user:{user_id}',
                   'search:trending:realtime',
                   keyword)
    return result == 1
```

**定时聚合**：
```python
def aggregate_trending():
    # 获取实时热搜所有数据
    realtime_data = r.zrange('search:trending:realtime', 0, -1, withscores=True)
    
    pipe = r.pipeline()
    for keyword, score in realtime_data:
        pipe.zincrby('search:trending:today', score, keyword)
        pipe.zincrby('search:trending:week', score, keyword)
    
    pipe.execute()
```

🔍 **深度反思**：
- 多时间维度使用多个 ZSet，空间换时间
- 防刷机制使用 Set + EXPIRE，5 分钟内同一用户同一关键词只计数一次
- 定时聚合使用 Pipeline 批量更新，减少网络往返

💬 **追问预判**：
- Q: 如果关键词数量达到百万，ZSet 会慢吗？  
  A: 百万级 ZSet 操作仍在毫秒级，但 ZRANGE 0 -1 会慢，应限制返回数量
- Q: 如何防止 ZSet 内存过大？  
  A: 定时清理低热度关键词（ZREMRANGEBYSCORE），或使用淘汰策略

---

### 🎯 今日高频面试题速览

1. **问题**：ZSet 底层实现？  
   **答案**：ziplist（元素少且值小）或 skiplist + hashtable，skiplist 支持 O(log N) 查找和范围查询

2. **问题**：ZINCRBY 是原子操作吗？  
   **答案**：是，Redis 单线程执行命令，ZINCRBY 不会被其他命令打断

3. **问题**：如何实现排行榜并列排名？  
   **答案**：ZREVRANK 不处理并列，需用 ZCOUNT 计算分数更高的数量 + 1

4. **问题**：ZSet 和 Set 的区别？  
   **答案**：ZSet 带 score 支持排序，Set 无序；ZSet 适合排行榜，Set 适合标签/去重

5. **问题**：ZREVRANGE 和 ZRANGEBYSCORE 的区别？  
   **答案**：ZREVRANGE 按排名范围查询，ZRANGEBYSCORE 按 score 范围查询
