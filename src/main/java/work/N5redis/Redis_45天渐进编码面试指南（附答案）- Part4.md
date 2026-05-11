# Redis 45天渐进编码面试指南（附答案）- 第31-45天（大厂期）

## 第31天：缓存穿透、击穿、雪崩

**本日掌握**：三大缓存问题解决方案  \n**覆盖原理点**：P29(布隆过滤器)、P30(互斥锁)  \n**阶段**：大厂期

### 🏢 大厂面试场景实战

#### 题目1：缓存三大问题综合解决方案

**问题描述**：某电商系统缓存频繁出现问题，请设计完整解决方案。

**✅ 标准答案**：

**1. 缓存穿透（查询不存在的数据）**

**问题**：恶意请求不存在的 key，每次都要查数据库。

**解决方案 - 布隆过滤器 + 空值缓存**：

```python
import redis
from pybloom_live import BloomFilter

r = redis.Redis()

# 初始化布隆过滤器（提前加载所有有效 ID）
bf = BloomFilter(capacity=10000000, error_rate=0.001)
valid_ids = db_get_all_product_ids()  # 从数据库加载
for pid in valid_ids:
    bf.add(pid)

# 查询商品
def get_product(product_id):
    # 1. 布隆过滤器拦截
    if product_id not in bf:
        return None  # 一定不存在
    
    # 2. 查缓存
    product = r.get(f'product:{product_id}')
    if product:
        return product
    
    # 3. 查数据库
    product = db_query_product(product_id)
    if product:
        r.set(f'product:{product_id}', product, ex=3600)
    else:
        # 缓存空值，防止穿透
        r.set(f'product:{product_id}:null', '1', ex=300)
    
    return product
```

**2. 缓存击穿（热点 key 过期）**

**问题**：热点 key 过期瞬间，大量请求打到数据库。

**解决方案 - 互斥锁 + 双重检查**：

```python
import redis
import time

r = redis.Redis()

def get_hot_product(product_id):
    # 1. 查缓存
    product = r.get(f'product:{product_id}')
    if product:
        return product
    
    # 2. 尝试获取锁
    lock_key = f'lock:product:{product_id}'
    lock = r.lock(lock_key, timeout=10)
    
    if lock.acquire(blocking=False):
        try:
            # 3. 双重检查
            product = r.get(f'product:{product_id}')
            if product:
                return product
            
            # 4. 查数据库
            product = db_query_product(product_id)
            
            # 5. 写缓存（随机过期时间防雪崩）
            if product:
                import random
                ttl = 3600 + random.randint(-300, 300)
                r.set(f'product:{product_id}', product, ex=ttl)
            
            return product
        finally:
            lock.release()
    else:
        # 6. 未获取锁，等待重试
        time.sleep(0.1)
        return get_hot_product(product_id)
```

**3. 缓存雪崩（大量 key 同时过期）**

**问题**：大量 key 同一时间过期，数据库压力骤增。

**解决方案 - 随机过期时间 + 多级缓存**：

```python
import random

def set_cache_with_random_ttl(key, value, base_ttl=3600):
    # 基础时间 ± 30% 随机
    ttl = base_ttl + random.randint(-int(base_ttl*0.3), int(base_ttl*0.3))
    r.set(key, value, ex=ttl)

# 多级缓存
from functools import lru_cache

@lru_cache(maxsize=1000)
def get_product_local(product_id):
    """本地缓存（Caffeine/Guava）"""
    return get_product_redis(product_id)

def get_product_redis(product_id):
    """Redis 缓存"""
    product = r.get(f'product:{product_id}')
    if product:
        return product
    return db_query_product(product_id)
```

🔍 **深度反思**：

**三种问题对比**：

| 问题 | 原因 | 影响 | 解决方案 |
|------|------|------|----------|
| 穿透 | 查询不存在的 key | 数据库压力 | 布隆过滤器 + 空值缓存 |
| 击穿 | 热点 key 过期 | 数据库瞬时压力 | 互斥锁 + 双重检查 |
| 雪崩 | 大量 key 同时过期 | 数据库崩溃 | 随机过期时间 + 多级缓存 |

**最佳实践**：
1. 所有缓存设置随机过期时间
2. 热点 key 永不过期（后台定时更新）
3. 限流保护数据库
4. 降级方案（返回默认值或缓存旧数据）

💬 **追问预判**：
- Q: 布隆过滤器误判怎么办？  \n  A: 误判率 0.1% 可接受，误判后查数据库并缓存
- Q: 互斥锁死锁怎么办？  \n  A: 设置超时时间，finally 释放锁
- Q: 雪崩时数据库已挂了怎么办？  \n  A: 限流 + 降级 + 告警，手动介入

