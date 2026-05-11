# Redis 45天渐进编码面试指南（附答案）- 第6-15天

## 第6天：键过期与淘汰策略

**本日掌握**：EXPIRE、TTL、键淘汰策略配置  \n**覆盖原理点**：无  \n**阶段**：使用期

### 🟢 基础用法题

#### 题目1：验证码缓存

**问题描述**：实现手机验证码缓存，要求：
1. 验证码 5 分钟过期
2. 查询验证码剩余时间
3. 续期验证码

**✅ 标准答案**：

```redis
# 1. 设置验证码并过期
SET sms:code:13800138000 "123456" EX 300

# 2. 查询验证码
GET sms:code:13800138000
# 输出: "123456"

# 3. 查询剩余时间（秒）
TTL sms:code:13800138000
# 输出: 245（剩余245秒）

# 4. 查询剩余时间（毫秒）
PTTL sms:code:13800138000
# 输出: 245000

# 5. 续期（重新设置过期时间）
EXPIRE sms:code:13800138000 600

# 6. 移除过期时间（永久有效）
PERSIST sms:code:13800138000

# 7. 查看 key 是否存在
EXISTS sms:code:13800138000
# 输出: 1 (存在) 或 0 (不存在)
```

🔍 **深度反思**：TTL 返回值的含义？
- 正数：剩余秒数
- -1：key 存在但没有过期时间
- -2：key 不存在

💬 **追问预判**：
- Q: 过期 key 会立即删除吗？  \n  A: 不会，采用惰性删除 + 定期删除策略
- Q: 如何精确控制过期时间？  \n  A: 使用 PEXPIRE（毫秒级）

---

### 🟡 中级用法题

#### 题目2：Session 管理

**问题描述**：实现用户 Session 管理，要求：
1. 用户登录后创建 Session，30 分钟无操作过期
2. 用户每次操作刷新过期时间
3. 查询 Session 是否有效

**✅ 标准答案**：

```redis
# 1. 用户登录，创建 Session
SET session:abc123 "user:1001" EX 1800

# 2. 用户操作，刷新 Session
EXPIRE session:abc123 1800

# 3. 查询 Session
GET session:abc123
# 输出: "user:1001"

# 4. 检查 Session 是否有效
TTL session:abc123
# 输出: 1500（有效）或 -2（已过期）

# 5. 用户登出
DEL session:abc123
```

**Python 封装**：
```python
import redis

r = redis.Redis()

def create_session(session_id, user_id, ttl=1800):
    r.set(f'session:{session_id}', f'user:{user_id}', ex=ttl)

def refresh_session(session_id, ttl=1800):
    r.expire(f'session:{session_id}', ttl)

def get_session_user(session_id):
    return r.get(f'session:{session_id}')

def is_session_valid(session_id):
    return r.ttl(f'session:{session_id}') > 0
```

🔍 **深度反思**：为什么 Session 适合用 Redis？
- 过期时间天然支持 Session 超时
- 内存操作速度快，适合高频查询
- 支持分布式部署（多应用共享 Session）

💬 **追问预判**：
- Q: Session 数据量大怎么办？  \n  A: 使用 Hash 结构存储 Session 详细信息，或使用 Redis 集群

---

### 🔴 高级用法题

#### 题目3：内存淘汰策略配置

**问题描述**：Redis 内存达到上限时，如何配置淘汰策略？对比不同策略的适用场景。

**✅ 标准答案**：

```redis
# 查看当前淘汰策略
CONFIG GET maxmemory-policy
# 输出: maxmemory-policy, noeviction

# 查看所有淘汰策略
# noeviction: 不淘汰，返回错误（默认）
# allkeys-lru: 所有 key 中使用最少的使用者
# volatile-lru: 有过期时间的 key 中 LRU
# allkeys-lfu: 所有 key 中使用频率最低
# volatile-lfu: 有过期时间的 key 中 LFU
# allkeys-random: 所有 key 随机
# volatile-random: 有过期时间的 key 随机
# volatile-ttl: 即将过期的 key

# 设置淘汰策略
CONFIG SET maxmemory-policy allkeys-lru

# 设置最大内存
CONFIG SET maxmemory 2gb

# 查看内存使用情况
INFO memory
```

**策略选择指南**：
```
缓存场景：
- 全部作为缓存 → allkeys-lru（推荐）
- 部分缓存部分持久 → volatile-lru

计数器场景：
- 热点数据保留 → allkeys-lfu

临时数据：
- 有过期时间的 key → volatile-ttl
```

🔍 **深度反思**：LRU 和 LFU 的区别？
- LRU（Least Recently Used）：最近最少使用，关注时间
- LFU（Least Frequently Used）：最不经常使用，关注频率
- LRU 适合“突然热点”场景，LFU 适合“持续热点”场景

