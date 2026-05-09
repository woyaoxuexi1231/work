# 🔪 Day2 面试评分报告 —— 锁的深度解析：Synchronized 与 ReentrantLock

> **评分师**：大厂技术面试官 · 无情打分机器  
> **评分日期**：2026-05-08  
> **被评者**：hulei  
> **综合评价**：锁升级过程有基本概念但表述混乱、细节错误；AQS 只说了一句话；后两题完全空白。**如果这是真实面试，一面直接挂**。

---

## 一、总体评分表（血淋淋地展示）

### 面试题部分（每题满分 20 分，共 4 题）

| 题号 | 原题摘要 | 得分 | 面试官内心反应（扣分核心理由一句话） |
|------|----------|------|--------------------------------------|
| 1 | 锁升级过程、AQS 核心架构、Condition 原理 | 10/20 | 锁升级说了一些但细节错误（Mark Word 位说错），AQS 只说了一句话，Condition 完全没答 |
| 2 | Synchronized 升级到重量级锁为何切换内核态 | 14/20 | 说对了用户态/内核态切换、上下文切换、等待队列，但缺少 Monitor 对象和 ObjectMonitor 的细节 |
| 3 | AQS 为何使用双向链表？Head 节点代表什么？ | 5/20 | **完全空白！交白卷等于不会** |
| 4 | ReentrantLock 的 lockInterruptibly() 如何响应中断 | 5/20 | **完全空白！交白卷等于不会** |

**面试题总分**：**34/80**

---

### 编程题部分（满分 100 分）

**本次无编程题**，跳过。

---

### 最终综合总分

- **面试题得分**：34/80
- **编程题得分**：0/0（无）
- **综合总分（80分制）**：**34/80**
- **一句话定生死**：**锁升级有概念但细节错误，AQS 只说了一句话，后两题直接交白卷。如果这是面试，面试官会直接认为你只背了皮毛，不会深入。建议重学 AQS 源码，补全空白题目。**

---

## 二、逐题血淋淋复盘

### 面试题 1：锁升级过程、AQS 核心架构、Condition 原理

- **你说的**：锁升级通过 Mark Word 实现，无锁→偏向锁→轻量级锁→重量级锁。偏向锁用 CAS 修改线程 ID，轻量级锁用 CAS 指向栈中指针，重量级锁指向互斥量。AQS 通过 state 字段和双向链表实现。
- **得分**：10/20
- **哪里对了**：
  - 知道锁升级的 4 个阶段
  - 知道 Mark Word 的作用
  - 知道偏向锁用 CAS 修改线程 ID
  - 知道重量级锁涉及用户态/内核态切换
- **哪里错了 / 哪里不够**：
  - • **表述极其口语化**："第1个锁升级过程，锁升级过程是在jdk1.8之后引入的" → 面试官心想：锁升级是 JDK 1.6 引入的！基础年代都记错！
  - • **Mark Word 位描述错误**：
    - "最后一个字节的数字它被分为了无锁和偏向锁" → 错！Mark Word 的**最后 3 位**是锁标志位（001=无锁，101=偏向锁，00=轻量级锁，10=重量级锁）
    - "前23个比特" → 错！偏向锁的线程 ID 占 **54 位**（64 位 JVM）
    - "前30个字段" → 错！轻量级锁指向栈中 Lock Record 的指针占 **62 位**
  - • **锁升级触发条件没说**：
    - 偏向锁：默认延迟 4 秒启动（`-XX:BiasedLockingStartupDelay=4000`），避免启动时大量锁竞争
    - 轻量级锁：CAS 自旋失败后升级为重量级锁（自旋次数由 `-XX:PreBlockSpin` 控制）
  - • **AQS 只说了一句话**："AQS是通过一个state字段来标标识它的状态，以及一个双向的链表来作为等待队列实现的阻塞队列的结构" → 面试官心想：就这？CLH 队列、Node 节点结构、独占/共享模式呢？
  - • **Condition 完全没答**：Condition 是基于 AQS 的等待队列，每个 Condition 对象维护一个等待队列，await() 将节点移到等待队列，signal() 将节点移回同步队列
  - • **缺少关键细节**：
    - 锁降级：JVM 不会主动降级，只有 GC 时会批量撤销偏向锁
    - AQS 的 Node 节点结构：waitStatus、thread、prev、next、nextWaiter
    - Condition 的 await/signal 原理：LockSupport.park/unpark
