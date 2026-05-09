#### `ReentrantReadWriteLock` 适合什么场景？存在什么问题（锁饥饿）？

ReentrantReadWriteLock 读写锁

读锁可以互相共存，写锁不能与任何锁共存的一种加锁机制。

**适用场景**：缓存、配置管理、白名单/黑名单

**存在的问题**：写操作性能低下，只能使用在读多写少的机制下使用。高并发写入性能会大打折扣。



**锁饥饿**：这是一个非常经典的场景：只要读锁一直被一个或多个线程持有，后续到来的所有读线程都能因为“读读共享”的特性而立即“插队”成功，导致试图获取写锁的线程无限期地等待下去



#### **锁降级的过程是怎样的？为什么不支持锁升级？**

锁降级是在 写操作后需要立马使用读取刚才写操作的最新数据而进行的

先进行写锁上锁，然后在写锁内部获取读锁，成功后释放写锁，完成读操作后释放读锁即可。



reentrantreadwritelock不支持锁升级，原因是因为 读多写一，两个线程同时持有读锁，然后同时进行写锁上锁就会形成死锁，reentrantreadwritelock规定写锁必须要所有读锁释放才能获取到，所以线程A在等待线程B的读锁释放，而线程B在等待线程A的读锁释放，死锁形成。



#### `StampedLock` 相比 `ReentrantReadWriteLock` 做了哪些改进？



StampedLock提供乐观锁(新增的)、读锁和写锁

读锁在默认情况下不是不进行加锁的。

```
    public long tryOptimisticRead() {
        long s;
        return (((s = state) & WBIT) == 0L) ? (s & SBITS) : 0L;
    }
    
    
    public long readLock() {
        // unconditionally optimistically try non-overflow case once
        long s = U.getLongOpaque(this, STATE) & RSAFE, nextState;
        if (casState(s, nextState = s + RUNIT))
            return nextState;
        else
            return acquireRead(false, false, 0L);
    }    
```

我的代码中doubleX如果去掉 if (!sl.validate(stamp)) 这个判断，而直接结束，那么可以完全通过乐观读锁来避免写锁饥饿的问题。