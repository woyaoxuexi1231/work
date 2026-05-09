> 请将以下完整内容保存为 `Java基础_30天编码指南.md`。

# Java 基础 · 30天编码驱动面试探源指南

**面试官导师寄语**：这不是一本八股文集，而是一张把 HashMap 扩容、synchronized 锁升级、GC 全过程写进代码里的作战地图。30天后，你不仅能回答“为什么”，还能掏出可运行的源码证明：“我写过，我压测过，我对比过不同版本的行为。”

---

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
9. **synchronized 锁升级过程**：偏向锁→轻量级锁→重量级锁；对象头 Mark Word 变化；自适应自旋。
10. **volatile 可见性与禁止重排**：内存屏障 Happens-Before；MESI 嗅探；DCL 中 volatile 作用。
11. **AQS 框架原理**：CLH 队列变体；`state` 与 `acquire/release`；ReentrantLock 公平/非公平实现。
12. **ReentrantLock 与 Condition**：可重入实现；等待/通知机制；与 synchronized wait/notify 对比。
13. **线程池核心参数与流程**：corePoolSize、maximumPoolSize、keepAliveTime；`ctl` 线程状态位；四种拒绝策略。
14. **ThreadLocal 与内存泄漏**：`ThreadLocalMap` 的 WeakReference Key；`InheritableThreadLocal` 作用。
15. **CAS 与 ABA 问题**：`Unsafe.compareAndSwap` 底层；`AtomicStampedReference` 解决方案。
16. **CountDownLatch/CyclicBarrier/Semaphore**：底层均依赖 AQS；`state` 与共享/独占模式。

### 三、JVM 内存与 GC（6 项）
17. **JVM 内存结构**：堆、栈、方法区/元空间、程序计数器；HotSpot 对象分配过程（指针碰撞/空闲列表）。
18. **对象存活判定与引用类型**：可达性分析；强/软/弱/虚引用及 `ReferenceQueue` 使用。
19. **分代回收与算法**：标记-清除、标记-整理、复制；新生代 Eden/Survivor 比例。
20. **CMS 垃圾收集器**：初始标记、并发标记、重新标记、并发清除；优缺点及浮动垃圾。
21. **G1 垃圾收集器**：Region 划分、Mixed GC、SATB、Remembered Set；`-XX:MaxGCPauseMillis`。
22. **GC 日志解读与调参**：`-Xlog:gc*`（Java9+）或 `-XX:+PrintGCDetails`；`jstat` 实时查看。

### 四、IO/NIO 模型（3 项）
23. **BIO/NIO/AIO 模型与使用**：面向流 vs 面向缓冲；Selector 多路复用；`epoll` 的 LT/ET。
24. **NIO Buffer 与 Channel**：`ByteBuffer` 的 position/limit/capacity/flip；FileChannel 与内存映射。
25. **零拷贝实现**：`FileChannel.transferTo`（`sendfile`）与 `MappedByteBuffer`（`mmap`）的区别与局限性。

### 五、反射、代理与类加载（4 项）
26. **反射机制与开销**：`Class` 获取方式；`Method.invoke` 的 JNI 调用与 `setAccessible(true)`；新版 `MethodHandle`。
27. **JDK 动态代理与 CGLIB**：`Proxy.newProxyInstance` 与 `InvocationHandler`；CGLIB 基于 ASM 生成子类；final 方法限制。
28. **类加载与双亲委派**：`ClassLoader.loadClass()` 流程；破坏双亲委派的 SPI（`Thread Context ClassLoader`）；Tomcat 类加载隔离。

### 六、基础语法糖与异常（2 项，按需增添）
29. **自动装箱与拆箱缓存陷阱**：`Integer.valueOf()` 缓存 -128~127；`==` 与 `equals` 引发的问题。
30. **异常体系与 finally 执行顺序**：checked/unchecked；`try-catch-finally` 中包含 return 时的执行与返回值。

> 以上 28 项严格映射到后续 30 天的每日覆盖中。每一天开篇都会标注当天的覆盖原理点。