- **你该补什么**：
  - 去查：Mark Word 的完整结构图（64 位 JVM）
  - 去查：AQS 的 Node 节点源码，理解 waitStatus 的 5 种状态
  - 去练：画出 Condition 的 await/signal 流程图
  - 去想：你的项目中如果用错 synchronized 会导致什么性能问题？
- **最后一句真话**：锁升级的年代都记错（1.6 不是 1.8），Mark Word 位描述全错，AQS 和 Condition 几乎没答。下次至少要写完整框架。

---

### 面试题 2：Synchronized 升级到重量级锁为何切换内核态？开销具体在哪？

- **你说的**：重量级锁依赖操作系统的互斥量，需要从用户态切换到内核态。开销体现在：1）用户态/内核态切换需要保存寄存器、线程栈；2）线程上下文切换保存/加载上下文；3）维护内核等待队列。
- **得分**：14/20
- **哪里对了**：
  - 说对了用户态/内核态切换
  - 说对了上下文切换的开销
  - 说对了等待队列的维护
- **哪里错了 / 哪里不够**：
  - • **表述口语化**："Synchronize升级到重量所，重量级所需要切换到内核态" → 面试官心想：能不能说"Synchronized 升级为重量级锁时需要切换到内核态"？
  - • **缺少 Monitor 对象的细节**：
    - 重量级锁依赖 `ObjectMonitor` 对象（C++ 实现）
    - ObjectMonitor 包含：`_owner`（持有锁的线程）、`_WaitSet`（等待队列）、`_EntryList`（阻塞队列）
    - 线程竞争失败后进入 `_EntryList`，调用 `park()` 阻塞
  - • **缺少具体开销数据**：
    - 用户态/内核态切换：约 **1000 纳秒**（1 微秒）
    - 上下文切换：约 **5000-10000 纳秒**（5-10 微秒）
    - 轻量级锁自旋：约 **10 纳秒**
    - 性能差距：**100-1000 倍**
  - • **缺少源码级细节**：
    - JVM 调用 `pthread_mutex_lock()`（Linux）或 `EnterCriticalSection()`（Windows）
    - 这些系统调用会触发陷阱指令（trap），进入内核态
  - • **缺少优化方案**：
    - 自旋锁：`-XX:+UseSpinning`（JDK 6 默认开启）
    - 适应性自旋：根据历史自旋成功率动态调整自旋次数
    - 锁消除：JIT 编译器通过逃逸分析消除不需要的锁
- **你该补什么**：
  - 去查：ObjectMonitor 的 C++ 源码（hotspot/src/share/vm/runtime/objectMonitor.cpp）
  - 去练：画出重量级锁的等待队列结构图（_EntryList、_WaitSet）
  - 去想：你的项目中如果高并发用 synchronized，如何优化？（答案：改用 ReentrantLock 或 ConcurrentHashMap）
- **最后一句真话**：方向对了，但缺少 Monitor 对象和具体开销数据。下次要补充源码级细节。

---

### 面试题 3：AQS 为何使用双向链表（CLH 变体）？Head 节点代表什么？

- **你说的**：（空白）
- **得分**：5/20（给个同情分）
- **哪里对了**：无
- **哪里错了 / 哪里不够**：
  - • **直接交白卷**，面试官心想：这人连 AQS 都没学过
- **你该补什么**：
  - 去查：AQS 的 Node 节点源码，理解 prev/next 的作用
  - 去查：CLH 队列的原始论文，理解为什么 AQS 是 CLH 变体
  - 去练：画出 AQS 的同步队列结构图
- **最后一句真话**：白卷 = 不会 = 直接挂，下次至少要写"AQS 使用双向链表因为需要支持取消节点和从尾部唤醒"。

---

### 面试题 4：ReentrantLock 的 lockInterruptibly() 是如何实现响应中断的？

- **你说的**：（空白）
- **得分**：5/20（给个同情分）
- **哪里对了**：无
- **哪里错了 / 哪里不够**：
  - • **继续交白卷**，面试官心想：这人只会背皮毛，不会深入
- **你该补什么**：
  - 去查：`AbstractQueuedSynchronizer.acquireInterruptibly()` 源码
  - 去查：`Thread.interrupted()` 和 `throw new InterruptedException()` 的处理逻辑
  - 去练：画出 lockInterruptibly() 的调用链
