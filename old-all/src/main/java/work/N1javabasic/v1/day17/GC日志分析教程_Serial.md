# Serial GC 日志分析完全教程

> 从零开始，逐行拆解 `gc_serial.log`，让你彻底看懂 GC 日志。

---

## 一、前置知识：JVM 堆内存结构

在看日志之前，你必须先理解 Serial GC 下的堆内存分区：

```
┌─────────────────────────────────────────────────────┐
│                     JVM 堆 (Heap)                    │
├────────────────────────────┬────────────────────────┤
│      新生代 (Young Gen)     │    老年代 (Tenured)     │
├──────────┬─────┬───────────┤                        │
│  Eden区  │From │   To      │                        │
│  (伊甸园) │Survivor│Survivor│                        │
└──────────┴─────┴───────────┴────────────────────────┘
```

**关键概念：**

| 区域 | 作用 | 日志中的名称 |
|------|------|-------------|
| Eden区 | 新对象首先分配在这里 | `Eden` |
| From Survivor | 上一次Young GC的幸存者 | `From` |
| To Survivor | 本次Young GC的幸存目标区 | `To` |
| 老年代 | 存活时间长/太大的对象晋升到这里 | `Tenured` |
| 元空间 | 存放类元数据（不在堆中，在本地内存） | `Metaspace` |

**GC 触发时机：**
- **Young GC（Minor GC）**：Eden 区满了 → 触发
- **Full GC（Major GC）**：老年代也放不下了 / 手动调用 `System.gc()`

---

## 二、日志头部信息（初始化段）

```log
[2026-05-14T13:32:56.107+0800][info][gc] Using Serial
```
**含义**：当前使用的垃圾收集器是 **Serial GC**（单线程、STW 收集器）。

```log
[2026-05-14T13:32:56.107+0800][info][gc,init] Version: 17.0.18+8-LTS-264 (release)
[2026-05-14T13:32:56.107+0800][info][gc,init] CPUs: 20 total, 20 available
[2026-05-14T13:32:56.107+0800][info][gc,init] Memory: 32549M
```
**含义**：
- JDK 版本：17.0.18
- 可用 CPU 核数：20
- 系统物理内存：约 32GB

```log
[2026-05-14T13:32:56.107+0800][info][gc,init] Heap Min Capacity: 128M
[2026-05-14T13:32:56.107+0800][info][gc,init] Heap Initial Capacity: 128M
[2026-05-14T13:32:56.107+0800][info][gc,init] Heap Max Capacity: 256M
```
**含义**：对应 JVM 参数 `-Xms128m -Xmx256m`
- 堆初始大小：128M
- 堆最大容量：256M（当内存不够时会动态扩容，最大到256M）

---

## 三、日志格式万能解读公式

JDK 17 统一日志格式（UL）每行结构：

```
[时间戳][级别][标签] 内容
```

| 字段 | 含义 |
|------|------|
| `时间戳` | 精确到毫秒的事件发生时间 |
| `info` | 日志级别（info/debug/trace） |
| `gc,start` | 标签：gc事件开始 |
| `gc,heap` | 标签：堆内存变化 |
| `gc,phases` | 标签：GC 阶段（仅 Full GC 有） |
| `gc,cpu` | 标签：CPU 耗时 |
| `gc` | 标签：GC 总结行 |

---

## 四、逐行拆解第一次 Young GC（GC(0)）

### 4.1 GC 开始

```log
[2026-05-14T13:32:56.452+0800][info][gc,start] GC(0) Pause Young (Allocation Failure)
```

**逐字段解读：**
- `GC(0)` → 第 0 次 GC 事件（从 0 开始编号）
- `Pause Young` → 这是一次 Young GC（新生代 GC），**会暂停所有应用线程（STW）**
- `(Allocation Failure)` → 触发原因：Eden 区分配内存失败（满了）

### 4.2 堆各区域变化

```log
GC(0) DefNew: 34944K(39296K)->4352K(39296K) Eden: 34944K(34944K)->0K(34944K) From: 0K(4352K)->4352K(4352K)
```

**万能阅读公式：`区域名: 使用前(总容量)->使用后(总容量)`**

