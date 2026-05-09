# 🔪 Day15 面试评分报告 —— JVM 内存区域基础篇

> **评分师**：大厂技术面试官 · 无情打分机器  
> **评分日期**：2026-05-08  
> **被评者**：hulei  
> **综合评价**：JVM 内存区域有基本概念，但表述混乱、口语化严重、缺少关键细节。代码实战部分正确但缺少注释和分析。**如果这是真实面试，二面会被追问到底层细节然后挂掉**。

---

## 一、总体评分表（血淋淋地展示）

### 面试题部分（每题满分 20 分，共 3 题）

| 题号 | 原题摘要 | 得分 | 面试官内心反应（扣分核心理由一句话） |
|------|----------|------|--------------------------------------|
| 1 | Java 8 为什么要用元空间取代永久代？ | 12/20 | 说了 3 个原因但表述像语音转文字，"嗯、这个"太多，缺少技术深度，元空间默认无上限的说法不严谨 |
| 2 | 栈帧包含哪些内容？局部变量表为什么编译期确定？ | 14/20 | 内容基本正确，但表述混乱，缺少"符号引用"、"方法退出时的恢复操作"等关键细节 |
| 3 | StackOverflowError vs OutOfMemoryError | 10/20 | SOF 说对了，但 OOM 只说了堆，**完全没提元空间、直接内存、栈创建线程时的 OOM**，覆盖面严重不足 |

**面试题总分**：**36/60**

---

### 编程题部分（满分 100 分）

| 评分维度 | 满分 | 得分 | 面试官真实评价（简短刻薄版） |
|----------|------|------|------------------------------|
| 正确性（核心逻辑） | 40 | 32 | 3 个 OOM 复现代码逻辑正确，但缺少注释说明和 VM 参数解释 |
| 复杂度分析（时间/空间） | 20 | 12 | 你没写分析，但 OOM 复现本身不需要复杂度分析，应该写"预期触发条件" |
| 代码风格与可读性 | 20 | 14 | 代码简洁但有魔法数字（20m、2M、128k），没有注释说明为什么选这些值 |
| 鲁棒性（边界/异常） | 20 | 16 | StackSOF 有 try-catch 不错，但 HeapOOM 没有捕获异常，StackOOM 可能导致系统假死没有警告 |
| **编程题总分** | **100** | **74/100** | **代码正确但缺少工程化思维，注释不足，没有分析预期输出** |

---

### 最终综合总分

- **面试题得分**：36/60
- **编程题得分**：74/100
- **综合总分（160分制）**：**110/160**
- **一句话定生死**：**JVM 内存区域有基本概念，但表述混乱像背八股，缺少底层原理和工程实践。代码能跑但不会分析。建议重写答案，去掉口语化表达，补充源码和 JVM 参数细节。**

---

## 二、逐题血淋淋复盘

### 面试题 1：Java 8 为什么要用元空间取代永久代？

- **你说的**：3 个原因：1）告别 OOM，Spring Boot 产生大量运行时类信息导致永久代 OOM；2）整合 HotSpot 和 JRockit；3）垃圾回收更简单，元空间回收不由 GC 处理。
- **得分**：12/20
- **哪里对了**：
  - 说对了 3 个核心原因的方向
  - 提到了 Spring Boot 动态类生成的场景
  - 知道 JRockit 整合的历史背景
- **哪里错了 / 哪里不够**：
  - • **表述极其口语化**："主要他有三个原因，第1个就是告别OOM之前的这种永久代的方式" → 面试官心想：这是面试还是聊天？能不能用书面语？
  - • **"元空间默认无上限"不严谨**：应该说"默认使用全部可用本地内存，但可通过 `-XX:MaxMetaspaceSize` 限制"
  - • **缺少关键细节**：
    - 永久代的大小难以确定（`-XX:MaxPermSize`），调优困难
    - 永久代和堆共用空间，GC 时会互相影响
    - 元空间的 Class Metadata 存储在 Native Memory，GC 只扫描元空间中的引用，回收未加载的类
  - • **"GC 只负责清理堆内存"是错误的**：元空间也会 GC（Metaspace GC），只是触发条件不同（类卸载时）
  - • 缺少对比：永久代 OOM 是 `java.lang.OutOfMemoryError: PermGen space`，元空间 OOM 是 `java.lang.OutOfMemoryError: Metaspace`