---

## 30天原理覆盖映射表

| 天 | 主题 | 覆盖原理编号 |
|----|------|--------------|
| 1 | 手写动态数组与 fail-fast | 5, 29 |
| 2 | 手写 HashMap 基本版（数组+链表） | 1, 2 |
| 3 | HashMap 树化与退化完整实现 | 3, 1 |
| 4 | LinkedHashMap 与 LRU 缓存 | 8 |
| 5 | TreeMap 与红黑树平衡探究 | 7 |
| 6 | ConcurrentHashMap 源码探针（1.8） | 4 |
| 7 | ArrayList 源码攻防与扩容微基准 | 5, 29 |
| 8 | synchronized 锁升级实验（JOL 观察对象头） | 9 |
| 9 | volatile 与 DCL 正确/错误版本对比 | 10 |
| 10 | AQS 自实现与 ReentrantLock 探针 | 11, 12 |
| 11 | 线程池源码级实验 + 自定义拒绝策略 | 13 |
| 12 | ThreadLocal 内存泄漏复现与分析 | 14 |
| 13 | CAS 实战：无锁栈 + ABA 复现与解决 | 15 |
| 14 | 三大并发工具 CountDownLatch 等底层追踪 | 16 |
| 15 | JVM 内存结构实验：堆/栈/元空间溢出 | 17, 22 |
| 16 | 引用类型实验：软引用缓存、WeakHashMap | 18 |
| 17 | GC 算法对比实验：不同收集器下日志分析 | 19, 20, 21, 22 |
| 18 | BIO/NIO 模型对比与小型聊天室 | 23 |
| 19 | NIO Buffer 与零拷贝性能对比 | 24, 25 |
| 20 | 反射方法调用与 MethodHandle 基准 | 26 |
| 21 | JDK 动态代理 vs CGLIB 源码生成剖析 | 27 |
| 22 | 自定义 ClassLoader 打破双亲委派 | 28 |
| 23 | 堆外内存泄漏与直接内存 OOM 复现 | 17, 22 |
| 24 | 死锁检测与 JStack 分析实战 | 11, 22 |
| 25 | CPU 100% 定位（死循环 + 线程转储） | 9, 10, 22 |
| 26 | HashMap 并发死循环复现（Java7）与修复对比 | 2, 4 |
| 27 | 伪共享问题与 `@Contended` 实验 | 10, 15 |
| 28 | 故障注入：模拟长 GC 与线程阻塞 | 21, 13 |
| 29 | 微型 RPC 框架骨架（Day1） | 全综合 |
| 30 | 微型 RPC 框架骨架（Day2）与压力面试 | 全综合 |

---

## 你将产出的面试项目清单

完成本计划后，你的 GitHub 上将多出以下可直接放在简历上的项目/组件：

1. **简化版 HashMap**：支持扩容、树化、LRU 淘汰。
2. **手写 AQS 与 ReentrantLock**：支持公平锁、Condition。
3. **手写线程池**：支持核心/最大线程、任务队列、拒绝策略。
4. **NIO 多路复用聊天室**：Selector + 非阻塞 IO。
5. **自定义类加载器与隔离容器**：打破双亲委派，加载同名类。
6. **微型 RPC 框架**：含动态代理、序列化、注册中心（Mock）、负载均衡、服务熔断。

---

## 第1天：动态数组的本质与 fail-fast 机制 - 覆盖原理点：[5, 29]

### 编码探源题

#### 题目1：手写动态数组并打印扩容时机
**编码要求**：
- 实现一个 `MyArrayList`（只用 Object[] 和 size），支持 `add`, `get`, `remove`。
- 在 `grow()` 时打印旧容量、新容量、当前元素数量，公式：`newCapacity = oldCapacity + (oldCapacity >> 1)`（1.5倍）。
- 编写测试 main 方法，循环插入 20 个元素，观察扩容发生的次数和容量变化。

**🔍 原理反思提问**：为什么 ArrayList 使用 1.5 倍扩容而不是 2 倍？这背后的内存时间权衡是什么？如果你的数组存的是引用类型，扩容数组拷贝时发生了什么？