💬 **追问预判**：
- Q: Redis 的 LRU 是精确的吗？  \n  A: 不是，是近似 LRU，随机采样 5 个 key（默认），淘汰最旧的
- Q: 如何调整采样数量？  \n  A: CONFIG SET maxmemory-samples 10（默认 5，越大越精确但越慢）

---

### 🏢 大厂面试场景实战

**问题描述**：某系统 Redis 内存经常爆满，导致写入失败。请设计一套内存管理和监控方案。

**✅ 标准答案**：

**解决方案**：
```redis
# 1. 设置合理的内存上限和淘汰策略
CONFIG SET maxmemory 4gb
CONFIG SET maxmemory-policy allkeys-lru
CONFIG SET maxmemory-samples 10

# 2. 监控内存使用
INFO memory
# 关注指标：
# used_memory: 已使用内存
# used_memory_peak: 峰值内存
# maxmemory: 最大内存
# mem_fragmentation_ratio: 内存碎片率

# 3. 查找大 key
redis-cli --bigkeys

# 4. 查找过期 key 比例
INFO stats
# 关注：expired_keys（过期 key 数量）
```

**Python 监控脚本**：
```python
import redis
import logging

r = redis.Redis()

def check_memory():
    info = r.info('memory')
    used = info['used_memory']
    max_mem = info['maxmemory']
    fragmentation = info['mem_fragmentation_ratio']
    
    if max_mem > 0:
        usage_percent = (used / max_mem) * 100
        
        if usage_percent > 90:
            logging.critical(f'🚫 内存使用率过高: {usage_percent:.1f}%')
        elif usage_percent > 80:
            logging.warning(f'⚠️ 内存使用率警告: {usage_percent:.1f}%')
        
        if fragmentation > 1.5:
            logging.warning(f'⚠️ 内存碎片率过高: {fragmentation}')
    
    return used, max_mem, fragmentation
```

🔍 **深度反思**：
- 内存碎片率 > 1.5 说明碎片严重，可重启 Redis 或使用 MEMORY PURGE
- 定期分析 bigkeys，优化数据结构
- 监控内存使用趋势，提前扩容

💬 **追问预判**：
- Q: 内存碎片率过高怎么办？  \n  A: Redis 4.0+ 可执行 MEMORY PURGE，或重启实例
- Q: 如何避免内存爆满？  \n  A: 设置合理的 maxmemory + 淘汰策略 + 监控告警

---

### 🎯 今日高频面试题速览

1. **问题**：TTL 返回 -1 和 -2 的区别？  \n   **答案**：-1 表示 key 存在但无过期时间，-2 表示 key 不存在

2. **问题**：Redis 过期 key 会立即删除吗？  \n   **答案**：不会，采用惰性删除（访问时检查）+ 定期删除（后台任务）

3. **问题**：allkeys-lru 和 volatile-lru 的区别？  \n   **答案**：allkeys-lru 从所有 key 中淘汰，volatile-lru 只从有过期时间的 key 中淘汰

4. **问题**：Redis 的 LRU 是精确的吗？  \n   **答案**：不是，是近似 LRU，随机采样 5 个 key 淘汰最旧的

5. **问题**：如何查找 Redis 中的大 key？  \n   **答案**：使用 redis-cli --bigkeys 命令

---

## 第7天：发布订阅模式

**本日掌握**：PUBLISH、SUBSCRIBE、PSUBSCRIBE  \n**覆盖原理点**：无  \n**阶段**：使用期

### 🟢 基础用法题

#### 题目1：实时通知系统

**问题描述**：实现一个简单的实时通知系统，支持：
1. 发布通知
2. 订阅通知频道
3. 取消订阅

**✅ 标准答案**：

```redis
# 1. 订阅频道（阻塞等待）
SUBSCRIBE notifications
# 输出:
# Reading messages... (press Ctrl-C to quit)
# 1) "subscribe"
# 2) "notifications"
# 3) (integer) 1

# 2. 发布通知（另一个客户端）
PUBLISH notifications "订单1001已发货"
# 输出: (integer) 1（1个订阅者收到）

# 3. 订阅者收到消息
# 1) "message"
# 2) "notifications"
# 3) "订单1001已发货"

# 4. 订阅多个频道
SUBSCRIBE notifications orders payments

# 5. 取消订阅
UNSUBSCRIBE notifications

# 6. 查看当前订阅的频道
PUBSUB CHANNELS
```

🔍 **深度反思**：发布订阅的特点？
- 发布者和订阅者解耦
- 消息不持久化，订阅者离线会丢失
- 适合实时通知，不适合可靠消息队列

💬 **追问预判**：
- Q: 发布消息时没有订阅者会怎样？  \n  A: 消息丢失，PUBLISH 返回 0
- Q: 如何保证消息不丢失？  \n  A: 使用 Redis Stream 或消息队列（RabbitMQ、Kafka）

---

### 🟡 中级用法题

#### 题目2：模式订阅

**问题描述**：实现按模式订阅，支持：
1. 订阅所有以 "order:" 开头的频道
2. 订阅所有以 "user:*:notifications" 格式的频道

