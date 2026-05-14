# G1 GC 日志分析完全教程

> G1 是 JDK 17 的默认收集器，也是面试最高频的 GC 知识点。本教程基于真实 `gc_g1.log` 逐行拆解。

---

## 一、G1 GC 的革命性设计

### 与 Serial/Parallel 的根本区别

| 对比项 | Serial/Parallel | **G1 GC** |
|--------|----------------|-----------|
| 堆结构 | 固定的 Young/Old 分区 | **Region（区块）化，动态分配** |
| 收集粒度 | 整个 Young 或整个堆 | **选择性收集部分 Region** |
| 大对象 | 直接放老年代 | **专门的 Humongous Region** |
| 并发标记 | 无（STW 全量标记） | **有！应用线程与 GC 并发** |
| 设计目标 | 吞吐量优先 | **可预测的低延迟** |
| 默认参数 | `-XX:+UseSerialGC/ParallelGC` | **JDK 9+ 默认** |

### G1 的堆内存结构（核心！）

```
┌───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┐
│ E │ E │ S │ O │ O │ H │ H │ E │ O │ E │ F │ F │
└───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┘
  E = Eden Region        S = Survivor Region
  O = Old Region         H = Humongous Region
  F = Free Region（空闲）
```

**关键理解**：
- 堆被切成大小相等的 **Region**（本例每个 Region = 1MB）
- 每个 Region 在任何时刻只属于一种角色（Eden/Survivor/Old/Humongous/Free）
- G1 可以动态调整哪些 Region 属于 Young、哪些属于 Old
- **不存在固定的"新生代大小"** — G1 根据暂停时间目标自动调整

---

## 二、日志头部 — G1 特有配置

```log
[info][gc] Using G1
[info][gc,init] Heap Region Size: 1M
[info][gc,init] Parallel Workers: 15
[info][gc,init] Concurrent Workers: 4
[info][gc,init] Concurrent Refinement Workers: 15
```

| 参数 | 值 | 含义 |
|------|-----|------|
| **Heap Region Size** | **1M** | 每个 Region 的大小（G1 独有！） |
| Parallel Workers | 15 | STW 阶段的并行 GC 线程数 |
| **Concurrent Workers** | **4** | **并发标记线程数（与应用线程同时运行）** |
| Concurrent Refinement Workers | 15 | 维护 RSet 的后台线程数 |

> **Region Size 计算规则**：`堆大小 / 2048`（目标约 2048 个 Region），最终取 1M/2M/4M/8M/16M/32M 中最接近的值。本例 256M / 2048 = 0.125M → 取最小值 1M。

---

## 三、G1 的五种 GC 类型（全部出现在本日志中！）

| 类型 | 日志标识 | 是否 STW | 说明 |
|------|----------|----------|------|
| **Young GC** | `Pause Young (Normal)` | STW | 只收集 Young Region |
| **Concurrent Start** | `Pause Young (Concurrent Start)` | STW | Young GC + 触发并发标记 |
| **Mixed GC** | `Pause Young (Mixed)` | STW | 收集 Young + 部分 Old Region |
| **Concurrent Mark Cycle** | `Concurrent Mark Cycle` | **并发！** | 后台标记存活对象 |
| **Full GC** | `Pause Full` | STW | 最后手段，整堆压缩 |

### G1 的工作流程图

```
Eden 满了
    │
    ▼
Young GC ──(堆使用率达到阈值)──► Concurrent Start
    │                                    │
    │                              Concurrent Mark Cycle
    │                              （后台并发标记）
    │                                    │
    │                                    ▼
    │                              标记完成，知道哪些 Old Region 垃圾多
    │                                    │
    ▼                                    ▼
Prepare Mixed ──────────────────► Mixed GC
                                  （收集 Young + 垃圾最多的 Old Region）
    │
    │ 如果 Mixed GC 也救不了...
    ▼
Full GC（G1 Compaction Pause）← 最后的救命稻草
```

---

## 四、逐行拆解第一次 Young GC（GC(0)）

### 4.1 触发与线程

```log
GC(0) Pause Young (Normal) (G1 Evacuation Pause)
GC(0) Using 3 workers of 15 for evacuation
```