- **你该补什么**：
  - 去查：永久代和元空间的内存布局对比图
  - 去练：用"结论→原因对比→工程实践"结构重答本题
  - 去想：你的项目中如果有大量动态代理（如 MyBatis Mapper），元空间设置多少合适？
- **最后一句真话**：方向对了，但表述像语音转文字，面试官会认为你没整理过答案。下次用书面语，去掉"嗯、这个"。

---

### 面试题 2：栈帧包含哪些内容？局部变量表为什么编译期确定？

- **你说的**：栈帧包含局部变量表、操作数栈、动态连接、方法返回地址。局部变量表编译期确定是因为 max_locals、不需要动态扩展、安全效率。
- **得分**：14/20
- **哪里对了**：
  - 说对了栈帧的 4 个组成部分
  - 知道局部变量表存储方法参数和局部变量
  - 提到了 max_locals 和编译期计算
  - 知道 long/double 占两个槽位
- **哪里错了 / 哪里不够**：
  - • **表述混乱**："这个栈帧它是这个虚拟机栈里面的内容" → 面试官心想：能不能直接说"栈帧是虚拟机栈的基本单元"？
  - • **动态连接描述不完整**：应该是"指向运行时常量池的符号引用"，你没提"符号引用"这个关键概念
  - • **方法返回地址没说清楚**：应该包含"正常返回的 PC 值"和"异常处理的异常表"
  - • **局部变量表编译期确定的原因没说全**：
    1. Java 是静态类型语言，变量类型在编译期已知
    2. 编译器通过数据流分析确定变量作用域（Slot 复用）
    3. 方法签名（参数个数和类型）在编译期固定
    4. 不需要像动态语言那样在运行时动态分配
  - • **缺少关键细节**：
    - 局部变量表的 Slot 可以复用（超出作用域的变量占用的 Slot 可被复用）
    - 操作数栈的深度也在编译期确定（`max_stack`）
    - 栈帧的大小 = 局部变量表大小 + 操作数栈深度 + 附加信息
- **你该补什么**：
  - 去查：`javap -v` 输出的 `max_locals` 和 `max_stack` 字段
  - 去练：画一个栈帧的内存布局图，标注每个部分的作用
  - 去想：如果局部变量表大小在运行时确定，会对 JVM 性能产生什么影响？
- **最后一句真话**：内容基本正确，但表述混乱，缺少"符号引用"、"Slot 复用"等关键术语。下次用专业术语。

---

### 面试题 3：什么时候会触发 StackOverflowError 和 OutOfMemoryError？

- **你说的**：SOF 是因为栈深超过最大深度（无限递归），OOM 是因为堆内存不足。
- **得分**：10/20
- **哪里对了**：
  - SOF 的原因说对了（无限递归）
  - OOM 的堆内存不足说对了