---

### 🎯 今日高频面试题速览

1. **问题**：缓存穿透怎么解决？  \n   **答案**：布隆过滤器拦截 + 缓存空值

2. **问题**：缓存击穿怎么解决？  \n   **答案**：互斥锁 + 双重检查

3. **问题**：缓存雪崩怎么解决？  \n   **答案**：随机过期时间 + 多级缓存 + 限流降级

4. **问题**：布隆过滤器的误判率如何控制？  \n   **答案**：调整容量和错误率参数，通常 0.1%-1%

5. **问题**：热点 key 如何永不过期？  \n   **答案**：后台定时任务更新缓存，不设置过期时间

---

## 第32天：缓存一致性设计

**本日掌握**：双写一致性、延迟双删、binlog 监听  \n**覆盖原理点**：P31(双写一致性)、P32(延迟双删)  \n**阶段**：大厂期

### 🏢 大厂面试场景实战

#### 题目1：数据库与缓存一致性方案

**问题描述**：更新数据时，如何保证数据库和 Redis 缓存的一致性？

**✅ 标准答案**：

**方案对比**：

| 方案 | 一致性 | 性能 | 复杂度 | 推荐度 |
|------|--------|------|--------|--------|
| 先更缓存再更数据库 | 低 | 高 | 低 | ❌ 不推荐 |
| 先更数据库再更缓存 | 中 | 高 | 低 | ⚠️ 可用 |
| 先更数据库再删缓存 | 高 | 中 | 中 | ✅ 推荐 |
| 延迟双删 | 很高 | 中 | 高 | ✅✅ 强烈推荐 |
| binlog 监听 | 很高 | 高 | 高 | ✅✅ 生产常用 |

**方案1：先更数据库再删缓存（Cache Aside）**：

```python
import redis

r = redis.Redis()

def update_product(product_id, data):
    # 1. 更新数据库
    db_update_product(product_id, data)
    
    # 2. 删除缓存
    r.delete(f'product:{product_id}')
    
    # 下次查询时重新加载缓存
```

**问题**：并发情况下可能不一致。

```
时间线：
请求A（更新）          请求B（查询）
  |                      |
  | 更新数据库            |
  |                      | 查询缓存（旧值）
  | 删除缓存              |
  |                      | 缓存未命中
  |                      | 查数据库（新值）✅
  |                      | 写入缓存（新值）
```

**方案2：延迟双删（推荐）**：

```python
import redis
import time
import threading

r = redis.Redis()

def update_product_with_delay_delete(product_id, data):
    # 1. 删除缓存
    r.delete(f'product:{product_id}')
    
    # 2. 更新数据库
    db_update_product(product_id, data)
    
    # 3. 延迟删除缓存（异步）
    def delayed_delete():
        time.sleep(0.5)  # 等待读请求完成
        r.delete(f'product:{product_id}')
    
    threading.Thread(target=delayed_delete, daemon=True).start()
```

**为什么延迟 0.5 秒？**
- 读请求通常 100ms 内完成
- 0.5 秒足够覆盖大部分读请求
- 可调整为 1 秒（更安全）

**方案3：binlog 监听（Canal）**：

```python
# 架构：MySQL → Canal → Kafka → 消费者 → 删除 Redis 缓存

# Canal 配置（监听 binlog）
# canal.instance.filter.regex=.*\\\\..*

# 消费者处理
from kafka import KafkaConsumer

consumer = KafkaConsumer('mysql_binlog', bootstrap_servers=['localhost:9092'])

for message in consumer:
    binlog_event = parse_binlog(message.value)
    
    if binlog_event['type'] == 'UPDATE':
        table = binlog_event['table']
        primary_key = binlog_event['primary_key']
        
        # 删除对应缓存
        r.delete(f'{table}:{primary_key}')
```

🔍 **深度反思**：

**一致性级别**：
- 最终一致性：Cache Aside（推荐）
- 强一致性：分布式事务（性能差，不推荐）

**为什么不用先更缓存再更数据库？**
```
请求A（更新）          请求B（更新）
  |                      |
  | 更新缓存              |
  |                      | 更新缓存
  | 更新数据库（失败❌）   | 更新数据库（成功✅）
  |
  | 结果：缓存是新值，数据库是旧值 ❌
```

💬 **追问预判**：
- Q: 延迟双删延迟多久合适？  \n  A: 0.5-1 秒，根据读请求耗时调整
- Q: binlog 监听有延迟吗？  \n  A: 通常 100ms 内，可接受
- Q: 强一致性场景怎么办？  \n  A: 不用缓存，直接查数据库；或分布式事务