- **最后一句真话**：连续两题白卷，面试官会直接写"不通过"。

---

## 三、编程题：代码审判级点评

**本次无编程题**，跳过。

---

## 四、标准答案（让面试官无可挑剔的版本）

> 以下是你本次所有题目的标准答案。达到这个水平，面试官会直接点头，让你过。

---

### 面试题 1 标准答案：锁升级过程、AQS 核心架构、Condition 原理

**结论**：Synchronized 锁升级是无锁→偏向锁→轻量级锁→重量级锁，通过 Mark Word 的锁标志位控制。AQS 是并发包的核心，使用 state 和 CLH 变体队列实现同步。Condition 是 AQS 的等待队列，支持多条件等待。

**锁升级过程（JDK 1.6 引入）**：

| 锁状态 | Mark Word 锁标志位 | 存储内容 | 特点 |
|--------|-------------------|----------|------|
| 无锁 | 001 | hashCode、分代年龄 | 未加锁 |
| 偏向锁 | 101 | 线程 ID、Epoch、分代年龄 | 单线程访问，无竞争 |
| 轻量级锁 | 00 | 指向 Lock Record 的指针 | 多线程交替执行，无竞争 |
| 重量级锁 | 10 | 指向 Monitor 的指针 | 多线程竞争，阻塞 |

**锁升级流程**：

```
无锁 → 偏向锁 → 轻量级锁 → 重量级锁
  ↓        ↓         ↓
  默认    CAS失败   自旋失败
  延迟4s  膨胀      膨胀
```

**1. 偏向锁**：
- 目的：优化单线程场景，消除无竞争下的同步开销
- 实现：Mark Word 存储线程 ID，后续加锁只需检查 ID 是否匹配
- 撤销条件：
  - 其他线程竞争（CAS 失败）
  - 线程释放锁（默认不撤销，只有 GC 时批量撤销）
  - 调用 `hashCode()`（偏向锁无法存储 hashCode）
- JVM 参数：
  - `-XX:+UseBiasedLocking`（JDK 15 废弃）
  - `-XX:BiasedLockingStartupDelay=4000`（延迟 4 秒启动）

**2. 轻量级锁**：
- 目的：优化多线程交替执行场景，避免用户态/内核态切换
- 实现：
  - 线程在栈帧中创建 Lock Record
  - CAS 将 Mark Word 复制到 Lock Record，并将 Mark Word 指向 Lock Record
  - 自旋等待（默认 10 次，`-XX:PreBlockSpin`）
- 升级条件：自旋失败或竞争激烈

**3. 重量级锁**：
- 目的：处理多线程竞争
- 实现：
  - 创建 ObjectMonitor 对象（C++ 实现）
  - Mark Word 指向 ObjectMonitor
  - 竞争失败的线程进入 `_EntryList` 阻塞
  - 调用 `wait()` 的线程进入 `_WaitSet`
- 开销：用户态/内核态切换（约 1000 纳秒）

**AQS 核心架构**：

```java
public abstract class AbstractQueuedSynchronizer {
    // 同步状态
    private volatile int state;
    
    // 同步队列（CLH 变体）
    private transient volatile Node head;
    private transient volatile Node tail;
    
    // Node 节点结构
    static final class Node {
        volatile int waitStatus;      // 等待状态
        volatile Node prev;           // 前驱节点
        volatile Node next;           // 后继节点
        volatile Thread thread;       // 线程引用
        Node nextWaiter;              // 条件队列下一个节点
        
        // waitStatus 值
        static final int SIGNAL = -1;     // 后继需要唤醒
        static final int CANCELLED = 1;   // 已取消
        static final int CONDITION = -2;  // 在条件队列中
        static final int PROPAGATE = -3;  // 释放需要传播（共享模式）
    }
}
```

**AQS 设计思想**：
1. **state 字段**：
   - 独占锁：state = 0（未加锁）/ 1（已加锁）/ N（重入次数）
   - 共享锁：state = 剩余资源数（如 Semaphore）

2. **CLH 变体队列**：
   - 原始 CLH：单向链表，自旋等待
   - AQS 变体：双向链表，阻塞等待（LockSupport.park）
   - 双向链表的优势：
     - 支持节点取消（从尾部往前找有效节点）
     - 支持条件队列（Node 可同时在同步队列和条件队列）