**💬 面试官可能追问**：在 add 时如果传入的 index 超出 size 但小于 capacity，会怎样？`System.arraycopy` 是浅拷贝，如果某位置被复制两次导致对象被其他引用持有，会有问题吗？

#### 题目2：复现 fail-fast 并捕获 ConcurrentModificationException
**编码要求**：
- 创建一个 `ArrayList` 并迭代，在迭代过程中使用 `list.remove(0)` 而非迭代器的 remove，触发 fail-fast。
- 打印 `modCount` 预期值和异常信息。同时展示使用 `Iterator.remove()` 安全删除的方式。

**🔍 原理反思提问**：fail-fast 的设计目的是什么？在多线程中是否一定可靠？`CopyOnWriteArrayList` 的 fail-safe 是怎么做到的？

**💬 面试官可能追问**：如果一个线程在迭代，另一个线程修改了 `ArrayList`，是否一定抛出异常？请说明 `modCount` 检测的代码位置（`Itr.checkForComodification()`）。

#### 题目3：源码级探针 - 跟踪 ArrayList.grow() 的参数变化
**编码要求**：
- 编写一个测试类，通过反射获取 `ArrayList` 内部的 `elementData` 数组和 `size`，在添加一个超大集合时打印每一步的容量。
- 使用 `-Djava.util.ArrayList.debug=true` （假设）之类的，当然我们通过反射即可。
- 观察添加 `Collections.nCopies(1000, "x")` 时的扩容次数。

**🔍 原理反思提问**：`grow()` 中的 `minCapacity` 是如何计算出来的？`ensureCapacityInternal` 做了什么？

**💬 面试官可能追问**：如果已知要添加大量元素，`ensureCapacity(int)` 和构造器指定容量有什么区别？`ArrayList` 最大容量为什么是 `Integer.MAX_VALUE - 8`？

#### 题目4：自动装箱导致的性能陷阱实验
**编码要求**：
- 写一个循环，使用 `List<Integer>`，进行 100万次求和，比较 `int` 基本类型和 `Integer` 对象带来的时间差异。
- 打印 `Integer.valueOf` 缓存的范围，并故意使用超出缓存的值，观察 `==` 和 `equals` 的区别。
- 使用 `-XX:AutoBoxCacheMax=200` 调整上限并验证。

**🔍 原理反思提问**：自动装箱调用的是 `Integer.valueOf()`，其中缓存范围默认 -128 到 127，这个缓存是以什么数据结构存储的？为什么使用 static 内部类 `IntegerCache`？

**💬 面试官可能追问**：在循环体内部如果频繁装箱，是否推荐使用 `new Integer(x)`？为什么 Java 9 中 `Integer(int)` 被标记为 @Deprecated？

#### 题目5（地狱级）：极值场景下 ArrayList 扩容与 GC 开销压测
**编码要求**：
- 使用 JMH 或简单的时间差，对比 `ArrayList<String>` 和 `LinkedList<String>` 在**尾部插入**、**头部插入**、**随机访问**三种场景下，数据量从 1 万到 100 万的性能曲线。
- 通过设置 JVM 参数 `-Xms128m -Xmx128m` 制造 GC 压力，观察 ArrayList 频繁扩容导致的 GC 停顿。
- 运行代码并记录 GC 日志（`-Xlog:gc*`），分析出 ArrayList 扩容引发的 `byte[]` 拷贝在 GC 中的比例。

**🔍 原理反思提问**：为什么 ArrayList 在频繁扩容场景下会产生大量垃圾？这些垃圾主要是哪些对象？`LinkedList` 的头插为什么优于 ArrayList？其内部 Node 对象分配对 GC 有什么影响？

**💬 面试官可能追问**：如果你的服务中有大量大 ArrayList 且提前未知大小，如何优化？请引出 `Collections.emptyList()` 或对象池设计思路。

