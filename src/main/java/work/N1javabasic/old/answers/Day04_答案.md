# Day 04: 并发容器：ConcurrentHashMap 与 CopyOnWrite - 完整答案

## 面试真题连环炮 - 详细解答

### 1. `ConcurrentHashMap` 1.8 为什么放弃分段锁？它的 `size()` 方法是如何实现的？

#### 为什么放弃分段锁

**一句话总结**：分段锁存在并发度受限、内存占用大、跨段操作性能差等问题，JDK 1.8 改用 CAS + synchronized 实现更细粒度的锁。

**详细分析**：

**JDK 1.7 分段锁的问题**：

**1. 并发度受限**
```java
// JDK 1.7 的结构
public class ConcurrentHashMap<K,V> {
    // Segment 数组，默认大小 16
    final Segment<K,V>[] segments;
    
    // 每个 Segment 是一把独立的锁
    static final class Segment<K,V> extends ReentrantLock {
        transient volatile HashEntry<K,V>[] table;
    }
}

// 问题：
// 最多只有 16 个 Segment，意味着最多只能有 16 个线程并发操作
// 即使 CPU 有 64 核，也无法提高并发度
```

**2. 内存占用大**
```java
// 每个 Segment 都包含：
// - ReentrantLock 对象（锁状态）
// - HashEntry 数组
// - 其他字段

// 假设有 16 个 Segment，即使很多 Segment 是空的
// 也要占用 16 个 ReentrantLock 对象的内存
```

**3. 跨段操作性能差**
```java
// size() 方法需要统计所有 Segment 的元素数量
public int size() {
    // 尝试 3 次不加锁统计
    for (int i = 0; i < 3; i++) {
        long sum = 0;
        for (Segment<K,V> seg : segments) {
            sum += seg.count;  // 读取 volatile 变量
        }
        if (统计结果一致) return sum;
    }
    
    // 如果 3 次不一致，说明有并发修改
    // 需要锁定所有 Segment！
    for (Segment<K,V> seg : segments) {
        seg.lock();  // 锁定所有段
    }
    // 统计...
    for (Segment<K,V> seg : segments) {
        seg.unlock();  // 解锁
    }
}

// 问题：
// - 锁定所有 Segment 时，整个 Map 都无法写入
// - 性能退化到 Hashtable 级别
```

**4. 扩容效率低**
```java
// JDK 1.7 扩容时，每个 Segment 独立扩容
// 无法利用多核优势进行协同扩容

// 某个 Segment 频繁扩容，其他 Segment 空闲
// 负载不均衡
```

---

#### JDK 1.8 的新设计

**核心改进：CAS + synchronized 实现桶级别锁**

```java
// JDK 1.8 的结构
public class ConcurrentHashMap<K,V> {
    // 直接使用 Node 数组，不再有 Segment
    transient volatile Node<K,V>[] table;
    
    // 使用 CAS 操作
    private static final sun.misc.Unsafe U;
    static final int MOVED = -1;      // 正在扩容
    static final int TREEBIN = -2;    // 红黑树
    static final int HASH_BITS = 0x7fffffff;
}
```

**优势对比**：

| 特性 | JDK 1.7（分段锁） | JDK 1.8（CAS + synchronized） |
|------|------------------|------------------------------|
| 锁粒度 | Segment 级别（16 个） | 桶级别（数组长度个） |
| 最大并发度 | 16 | 数组长度（默认 16，最大 2^30） |
| 内存占用 | 大（每个 Segment 都有锁） | 小（只在冲突时锁定） |
| 扩容 | 各 Segment 独立扩容 | 多线程协同扩容 |
| size() 实现 | 可能需要锁定所有 Segment | 基于 baseCount + CounterCell |

