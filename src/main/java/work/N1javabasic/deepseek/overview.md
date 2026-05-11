> 请将以下完整内容保存为 `Java基础_30天编码指南.md`。

# Java 基础 · 30天编码驱动面试探源指南

**面试官导师寄语**：这不是一本八股文集，而是一张把 HashMap 扩容、synchronized 锁升级、GC 全过程写进代码里的作战地图。30天后，你不仅能回答“为什么”，还能掏出可运行的源码证明：“我写过，我压测过，我对比过不同版本的行为。”

***

## 面试必考原理清单（共 28 项，按模块分组）

### 一、集合框架（8 项）

1. **HashMap 1.8 数据结构与哈希扰动**：数组+链表+红黑树；`hash()` 扰动函数高16位异或。
2. **HashMap 扩容机制**：`resize()` 触发条件（size > threshold）、负载因子0.75、链表分裂与迁移。
3. **HashMap 树化与退树化**：链表长度≥8且数组长度≥64时树化；节点数≤6时退化为链表。
4. **ConcurrentHashMap 1.7 vs 1.8**：分段锁 vs CAS+synchronized；`sizeCtl` 含义；多线程协助扩容（`helpTransfer`）。
5. **ArrayList 扩容与 fail-fast**：`grow()`（约1.5倍扩容）；`modCount` 与 `ConcurrentModificationException`。
6. **LinkedList 与 ArrayDeque**：链表 vs 循环数组；头尾插入性能；队列/双端队列接口。
7. **TreeMap 红黑树排序**：红黑树性质；自然排序与比较器；`compareTo` 一致性。
8. **LinkedHashMap 与 LRU 实现**：双向链表维护顺序；`accessOrder=true` 实现LRU缓存淘汰。

### 二、并发基础与工具（8 项）

1. **synchronized 锁升级过程**：偏向锁→轻量级锁→重量级锁；对象头 Mark Word 变化；自适应自旋。
2. **volatile 可见性与禁止重排**：内存屏障 Happens-Before；MESI 嗅探；DCL 中 volatile 作用。
3. **AQS 框架原理**：CLH 队列变体；`state` 与 `acquire/release`；ReentrantLock 公平/非公平实现。
4. **ReentrantLock 与 Condition**：可重入实现；等待/通知机制；与 synchronized wait/notify 对比。
5. **线程池核心参数与流程**：corePoolSize、maximumPoolSize、keepAliveTime；`ctl` 线程状态位；四种拒绝策略。
6. **ThreadLocal 与内存泄漏**：`ThreadLocalMap` 的 WeakReference Key；`InheritableThreadLocal` 作用。
7. **CAS 与 ABA 问题**：`Unsafe.compareAndSwap` 底层；`AtomicStampedReference` 解决方案。
8. **CountDownLatch/CyclicBarrier/Semaphore**：底层均依赖 AQS；`state` 与共享/独占模式。

### 三、JVM 内存与 GC（6 项）

1. **JVM 内存结构**：堆、栈、方法区/元空间、程序计数器；HotSpot 对象分配过程（指针碰撞/空闲列表）。
2. **对象存活判定与引用类型**：可达性分析；强/软/弱/虚引用及 `ReferenceQueue` 使用。
3. **分代回收与算法**：标记-清除、标记-整理、复制；新生代 Eden/Survivor 比例。
4. **CMS 垃圾收集器**：初始标记、并发标记、重新标记、并发清除；优缺点及浮动垃圾。
5. **G1 垃圾收集器**：Region 划分、Mixed GC、SATB、Remembered Set；`-XX:MaxGCPauseMillis`。
6. **GC 日志解读与调参**：`-Xlog:gc*`（Java9+）或 `-XX:+PrintGCDetails`；`jstat` 实时查看。

### 四、IO/NIO 模型（3 项）

1. **BIO/NIO/AIO 模型与使用**：面向流 vs 面向缓冲；Selector 多路复用；`epoll` 的 LT/ET。
2. **NIO Buffer 与 Channel**：`ByteBuffer` 的 position/limit/capacity/flip；FileChannel 与内存映射。
3. **零拷贝实现**：`FileChannel.transferTo`（`sendfile`）与 `MappedByteBuffer`（`mmap`）的区别与局限性。

### 五、反射、代理与类加载（4 项）