### 核心实现题（Day1）
**从零实现一个支持迭代的简化版 ArrayList**（包含 fail-fast）
- 实现完整接口：`add(E e)`, `add(int index, E e)`, `remove(int index)`, `get(int)`, `size()`。
- 实现内部迭代器 `Itr`，包含 `hasNext`, `next`, `remove`，并在 `next` 和 `remove` 中检查 `modCount`。
- 编写单元测试：覆盖并发修改抛出异常、边界 index 异常。
  **🔍 原理反思提问**：你的迭代器是如何感知外部修改的？`modCount` 的修改在哪些方法中调用？移除元素时，为什么需要 `System.arraycopy` 将后续元素前移？
  **💬 面试官可能追问**：在 `remove(int)` 内部，为什么要把数组中最后一个有效位置置为 null？这跟 GC 有什么关联？

---

## 第2天：HashMap 深度解剖（一） - 覆盖原理点：[1, 2]

### 编码探源题

#### 题目1：手写简单的数组+链表 HashMap，打印哈希下标和冲突
**编码要求**：

- 实现一个 `SimpleHashMap<K,V>`，采用数组+链表，不做树化。
- 哈希函数使用 `hashCode & (table.length-1)`。
- 在 `put` 时打印 key 的 hashCode、扰动后的 hash、数组下标、链表长度。
- 故意放入多个产生哈希冲突的 key（自定义类 hashCode 恒定），观察链表增长。

**🔍 原理反思提问**：简单取模与 `hash & (n-1)` 的适用前提是什么？HashMap 是如何通过扰动函数让哈希分布更均匀的？为什么高16位也要参与异或？

**💬 面试官可能追问**：如果数组长度不是 2 的幂次，`& (n-1)` 会怎样？为什么 HashMap 的容量必须是 2 的幂次？请用实验说明。

#### 题目2：通过反射打印 HashMap 内部 table、threshold、size 变化
**编码要求**：

- 创建一个 `HashMap<Integer, String>()`，连续 put 16 个元素，通过反射获取 `table` 数组长度，`threshold`，`size`。
- 在 put 前后打印这些字段，观察扩容触发的边界（默认 loadFactor 0.75，初始化 capacity 16，threshold=12）。

**🔍 原理反思提问**：扩容的具体条件是什么？（`size > threshold` 且 table[i] 不为空）当达到 13 个元素时是否一定扩容？触发扩容的代码在 `putVal` 的哪一行？

**💬 面试官可能追问**：如果在构造函数中指定了 `initialCapacity`，那 `threshold` 会是多少？`tableSizeFor` 做了什么？

#### 题目3：源码探针 - 模拟 `putVal` 过程，打印每一步内部状态
**编码要求**：

- 阅读 JDK 1.8 `HashMap.putVal` 源码（`hash`, `putVal`），写一个插桩版本：复制部分逻辑，在每次插入时打印是否命中首个节点、是否遍历链表、是否转为红黑树。
- 使用 Java Agent 或简单 AOP 可以，但我们要求复制一个模拟类 `DebugHashMap`，暴露关键步骤日志。

**🔍 原理反思提问**：在插入链表尾部时，1.8 使用尾部插入，而 1.7 是头插，这导致了什么并发问题？

**💬 面试官可能追问**：为什么在 `if (binCount >= TREEIFY_THRESHOLD - 1)` 之后还要判断 `tab.length >= MIN_TREEIFY_CAPACITY`，否则只是扩容？

#### 题目4：扩容时链表迁移的模拟实验
**编码要求**：

- 手工构造一个容量为 4 的 HashMap，插入若干元素，使其在扩容时一个槽位的链表被拆分成 loHead/loTail 与 hiHead/hiTail。
- 打印扩容前后每个元素所在的新索引，验证其等于 `oldIndex` 或 `oldIndex + oldCap`。
- 代码需体现 `e.hash & oldCap` 分支。

**🔍 原理反思提问**：为什么 1.8 扩容不需要重新计算哈希，只需要判断 hash & oldCap 是否为 0？这种优化相比 1.7 的 rehash 有什么性能提升？