**put 操作的实现**：
```java
public V put(K key, V value) {
    return putVal(key, value, false);
}

final V putVal(K key, V value, boolean onlyIfAbsent) {
    if (key == null || value == null) throw new NullPointerException();
    
    // 步骤 1：计算 hash 值
    int hash = spread(key.hashCode());
    
    // 步骤 2：定位桶位置
    int binCount = 0;
    Node<K,V>[] tab = table;
    Node<K,V> f;
    int n, i, fh;
    
    // 步骤 3：如果桶为空，使用 CAS 插入
    if ((f = tabAt(tab, i = (n - 1) & hash)) == null) {
        if (casTabAt(tab, i, null, new Node<K,V>(hash, key, value, null)))
            break;  // CAS 成功，无需加锁
    }
    
    // 步骤 4：如果正在扩容，协助扩容
    else if ((fh = f.hash) == MOVED) {
        tab = helpTransfer(tab, f);
    }
    
    // 步骤 5：桶不为空，使用 synchronized 锁定
    else {
        V oldVal = null;
        synchronized (f) {  // 只锁定这个桶！
            if (tabAt(tab, i) == f) {
                if (fh >= 0) {
                    // 链表：遍历并插入
                    binCount = 1;
                    for (Node<K,V> e = f;; ++binCount) {
                        K ek;
                        if (e.hash == hash &&
                            ((ek = e.key) == key ||
                             (ek != null && key.equals(ek)))) {
                            oldVal = e.val;
                            if (!onlyIfAbsent)
                                e.val = value;
                            break;
                        }
                        Node<K,V> pred = e;
                        if ((e = e.next) == null) {
                            pred.next = new Node<K,V>(hash, key, value, null);
                            break;
                        }
                    }
                }
                else if (f instanceof TreeBin) {
                    // 红黑树：调用树的插入方法
                    Node<K,V> p;
                    if ((p = ((TreeBin<K,V>)f).putTreeVal(hash, key, value)) != null) {
                        oldVal = p.val;
                        if (!onlyIfAbsent)
                            p.val = value;
                    }
                }
            }
        }
        
        // 步骤 6：检查是否需要转换为红黑树
        if (binCount != 0) {
            if (binCount >= TREEIFY_THRESHOLD)
                treeifyBin(tab, i);
            if (oldVal != null)
                return oldVal;
            break;
        }
    }
    
    // 步骤 7：增加元素计数
    addCount(1L, binCount);
    return null;
}
```

**为什么选择 synchronized 而不是 ReentrantLock**：

```
1. JDK 1.6 之后 synchronized 优化很多：
   - 锁升级（偏向锁 → 轻量级锁 → 重量级锁）
   - 自适应自旋
   - 锁消除
   
2. synchronized 是 JVM 内置的，不需要额外对象
   ReentrantLock 需要创建对象，占用内存
   
3. synchronized 的代码更简洁
   编译器自动插入 monitorexit，保证异常时也能释放锁
   
4. 桶级别的锁竞争激烈程度低
   synchronized 足够应对
```

---

#### size() 方法的实现

**核心原理**：使用 baseCount + CounterCell 数组实现高并发统计

**详细分析**：

**问题背景**：
```java
// 传统实现：使用 volatile 变量
volatile int size;

public void put(K key, V value) {
    // ... 插入元素
    size++;  // 问题：不是原子操作！
}

// size++ 实际上是三个步骤：
// 1. 读取 size
// 2. size + 1
// 3. 写回 size

// 多线程并发执行时：
// 线程 1：读取 size = 10
// 线程 2：读取 size = 10
// 线程 1：size = 10 + 1 = 11
// 线程 2：size = 10 + 1 = 11
// 结果：两个线程插入后，size 只增加了 1！
```

**解决方案 1：使用 AtomicInteger**
```java
AtomicInteger size = new AtomicInteger(0);

public void put(K key, V value) {
    // ... 插入元素
    size.incrementAndGet();  // CAS 操作
}

// 问题：
// 高并发下，大量线程竞争同一个 AtomicInteger
// CAS 失败率高，需要不断重试
// 性能差！
```

**JDK 1.8 的解决方案：CounterCell 数组**

```java
// ConcurrentHashMap 的核心字段
private transient volatile long baseCount;  // 基础计数
private transient volatile CounterCell[] counterCells;  // 分散计数

// CounterCell 结构
@sun.misc.Contended
static final class CounterCell {
    volatile long value;
    CounterCell(long value) { this.value = value; }
}

// @Contended 注解的作用：
// - 避免伪共享（False Sharing）
// - 让 CounterCell 独占缓存行
// - 提升多核 CPU 的性能
```