- `Pause Young (Normal)` → 普通的 Young GC
- `G1 Evacuation Pause` → 触发原因：将存活对象"疏散"（复制）到其他 Region
- `Using 3 workers of 15` → 只用了 3 个线程（因为 GC 工作量小，自适应调节）

### 4.2 GC 阶段（G1 Young GC 特有）

```log
GC(0)   Pre Evacuate Collection Set: 0.0ms    ← 选择要收集的 Region 集合
GC(0)   Merge Heap Roots: 0.0ms               ← 合并堆中的 GC Roots
GC(0)   Evacuate Collection Set: 3.0ms        ← 🔑 核心！复制存活对象
GC(0)   Post Evacuate Collection Set: 0.4ms   ← 收尾（更新引用等）
GC(0)   Other: 0.4ms                          ← 其他杂项
```

**G1 的"Evacuation"（疏散）= 其他收集器的"复制"**：
把选定 Region 中的存活对象复制到空闲 Region，然后回收整个旧 Region。

### 4.3 Region 变化（G1 独有的日志格式！）

```log
GC(0) Eden regions: 23->0(15)
GC(0) Survivor regions: 0->3(3)
GC(0) Old regions: 0->12
GC(0) Archive regions: 0->0
GC(0) Humongous regions: 5->3
```

**阅读公式**：`区域: GC前Region数->GC后Region数(下次分配的目标数)`

| 区域 | GC 前 | GC 后 | 解读 |
|------|-------|-------|------|
| Eden | 23 个 Region(23M) | 0 (清空) | Eden 全部回收，下次分配 15 个 |
| Survivor | 0 | 3 | 3 个 Region 的对象幸存 |
| Old | 0 | 12 | 12M 对象晋升到 Old |
| **Humongous** | **5** | **3** | **5 个大对象 Region → 回收了 2 个** |

### 4.4 总结行

```log
GC(0) Pause Young (Normal) (G1 Evacuation Pause) 28M->17M(128M) 4.055ms
```

- 堆使用 28M → 17M，回收了 11M
- 暂停时间 **4.055ms** — 非常短！

---

## 五、G1 的核心概念：Humongous Object（巨型对象）

### 什么是 Humongous？

```log
GC(0) Humongous regions: 5->3
GC(1) Humongous regions: 45->45
GC(3) Humongous regions: 107->107
GC(9) Humongous regions: 165->165
```

**定义**：对象大小 ≥ Region Size 的 50% = **Humongous Object**

本例中：
- Region Size = 1M
- `new byte[1024 * 1024]` = 1MB ≥ 0.5M → **每个 byte[] 都是 Humongous！**

**Humongous 的特殊行为**：
1. 直接分配在 Old 区的连续 Region 中（跳过 Eden）
2. 不参与 Young GC 的复制（太大了复制成本高）
3. 只有在**并发标记完成后**或 **Full GC** 时才能被回收
4. 是导致本例 GC 频繁的**根本原因**！

### 为什么日志中 Humongous 不断增长？

```
GC(0):   5 个 Humongous
GC(1):  45 个 → 一下增加了 40 个！
GC(3): 107 个
GC(9): 165 个
GC(11): 187 个
GC(15): 225→189 个（并发标记完成，终于回收了一些！）
```

> **核心诊断**：程序每次循环分配 1MB byte[]，全部变成 Humongous Region，Eden 几乎没被用到。G1 的 Young GC 对 Humongous 无能为力，必须等并发标记完成才能回收。

---

## 六、并发标记周期（Concurrent Mark Cycle）详解

### 6.1 触发条件

```log
GC(1) Pause Young (Concurrent Start) (G1 Humongous Allocation)
GC(2) Concurrent Mark Cycle
```

- `Concurrent Start` = "这次 Young GC 完了之后，启动并发标记"
- `G1 Humongous Allocation` = 触发原因：Humongous 分配时发现堆快满了

**触发阈值**：当堆使用率达到 `InitiatingHeapOccupancyPercent`（默认 45%）时触发。

### 6.2 并发标记的六个子阶段

