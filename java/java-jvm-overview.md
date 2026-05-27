好的，以下是基于 **JDK 8** 环境调整后的 JVM 核心面试原理清单。结合你简历中"熟悉 JVM 基本原理，了解 GC 回收机制、类加载过程及 Java 内存模型"的描述，面试官很可能围绕这些点进行深挖。

---

## JDK 8 JVM 核心面试原理清单

---

### 一、运行时数据区域与内存模型

**P1: JVM 运行时数据区域划分（JDK 8）**
- **线程私有**：程序计数器（无 OOM）、虚拟机栈（StackOverflowError/OOM）、本地方法栈
- **线程共享**：堆（OOM）、方法区（JDK 8 用**元空间 Metaspace** 替代永久代，使用本地内存，OOM）
- **直接内存**：NIO 的 DirectByteBuffer 使用，不受堆大小限制，OOM 时 dump 文件很小

**P2: 对象内存布局与指针压缩**
- 对象头（Mark Word 8 字节 + Klass 指针）、实例数据、对齐填充
- JDK 8 默认开启指针压缩（`-XX:+UseCompressedOops`），Klass 指针和引用从 8 字节压缩为 4 字节
- 压缩后对象头为 12 字节（Mark Word 8 + Klass 4），堆内存小于 32GB 时有效

**P3: 元空间（Metaspace）vs 永久代（PermGen）**
- JDK 8 移除永久代，类元数据移至本地内存的 Metaspace
- Metaspace 可动态扩展，避免 PermGen OOM，但需关注 `MaxMetaspaceSize` 限制
- 字符串常量池和静态变量从 PermGen 移到堆中（JDK 7 已移字符串常量池）


### 二、类加载机制

**P4: 类加载双亲委派机制**
- 三层类加载器：Bootstrap ClassLoader（启动类，C++ 实现，`System.getProperty("sun.boot.class.path")` 查看）→ Extension ClassLoader（扩展类，`jre/lib/ext`）→ Application ClassLoader（应用类，`classpath`）
- 工作流程：`loadClass` 中先检查是否已加载 → 委托父加载器 → 父无法加载才自己加载
- **JDK 8 中 SPI 破坏场景**：`ServiceLoader` 加载核心库接口（如 JDBC Driver），使用线程上下文类加载器（Thread Context ClassLoader）反转委派，从 `classpath` 加载实现类

**P5: 类加载链接阶段（验证、准备、解析）**
- **验证**：文件格式、元数据、字节码、符号引用验证
- **准备**：为静态变量分配内存并赋零值；`static final` 常量直接赋常量值（基本类型和字符串）
- **解析**：符号引用转为直接引用（可选延迟至初始化后）
- **初始化**：`<clinit>` 方法执行，JVM 保证多线程下只执行一次（加锁）


### 三、垃圾回收与对象生命周期

**P6: 分代假说与堆内存划分**
- 弱分代假说：绝大多数对象朝生夕灭 → 新生代用复制算法
- 强分代假说：熬过多次 GC 的对象更难消亡 → 老年代用标记-清除/整理
- JDK 8 堆结构：新生代（Eden:S0:S1 = 8:1:1） + 老年代
- 跨代引用：老年代引用新生代对象，通过**卡表（Card Table）**记录，避免全堆扫描

**P7: 可达性分析与 GC Roots**
- **GC Roots 集合**（JDK 8）：
    - 虚拟机栈中引用的对象（局部变量表）
    - 方法区中静态变量引用的对象
    - 方法区中常量引用的对象（字符串常量池在堆中，但引用本身仍视为 Root）
    - 本地方法栈中 JNI 引用的对象
    - 活跃线程对象
    - 同步锁（synchronized）持有的对象
- 区别于引用计数法：解决循环引用问题

**P8: 四种引用类型及其应用**
- **强引用**：`Object o = new Object()`，GC 永不回收
- **软引用**：`SoftReference`，OOM 前回收，适合内存敏感缓存
- **弱引用**：`WeakReference`，下次 GC 必回收；`WeakHashMap` 的 key、`ThreadLocal` 的 key 都是弱引用
- **虚引用**：`PhantomReference`，无法获取对象，仅用于对象回收跟踪（`DirectByteBuffer` 的 `Cleaner` 用于释放直接内存）

