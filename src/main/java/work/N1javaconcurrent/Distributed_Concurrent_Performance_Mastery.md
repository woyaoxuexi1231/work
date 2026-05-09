# 分布式、并发与高性能进阶通关手册 (🔥 30天冲刺·大厂架构师必经之路)

这份手册专为 **Java 后端开发面试** 中的高阶领域打造，深度对标分布式系统设计、并发编程底层、以及系统性能调优。

你需要每天将这些问题的回答写下来，我会为你进行精准的“面试官级”点评与纠偏。

---

## 📅 第一周：并发编程底层与 JUC 高阶应用

### Day 01: 线程基础与 JMM 内存模型
*   **学习重点**：可见性、原子性、有序性、Happens-Before 原则、Volatile 内存屏障。
*   **面试真题连环炮**：
    1. 什么是 CPU 缓存行（Cache Line）？为什么会有“伪共享”问题？
    2. `volatile` 是如何保证可见性的？它能保证 `count++` 的安全性吗？
    3. 详细解释一下 `Happens-Before` 原则中的“传递性”和“监视器锁规则”。
*   **代码实战**：阅读 [Volatile.java](file:///d:/project/demo/demo-java/jdk/demo-java-basic/src/main/java/org/hulei/basic/concurrent/basic/Volatile.java) 与 [ThreadTest.java](file:///d:/project/demo/demo-java/jdk/demo-java-basic/src/main/java/org/hulei/basic/concurrent/thread/ThreadTest.java)。

### Day 02: 锁的深度解析：Synchronized 与 ReentrantLock
*   **学习重点**：锁升级过程、AQS 核心架构、Condition 原理。
*   **面试真题连环炮**：
    1. 为什么 `Synchronized` 升级到重量级锁需要切换到内核态？开销具体在哪？
    2. AQS 为什么使用双向链表（CLH 变体）？Head 节点代表什么？
    3. `ReentrantLock` 的 `lockInterruptibly()` 是如何实现响应中断的？
*   **代码实战**：阅读 [SynchronizedExample.java](file:///d:/project/demo/demo-java/jdk/demo-java-basic/src/main/java/org/hulei/basic/concurrent/basic/SynchronizedExample.java) 与 [ReentrantLockTest.java](file:///d:/project/demo/demo-java/jdk/demo-java-basic/src/main/java/org/hulei/basic/concurrent/lock/ReentrantLockTest.java)。

### Day 03: 并发工具类：CountDownLatch, CyclicBarrier 与 Semaphore
*   **学习重点**：JUC 三大工具类的应用场景与底层 AQS 实现差异。
*   **面试真题连环炮**：
    1. `CountDownLatch` 和 `CyclicBarrier` 有什么区别？为什么后者可以重用？
    2. 如果 `Semaphore` 的许可设为 1，它和 `ReentrantLock` 的区别是什么？
    3. 模拟一个场景：10 个线程并发执行，只有前 3 个能获取资源，其他线程等待 5 秒后自动放弃，如何实现？
*   **代码实战**：阅读 [CountDownLatchTest.java](file:///d:/project/demo/demo-java/jdk/demo-java-basic/src/main/java/org/hulei/basic/concurrent/tool/CountDownLatchTest.java) 与 [SemaphoreTest.java](file:///d:/project/demo/demo-java/jdk/demo-java-basic/src/main/java/org/hulei/basic/concurrent/tool/SemaphoreTest.java)。

### Day 04: 线程池深度调优与监控
*   **学习重点**：7 大参数配置、拒绝策略原理、Worker 工作逻辑、线程池监控指标。
*   **面试真题连环炮**：
    1. 线程池是如何实现“线程复用”的？核心线程执行完任务会立即销毁吗？
    2. `ScheduledThreadPoolExecutor` 是如何实现定时任务的？
    3. 如果线上线程池队列积压，除了增加线程数，你还有哪些排查和优化方案？
*   **代码实战**：阅读 [ExecutorServiceTest.java](file:///d:/project/demo/demo-java/jdk/demo-java-basic/src/main/java/org/hulei/basic/concurrent/thread/ExecutorServiceTest.java)。

### Day 05: 并发容器与原子类底层 (CAS/Unsafe)
*   **学习重点**：ConcurrentHashMap 1.8 扩容细节、LongAdder 性能优化原理、ABA 问题。
*   **面试真题连环炮**：
    1. `ConcurrentHashMap` 1.8 的 `size()` 方法是如何通过 `CounterCell` 避免竞争的？
    2. 为什么在高并发下 `LongAdder` 比 `AtomicLong` 性能更好？使用了什么思想？
    3. 谈谈对 `Unsafe` 类的理解，它在 AQS 和并发包中扮演了什么角色？
*   **代码实战**：阅读 [AtomicClassTest.java](file:///d:/project/demo/demo-java/jdk/demo-java-basic/src/main/java/org/hulei/basic/concurrent/atomic/AtomicClassTest.java)。

### Day 06: 异步编程与 CompletableFuture
*   **学习重点**：多任务编排、非阻塞 IO 在异步中的体现、自定义线程池隔离。
*   **面试真题连环炮**：
    1. `CompletableFuture` 的 `thenCombine` 和 `thenCompose` 有什么区别？
    2. 异步回调任务默认由哪个线程池执行？在高并发场景下会有什么风险？
*   **代码实战**：阅读 [CompletableFutureTest.java](file:///d:/project/demo/demo-java/jdk/demo-java-basic/src/main/java/org/hulei/basic/concurrent/future/CompletableFutureTest.java)。

### Day 07: 第一周综合模拟与线程问题排查
*   **任务**：模拟线上死锁和 CPU 100% 排查。
*   **代码实战**：阅读 [DeadLock.java](file:///d:/project/demo/demo-java/jdk/demo-java-basic/src/main/java/org/hulei/basic/concurrent/problem/DeadLock.java)。

---

## 📅 第二周：分布式系统基石：Redis 与 ZooKeeper

### Day 08: Redis 核心数据结构与底层原理
*   **学习重点**：SDS、跳表、压缩列表、持久化（RDB/AOF）优劣。
*   **面试真题连环炮**：
    1. 为什么 Redis 使用单线程还能这么快？
    2. 谈谈对 `zset` 的理解，为什么它要同时使用跳表和哈希表？
    3. AOF 重写机制（AOF Rewrite）是如何在不阻塞主线程的情况下完成的？

### Day 09: Redis 高可用与分布式锁
*   **学习重点**：主从复制、哨兵模式、Cluster 集群槽位、Redlock 算法。
*   **面试真题连环炮**：
    1. Redis Cluster 的槽位映射是如何实现的？如果增加一个节点，槽位如何迁移？
    2. 实现一个 Redis 分布式锁，如何解决“锁过期但业务未执行完”的问题？
    3. 什么是脑裂问题？哨兵模式是如何应对的？

### Day 10: Redis 缓存实战：雪崩、击穿、穿透与一致性
*   **学习重点**：缓存三剑客解决方案、双写一致性策略（延时双删等）。
*   **面试真题连环炮**：
    1. 数据库更新成功但 Redis 删除失败，如何保证最终一致性？
    2. 什么是热点 Key 探测？如何防止大流量瞬间击垮缓存？

### Day 11: ZooKeeper 核心原理与 ZAB 协议
*   **学习重点**：ZNode 类型、Watcher 机制、ZAB 协议选举与恢复。
*   **面试真题连环炮**：
    1. ZooKeeper 为什么不适合作为大规模的存储系统？它的写性能瓶颈在哪？
    2. 详细描述 ZAB 协议中崩溃恢复的过程。
*   **代码实战**：参考 [component_install_zookeeper_docker.sh](file:///d:/project/demo/demo-java/bash/component-base/component_install_zookeeper_docker.sh)。

### Day 12: ZooKeeper 分布式锁与配置管理
*   **学习重点**：临时顺序节点、监听机制实现公平锁、羊群效应及其规避。
*   **面试真题连环炮**：
    1. 基于 ZK 的分布式锁相比 Redis 分布式锁，优缺点分别是什么？
    2. 如何利用 ZK 实现一个简单的服务注册与发现中心？

### Day 13: 分布式一致性理论：CAP 与 BASE
*   **学习重点**：CAP 定理、强一致性 vs 最终一致性、Paxos 与 Raft 算法简述。
*   **面试真题连环炮**：
    1. 为什么 Eureka 选择 AP 而 ZooKeeper 选择 CP？这在服务发现中意味着什么？
    2. 在金融级系统中，你是如何权衡一致性和可用性的？

### Day 14: 第二周分布式协调模拟面试
*   **任务**：设计一个支持百万级并发的分布式秒杀系统。

---

## 📅 第三周：消息机制与分布式事务

### Day 15: 消息队列选型与核心架构（RabbitMQ/RocketMQ）
*   **学习重点**：RabbitMQ 模型、RocketMQ 存储设计、Kafka 顺序读写原理。
*   **面试真题连环炮**：
    1. 为什么 RocketMQ 的性能比 RabbitMQ 高？（提示：存储引擎与协议）。
    2. 什么是零拷贝（Zero-copy）？它是如何在 Kafka 中应用的？
*   **实战参考**：[RabbitMQ_Learning_Plan.md](file:///d:/project/demo/demo-java/example/rabbit/study/RabbitMQ_Learning_Plan.md)。

### Day 16: 消息全链路 0 丢失方案
*   **学习重点**：发送端确认、MQ 存储持久化、消费端 ACK、死信队列。
*   **面试真题连环炮**：
    1. 如果消息在消费时报错，MQ 会如何重试？如何避免死循环重试？
    2. 如何保证消息的消费幂等性？请给出具体的业务方案。

### Day 17: 分布式事务（一）：2PC, 3PC 与 TCC
*   **学习重点**：XA 协议、TCC 补偿机制及其三段式逻辑、空补偿/悬挂问题。
*   **面试真题连环炮**：
    1. 2PC 在协调者宕机后会有什么问题？3PC 引入了什么机制来改进？
    2. TCC 模式下，如果 `Confirm` 阶段失败了该怎么办？

### Day 18: 分布式事务（二）：可靠消息一致性与 Seata
*   **学习重点**：事务消息（RocketMQ）、最大努力通知、Seata AT 模式原理。
*   **面试真题连环炮**：
    1. Seata AT 模式是如何利用 `undo_log` 表实现自动回滚的？
    2. 比较事务消息和 TCC 模式，它们在业务复杂度和性能上的差异。

### Day 19: 分布式 ID 生成方案
*   **学习重点**：Snowflake 算法、号段模式、Leaf 实现。
*   **面试真题连环炮**：
    1. 雪花算法的时钟回拨问题如何解决？
    2. 为什么分布式 ID 必须具有趋势递增性？（提示：索引性能）。

### Day 20: 负载均衡与限流熔断
*   **学习重点**：Nginx 负载算法、令牌桶 vs 漏桶、Sentinel/Hystrix 原理。
*   **面试真题连环炮**：
    1. 如何在网关层实现灰度发布？
    2. Sentinel 的热点参数限流是如何实现的？

### Day 21: 第三周消息与事务综合复盘
*   **任务**：画出一个典型的分布式订单处理流程，标注事务和消息的边界。

---

## 📅 第四周：高性能设计与系统调优

### Day 22: 高性能 IO 模型：NIO, Reactor 与 Netty
*   **学习重点**：多路复用原理、主从 Reactor 模式、Netty 零拷贝。
*   **面试真题连环炮**：
    1. 为什么说 Netty 是高性能网关的首选？它在内存管理上做了哪些优化？
    2. 解释一下 `select`, `poll`, `epoll` 的区别，为什么 epoll 更高效？
*   **代码实战**：阅读 [MultiThreadEchoHandler.java](file:///d:/project/demo/demo-java/jdk/demo-java-basic/src/main/java/org/hulei/basic/io/reactor/MultiThreadEchoHandler.java)。

### Day 23: 数据库高性能：分库分表与读写分离
*   **学习重点**：ShardingSphere、水平切分 vs 垂直切分、分片键选择、分布式 Join。
*   **面试真题连环炮**：
    1. 分库分表后，原来的跨库分页查询该如何优化？
    2. 读写分离产生的数据同步延迟问题，如何解决？

### Day 24: 系统性能指标与全链路压测
*   **学习重点**：QPS, TPS, RT, P99 指标含义、压测工具、影子库。
*   **面试真题连环炮**：
    1. 你的系统 QPS 是多少？你是如何估算出这个数字的？
    2. 如何识别全链路中的性能瓶颈点？

### Day 25: JVM 内存调优实战
*   **学习重点**：GC 调优策略、内存泄漏定位（Mat/JVisualVM）。
*   **面试真题连环炮**：
    1. 如果线上频繁 Full GC，但内存回收不掉，你应该怎么排查？
    2. 调整过哪些 JVM 参数？为什么要这么调？

### Day 26: 架构演进与可伸缩性
*   **学习重点**：无状态化、微服务拆分、Sidecar 模式。
*   **面试真题连环炮**：
    1. 如果系统流量突增 10 倍，你的架构哪些部分会先挂掉？如何应对？

### Day 27: 面试综合突击：设计模式在分布式中的应用
### Day 28: 大厂真题模拟：高并发系统设计（从 0 到 1）
### Day 29: 错题本复盘与性能调优总结
### Day 30: 冲刺：架构师视野与面试心态

---

## 🏆 学习规则
1. **每日打卡**：每天在 `jdk/study/distributed_answers/dayxx.md` 记录回答。
2. **深度评审**：我会针对每一天的分布式和并发问题进行“压榨式”点评。
3. **结合代码**：分布式问题虽然偏理论，但一定要结合 [jdk/concurrent](file:///d:/project/demo/demo-java/jdk/demo-java-basic/src/main/java/org/hulei/basic/concurrent) 目录下的并发基础来回答，底层决定上层。

**进阶之路正式开启，请从 Day 01 开始提交你的“投名状”！**
