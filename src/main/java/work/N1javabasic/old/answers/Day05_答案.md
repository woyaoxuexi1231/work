# Day 05: 常用集合：ArrayList、LinkedList 与 BlockingQueue - 完整答案

## 面试真题连环炮 - 详细解答

### 1. `ArrayList` 扩容是 1.5 倍，为什么不是 2 倍？

#### 核心原理

**一句话总结**：1.5 倍扩容是在空间利用率和扩容频率之间的平衡，避免内存浪费的同时减少扩容次数。

#### 详细分析

**扩容策略**：
```java
// ArrayList 的 grow 方法
private void grow(int minCapacity) {
    int oldCapacity = elementData.length;
    int newCapacity = oldCapacity + (oldCapacity >> 1);  // oldCapacity * 1.5
    
    if (newCapacity - minCapacity < 0)
        newCapacity = minCapacity;
    
    if (newCapacity - MAX_ARRAY_SIZE > 0)
        newCapacity = hugeCapacity(minCapacity);
    
    elementData = Arrays.copyOf(elementData, newCapacity);
}
```

**为什么是 1.5 倍而不是 2 倍**：

**1. 内存利用率对比**

假设初始容量 = 10，需要存储 100 个元素：

| 扩容倍数 | 扩容过程 | 最终容量 | 空间利用率 | 浪费空间 |
|---------|---------|---------|-----------|---------|
| 1.5 倍 | 10→15→22→33→49→73→109 | 109 | 91.7% | 9 |
| 2 倍 | 10→20→40→80→160 | 160 | 62.5% | 60 |

**结论**：1.5 倍扩容的空间利用率更高。

**2. 扩容频率对比**

```
1.5 倍：需要扩容 6 次
2 倍：需要扩容 4 次

虽然 2 倍扩容次数少，但最后一次扩容浪费了大量空间。
```

**3. 时间复杂度分析**

```java
// 扩容的总时间复杂度（均摊）
// 假设扩容倍数为 k，初始容量为 n0，最终容量为 N

// 扩容次数：log_k(N/n0)
// 每次扩容的代价：O(n)（复制数组）
// 总代价：n0 + k*n0 + k^2*n0 + ... + N
//        = N * (1 + 1/k + 1/k^2 + ...)
//        = N * k / (k - 1)  （等比数列求和）

// k = 1.5：总代价 = N * 1.5 / 0.5 = 3N
// k = 2：  总代价 = N * 2 / 1 = 2N

// 2 倍扩容的总复制次数更少，但浪费空间更多
// 1.5 倍是时间和空间的折中
```

**4. 为什么不选择其他倍数**：

| 倍数 | 优点 | 缺点 |
|------|------|------|
| 1.2 | 空间利用率高 | 扩容太频繁，性能差 |
| **1.5** | **平衡** | **无明显缺点** |
| 2.0 | 扩容次数少 | 空间浪费严重 |
| 3.0 | 扩容次数更少 | 空间浪费更严重 |

**5. 位运算优化**

```java
// 1.5 倍的实现：oldCapacity + (oldCapacity >> 1)
// 使用位移运算替代乘法，性能更好

// 对比：
int newCapacity1 = (int)(oldCapacity * 1.5);  // 浮点运算，慢
int newCapacity2 = oldCapacity + (oldCapacity >> 1);  // 位运算，快

// 位运算只需要 1 个 CPU 周期
// 浮点乘法需要 10+ 个 CPU 周期
```

**6. 历史原因**

```
- ArrayList 的前身是 Vector
- Vector 的扩容倍数是 2 倍
- 后来发现 2 倍浪费空间，改为 1.5 倍
- 这个值经过实践验证是合理的
```

---

### 2. `ArrayBlockingQueue` 与 `LinkedBlockingQueue` 的锁实现有什么区别？

#### 核心区别

**一句话总结**：ArrayBlockingQueue 使用单锁（生产和消费竞争同一把锁），LinkedBlockingQueue 使用双锁（生产和消费各有一把锁，可以并行）。

#### 详细分析

**ArrayBlockingQueue 的锁实现**：

```java
public class ArrayBlockingQueue<E> {
    final Object[] items;  // 数组
    int takeIndex;         // 队头索引
    int putIndex;          // 队尾索引
    int count;             // 元素数量
    
    // 单锁！生产和消费共享
    final ReentrantLock lock;
    private final Condition notEmpty;  // 消费条件
    private final Condition notFull;   // 生产条件
    
    public ArrayBlockingQueue(int capacity, boolean fair) {
        items = new Object[capacity];
        lock = new ReentrantLock(fair);
        notEmpty = lock.newCondition();
        notFull = lock.newCondition();
    }
}
```