**P9: 垃圾回收算法与堆内存分配**
- **标记-清除**：直接回收不标记对象，产生碎片
- **复制**：Eden 存活对象 → S0/S1，清理 Eden，S0/S1 轮换，无碎片但浪费空间
- **标记-整理**：老年代对象存活率高，移动对象整理碎片，减少分配开销
- **对象分配优先在 Eden**；大对象（`-XX:PretenureSizeThreshold` 设定）直接进老年代


### 四、JDK 8 垃圾收集器

**P10: Serial / Parallel / CMS 收集器**
- **Serial**：单线程，适合 Client 模式或小堆，新生代 Serial（复制），老年代 Serial Old（标记-整理）
- **Parallel**：吞吐量优先，新生代 Parallel Scavenge（复制），老年代 Parallel Old（标记-整理）；`-XX:MaxGCPauseMillis` 和 `-XX:GCTimeRatio` 调优
- **CMS**（Concurrent Mark Sweep）：
    - 步骤：初始标记（STW，查 GC Roots 直接可达）→ 并发标记 → 重新标记（STW，处理并发阶段新产生的引用变化）→ 并发清除
    - 问题：**浮动垃圾**（并发清除时产生的垃圾，本次无法回收）、**内存碎片**（需 `-XX:+UseCMSCompactAtFullCollection` 整理）、**Concurrent Mode Failure**（老年代剩余空间不足，退化为 Serial Old 导致长 STW）

**P11: G1 收集器（JDK 8 可用，但需显式开启 `-XX:+UseG1GC`）**
- 堆分为多个等大 Region（1~32MB，`-XX:G1HeapRegionSize`），取消物理分代，逻辑上仍区分 E/S/O
- **RSet（Remembered Set）**：每个 Region 维护一个记忆集，记录"谁引用了我"，通过卡表实现
- **SATB（Snapshot-At-The-Beginning）**：并发标记开始时拍快照，通过写前屏障记录引用变化，保证并发标记不漏标
- **Mixed GC**：优先回收垃圾最多的 Region（Garbage First），包含所有新生代 + 部分老年代

**P12: CMS 调优参数与常见陷阱**
- `-XX:CMSInitiatingOccupancyFraction`：老年代使用比例达阈值触发并发收集（默认 92%）
- `-XX:+UseCMSInitiatingOccupancyOnly`：仅用设定阈值触发，禁止 JVM 动态调整
- **Promotion Failed**：新生代晋升老年代时空间不足，触发 Full GC（Serial Old），非常耗时
- **Concurrent Mode Failure**：并发标记期间老年代被填满，需在 CMS 中预留足够的浮动垃圾空间


### 五、安全点与并发机制

**P13: 安全点（SafePoint）与安全区域（SafeRegion）**
- **安全点**：GC 执行时线程必须停在安全点（方法调用、循环回边、异常跳转等位置设置）
- JVM 在 GC 时设置全局标志，线程运行到安全点主动暂停（主动式中断）
- **安全区域**：线程不在 CPU 上执行（Sleep/Blocked），无法响应中断时，进入安全区域保证不会改变对象引用

**P14: 卡表（Card Table）与写屏障**
- 卡表：字节数组，每个 byte 对应 512 字节的老年代内存块
- **写屏障**：引用赋值时，生成一个"写屏障"指令，将对应的卡表标记为脏（dirty card）
- Minor GC 扫描新生代时，只扫描脏卡对应的老年代块，而非整个老年代

**P15: 动态年龄判断**
- 对象年龄存储在对象头 Mark Word 中，每次 Minor GC 存活年龄 +1
- 晋升阈值 `-XX:MaxTenuringThreshold`（默认 15）
- **动态年龄判断**：如果 Survivor 中**同龄对象的总大小**超过 Survivor 的一半，则年龄大于等于该年龄的对象直接晋升，可能绕过最大晋升阈值


### 六、编译优化

