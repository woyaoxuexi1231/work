# Java集合 核心面试原理清单

1. **P1: ArrayList动态扩容与内存拷贝** – 默认初始容量10，每次add超过阈值时按1.5倍扩容（右移一位），通过Arrays.copyOf迁移旧数组，频繁扩容影响性能。

2. **P2: LinkedList双链表节点结构** – Node节点含prev、next、item，首尾指针维护；删除/插入O(1)但查找O(n)，不支持随机访问，内存开销高于ArrayList。

3. **P3: HashMap扰动函数与寻址** – 哈希值高16位与低16位异或（扰动），再与(n-1)按位与计算下标，减少冲突；容量为2的幂保证位运算取模效率。

4. **P4: HashMap链表转红黑树阈值** – 链表长度≥8且数组长度≥64时树化为红黑树；长度≤6时退化为链表，避免TreeNode内存浪费与检索开销。

5. **P5: HashMap扩容与rehash优化** – 扩容为2倍时，节点索引要么在原位，要么在原位+旧容量；通过(e.hash & oldCap)==0判断，避免重新哈希。

6. **P6: ConcurrentHashMap JDK8锁粒度优化** – 采用CAS初始化数组和插入头节点，synchronized锁定桶头节点；粒度从Segment锁缩小到单桶，并发度提升。

7. **P7: ConcurrentHashMap size()与CounterCell** – 使用baseCount + CounterCell数组，每个线程更新自己的CounterCell，累加时降低竞争；替代JDK7的Segment统计。

8. **P8: HashSet底层HashMap实现** – HashSet内部使用HashMap存储元素作为key，value为固定Object PRESENT；无序、无重复依赖HashMap的key唯一性。

9. **P9: TreeMap红黑树与NavigableMap** – 基于红黑树存储键值对，左<根<右；支持自然排序或Comparator，查找/插入O(log n)，提供ceiling/floor等导航方法。

10. **P10: LinkedHashMap双向链表与访问顺序** – 在HashMap基础上串联双向链表，按插入顺序或访问顺序迭代；accessOrder参数可用于实现LRU缓存（重写removeEldestEntry）。

11. **P11: PriorityQueue二叉堆结构** – 基于数组存储小顶堆（默认），通过siftUp/siftDown上下调整；非线程安全，时间复杂度O(log n)，不支持null元素。

12. **P12: ArrayDeque双端队列循环数组** – 数组头尾指针循环（head/tail），避免数据搬移；容量为2的幂，支持栈与队列操作，性能优于Stack和LinkedList。

13. **P13: CopyOnWriteArrayList写时复制原理** – 读操作无锁（直接返回快照），写操作加锁后复制新数组（Arrays.copyOf），写完替换volatile引用；适用于读多写少场景。

14. **P14: Vector与Stack遗留类缺陷** – Vector内部方法全synchronized（锁粗粒度），性能差；Stack继承Vector，栈操作存在线程安全问题且不推荐使用，用Deque替代。

15. **P15: Collections.synchronizedXXX包装** – 基于synchronized代码块，包装非线程安全集合；迭代器需外部手动同步（如synchronized(list)），否则并发修改异常。

16. **P16: 快速失败（fail-fast）与安全失败（fail-safe）** – 非并发集合迭代时检查modCount，检测结构性修改抛出ConcurrentModificationException；CopyOnWriteArrayList使用快照实现安全失败。

17. **P17: IdentityHashMap与HashMap区别** – IdentityHashMap使用==而非equals比较key，且不调用hashCode()而使用System.identityHashCode，用于特殊引用语义场景。

18. **P18: WeakHashMap弱引用回收机制** – Entry的key为弱引用，GC时自动回收，并在下次操作时清除过期Entry；适合缓存映射（key无强引用即自动删除）。

19. **P19: EnumMap紧凑数组实现** – 以枚举类ordinal作为索引，使用两个等长数组存储键与值；内部无哈希计算，内存极省，性能高于HashMap。

20. **P20: 集合工厂方法与不可变集合** – List.of/Set.of/Map.of创建不可变集合，内部不允许修改；元素非空且不支持null；JDK 9+，底层使用不可变数组，修改抛出UnsupportedOperationException。