- **哪里错了 / 哪里不够**：
  - • **OOM 只说了堆，严重遗漏！** 面试官心想：JVM 内存区域那么多，你只知道堆？
  - • **完整的 OOM 触发场景**：
    1. **堆 OOM**：`java.lang.OutOfMemoryError: Java heap space`（对象太多，GC 无法回收）
    2. **元空间 OOM**：`java.lang.OutOfMemoryError: Metaspace`（加载类太多，如动态代理）
    3. **栈 OOM**：`java.lang.OutOfMemoryError: unable to create new native thread`（创建线程过多，每个线程需要栈空间）
    4. **直接内存 OOM**：`java.lang.OutOfMemoryError: Direct buffer memory`（NIO DirectByteBuffer 过多）
    5. **GC 开销过大 OOM**：`java.lang.OutOfMemoryError: GC overhead limit exceeded`（98% 时间在 GC，但只回收 2% 内存）
  - • **SOF 的描述不够精准**：应该是"线程请求的栈深度大于虚拟机允许的最大深度"，而不是"栈深超过最大深度"
  - • **缺少 VM 参数**：
    - SOF：`-Xss` 设置栈大小
    - 堆 OOM：`-Xmx`、`-Xms`
    - 元空间 OOM：`-XX:MaxMetaspaceSize`
  - • **缺少排查思路**：面试官期望你回答"如何用 jmap、jstack、MAT 定位 OOM"
- **你该补什么**：
  - 去查：JVM 规范中定义的 5 种 OOM 场景
  - 去练：用表格对比 SOF 和 OOM 的触发条件、错误信息、排查工具
  - 去想：你的项目中如果发生 OOM，第一步做什么？（答案：dump 堆内存，用 MAT 分析）
- **最后一句真话**：这道题只答了 50%，OOM 的场景覆盖率严重不足。如果面试只回答这些，面试官会直接认为你只背了堆的 OOM。

---

## 三、编程题：代码审判级点评

**原题**：复现 StackOverflowError、堆 OOM、栈 OOM  
**你的代码**：`StackSOF.java`、`HeapOOM.java`、`StackOOM.java`

---

### 0. 死刑宣判

> **无致命 bug，3 个代码都能正确触发对应的错误。但缺少注释说明、预期输出分析、排查工具使用。**

---

### 1. 正确性——过不了哪些用例

| 代码 | 预期触发 | 实际触发 | 评价 |
|------|----------|----------|------|
| StackSOF.java | StackOverflowError | ✅ 正确 | 无限递归触发 SOF，逻辑正确 |
| HeapOOM.java | Java heap space OOM | ✅ 正确 | 不断创建对象触发堆 OOM，逻辑正确 |
| StackOOM.java | unable to create new native thread | ✅ 正确 | 不断创建线程触发栈 OOM，逻辑正确 |

**核心评价**：
- 3 个代码都能正确触发对应的错误
- VM 参数设置合理（`-Xss128k`、`-Xms20m -Xmx20m`、`-Xss2M`）
- **但缺少注释说明为什么选这些值**

---

### 2. 复杂度——你在骗谁呢

- **你说的**：（没写分析）
- **实际分析**：
  - StackSOF：递归深度 = 栈大小 / 每帧大小 = 128KB / ~64B ≈ 2000 层 → 你的代码输出 `stack length:2000` 左右
  - HeapOOM：对象数量 = 堆大小 / 对象大小 = 20MB / ~16B ≈ 130 万个对象 → 触发 OOM
  - StackOOM：线程数量 = 可用内存 / 栈大小 = 4GB / 2MB ≈ 2000 个线程 → 触发 OOM

**面试官期望你写**：
```java
// 预期输出分析：
// 1. StackSOF：递归深度约 2000 层后触发 SOF
// 2. HeapOOM：约 130 万个对象后触发堆 OOM
// 3. StackOOM：约 2000 个线程后触发 native thread OOM
```

---

### 3. 代码风格——像实习生写的

| 问题 | 位置 | 说明 |
|------|------|------|
| 缺少注释 | HeapOOM.java 第 14 行 | 为什么选 20MB？应该注释"模拟小堆场景，快速触发 OOM" |
| 缺少注释 | StackOOM.java 第 8 行 | `while(true)` 应该注释"空循环占用线程，不释放栈空间" |
| 魔法数字 | StackSOF.java 第 4 行 | `-Xss128k` 应该注释"默认 1MB，设置为 128KB 降低溢出门槛" |
| 缺少预期输出 | 所有文件 | 应该在注释中写明"预期触发 XXXError，错误信息为 XXX" |