**P16: 逃逸分析与优化（JDK 8 默认开启）**
- **逃逸分析**（`-XX:+DoEscapeAnalysis`）：分析对象作用域是否逃逸出方法或线程
- **栈上分配**：未逃逸对象在栈帧内分配（HotSpot 实际用标量替换，非真正的栈上分配）
- **标量替换**（`-XX:+EliminateAllocations`）：将对象的成员变量拆为基本类型局部变量
- **同步锁消除**（`-XX:+EliminateLocks`）：未逃逸对象的同步块可以消除锁

**P17: 分层编译（Tiered Compilation，JDK 8 默认开启）**
- Level 0：解释执行
- Level 1：C1 编译，无 profiling（简单方法）
- Level 2：C1 编译，带轻量 profiling
- Level 3：C1 编译，带完整 profiling
- Level 4：C2 编译，基于 profiling 数据做激进优化
- 方法调用次数或循环回边次数达到阈值时升级

**P18: 方法内联**
- 将频繁调用的方法体直接嵌入调用者，消除方法调用开销
- 内联阈值：`-XX:MaxInlineSize`（默认 35 字节）、`-XX:FreqInlineSize`（热点方法 325 字节）
- **getter/setter 几乎必内联**；虚方法通过**类型继承分析（CHA）** 去虚化后内联


### 七、并发与内存模型

**P19: synchronized 锁升级（JDK 8 偏向锁默认延迟开启）**
- 偏向锁（JDK 8 启动后 4 秒才开启，`-XX:BiasedLockingStartupDelay=4000`）→ 轻量级锁（CAS 自旋）→ 重量级锁（`monitorenter/monitorexit`，`ObjectMonitor`）
- Mark Word 根据锁状态存储不同信息：偏向线程 ID、Lock Record 指针、Monitor 指针
- 偏向锁撤销在 SafePoint 执行，高竞争场景可关闭（`-XX:-UseBiasedLocking`）

**P20: volatile 的内存屏障实现**
- volatile 写：前插 StoreStore 屏障，后插 StoreLoad 屏障
- volatile 读：前插 LoadLoad 屏障，后插 LoadStore 屏障
- 保证可见性（写立即刷新，读从主存拉取）和有序性（禁止重排序），不保证原子性


### 八、监控与排障

**P21: JDK 8 自带监控工具**
- **jstat**：`jstat -gc <pid> 1000` 实时查看 GC 情况（YGC、FGC、各代使用量）
- **jmap**：`jmap -heap <pid>` 查看堆配置，`jmap -dump:format=b,file=heap.bin <pid>` 导出堆转储
- **jstack**：`jstack <pid>` 查看线程堆栈，检测死锁（会输出 Found one Java-level deadlock）
- **jinfo**：查看和动态修改 JVM 参数
- **MAT（Memory Analyzer Tool）**：分析 heap dump，看支配树、浅堆/深堆、GC Root 引用链

**P22: OOM 排查流程**
- 先确认是堆 OOM（`java.lang.OutOfMemoryError: Java heap space`）还是元空间 OOM（`Metadata space`）或直接内存 OOM
- 堆 OOM：`-XX:+HeapDumpOnOutOfMemoryError` 自动 dump，MAT 分析大对象和 GC Root 路径
- 元空间 OOM：检查是否频繁热部署（不同 ClassLoader）或类加载泄漏
- 直接内存 OOM：检查 `-XX:MaxDirectMemorySize` 设置和 NIO 使用情况


### JDK 8 关键默认参数速查

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `-XX:+UseCompressedOops` | 开启 | 指针压缩 |
| `-XX:+UseCompressedClassPointers` | 开启 | Klass 指针压缩 |
| `-XX:+UseParallelGC` | 开启 | 默认 Parallel 收集器（非 CMS/G1） |
| `-XX:MetaspaceSize` | ~21MB | 触发 Full GC 的元空间阈值 |
| `-XX:MaxMetaspaceSize` | 无限制 | 元空间最大大小（建议设置） |
| `-XX:+TieredCompilation` | 开启 | 分层编译 |
| `-XX:+DoEscapeAnalysis` | 开启 | 逃逸分析 |
| `-XX:BiasedLockingStartupDelay` | 4000ms | 偏向锁延迟开启 |

---

这份清单覆盖了 JDK 8 JVM 面试的核心知识点。需要我针对其中某几个点，以面试官追问的方式给你出一套模拟面试题吗？