**💬 面试官可能追问**：在多线程环境下，1.8 的扩容仍然可能有数据丢失，能画出示意图吗？

#### 题目5（地狱级）：通过 JVM 参数限制堆大小，复现 HashMap 扩容导致的 GC 频繁
**编码要求**：

- 设置堆内存 `-Xms32m -Xmx32m`，向一个大 HashMap 中不断插入大量对象，开启 GC 日志 `-Xlog:gc*`。
- 观察扩容导致的连续 Full GC，并分析日志中因分配新数组触发的内存不足。
- 对比指定合适初始容量时 GC 次数。

**🔍 原理反思提问**：大对象分配（新数组）在 GC 中属于直接进入老年代还是伊甸区？什么情况下会触发 Young GC 失败而提前晋升？

**💬 面试官可能追问**：如果创建 HashMap 时传入极大容量（如 1亿），会立即分配 1亿的数组吗？`tableSizeFor` 计算初始阈值有什么陷阱？

### 核心实现题（Day2）
**从零实现一个支持扩容的简化版 HashMap（不含树化）**
- 包含 `put(key, value)`, `get(key)`, `resize()`，使用头插法或尾插法均可。
- 在 `resize` 时完成所有节点的迁移（如简单重新哈希），打印旧表和新表的长度与元素数量。
- 使用 `@Test` 验证：相同 key 插入覆盖旧值，扩容后 key 可正确访问。
  **🔍 原理反思提问**：在并发场景下，你自己的 `resize` 仍有线程安全问题吗？如何解决？（提示：可引出 `ConcurrentHashMap`）
  **💬 面试官可能追问**：有没有比复制整个哈希表更轻量的扩容方式？一致性哈希思想在这里能用吗？

---





## 第3天：HashMap 树化与退化深度实验 - 覆盖原理点：[3, 1]

### 编码探源题

#### 题目1：手动构造哈希冲突，迫使链表转为红黑树
**编码要求**：  
- 重写一个类的 `hashCode()`，使其返回固定值（但 `equals` 正常），作为 HashMap 的 key。  
- 连续插入 10 个该对象（确保运行前通过反射或 JVM 参数使 `MIN_TREEIFY_CAPACITY` 为 64，容量需主动扩充至 64）。  
- 插入过程中打印链表长度，当 >=8 且容量 >=64 时，观察是否调用 `treeifyBin()`，并通过反射查看 table 中是否变为 `TreeNode`。

**🔍 原理反思提问**：树化的两个必要条件是什么？为什么链表长度阈值是 8，退化阈值是 6，多出两个的缓冲区间有什么意义？  
**💬 面试官可能追问**：如果数组长度小于 64，即使链表长度达到 8，HashMap 会做什么？扩容与树化谁先触发，为什么？

#### 题目2：用反射观测树化后节点类型和结构变化
**编码要求**：  
- 在树化前后，通过反射获取 `table` 数组某槽位元素，打印其 Class 名称（`Node` 还是 `TreeNode`）。  
- 树化后，插入一个能引起红黑树旋转的新 key，观察该槽位元素 `moveRootToFront` 是否调用（可添加日志或断点模拟）。

**🔍 原理反思提问**：`TreeNode` 保留了 next 指针，为什么还需要维护红黑树？这如何支持退化为链表？  
**💬 面试官可能追问**：红黑树的查找时间复杂度是 O(log n)，但为何 HashMap 在链表长度<8 时不用树？综合考虑 CPU 缓存、节点大小和内存开销，你会如何解释？

#### 题目3：源码探针 - 复现树化和反树化全过程
**编码要求**：  
- 复制 `HashMap` 的 `treeifyBin` 和 `untreeify` 逻辑到自定义 Debug 类中，在节点类型变化时打印关键变量（如 `hc`, `root`）。  
- 先构造树化场景，记录过程；然后逐个删除节点，当节点数 <=6 时触发 `untreeify`，打印出从 `TreeNode` 变回普通 `Node` 的证据。