**put 操作**：
```java
public void put(E e) throws InterruptedException {
    Objects.requireNonNull(e);
    final ReentrantLock lock = this.lock;
    lock.lockInterruptibly();  // 获取锁
    try {
        while (count == items.length)
            notFull.await();  // 队列满，等待
        
        enqueue(e);  // 入队
        notEmpty.signal();  // 通知消费者
    } finally {
        lock.unlock();
    }
}
```

**take 操作**：
```java
public E take() throws InterruptedException {
    final ReentrantLock lock = this.lock;
    lock.lockInterruptibly();  // 获取同一把锁！
    try {
        while (count == 0)
            notEmpty.await();  // 队列空，等待
        
        E e = dequeue();  // 出队
        notFull.signal();  // 通知生产者
        return e;
    } finally {
        lock.unlock();
    }
}
```

**问题**：
```
生产者和消费者不能并行：
- 生产者 put 时，消费者必须等待
- 消费者 take 时，生产者必须等待
- 即使队列不满也不空，两者仍然竞争同一把锁
```

---

**LinkedBlockingQueue 的锁实现**：

```java
public class LinkedBlockingQueue<E> {
    static class Node<E> {
        E item;
        Node<E> next;
        Node(E x) { item = x; }
    }
    
    private final int capacity;
    private final AtomicInteger count = new AtomicInteger();  // 原子计数
    
    transient Node<E> head;
    private transient Node<E> last;
    
    // 双锁！生产和消费各有独立的锁
    private final ReentrantLock takeLock = new ReentrantLock();
    private final Condition notEmpty = takeLock.newCondition();
    
    private final ReentrantLock putLock = new ReentrantLock();
    private final Condition notFull = putLock.newCondition();
}
```

**put 操作**：
```java
public void put(E e) throws InterruptedException {
    if (e == null) throw new NullPointerException();
    
    int c = -1;
    Node<E> node = new Node<E>(e);
    final ReentrantLock putLock = this.putLock;  // 只获取生产锁
    final AtomicInteger count = this.count;
    
    putLock.lockInterruptibly();
    try {
        while (count.get() == capacity)
            notFull.await();
        
        enqueue(node);
        c = count.getAndIncrement();  // 原子增加
        
        if (c + 1 < capacity)
            notFull.signal();  // 通知其他生产者
    } finally {
        putLock.unlock();
    }
    
    // 如果之前队列为空，通知消费者
    if (c == 0)
        signalNotEmpty();
}
```

**take 操作**：
```java
public E take() throws InterruptedException {
    E x;
    int c = -1;
    final AtomicInteger count = this.count;
    final ReentrantLock takeLock = this.takeLock;  // 只获取消费锁
    
    takeLock.lockInterruptibly();
    try {
        while (count.get() == 0)
            notEmpty.await();
        
        x = dequeue();
        c = count.getAndDecrement();  // 原子减少
        
        if (c > 1)
            notEmpty.signal();  // 通知其他消费者
    } finally {
        takeLock.unlock();
    }
    
    // 如果之前队列已满，通知生产者
    if (c == capacity)
        signalNotFull();
    
    return x;
}
```

**优势**：
```
生产和消费可以并行：
- 生产者 put 时，使用 putLock
- 消费者 take 时，使用 takeLock
- 两把锁互不干扰，可以并发执行

只有在队列满或空时才需要等待
```

---

**对比总结**：

| 特性 | ArrayBlockingQueue | LinkedBlockingQueue |
|------|-------------------|---------------------|
| **数据结构** | 数组 | 链表 |
| **锁数量** | 1 把锁 | 2 把锁（putLock、takeLock） |
| **并发度** | 低（生产消费互斥） | 高（生产消费可并行） |
| **内存占用** | 预分配，固定大小 | 动态分配，节点对象开销 |
| **容量限制** | 必须指定容量 | 可指定容量（默认 Integer.MAX_VALUE） |
| **适用场景** | 对内存敏感 | 对性能敏感 |

---

### 3. `PriorityBlockingQueue` 是如何保证有序的？它的扩容逻辑有何特殊之处？

#### 核心原理

**一句话总结**：PriorityBlockingQueue 使用二叉堆（完全二叉树）实现优先级队列，通过 siftUp 和 siftDown 操作维护堆的有序性。

#### 详细分析

**二叉堆结构**：

```java
public class PriorityBlockingQueue<E> {
    private transient Object[] queue;  // 数组实现二叉堆
    private transient int size;        // 元素数量
    
    // 二叉堆的性质：
    // 1. 完全二叉树：除了最后一层，其他层都是满的
    // 2. 堆序性：父节点的优先级 <= 子节点的优先级（小顶堆）
    
    // 数组表示：
    // 索引 0：根节点
    // 索引 i 的父节点：(i - 1) >>> 1
    // 索引 i 的左子节点：2 * i + 1
    // 索引 i 的右子节点：2 * i + 2
}
```