```log
GC(2) Concurrent Clear Claimed Marks         0.018ms  ← 清除上次标记
GC(2) Concurrent Scan Root Regions            0.553ms  ← 扫描 Survivor 作为 Root
GC(2) Concurrent Mark From Roots              0.388ms  ← 🔑 从 Root 出发并发标记
GC(2) Concurrent Preclean                     0.084ms  ← 预清理（处理标记期间的变化）
GC(2) Pause Remark                            0.550ms  ← ⚠️ STW！最终标记修正
GC(2) Concurrent Rebuild Remembered Sets      0.514ms  ← 重建 RSet（跨 Region 引用表）
GC(2) Pause Cleanup                           0.067ms  ← ⚠️ STW！清理空 Region
GC(2) Concurrent Cleanup for Next Mark        0.314ms  ← 为下次标记做准备
```

**关键理解**：

| 阶段 | 并发/STW | 含义 |
|------|----------|------|
| Concurrent Mark From Roots | **并发** | 和应用线程同时运行，标记所有存活对象 |
| **Pause Remark** | **STW** | 处理并发标记期间的引用变化（SATB 写屏障） |
| **Pause Cleanup** | **STW** | 识别完全空的 Region 并回收 |

> **面试关键**：G1 的并发标记不是为了直接回收内存，而是为了**找出哪些 Old Region 垃圾最多**，为后续 Mixed GC 做准备。

### 6.3 并发标记总耗时

```log
GC(2) Concurrent Mark Cycle 3.189ms
```

这 3.2ms 大部分是并发的（不暂停应用），只有 Remark(0.55ms) 和 Cleanup(0.07ms) 是 STW。

---

## 七、Mixed GC（混合收集）

### 7.1 什么是 Mixed GC？

```log
GC(3) Pause Young (Prepare Mixed) (G1 Preventive Collection)
GC(4) Pause Young (Mixed) (G1 Preventive Collection)
```

**Mixed GC = Young GC + 选择性收集部分 Old Region**

G1 在并发标记完成后，知道了哪些 Old Region 的垃圾比例最高（"Garbage First" 名字的由来！），优先回收垃圾最多的 Region。

### 7.2 Prepare Mixed vs Mixed

| 阶段 | 含义 |
|------|------|
| `Prepare Mixed` | 准备阶段，选择要收集的 Old Region |
| `Mixed` | 实际收集 Young + 选中的 Old Region |

### 7.3 G1 Preventive Collection

```log
(G1 Preventive Collection)
```

这是 JDK 17 新增的特性！当 G1 检测到堆即将耗尽时，主动触发预防性收集，避免滑入 Full GC。

---

## 八、G1 的 Full GC — 最后手段

### 8.1 触发模式

```log
[gc,ergo] Attempting full compaction
GC(29) Pause Full (G1 Compaction Pause)
```

**触发条件**：Young GC 无法释放足够空间 → G1 尝试 Full Compaction。

本日志中的模式：
```
Young GC: 252M->252M（完全无效）
  ↓
"Attempting full compaction"  ← G1 的求救信号
  ↓
Pause Full (G1 Compaction Pause)
```

### 8.2 G1 Full GC 的四个阶段

```log
GC(29) Phase 1: Mark live objects        1.014ms
GC(29) Phase 2: Prepare for compaction   0.280ms
GC(29) Phase 3: Adjust pointers          0.564ms
GC(29) Phase 4: Compact heap             0.286ms
GC(29) Pause Full (G1 Compaction Pause)  252M->214M(256M) 2.795ms
```

与 Serial/Parallel 的 Full GC 类似（Mark-Compact），但 G1 使用多线程执行：
```log
Using 6 workers of 15 for full compaction
```

### 8.3 Concurrent Mark Abort

```log
GC(23) Concurrent Mark From Roots 11.636ms
GC(23) Concurrent Mark Abort               ← 被中断了！
GC(23) Concurrent Mark Cycle 11.712ms
```

**含义**：Full GC 发生时，正在后台运行的并发标记被强制中止（因为 Full GC 会移动所有对象，之前的标记信息失效）。

---

## 九、System.gc() 在 G1 中的表现

```log
GC(24) Pause Full (System.gc()) 213M->212M(256M) 2.705ms
```