| 区域 | GC 前使用 | 总容量 | GC 后使用 | 解读 |
|------|-----------|--------|-----------|------|
| DefNew(整个新生代) | 34944K(≈34M) | 39296K(≈38M) | 4352K(≈4M) | 回收了约 30M |
| Eden | 34944K | 34944K | 0K | Eden 被完全清空 ✅ |
| From | 0K | 4352K | 4352K | 幸存对象进入 Survivor |

> **核心理解**：Young GC 会清空 Eden，存活对象复制到 Survivor 区。

### 4.3 老年代变化

```log
GC(0) Tenured: 0K(87424K)->14104K(87424K)
```

| 项目 | 值 | 含义 |
|------|-----|------|
| GC 前 | 0K | 老年代之前是空的 |
| GC 后 | 14104K(≈14M) | 有 14M 对象晋升到老年代 |
| 总容量 | 87424K(≈85M) | 老年代总大小 |

> **为什么有对象晋升？** Survivor 区放不下的对象，直接晋升到老年代。

### 4.4 GC 总结行

```log
GC(0) Pause Young (Allocation Failure) 34M->18M(123M) 5.557ms
```

**万能公式：`GC类型 堆使用前->堆使用后(堆总大小) 耗时`**

| 字段 | 值 | 含义 |
|------|-----|------|
| 堆 GC 前 | 34M | 整个堆使用了 34M |
| 堆 GC 后 | 18M | GC 后堆还在使用 18M |
| 堆总大小 | 123M | 当前堆总容量 |
| **回收量** | **34-18=16M** | 本次 GC 释放了 16M 内存 |
| **耗时** | **5.557ms** | 应用暂停了 5.5 毫秒 |

### 4.5 CPU 耗时

```log
GC(0) User=0.00s Sys=0.02s Real=0.01s
```

| 字段 | 含义 |
|------|------|
| User | 用户态 CPU 时间（GC 线程计算耗时） |
| Sys | 内核态 CPU 时间（系统调用，如内存分配） |
| Real | **实际墙钟时间（STW 暂停时间）** ← 最关注的！ |

> **面试关键点**：Serial GC 是单线程的，所以 `User + Sys ≈ Real`。如果是 Parallel GC，`User + Sys > Real`（多线程并行，墙钟时间短）。

---

## 五、后续 Young GC 快速对比（GC 1-3）

| GC编号 | 类型 | 堆变化 | 回收量 | 耗时 | 老年代增长 |
|--------|------|--------|--------|------|-----------|
| GC(0) | Young | 34M→18M | 16M | 5.6ms | 0→14M |
| GC(1) | Young | 51M→50M | 1M | 8.1ms | 14M→48M |
| GC(2) | Young | 83M→83M | 0M | 7.2ms | 48M→81M |
| GC(3) | Young | 116M→116M | 0M | 7.2ms | 继续增长 |

**趋势分析：**
- 回收量越来越少（从 16M → 0M）
- 老年代增长越来越快（0→14→48→81M）
- **说明**：程序中持有的大量对象是存活的（pool 中的 byte[]），GC 回收不掉，只能不断晋升到老年代

> 🔑 **诊断结论**：对象存活率太高，新生代 GC 效率极低，老年代快速被填满。

---

## 六、Full GC 详细拆解（GC(4)）

### 6.1 触发

```log
GC(4) Pause Full (Allocation Failure)
```

**触发原因**：`Allocation Failure` — 新生代 GC 后对象需要晋升到老年代，但老年代也放不下了。

### 6.2 Full GC 的四个阶段

Serial GC 的 Full GC 使用 **Mark-Compact（标记-整理）** 算法：

```log
Phase 1: Mark live objects        1.688ms   ← 标记所有存活对象
Phase 2: Compute new object addresses 0.480ms   ← 计算整理后的新地址
Phase 3: Adjust pointers          0.978ms   ← 更新所有引用指向新地址
Phase 4: Move objects             0.300ms   ← 实际移动对象到新位置
```

**图解 Mark-Compact 过程：**

```
标记前：  [存活][垃圾][存活][垃圾][垃圾][存活]
           ↓ Phase 1: 标记存活
标记后：  [存活✓][垃圾][存活✓][垃圾][垃圾][存活✓]
           ↓ Phase 2-4: 整理压缩
整理后：  [存活][存活][存活][          空闲空间          ]
```

