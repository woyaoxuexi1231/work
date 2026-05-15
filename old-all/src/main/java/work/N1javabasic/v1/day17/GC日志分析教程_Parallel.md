# Parallel GC 日志分析完全教程

> 基于 `gc_parallel.log` 逐行拆解，与 Serial GC 对比学习，掌握多线程并行收集器的日志特征。

---

## 一、Parallel GC vs Serial GC：核心区别

| 对比项 | Serial GC | Parallel GC |
|--------|-----------|-------------|
| GC 线程数 | 1（单线程） | 多线程（本例 15 线程） |
| 新生代名称 | `DefNew` | `PSYoungGen` |
| 老年代名称 | `Tenured` | `ParOldGen` |
| Young GC 算法 | 复制算法（单线程） | 复制算法（多线程并行） |
| Full GC 算法 | Mark-Compact（单线程） | Mark-Compact（多线程并行） |
| Full GC 触发特有原因 | — | `Ergonomics`（自适应策略） |
| 设计目标 | 简单、低开销 | **最大化吞吐量** |
| JVM 参数 | `-XX:+UseSerialGC` | `-XX:+UseParallelGC` |

> **吞吐量** = 应用运行时间 / (应用运行时间 + GC 时间)。Parallel GC 的目标是让这个比值尽可能高。

---

## 二、日志头部信息解读

```log
[2026-05-14T14:07:39.903+0800][info][gc] Using Parallel
```
**收集器确认**：使用 Parallel GC。

```log
[2026-05-14T14:07:39.926+0800][info][gc,init] Parallel Workers: 15
```
**关键信息！** GC 工作线程数 = **15**。这是 Parallel GC 独有的配置行。
- 默认公式：`ParallelGCThreads = CPU核数 ≤ 8 ? CPU核数 : 8 + (CPU核数-8) × 5/8`
- 本机 20 核：8 + (20-8) × 5/8 = 8 + 7.5 = 15.5 → 取 15

```log
Heap Min Capacity: 128M
Heap Initial Capacity: 128M
Heap Max Capacity: 256M
```
与 Serial GC 实验相同：`-Xms128m -Xmx256m`。

---

## 三、Parallel GC 的堆内存结构

```
┌────────────────────────────────────────────────────────┐
│                     JVM 堆 (Heap)                       │
├─────────────────────────────┬──────────────────────────┤
│   PSYoungGen (新生代)        │   ParOldGen (老年代)      │
├──────────┬──────┬───────────┤                          │
│  Eden区  │ From │    To     │                          │
│          │Surv  │  Surv     │                          │
└──────────┴──────┴───────────┴──────────────────────────┘
     PS = Parallel Scavenge        Par = Parallel Old
```

**命名规则记忆法**：
- `PS` = **P**arallel **S**cavenge（新生代收集器名称）
- `Par` = **Par**allel（老年代收集器名称）

---

## 四、逐行拆解第一次 Young GC（GC(0)）

### 4.1 触发

```log
GC(0) Pause Young (Allocation Failure)
```
- `Pause Young` → Young GC，STW 暂停
- `Allocation Failure` → Eden 满了，分配失败

### 4.2 新生代变化

```log
GC(0) PSYoungGen: 33280K(38400K)->5115K(38400K) Eden: 33280K(33280K)->0K(33280K) From: 0K(5120K)->5115K(5120K)
```

**阅读公式**：`区域: 使用前(容量)->使用后(容量)`

| 区域 | GC 前 | 容量 | GC 后 | 解读 |
|------|-------|------|-------|------|
| PSYoungGen(整个新生代) | 33280K(≈32M) | 38400K(≈37M) | 5115K(≈5M) | 回收了约 27M |
| Eden | 33280K | 33280K | 0K | 完全清空 ✅ |
| From Survivor | 0K | 5120K | 5115K | 存活对象复制到 Survivor |

### 4.3 老年代变化

```log
GC(0) ParOldGen: 0K(87552K)->12424K(87552K)
```

| GC 前 | GC 后 | 含义 |
|-------|-------|------|
| 0K | 12424K(≈12M) | 12M 对象从新生代晋升到老年代 |

> **晋升原因**：Survivor 区只有 5M，放不下所有存活对象，多余的直接进老年代。

### 4.4 总结行

```log
GC(0) Pause Young (Allocation Failure) 32M->17M(123M) 4.915ms
```

| 字段 | 值 | 含义 |
|------|-----|------|
| 堆 GC 前 | 32M | 整个堆使用 32M |
| 堆 GC 后 | 17M | GC 后堆使用 17M |
| 回收量 | 15M | 释放了 15M |
| 堆总容量 | 123M | 当前堆大小 |
| **暂停时间** | **4.915ms** | 应用暂停了不到 5ms |