G1 中 `System.gc()` 直接触发 Full GC，但回收效果极差（只回收了 1M），因为几乎所有 Humongous 对象都还被 pool 引用着。

---

## 十、全局数据统计

### GC 事件分布

| 类型 | 次数 | 总 STW 时间 |
|------|------|-------------|
| Young GC (Normal/Concurrent Start) | ~50 次 | 约 25ms |
| Mixed GC | 1 次 | 0.6ms |
| Full GC (Compaction + System.gc) | 6 次 | 约 15ms |
| Concurrent Mark Cycle | ~15 轮 | 并发（Remark STW 约 6ms） |
| **总计** | **~70 次 GC 事件** | **STW 约 46ms** |

### 时间跨度

- 程序 GC 区间：43.773s ~ 44.034s = **261ms**
- 总 STW 暂停：约 46ms
- **GC 占比**：46/261 ≈ **17.6%** ← 比 Serial(68.7%) 和 Parallel(57.4%) 好太多！

### 但为什么 GC 次数这么多？

因为 G1 的策略是：**每次暂停尽可能短，但允许多次小暂停**。

| 收集器 | 单次最大暂停 | GC 总次数 | 总暂停时间 |
|--------|-------------|-----------|-----------|
| Serial | 14.8ms | 12 | 92ms |
| Parallel | 9.9ms | 11 | 65ms |
| **G1** | **4.1ms** | **70** | **46ms** |

> G1 用"多次微暂停"替代"少次长暂停"，对延迟敏感的应用友好得多。

---

## 十一、本日志的核心问题：Humongous 灾难

### 问题根因

```java
byte[] chunk = new byte[1024 * 1024]; // 1MB = Region Size = Humongous!
```

当对象大小 ≥ Region Size × 50% 时：
1. 绕过 Eden，直接进入 Humongous Region
2. Young GC 无法回收它
3. 只有并发标记完成后才能识别为垃圾
4. 如果并发标记来不及完成 → 堆被 Humongous 塞满 → Full GC

### 解决方案

```bash
# 方案一：增大 Region Size，让 1MB 不再是 Humongous
-XX:G1HeapRegionSize=4m   # 阈值变为 2M，1MB 对象正常进 Eden

# 方案二：增大堆，给并发标记更多时间
-Xmx1g -Xms1g

# 方案三：降低并发标记触发阈值（更早开始标记）
-XX:InitiatingHeapOccupancyPercent=30
```

---

## 十二、G1 日志阅读速查表

### Region 日志格式

```
Eden regions: 23->0(15)
              │    │  │
              │    │  └── 下次 Eden 分配目标数（G1 自适应调节）
              │    └── GC 后的 Region 数
              └── GC 前的 Region 数
```

### G1 独有的触发原因

| 触发原因 | 含义 | 严重程度 |
|----------|------|----------|
| `G1 Evacuation Pause` | 正常的 Young GC | ✅ 正常 |
| `G1 Humongous Allocation` | Humongous 分配触发 | ⚠️ 注意 |
| `G1 Preventive Collection` | 预防性收集（避免 Full GC） | ⚠️ 堆压力大 |
| `G1 Compaction Pause` | G1 兜底 Full GC | ❌ 非常严重 |
| `Concurrent Mark Abort` | 并发标记被 Full GC 打断 | ❌ 系统濒临崩溃 |

### 并发标记 vs STW 标记

```
✅ Concurrent = 后台运行，不暂停应用
⚠️ Pause = STW，暂停应用
```

---

## 十三、三大收集器终极对比

| 指标 | Serial | Parallel | **G1** |
|------|--------|----------|--------|
| GC 总次数 | 12 | 11 | **70** |
| 单次最大暂停 | 14.8ms | 9.9ms | **4.1ms** |
| 总 STW 时间 | 92ms | 65ms | **46ms** |
| GC 时间占比 | 68.7% | 57.4% | **17.6%** |
| 退出时堆使用率 | 88% | 59% | **93%** |
| 并发 GC | 无 | 无 | **有** |
| 适用场景 | 教学/客户端 | 批处理/后台任务 | **Web服务/低延迟** |

