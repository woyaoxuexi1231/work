# Java 进阶面试巅峰通关 - Day 09 评分报告

## 一、总体评分表

### 面试题部分（每题满分 20 分，共 60 分）

| 题号 | 原题摘要 | 得分 | 面试官内心反应（扣分核心理由一句话） |
|------|----------|------|--------------------------------------|
| 1 | synchronized 静态方法与实例方法区别 | 12/20 | **回答太浅**：两句话说完了，锁对象都没指明，字节码层面差异一字不提 |
| 2 | 锁升级过程与多级锁原因 | 7/20 | **概念多处混淆**：把 CAS+自旋说成"无锁"、轻量级锁说成"偏向锁"、monitorenter/monitorexit概念错误 |
| 3 | 重量级锁内核态切换原因 | 16/20 | **相对稳健**：核心逻辑正确，但缺少 syscall 陷阱和缓存/TLB 刷新的细节 |
| **面试题总分** | | **35/60 (折算 58/100)** | |

### 编程题部分（满分 100 分）

| 评分维度 | 满分 | 得分 | 面试官真实评价（简短刻薄版） |
|----------|------|------|------------------------------|
| 正确性（核心逻辑） | 40 | 25 | 基本演示了区别，但缺少 join() 无法验证并发行为 |
| 复杂度分析（时间/空间） | 20 | 20 | 无复杂度问题，跳过 |
| 代码风格与可读性 | 20 | 12 | @SneakyThrows 懒人包、残留 Tomcat 无引用 import、javap 注释只展示了一面 |
| 鲁棒性（边界/异常） | 20 | 8 | 没有 join()、异常全靠 @SneakyThrows 吞掉、无任何输出来证明同步效果 |
| **编程题总分** | **100** | **65/100** | |

### 最终综合总分
- **面试题得分**：58/100  
- **编程题得分**：65/100  
- **综合总分（200分制）**：**123/200**  
- **一句话定生死**：**题 2 犯了三个概念级错误，说明对锁升级的理解存在结构性漏洞。比 Day 08 还低了 9 分——不是内容少了，是错的硬伤太重。**

---

## 二、逐题血淋淋复盘

### 面试题 1：synchronized 加在静态方法和实例方法上有什么区别？

- **你说的**：静态方法锁整个类，同一时间仅允许一个线程访问；实例方法锁单个对象，不同对象互不干扰。
- **得分**：12/20  
- **哪里对了**：结论方向正确。静态方法锁的是 `Class` 对象，实例方法锁的是 `this`，这个理解是对的。
- **哪里错了 / 哪里不够**：
  - • **太浅了，只有结论没有原理**：两句话就答完了，面试官心里的反应是："就这？我期待你跟我聊字节码的。"
  - • **没有指明锁对象**：
    - 静态方法 → 锁的是 `Test.class`（Class 对象）
    - 实例方法 → 锁的是 `this`
    - 你只说"作用于整个类"和"作用于单个对象"，但没有说**锁的是什么**。面试官想知道的是——你能说出 `synchronized` 在底层锁的对象到底是什么。
  - • **没有字节码层面分析**：`synchronized` **方法**在字节码层面是通过方法的 `ACC_SYNCHRONIZED` 标志位实现的（`javap -v` 可以看到 flags: ACC_SYNCHRONIZED），而 `synchronized` **代码块**才是通过 `monitorenter`/`monitorexit` 实现的。你代码里的 javap 注释只展示了代码块的字节码，却说自己"看到字节码中加上了 monitorenter 和 monitorexit"——那方法上的呢？你没有对比展示。
  - • **缺少等价写法**：`synchronized static void test()` 等价于 `synchronized (Test.class)`，`synchronized void test2()` 等价于 `synchronized (this)`。说出这个等价关系会让面试官觉得你真的理解锁的机制。
- **你该补什么（知识点地图，不给全答案）**：
  - 去查：javap -v 看 synchronized 方法的 flags 里 ACC_SYNCHRONIZED 是啥意思
  - 去练：写出两种 synchronized 分别在字节码层面的差异（method flag vs monitorenter/monitorexit）
  - 去练：用"锁的对象是谁 → 字节码层面的实现差异 → 等价写法 → 两个实例方法在同一个对象和不同对象上的竞争情况"这个链条组织答案
