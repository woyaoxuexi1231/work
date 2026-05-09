#### 线程池的执行流程是怎样的？先加队列还是先开线程？

1. 任务通过 execute 方法提交到线程池
2. 如果核心线程数低于指定的核心线程数，那么创建新的worker来执行指定的任务
3. 如果核心线程数等于指定的核心线程数，那么任务将被放入阻塞队列
4. 如果阻塞队列已满放不下了，将继续扩容线程数直到 maximumPoolSize 
5. 如果已经达到最大线程数，将执行对应的拒绝策略

**先填充核心线程，再填充阻塞队列，最后填充非核心线程**



#### 如何根据业务类型（IO 密集型 vs CPU 密集型）配置核心线程数？

上公式：
IO密集型 2N或N/(1-阻塞系数)，阻塞系数为   阻塞时间/阻塞时间+计算时间

CPU密集型 N+1，+1是为了让线程某些原因被挂起时：页缺失、CPU竞争、硬件中断时，备用线程去占用空闲的核心从而提高cpu利用率的手段。



#### 线程池中的线程如果抛出了未捕获异常，会发生什么？

ThreadExecutorPool 提供了 execute 和 sumbit 两个提交任务的方法

两个方法在异常时都不会有任何反应，只是worker被中断，随后新建一个worker而已。

所以一定要进行业务代码的异常处理，否则无法排查业务异常。

在 Worker 的 源码中

```
                    try {
                        task.run();
                    } catch (RuntimeException x) {
                        thrown = x; throw x;
                    } catch (Error x) {
                        thrown = x; throw x;
                    } catch (Throwable x) {
                        thrown = x; throw new Error(x);
                    } finally {
                        afterExecute(task, thrown);
                    }
```

都是直接异常直接抛出去了，没有任何的处理，甚至没有打印。