**✅ 标准答案**：

```redis
# 1. 模式订阅
PSUBSCRIBE order:*
# 订阅所有 order: 开头的频道

# 2. 发布到不同频道
PUBLISH order:1001 "订单创建"
PUBLISH order:1002 "订单支付"
PUBLISH user:1001:notifications "您有新消息"

# 3. 订阅者收到所有匹配的消息
# 1) "pmessage"
# 2) "order:*"
# 3) "order:1001"
# 4) "订单创建"

# 4. 多个模式订阅
PSUBSCRIBE order:* user:*:notifications

# 5. 取消模式订阅
PUNSUBSCRIBE order:*

# 6. 查看活跃订阅
PUBSUB NUMSUB order:1001 order:1002
```

🔍 **深度反思**：SUBSCRIBE 和 PSUBSCRIBE 的区别？
- SUBSCRIBE 订阅具体频道
- PSUBSCRIBE 使用 glob 模式匹配多个频道
- 模式订阅更灵活，但性能略低

💬 **追问预判**：
- Q: 模式订阅支持正则表达式吗？  \n  A: 不支持，只支持 glob 模式（*、?、[]）
- Q: 发布订阅的性能如何？  \n  A: O(N)，N 是订阅者数量，订阅者多时会影响性能

---

### 🔴 高级用法题

#### 题目3：发布订阅的局限性

**问题描述**：发布订阅有哪些局限性？如何弥补？

**✅ 标准答案**：

**局限性**：
1. 消息不持久化，订阅者离线丢失
2. 无消息确认机制
3. 无消费者组概念
4. 发布阻塞直到所有订阅者处理完

**替代方案 - Redis Stream**：
```redis
# 1. 添加消息（持久化）
XADD mystream * sensor_id 1001 temperature 36.5

# 2. 读取消息
XRANGE mystream - +

# 3. 消费者组
XGROUP CREATE mystream group1 $ MKSTREAM

# 4. 消费消息（带 ACK）
XREADGROUP GROUP group1 consumer1 BLOCK 5000 COUNT 10 STREAMS mystream >

# 5. 确认消息
XACK mystream group1 1234567890123-0
```

🔍 **深度反思**：何时使用发布订阅 vs Stream？
- 发布订阅：实时通知、简单事件广播
- Stream：可靠消息队列、需要 ACK、消费者组

💬 **追问预判**：
- Q: 发布订阅和消息队列的区别？  \n  A: 发布订阅不持久化、无 ACK；消息队列持久化、有 ACK、支持重试
- Q: 发布订阅适合什么场景？  \n  A: 实时聊天、配置变更通知、日志收集

---

### 🏢 大厂面试场景实战

**问题描述**：设计一个实时配置更新系统，多个服务实例需要实时获取配置变更。

**✅ 标准答案**：

**架构设计**：
```
配置中心 → PUBLISH config:update:{service_name} → 服务实例1/2/3 (PSUBSCRIBE config:*)
```

**Python 实现**：

**配置中心（发布者）**：
```python
import redis
import json

r = redis.Redis()

def update_config(service_name, config_key, config_value):
    # 更新数据库配置
    db_update_config(service_name, config_key, config_value)
    
    # 发布配置变更通知
    message = json.dumps({
        'service': service_name,
        'key': config_key,
        'value': config_value
    })
    r.publish(f'config:update:{service_name}', message)
```

**服务实例（订阅者）**：
```python
import redis
import json
import threading

r = redis.Redis()

def subscribe_config_updates():
    pubsub = r.pubsub()
    pubsub.psubscribe('config:update:*')
    
    for message in pubsub.listen():
        if message['type'] == 'pmessage':
            channel = message['channel'].decode()
            data = json.loads(message['data'])
            
            # 更新本地配置
            update_local_config(data['key'], data['value'])
            print(f'✅ 配置已更新: {data["key"]} = {data["value"]}')

# 后台线程运行订阅
thread = threading.Thread(target=subscribe_config_updates, daemon=True)
thread.start()
```

🔍 **深度反思**：
- 发布订阅实现配置实时更新，避免轮询
- 服务实例启动时从数据库加载最新配置
- 配置变更通知是“尽最大努力交付”，不保证 100% 可靠

💬 **追问预判**：
- Q: 如果订阅者重启错过配置更新怎么办？  \n  A: 启动时从数据库加载最新配置，订阅只用于实时更新
- Q: 如何保证配置更新的顺序？  \n  A: Redis 单线程保证同一频道的消息顺序

---

### 🎯 今日高频面试题速览

1. **问题**：发布订阅的消息会持久化吗？  \n   **答案**：不会，订阅者离线会丢失消息

2. **问题**：SUBSCRIBE 和 PSUBSCRIBE 的区别？  \n   **答案**：SUBSCRIBE 订阅具体频道，PSUBSCRIBE 使用 glob 模式匹配多个频道