### 4.5 CPU 时间

```log
GC(0) User=0.00s Sys=0.00s Real=0.01s
```

> **与 Serial GC 对比的关键点**：
> - Serial GC：`User + Sys ≈ Real`（单线程，一对一）
> - Parallel GC：`User + Sys ≥ Real`（多线程并行，CPU 时间累加）
> 
> 本例因为堆太小 GC 太快，差异不明显。在大堆场景下，你会看到类似：
> `User=0.15s Sys=0.01s Real=0.02s` → 15个线程并行，墙钟时间只有 20ms！

---

## 五、GC(1)：观察堆动态扩容

```log
GC(1) PSYoungGen: 37762K(38400K)->5108K(71680K) Eden: 32647K(33280K)->0K(66560K) From: 5115K(5120K)->5108K(5120K)
GC(1) Pause Young (Allocation Failure) 49M->47M(155M) 5.260ms
```

**发现扩容！**

| 指标 | GC(0) 时 | GC(1) 后 | 变化 |
|------|----------|----------|------|
| Eden 容量 | 33280K(32M) | 66560K(65M) | **扩了一倍** |
| 新生代总容量 | 38400K(37M) | 71680K(70M) | 扩容 |
| 堆总容量 | 123M | 155M | 扩容 |

> **为什么自动扩容？** 这就是 Parallel GC 的 **Ergonomics（自适应调节）** 机制！
> JVM 发现 GC 太频繁（Eden 一下就满了），自动把 Eden 扩大，减少 GC 频率，提升吞吐量。

---

## 六、Parallel GC 独有的 Full GC 触发原因：Ergonomics

### 6.1 什么是 Ergonomics？

```log
GC(3) Pause Full (Ergonomics)
```

**这是 Parallel GC 特有的 Full GC 触发原因！**

| 触发原因 | 含义 | 出现在哪个收集器 |
|----------|------|-----------------|
| `Allocation Failure` | 分配内存失败（空间不够） | 所有收集器 |
| `System.gc()` | 代码主动调用 | 所有收集器 |
| **`Ergonomics`** | **JVM 自适应策略主动触发** | **仅 Parallel GC** |

**Ergonomics 决策逻辑**：
- JVM 预判："如果我现在不做 Full GC，老年代马上就要满了"
- 或者："晋升速率太快，老年代剩余空间不足以接纳下一次 Young GC 的晋升对象"
- 于是**提前主动触发** Full GC，避免更严重的 OOM

> 简单记忆：**Ergonomics = JVM 觉得该 GC 了，主动出手**

---

## 七、Parallel GC 的 Full GC 阶段详解（GC(3)）

```log
GC(3) Marking Phase     2.015ms   ← 多线程并行标记存活对象
GC(3) Summary Phase     0.009ms   ← 统计各区域存活对象密度
GC(3) Adjust Roots      0.674ms   ← 调整 GC Roots 引用
GC(3) Compaction Phase  6.560ms   ← 多线程并行压缩整理
GC(3) Post Compact      0.370ms   ← 收尾工作（更新引用等）
```

**与 Serial GC Full GC 对比：**

| Serial GC | Parallel GC | 区别 |
|-----------|-------------|------|
| Phase 1: Mark live objects | Marking Phase | 功能相同，Parallel 多线程执行 |
| Phase 2: Compute new addresses | Summary Phase | Parallel 更智能，统计密度决定是否移动 |
| Phase 3: Adjust pointers | Adjust Roots | 功能相同 |
| Phase 4: Move objects | Compaction Phase | Parallel 多线程移动对象 |
| — | Post Compact | Parallel 独有的收尾阶段 |

**Summary Phase 的特殊含义**：
Parallel GC 不是简单地把所有对象都压缩到一端，而是先统计各区域的"存活密度"。密度高的区域不动（没必要移动大量存活对象），只压缩密度低的区域。这就是为什么耗时极短（0.009ms）—— 只是决策，不实际移动。

### Full GC 结果

```log
GC(3) PSYoungGen: 4144K(71680K)->3504K(71680K)
GC(3) ParOldGen: 110069K(110592K)->110429K(175104K)
GC(3) Pause Full (Ergonomics) 111M->111M(241M) 9.898ms
```

| 指标 | 值 | 解读 |
|------|-----|------|
| 回收量 | 111M→111M = **0** | 没有回收任何内存！ |
| 老年代扩容 | 110M → 175M | 老年代从 108M 扩到 171M |
| 堆总容量 | 123M → 241M | 接近最大值 256M |

