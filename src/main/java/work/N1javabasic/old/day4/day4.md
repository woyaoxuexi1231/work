## `ConcurrentHashMap` 1.8 为什么放弃分段锁？它的 `size()` 方法是如何实现的？

我知道的是 1.8 concurrentHashmap 使用的是 syncornized 进行加锁，且它仅在更新操作时加锁。

我瞄了一眼源码，应该是通过一个 CounterCell数组来实现的，不同线程操作不同的位置，通过cap来实现无锁计数，然后size直接每个位置相加得到总数。



## `ConcurrentHashMap` 的扩容过程（多线程协同扩容）是怎样的？

这个确实不太清楚



## `CopyOnWriteArrayList` 的缺点是什么？适用于什么场景？

并发写入高的时候内存占用大？？

应该是适用于读取场景高的情况吧