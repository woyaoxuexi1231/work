#### 讲讲 AQS 的内部结构。它是如何唤醒后续节点的？

Java AQS（AbstractQueuedSynchronizer）采用了 **CLH 锁的变体**

CLH锁维护了一个**隐式的 FIFO 单向队列**，每个节点都使用prev指向前驱节点，自选前驱节点的locked字段，当locked字段为false的时候，认为前置节点已经释放了锁。



AQS在此基础上进行改造，引入了双向链表 外加一个 state 状态字段。



state ，在不同的结构中表示的意义不同，这里以 reentrantlock为例

1. state  0-未上锁 正数-重入次数

head / tail (Node) ：指向 CLH 队列（一个双向链表）的头尾。

Node 对象由以下字段构成

```
// SIGNAL -1 - 当前节点的后继节点已（或即将）阻塞，当前节点释放锁时必须调用 unparkSuccessor 唤醒它
// CANCELLED 1 - 节点所代表的线程因超时或中断被取消，需要被清理
// 0 - 节点刚创建并入队时的默认值
volatile int waitStatus; 

// 指向当前节点在同步队列中的前一个节点，形成双向链表的基础。
volatile Node prev;

// 指向同步队列中的后一个节点，形成双向链表。
volatile Node next;

// 保存被该节点封装的线程引用。
volatile Thread thread;
```



jdk1.8加锁过程

```
    public final void acquire(int arg) {
        if (!tryAcquire(arg) &&  // 尝试加锁，使用cas自选改变state的值
            acquireQueued(addWaiter(Node.EXCLUSIVE), arg)) // 先把当前线程加入队列
            selfInterrupt();
    }
   
   
    private Node addWaiter(Node mode) {
        Node node = new Node(Thread.currentThread(), mode);
        // Try the fast path of enq; backup to full enq on failure
        Node pred = tail;
        // 相比直接在 enq 内自旋，这种“try once, then loop”的分拆写法让 JIT 更容易内联和优化快速路径，提升整体吞吐量。
        if (pred != null) {
            node.prev = pred;
            if (compareAndSetTail(pred, node)) {
                pred.next = node;
                return node;
            }
        }
        // 如果一次失败了，那么兜底无限循环+cas
        enq(node);
        return node;
    }
   
   
   
   // 在节点已经入队后，以独占、不可中断的模式不断尝试获取锁，必要时阻塞线程，直到成功获取锁为止。
   final boolean acquireQueued(final Node node, int arg) {
        boolean failed = true;
        try {
            boolean interrupted = false;
            for (;;) {
                // 获取前驱节点
                final Node p = node.predecessor();
                // 判断当前节点是否已经是队列的第一个有效等待者（前驱是虚节点 head）
                // tryAcquire 由子类实现的钩子方法，实际去争抢锁（如 CAS 修改 state）。
                if (p == head &&  (arg)) {
                    // 获取成功——变为新的 head
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return interrupted;
                }
                // 获取失败——判断是否需要阻塞
                // shouldParkAfterFailedAcquire(p, node) 检查并更新前驱节点的 waitStatus，决定当前节点是否可以安全阻塞。
                // 初始 0 或 PROPAGATE：将前驱状态 CAS 设置为 SIGNAL，返回 false，外层循环重试至少一次后，下次再调用就会返回 true 并阻塞。
                // 在“设置前驱为 SIGNAL”和“当前线程调用 park”这两个动作之间，前驱（持有锁的线程）可能已经完成了释放锁的全部操作，包括检查并唤醒后继。 如果此时直接阻塞，这个唤醒信号就彻底丢失，线程可能永远挂起。
                // 这里要多花点时间理解，如果立马进行 park 确实存在线程可能永远挂起的可能，而在循环一次的时候，在tryAcquire的操作时，当前线程时可以拿到锁的
                if (shouldParkAfterFailedAcquire(p, node) &&
                    parkAndCheckInterrupt())
                    interrupted = true;
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }
    
    
    // 释放锁，并唤醒后续节点
    public final boolean release(int arg) {
    	// 抽象方法，由子类实现，这里以 java.util.concurrent.locks.ReentrantLock.Sync#tryRelease 为例，把 state 改为0，然后设置exclusiveOwnerThread变量为null
        if (tryRelease(arg)) {
            Node h = head;
            // 如果头节点的 waitStatus 不为0，那么说明有线程等待，则使用unparkSuccessor进行一次唤醒
            if (h != null && h.waitStatus  != 0)
            // 这里就是唤醒后继节点的方法
                unparkSuccessor(h);
            return true;
        }
        return false;
    }
    
    // 找到最前面的一个符合条件的节点，然后进行唤醒
    private void unparkSuccessor(Node node) {
        /*
         * If status is negative (i.e., possibly needing signal) try
         * to clear in anticipation of signalling.  It is OK if this
         * fails or if status is changed by waiting thread.
         */
        int ws = node.waitStatus;
        // 这里如果头节点的后续节点当前的状态是阻塞的，那么就唤醒它
        if (ws < 0)
            compareAndSetWaitStatus(node, ws, 0);

        /*
         * Thread to unpark is held in successor, which is normally
         * just the next node.  But if cancelled or apparently null,
         * traverse backwards from tail to find the actual
         * non-cancelled successor.
         */
         // 如果不是，那么这里从后往前找，一直找到符合条件的那个需要唤醒的节点然后然后唤醒
         // 这是因为新节点尾插时，compareAndSetTail(pred, node) 和 pred.next = node; 属于非原子操作，compareAndSetTail(pred, node) 时中断了，头节点进行唤醒，却发现next为空，不进行从后面往前找就唤醒不了了
        Node s = node.next;
        if (s == null || s.waitStatus > 0) {
            s = null;
            for (Node t = tail; t != null && t != node; t = t.prev)
                if (t.waitStatus <= 0)
                    s = t;
        }
        if (s != null)
            LockSupport.unpark(s.thread);
    }
```







#### `ReentrantLock` 的公平锁与非公平锁实现差异在哪里？

公平锁在尝试加锁的时候会先判断是否有其他节点在头节点的后面等待着，如果有，那么直接判定为加锁失败。

而公平锁进来就直接尝试获取锁了，他不管前面是否还有其他节点在等着。



甚至非公平锁，在lock已经来就尝试获取锁了

```
        final void lock() {
        // 这里直接尝试获取锁
            if (compareAndSetState(0, 1))
                setExclusiveOwnerThread(Thread.currentThread());
            else
                acquire(1);
        }
```





#### 为什么非公平锁的吞吐量更高？



公平锁的加锁的过程中，一旦发现上不了锁，就排队等待。

直到当前加锁的线程进行所释放，而锁释放需要的时间包括， 释放锁 - 系统唤醒阻塞线程 - 被唤醒的线程从队列移入就绪队列 - cpu调度器选中运行 这个过程涉及**内核态切换、上下文保存与恢复、缓存预热**，耗时巨大（数万甚至数十万个 CPU 周期）。



非公平锁可以利用这个空档期，非公平锁在前一个加锁节点释放锁之后，他立马可以感知到，并且上锁执行自己的业务逻辑，完美利用了这个空档期，而被唤醒的线程在被唤醒后 会继续执行这个死循环，去调用tryAcquire尝试获取锁

```
            for (;;) {
                final Node p = node.predecessor();
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return interrupted;
                }
                if (shouldParkAfterFailedAcquire(p, node) &&
                    parkAndCheckInterrupt())
                    interrupted = true;
            }
```