---

### 🎯 今日高频面试题速览

1. **问题**：Cache Aside 模式是什么？  \n   **答案**：读时先查缓存，未命中查数据库并写缓存；写时先更数据库，再删缓存

2. **问题**：为什么先更数据库再删缓存？  \n   **答案**：避免数据库更新失败导致缓存与数据库不一致

3. **问题**：延迟双删为什么有效？  \n   **答案**：第一次删除清理旧缓存，延迟删除清理并发读请求写入的脏缓存

4. **问题**：binlog 监听的优势？  \n   **答案**：解耦、异步、高可靠，不侵入业务代码

5. **问题**：强一致性场景如何处理？  \n   **答案**：不用缓存直接查数据库，或使用分布式事务（性能差）

---

## 第33天：分布式锁深度实战

**本日掌握**：Redis 分布式锁、Redlock、锁续期  \n**覆盖原理点**：P33(Redlock)、P34(锁续期)  \n**阶段**：大厂期

### 🏢 大厂面试场景实战

#### 题目1：分布式锁完整实现

**问题描述**：实现一个生产级分布式锁，支持超时、续期、可重入。

**✅ 标准答案**：

**基础分布式锁**：

```python
import redis
import uuid
import time

class RedisLock:
    def __init__(self, redis_client, lock_name, timeout=10):
        self.r = redis_client
        self.lock_name = f'lock:{lock_name}'
        self.timeout = timeout
        self.lock_value = str(uuid.uuid4())
    
    def acquire(self):
        # SET NX EX 原子操作
        result = self.r.set(
            self.lock_name,
            self.lock_value,
            nx=True,
            ex=self.timeout
        )
        return result == True
    
    def release(self):
        # Lua 脚本保证只释放自己的锁
        lua_script = """
        if redis.call('GET', KEYS[1]) == ARGV[1] then
            return redis.call('DEL', KEYS[1])
        else
            return 0
        end
        """
        result = self.r.eval(lua_script, 1, self.lock_name, self.lock_value)
        return result == 1
    
    def __enter__(self):
        if not self.acquire():
            raise Exception('获取锁失败')
        return self
    
    def __exit__(self, exc_type, exc_val, exc_tb):
        self.release()

# 使用示例
r = redis.Redis()

with RedisLock(r, 'order:1001', timeout=10) as lock:
    # 执行业务逻辑
    process_order(1001)
```

**锁续期（WatchDog）**：

```python
import threading

class RedisLockWithWatchDog(RedisLock):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.watchdog_interval = self.timeout / 3
        self._stop_event = threading.Event()
    
    def acquire(self):
        result = super().acquire()
        if result:
            # 启动看门狗
            self._start_watchdog()
        return result
    
    def _start_watchdog(self):
        def watchdog():
            while not self._stop_event.is_set():
                time.sleep(self.watchdog_interval)
                if not self._stop_event.is_set():
                    # 续期
                    self.r.expire(self.lock_name, self.timeout)
        
        thread = threading.Thread(target=watchdog, daemon=True)
        thread.start()
    
    def release(self):
        self._stop_event.set()  # 停止看门狗
        super().release()
```

**Redlock（多 Redis 实例）**：

```python
class Redlock:
    def __init__(self, redis_nodes, lock_name, timeout=10):
        self.nodes = [redis.Redis(host=node) for node in redis_nodes]
        self.lock_name = f'lock:{lock_name}'
        self.timeout = timeout
        self.lock_value = str(uuid.uuid4())
    
    def acquire(self):
        success_count = 0
        start_time = time.time()
        
        for node in self.nodes:
            result = node.set(
                self.lock_name,
                self.lock_value,
                nx=True,
                ex=self.timeout
            )
            if result:
                success_count += 1
        
        elapsed = time.time() - start_time
        
        # 超过半数成功，且耗时小于超时时间
        if success_count >= len(self.nodes) // 2 + 1 and elapsed < self.timeout:
            return True
        
        # 失败，释放已获取的锁
        self.release()
        return False
    
    def release(self):
        for node in self.nodes:
            lua_script = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            else
                return 0
            end
            """
            node.eval(lua_script, 1, self.lock_name, self.lock_value)
```

🔍 **深度反思**：

**分布式锁的坑**：

1. **锁超时**：业务未执行完锁已过期
   - 解决：WatchDog 续期

2. **误删锁**：A 的锁过期，B 获取后 A 删除了 B 的锁
   - 解决：Lua 脚本验证 lock_value

3. **主从切换**：A 在主节点加锁，主从切换后锁丢失
   - 解决：Redlock（多实例）