3. **模板方法模式**：
   - AQS 定义框架：`acquire()`、`release()`
   - 子类实现：`tryAcquire()`、`tryRelease()`、`tryAcquireShared()`

**Condition 原理**：

```java
public class ConditionObject implements Condition {
    private transient Node firstWaiter;  // 条件队列头
    private transient Node lastWaiter;   // 条件队列尾
    
    public final void await() throws InterruptedException {
        // 1. 创建条件节点，加入条件队列
        Node node = addConditionWaiter();
        // 2. 释放锁
        int savedState = fullyRelease(node);
        // 3. 阻塞线程
        while (!isOnSyncQueue(node)) {
            LockSupport.park(this);
            if (Thread.interrupted())
                throw new InterruptedException();
        }
        // 4. 重新获取锁
        acquireQueued(node, savedState);
    }
    
    public final void signal() {
        // 1. 从条件队列移除第一个节点
        Node first = firstWaiter;
        // 2. 转移到同步队列
        doSignal(first);
        // 3. 唤醒线程
        LockSupport.unpark(first.thread);
    }
}
```

**Condition 等待队列 vs 同步队列**：

```
同步队列（sync queue）：
head <-> Node1 <-> Node2 <-> tail

条件队列（condition queue）：
firstWaiter -> Node3 -> Node4 -> lastWaiter

await() 流程：
Node2 从同步队列移除 → 加入条件队列 → park()

signal() 流程：
Node3 从条件队列移除 → 加入同步队列尾部 → unpark()
```

**常见错误**：
- ❌ "锁升级是 JDK 1.8 引入的" → 错，是 JDK 1.6
- ❌ "偏向锁存储 hashCode" → 错，偏向锁无法存储 hashCode
- ❌ "AQS 使用单向链表" → 错，是双向链表
- ❌ "Condition 只能在 ReentrantLock 中使用" → 错，任何实现 Lock 接口的类都可以

**一句话总结**：锁升级优化了无竞争场景的性能，AQS 是并发包的核心框架，Condition 提供了多条件等待能力。

---

### 面试题 2 标准答案：Synchronized 升级到重量级锁为何切换内核态？开销具体在哪？

**结论**：重量级锁依赖操作系统的互斥量（Mutex），需要通过系统调用进入内核态阻塞/唤醒线程，开销包括用户态/内核态切换、上下文切换、等待队列维护。

**重量级锁的底层实现**：

```cpp
// hotspot/src/share/vm/runtime/objectMonitor.hpp
class ObjectMonitor {
  volatile markOop  _header;       // 对象头
  void*             _object;       // 对象引用
  int               _count;        // 重入次数
  int               _recursions;   // 递归次数
  Thread*           _owner;        // 持有锁的线程
  ObjectWaiter*     _WaitSet;      // wait() 等待队列
  ObjectWaiter*     _EntryList;    // 竞争阻塞队列
  ObjectWaiter*     _cxq;          // 竞争队列（LIFO）
};
```

**为何需要切换到内核态**：

1. **线程阻塞/唤醒是操作系统功能**：
   - JVM 无法直接阻塞/唤醒线程，必须调用操作系统 API
   - Linux：`pthread_mutex_lock()` / `pthread_cond_wait()`
   - Windows：`EnterCriticalSection()` / `SleepConditionVariableCS()`
   - 这些系统调用会触发**陷阱指令（trap）**，进入内核态

2. **用户态 vs 内核态**：
   - 用户态：JVM 运行在用户态，权限受限
   - 内核态：操作系统内核运行在内核态，可以操作硬件
   - 切换方式：通过中断或系统调用（int 0x80 / syscall）

**开销具体分析**：

| 开销项 | 耗时 | 说明 |
|--------|------|------|
| 用户态/内核态切换 | ~1000 纳秒 | 保存寄存器、切换栈、执行系统调用 |
| 线程上下文切换 | ~5000-10000 纳秒 | 保存/加载 PC、寄存器、页表 |
| 等待队列维护 | ~500 纳秒 | 插入/删除 ObjectWaiter 节点 |
| 缓存失效 | ~100 纳秒 | 缓存行失效，重新从内存加载 |
| **总开销** | **~7000-12000 纳秒** | **比轻量级锁慢 100-1000 倍** |