> **优点**：没有内存碎片   **缺点**：需要移动对象，耗时长

### 6.3 Full GC 结果

```log
GC(4) DefNew: 38554K(39296K)->4096K(78656K)
GC(4) Tenured: 81196K(87424K)->114989K(174784K)
GC(4) Pause Full (Allocation Failure) 116M->116M(247M) 3.713ms
```

**关键信息：**
- 堆总容量从 123M 扩容到 **247M**（接近最大值 256M）
- 堆使用从 116M → 116M，**一点都没减少！**
- 老年代从 87M 扩容到 174M

> 🔑 **诊断结论**：Full GC 回收了 0 字节有效内存，同时触发了堆扩容。说明这些对象全部是存活的，不是垃圾。

---

## 七、System.gc() 触发的 Full GC（GC(6)）

```log
GC(6) Pause Full (System.gc())
```

**区别**：触发原因是代码中主动调用了 `System.gc()`（对应 GCGenerator.java 第 66 行）。

```log
GC(6) Pause Full (System.gc()) 194M->122M(247M) 14.769ms
```

**这次回收有效果了！**
- 回收了 194-122 = **72M** 内存
- 为什么有效果？因为 GCGenerator.java 中在 `System.gc()` 之前的循环中随机删除了 pool 中的对象

---

## 八、GC 事件全局时间线

```
时间(ms)     事件              堆使用    暂停时间   效果
─────────────────────────────────────────────────────────
452ms        Young GC(0)      34M→18M   5.6ms     ✅ 回收16M
466ms        Young GC(1)      51M→50M   8.1ms     ⚠️ 仅回收1M
476ms        Young GC(2)      83M→83M   7.2ms     ❌ 无效
484ms        Young GC(3)      116M→116M 7.2ms     ❌ 无效
492ms        Full GC(4)       116M→116M 3.7ms     ❌ 无效(扩容)
505ms        Young GC(5)      179M→155M 11.0ms    ✅ 回收24M
517ms        Full GC(6)       194M→122M 14.8ms    ✅ 回收72M(System.gc)
537ms        Young GC(7)      189M→172M 8.4ms     ⚠️ 回收17M
548ms        Young GC(8)      239M→239M 0.1ms     ❌ 无效
548ms        Full GC(9)       239M→118M 10.6ms    ✅ 回收121M
562ms        Full GC(10)      185M→116M 11.6ms    ✅ 回收69M(System.gc)
579ms        Young GC(11)     183M→158M 4.0ms     ✅ 回收25M
```

**总暂停时间**：约 92ms（所有 GC 暂停之和）
**程序总运行时间**：约 134ms（从 452ms 到 586ms）
**GC 占比**：92/134 ≈ **68.7%** ← 非常严重！

---

## 九、Heap Exit（程序退出时堆快照）

```log
def new generation   total 78656K, used 42716K
  eden space 69952K,  49% used
  from space 8704K,   94% used    ← Survivor 快满了
  to   space 8704K,    0% used    ← 空的（正常，To 区在非GC时总是空的）
tenured generation   total 174784K, used 153849K  ← 老年代 88% 使用率！
Metaspace       used 2341K, committed 2560K
```

**解读：**
- 老年代使用率 88%：非常高，随时可能再次 Full GC
- Survivor From 94%：下次 Young GC 大概率有大量对象直接晋升
- 这个程序如果继续运行，会频繁 Full GC → OOM

---

## 十、结合代码理解 GC 行为

### GCGenerator.java 关键代码逻辑：

```java
// 每次分配 1MB 的 byte[]
byte[] chunk = new byte[CHUNK_SIZE];  // CHUNK_SIZE = 1MB
pool.add(chunk);

// 40% 概率随机删除 pool 中 1/3 的对象
if (pool.size() > POOL_SIZE / 2 && random.nextDouble() < 0.4) {
    int removeCount = random.nextInt(pool.size() / 3) + 1;
    ...
}
```

**为什么 GC 表现这样？**