**🔍 原理反思提问**：在 `remove` 时，如何判断是否需要反树化？代码中 `if (root == null || root.right == null || (rl = root.left) == null || rl.left == null)` 这个条件怎样粗略判断树节点过少？  
**💬 面试官可能追问**：如果频繁增删，在树化与反树化之间切换，会有性能抖动吗？实际项目中有哪些调优方式？

#### 题目4：红黑树平衡操作的可视化日志
**编码要求**：  
- 编写一个简化的红黑树插入逻辑，每次插入后打印树的结构（缩进表示层次），并进行旋转，打印旋转类型（左旋/右旋）及平衡后的根节点颜色。  
- 用一小批乱序数字插入，观察旋转次数，验证红黑树的平衡性。

**🔍 原理反思提问**：HashMap 的 `balanceInsertion` 代码中，`xpp`（祖父节点）的颜色是如何影响旋转选择的？红黑树与 AVL 树在插入删除时，谁旋转次数更少？  
**💬 面试官可能追问**：为什么红黑树在 Java 中被选中？与完全平衡树（AVL）相比，它的优点是什么？

#### 题目5（地狱级）：模拟树化时机不当引发的频繁退树化，并用 JMH 压测不同负载因子的影响
**编码要求**：  
- 构造一个场景：先让大量冲突 key 树化，然后执行一系列删除插入使得节点数量在 6~10 之间来回摇摆，导致反复树化和反树化。  
- 使用 JMH 测试这种场景下，不同负载因子（0.5, 0.75, 1.0）对吞吐量的影响。  
- 开启 `-Xlog:gc*` 和打印 CPU 时间，分析性能差异的原因。

**🔍 原理反思提问**：频繁树化会涉及较多的内存分配与 GC，如何通过监控 `sun.misc.VM` 或 JFR 事件观察到这些开销？  
**💬 面试官可能追问**：在你的业务中，有没有遇到过 HashMap 此类现象？如果遇到过，最终是如何通过重写 `hashCode` 或更换数据结构（如 `IdentityHashMap`）来解决的？

### 核心实现题（Day3）
**在之前 SimpleHashMap 基础上增加红黑树化功能**
- 实现内部类 `TreeNode`，包含 left/right/parent/red 字段。  
- 实现 `treeify` 和 `balanceInsertion` 简化版（忽略删除反树化）。  
- 在 `putVal` 中检测链表长度 >= TREEIFY_THRESHOLD 且桶数组长度 >= MIN_TREEIFY_CAPACITY 时调用树化；查找时若桶里是 TreeNode 则走红黑树查找。  
- 单元测试：验证树化后查找正确；验证重复插入相同 key 覆盖值。

**🔍 原理反思提问**：你实现的红黑树查找和链表查找，在节点数从 7 增长到 8 时性能差距多大？是否支持 `Comparable` 接口来优化比较？  
**💬 面试官可能追问**：如果 key 没有实现 `Comparable`，HashMap 如何决定红黑树的顺序？`tieBreakOrder` 又是怎么保证一致的？

---

## 第4天：LinkedHashMap 与 LRU 缓存实现 - 覆盖原理点：[8]

### 编码探源题

#### 题目1：通过 LinkedHashMap 观察插入顺序与访问顺序
**编码要求**：  
- 创建两个 `LinkedHashMap`，一个默认 `accessOrder=false`，一个 `accessOrder=true`。  
- 插入几条数据，然后 `get` 某些条目，打印映射内容，观察迭代顺序变化。使用 `Map.Entry` 输出 key 以体现顺序。  
- 在访问顺序模式下反复访问某个 key，观察它是否移动到末尾。

**🔍 原理反思提问**：`accessOrder` 为 true 时，哪个方法在 `get` 后触发了节点移动？`afterNodeAccess` 如何将节点移至链表尾部？这用到了哪些双向链表操作？  
**💬 面试官可能追问**：`LinkedHashMap` 的迭代性能与 `HashMap` 有何不同？为什么不直接继承链表实现的 `LinkedList`？