**对比各种锁的性能**：

| 锁类型 | 加锁耗时 | 适用场景 |
|--------|----------|----------|
| 偏向锁 | ~10 纳秒 | 单线程访问 |
| 轻量级锁（自旋） | ~10-100 纳秒 | 多线程交替执行 |
| 重量级锁 | ~7000-12000 纳秒 | 多线程竞争 |

**重量级锁的等待队列**：

```
_EntryList（阻塞队列）：竞争失败的线程
  ↓
_cxq（竞争队列，LIFO）：新来的线程先插入这里
  ↓
_WaitSet（等待队列）：调用 wait() 的线程
```

**线程竞争流程**：

1. 线程 A 持有锁，线程 B 竞争
2. 线程 B CAS 失败，进入自旋（轻量级锁）
3. 自旋失败，升级为重量级锁
4. 线程 B 插入 `_cxq` 队列（LIFO）
5. 线程 A 释放锁，唤醒 `_cxq` 或 `_EntryList` 中的线程
6. 线程 B 获取锁，成为 `_owner`

**优化方案**：

1. **自旋锁**（JDK 6 默认开启）：
   - `-XX:+UseSpinning`
   - 自旋次数：`-XX:PreBlockSpin=10`
   - 适应性自旋：根据历史成功率动态调整

2. **锁消除**（JIT 优化）：
   - 逃逸分析证明对象不会被其他线程访问
   - JIT 编译器直接消除 synchronized

3. **锁粗化**（JIT 优化）：
   - 多个连续的 synchronized 合并为一个

4. **改用 ReentrantLock**：
   - 支持公平/非公平锁
   - 支持可中断锁
   - 支持条件变量

**常见错误**：
- ❌ "重量级锁不需要内核态" → 错，必须调用操作系统 API
- ❌ "用户态/内核态切换只需 100 纳秒" → 错，约 1000 纳秒
- ❌ "synchronized 性能很差" → 错，无竞争时偏向锁性能极好

**一句话总结**：重量级锁需要调用操作系统 API 阻塞/唤醒线程，必须切换到内核态，开销约 7000-12000 纳秒，比轻量级锁慢 100-1000 倍。

---

### 面试题 3 标准答案：AQS 为何使用双向链表（CLH 变体）？Head 节点代表什么？

**结论**：AQS 使用双向链表因为需要支持节点取消和从尾部唤醒，Head 节点代表虚拟节点（不关联线程），是同步队列的入口。

**为什么使用双向链表**：

| 特性 | 单向链表 | 双向链表 | AQS 需求 |
|------|----------|----------|----------|
| 前驱节点访问 | ❌ 不支持 | ✅ 支持 | 需要找到前驱节点设置 waitStatus |
| 节点取消 | ❌ 困难 | ✅ 简单 | 需要从尾部往前找有效节点 |
| 尾部插入 | ✅ O(1) | ✅ O(1) | 新节点插入尾部 |
| 头部删除 | ✅ O(1) | ✅ O(1) | Head 节点出队 |

**双向链表的优势**：

1. **支持节点取消**：
   - 线程超时或中断时，节点状态变为 CANCELLED
   - 需要从尾部往前找第一个非 CANCELLED 节点唤醒
   - 双向链表可以通过 `prev` 指针快速回溯

2. **支持条件队列**：
   - Node 节点可能同时在同步队列和条件队列
   - `nextWaiter` 字段用于条件队列
   - 双向链表支持节点在两个队列间转移

3. **支持共享模式**：
   - 共享锁需要传播唤醒（PROPAGATE）
   - 双向链表可以向前传播状态

**原始 CLH 队列 vs AQS 变体**：

| 特性 | 原始 CLH | AQS 变体 |
|------|----------|----------|
| 链表方向 | 单向 | 双向 |
| 等待方式 | 自旋 | 阻塞（LockSupport.park） |
| 节点状态 | 仅 locked/unlocked | 5 种 waitStatus |
| 取消支持 | ❌ | ✅ |
| 适用场景 | 多处理器、低延迟 | 通用场景 |

**Head 节点的含义**：

```java
// AQS 源码
private transient volatile Node head;

// Head 节点的特点：
// 1. 是虚拟节点，不关联线程（thread = null）
// 2. 代表当前持有锁的线程（或空）
// 3. head.next 是第一个等待锁的线程
```