1. **反射机制与开销**：`Class` 获取方式；`Method.invoke` 的 JNI 调用与 `setAccessible(true)`；新版 `MethodHandle`。
2. **JDK 动态代理与 CGLIB**：`Proxy.newProxyInstance` 与 `InvocationHandler`；CGLIB 基于 ASM 生成子类；final 方法限制。
3. **类加载与双亲委派**：`ClassLoader.loadClass()` 流程；破坏双亲委派的 SPI（`Thread Context ClassLoader`）；Tomcat 类加载隔离。

### 六、基础语法糖与异常（2 项，按需增添）

1. **自动装箱与拆箱缓存陷阱**：`Integer.valueOf()` 缓存 -128\~127；`==` 与 `equals` 引发的问题。
2. **异常体系与 finally 执行顺序**：checked/unchecked；`try-catch-finally` 中包含 return 时的执行与返回值。

> 以上 28 项严格映射到后续 30 天的每日覆盖中。每一天开篇都会标注当天的覆盖原理点。

***

## 30天原理覆盖映射表

| 天   | 主题                                      | 覆盖原理编号   |
| ---- | ----------------------------------------- | -------------- |
| 1    | 手写动态数组与 fail-fast                  | 5, 29          |
| 2    | 手写 HashMap 基本版（数组+链表）          | 1, 2           |
| 3    | HashMap 树化与退化完整实现                | 3, 1           |
| 4    | LinkedHashMap 与 LRU 缓存                 | 8              |
| 5    | TreeMap 与红黑树平衡探究                  | 7              |
| 6    | ConcurrentHashMap 源码探针（1.8）         | 4              |
| 7    | ArrayList 源码攻防与扩容微基准            | 5, 29          |
| 8    | synchronized 锁升级实验（JOL 观察对象头） | 9              |
| 9    | volatile 与 DCL 正确/错误版本对比         | 10             |
| 10   | AQS 自实现与 ReentrantLock 探针           | 11, 12         |
| 11   | 线程池源码级实验 + 自定义拒绝策略         | 13             |
| 12   | ThreadLocal 内存泄漏复现与分析            | 14             |
| 13   | CAS 实战：无锁栈 + ABA 复现与解决         | 15             |
| 14   | 三大并发工具 CountDownLatch 等底层追踪    | 16             |
| 15   | JVM 内存结构实验：堆/栈/元空间溢出        | 17, 22         |
| 16   | 引用类型实验：软引用缓存、WeakHashMap     | 18             |
| 17   | GC 算法对比实验：不同收集器下日志分析     | 19, 20, 21, 22 |
| 18   | BIO/NIO 模型对比与小型聊天室              | 23             |
| 19   | NIO Buffer 与零拷贝性能对比               | 24, 25         |
| 20   | 反射方法调用与 MethodHandle 基准          | 26             |
| 21   | JDK 动态代理 vs CGLIB 源码生成剖析        | 27             |
| 22   | 自定义 ClassLoader 打破双亲委派           | 28             |
| 23   | 堆外内存泄漏与直接内存 OOM 复现           | 17, 22         |
| 24   | 死锁检测与 JStack 分析实战                | 11, 22         |
| 25   | CPU 100% 定位（死循环 + 线程转储）        | 9, 10, 22      |
| 26   | HashMap 并发死循环复现（Java7）与修复对比 | 2, 4           |
| 27   | 伪共享问题与 `@Contended` 实验            | 10, 15         |
| 28   | 故障注入：模拟长 GC 与线程阻塞            | 21, 13         |
| 29   | 微型 RPC 框架骨架（Day1）                 | 全综合         |
| 30   | 微型 RPC 框架骨架（Day2）与压力面试       | 全综合         |

***

## 你将产出的面试项目清单

完成本计划后，你的 GitHub 上将多出以下可直接放在简历上的项目/组件：

1. **简化版 HashMap**：支持扩容、树化、LRU 淘汰。
2. **手写 AQS 与 ReentrantLock**：支持公平锁、Condition。
3. **手写线程池**：支持核心/最大线程、任务队列、拒绝策略。
4. **NIO 多路复用聊天室**：Selector + 非阻塞 IO。
5. **自定义类加载器与隔离容器**：打破双亲委派，加载同名类。
6. **微型 RPC 框架**：含动态代理、序列化、注册中心（Mock）、负载均衡、服务熔断。