3. **问题**：发布订阅适合什么场景？  \n   **答案**：实时通知、配置变更、简单事件广播

4. **问题**：发布者和订阅者哪个会阻塞？  \n   **答案**：订阅者 SUBSCRIBE 后阻塞等待，发布者 PUBLISH 非阻塞

5. **问题**：如何查看当前有多少订阅者？  \n   **答案**：PUBSUB NUMSUB channel1 channel2

---

## 第8天：事务与管道

**本日掌握**：MULTI、EXEC、WATCH、Pipeline  \n**覆盖原理点**：无  \n**阶段**：使用期

### 🟢 基础用法题

#### 题目1：事务基础

**问题描述**：使用 Redis 事务实现批量操作，要求：
1. 开启事务
2. 执行多条命令
3. 提交或取消事务

**✅ 标准答案**：

```redis
# 1. 开启事务
MULTI
# 输出: OK

# 2. 添加命令到事务队列
SET user:1001:name "张三"
# 输出: QUEUED

SET user:1001:age 25
# 输出: QUEUED

INCR user:1001:visit_count
# 输出: QUEUED

# 3. 执行事务
EXEC
# 输出:
# 1) OK
# 2) OK
# 3) (integer) 1

# 4. 取消事务
MULTI
SET user:1001:name "李四"
DISCARD
# 输出: OK（事务取消，name 未更新）
```

🔍 **深度反思**：Redis 事务的特点？
- 原子性：EXEC 后所有命令顺序执行，不会被插入
- 不支持回滚：命令错误不会回滚已执行的命令
- 无隔离性：事务执行期间其他命令可以执行

💬 **追问预判**：
- Q: 事务中命令语法错误会怎样？  \n  A: 入队时就报错，EXEC 时整个事务不执行
- Q: 事务中命令运行时错误会怎样？  \n  A: 错误命令执行失败，其他命令继续执行，不回滚

---

### 🟡 中级用法题

#### 题目2：WATCH 乐观锁

**问题描述**：实现库存扣减，使用 WATCH 保证并发安全。

**✅ 标准答案**：

```redis
# 1. 初始化库存
SET product:1001:stock 100

# 2. 客户端 A 扣减库存
WATCH product:1001:stock
# 输出: OK

# 3. 读取当前库存
stock = GET product:1001:stock
# 输出: "100"

# 4. 开启事务
MULTI
DECR product:1001:stock
EXEC
# 输出: (integer) 99（成功）

# 5. 并发场景：客户端 B 也在 WATCH
# 如果客户端 B 在 A 的 EXEC 之前修改了 stock
# A 的 EXEC 会返回 nil（事务失败）
```

**Python 实现**：
```python
import redis

r = redis.Redis()

def decrease_stock(product_id):
    while True:
        try:
            # 监视库存
            pipe = r.pipeline(True)  # True 启用事务
            pipe.watch(f'product:{product_id}:stock')
            
            # 读取库存
            stock = int(pipe.get(f'product:{product_id}:stock'))
            
            if stock <= 0:
                pipe.unwatch()
                return False
            
            # 开启事务
            pipe.multi()
            pipe.decr(f'product:{product_id}:stock')
            
            # 执行事务
            pipe.execute()
            return True
            
        except redis.WatchError:
            # 被其他客户端修改，重试
            continue
```

🔍 **深度反思**：WATCH 的工作原理？
- WATCH 监视 key，如果 EXEC 前 key 被修改，事务失败
- 乐观锁策略：假设冲突少，失败重试
- 适合读多写少场景

💬 **追问预判**：
- Q: WATCH 能监视多个 key 吗？  \n  A: 可以，WATCH key1 key2 key3
- Q: 高并发下 WATCH 性能如何？  \n  A: 冲突多时会频繁重试，性能下降，建议用 Lua 脚本

---

### 🔴 高级用法题

#### 题目3：Pipeline 性能优化

**问题描述**：对比普通操作、Pipeline、事务的性能差异。

**✅ 标准答案**：

**Python 性能测试**：
```python
import redis
import time

r = redis.Redis()

# 1. 普通操作（1000次网络往返）
start = time.time()
for i in range(1000):
    r.set(f'key:{i}', i)
end = time.time()
print(f'普通操作: {end-start:.3f}秒')  # 约1秒

# 2. Pipeline（1次网络往返）
start = time.time()
pipe = r.pipeline()
for i in range(1000):
    pipe.set(f'key:{i}', i)
pipe.execute()
end = time.time()
print(f'Pipeline: {end-start:.3f}秒')  # 约0.01秒

# 3. 事务（1次网络往返，原子执行）
start = time.time()
pipe = r.pipeline(True)
for i in range(1000):
    pipe.set(f'key:{i}', i)
pipe.execute()
end = time.time()
print(f'事务: {end-start:.3f}秒')  # 约0.01秒
```

