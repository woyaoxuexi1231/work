1. ### Java集合 核心面试原理清单（完整版）

   ---

   **P1: ArrayList动态扩容与内存拷贝**
   - 默认初始容量10，每次add超过阈值时按1.5倍扩容（右移一位），通过`Arrays.copyOf`迁移旧数组。
   - 频繁扩容影响性能，建议预估容量时使用`ensureCapacity`或指定初始容量。

   **P2: LinkedList双链表节点结构**
   - 内部类`Node`含prev、next、item，维护first/last指针。
   - 头尾增删O(1)，按索引查找O(n)，不支持随机访问，内存开销高于ArrayList。

   **P3: HashMap扰动函数与寻址**
   - 哈希值高16位与低16位异或（扰动），再与`(n-1)`按位与计算下标，减少冲突。
   - 容量保持2的幂，使位运算取模效率远高于`%`。

   **P4: HashMap链表转红黑树阈值**

   - 链表长度≥8且数组长度≥64时，树化为红黑树。
   - 长度≤6时退化为链表，避免TreeNode内存浪费与长尾检索开销。

   **P5: HashMap扩容与rehash优化**
   - 扩容为2倍时，节点索引要么在原位，要么在原位+旧容量。
   - 通过`(e.hash & oldCap) == 0`判断，避免重新哈希，提升迁移效率。

   **P6: ConcurrentHashMap JDK8锁粒度优化**

   - 采用CAS初始化数组和插入头节点，synchronized锁定桶头节点。
   - 粒度从Segment锁缩小到单桶（即数组下标级别），并发度极大提升。

   **P7: ConcurrentHashMap size()与CounterCell**

   - 使用`baseCount + CounterCell`数组，每个线程更新自己的CounterCell，累加时降低竞争。
   - 替代JDK7的Segment统计方式，适合高并发下频繁计数。

   **P8: HashSet底层HashMap实现**

   - 内部使用HashMap存储元素作为key，value为固定Object `PRESENT`。
   - 无序、无重复依赖HashMap的key唯一性，add操作返回HashMap的put结果。

   **P9: TreeMap红黑树与NavigableMap**

   - 基于红黑树存储键值对，左<根<右，支持自然排序或Comparator。
   - 查找/插入O(log n)，提供`ceiling`/`floor`/`higher`/`lower`等导航方法。

   **P10: LinkedHashMap双向链表与访问顺序**
   - 继承HashMap，再串联双向链表维护顺序。
   - `accessOrder`为false时按插入顺序迭代，为true时按访问顺序，可用于实现LRU缓存（需重写`removeEldestEntry`）。

   **P11: PriorityQueue二叉堆结构**

   - 基于数组存储小顶堆（默认），通过`siftUp`/`siftDown`上下调整。
   - 非线程安全，入队出队O(log n)，不支持null元素，堆顶始终是最小元素。

   **P12: ArrayDeque双端队列循环数组**
   - 数组头尾指针循环（head/tail），避免数据搬移。
   - 容量为2的幂，支持栈与队列操作，性能优于Stack和LinkedList，不允许null元素。

   **P13: CopyOnWriteArrayList写时复制原理**
   - 读操作无锁（直接返回volatile数组快照），写操作加锁后复制新数组（`Arrays.copyOf`），写完替换volatile引用。
   - 适用于读多写少场景，弱一致性，迭代器不支持remove。

   **P14: Vector与Stack遗留类缺陷** （强制不展开了）

   - Vector内部方法全synchronized（锁粗粒度），性能差，基本被ArrayList替代。
   - Stack继承Vector，栈操作存在线程安全问题且违反LIFO设计，推荐用`Deque`（如ArrayDeque）替代。

   **P15: Collections.synchronizedXXX包装**
   - 基于synchronized代码块包装非线程安全集合，每个方法加锁。
   - 迭代器需外部手动同步（如`synchronized(list)`），否则可能抛出`ConcurrentModificationException`。

   **P16: 快速失败（fail-fast）与安全失败（fail-safe）**
   - 非并发集合迭代时检查`modCount`，检测到结构性修改立即抛出`ConcurrentModificationException`。
   - `CopyOnWriteArrayList`使用快照实现安全失败，迭代不抛异常但可能读到旧数据。

   **P17: IdentityHashMap与HashMap区别**
   - `IdentityHashMap`使用`==`而非`equals`比较key，且不调用`hashCode()`，使用`System.identityHashCode`。
   - 用于特殊引用语义场景，如对象代理、序列化处理。

   **P18: WeakHashMap弱引用回收机制**
   - Entry的key为弱引用，GC时自动回收，并在下次操作（get/put/size等）时清除过期Entry。
   - 适合缓存映射，key无强引用即自动删除，常用于内存敏感的缓存。

   **P19: EnumMap紧凑数组实现**
   - 以枚举类ordinal作为索引，使用两个等长数组存储键与值。
   - 内部无哈希计算，内存极省，性能高于HashMap，key必须为同一枚举类型，不允许null key。

   **P20: 集合工厂方法与不可变集合**
   - `List.of`/`Set.of`/`Map.of`（JDK 9+）创建不可变集合，元素非空且不支持null。
   - 底层使用不可变数组，修改抛出`UnsupportedOperationException`，与`Collections.unmodifiableXXX`视图不同，是真正不可变。

   ---

   **P21: ConcurrentSkipListMap跳表结构**
   - 基于跳表实现的有序并发Map，key按自然序或比较器排序。
   - 跳表通过多层索引实现O(log n)的查找/插入/删除，比红黑树更易实现并发。
   - 使用CAS+自旋实现无锁/低锁竞争，支持高并发有序遍历与范围查找，如排行榜场景。
   - 与`TreeMap`对比：TreeMap非线程安全，ConcurrentSkipListMap并发安全且支持`NavigableMap`接口。

   **P22: BlockingQueue阻塞队列与生产者-消费者模型**
   - 核心接口：`take()`（阻塞取）、`put()`（阻塞放）、`offer/poll`超时变体。
   - 常见实现：
     - `ArrayBlockingQueue`：有界，单锁，基于数组。
     - `LinkedBlockingQueue`：可选有界（默认Integer.MAX_VALUE），双锁分离入队/出队，吞吐量高。
     - `PriorityBlockingQueue`：无界，基于堆，可排序。
     - `SynchronousQueue`：不存储元素，直接传递，适合手递手交换。
     - `DelayQueue`：元素需实现`Delayed`接口，到期才能取出。
   - 是`ThreadPoolExecutor`的工作队列基础，面试常与线程池结合考察。

   **P23: ConcurrentLinkedQueue/ConcurrentLinkedDeque**
   - 基于CAS+自旋的无锁并发队列/双端队列，内部采用单向/双向链表。
   - 入队/出队无锁，适合高并发非阻塞场景，是典型的Lock-Free算法。
   - 与阻塞队列区别：不阻塞线程，通过循环CAS尝试，不会引起线程挂起。

   **P24: Hashtable与HashMap、ConcurrentHashMap区别**
   - `Hashtable`：遗留类，全表synchronized锁，性能极差；key/value不能为null；枚举迭代器是fail-safe的，已不推荐使用。
   - `HashMap`：非线程安全，key/value均可为null。
   - `ConcurrentHashMap`：分段锁思想（JDK7 Segment继承ReentrantLock，JDK8 CAS+synchronized），支持高并发，key/value不能为null。

   **P25: 排序与比较机制：Comparable与Comparator**
   - `Comparable`：类内部实现`compareTo`，定义自然顺序（如String、Integer）。
   - `Comparator`：外部实现`compare`，可传递给有序集合（如`TreeMap(Comparator)`），支持多策略排序。
   - 常考点：两者区别、`TreeMap`/`TreeSet`维持有序性原理、Comparator的Lambda/链式写法（thenComparing）。

   **P26: 迭代器设计模式与ListIterator**
   - `Iterator`统一遍历方式，`ListIterator`支持双向移动、添加/修改元素。
   - 迭代中修改集合会触发fail-fast（除迭代器自身的remove外），必须使用迭代器安全操作。
   - `ListIterator`特有方法：`hasPrevious()`、`previous()`、`nextIndex()`、`previousIndex()`、`add()`、`set()`。

   **P27: 集合工具类Collections的排序与二分查找**
   - `Collections.sort()`要求列表元素实现Comparable或提供Comparator，底层调用`List.sort`（归并排序/TimSort）。
   - `Collections.binarySearch()`必须在有序列表上使用，返回负值表示未找到，可通过`-(插入点)-1`计算插入点。
   - 其他常见工具：`reverse()`、`shuffle()`、`frequency()`、`unmodifiableXXX`（不可变视图，区别于`List.of`的不可变集合）。

   **P28: 数组与集合的转换**

   - 集合转数组：`list.toArray(new String[0])`（推荐零长度数组），直接强转会抛出`ClassCastException`。
   - 数组转集合：`Arrays.asList()`返回固定大小的视图，修改会相互影响，不支持增删操作；JDK 9+ `List.of(array)`创建不可变集合。
   - 需要注意基本类型数组陷阱：`Arrays.asList(int[])`会将其视为单个元素，需使用包装类数组。