1. **每次分配 1MB**：Eden 区约 34MB，分配约 34 个对象就满了 → 触发 Young GC
2. **对象存活率极高**：pool 持有引用的对象不会被回收
3. **删除概率低(40%)**且**删除量少(1/3)**：导致 pool 不断膨胀
4. **大对象持续存活 → 老年代快速填满 → 频繁 Full GC**

---

## 十一、核心诊断技巧总结

### 🎯 看一条 GC 日志，你需要关注的 5 个要点：

| # | 关注点 | 怎么看 | 危险信号 |
|---|--------|--------|----------|
| 1 | **GC 类型** | `Pause Young` / `Pause Full` | Full GC 频繁出现 |
| 2 | **触发原因** | 括号中的原因 | `Allocation Failure` 反复出现 |
| 3 | **回收效果** | `前→后` 差值 | GC 后内存几乎没减少 |
| 4 | **暂停时间** | 最后的 ms 数字 | 单次 >200ms 需警惕 |
| 5 | **老年代趋势** | Tenured 使用量 | 持续增长不回落 = 内存泄漏 |

### 🚨 常见问题模式识别：

| 模式 | 日志特征 | 可能原因 |
|------|----------|----------|
| **内存泄漏** | 老年代 GC 后使用量只升不降 | 对象被持有引用无法回收 |
| **内存不足** | Full GC 频繁 + 回收量极少 | 堆太小 / 存活对象太多 |
| **大对象分配** | Young GC 回收后老年代突然增大 | 大对象直接进入老年代 |
| **过早晋升** | Survivor 始终接近 100% | Survivor 区太小 |

### 🔧 调优方向：

```
问题：Full GC 太频繁
  → 增大堆 -Xmx / 增大老年代比例
  → 检查代码是否有内存泄漏

问题：Young GC 回收率低
  → 检查对象是否过早晋升（增大 Survivor 或 -XX:MaxTenuringThreshold）
  → 检查是否有大量长生命周期对象

问题：GC 暂停时间太长
  → 换用低延迟收集器（G1/ZGC）
  → 减小堆大小（Mark 阶段扫描更快）
```

---

## 十二、快速参考卡片

### JDK 17 GC 日志参数

```bash
# 输出到文件
-Xlog:gc*=info:file=gc.log:time,level,tags

# 输出到控制台
-Xlog:gc*=info::time,level,tags

# 详细输出（含 debug 级别）
-Xlog:gc*=debug:file=gc_debug.log:time,level,tags
```

### 内存单位换算

```
1 KB = 1024 Bytes
1 MB = 1024 KB
1 GB = 1024 MB

日志中的 K = KB，如 34944K ≈ 34MB（34944 ÷ 1024）
```

### Serial GC 特点

| 特性 | 说明 |
|------|------|
| 线程 | 单线程 |
| Young GC 算法 | 复制算法（Copy） |
| Full GC 算法 | 标记-整理（Mark-Compact） |
| STW | 两种 GC 都会暂停应用 |
| 适用场景 | 客户端/小堆/教学 |
| JVM 参数 | `-XX:+UseSerialGC` |

---

## 十三、练习：自己分析这条日志

试着分析下面这条，写出你的解读：

```log
[2026-05-14T13:32:56.548+0800][info][gc] GC(9) Pause Full (Allocation Failure) 239M->118M(247M) 10.647ms
```

<details>
<summary>点击查看答案</summary>

- **GC 编号**：第 9 次 GC
- **类型**：Full GC（整个堆的垃圾回收）
- **触发原因**：Allocation Failure（分配内存失败，新生代和老年代都放不下）
- **GC 前堆使用**：239M（几乎用满了 247M 的堆）
- **GC 后堆使用**：118M
- **回收量**：239 - 118 = **121M** 被释放
- **堆总容量**：247M
- **暂停时间**：10.647ms
- **诊断**：回收了大量内存(121M)，说明之前 pool 中有大量对象被 remove 了变成了垃圾。但堆使用率仍高达 48%，后续仍有 Full GC 风险。

</details>

---

> 💡 **记住**：分析 GC 日志的核心就是回答三个问题：
> 1. **什么时候 GC？**（频率）
> 2. **GC 了多少？**（回收效果）
> 3. **花了多长时间？**（对应用的影响）
>
> 把这三个答案连起来看趋势，就能诊断出内存问题。