**Head 节点的作用**：

1. **同步队列的入口**：
   - head 指向当前持有锁的线程（或空）
   - head.next 是下一个尝试获取锁的线程

2. **释放锁时的起点**：
   - `release()` 时，从 head 开始唤醒后继节点
   - `unparkSuccessor(head)` 从 head.next 开始找有效节点

3. **虚拟节点的优势**：
   - 简化边界处理（不需要判断 head 是否为 null）
   - 统一入队/出队逻辑

**同步队列结构图**：

```
初始状态：
head -> null
tail -> null

线程 A 获取锁：
head -> Node(A, 持有锁)
tail -> Node(A)

线程 B 竞争失败，入队：
head -> Node(A) <-> Node(B, park) <- tail

线程 C 竞争失败，入队：
head -> Node(A) <-> Node(B) <-> Node(C, park) <- tail

线程 A 释放锁，唤醒 B：
head -> Node(B, 持有锁) <-> Node(C, park) <- tail
        (原 Node(B) 成为新的 head)
```

**Node 节点结构**：

```java
static final class Node {
    // 等待状态
    volatile int waitStatus;
    
    // 双向链表指针
    volatile Node prev;
    volatile Node next;
    
    // 线程引用
    volatile Thread thread;
    
    // 条件队列下一个节点
    Node nextWaiter;
    
    // waitStatus 值
    static final int SIGNAL = -1;     // 后继需要唤醒
    static final int CANCELLED = 1;   // 已取消
    static final int CONDITION = -2;  // 在条件队列中
    static final int PROPAGATE = -3;  // 释放需要传播
}
```

**为什么不用原始 CLH**：

1. **CLH 自旋不适合通用场景**：
   - 自旋浪费 CPU 资源
   - 不适合长时间等待

2. **CLH 不支持取消**：
   - 线程中断时无法从队列中移除

3. **CLH 不支持共享模式**：
   - 原始 CLH 只支持独占锁

**常见错误**：
- ❌ "AQS 使用单向链表" → 错，是双向链表
- ❌ "Head 节点代表第一个等待的线程" → 错，Head 是虚拟节点，head.next 才是
- ❌ "CLH 队列是双向链表" → 错，原始 CLH 是单向链表

**一句话总结**：AQS 使用双向链表支持节点取消和从尾部唤醒，Head 节点是虚拟节点，代表同步队列的入口。

---

### 面试题 4 标准答案：ReentrantLock 的 lockInterruptibly() 是如何实现响应中断的？

**结论**：lockInterruptibly() 在获取锁的过程中检查中断状态，如果线程被中断，立即抛出 InterruptedException 并取消节点。

**lockInterruptibly() 调用链**：

```java
// ReentrantLock.java
public void lockInterruptibly() throws InterruptedException {
    sync.acquireInterruptibly(1);
}

// AbstractQueuedSynchronizer.java
public final void acquireInterruptibly(int arg) throws InterruptedException {
    // 1. 检查中断状态
    if (Thread.interrupted())
        throw new InterruptedException();
    
    // 2. 尝试获取锁
    if (!tryAcquire(arg))
        // 3. 进入可中断的获取流程
        doAcquireInterruptibly(arg);
}

private void doAcquireInterruptibly(int arg) throws InterruptedException {
    // 1. 创建节点，加入队列
    final Node node = addWaiter(Node.EXCLUSIVE);
    boolean failed = true;
    try {
        // 2. 自旋获取锁
        for (;;) {
            final Node p = node.predecessor();
            if (p == head && tryAcquire(arg)) {
                setHead(node);
                p.next = null; // help GC
                failed = false;
                return;
            }
            
            // 3. 检查是否需要阻塞
            if (shouldParkAfterFailedAcquire(p, node) &&
                parkAndCheckInterrupt())
                // 4. 被中断，抛出异常
                throw new InterruptedException();
        }
    } finally {
        if (failed)
            // 5. 取消节点
            cancelAcquire(node);
    }
}
```

**响应中断的关键点**：

1. **入口检查**：
   ```java
   if (Thread.interrupted())
       throw new InterruptedException();
   ```
   - 如果线程在进入前已被中断，立即抛出异常
   - `Thread.interrupted()` 会清除中断状态