**addCount 方法的实现**：
```java
private final void addCount(long x, int check) {
    CounterCell[] as; long b, s;
    
    // 步骤 1：尝试更新 counterCells 数组中的某个 Cell
    if ((as = counterCells) != null ||
        !U.compareAndSwapLong(this, BASECOUNT, b = baseCount, s = b + x)) {
        // 如果 counterCells 不为空，或者 CAS baseCount 失败
        
        CounterCell a; long v; int m;
        boolean uncontended = true;
        
        // 步骤 2：获取当前线程的 hash 值，选择 Cell
        int h = ThreadLocalRandom.getProbe();
        
        if (as == null || as.length == 0 ||
            (a = as[(h - 1) & m]) == null ||
            !(uncontended = U.compareAndSwapLong(a, CELLVALUE, v = a.value, v + x))) {
            
            // 步骤 3：如果 Cell 不存在或 CAS 失败，进入 fullAddCount
            fullAddCount(x, uncontended);
            return;
        }
    }
    
    // 步骤 4：检查是否需要扩容
    if (check >= 0) {
        // ... 检查逻辑
    }
}
```

**size() 方法**：
```java
public int size() {
    long n = sumCount();
    return ((n < 0L) ? 0 : (n > (long)Integer.MAX_VALUE) ? 
            Integer.MAX_VALUE : (int)n);
}

final long sumCount() {
    CounterCell[] as = counterCells;
    long sum = baseCount;  // 基础计数
    
    // 累加所有 CounterCell 的值
    if (as != null) {
        for (CounterCell a : as) {
            if (a != null)
                sum += a.value;
        }
    }
    
    return sum;
}
```

**工作原理图解**：

```
初始状态：
baseCount = 0
counterCells = null

线程 1 插入：
CAS baseCount: 0 → 1  （成功）
baseCount = 1

线程 2 插入：
CAS baseCount: 1 → 2  （成功）
baseCount = 2

高并发时（线程 3、4、5 同时插入）：
线程 3：CAS baseCount: 2 → 3  （失败！线程 4 抢先了）
         → 创建 counterCells[0]，value = 1
         
线程 4：CAS baseCount: 2 → 3  （成功）
         baseCount = 3
         
线程 5：CAS baseCount: 3 → 4  （失败！）
         → 创建 counterCells[1]，value = 1

最终状态：
baseCount = 3
counterCells[0].value = 1  （线程 3 的贡献）
counterCells[1].value = 1  （线程 5 的贡献）

size() = baseCount + counterCells[0] + counterCells[1]
       = 3 + 1 + 1 = 5
```

**为什么性能好**：
```
1. 分散竞争：
   - 不同线程更新不同的 CounterCell
   - 减少 CAS 冲突
   
2. @Contended 避免伪共享：
   - 每个 CounterCell 独占缓存行（64 字节）
   - 避免 CPU 缓存一致性协议的开销
   
3. 延迟初始化：
   - 低并发时只用 baseCount
   - 高并发时才创建 counterCells
```

---

### 2. `ConcurrentHashMap` 的扩容过程（多线程协同扩容）是怎样的？

#### 核心原理

**一句话总结**：JDK 1.8 使用 transfer 方法实现多线程协同扩容，通过 sizeCtl 控制并发，多个线程可以同时迁移不同桶的数据。

#### 详细分析

**扩容触发条件**：
```java
// 1. 元素数量超过阈值
if (元素数量 > capacity * loadFactor) {
    resize();
}

// 2. 链表长度达到 8，但数组容量 < 64
if (链表长度 >= 8 && capacity < 64) {
    resize();  // 优先扩容而不是转红黑树
}
```

**扩容流程**：

**步骤 1：创建新数组**
```java
private final void transfer(Node<K,V>[] tab, Node<K,V>[] nextTab) {
    int n = tab.length, stride;
    
    // 步骤 1：确定每个线程处理的桶数量（stride）
    if ((stride = (NCPU > 1) ? (n >>> 3) / NCPU : n) < MIN_TRANSFER_STRIDE)
        stride = MIN_TRANSFER_STRIDE;  // 最少 16 个桶
    
    // 步骤 2：只有一个线程负责创建新数组
    if (nextTab == null) {
        try {
            @SuppressWarnings("unchecked")
            Node<K,V>[] nt = (Node<K,V>[])new Node<?,?>[n << 1];  // 2 倍容量
            nextTab = nt;
        } catch (Throwable ex) {
            sizeCtl = Integer.MAX_VALUE;
            return;
        }
        nextTable = nextTab;
        transferIndex = n;  // 设置迁移起点
    }
}
```