- **最后一句真话**：12 分是给"结论正确"的保底分。如果想拿 18+，需要展示字节码级的理解。代码里你明明做了 javap，为什么不在答案正文里写出来？

---

### 面试题 2：描述一下锁升级的过程。为什么 1.6 之后引入了这么多级别的锁？

- **你说的**：无锁→偏向锁→轻量级锁→重量级锁的四级升级过程，以及每种状态的大致描述。
- **得分**：7/20  
- **哪里对了**：知道锁升级涉及 Mark Word、知道四种状态名称、知道重量级锁用到了操作系统 mutex。大框架是有的，说明你确实看过这方面的文章。
- **哪里错了 / 哪里不够**：
  - • **【概念错误 1】"无锁"的描述完全就是轻量级锁**：你说"这个时候引入了 CAS 操作……改变成功的线程相当于成功获取了该对象的锁，没有改变成功的对象将进行自选尝试"。这不是"无锁"——这叫**轻量级锁的 CAS 自旋获取**。"无锁"状态就是没有被任何线程锁定的初始状态，**不需要 CAS**。
  - • **【概念错误 2】轻量级锁的描述里写"获取到偏向锁"**：第 51 行 "如果成功，则获取到**偏向锁**"——这是错的。这里获取的是**轻量级锁**。偏向锁的获取是通过检查 Thread ID 是否匹配，不需要 CAS。CAS 是把 Mark Word 替换为 Lock Record 指针，这是轻量级锁的获取方式。
  - • **【概念错误 3】"规避了使用 monitorenter 和 monitorexit"**：第 69 行。**错得离谱。** `monitorenter` 和 `monitorexit` 是 **JVM 字节码指令**，它们编译后就在 .class 文件里——不管你是什么级别的锁，**字节码里都有 monitorenter 和 monitorexit**。优化的不是"使用或不使用这些指令"，而是 **JVM 运行时对这些指令的解释执行方式**：是走 CAS 自旋（轻量级），还是走 mutex（重量级）。
  - • **逻辑跳跃**：第 51 行和第 35 行关于偏向锁的获取流程出现了一模一样的 CAS 描述，但实际上偏向锁获取**不需要 CAS**，只需要检查 Thread ID。CAS 是轻量级锁的获取方式。
  - • **缺少关键细节**：没提 `Epoch` 和批量重偏向/批量撤销，没提锁升级是"单向不可逆"的（有少数情况会降级，但 GC 场景下）。没提及 Mark Word 中不同锁状态下各 bit 位是什么含义。
  - • **表达混乱**：第 33-35 行对偏向锁获取流程的描述出现了大量"然后 那么 如果 那么 但是 如果 那么"的堆砌，读起来非常吃力。面试官听了会皱眉。
- **你该补什么（知识点地图，不给全答案）**：
  - 去查：Mark Word 在 32-bit JVM 中四种锁状态下各 bit 位的精确分配（biased_lock + lock 两个标志位组合的含义）
  - 去查：偏向锁的获取流程——**不涉及 CAS**，只检查 Thread ID 是否匹配，不匹配才触发偏向锁撤销
  - 去查：轻量级锁的 CAS 是把 Mark Word 替换为 Lock Record 的指针（DWR，Displaced Mark Word），不是"改变对象内的某个值"
  - 去查：重量级锁的 ObjectMonitor 结构（cxq、EntryList、WaitSet）
  - 去想：这三个概念错误（无锁=轻量级锁、轻量级=偏向锁、monitorenter被规避）说明你读的文章可能是拼凑式的，建议找一份完整的源码分析重新理解锁升级流程
- **最后一句真话**：7/20，这是你今天最致命的伤口。三个概念级错误意味着面试官会认为你"没看懂源码，只是背了八股文但还背串了"。**建议你把这一题重写**，逐行对着 HotSpot 源码重新梳理。

---

### 面试题 3：为什么重量级锁需要切换到内核态？性能开销在哪里？