2. **阻塞后检查**：
   ```java
   private final boolean parkAndCheckInterrupt() {
       LockSupport.park(this);  // 阻塞
       return Thread.interrupted();  // 唤醒后检查中断
   }
   ```
   - `LockSupport.park()` 阻塞线程
   - 线程被中断时，park() 返回
   - `Thread.interrupted()` 检查中断状态并清除

3. **取消节点**：
   ```java
   private void cancelAcquire(Node node) {
       // 1. 清除 thread 引用
       node.thread = null;
       
       // 2. 跳过已取消的前驱节点
       Node pred = node.prev;
       while (pred.waitStatus > 0)
           node.prev = pred = pred.prev;
       
       // 3. 更新前驱节点的 next 指针
       Node predNext = pred.next;
       node.waitStatus = Node.CANCELLED;
       
       // 4. 如果后继节点需要唤醒，直接 unpark
       if (node == tail && compareAndSetTail(node, pred)) {
           compareAndSetNext(pred, predNext, null);
       } else {
           pred.next = pred.next; // 跳过取消节点
       }
   }
   ```

**lockInterruptibly() vs lock()**：

| 特性 | lock() | lockInterruptibly() |
|------|--------|---------------------|
| 响应中断 | ❌ 不响应 | ✅ 响应 |
| 中断处理 | 继续等待 | 抛出 InterruptedException |
| 适用场景 | 不允许中断的场景 | 需要支持取消的场景 |
| 性能 | 略高（少一次检查） | 略低（每次检查中断） |

**使用场景**：

```java
// 场景：用户取消任务
ReentrantLock lock = new ReentrantLock();

public void executeTask() {
    try {
        lock.lockInterruptibly();
        try {
            // 执行任务
        } finally {
            lock.unlock();
        }
    } catch (InterruptedException e) {
        // 用户取消了任务
        Thread.currentThread().interrupt(); // 恢复中断状态
        log.warn("任务被中断");
    }
}

// 用户取消
thread.interrupt();
```

**中断状态恢复**：

```java
catch (InterruptedException e) {
    // 必须恢复中断状态
    Thread.currentThread().interrupt();
}
```
- 捕获 InterruptedException 后，中断状态被清除
- 必须调用 `Thread.interrupt()` 恢复中断状态
- 否则上层调用者无法感知中断

**常见错误**：
- ❌ "lock() 也能响应中断" → 错，lock() 不响应中断
- ❌ "中断后线程会立即终止" → 错，只是抛出异常，线程继续执行
- ❌ "不需要恢复中断状态" → 错，必须恢复，否则上层无法感知

**一句话总结**：lockInterruptibly() 在获取锁的过程中检查中断状态，如果线程被中断，立即抛出 InterruptedException 并取消节点，适用于需要支持取消的场景。

---

## 五、总结与建议

### 你的问题清单

1. **锁升级年代记错**：说成 JDK 1.8，实际是 JDK 1.6
2. **Mark Word 位描述错误**：最后 3 位是锁标志位，不是"最后一个字节"
3. **AQS 只说了一句话**：缺少 Node 结构、waitStatus、独占/共享模式
4. **Condition 完全没答**：Condition 是 AQS 的等待队列，支持多条件等待
5. **后两题交白卷**：AQS 双向链表原因、lockInterruptibly() 中断响应机制完全空白

### 下一步学习计划

1. **重学锁升级**：画 Mark Word 结构图，标注每个位的含义
2. **重学 AQS 源码**：理解 Node 结构、waitStatus、同步队列/条件队列
3. **学习 Condition**：画出 await/signal 流程图
4. **学习 lockInterruptibly()**：理解中断检查、节点取消、中断状态恢复
5. **看《Java 并发编程实战》第 14 章**：构建自定义同步工具

**最后一句真话**：  
你现在的并发基础**不及格**，锁升级细节错误、AQS 只说了一句话、后两题直接交白卷。把这份报告的标准答案全部消化，重写 Day2 答案，**下次练习要达到 65/80 以上**。加油！

---

> **评分师留言**：  
> 锁和 AQS 是并发编程的核心，不能只背概念，要理解源码和底层原理。把 ObjectMonitor、Node 结构、Condition 流程图搞懂，你的并发水平会提升一个台阶。期待 Day3 的进步！