**G1 为什么退出时堆使用率最高(93%)？**
因为 G1 的策略是"刚好够用就行"——只要暂停时间达标，不追求把堆清得很干净。这正是"低延迟优先"的设计哲学。

---

## 十四、面试高频考点

### Q1：G1 的 "Garbage First" 名字是什么意思？

**标准答案**：G1 通过并发标记找出每个 Region 的垃圾占比，在 Mixed GC 时**优先回收垃圾比例最高的 Region**（Garbage First），以最小的工作量回收最多的内存，从而控制暂停时间。

### Q2：G1 什么时候会退化成 Full GC？

**标准答案**：三种情况——
1. **Evacuation Failure**：复制存活对象时找不到空闲 Region（堆满了）
2. **Humongous Allocation Failure**：分配大对象时找不到连续空闲 Region
3. **并发标记来不及完成**：对象分配速率 > 并发标记速率，堆被填满

日志特征：`Attempting full compaction` → `Pause Full (G1 Compaction Pause)`

### Q3：Humongous Object 对 G1 有什么影响？

**标准答案**：
- 直接分配在 Old Region，不经过 Eden → 绕过了 Young GC 的快速回收路径
- 可能占用多个连续 Region → 加剧内存碎片
- 只有并发标记完成后才能回收 → 回收延迟大
- 大量 Humongous 会频繁触发并发标记和 Full GC

**解决**：增大 `-XX:G1HeapRegionSize` 使对象不再被判定为 Humongous。

### Q4：G1 的 Concurrent Mark 和 CMS 的有什么区别？

**标准答案**：
- G1 使用 **SATB（Snapshot-At-The-Beginning）** 写屏障，CMS 使用增量更新
- G1 的 Remark 阶段更快（SATB 产生的漏标少）
- G1 标记后会进行 Mixed GC（选择性回收），CMS 只做并发清除（有碎片）
- G1 有 Region 化的 Remembered Set，CMS 没有

---

## 十五、G1 调优参数速查

| 参数 | 默认值 | 作用 |
|------|--------|------|
| `-XX:MaxGCPauseMillis` | **200ms** | 目标最大暂停时间（G1 核心参数！） |
| `-XX:G1HeapRegionSize` | 自动计算 | Region 大小（1M~32M） |
| `-XX:InitiatingHeapOccupancyPercent` | 45% | 触发并发标记的堆使用率阈值 |
| `-XX:G1MixedGCCountTarget` | 8 | 一次并发标记后最多做几次 Mixed GC |
| `-XX:G1HeapWastePercent` | 5% | 允许浪费的堆空间比例 |
| `-XX:ConcGCThreads` | 自动 | 并发标记线程数 |
| `-XX:ParallelGCThreads` | 自动 | STW 阶段并行线程数 |

**调优黄金法则**：
```
1. 只设 -XX:MaxGCPauseMillis（通常 100~200ms）
2. 让 G1 自己调节其他参数
3. 如果有大量 Humongous → 增大 G1HeapRegionSize
4. 如果 Full GC 频繁 → 降低 IHOP 或增大堆
```

---

## 十六、快速诊断检查清单

拿到一份 G1 日志，按以下顺序检查：

```
□ 1. 是否有 Full GC？（搜索 "Pause Full"）
     → 有 = 系统有严重问题
□ 2. 是否有 "Concurrent Mark Abort"？
     → 有 = 并发标记跟不上分配速度
□ 3. Humongous regions 是否持续增长？
     → 是 = 大对象问题，调大 RegionSize
□ 4. Pause Remark 耗时是否 >50ms？
     → 是 = 并发标记期间引用变化太多
□ 5. Mixed GC 后老年代使用率是否下降？
     → 否 = 存活对象太多，可能内存泄漏
□ 6. 单次 Young GC 是否超过 MaxGCPauseMillis？
     → 是 = Region 数太多或 RSet 太大
```

---

> 💡 **一句话记住 G1**：
> "把堆切成小块(Region)，并发标记找出垃圾最多的块，优先回收它们(Garbage First)，用'多次微暂停'替代'少次长暂停'，是 JDK 17 默认的面向低延迟的收集器。"