**性能对比**：
| 方式 | 网络往返 | 原子性 | 1000次耗时 |
|------|---------|--------|-----------|
| 普通 | 1000次 | 单命令原子 | ~1秒 |
| Pipeline | 1次 | 不保证 | ~0.01秒 |
| 事务 | 1次 | 保证 | ~0.01秒 |

🔍 **深度反思**：何时使用 Pipeline vs 事务？
- Pipeline：批量操作，不要求原子性，追求性能
- 事务：批量操作，要求原子性，性能与 Pipeline 相当

💬 **追问预判**：
- Q: Pipeline 会阻塞 Redis 吗？  \n  A: 单次 Pipeline 过大（如 10万条）会阻塞，建议分批 1000-5000 条
- Q: 事务和 Lua 脚本哪个更好？  \n  A: Lua 脚本更灵活（支持逻辑判断），事务只能顺序执行命令

---

### 🏢 大厂面试场景实战

**问题描述**：实现用户转账功能，保证原子性和并发安全。

**✅ 标准答案**：

**方案1：Lua 脚本（推荐）**：
```python
lua_transfer = """
local from_key = KEYS[1]
local to_key = KEYS[2]
local amount = tonumber(ARGV[1])

local from_balance = tonumber(redis.call('GET', from_key))
if from_balance and from_balance >= amount then
    redis.call('DECRBY', from_key, amount)
    redis.call('INCRBY', to_key, amount)
    return 1
end
return 0
"""

def transfer(from_user, to_user, amount):
    result = r.eval(lua_transfer, 2,
                   f'user:{from_user}:balance',
                   f'user:{to_user}:balance',
                   amount)
    return result == 1
```

**方案2：WATCH + 事务**：
```python
def transfer_with_watch(from_user, to_user, amount):
    while True:
        try:
            pipe = r.pipeline(True)
            from_key = f'user:{from_user}:balance'
            to_key = f'user:{to_user}:balance'
            
            pipe.watch(from_key, to_key)
            
            from_balance = int(pipe.get(from_key))
            
            if from_balance < amount:
                pipe.unwatch()
                return False
            
            pipe.multi()
            pipe.decrby(from_key, amount)
            pipe.incrby(to_key, amount)
            
            pipe.execute()
            return True
            
        except redis.WatchError:
            continue
```

🔍 **深度反思**：
- Lua 脚本更简洁，原子执行，推荐
- WATCH + 事务在高并发下可能频繁重试
- 转账必须保证原子性，不能出现钱消失或凭空产生

💬 **追问预判**：
- Q: 如果 Redis 宕机，转账会丢失吗？  \n  A: 取决于持久化配置，AOF everysec 最多丢1秒
- Q: 如何保证 Redis 和数据库双写一致性？  \n  A: 先写数据库，再删 Redis 缓存（延迟双删）

---

### 🎯 今日高频面试题速览

1. **问题**：Redis 事务支持回滚吗？  \n   **答案**：不支持，命令执行失败不会回滚已执行的命令

2. **问题**：WATCH 的作用是什么？  \n   **答案**：乐观锁，监视 key，如果 EXEC 前 key 被修改则事务失败

3. **问题**：Pipeline 和事务的区别？  \n   **答案**：Pipeline 不保证原子性但减少网络往返；事务保证原子性也减少网络往返

4. **问题**：事务中命令语法错误会怎样？  \n   **答案**：入队时就报错，EXEC 时整个事务不执行

5. **问题**：Pipeline 会阻塞 Redis 吗？  \n   **答案**：单次 Pipeline 过大（如 10万条）会阻塞，建议分批

---

## 第9天：位图BitMap操作

**本日掌握**：SETBIT、GETBIT、BITCOUNT、位运算  \n**覆盖原理点**：无  \n**阶段**：使用期

### 🟢 基础用法题

#### 题目1：用户签到

**问题描述**：实现用户每日签到，支持：
1. 用户签到
2. 检查用户今天是否签到
3. 统计用户本月签到天数

**✅ 标准答案**：

```redis
# 1. 用户签到（user_id=1001, 2024-04-01是第1天）
SETBIT user:1001:signin:202404 0 1
# 输出: 0（之前是0）

# 2. 检查今天是否签到（4月5日是第4天）
GETBIT user:1001:signin:202404 4
# 输出: 0（未签到）或 1（已签到）

# 3. 签到
SETBIT user:1001:signin:202404 4 1

# 4. 统计本月签到天数
BITCOUNT user:1001:signin:202404
# 输出: 2（签到了2天）

# 5. 统计连续签到天数（需要应用层计算）
```

🔍 **深度反思**：为什么用 BitMap 存储签到？
- 节省空间：1个用户1个月只需 31 bit，约 4 字节
- 高效统计：BITCOUNT O(N)，N 是字节数
- 支持位运算：连续签到、活跃用户分析

💬 **追问预判**：
- Q: BitMap 最大支持多少位？  \n  A: 2^32 位（512MB），单个 key 最大 512MB
- Q: 如果 user_id 很大怎么办？  \n  A: 使用 user_id 作为偏移量，或使用 Hash 分片