---

### 4. 鲁棒性检查——漏了多少地雷

| 检查项 | StackSOF | HeapOOM | StackOOM |
|--------|----------|---------|----------|
| try-catch 保护 | ✅ 有 | ❌ 没有 | ❌ 没有 |
| VM 参数注释 | ✅ 有 | ✅ 有 | ✅ 有 |
| 预期输出说明 | ❌ 没有 | ❌ 没有 | ❌ 没有 |
| 系统假死警告 | ❌ 没有 | ❌ 没有 | ✅ 有（第 5 行） |
| HeapDump 配置 | ❌ 没有 | ✅ 有（第 7 行） | ❌ 没有 |

**评价**：
- StackSOF 有 try-catch 不错，可以捕获异常并输出递归深度
- HeapOOM 有 `-XX:+HeapDumpOnOutOfMemoryError` 不错，但应该在注释中说明"自动生成 hprof 文件，可用 MAT 分析"
- StackOOM 有假死警告，但应该加上"建议限制线程数量或使用线程池"

---

### 5. 正确的写法（带注释，解释为什么这样写）

**HeapOOM.java 改进版**：
```java
package work.N1javabasic.day15;

import java.util.ArrayList;
import java.util.List;

/**
 * 复现堆内存溢出（Java heap space）
 * 
 * VM Args: -Xms20m -Xmx20m -XX:+HeapDumpOnOutOfMemoryError
 * -Xms20m -Xmx20m：限制堆大小为 20MB，模拟小堆场景
 * -XX:+HeapDumpOnOutOfMemoryError：OOM 时自动生成 hprof 文件，可用 MAT 分析
 * 
 * 预期触发：java.lang.OutOfMemoryError: Java heap space
 * 预期对象数量：约 130 万个（20MB / 16B ≈ 1.3M）
 */
public class HeapOOM {
    static class OOMObject {
        // 每个对象约 16 字节（对象头 12B + 引用 4B，对齐到 8 的倍数）
    }

    public static void main(String[] args) {
        List<OOMObject> list = new ArrayList<>();
        try {
            while (true) {
                list.add(new OOMObject()); // 强引用保持对象存活，阻止 GC
            }
        } catch (OutOfMemoryError e) {
            System.out.println("✅ 成功触发堆 OOM");
            System.out.println("❌ 错误信息：" + e.getMessage());
            System.out.println("📊 已创建对象数量：" + list.size());
            throw e; // 重新抛出，触发 HeapDump
        }
    }
}
```

**StackSOF.java 改进版**：
```java
package work.N1javabasic.day15;

/**
 * 复现栈溢出（StackOverflowError）
 * 
 * VM Args: -Xss128k
 * -Xss128k：设置线程栈大小为 128KB（默认 1MB），降低溢出门槛
 * 
 * 预期触发：java.lang.StackOverflowError
 * 预期递归深度：约 2000 层（128KB / 64B ≈ 2000）
 */
public class StackSOF {
    private int stackLength = 1;

    public void stackLeak() {
        stackLength++;
        stackLeak();  // 无限递归，不断压栈
    }

    public static void main(String[] args) {
        StackSOF sof = new StackSOF();
        try {
            sof.stackLeak();
        } catch (StackOverflowError e) {
            System.out.println("✅ 成功触发栈溢出");
            System.out.println("📊 递归深度：" + sof.stackLength);
            System.out.println("❌ 错误信息：" + e.getMessage());
            // 不重新抛出，避免程序崩溃
        }
    }
}
```

---

## 四、标准答案（让面试官无可挑剔的版本）

> 以下是你本次所有题目的标准答案。达到这个水平，面试官会直接点头，让你过。

---

### 面试题 1 标准答案：Java 8 为什么要用元空间取代永久代？