**二叉堆示例**：
```
       1
     /   \
    2     3
   / \   / \
  5   4 6   7

数组表示：[1, 2, 3, 5, 4, 6, 7]

性质：
- queue[0] = 1 是最小元素（队头）
- queue[i] <= queue[2*i+1] 且 queue[i] <= queue[2*i+2]
```

---

**插入操作（offer）**：

```java
public boolean offer(E e) {
    if (e == null) throw new NullPointerException();
    final ReentrantLock lock = this.lock;
    lock.lock();
    int n, cap;
    Object[] array;
    
    // 扩容检查
    while ((n = size) >= (cap = (array = queue).length))
        tryGrow(array, cap);
    
    try {
        Comparator<? super E> cmp = comparator;
        if (cmp == null)
            siftUpComparable(n, e, array);  // 使用自然顺序
        else
            siftUpComparator(n, e, array, cmp);
        
        size = n + 1;
        notEmpty.signal();
    } finally {
        lock.unlock();
    }
    return true;
}
```

**siftUp 操作**：
```java
private static <T> void siftUpComparable(int k, T x, Object[] array) {
    Comparable<? super T> key = (Comparable<? super T>) x;
    
    // 从最后一个位置开始上浮
    while (k > 0) {
        int parent = (k - 1) >>> 1;  // 父节点索引
        Object e = array[parent];
        
        // 如果 key >= 父节点，说明已经满足堆序性
        if (key.compareTo((T) e) >= 0)
            break;
        
        // 否则，父节点下沉
        array[k] = e;
        k = parent;
    }
    
    // 将 key 放在最终位置
    array[k] = key;
}
```

**siftUp 示例**：
```
初始堆：[1, 2, 3, 5, 4, 6, 7]
插入 0：

步骤 1：放在末尾
[1, 2, 3, 5, 4, 6, 7, 0]
                    ↑ k=7

步骤 2：与父节点比较（parent = (7-1)/2 = 3）
queue[3] = 5 > 0，交换
[1, 2, 3, 0, 4, 6, 7, 5]
              ↑ k=3

步骤 3：与父节点比较（parent = (3-1)/2 = 1）
queue[1] = 2 > 0，交换
[1, 0, 3, 2, 4, 6, 7, 5]
     ↑ k=1

步骤 4：与父节点比较（parent = (1-1)/2 = 0）
queue[0] = 1 > 0，交换
[0, 1, 3, 2, 4, 6, 7, 5]
 ↑ k=0

步骤 5：k=0，停止
最终堆：[0, 1, 3, 2, 4, 6, 7, 5]
```

---

**删除操作（poll）**：

```java
public E poll() {
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        return dequeue();
    } finally {
        lock.unlock();
    }
}

private E dequeue() {
    int n = size - 1;
    if (n < 0)
        return null;
    
    Object[] array = queue;
    E result = (E) array[0];  // 队头元素（最小）
    
    E x = (E) array[n];  // 最后一个元素
    array[n] = null;
    
    if (n != 0)
        siftDown(0, x, array);  // 从根节点下沉
    
    size = n;
    return result;
}
```

**siftDown 操作**：
```java
private static <T> void siftDownComparable(int k, T x, Object[] array) {
    Comparable<? super T> key = (Comparable<? super T>) x;
    int half = size >>> 1;  // 只需要检查非叶子节点
    
    while (k < half) {
        int child = (k << 1) + 1;  // 左子节点
        Object c = array[child];
        int right = child + 1;     // 右子节点
        
        // 选择较小的子节点
        if (right < size && ((Comparable<? super T>) c).compareTo((T) array[right]) > 0)
            c = array[child = right];
        
        // 如果 key <= 较小的子节点，满足堆序性
        if (key.compareTo((T) c) <= 0)
            break;
        
        // 否则，子节点上移
        array[k] = c;
        k = child;
    }
    
    array[k] = key;
}
```

**siftDown 示例**：
```
初始堆：[0, 1, 3, 2, 4, 6, 7, 5]
删除 0：

步骤 1：取走根节点，最后一个元素补到根
[5, 1, 3, 2, 4, 6, 7]
 ↑ k=0

步骤 2：与较小的子节点比较（left=1, right=2）
queue[1]=1 < queue[2]=3，选择左子节点
queue[0]=5 > 1，交换
[1, 5, 3, 2, 4, 6, 7]
     ↑ k=1

步骤 3：与较小的子节点比较（left=3, right=4）
queue[3]=2 < queue[4]=4，选择左子节点
queue[1]=5 > 2，交换
[1, 2, 3, 5, 4, 6, 7]
           ↑ k=3

步骤 4：k=3 是叶子节点，停止
最终堆：[1, 2, 3, 5, 4, 6, 7]
```