#### 题目2：手写一个基于 LinkedHashMap 的 LRU 缓存
**编码要求**：  
- 继承 `LinkedHashMap` 并重写 `removeEldestEntry`，当 `size() > capacity` 时返回 true。  
- 插入超过容量的数据，观察最老的条目是否被自动删除，打印每次删除的 key。  
- 验证过期淘汰策略的正确性（最近访问过的条目不会被淘汰）。

**🔍 原理反思提问**：`removeEldestEntry` 是在什么时候被回调的？（`afterNodeInsertion`）如果直接调用 `putAll` 插入大量数据，会不会一次性删除很多元素？  
**💬 面试官可能追问**：这种基于 `LinkedHashMap` 的 LRU 缓存是线程安全的吗？如果要用于多线程环境，有哪些简单包装手段？

#### 题目3：源码探针 - 跟踪 `afterNodeAccess` 和 `afterNodeInsertion` 的回调链
**编码要求**：  
- 继承 `LinkedHashMap` 重写 `afterNodeAccess`、`afterNodeInsertion` 和 `afterNodeRemoval`，添加日志。  
- 分别触发 put、get、remove 操作，观察这三个回调的执行时机和参数（通过传入的 Node 获取 Key）。  
- 结合 HashMap 的源码，说明这三个方法在父类中如何被调用。

**🔍 原理反思提问**：HashMap 中设计了这三个空方法供子类扩展，这体现了什么设计模式？  
**💬 面试官可能追问**：如果我们在 `afterNodeInsertion` 中删除旧节点，是否必须注意并发修改？HashMap 自身的内部锁是什么？

#### 题目4：对比 LRU 与 FIFO 策略的性能
**编码要求**：  
- 实现一个固定大小的 FIFO 缓存（使用队列），与第2题实现的 LRU 进行比较。  
- 使用随机访问序列模拟局部性原理，统计两种策略的命中率，观察 LRU 优势。  
- 打印两种缓存的淘汰记录。

**🔍 原理反思提问**：在什么访问模式下 LRU 的效率反而低于 FIFO？请举例说明。  
**💬 面试官可能追问**：Redis 中使用的是近似 LRU，为什么不用严格的 LRU？空间开销有多大？

#### 题目5（地狱级）：多线程环境下的 LRU 并发问题与解决
**编码要求**：  
- 在多线程并发 put/get 未加锁的 LRU 缓存上操作，复现数据丢失或不一致。  
- 使用 `Collections.synchronizedMap` 包装或自己实现 `ReentrantReadWriteLock` 改造，对比性能。  
- 使用 JMH 压测线程安全版本，分析读写锁在缓存场景下的吞吐量瓶颈。

**🔍 原理反思提问**：`ConcurrentLinkedHashMap`（或 Guava Cache）内部如何实现并发 LRU？它用到了哪些并发队列思想？  
**💬 面试官可能追问**：为什么 `ConcurrentHashMap` 没有直接提供一个并发 LRU 实现？高并发下维护全局双向链表存在什么困难？

### 核心实现题（Day4）
**从零实现一个支持 LRU 的 ConcurrentLruCache**
- 接口：`put(K key, V value)`, `get(K key)`, 容量限制。  
- 内部用一个并发安全的数据结构（如分段锁或读改写锁），并维护一个双向链表（或类似访问顺序）。  
- 实现近似 LRU 算法，比如每过一段时间扫描淘汰一批。  
- 单元测试：验证基本淘汰和并发安全。

**🔍 原理反思提问**：你实现的并发 LRU 与 Guava Cache 的 `maximumSize` 有何设计差异？如果要求始终 O(1) 淘汰，需要什么数据结构？  
**💬 面试官可能追问**：缓存过期除了基于容量，还有基于时间的策略。如果同时要求“最近10分钟未访问即淘汰”，如何集成？

---

## 第5天：TreeMap 与红黑树平衡深度探究 - 覆盖原理点：[7]

### 编码探源题

#### 题目1：观察 TreeMap 的自然排序与比较器排序
**编码要求**：  
- 分别使用无参构造（自然排序）和自定义 `Comparator` 创建 `TreeMap`，插入一串字母，打印迭代顺序。  
- 故意插入未实现 `Comparable` 的对象，观察是否抛出 `ClassCastException`。

