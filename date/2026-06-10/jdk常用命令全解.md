你列出的这几个命令，很可能是想了解 JDK 中最核心的诊断工具，其中有两个明显的笔误：

- `jheap` 应该是 **jhat**（Java Heap Analysis Tool）
- `jstak` 应该是 **jstack**

下面按照 `jstat`、`jmap`、`jhat`、`jstack`、`jcmd` 的顺序，给出它们的功能、常用选项和实用示例。

---

## 1. jstat —— JVM 统计信息监控
> 全称 **J**ava Virtual Machine **Stat**istics Monitoring Tool

用于实时查看 JVM 的类加载、垃圾回收、JIT 编译等运行数据，非常适合做性能监控。

**常用选项**：

| 参数 | 说明 |
|------|------|
| `-class` | 类加载/卸载数量及耗时 |
| `-compiler` | JIT 编译统计 |
| `-gc` | 各代堆容量及 GC 统计数据 |
| `-gccapacity` | 各代最大最小容量 |
| `-gcutil` | 各代使用百分比 + GC 总时间（最常见） |
| `-gccause` | 同 `-gcutil`，额外显示上次 GC 的原因 |

**命令格式**：  
`jstat [option] <pid> [interval] [count]`

**示例**：  
每隔 2 秒输出一次 GC 百分比，共 5 次
```bash
jstat -gcutil 12345 2000 5
```
输出列通常包括 `S0, S1, E, O, M, CCS, YGC, YGCT, FGC, FGCT, GCT`，代表 Survivor、Eden、老年代、元空间使用率，以及 Young GC 和 Full GC 的次数/耗时。

---

## 2. jmap —— 内存映射与堆转储
> 全称 **J**ava **M**emory **Map**

用来查看堆配置、类直方图、生成堆 dump 文件等。

**常用选项**：

| 参数 | 说明 |
|------|------|
| `-heap` | 打印堆的概要信息（JDK 8 可用，新版本可用 `jhsdb jmap --heap` 或 `jcmd GC.heap_info` 替代） |
| `-histo[:live]` | 输出堆中对象的统计直方图（按占用大小排序），加 `live` 会先触发 Full GC |
| `-dump:format=b,file=<路径>` | 生成 HPROF 格式的堆转储文件（可用于后续分析） |
| `-clstats` | 类加载器统计（JDK 9+） |
| `-finalizerinfo` | 等待执行 finalize 的对象信息 |

**命令格式**：  
`jmap [option] <pid>`

**示例**：  
生成 heap dump 文件（最常用的功能）
```bash
jmap -dump:live,format=b,file=heap.bin 12345
```
> **注意**：JDK 9 以后，推荐尽量用 `jcmd` 或 `jhsdb` 来替代 `jmap` 的部分功能，尤其是 `-heap` 已被标记为过时。

---

## 3. jhat —— 堆转储文件分析工具（已过时）
> 全称 **J**ava **H**eap **A**nalysis **T**ool

读取 `jmap -dump` 生成的文件，启动一个 Web 服务（默认 7000 端口），通过浏览器查看堆中的对象关系。

**基本用法**：
```bash
jhat heap.bin
```
然后在浏览器打开 `http://localhost:7000` 即可看到分析界面，支持 OQL 查询。

**现状**：
- **从 JDK 9 开始已被正式移除**。
- 现在主流替代方案是 **Eclipse Memory Analyzer (MAT)**、**VisualVM**、**JDK 自带的 jhsdb** 或商业的 Profiler。  
  因此，除非你还在用 JDK 6/7/8，否则几乎不会再用到 `jhat`。

---

## 4. jstack —— 线程堆栈跟踪与死锁检测
> 全称 **J**ava **Stack** Trace

打印 JVM 内所有 Java 线程的当前调用栈，是排查 CPU 高、死锁、线程 hang 住的利器。

**常用选项**：

| 参数 | 说明 |
|------|------|
| `-l` | 显示关于锁的附加信息（持有的锁、等待的锁），可用于死锁检测 |
| `-m` | 混合模式，同时打印 Java 和本地 (native) 堆栈帧 |
| `-F` | 当正常请求无响应时，强制输出堆栈（如进程僵死） |

**命令格式**：  
`jstack [options] <pid>`

**示例**：
```bash
jstack -l 12345 > thread_dump.txt
```
输出末尾如果检测到死锁，会直接打印 `Found one Java-level deadlock:` 并给出具体线程。

> 同样地，`jcmd <pid> Thread.print` 可以输出同样格式的线程堆栈，`jstack` 其实在内部调用了该诊断命令。

---

## 5. jcmd —— 全能诊断命令（现代 JDK 首选）
> 全称 **J**ava **C**omman**d**

自 JDK 7 引入，本质上是一个“向本地 JVM 发送诊断命令”的通用工具，可以替代 `jstack`、`jmap`、`jstat` 等的大部分功能。

**查看可用命令**：
```bash
jcmd <pid> help
# 或者查看本机所有 Java 进程
jcmd -l
```

**最常用的内置命令**：

| 命令 | 说明 | 替代的原工具 |
|------|------|-------------|
| `VM.version` | 显示 JVM 版本等信息 | - |
| `VM.system_properties` | 打印所有系统属性 | `jinfo` |
| `VM.flags` | 打印 JVM 参数 | `jinfo` |
| `Thread.print` | 打印线程堆栈（带 `-l` 显示锁） | `jstack` |
| `GC.heap_info` | 堆内存配置与使用概况 | `jmap -heap` |
| `GC.heap_dump` | 生成堆转储文件 | `jmap -dump` |
| `GC.class_histogram` | 类直方图 | `jmap -histo` |
| `VM.uptime` | JVM 启动时长 | - |

**示例**：

获取堆信息
```bash
jcmd 12345 GC.heap_info
```

生成 heap dump
```bash
jcmd 12345 GC.heap_dump /tmp/heap.hprof
```

打印所有线程栈（包含锁）
```bash
jcmd 12345 Thread.print -l
```

查看所有系统属性
```bash
jcmd 12345 VM.system_properties
```

**总结**：在 JDK 9 及更高版本中，**推荐优先使用 `jcmd`**，因为它的功能统一、行为稳定，且不会像 `jmap -heap` 那样在特定版本中被限制。

---

### 速查表总览

| 命令 | 核心用途 | 典型示例 |
|------|----------|----------|
| `jstat` | 实时监控 GC、类加载等 | `jstat -gcutil <pid> 1000` |
| `jmap` | 查看内存、生成 heap dump | `jmap -dump:live,format=b,file=h.bin <pid>` |
| `jhat` | 分析 heap dump（已移除） | （过时，改用 MAT / VisualVM） |
| `jstack` | 打印线程堆栈、检测死锁 | `jstack -l <pid>` |
| `jcmd` | 统一诊断入口，最全能 | `jcmd <pid> GC.heap_dump h.hprof` |

如果你使用的是 JDK 11/17/21 等较新版本，核心思路就是：**日常监控用 `jstat`，需要详细信息或操作时统一用 `jcmd`，线程问题偶尔用 `jstack`，堆 dump 分析交给专门的图形化工具。**