---

#### 扩容逻辑的特殊之处

**特殊之处 1：使用 AllocationSpinner 优化**

```java
private void tryGrow(Object[] array, int oldCap) {
    lock.unlock();  // 释放锁！允许并发读取
    
    Object[] newArray = null;
    
    // 使用 CAS 控制只有一个线程负责扩容
    if (allocationSpinLock == 0 &&
        UNSAFE.compareAndSwapInt(this, allocationSpinLock, 0, 1)) {
        try {
            // 扩容策略：小数组 2 倍，大数组 1.5 倍
            int newCap = oldCap + ((oldCap < 64) ? (oldCap + 2) : (oldCap >> 1));
            
            if (newCap - MAX_ARRAY_SIZE > 0) {
                int minCap = oldCap + 1;
                if (minCap < 0 || minCap > MAX_ARRAY_SIZE)
                    throw new OutOfMemoryError();
                newCap = MAX_ARRAY_SIZE;
            }
            
            if (newCap > array.length)
                newArray = new Object[newCap];
            
        } finally {
            allocationSpinLock = 0;  // 释放自旋锁
        }
    }
    
    // 如果其他线程正在扩容，当前线程让出 CPU
    else if (newArray == null)
        Thread.yield();
    
    lock.lock();  // 重新获取锁
    
    if (newArray != null && array == queue) {
        queue = newArray;
        System.arraycopy(array, 0, newArray, 0, oldCap);  // 复制元素
    }
}
```

**特殊之处 2：扩容时释放锁**

```
为什么扩容时要释放锁？
- 扩容是耗时操作（分配新数组 + 复制元素）
- 如果持有锁扩容，其他线程无法读取数据
- 释放锁后，读取操作可以并发进行

如何保证线程安全？
- 使用 allocationSpinLock 保证只有一个线程负责扩容
- 其他线程让出 CPU（Thread.yield）
- 扩容完成后重新获取锁，检查 queue 是否被修改
```

**特殊之处 3：动态扩容策略**

```java
// 小数组（< 64）：接近 2 倍扩容
// 大数组（>= 64）：1.5 倍扩容

// 原因：
// - 小数组时，2 倍扩容可以快速达到合适大小
// - 大数组时，1.5 倍扩容避免内存浪费
```

---

## 面试加分技巧

### 回答模板

**面试官**：ArrayList 扩容为什么是 1.5 倍？

**回答结构**：
1. **直接回答**："1.5 倍是空间利用率和扩容频率的平衡点"
2. **数据对比**："假设存储 100 个元素，1.5 倍扩容最终容量 109，空间利用率 91.7%；2 倍扩容最终容量 160，空间利用率只有 62.5%"
3. **理论依据**："从数学上看，扩容倍数为 k 时，总复制代价是 N * k / (k - 1)，1.5 倍在时间和空间上都是合理的折中"
4. **实现细节**："JDK 使用位运算 `oldCapacity + (oldCapacity >> 1)` 实现 1.5 倍，比浮点乘法性能更好"

### 常见错误回答

❌ **错误 1**："ArrayBlockingQueue 和 LinkedBlockingQueue 性能差不多"
✅ **正确**：LinkedBlockingQueue 使用双锁，生产消费可以并行，高并发下性能更好。

❌ **错误 2**："PriorityBlockingQueue 是完全有序的"
✅ **正确**：PriorityBlockingQueue 只保证队头是最小元素，其他元素不完全有序。

❌ **错误 3**："ArrayList 扩容是 2 倍"
✅ **正确**：ArrayList 扩容是 1.5 倍（oldCapacity + oldCapacity >> 1）。

---

## 深入学习建议

1. **阅读源码**：
   - `java.util.ArrayList` - 重点看 grow 方法
   - `java.util.concurrent.ArrayBlockingQueue` - 单锁实现
   - `java.util.concurrent.LinkedBlockingQueue` - 双锁实现
   - `java.util.concurrent.PriorityBlockingQueue` - siftUp/siftDown 操作

2. **实践练习**：
   - 手写一个简单的二叉堆
   - 对比 ArrayBlockingQueue 和 LinkedBlockingQueue 的性能差异
   - 测试 ArrayList 在不同初始容量下的性能

3. **扩展阅读**：
   - 《算法导论》- 堆排序章节
   - Java 并发包源码分析