> **诊断**：对象全部存活，GC 无法回收，只能扩容。

---

## 八、System.gc() 在 Parallel GC 中的特殊表现

```log
GC(5) Pause Young (System.gc())      142M->137M(251M) 2.167ms
GC(6) Pause Full (System.gc())       137M->114M(251M) 5.788ms
```

**重要发现！** 调用一次 `System.gc()`，Parallel GC 实际执行了 **两次** GC：
1. 先做一次 Young GC（清理新生代）
2. 再做一次 Full GC（清理整个堆）

**对比 Serial GC**：Serial GC 调用 `System.gc()` 只触发一次 Full GC。

> 这是因为 Parallel GC 认为：先做 Young GC 可以减少 Full GC 需要扫描的对象数量，从而让 Full GC 更高效。

---

## 九、全局时间线

```
时间(ms)     事件                    堆变化         暂停     效果
──────────────────────────────────────────────────────────────────
244ms        Young GC(0)            32M→17M       4.9ms    ✅ 回收15M
267ms        Young GC(1)            49M→47M       5.3ms    ⚠️ 仅回收2M
278ms        Young GC(2)            111M→111M     8.3ms    ❌ 无效
288ms        Full GC(3) Ergonomics  111M→111M     9.9ms    ❌ 无效(扩容)
302ms        Full GC(4) Ergonomics  175M→99M      5.9ms    ✅ 回收76M
310ms        Young GC(5) System.gc  142M→137M     2.2ms    ⚠️ 回收5M
313ms        Full GC(6) System.gc   137M→114M     5.8ms    ✅ 回收23M
323ms        Full GC(7) Ergonomics  188M→110M     5.4ms    ✅ 回收78M
333ms        Full GC(8) Ergonomics  185M→121M     6.1ms    ✅ 回收64M
342ms        Full GC(9) System.gc   173M→116M     6.1ms    ✅ 回收57M
352ms        Full GC(10) Ergonomics 190M→102M     5.5ms    ✅ 回收88M
```

**统计指标：**
- 总 GC 次数：11 次（2 次 Young GC 有效 + 8 次 Full GC + 1 次无效 Young GC）
- 总暂停时间：≈ 65.4ms
- 程序 GC 区间：244ms ~ 358ms = 约 114ms
- **GC 占比**：65.4 / 114 ≈ **57.4%**

---

## 十、Parallel GC vs Serial GC 实测对比

| 指标 | Serial GC | Parallel GC | 优胜 |
|------|-----------|-------------|------|
| 总 GC 次数 | 12 次 | 11 次 | Parallel |
| Young GC 次数 | 8 次 | 3 次 | Parallel（Eden 扩容后 GC 频率降低） |
| Full GC 次数 | 4 次 | 8 次 | Serial（Parallel 的 Ergonomics 更激进） |
| 总暂停时间 | ≈92ms | ≈65ms | **Parallel 快 29%** |
| 单次 Full GC 最大耗时 | 14.8ms | 9.9ms | **Parallel 快 33%** |
| GC 时间占比 | 68.7% | 57.4% | **Parallel 更好** |
| 程序退出时老年代使用率 | 88% | 59% | **Parallel 更健康** |

**结论**：
- Parallel GC 通过多线程并行，显著缩短了单次 GC 暂停时间
- Ergonomics 自适应策略让堆管理更智能（自动扩容 Eden、提前触发 Full GC）
- 老年代压力更小（59% vs 88%），说明 Parallel GC 的整理压缩更彻底

---

## 十一、Parallel GC 独有的诊断要点

### 看到 `Ergonomics` 触发的 Full GC 怎么判断？

| 场景 | 日志特征 | 诊断 | 调优 |
|------|----------|------|------|
| 偶尔出现 | Full GC 回收大量内存，之后长时间无 GC | ✅ 正常 | 不需要调 |
| 频繁出现 | 连续多次 Ergonomics Full GC | ⚠️ 老年代压力大 | 增大堆/检查泄漏 |
| 回收量为 0 | `111M->111M` | ❌ 对象全存活 | 检查代码是否持有过多引用 |

### CPU 时间怎么判断多线程是否生效？

```
理想情况：User=0.10s Sys=0.01s Real=0.01s
                                        ↑ 15个线程并行，墙钟时间短
```

```
异常情况：User=0.01s Sys=0.00s Real=0.01s
                                        ↑ 和单线程一样？可能堆太小，GC 工作量不足以发挥并行优势
```

本例中因为堆只有 256M，GC 工作量极小，多线程优势体现不明显。**在生产环境（堆 4G+）时差异会非常显著。**

---

## 十二、Heap Exit 对比分析