**🔍 原理反思提问**：`TreeMap` 的 `put` 方法是如何使用比较器或 `Comparable` 的？若 key 同时实现了 `Comparable` 且传入了比较器，哪个优先？  
**💬 面试官可能追问**：如果比较器返回 0，新值是否会覆盖旧值？TreeMap 和 IdentityHashMap 对“相等”的定义有何不同？

#### 题目2：通过反射观察 TreeMap 内部红黑树的旋转操作次数
**编码要求**：  
- 实现一个自定义 `TimerTreeMap`，继承 `TreeMap` 并重写 `fixAfterInsertion`、`fixAfterDeletion`，统计旋转次数。  
- 按递增顺序插入 10000 个整数，记录旋转次数；然后乱序插入 10000 个整数，对比旋转次数，说明差异。

**🔍 原理反思提问**：顺序插入导致树完全不平衡，为什么红黑树仍能保持相对平衡？它与二叉查找树的最坏情况有何本质区别？  
**💬 面试官可能追问**：TreeMap 的查找复杂度在平衡时是 O(log n)，如果大量插入顺序数据，实际查找深度会如何？会不会影响性能？

#### 题目3：源码探针 - 跟踪 `fixAfterInsertion` 的每一次循环
**编码要求**：  
- 复制 TreeMap 的插入逻辑到一个调试类，并在 `fixAfterInsertion` 中打印当前节点、父节点颜色和旋转类型。  
- 用几个简单的例子（如插入 3,1,2）演示旋转过程，画出最终树结构。

**🔍 原理反思提问**：`fixAfterInsertion` 的 while 循环条件是什么？为什么只检查父节点是否为红色？  
**💬 面试官可能追问**：红黑树的 5 个性质是什么？插入后为什么最多两次旋转即可恢复平衡？

#### 题目4：比较 TreeMap 和 HashMap 在排序场景下的性能
**编码要求**：  
- 生成 10 万个随机 key，分别用 HashMap 存储再排序、以及直接用 TreeMap 存储（自动排序），使用 JMH 测试写入和遍历性能。  
- 分析 TreeMap 的维护红黑树插入成本和 HashMap 的 O(n log n) 排序成本。

**🔍 原理反思提问**：什么情况下应该优先使用 TreeMap 而不是 HashMap 后再排序？内存和 CPU 的 trade-off 是什么？  
**💬 面试官可能追问**：如果你需要持续的排序迭代，还有哪些数据结构可选？`ConcurrentSkipListMap` 的时间复杂度是多少？

#### 题目5（地狱级）：模拟红黑树退化策略与实现一个不依赖比较器的跳表 Map
**编码要求**：  
- 编写一个简化跳表 `SkipListMap`，支持 put/get，打印每层跳表结构。  
- 与 `TreeMap` 比较在随机、顺序、逆序等多种数据分布下的插入、查找性能。  
- 分析哪种分布下跳表更优，为什么 Redis 使用跳表而非红黑树。

**🔍 原理反思提问**：跳表的层数生成机制（概率晋升）如何影响性能？与红黑树相比，跳表在并发实现上有哪些天然优势？  
**💬 面试官可能追问**：`ConcurrentSkipListMap` 是如何实现并发的？它是完全无锁的吗？CAS 在哪些地方使用？

### 核心实现题（Day5）
**手写一个支持排序的简易 TreeMap（红黑树版本）**
- 实现 `put`, `get`, `remove` 及迭代器（中序遍历）。  
- 内部实现左旋、右旋、颜色调整。  
- 单元测试：验证基本性质（根黑色，红色节点子节点黑色，任意路径黑色数量相同）。  
**🔍 原理反思提问**：删除节点时修复红黑树逻辑包含多少种情况？为什么删除比插入复杂？  
**💬 面试官可能追问**：如果要求你的 TreeMap 支持范围查询（subMap），迭代器如何设计？

---