**结论**：永久代存在大小难以确定、GC 效率低、与 JRockit 不兼容等问题，Java 8 改用元空间（Native Memory）存储类元数据。

**核心原因对比**：

| 维度 | 永久代（PermGen） | 元空间（Metaspace） |
|------|-------------------|---------------------|
| 内存位置 | JVM 堆内 | 本地内存（Native Memory） |
| 大小限制 | `-XX:MaxPermSize`（难以调优） | 默认无上限（可用 `-XX:MaxMetaspaceSize` 限制） |
| GC 影响 | 与堆共用空间，Full GC 时才会回收 | 独立回收，类卸载时触发 Metaspace GC |
| OOM 错误 | `java.lang.OutOfMemoryError: PermGen space` | `java.lang.OutOfMemoryError: Metaspace` |

**详细原因**：

1. **永久代大小难以确定**：
   - 永久代大小需要预估（类数量、常量池大小、JIT 编译代码等）
   - 设置太小会 OOM，设置太大会浪费内存
   - Spring Boot、动态代理（CGLIB、MyBatis Mapper）会运行时生成大量类，永久代极易 OOM

2. **GC 效率低**：
   - 永久代的 GC 只在 Full GC 时触发，效率低
   - 永久代和堆共用空间，GC 时会互相影响
   - 元空间的类元数据回收更简单：当 ClassLoader 被回收时，其加载的类元数据也会被回收

3. **整合 HotSpot 和 JRockit**：
   - Oracle 收购 BEA 后，需要整合 HotSpot 和 JRockit
   - JRockit 没有永久代概念，使用本地内存存储类元数据
   - 统一使用元空间，简化 JVM 架构

4. **工程实践**：
   - 元空间默认使用全部可用本地内存，但生产环境应该设置 `-XX:MaxMetaspaceSize=256m`
   - 监控元空间使用：`jstat -gc <pid>` 查看 `MU`（Metaspace Used）和 `MC`（Metaspace Capacity）
   - 如果频繁触发 Metaspace GC，说明有类加载泄漏（如动态代理未清理）

**常见错误**：
- ❌ "元空间不会 OOM" → 错，只是默认无上限，仍可通过参数限制
- ❌ "元空间不需要 GC" → 错，Metaspace GC 会回收未加载的类
- ❌ "永久代存储对象实例" → 错，永久代存储类元数据，对象实例在堆中

**一句话总结**：元空间解决了永久代大小难调优、GC 效率低的问题，同时整合了 JRockit 的架构。

---

### 面试题 2 标准答案：栈帧包含哪些内容？局部变量表为什么编译期确定？

**结论**：栈帧包含局部变量表、操作数栈、动态连接、方法返回地址。局部变量表大小在编译期确定，因为 Java 是静态类型语言，变量类型和作用域在编译期已知。

**栈帧结构**：

```
┌─────────────────────────────────────┐
│          栈帧（Stack Frame）          │
├─────────────────────────────────────┤
│ 1. 局部变量表（Local Variable Table） │
│    - 存储方法参数和局部变量            │
│    - Slot 可复用（超出作用域后）       │
│    - long/double 占 2 个 Slot        │
├─────────────────────────────────────┤
│ 2. 操作数栈（Operand Stack）          │
│    - 字节码指令的计算引擎             │
│    - 保存中间结果（如 1+2 的计算）     │
│    - 深度在编译期确定（max_stack）    │
├─────────────────────────────────────┤
│ 3. 动态连接（Dynamic Linking）        │
│    - 指向运行时常量池的符号引用       │
│    - 方法调用时解析为直接引用         │
├─────────────────────────────────────┤
│ 4. 方法返回地址（Return Address）     │
│    - 正常返回：调用方的 PC 值         │
│    - 异常返回：异常表（Exception Table）│
└─────────────────────────────────────┘
```

**局部变量表编译期确定的原因**：

