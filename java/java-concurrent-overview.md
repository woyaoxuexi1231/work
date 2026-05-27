# Java并发 核心面试原理清单

1. **P1: JMM内存可见性与happens-before规则** – 主内存与工作内存分离，volatile、synchronized、final等操作建立happens-before关系，编译器/处理器重排序受约束。

2. **P2: CAS与ABA问题解决** – 比较并交换原子操作（Unsafe类），乐观锁基础；ABA问题通过AtomicStampedReference或AtomicMarkableReference引入版本号/标记位。

3. **P3: synchronized锁升级（偏向→轻量→重量）** – 对象头Mark Word锁标志位变化，偏向锁消除无竞争同步，轻量锁CAS自旋，重量锁阻塞并依赖操作系统的互斥量。

4. **P4: AbstractQueuedSynchronizer（AQS）核心设计** – 基于CLH队列（双向链表）的同步器，state变量控制状态，独占/共享模式，tryAcquire/tryRelease等钩子方法由子类实现。

5. **P5: ReentrantLock公平与非公平策略** – 非公平锁默认插队（减少上下文切换），公平锁按CLH队列顺序获取；通过Sync内部类继承AQS，nonfairTryAcquire尝试CAS抢占。

6. **P6: Condition等待通知机制** – 每个Condition内部维护独立等待队列，调用await释放锁并进入等待，signal将节点从等待队列移到AQS同步队列，与Object的wait/notify相比支持多等待集。

7. **P7: ReentrantReadWriteLock锁降级与饥饿问题** – 读锁共享、写锁互斥；写锁可降级为读锁（先获取写锁再获取读锁再释放写锁），读锁不能升级为写锁；写锁可能被读锁线程阻塞导致写线程饥饿。

8. **P8: StampedLock乐观读锁与版本号** – tryOptimisticRead获取戳，validate验证写锁未占用时无锁读；写锁阻塞读锁（悲观），比ReentrantReadWriteLock并发更高，注意不可重入。

9. **P9: volatile底层内存屏障** – 写操作后StoreStore+StoreLoad屏障，读操作前LoadLoad+LoadStore屏障；禁止指令重排，不保证原子性，常与CAS组合实现无锁。

10. **P10: ThreadLocal内存泄漏根源与InheritableThreadLocal** – Entry的key弱引用ThreadLocal，value强引用；若ThreadLocal无外部强引用，GC后key为null，value泄漏；InheritableThreadLocal让子线程继承父线程值，通过childValue重写。

11. **P11: 线程池ThreadPoolExecutor核心参数交互** – corePoolSize、maximumPoolSize、keepAliveTime、workQueue；线程创建顺序：core线程 → 工作队列 → 非core线程（max满时拒绝）。

12. **P12: 阻塞队列7种实现与应用场景** – ArrayBlockingQueue有界数组、LinkedBlockingQueue可选有界、SynchronousQueue不存储（直接移交）、PriorityBlockingQueue堆结构等；底层使用ReentrantLock配合Condition实现put/take阻塞。

13. **P13: 线程池饱和策略（RejectedExecutionHandler）** – AbortPolicy抛异常、CallerRunsPolicy调用者线程执行、DiscardPolicy静默丢弃、DiscardOldestPolicy丢弃队列头；可自定义。

14. **P14: ForkJoinPool工作窃取（work-stealing）** – 每个线程维护双端任务队列，本地LIFO（push/pop），窃取FIFO（poll），减少竞争；用于RecursiveTask/RecursiveAction，适用分治场景。

15. **P15: CompletableFuture异步编排与任务依赖** – 基于ForkJoinPool.commonPool()，实现回调编排（thenApply/thenCompose），异步执行（supplyAsync），异常处理（exceptionally），组合多个Future（allOf/anyOf）。

16. **P16: CountDownLatch与CyclicBarrier区别** – CountDownLatch一票否决（计数器不可重置），await等待计数归零；CyclicBarrier循环屏障（可重置），所有线程到达屏障点后触发barrierAction，使用ReentrantLock+Condition。

17. **P17: Semaphore信号量与公平性** – 内部Sync继承AQS，state表示剩余许可；acquire减许可（阻塞），release加许可；可控制并发资源数（如数据库连接池）。

18. **P18: Exchanger线程间交换数据** – 核心使用Exchanger.Node作为槽位，竞争严重时采用多槽位（arena）；通过CAS配合LockSupport.park实现配对交换，常用于遗传算法等。

19. **P19: 伪共享（false sharing）与缓存行填充** – CPU缓存行（通常64字节）中多个变量被不同线程修改，导致缓存一致性协议频繁失效；通过@Contended或手动补齐字段解决。

20. **P20: LockSupport与线程阻塞/唤醒原语** – park/unpark基于per-thread的许可证（最多1），无需先获取锁；区别于wait/notify需要synchronized，且不会释放监视器，支持更灵活的线程调度。