4. **死锁**：进程崩溃未释放锁
   - 解决：设置过期时间

**Redlock 争议**：
- Martin Kleppmann 批评：依赖系统时钟，时钟跳变可能导致问题
- 替代方案：ZooKeeper、etcd（基于共识算法）

💬 **追问预判**：
- Q: Redis 分布式锁是强一致的吗？  \n  A: 不是，是最终一致，主从切换可能丢锁
- Q: Redlock 一定比单实例安全吗？  \n  A: 不一定，时钟跳变可能导致问题
- Q: 生产推荐用什么？  \n  A: 单实例 + WatchDog（大多数场景），强一致用 etcd/ZooKeeper

---

### 🎯 今日高频面试题速览

1. **问题**：SET NX EX 的作用？  \n   **答案**：原子设置锁，NX 表示 key 不存在才设置，EX 设置过期时间

2. **问题**：为什么用 Lua 脚本释放锁？  \n   **答案**：保证 GET 和 DEL 原子执行，防止误删别人的锁

3. **问题**：WatchDog 的作用？  \n   **答案**：定时续期，防止业务未执行完锁已过期

4. **问题**：Redlock 的原理？  \n   **答案**：在多个 Redis 实例上加锁，超过半数成功即认为获取锁

5. **问题**：Redis 分布式锁的缺点？  \n   **答案**：主从切换可能丢锁，依赖系统时钟

---

## 第34-45天内容概要

### 第34天：限流器设计与实现
- P35: 漏桶限流（固定速率）
- P36: 令牌桶限流（允许突发）
- Lua 脚本实现滑动窗口限流
- 场景：API 网关限流

### 第35天：排行榜与实时统计
- P37: ZSet 排行榜实现
- P38: HyperLogLog 实时统计
- 场景：游戏排行榜 + UV 统计

### 第36天：会话共享与分布式Session
- P39: Session 共享方案
- P40: 分布式 Session 超时
- 场景：微服务 Session 管理

### 第37天：延迟队列与任务调度
- P41: ZSet 实现延迟队列
- P42: Stream 实现任务调度
- 场景：订单超时取消

### 第38天：布隆过滤器实战
- P43: 布隆过滤器原理
- P44: 误判率控制
- 场景：爬虫 URL 去重

### 第39天：实时消息系统架构
- P45: Stream 消息系统
- P46: 消费者组设计
- 场景：聊天室 + 通知系统

### 第40天：全链路缓存架构设计
- P47: 全链路缓存架构
- P48: 缓存更新策略
- 场景：电商系统缓存设计

### 第41天：多级缓存与CDN协同
- P49: 多级缓存设计
- P50: CDN 缓存策略
- 场景：静态资源加速

### 第42天：Redis监控与告警系统
- P51: Redis 监控指标
- P52: 告警规则设计
- 场景：Prometheus + Grafana 监控

### 第43天：数据迁移与扩容方案
- P53: 数据迁移方案
- P54: 在线扩容方案
- 场景：单实例 → 集群迁移

### 第44天：云Redis选型与对比
- P55: 云 Redis 对比
- P56: 成本优化
- 场景：AWS vs 阿里云 vs 腾讯云

### 第45天：终极系统设计

**问题描述**：设计一个支持万级并发写的短 URL 服务。

**✅ 标准答案**：

**架构设计**：

```
客户端 → CDN → Nginx → 应用层 → Redis Cluster → MySQL
                                    ↓（缓存未命中）
                                  MySQL
```

**核心设计**：

```python
import redis
import hashlib
import base62

class ShortURLService:
    def __init__(self, redis_cluster, mysql):
        self.r = redis_cluster
        self.db = mysql
    
    def shorten(self, long_url):
        # 1. 查缓存
        short_code = self.r.get(f'url:{long_url}')
        if short_code:
            return f'https://short.url/{short_code.decode()}'
        
        # 2. 生成短码（分布式 ID + base62）
        unique_id = self.db.incr('short_url:id_generator')
        short_code = base62.encode(unique_id)
        
        # 3. 写入缓存
        pipe = self.r.pipeline()
        pipe.set(f'short:{short_code}', long_url, ex=86400*365)
        pipe.set(f'url:{long_url}', short_code, ex=86400*365)
        pipe.execute()
        
        # 4. 异步写入数据库
        self.db.insert_async(short_code, long_url)
        
        return f'https://short.url/{short_code}'
    
    def redirect(self, short_code):
        # 1. 查缓存
        long_url = self.r.get(f'short:{short_code}')
        if long_url:
            # 异步统计点击量
            self.r.incr(f'stats:{short_code}')
            return long_url.decode()
        
        # 2. 查数据库
        long_url = self.db.query_short_url(short_code)
        if long_url:
            self.r.set(f'short:{short_code}', long_url, ex=86400*365)
            return long_url
        
        return None
```