**步骤 2：多线程协同迁移**
```java
// 使用 sizeCtl 控制并发
// sizeCtl < 0 表示正在扩容
// 高 16 位表示扩容标识，低 16 位表示参与扩容的线程数

int nextn = nextTab.length;
ForwardingNode<K,V> fwd = new ForwardingNode<K,V>(nextTab);
boolean advance = true;
boolean finishing = false;

// 步骤 3：多个线程分配迁移任务
for (int i = 0, bound = 0;;) {
    Node<K,V> f; int fh;
    
    // 分配下一个要迁移的桶
    while (advance) {
        int nextIndex, nextBound;
        
        // 检查是否完成
        if (--i >= bound || finishing)
            advance = false;
        
        // 获取新的迁移范围
        else if ((nextIndex = transferIndex) <= 0) {
            i = -1;
            advance = false;
        }
        else if (U.compareAndSwapInt(this, TRANSFERINDEX, nextIndex,
                 nextBound = (nextIndex > stride ? nextIndex - stride : 0))) {
            bound = nextBound;
            i = nextIndex - 1;
            advance = false;
        }
    }
    
    // 所有桶迁移完成
    if (i < 0 || i >= n || i + n >= nextn) {
        if (finishing) {
            nextTable = null;
            table = nextTab;  // 替换数组
            sizeCtl = (n << 1) - (n >>> 1);  // 设置新阈值
            return;
        }
        
        // 使用 CAS 减少扩容线程数
        if (U.compareAndSwapInt(this, SIZECTL, sc = sizeCtl, sc - 1)) {
            if ((sc - 2) != resizeStamp(n) << RESIZE_STAMP_SHIFT)
                return;
            
            finishing = true;
            advance = true;
            i = 0;  // 重新检查
        }
    }
    
    // 步骤 4：迁移当前桶
    else if ((f = tabAt(tab, i)) == null)
        advance = casTabAt(tab, i, null, fwd);  // 标记为正在迁移
    
    else if ((fh = f.hash) == MOVED)
        advance = true;  // 已经迁移过
    
    else {
        // 锁定当前桶，开始迁移
        synchronized (f) {
            if (tabAt(tab, i) == f) {
                Node<K,V> ln, hn;  // 低位桶和高位桶
                
                if (fh >= 0) {
                    // 链表迁移
                    int runBit = fh & n;  // 判断去低位还是高位
                    Node<K,V> lastRun = f;
                    
                    // 找到最后一个需要移动的节点
                    for (Node<K,V> p = f.next; p != null; p = p.next) {
                        int b = p.hash & n;
                        if (b != runBit) {
                            runBit = b;
                            lastRun = p;
                        }
                    }
                    
                    // 设置低位桶或高位桶
                    if (runBit == 0) {
                        ln = lastRun;
                        hn = null;
                    }
                    else {
                        hn = lastRun;
                        ln = null;
                    }
                    
                    // 拆分链表
                    for (Node<K,V> p = f; p != lastRun; p = p.next) {
                        int ph = p.hash; K pk = p.key; V pv = p.val;
                        if ((ph & n) == 0)
                            ln = new Node<K,V>(ph, pk, pv, ln);
                        else
                            hn = new Node<K,V>(ph, pk, pv, hn);
                    }
                    
                    // 设置新数组中的位置
                    setTabAt(nextTab, i, ln);      // 低位桶：原索引
                    setTabAt(nextTab, i + n, hn);  // 高位桶：原索引 + n
                    setTabAt(tab, i, fwd);         // 旧数组标记为已迁移
                    advance = true;
                }
                else if (f instanceof TreeBin) {
                    // 红黑树迁移（类似逻辑）
                    // ...
                }
            }
        }
    }
}
```

**多线程协同扩容图解**：

```
初始状态：
table 容量 = 64
transferIndex = 64
stride = 16（每个线程处理 16 个桶）

线程 1 开始扩容：
  创建新数组 nextTab，容量 = 128
  transferIndex = 64
  
线程 1 分配任务：
  CAS transferIndex: 64 → 48
  处理桶范围：[48, 64)
  
线程 2 加入扩容：
  CAS transferIndex: 48 → 32
  处理桶范围：[32, 48)
  
线程 3 加入扩容：
  CAS transferIndex: 32 → 16
  处理桶范围：[16, 32)
  
线程 1 完成 [48, 64)：
  CAS transferIndex: 16 → 0
  处理桶范围：[0, 16)
  
所有线程完成后：
  检查 finishing 标志
  替换 table = nextTab
  设置新的 sizeCtl
```

**为什么性能好**：

