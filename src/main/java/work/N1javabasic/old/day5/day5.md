## `ArrayList` 扩容是 1.5 倍，为什么不是 2 倍？

arraylist扩容时为  arraylist.size() < 1 + arraylist 所以是1.5倍



## `ArrayBlockingQueue` 与 `LinkedBlockingQueue` 的锁实现有什么区别？

稍微看了一下

arrayblockingqueue是整个方法全锁，整个类只有一个锁。

LinkedBlockingQueue一共有2把锁，takeLock、putLock，写和读是分开上锁的。



## `PriorityBlockingQueue` 是如何保证有序的？它的扩容逻辑有何特殊之处？

PriorityBlockingQueue 的元素必须要实现 comparable接口，或者在构造时指定Comparator进行排序。

扩容时原本的数组将复制到新的数组上。