### Parallel GC 退出时的堆状态：

```log
PSYoungGen      total 81920K, used 27626K   ← 新生代使用 27M/80M (35%)
  eden space 76800K, 35% used               ← Eden 35%（很健康）
  from space 5120K, 0% used                 ← Survivor 空
  to   space 5120K, 0% used                 ← Survivor 空
ParOldGen       total 175104K, used 104696K ← 老年代使用 102M/171M (59%)
```

### 对比 Serial GC：

```log
def new generation   total 78656K, used 42716K  ← 新生代 42M/77M (55%)
  eden space 69952K,  49% used                  ← Eden 49%
  from space 8704K,   94% used                  ← Survivor 94%！⚠️ 快满了
tenured generation   total 174784K, used 153849K ← 老年代 150M/171M (88%)！⚠️
```

| 指标 | Serial GC | Parallel GC | 分析 |
|------|-----------|-------------|------|
| Eden 使用率 | 49% | 35% | Parallel 更宽裕 |
| Survivor 使用率 | **94%** | **0%** | Serial 危险，Parallel 健康 |
| 老年代使用率 | **88%** | **59%** | Parallel 明显更好 |

> Parallel GC 在最后一次 Full GC(10) 中彻底整理了堆，所以退出时状态很健康。Serial GC 最后只做了 Young GC(11)，老年代积压严重。

---

## 十三、Parallel GC 调优参数速查

| 参数 | 默认值 | 作用 |
|------|--------|------|
| `-XX:ParallelGCThreads=N` | CPU 核数公式 | GC 并行线程数 |
| `-XX:MaxGCPauseMillis=N` | 无限制 | 最大暂停时间目标（ms） |
| `-XX:GCTimeRatio=N` | 99 | 吞吐量目标 = 1/(1+1/N) |
| `-XX:+UseAdaptiveSizePolicy` | 默认开启 | 自适应调节 Eden/Survivor/老年代比例 |
| `-XX:YoungGenerationSizeIncrement=N` | 20(%) | 每次扩容新生代的增长百分比 |

**调优经验法则**：
- 想要**低延迟**：设置 `-XX:MaxGCPauseMillis=100`（JVM 会自动缩小堆来达标）
- 想要**高吞吐**：设置 `-XX:GCTimeRatio=19`（GC 时间占比 ≤ 5%）
- 两者**互相矛盾**：堆小→暂停短但 GC 频繁；堆大→暂停长但 GC 少

---

## 十四、面试考点总结

### Q1：Parallel GC 的 Full GC 为什么会被 "Ergonomics" 触发？

**标准答案**：Parallel GC 启用了自适应大小策略（Adaptive Size Policy），JVM 根据历史 GC 数据预测老年代空间是否能容纳下一次晋升。如果预测将失败，会提前触发 Full GC，这就是 Ergonomics 触发。目的是避免出现 Promotion Failed 导致更严重的问题。

### Q2：为什么 Parallel GC 的 CPU 时间 User > Real？

**标准答案**：因为 Parallel GC 使用多个 GC 线程并行工作（本例 15 个），User 时间是所有 GC 线程 CPU 时间的总和，而 Real 是实际经过的墙钟时间。多线程并行意味着 User 时间被"摊薄"到 Real 中，所以 Real < User。

### Q3：同样 -Xms128m -Xmx256m，为什么 Parallel GC 比 Serial GC 更高效？

**标准答案**：三个层面——
1. **并行收集**：15 个线程并行标记/复制/压缩，STW 时间更短
2. **自适应调节**：动态扩容 Eden（32M→76M），减少 Young GC 频率
3. **Ergonomics 预判**：提前 Full GC 避免极端情况，整体更平稳

---

## 十五、快速诊断检查清单

拿到一份 Parallel GC 日志，按以下顺序检查：

```
□ 1. 看 Parallel Workers 数量是否合理（≈ CPU 核数 × 0.75）
□ 2. 看 Young GC 频率（每秒超过 5 次要警惕）
□ 3. 看 Full GC 频率（每分钟超过 1 次要警惕）
□ 4. 看 Full GC 触发原因（Ergonomics 正常，Allocation Failure 危险）
□ 5. 看 Full GC 回收效果（回收后老年代使用率 > 80% = 危险）
□ 6. 看 CPU 时间比（User/Real 远大于 Workers 数 = 线程竞争）
□ 7. 看堆是否频繁扩缩容（来回波动 = MaxGCPauseMillis 设置过激进）
```

---

> 💡 **一句话记住 Parallel GC**：
> "用多线程暴力并行来换取最大吞吐量的收集器，适合后台批处理任务，不适合对延迟敏感的 Web 服务。"