---

### 🟡 中级用法题

#### 题目2：用户活跃度统计

**问题描述**：统计平台的日活跃用户（DAU）、周活跃用户（WAU）、月活跃用户（MAU）。

**✅ 标准答案**：

```redis
# 1. 用户登录时记录（日期作为 key，user_id 作为偏移量）
SETBIT dau:20240401 1001 1
SETBIT dau:20240401 1002 1
SETBIT dau:20240401 1003 1

SETBIT dau:20240402 1001 1
SETBIT dau:20240402 1004 1

# 2. 统计 DAU（4月1日）
BITCOUNT dau:20240401
# 输出: 3

# 3. 统计 WAU（4月1日-7日）
BITOP OR wau:202404w1 dau:20240401 dau:20240402 dau:20240403 dau:20240404 dau:20240405 dau:20240406 dau:20240407
BITCOUNT wau:202404w1
# 输出: 4（1001,1002,1003,1004）

# 4. 统计 MAU（4月）
BITOP OR mau:202404 dau:20240401 dau:20240402 ... dau:20240430
BITCOUNT mau:202404
```

**Python 实现**：
```python
import redis
from datetime import datetime, timedelta

r = redis.Redis()

def record_login(user_id):
    today = datetime.now().strftime('%Y%m%d')
    r.setbit(f'dau:{today}', user_id, 1)

def get_dau(date_str):
    return r.bitcount(f'dau:{date_str}')

def get_wau(start_date):
    # 计算7天的日期列表
    dates = [(start_date + timedelta(days=i)).strftime('%Y%m%d') for i in range(7)]
    keys = [f'dau:{d}' for d in dates]
    
    if len(keys) > 1:
        r.bitop('OR', f'wau:{start_date}', *keys)
        return r.bitcount(f'wau:{start_date}')
    elif len(keys) == 1:
        return r.bitcount(keys[0])
    return 0
```

🔍 **深度反思**：BITOP 的性能？
- BITOP OR/AND/XOR/NOT 时间复杂度 O(N)，N 是最大 key 的字节数
- 百万用户日活跃位图约 125KB，BITOP 很快
- 但 BITOP 会创建新 key，注意清理

💬 **追问预判**：
- Q: 用户 ID 从 1000000 开始，BitMap 会浪费空间吗？  \n  A: 会，可偏移 user_id（user_id - 1000000）或使用分段
- Q: BITOP 会阻塞 Redis 吗？  \n  A: 大 key 的 BITOP 会阻塞，建议异步执行

---

### 🔴 高级用法题

#### 题目3：连续签到奖励

**问题描述**：实现连续签到奖励系统，连续签到 7 天送优惠券。

**✅ 标准答案**：

```redis
# 1. 用户签到
SETBIT user:1001:signin:202404 4 1  # 4月5日

# 2. 获取最近7天的签到记录（位偏移 0-6）
# 需要应用层计算偏移量

# 3. Python 计算连续签到天数
def get_consecutive_signin(user_id, year_month, today_offset):
    signin_key = f'user:{user_id}:signin:{year_month}'
    
    # 获取整个月的签到位图
    signin_data = r.get(signin_key)
    if not signin_data:
        return 0
    
    # 从今天往前检查
    consecutive_days = 0
    for i in range(today_offset, -1, -1):
        if r.getbit(signin_key, i) == 1:
            consecutive_days += 1
        else:
            break
    
    return consecutive_days

# 4. 检查是否满足奖励条件
consecutive = get_consecutive_signin(1001, '202404', 4)
if consecutive >= 7:
    grant_reward(1001)
```

🔍 **深度反思**：如何高效检查连续签到？
- 直接逐位检查：简单但可能慢（7次 GETBIT）
- 使用 BITFIELD 批量获取：一次获取多位
- 应用层缓存连续天数，签到时更新

💬 **追问预判**：
- Q: BITFIELD 怎么用？  \n  A: BITFIELD key GET u7 0（获取从偏移0开始的7位无符号整数）
- Q: 跨月连续签到怎么处理？  \n  A: 检查上月底和本月初，或使用全局签到记录

---

### 🏢 大厂面试场景实战

**问题描述**：设计一个亿级用户的活跃分析系统，支持：
1. DAU/WAU/MAU 统计
2. 新老用户区分
3. 用户留存率分析

**✅ 标准答案**：

**数据结构设计**：
```redis
# 日活跃用户
SETBIT dau:20240401 {user_id} 1

# 新用户（首次登录）
SETBIT new_users:20240401 {user_id} 1

# 用户注册日期（用于判断新老用户）
SET user:1001:register_date 20240315
```