- **你说的**：重量级锁依赖 OS mutex；用户态无法让线程休眠和唤醒，需要内核调度；性能开销包括内核态切换、上下文切换、线程调度。
- **得分**：16/20  
- **哪里对了**：核心逻辑完全正确。三个开销点也都命中要害。这是你三道题中答得最好的，说明你对操作系统的用户态/内核态理解比较扎实。
- **哪里错了 / 哪里不够**：
  - • **没提 syscall 陷阱（trap）**：用户态切换到内核态不是凭空发生的，是通过系统调用（如 `futex`）触发陷阱，CPU 从用户态陷入内核态。这个机制本身就有几百个周期的开销。
  - • **没提缓存/TLB 失效**：上下文切换不仅仅是"保存/恢复线程数据"，还涉及 CPU 缓存（L1/L2 cache）和 TLB 的失效和重填。这个开销在实际生产中往往比指令执行时间更大。
  - • **错别字**：第 86 行"线程上文的切换" → "线程上下文的切换"。
- **你该补什么（知识点地图，不给全答案）**：
  - 去查：Linux 下 `futex` 系统调用的实现——Java 重量级锁的 mutex 底层就是走 futex
  - 去查：上下文切换的成本到底是多少（L1 cache miss ~10 cycles vs context switch ~10μs，差三个数量级）
  - 去练：补充"系统调用触发陷阱 → 内核态完成线程状态变更 → 涉及 cache/TLB 刷新"这个完整链条
- **最后一句真话**：16 分，三道题中唯一能拿出手的。如果 Q1 和 Q2 是这个水平，总分至少 150+。

---

## 三、编程题：代码审判级点评

**原题**：编写 Java 程序，演示 synchronized 加在静态方法和实例方法上的区别，并提供字节码层面的分析。