```
1. 多线程并行迁移：
   - 不同线程处理不同范围的桶
   - 充分利用多核 CPU
   
2. 细粒度锁定：
   - 只锁定当前正在迁移的桶
   - 其他桶可以正常读写
   
3. ForwardingNode 标记：
   - 已迁移的桶用 ForwardingNode 标记
   - 其他线程看到这个标记会协助扩容
   - 避免重复迁移
   
4. 无锁读：
   - 读操作不需要等待扩容完成
   - 通过 volatile 保证可见性
```

---

### 3. `CopyOnWriteArrayList` 的缺点是什么？适用于什么场景？

#### 写时复制思想

**核心原理**：写入时复制一份新数组，在新数组上修改，最后替换引用。

```java
public class CopyOnWriteArrayList<E> {
    private transient volatile Object[] array;
    
    public boolean add(E e) {
        final ReentrantLock lock = this.lock;
        lock.lock();  // 写入时加锁
        try {
            Object[] elements = getArray();
            int len = elements.length;
            
            // 步骤 1：复制新数组
            Object[] newElements = Arrays.copyOf(elements, len + 1);
            
            // 步骤 2：在新数组上修改
            newElements[len] = e;
            
            // 步骤 3：替换引用
            setArray(newElements);
            return true;
        } finally {
            lock.unlock();
        }
    }
    
    public E get(int index) {
        // 读取时不需要加锁
        return get(getArray(), index);
    }
}
```

---

#### 缺点分析

**1. 内存占用大**
```java
// 每次写入都要复制整个数组
List<Integer> list = new CopyOnWriteArrayList<>();

// 假设有 100 万个元素
for (int i = 0; i < 1000000; i++) {
    list.add(i);
}

// 此时内存中有 100 万元素
list.add(1000001);

// 写入时：
// - 旧数组：100 万元素（仍然存在，直到 GC）
// - 新数组：101 万元素
// - 峰值内存：201 万元素！

// 如果频繁写入，会触发频繁的 GC
```

**2. 数据延迟（弱一致性）**
```java
CopyOnWriteArrayList<String> list = new CopyOnWriteArrayList<>();
list.add("A");
list.add("B");

// 线程 1：迭代
Iterator<String> it = list.iterator();

// 线程 2：修改
list.add("C");

// 线程 1：继续迭代
while (it.hasNext()) {
    System.out.println(it.next());  // 只输出 A、B，不包含 C
}

// 原因：
// iterator 创建时获取的是旧数组的引用
// 后续的修改对新数组生效，旧数组不变
```

**3. 不支持实时迭代**
```java
// 迭代期间看不到最新数据
for (String item : list) {
    // 如果其他线程修改了 list
    // 这个 for 循环看到的是旧数据
}
```

**4. 写入性能差**
```java
// 写入操作的时间复杂度：O(n)
// - 复制数组：O(n)
// - 插入元素：O(1)

// 对比 ArrayList：
// - 写入：O(1)（均摊）
// - CopyOnWriteArrayList：O(n)

// 测试：
List<Integer> arrayList = new ArrayList<>();
List<Integer> cowList = new CopyOnWriteArrayList<>();

// 插入 10 万元素
// arrayList：约 10ms
// cowList：约 5000ms（慢 500 倍！）
```

---

#### 适用场景

**1. 读多写少**
```java
// 适合场景：配置信息、白名单、黑名单
// 这些场景特点：
// - 大部分时间只读
// - 偶尔更新（如管理员修改配置）
// - 对数据实时性要求不高

// 示例：系统配置
public class SystemConfig {
    private static CopyOnWriteArrayList<String> whitelist = 
        new CopyOnWriteArrayList<>();
    
    // 读取频繁
    public static boolean isAllowed(String ip) {
        return whitelist.contains(ip);  // O(n)，但不加锁
    }
    
    // 写入很少
    public static void addToWhitelist(String ip) {
        whitelist.add(ip);  // O(n)，但很少调用
    }
}
```

**2. 迭代器安全**
```java
// ArrayList 的迭代器会抛出 ConcurrentModificationException
ArrayList<String> list = new ArrayList<>();
list.add("A");
list.add("B");

for (String item : list) {
    list.add("C");  // ConcurrentModificationException!
}

// CopyOnWriteArrayList 不会抛出异常
CopyOnWriteArrayList<String> cowList = new CopyOnWriteArrayList<>();
cowList.add("A");
cowList.add("B");

for (String item : cowList) {
    cowList.add("C");  // 不会抛异常，但 C 不会在本次迭代中出现
}
```