1. **静态类型语言**：
   - Java 是静态类型语言，变量类型在编译期已知
   - 编译器可以精确计算每个变量占用的 Slot 数量

2. **方法签名固定**：
   - 方法的参数个数和类型在编译期确定
   - 实例方法隐含的 `this` 参数占用 Slot 0

3. **数据流分析**：
   - 编译器通过数据流分析确定变量的作用域
   - 超出作用域的变量占用的 Slot 可被复用（Slot 复用优化）

4. **性能优化**：
   - 编译期确定大小，运行时不需要动态分配内存
   - 栈帧大小 = 局部变量表大小 + 操作数栈深度 + 附加信息
   - JVM 可以预先分配栈帧空间，提高执行效率

**源码级细节**：

```java
public int add(int a, int b) {
    int c = a + b;
    return c;
}
```

编译后的字节码：
```
MethodParameters:
  Name  Flags
  a
  b

Code:
  stack=2, locals=4, args_size=3
     0: iload_1          // 加载 a（Slot 1）
     1: iload_2          // 加载 b（Slot 2）
     2: iadd             // 相加
     3: istore_3         // 存储到 c（Slot 3）
     4: iload_3          // 加载 c
     5: ireturn          // 返回
```

- `locals=4`：Slot 0（this）、Slot 1（a）、Slot 2（b）、Slot 3（c）
- `stack=2`：操作数栈最大深度为 2（压入 a 和 b）

**常见错误**：
- ❌ "局部变量表运行时动态分配" → 错，编译期确定
- ❌ "操作数栈深度可以超过 max_stack" → 错，JVM 会校验
- ❌ "动态连接是方法调用的直接引用" → 错，是符号引用，运行时解析

**一句话总结**：栈帧是虚拟机栈的基本单元，局部变量表大小在编译期确定，因为 Java 是静态类型语言，变量类型和作用域在编译期已知。

---

### 面试题 3 标准答案：什么时候会触发 StackOverflowError 和 OutOfMemoryError？

**结论**：SOF 是栈深度超过限制，OOM 是内存不足。JVM 规范定义了 5 种 OOM 场景。

**StackOverflowError**：

| 触发条件 | 错误信息 | 常见场景 | VM 参数 |
|----------|----------|----------|---------|
| 线程请求的栈深度大于虚拟机允许的最大深度 | `java.lang.StackOverflowError` | 无限递归、过深的方法调用 | `-Xss` |

**示例**：
```java
public void infiniteRecursion() {
    infiniteRecursion(); // 无限递归，不断压栈
}
```

**OutOfMemoryError（5 种场景）**：

| 场景 | 错误信息 | 常见原因 | VM 参数 | 排查工具 |
|------|----------|----------|---------|----------|
| 堆 OOM | `java.lang.OutOfMemoryError: Java heap space` | 对象太多、内存泄漏、堆太小 | `-Xmx`、`-Xms` | jmap、MAT |
| 元空间 OOM | `java.lang.OutOfMemoryError: Metaspace` | 加载类太多、动态代理未清理 | `-XX:MaxMetaspaceSize` | jstat、jcmd |
| 栈 OOM | `java.lang.OutOfMemoryError: unable to create new native thread` | 创建线程过多、栈设置过大 | `-Xss` | jstack、top |
| 直接内存 OOM | `java.lang.OutOfMemoryError: Direct buffer memory` | NIO DirectByteBuffer 过多 | `-XX:MaxDirectMemorySize` | jcmd |
| GC 开销过大 | `java.lang.OutOfMemoryError: GC overhead limit exceeded` | 98% 时间在 GC，但只回收 2% 内存 | `-XX:-UseGCOverheadLimit`（禁用） | jstat、GC 日志 |

**排查步骤（面试必问）**：