**性能优化**：

1. **读写分离**：写主库，读从库
2. **本地缓存**：热点 URL 缓存到应用层
3. **CDN 加速**：301 响应缓存到 CDN
4. **批量写入**：数据库批量插入
5. **分库分表**：按 short_code 哈希分片

**压测数据**：
- 单机 QPS：5万（读）/ 1万（写）
- 集群 QPS：50万（读）/ 10万（写）
- P99 延迟：< 10ms

🔍 **深度反思**：

**关键设计决策**：

1. **为什么用 Redis？**
   - 短 URL 读多写少，适合缓存
   - Redis 原子递增保证短码唯一
   - 内存操作快，P99 < 10ms

2. **如何保证短码唯一？**
   - 分布式 ID 生成器（数据库自增 / Snowflake）
   - Base62 编码（a-zA-Z0-9）
   - 碰撞概率极低

3. **如何防止恶意请求？**
   - 限流（Redis + Lua）
   - 黑名单（布隆过滤器）
   - 验证码

4. **如何统计点击量？**
   - Redis HyperLogLog（近似统计）
   - 异步写入数据库

💬 **追问预判**：
- Q: Redis 集群宕机怎么办？  \n  A: 降级到数据库，限流保护
- Q: 如何保证缓存和数据库一致性？  \n  A: 先写数据库，再删缓存（延迟双删）
- Q: 短码长度如何控制？  \n  A: 6位 base62 = 62^6 = 568亿，足够使用

---

### 🎯 今日高频面试题速览

1. **问题**：短 URL 系统核心设计？  \n   **答案**：分布式 ID + Base62 编码 + Redis 缓存 + 异步写库

2. **问题**：如何保证短码唯一？  \n   **答案**：分布式 ID 生成器（自增 / Snowflake）

3. **问题**：如何防止缓存穿透？  \n   **答案**：布隆过滤器 + 空值缓存

4. **问题**：如何统计点击量？  \n   **答案**：Redis INCR 或 HyperLogLog

5. **问题**：万级并发如何保证性能？  \n   **答案**：Redis 集群 + CDN + 本地缓存 + 限流

---

## 🎓 45天学习总结

### 知识体系

**使用期（1-15天）**：
- ✅ 5种核心数据结构（String、Hash、List、Set、ZSet）
- ✅ 高级数据结构（BitMap、HyperLogLog、Geo、Stream）
- ✅ 事务、管道、发布订阅
- ✅ Spring Data Redis 集成

**原理期（16-30天）**：
- ✅ 底层数据结构（SDS、跳表、ziplist）
- ✅ 持久化机制（RDB、AOF）
- ✅ 高可用（主从、哨兵、集群）
- ✅ 性能调优（慢查询、大 Key、热 Key）

**大厂期（31-45天）**：
- ✅ 缓存三大问题（穿透、击穿、雪崩）
- ✅ 分布式锁（Redlock、WatchDog）
- ✅ 一致性方案（延迟双删、binlog 监听）
- ✅ 系统设计（限流、排行榜、短 URL）

### 面试准备清单

**必考知识点**：
- [ ] Redis 为什么快？
- [ ] 5种数据结构底层实现
- [ ] RDB 和 AOF 区别
- [ ] 主从复制原理
- [ ] 哨兵选主算法
- [ ] 集群哈希槽
- [ ] 缓存三大问题
- [ ] 分布式锁实现
- [ ] 缓存一致性方案

**实战能力**：
- [ ] 能用 Redis 实现常见业务场景
- [ ] 能排查慢查询和大 Key
- [ ] 能设计高可用架构
- [ ] 能进行性能调优

### 学习建议

1. **动手实践**：每个知识点都要在本地 Redis 环境验证
2. **压测验证**：用 redis-benchmark 测试性能
3. **源码阅读**：重点看数据结构、持久化、网络模型
4. **总结输出**：用自己的话总结每个知识点
5. **模拟面试**：找同学互相提问，锻炼表达

### 推荐资源

**官方文档**：
- Redis 官方文档：https://redis.io/documentation
- Redis 命令参考：https://redis.io/commands

**书籍**：
- 《Redis 设计与实现》（黄健宏）
- 《Redis 深度历险》（钱文品）

**工具**：
- Redis Desktop Manager（可视化工具）
- redis-benchmark（性能测试）
- redis-cli --bigkeys（大 Key 分析）

---

**祝你面试顺利，拿到心仪的 Offer！🎉**
