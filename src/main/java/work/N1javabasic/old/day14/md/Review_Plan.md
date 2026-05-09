# 🔥 Java 面试巅峰复习 - 今日冲刺计划（编码驱动版）

> **目标**：一天内通过 3 道编程题 + 核心原理回顾，唤醒前 14 天记忆
> **时间分配**：编码实战 4 小时 + 原理回顾 2 小时 + 场景模拟 1 小时 = 7 小时

---

## ⏰ 今日学习时间表

| 时间段 | 任务 | 时长 |
|--------|------|------|
| 09:00-11:00 | 编程题 1：LRU Cache + 限流器 | 2 小时 |
| 11:00-12:00 | 原理回顾：集合 + 并发核心知识点 | 1 小时 |
| 13:30-15:30 | 编程题 2：简易 AOP 框架 + 监控线程池 | 2 小时 |
| 15:30-16:30 | 原理回顾：反射 + 线程池 + ThreadLocal | 1 小时 |
| 17:00-18:00 | 编程题 3：OOM 复现 + 诊断 | 1 小时 |
| 18:00-19:00 | 场景模拟面试（口头回答） | 1 小时 |

---

## 🛠️ 编程题 1：LRU Cache + 高性能限流器（上午重点）

### 题目 A：手写 LRU Cache（带过期时间）

**需求**：
1. 实现一个支持过期时间的 LRU Cache，要求：
   - `put(key, value, ttl)` 支持设置存活时间（毫秒）
   - `get(key)` 返回有效值，过期则删除并返回 null
   - 容量满时淘汰最久未使用且未过期的元素
2. **不能使用 `LinkedHashMap`**，要求用 `HashMap + 双向链表` 自己实现
3. 支持并发访问（写时加锁，读时不加锁）

**考察点**：

- 双向链表操作（插入、删除、移动到头/尾）
- HashMap 索引加速查找
- 并发安全策略（ReadWriteLock 或分段锁）
- LRU 淘汰算法实现

**实现提示**：
```java
class LRUCache<K, V> {
    private final int capacity;
    private final Map<K, Node<K, V>> map;
    private final Node<K, V> head; // 虚拟头节点
    private final Node<K, V> tail; // 虚拟尾节点
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    // Node 需要包含：key, value, expireTime, prev, next
}
```

---

### 📚 原理回顾题

1. **HashMap 扰动函数**
   - 为什么 `hash = (h = key.hashCode()) ^ (h >>> 16)` 能减少碰撞？
   - 如果不用扰动函数，在数组长度为 16 时，hash 值为 `0000 0000 0000 1111` 和 `0000 0000 1111 1111` 的两个 key 会映射到同一个桶吗？

2. **ConcurrentHashMap 扩容**
   - 1.8 中多线程如何协同扩容？
   - 如果一个线程正在迁移桶 A，另一个线程要 put 到桶 A，会发生什么？

3. **CopyOnWriteArrayList 缺陷**
   - 为什么它的迭代器是"弱一致性"的？
   - 如果在遍历过程中另一个线程添加了元素，迭代器会报错吗？

---

### 💡 场景实战题

**面试官连环炮**：

1. 你的 LRU Cache 如果用 `ReentrantReadWriteLock`，读写锁的粒度应该如何设计？（全表锁 vs 分段锁）
2. 如果要求支持百万级 QPS 读取，你的实现会有什么性能瓶颈？如何优化？
3. 如果要在分布式环境下实现 LRU Cache，你会选择什么方案？

---

### 题目 B：实现令牌桶限流器（上午重点）

**需求**：

1. 实现 `RateLimiter` 类，支持：
   - `tryAcquire()` 尝试获取令牌，成功返回 true，失败返回 false
   - 令牌桶容量固定，每秒生成固定数量的令牌
   - 支持并发调用，线程安全
2. **不能使用 `synchronized` 全方法锁**，要求用 `AtomicLong + CAS` 实现
3. 编写测试代码，模拟 100 个线程并发调用，统计 QPS 和拒绝率

**考察点**：
- CAS 操作与原子类使用
- 令牌桶算法实现
- 高并发测试与性能统计

**实现提示**：
```java
class RateLimiter {
    private final long maxTokens;
    private final long refillRate; // 每秒生成令牌数
    private final AtomicLong currentTokens;
    private volatile long lastRefillTime;
    
    public synchronized boolean tryAcquire() {
        // 1. 补充令牌
        // 2. CAS 扣减令牌
    }
}
```

---

### 📚 原理回顾题

1. **Volatile 内存屏障**
   - `volatile` 写操作会插入 `StoreStore` 和 `StoreLoad` 屏障，这两个屏障分别阻止了什么类型的重排序？

2. **AQS 唤醒机制**
   - `ReentrantLock` 的 `unlock()` 是如何唤醒等待队列中的下一个线程的？
   - 如果唤醒失败（线程被中断），会发生什么？

3. **线程池 Worker 原理**
   - 线程池的 `Worker` 类为什么继承 `AQS` 而不是直接用 `ReentrantLock`？

---

### 💡 场景实战题

**面试官连环炮**：

1. 你的限流器如果每秒生成 1000 个令牌，但突然有 5000 个请求同时到达，会发生什么？如何避免"突发流量击穿"？
2. 如果要在分布式环境下实现限流（多节点共享令牌桶），你会怎么设计？
3. 令牌桶算法和漏桶算法的区别是什么？各自适合什么场景？