**你的代码**：[Test.java](file:///d:/project/demo/demo-java/work/src/main/java/work/N1javabasic/day9/Test.java)

### 0. 死刑宣判

没有编译错误，但是——**第 4 行 import org.apache.tomcat.util.http.parser.TE 完全没用**，典型的 IDE 自动补全残留。面试官看到这个会在心里默默扣一分："代码都不检查就提交？"

### 1. 正确性——过不了哪些用例

- **缺少 join() 导致并发行为不可验证**：这是连续第二次（Day 08 也是同样问题）。`t1.start(); t2.start(); ... Thread.sleep(10000)` 之后，main 线程在 synchronized 块结束后就直接退出了。你没有用 `t1.join()` / `t2.join()` 来**等待子线程完成**，没有任何输出证明"静态方法锁住了类"或者"实例方法在不同对象上不竞争"。

- **javap 注释只展示了"代码块"的字节码**：你展示了 `synchronized (Test.class) {}` 的 `monitorenter`/`monitorexit`，但题目问的是**方法**的区别——`synchronized static void test()` 和 `synchronized void test2()` 的字节码里不会有 `monitorenter`/`monitorexit`，它们的同步是通过 **ACC_SYNCHRONIZED 标志位**实现的。你完全没有展示这个。

- **javap 输出的验证性不足**：你贴了 `103: monitorenter` 等字节码，但没展示 `test()` 和 `test2()` 方法的完整字节码，没有对比静态和实例方法的 ACC_SYNCHRONIZED 标志有什么不同。

### 2. 复杂度——跳过

### 3. 代码风格与可读性

- **好的**：线程命名 t1/t2/thread3/thread4 虽然不够语义化但基本可读。lambda 表达式简洁。
- **坏的**：
  - **@SneakyThrows 无处不在**：三个方法全部用了 `@SneakyThrows`。这是一个 Lombok 注解，它会**吞掉 checked exception**。面试官看到 @SneakyThrows 的第一反应是："这人不愿意处理异常。" 正确的做法是在方法签名上声明 `throws InterruptedException`。
  - **无用 import 残留**：第 4 行 `import org.apache.tomcat.util.http.parser.TE;`——这个类在这个文件中一次都没有被引用过。面试官看到了会觉得代码质量意识差。
  - **无任何有意义的输出**：System.out.println 只打印了 "test" 和 "test2"，没有线程名字，没有时间戳，无法从输出中看到任何同步效果。你应该打印类似 `Thread.currentThread().getName() + " 进入 test()"`。

### 4. 鲁棒性检查——漏了多少地雷

- [ ] **join() 缺失**：连续两次了。线程启动后不等待，main 提前退出，程序行为不可控。
- [ ] **@SneakyThrows 代替异常处理**：`Thread.sleep()` 的 InterruptedException 被直接吞掉，没有任何中断恢复逻辑。
- [ ] **main 线程的 synchronized 块无意义**：main 线程执行 `synchronized (Test.class) { ... }` 时，t1/t2 可能已经因为 test() 的 sleep(5000) 而持有锁，main 和 t1/t2 之间的锁竞争情况完全不可见。

### 5. 正确的方向（为什么这样改）

```java
public class SynchronizedDemo {

    public static void main(String[] args) throws InterruptedException {
        // 演示静态方法锁——锁 Class 对象
        Thread t1 = new Thread(() -> testStatic(), "T1");
        Thread t2 = new Thread(() -> testStatic(), "T2");

        // 演示实例方法锁——锁 this
        SynchronizedDemo obj1 = new SynchronizedDemo();
        SynchronizedDemo obj2 = new SynchronizedDemo();
        Thread t3 = new Thread(() -> obj1.testInstance(), "T3");
        Thread t4 = new Thread(() -> obj2.testInstance(), "T4"); // 不同对象，不竞争

        t1.start();
        t2.start();
        t3.start();
        t4.start();

        t1.join();
        t2.join();
        t3.join();
        t4.join();

        System.out.println("✅ 所有线程执行完毕");
    }

    public synchronized static void testStatic() {
        System.out.println("✅ [静态方法] " + Thread.currentThread().getName() + " 进入");
        // 输出可以帮助观察 T1/T2 是否串行执行
    }

    public synchronized void testInstance() {
        System.out.println("✅ [实例方法] " + Thread.currentThread().getName() + " 进入");
    }
}
```

**需要补充的字节码验证步骤：**
```
1. javap -v SynchronizedDemo.class 查看 testStatic 方法的 flags
   → 应看到 ACC_SYNCHRONIZED, ACC_STATIC

2. javap -v SynchronizedDemo.class 查看 testInstance 方法的 flags
   → 应看到 ACC_SYNCHRONIZED（没有 ACC_STATIC）

3. 对比 main 方法中的 synchronized(SynchronizedDemo.class) {} 块
   → 可看到 monitorenter / monitorexit 指令
```

**关键点**：`ACC_SYNCHRONIZED` 方法是 JVM 执行方法时隐式获取管道的，不需要显式 `monitorenter`；而 `synchronized` 代码块有显式的 `monitorenter`/`monitorexit`。这才是你 Day 08 学过的东西的延伸——字节码层面的理解。

---

## 四、改进总结 & 与 Day 08 对比

| 维度 | Day 08（volatile） | Day 09（synchronized） | 变化 |
|------|-------------------|----------------------|------|
| 综合总分 | 132/200 | 123/200 | 📉 **退步 9 分** |
| 概念准确度 | 基本准确 | 3 个概念级错误（题 2） | 📉 **严重退步** |
| Q1 深度 | 提到了内存屏障（进步） | 两句话解决战斗（太浅） | 📉 退步 |
| Q3 质量 | 第二问没答 | 16 分，相对扎实 | ✅ 最好的一题 |
| 代码 join() | 缺失 | 再次缺失 | ❌ 拒绝修改 |
| 异常处理 | 抛 RuntimeException | @SneakyThrows 吞异常 | ❌ 更差了 |

**面试官结语**：

Day 09 的锁升级是比 volatile 屏障复杂得多的内容，你在题 2 犯了三个概念级错误，说明你读的资料有偏差或者理解不到位。这是可以理解的——锁升级确实复杂。

但**不可接受**的是：
1. **join() 连续两次不修**——这已经不是能力问题，是执行力和复盘习惯的问题。
2. **题 1 答得太敷衍**——两句话就完了，这是在浪费面试机会。
3. **题 2 的三个概念错误需要你自己去纠正**——我不会给你完整的正确答案，但上面已经标明了错的点在哪。你的任务是：把无锁、偏向锁、轻量级锁三个状态的获取流程**逐字逐句重新梳理**，确保下次不再犯。

Day 10 是 **AQS + ReentrantLock**——这比 synchronized 又上了一个台阶。如果你不把锁升级的漏洞补上，AQS 的 CLH 队列你会听得更懵。

**建议你先把 Day 09 题 2 重写一遍，确认概念不再混淆，再进入 Day 10。**
