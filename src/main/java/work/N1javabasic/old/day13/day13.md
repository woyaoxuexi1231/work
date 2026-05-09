#### `ThreadLocal` 为什么会内存泄漏？为什么 Key 使用弱引用

Threadlocal在使用后如果没有即使remove，会导致一直在线程中运行下去。

这在线程池中非常容易泄露，因为线程池中的线程不会停止，这导致任务执行完了，其实Threadlocal其实还存在。

key设置为弱引用是为了在 theadLocal被设置为 null时，所有引用此 ThreadLocal的线程都可以正常gc此ThreadLocal，如果为强引用，那么 ThreadLocal虽然被设置为null了，但是各线程依旧只有此ThreadLocal的强引用，是不会被回收的。



#### 在线程池环境中使用 `ThreadLocal` 有什么风险？如何解决？

存在内存泄漏的风险。

ThreadLocal在set后没有及时的remove，这导致运行的线程一直持有对某个threadlocal的引用而又无法回收，直接导致了内存泄漏。

解决方案：在set后，一定要在适当的时候进行remove。



#### `CompletableFuture` 如何实现多个异步任务的组合（如 thenCombine）？

combined allOf 都可以完成多个异步任务的组合。