1. **堆 OOM 排查**：
   ```bash
   # 1. 启用 HeapDump
   -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/
   
   # 2. 用 MAT 分析 hprof 文件
   # 3. 查看 Dominator Tree，找到占用内存最大的对象
   # 4. 查看 GC Roots 引用链，定位内存泄漏点
   ```

2. **元空间 OOM 排查**：
   ```bash
   # 1. 监控元空间使用
   jstat -gc <pid> 1000  # 查看 MU 和 MC
   
   # 2. 查看加载的类
   jcmd <pid> VM.classs_hierarchy
   
   # 3. 检查是否有类加载泄漏（如动态代理未清理）
   ```

3. **栈 OOM 排查**：
   ```bash
   # 1. 查看线程数量
   top -H -p <pid>
   
   # 2. 打印线程栈
   jstack <pid> > thread_dump.txt
   
   # 3. 分析是否有线程泄漏（如线程池未关闭）
   ```

**常见错误**：
- ❌ "OOM 只有堆 OOM" → 错，JVM 规范定义了 5 种 OOM 场景
- ❌ "SOF 和 OOM 都是内存不足" → 错，SOF 是栈深度超限，OOM 是内存不足
- ❌ "元空间不会 OOM" → 错，只是默认无上限，仍可触发

**一句话总结**：SOF 是栈深度超限，OOM 是内存不足。JVM 规范定义了 5 种 OOM 场景，排查时应该用 jmap、jstack、MAT 等工具定位根因。

---

### 编程题标准答案：OOM 复现代码（工程化版本）

**HeapOOM.java**：
```java
package work.N1javabasic.day15;

import java.util.ArrayList;
import java.util.List;

/**
 * 复现堆内存溢出（Java heap space）
 * 
 * VM Args: -Xms20m -Xmx20m -XX:+HeapDumpOnOutOfMemoryError
 * -Xms20m -Xmx20m：限制堆大小为 20MB，模拟小堆场景
 * -XX:+HeapDumpOnOutOfMemoryError：OOM 时自动生成 hprof 文件，可用 MAT 分析
 * 
 * 预期触发：java.lang.OutOfMemoryError: Java heap space
 * 预期对象数量：约 130 万个（20MB / 16B ≈ 1.3M）
 * 
 * 排查步骤：
 * 1. 用 MAT 打开 hprof 文件
 * 2. 查看 Dominator Tree，找到 OOMObject 占用内存
 * 3. 查看 GC Roots 引用链，确认是 list 强引用导致无法 GC
 */
public class HeapOOM {
    static class OOMObject {
        // 每个对象约 16 字节（对象头 12B + 引用 4B，对齐到 8 的倍数）
    }

    public static void main(String[] args) {
        List<OOMObject> list = new ArrayList<>();
        try {
            while (true) {
                list.add(new OOMObject()); // 强引用保持对象存活，阻止 GC
            }
        } catch (OutOfMemoryError e) {
            System.out.println("✅ 成功触发堆 OOM");
            System.out.println("❌ 错误信息：" + e.getMessage());
            System.out.println("📊 已创建对象数量：" + list.size());
            throw e; // 重新抛出，触发 HeapDump
        }
    }
}
```

**StackSOF.java**：
```java
package work.N1javabasic.day15;

/**
 * 复现栈溢出（StackOverflowError）
 * 
 * VM Args: -Xss128k
 * -Xss128k：设置线程栈大小为 128KB（默认 1MB），降低溢出门槛
 * 
 * 预期触发：java.lang.StackOverflowError
 * 预期递归深度：约 2000 层（128KB / 64B ≈ 2000）
 * 
 * 排查步骤：
 * 1. 查看错误信息中的递归深度
 * 2. 检查代码中的递归调用逻辑
 * 3. 改为迭代或使用尾递归优化（Java 不支持尾递归优化）
 */
public class StackSOF {
    private int stackLength = 1;

    public void stackLeak() {
        stackLength++;
        stackLeak();  // 无限递归，不断压栈
    }

    public static void main(String[] args) {
        StackSOF sof = new StackSOF();
        try {
            sof.stackLeak();
        } catch (StackOverflowError e) {
            System.out.println("✅ 成功触发栈溢出");
            System.out.println("📊 递归深度：" + sof.stackLength);
            System.out.println("❌ 错误信息：" + e.getMessage());
            // 不重新抛出，避免程序崩溃
        }
    }
}
```