**核心操作**：
```redis
# 1. 统计 DAU
BITCOUNT dau:20240401

# 2. 统计 WAU
BITOP OR wau:202404w1 dau:20240401 dau:20240402 ... dau:20240407
BITCOUNT wau:202404w1

# 3. 统计新用户 DAU
BITCOUNT new_users:20240401

# 4. 统计老用户 DAU（DAU - 新用户）
BITOP AND old_users:20240401 dau:20240401 new_users:20240401
BITOP NOT old_users:20240401 old_users:20240401
BITCOUNT old_users:20240401

# 5. 次日留存率（4月1日活跃且4月2日也活跃）
BITOP AND retain:20240401_02 dau:20240401 dau:20240402
retain_count = BITCOUNT retain:20240401_02
dau_count = BITCOUNT dau:20240401
retention_rate = retain_count / dau_count
```

**Python 留存率计算**：
```python
def calculate_retention(base_date, days_after):
    from datetime import timedelta
    
    base_key = f'dau:{base_date}'
    after_date = (datetime.strptime(base_date, '%Y%m%d') + timedelta(days=days_after)).strftime('%Y%m%d')
    after_key = f'dau:{after_date}'
    
    # 计算交集
    r.bitop('AND', f'retain:{base_date}_{days_after}', base_key, after_key)
    retain_count = r.bitcount(f'retain:{base_date}_{days_after}')
    
    # 计算留存率
    dau_count = r.bitcount(base_key)
    return retain_count / dau_count if dau_count > 0 else 0
```

🔍 **深度反思**：
- BitMap 节省空间，亿级用户日活跃只需 12.5MB
- BITOP 支持集合运算，灵活计算留存、新老用户
- 定时任务每日凌晨计算并存储结果

💬 **追问预判**：
- Q: 亿级用户的 BitMap 有多大？  \n  A: 1亿位 = 12.5MB，非常紧凑
- Q: 如何优化 BITOP 性能？  \n  A: 异步执行、分片计算、使用 HyperLogLog 近似统计

---

### 🎯 今日高频面试题速览

1. **问题**：BitMap 适合什么场景？  \n   **答案**：签到、活跃统计、用户标签等二元状态存储

2. **问题**：BITCOUNT 的时间复杂度？  \n   **答案**：O(N)，N 是字节数，非常快

3. **问题**：BITOP 支持哪些运算？  \n   **答案**：AND、OR、XOR、NOT

4. **问题**：BitMap 最大支持多少位？  \n   **答案**：2^32 位（512MB）

5. **问题**：用户 ID 很大时 BitMap 会浪费空间吗？  \n   **答案**：会，可偏移 ID 或使用分段存储

---

## 第10天：HyperLogLog 与基数统计

**本日掌握**：PFADD、PFCOUNT、PFMERGE  \n**覆盖原理点**：无  \n**阶段**：使用期

### 🟢 基础用法题

#### 题目1：UV 统计

**问题描述**：统计网页的独立访客（UV），要求：
1. 记录访问用户
2. 统计 UV
3. 节省内存

**✅ 标准答案**：

```redis
# 1. 记录用户访问
PFADD page:1001:uv user:1001
# 输出: 1（新元素）

PFADD page:1001:uv user:1002
# 输出: 1

PFADD page:1001:uv user:1001
# 输出: 0（已存在）

# 2. 统计 UV
PFCOUNT page:1001:uv
# 输出: 2

# 3. 继续添加
PFADD page:1001:uv user:1003 user:1004 user:1005
PFCOUNT page:1001:uv
# 输出: 5
```

🔍 **深度反思**：HyperLogLog 的优势？
- 固定内存：无论多少元素，只占 12KB
- 近似统计：标准误差 0.81%
- 适合海量去重统计

💬 **追问预判**：
- Q: HyperLogLog 能获取具体元素吗？  \n  A: 不能，只统计基数，不存储元素
- Q: 误差 0.81% 是什么意思？  \n  A: 100万 UV，实际可能在 991900-1008100 之间

---

### 🟡 中级用法题

#### 题目2：合并统计

**问题描述**：统计多个页面的总 UV（去重）。

**✅ 标准答案**：

```redis
# 1. 各页面 UV
PFADD page:1001:uv user:1001 user:1002 user:1003
PFADD page:1002:uv user:1002 user:1003 user:1004
PFADD page:1003:uv user:1003 user:1004 user:1005

# 2. 单页面 UV
PFCOUNT page:1001:uv  # 3
PFCOUNT page:1002:uv  # 3

# 3. 多页面总 UV（去重）
PFMERGE site:total:uv page:1001:uv page:1002:uv page:1003:uv
PFCOUNT site:total:uv
# 输出: 5（1001,1002,1003,1004,1005）
```

🔍 **深度反思**：PFMERGE 的性能？
- PFMERGE 合并多个 HyperLogLog，O(N)
- 合并后仍可继续 PFADD
- 适合分片统计后汇总

💬 **追问预判**：
- Q: PFMERGE 会修改原 key 吗？  \n  A: 不会，只写入目标 key
- Q: 能合并不同日期的 UV 吗？  \n  A: 可以，PFMERGE week:uv day1:uv day2:uv ... day7:uv

---

### 🔴 高级用法题

