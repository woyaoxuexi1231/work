# Java 进阶面试巅峰通关手册 (🔥 30天冲刺·后端架构师之路)

这份手册专为 **Java 后端开发面试** 打造，深度对标大厂（阿里、腾讯、字节等）面试要求。每天的内容包含：**核心底层原理、代码实战演练、以及“面试真题连环炮”**。

你需要每天将这些问题的回答写下来，我会为你进行精准的“面试官级”点评与纠偏。

---

## 📅 第一周：Java 基石与集合容器深度解析

### Day 01: 核心基础：Object、String 与泛型
*   **学习重点**：理解 `hashCode` 与 `equals` 的约束、`String` 的不可变性与常量池、泛型擦除及其影响。
*   **面试真题连环炮**：
    1. 为什么重写 `equals` 必须重写 `hashCode`？不重写会有什么后果？
    2. `String s = new String("abc")` 到底创建了几个对象？
    3. `String` 为什么要设计成不可变的？从安全和性能两方面回答。
    4. 谈谈泛型擦除。既然擦除了，为什么 Java 还能在运行时通过反射获取泛型信息？
*   **代码实战**：阅读 [IntegerExample.java](file:///d:/project/demo/demo-java/jdk/demo-java-basic/src/main/java/org/hulei/basic/oop/IntegerExample.java)，解释其缓存机制。

### Day 02: 反射与动态代理（框架的灵魂）
*   **学习重点**：反射的性能损耗原因、JDK 动态代理与 CGLIB 的实现差异。
*   **面试真题连环炮**：
    1. 反射为什么慢？有哪些优化手段？
    2. JDK 动态代理为什么要求必须有接口？CGLIB 是如何实现代理的？
    3. 动态代理在 Spring AOP 中是如何切换的？
*   **代码实战**：参考 [ReflectionExample.java](file:///d:/project/demo/demo-java/jdk/demo-java-basic/src/main/java/org/hulei/basic/oop/ReflectionExample.java) 与 [ProxyTest.java](file:///d:/project/demo/demo-java/jdk/demo-java-basic/src/main/java/org/hulei/basic/pattern/structural/ProxyTest.java)。

### Day 03: HashMap 源码地狱级解析
*   **学习重点**：1.7 与 1.8 的结构差异、扩容机制、红黑树转换阈值、扰动函数。
*   **面试真题连环炮**：
    1. 1.7 的死循环是怎么产生的？1.8 是如何解决的？
    2. 为什么负载因子默认是 0.75？为什么红黑树转换阈值是 8？
    3. 扰动函数（hash 方法）的作用是什么？为什么数组长度要是 2 的幂次方？
*   **代码实战**：阅读 [HashMapTest.java](file:///d:/project/demo/demo-java/jdk/demo-java-basic/src/main/java/org/hulei/basic/collection/HashMapTest.java)。

### Day 04: 并发容器：ConcurrentHashMap 与 CopyOnWrite
*   **学习重点**：1.7 分段锁 vs 1.8 CAS+Synchronized、CopyOnWrite 的写时复制思想。
*   **面试真题连环炮**：
    1. `ConcurrentHashMap` 1.8 为什么放弃分段锁？它的 `size()` 方法是如何实现的？
    2. `ConcurrentHashMap` 的扩容过程（多线程协同扩容）是怎样的？
    3. `CopyOnWriteArrayList` 的缺点是什么？适用于什么场景？
*   **代码实战**：阅读 [ConcurrentHashMapTest.java](file:///d:/project/demo/demo-java/jdk/demo-java-basic/src/main/java/org/hulei/basic/collection/ConcurrentHashMapTest.java)。

### Day 05: 常用集合：ArrayList、LinkedList 与 BlockingQueue
*   **学习重点**：扩容策略、Fail-fast 机制、各种阻塞队列的实现差异。
*   **面试真题连环炮**：
    1. `ArrayList` 扩容是 1.5 倍，为什么不是 2 倍？
    2. `ArrayBlockingQueue` 与 `LinkedBlockingQueue` 的锁实现有什么区别？
    3. `PriorityBlockingQueue` 是如何保证有序的？它的扩容逻辑有何特殊之处？
*   **代码实战**：阅读 [ArrayListTest.java](file:///d:/project/demo/demo-java/jdk/demo-java-basic/src/main/java/org/hulei/basic/collection/ArrayListTest.java) 与 [BlockQueueTest.java](file:///d:/project/demo/demo-java/jdk/demo-java-basic/src/main/java/org/hulei/basic/concurrent/queue/BlockQueueTest.java)。

### Day 06: Java 8+ 函数式编程新特性
*   **学习重点**：Stream 流式计算原理、Lambda 的实现方式（invokedynamic）。
*   **面试真题连环炮**：
    1. `Stream` 的并行流（parallelStream）底层是用什么实现的？有什么坑？
    2. `Optional` 真的能完全解决 NPE 吗？正确的使用姿势是什么？
    3. 接口的默认方法（default method）冲突了怎么办？
*   **代码实战**：阅读 [StreamTest.java](file:///d:/project/demo/demo-java/jdk/demo-java-basic/src/main/java/org/hulei/basic/java8/stream/StreamTest.java)。

### Day 07: 第一周综合复盘与模拟面试
*   **任务**：整理前 6 天的所有错题，尝试从源码角度回答。
*   **加餐题**：如果让你实现一个 LRU Cache，你会选择哪种集合？为什么？

---

## 📅 第二周：并发编程与 JUC 工具包（面试重灾区）

### Day 08: JMM 与并发三大特性
*   **学习重点**：可见性、原子性、有序性、Happens-Before 原则、Volatile 屏障原理。
*   **面试真题连环炮**：
    1. 什么是指令重排序？`volatile` 是如何禁止重排序的？
    2. `volatile` 能保证原子性吗？为什么 `count++` 不是原子操作？
    3. 谈谈 `Happens-Before` 的理解，为什么它比直接看源码更重要？
*   **代码实战**：阅读 [Volatile.java](file:///d:/project/demo/demo-java/jdk/demo-java-basic/src/main/java/org/hulei/basic/concurrent/basic/Volatile.java)。

### Day 09: Synchronized 深度探究
*   **学习重点**：对象头结构、锁升级过程（偏向锁、轻量级锁、重量级锁）。
*   **面试真题连环炮**：
    1. `synchronized` 加在静态方法和实例方法上有什么区别？
    2. 描述一下锁升级的过程。为什么 1.6 之后引入了这么多级别的锁？
    3. 为什么重量级锁需要切换到内核态？性能开销在哪里？
*   **代码实战**：阅读 [SynchronizedExample.java](file:///d:/project/demo/demo-java/jdk/demo-java-basic/src/main/java/org/hulei/basic/concurrent/basic/SynchronizedExample.java)。

### Day 10: AQS 原理及其实现（ReentrantLock 等）
*   **学习重点**：CLH 队列、State 状态位、独占锁与共享锁。
*   **面试真题连环炮**：
    1. 讲讲 AQS 的内部结构。它是如何唤醒后续节点的？
    2. `ReentrantLock` 的公平锁与非公平锁实现差异在哪里？
    3. 为什么非公平锁的吞吐量更高？
*   **代码实战**：阅读 [ReentrantLockTest.java](file:///d:/project/demo/demo-java/jdk/demo-java-basic/src/main/java/org/hulei/basic/concurrent/lock/ReentrantLockTest.java)。

### Day 11: 读写锁与乐观锁（StampedLock）
*   **学习重点**：写锁降级、读锁插队问题、`StampedLock` 的乐观读模式。
*   **面试真题连环炮**：
    1. `ReentrantReadWriteLock` 适合什么场景？存在什么问题（锁饥饿）？
    2. 锁降级的过程是怎样的？为什么不支持锁升级？
    3. `StampedLock` 相比 `ReentrantReadWriteLock` 做了哪些改进？
*   **代码实战**：手写一个基于 `ReentrantReadWriteLock` 的缓存容器。

### Day 12: 线程池核心参数与执行流程
*   **学习重点**：7 大参数配置、拒绝策略、Worker 工作原理。
*   **面试真题连环炮**：
    1. 线程池的执行流程是怎样的？先加队列还是先开线程？
    2. 如何根据业务类型（IO 密集型 vs CPU 密集型）配置核心线程数？
    3. 线程池中的线程如果抛出了未捕获异常，会发生什么？
*   **代码实战**：阅读 [ExecutorServiceTest.java](file:///d:/project/demo/demo-java/jdk/demo-java-basic/src/main/java/org/hulei/basic/concurrent/thread/ExecutorServiceTest.java)。

### Day 13: ThreadLocal 与异步编程工具
*   **学习重点**：ThreadLocalMap 内存泄漏原因、CompletableFuture 编排能力。
*   **面试真题连环炮**：
    1. `ThreadLocal` 为什么会内存泄漏？为什么 Key 使用弱引用？
    2. 在线程池环境中使用 `ThreadLocal` 有什么风险？如何解决？
    3. `CompletableFuture` 如何实现多个异步任务的组合（如 thenCombine）？
*   **代码实战**：阅读 [CompletableFutureTest.java](file:///d:/project/demo/demo-java/jdk/demo-java-basic/src/main/java/org/hulei/basic/concurrent/future/CompletableFutureTest.java)。

### Day 14: 第二周并发编程模拟面试
*   **任务**：模拟面试官提问：如何实现一个高效的限流器？（提示：AQS 或 Atomic）。

---

## 📅 第三周：JVM 虚拟机深度解剖（高薪分水岭）

### Day 15: JVM 运行时内存区域
*   **学习重点**：堆、栈、方法区（元空间）、直接内存。
*   **面试真题连环炮**：
    1. Java 8 为什么要用元空间取代永久代？
    2. 栈帧中包含哪些内容？为什么局部变量表的大小在编译期就确定了？
    3. 什么时候会触发 `StackOverflowError` 和 `OutOfMemoryError`？
*   **代码实战**：阅读 [JvmThreadStack.java](file:///d:/project/demo/demo-java/jdk/demo-java-basic/src/main/java/org/hulei/basic/jvm/JvmThreadStack.java)。

### Day 16: 对象创建、布局与定位
*   **学习重点**：指针碰撞 vs 空闲列表、TLAB、对象头（Mark Word、Klass Pointer）。
*   **面试真题连环炮**：
    1. 对象在内存中是如何布局的？（Mark Word 存了什么？）
    2. 什么是 TLAB？它解决了什么问题？
    3. 句柄访问和直接指针访问有什么优劣？HotSpot 使用的是哪种？
*   **代码实战**：阅读 [SimpleObject.java](file:///d:/project/demo/demo-java/jdk/demo-java-basic/src/main/java/org/hulei/basic/jvm/SimpleObject.java)。

### Day 17: 垃圾回收算法与判断标准
*   **学习重点**：可达性分析、GC Roots 包含哪些、标记-整理 vs 标记-复制。
*   **面试真题连环炮**：
    1. 哪些对象可以作为 GC Roots？
    2. 为什么分代收集算法将堆分为新生代和老年代？
    3. 什么是 Stop The World (STW)？为什么要尽量避免它？

### Day 18: 垃圾收集器：CMS、G1 与 ZGC
*   **学习重点**：CMS 的四阶段、G1 的 Region 概念、ZGC 的染色指针与读屏障。
*   **面试真题连环炮**：
    1. CMS 为什么会被废弃？它的“并发标记”阶段如何解决多线程干扰？
    2. G1 相比 CMS 的优势在哪里？什么是 Mixed GC？
    3. ZGC 为什么能做到 10ms 以内的延迟？它对大内存的支持如何？

### Day 19: 类加载机制与双亲委派
*   **学习重点**：加载、验证、准备、解析、初始化、双亲委派及其破坏。
*   **面试真题连环炮**：
    1. 类加载的过程是怎样的？“准备”和“初始化”有什么区别？
    2. 为什么要设计双亲委派模型？
    3. 如何破坏双亲委派？Tomcat 为什么要破坏它？
*   **代码实战**：阅读 [MyClassLoader.java](file:///d:/project/demo/demo-java/jdk/demo-java-basic/src/main/java/org/hulei/basic/jvm/MyClassLoader.java)。

### Day 20: 字节码与 JIT 编译优化
*   **学习重点**：常用字节码指令、逃逸分析、方法内联。
*   **面试真题连环炮**：
    1. 什么是 JIT 即时编译？它和解释执行有什么区别？
    2. 逃逸分析如何优化代码？（栈上分配、标量替换、锁消除）。
*   **代码实战**：阅读 [ClassInitialize.java](file:///d:/project/demo/demo-java/jdk/demo-java-basic/src/main/java/org/hulei/basic/jvm/ClassInitialize.java)。

### Day 21: 第三周 JVM 复盘与调优工具
*   **任务**：熟练使用 `jmap`, `jstack`, `Arthas`。

---

## 📅 第四周：性能调优、IO 与高阶特性

### Day 22: Java IO 模型演进
*   **学习重点**：BIO、NIO（Buffer、Channel、Selector）、AIO。
*   **面试真题连环炮**：
    1. 阻塞 IO 和非阻塞 IO 的区别？
    2. NIO 的多路复用是如何实现的？Select/Poll/Epoll 的区别是什么？
    3. 零拷贝（Zero-copy）在 Java 中是如何体现的？
*   **代码实战**：阅读 [NIOUtil.java](file:///d:/project/demo/demo-java/jdk/demo-java-basic/src/main/java/org/hulei/basic/io/NIOUtil.java)。

### Day 23: Netty 基础（NIO 的巅峰应用）
*   **学习重点**：Reactor 线程模型、Pipeline、ByteBuf。
*   **面试真题连环炮**：
    1. 为什么说 Netty 的 ByteBuf 比 Java NIO 的 ByteBuffer 更好用？
    2. Netty 是如何解决 TCP 粘包/拆包问题的？
    3. 讲讲 Netty 的单线程和多线程 Reactor 模型。
*   **代码实战**：参考 [NettySimpleServer.java](file:///d:/project/demo/demo-java/jdk/demo-java-basic/src/main/java/org/hulei/basic/io/netty/NettySimpleServer.java)。

### Day 24: JVM 线上问题排查实战
*   **学习重点**：CPU 100%、频繁 Full GC、内存泄漏排查。
*   **面试真题连环炮**：
    1. 线上 CPU 飙升 100%，你的排查步骤是怎样的？
    2. 内存泄漏和内存溢出有什么区别？如何定位内存泄漏的代码？
    3. 如果系统响应变慢，但 CPU 和内存都不高，你会排查哪些地方？
*   **代码实战**：阅读 [MemoryLeakDemo.java](file:///d:/project/demo/demo-java/jdk/demo-java-basic/src/main/java/org/hulei/basic/jvm/MemoryLeakDemo.java)。

### Day 25: 序列化与 SPI 扩展机制
*   **学习重点**：JDK 序列化的坑、Protobuf、Java SPI 原理。
*   **面试真题连环炮**：
    1. JDK 序列化为什么不推荐使用？有哪些替代方案？
    2. Java SPI 的原理是什么？它有哪些局限性？（提示：Spring 和 Dubbo 是如何改进的）。
*   **代码实战**：阅读 [TranslateEngineLoad.java](file:///d:/project/demo/demo-java/jdk/demo-java-basic/src/main/java/org/hulei/basic/spi/TranslateEngineLoad.java)。

### Day 26: Java 11/17+ 高版本新特性
*   **学习重点**：Var、Records、Sealed Classes、新垃圾收集器。
*   **面试真题连环炮**：
    1. Java 17 相比 Java 8 最大的改进是什么？
    2. 什么是 Sealed Classes（密封类）？它的应用场景是什么？

### Day 27: 设计模式在 JDK 中的应用
*   **学习重点**：单例、工厂、模板方法、装饰器、观察者模式。
*   **面试真题连环炮**：
    1. JDK 源码中哪里用到了装饰器模式？（IO 流）。
    2. 线程池的执行策略用到了哪种设计模式？
*   **代码实战**：阅读 [pattern](file:///d:/project/demo/demo-java/jdk/demo-java-basic/src/main/java/org/hulei/basic/pattern) 包下的所有示例。

### Day 28: 综合模拟面试（一）：基础与并发
### Day 29: 综合模拟面试（二）：JVM 与性能调优
### Day 30: 错题本复盘与心态建设

---

## 🏆 学习规则
1. **每日打卡**：每天阅读相关章节，并在 `jdk/study/answers/dayxx.md` 记录你的回答。
2. **深度评审**：你提交回答后，我会扮演面试官，对你的回答进行打分（0-100）并指出你的“致命伤”。
3. **拒绝死记硬背**：回答时尽量结合你代码仓库中的 [jdk](file:///d:/project/demo/demo-java/jdk) 示例进行说明。

**准备好接受这 30 天的地狱洗礼了吗？如果准备好了，从 Day 01 开始，把你的答案告诉我！**