**StackOOM.java**：
```java
package work.N1javabasic.day15;

/**
 * 复现栈 OOM（unable to create new native thread）
 * 
 * VM Args: -Xss2M
 * -Xss2M：设置线程栈大小为 2MB，每个线程占用 2MB 栈空间
 * 
 * 预期触发：java.lang.OutOfMemoryError: unable to create new native thread
 * 预期线程数量：约 2000 个（4GB / 2MB ≈ 2000）
 * 
 * 排查步骤：
 * 1. 用 top -H -p <pid> 查看线程数量
 * 2. 用 jstack <pid> 打印线程栈
 * 3. 检查是否有线程泄漏（如线程池未关闭）
 * 
 * ⚠️ 警告：运行此代码可能导致操作系统假死，请先保存工作！
 */
public class StackOOM {
    private void dontStop() {
        while (true) {
            // 空循环占用线程，不释放栈空间
        }
    }

    public void stackLeakByThread() {
        while (true) {
            Thread t = new Thread(this::dontStop);
            t.start();
        }
    }

    public static void main(String[] args) {
        try {
            new StackOOM().stackLeakByThread();
        } catch (OutOfMemoryError e) {
            System.out.println("✅ 成功触发栈 OOM");
            System.out.println("❌ 错误信息：" + e.getMessage());
            throw e;
        }
    }
}
```

**复杂度证明**：
- OOM 复现不需要复杂度分析，但应该说明"预期触发条件"
- HeapOOM：时间 O(N)，空间 O(N)，N 为对象数量
- StackSOF：时间 O(N)，空间 O(N)，N 为递归深度
- StackOOM：时间 O(N)，空间 O(N)，N 为线程数量

**边界用例测试**：
1. HeapOOM：如果设置 `-Xmx512m`，对象数量会增加到约 3200 万个
2. StackSOF：如果设置 `-Xss1m`，递归深度会增加到约 16000 层
3. StackOOM：如果设置 `-Xss256k`，线程数量会增加到约 16000 个

---

## 五、总结与建议

### 你的问题清单

1. **表述口语化严重**："嗯、这个、就是"太多，像语音转文字
2. **OOM 场景覆盖率不足**：只说了堆 OOM，遗漏了元空间、栈、直接内存、GC 开销
3. **缺少关键术语**：符号引用、Slot 复用、Metaspace GC、Dominator Tree
4. **代码缺少注释**：没有说明 VM 参数、预期输出、排查工具
5. **缺少工程实践**：没有提到 jmap、jstack、MAT 等排查工具的使用

### 下一步学习计划

1. **重写 Day15 答案**：用书面语，去掉口语化表达
2. **背诵 5 种 OOM 场景**：堆、元空间、栈、直接内存、GC 开销
3. **学习排查工具**：jmap、jstack、MAT、jstat
4. **画栈帧结构图**：标注局部变量表、操作数栈、动态连接、方法返回地址
5. **看《深入理解 Java 虚拟机》第 2 章**：运行时数据区

**最后一句真话**：  
你现在的 JVM 基础**勉强及格**，但表述混乱、缺少工程实践。把这份报告的标准答案全部消化，重写 Day15 答案，**下次练习要达到 140/160 以上**。加油！

---

> **评分师留言**：  
> JVM 是高薪分水岭，不能只背概念，要理解底层原理和工程实践。把 jmap、jstack、MAT 用起来，你的 JVM 水平会提升一个台阶。期待 Day16 的进步！