**3. 线程安全要求高**
```java
// 读操作完全无锁，性能接近 ArrayList
// 适合高并发读场景

// 对比：
// - ArrayList + Collections.synchronizedList：
//   读操作也要加锁，性能差
// - CopyOnWriteArrayList：
//   读操作无锁，性能高
```

---

#### 与其他并发容器对比

| 容器 | 读性能 | 写性能 | 内存占用 | 一致性 | 适用场景 |
|------|--------|--------|---------|--------|---------|
| ArrayList | O(1) | O(1) | 低 | 强 | 单线程 |
| Collections.synchronizedList | O(1)（有锁开销） | O(1)（有锁开销） | 低 | 强 | 低并发 |
| CopyOnWriteArrayList | O(1)（无锁） | O(n) | 高 | 弱 | 读多写少 |
| ConcurrentHashMap | O(1)（无锁） | O(1)（细粒度锁） | 中 | 弱 | 高并发 |

---

## 代码实战解析：ConcurrentHashMapTest.java

### 核心要点解读

**1. 不允许 null 键值**
```java
// ConcurrentHashMap 严格禁止 null
concurrentHashMap.put(null, "value");  // NullPointerException
concurrentHashMap.put("key", null);    // NullPointerException

// 原因：
// 1. 避免歧义：get 返回 null 时，无法区分是键不存在还是值为 null
// 2. 简化实现：不需要特殊处理 null 的情况
// 3. 性能考虑：避免并发操作中的 null 检查
```

**2. 读操作无锁**
```java
// 读取操作直接使用 volatile 保证可见性
public V get(Object key) {
    Node<K,V>[] tab; Node<K,V> e, p; int n, eh; K ek;
    int h = spread(key.hashCode());
    
    // 直接读取 volatile 数组
    if ((tab = table) != null && (n = tab.length) > 0 &&
        (e = tabAt(tab, (n - 1) & h)) != null) {
        
        if ((eh = e.hash) == h) {
            if ((ek = e.key) == key || (ek != null && key.equals(ek)))
                return e.val;
        }
        else if (eh < 0)
            return (p = e.find(h, key)) != null ? p.val : null;
        
        // 遍历链表或红黑树
        while ((e = e.next) != null) {
            if (e.hash == h &&
                ((ek = e.key) == key || (ek != null && key.equals(ek))))
                return e.val;
        }
    }
    return null;
}

// 整个过程不需要加锁！
```

---

## 面试加分技巧

### 回答模板

**面试官**：ConcurrentHashMap 1.8 的 size() 方法是如何实现的？

**回答结构**：
1. **直接回答**："使用 baseCount + CounterCell 数组实现高并发统计"
2. **解释原理**："低并发时直接更新 baseCount，高并发时分散到 CounterCell 数组的不同 Cell 中，减少 CAS 竞争"
3. **对比旧方案**："JDK 1.7 的 size() 可能需要锁定所有 Segment，性能差。JDK 1.8 的方案完全无锁"
4. **拓展延伸**："CounterCell 使用了 @Contended 注解避免伪共享，进一步提升多核 CPU 的性能"

### 常见错误回答

❌ **错误 1**："ConcurrentHashMap 的 size() 是精确的"
✅ **正确**：size() 返回的是近似值，因为在统计过程中可能有其他线程修改数据。

❌ **错误 2**："CopyOnWriteArrayList 适合写入频繁的场景"
✅ **正确**：CopyOnWriteArrayList 只适合读多写少的场景，写入频繁会导致性能严重下降和频繁 GC。

❌ **错误 3**："JDK 1.8 的 ConcurrentHashMap 完全没有锁"
✅ **正确**：JDK 1.8 使用 synchronized 锁定单个桶，只是锁粒度更细，不是完全无锁。

---

## 深入学习建议

1. **阅读源码**：
   - `java.util.concurrent.ConcurrentHashMap` - 重点看 putVal、transfer、addCount 方法
   - `java.util.concurrent.CopyOnWriteArrayList` - 重点看 add、get、iterator 方法
   - `sun.misc.Unsafe` - CAS 操作的底层实现

2. **实践练习**：
   - 使用 JMH 测试 ConcurrentHashMap 和 Hashtable 的性能差异
   - 实现一个简单的基于 CAS 的并发容器
   - 测试 CopyOnWriteArrayList 在不同写频率下的性能

3. **扩展阅读**：
   - 《Java 并发编程实战》- 并发容器章节
   - JVM 内存模型和缓存一致性协议
   - 伪共享（False Sharing）问题及解决方案