#### 题目3：误差分析与优化

**问题描述**：HyperLogLog 的误差如何？什么场景不适合使用？

**✅ 标准答案**：

**误差测试**：
```python
import redis

r = redis.Redis()

# 添加 100万 个元素
pipe = r.pipeline()
for i in range(1000000):
    pipe.pfadd('test:hll', f'user:{i}')
pipe.execute()

count = r.pfcount('test:hll')
print(f'实际: 1000000, 统计: {count}, 误差: {abs(count-1000000)/1000000*100:.2f}%')
# 输出: 实际: 1000000, 统计: 1000812, 误差: 0.08%
```

**不适合场景**：
1. 需要精确计数（如财务）
2. 需要获取具体元素
3. 数据量小（< 1万）且内存充足

**适合场景**：
1. UV 统计（百万级以上）
2. 搜索词去重
3. 用户画像标签基数

🔍 **深度反思**：HyperLogLog 原理？
- 基于概率算法，记录最大前导零数量
- 使用调和平均减少误差
- 12KB 固定内存是设计权衡

💬 **追问预判**：
- Q: 如何减少误差？  \n  A: 无法减少，这是算法特性；可多次统计取平均
- Q: 内存为什么固定 12KB？  \n  A: 16384 个桶 × 6 bit = 12288 bytes = 12KB

---

### 🏢 大厂面试场景实战

**问题描述**：设计一个电商网站的全链路 UV 统计系统。

**✅ 标准答案**：

**架构设计**：
```redis
# 页面级 UV
PFADD page:home:uv {user_id}
PFADD page:product:{id}:uv {user_id}
PFADD page:cart:uv {user_id}

# 小时级 UV
PFADD uv:20240401:10 {user_id}

# 天级 UV（合并小时级）
PFMERGE uv:20240401 uv:20240401:0 uv:20240401:1 ... uv:20240401:23

# 全站 UV
PFMERGE uv:site:20240401 page:home:uv page:product:uv page:cart:uv
```

**定时聚合**：
```python
def aggregate_daily_uv(date_str):
    hours = [f'uv:{date_str}:{h:02d}' for h in range(24)]
    r.pfmerge(f'uv:{date_str}', *hours)
    
    # 清理小时级数据
    for h in hours:
        r.delete(h)
```

🔍 **深度反思**：
- 分层次统计：页面 → 小时 → 天 → 全站
- HyperLogLog 固定内存，适合长期存储
- 定时聚合减少存储，提高查询效率

💬 **追问预判**：
- Q: 如何统计转化率？  \n  A: 页面 UV / 上一页 UV，如 cart_uv / product_uv
- Q: 数据要保留多久？  \n  A: 原始数据保留 7 天，聚合数据保留 1 年

---

### 🎯 今日高频面试题速览

1. **问题**：HyperLogLog 能获取具体元素吗？  \n   **答案**：不能，只统计基数（去重数量），不存储元素

2. **问题**：HyperLogLog 的误差是多少？  \n   **答案**：标准误差 0.81%，100万元素误差约 ±8100

3. **问题**：HyperLogLog 占用多少内存？  \n   **答案**：固定 12KB，无论多少元素

4. **问题**：PFMERGE 的作用？  \n   **答案**：合并多个 HyperLogLog，计算并集基数

5. **问题**：什么场景适合用 HyperLogLog？  \n   **答案**：海量去重统计（UV、搜索词、标签基数），不要求精确

---

## 第11-15天内容概要

由于篇幅限制，以下是第11-15天的核心知识点和题目概要，完整内容可按相同格式展开：

### 第11天：Geo 地理空间操作
- 🟢 GEOADD、GEODIST 基础
- 🟡 附近的人 GEORADIUS
- 🔴 地理围栏 GEOHASH
- 🏢 外卖配送范围计算
- 🎯 GEO vs ZSet 底层实现

### 第12天：Stream 数据结构
- 🟢 XADD、XRANGE 基础
- 🟡 消费者组 XREADGROUP
- 🔴 消息确认 XACK
- 🏢 可靠消息队列设计
- 🎯 Stream vs Kafka 对比

### 第13天：Redis 命令高级组合
- 🟢 SORT 排序
- 🟡 SCAN 系列遍历
- 🔴 Lua 脚本编排
- 🏢 复杂业务编排
- 🎯 命令组合最佳实践

### 第14天：Spring Data Redis 使用
- 🟢 RedisTemplate 配置
- 🟡 序列化方案对比
- 🔴 @Cacheable 注解
- 🏢 微服务缓存架构
- 🎯 序列化陷阱与优化

### 第15天：综合实战
- 🏢 电商大促缓存架构
- 缓存 + 计数器 + 排行榜完整设计
- 性能压测与调优
- 监控告警方案
- 高可用部署架构

---

**说明**：第16-45天（原理期和大厂期）内容将在后续文件中继续生成，包含性能压测题、原理剖析题和大厂场景实战。
