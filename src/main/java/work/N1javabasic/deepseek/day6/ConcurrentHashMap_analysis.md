# ConcurrentHashMap 核心机制分析

## 1. 弱一致性迭代器（Weakly Consistent Iterator）

### 现象

`ConcurrentHashMap` 的迭代器不会抛出 `ConcurrentModificationException`。遍历过程中，其他线程对 map 的修改**可能反映、也可能不反映**在迭代器中。

```java
ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();
map.put("a", "1");
map.put("b", "2");

Iterator<String> it = map.keySet().iterator();
// 此时另一个线程 put("c", "3") —— 迭代器可能看到也可能看不到 "c"
while (it.hasNext()) {
    System.out.println(it.next());
}
```

### 原理

Java 8+ 的 `ConcurrentHashMap` 底层数据结构是 **Node 数组 + 链表/红黑树**。迭代器（如 `KeyIterator`、`ValueIterator`）的实现类继承自 `BaseIterator`，其核心遍历逻辑：

```java
// 简化后的遍历逻辑
final Node<K,V> advance() {
    Node<K,V> e;
    // 当前链表还有节点，直接返回下一个
    if ((e = next) != null)
        e = e.next;
    // 当前链表走完了，跳到 table 中下一个不为空的槽位
    while (e == null) {
        // 检查是否还有下一个槽位
        if (i >= n)
            return null; // 遍历结束
        // 用 tabAt  volatile 读下一个槽位的头节点
        e = tabAt(tab, i++);
    }
    next = e;
    return e;
}
```

关键设计：

- **Node 的 `next` 字段声明为 `volatile`**：迭代器每次调用 `next()` 时，读取到的都是当前时刻链表的最新引用。但这不代表它能看到该链表上**后续新增**的节点。
- **迭代器持有 `tab` 引用**：创建迭代器时，拿到的是当时 `table` 数组的引用。即便之后发生扩容（`table` 指向新数组），迭代器依然遍历**旧数组**上的链表。
- **没有修改计数**：与 `HashMap` 不同，CHM 不维护全局 `modCount`，所以不会检测并发修改，也就不用抛异常。

### 一句话总结

> 迭代器遍历的是它创建时**那一刻的链表状态**的**某种延续**。遍历过程中并发插入的新节点，如果被链在了已遍历过的节点后面，就看不到；如果链在了未遍历的节点后面，就可能看到。这就是"弱一致性"。

---

## 2. 并发 get / put 的可见性问题

### 问题

多个线程同时 `get` 和 `put`，会不会读到脏数据？会不会读到 `null`？

### 答案

**不会读到脏数据，也不会读到 `null`（除非 key 本身不存在）。**

### 源码分析

#### Node 的字段设计

```java
static class Node<K,V> implements Map.Entry<K,V> {
    final int hash;
    final K key;
    volatile V val;       // val 是 volatile 的！
    volatile Node<K,V> next; // next 也是 volatile 的！
}
```

- `val` 声明为 `volatile`，保证一个线程对 `val` 的写入**立即对另一个线程的读取可见**。
- `next` 声明为 `volatile`，保证链表结构的变更对其他线程立即可见。

#### get 操作（无锁）

```java
public V get(Object key) {
    Node<K,V>[] tab; Node<K,V> e, p; int n, eh; K ek;
    int h = spread(key.hashCode());
    if ((tab = table) != null && (n = tab.length) > 0 &&
        (e = tabAt(tab, (n - 1) & h)) != null) {
        // 检查头节点
        if ((eh = e.hash) == h) {
            if ((ek = e.key) == key || (ek != null && key.equals(ek)))
                return e.val; // volatile 读
        }
        // 负数 hash => ForwardingNode（扩容中）或 TreeBin
        else if (eh < 0)
            return (p = e.find(h, key)) != null ? p.val : null;
        // 遍历链表
        while ((e = e.next) != null) {
            if (e.hash == h &&
                ((ek = e.key) == key || (ek != null && key.equals(ek))))
                return e.val; // volatile 读
        }
    }
    return null;
}
```

`tabAt()` 底层是 `Unsafe.getObjectVolatile()`，即 volatile 读。

#### put 操作（锁住槽位）

```java
final V putVal(K key, V value, boolean onlyIfAbsent) {
    // ...
    Node<K,V> f;
    if ((f = tabAt(tab, i)) == null) {
        // 槽位为空 => CAS 无锁插入
        if (casTabAt(tab, i, null, new Node<>(hash, key, value)))
            break;
    } else {
        // 槽位有节点 => synchronized 锁住头节点
        synchronized (f) {
            // 再次验证头节点没变（double-check）
            if (tabAt(tab, i) == f) {
                // ... 执行插入或更新
                e.val = value; // volatile 写
            }
        }
    }
}
```

### 可见性保证链条

```
put 线程: e.val = value  (volatile 写)
    ↓  happen-before 规则
get 线程: e.val          (volatile 读)
```

`volatile` 写-读建立的 **happen-before** 关系保证：只要 get 读到了某个 Node 引用，该 Node 上的 `val` 一定是 put 线程写入的最新值。

### 扩容期间的特殊处理

扩容时，旧 table 中的节点会被复制到新 table，旧槽位插入一个 `ForwardingNode`（hash = MOVED）。
- get 操作遇到 `ForwardingNode`，会调用其 `find()` 方法，**自动跳到新 table 去查**，不会阻塞。
- put 操作遇到 `ForwardingNode`，会**协助扩容**（`helpTransfer()`），等扩容完成后再插入。

### 边界情况：key 不存在

即使并发 put，get 也只能返回两种结果：
- key 已存在 → volatile 读到最新 `val`
- key 不存在 → 返回 `null`

不存在"读到 null 但 key 其实已存在"的情况，因为 volatile 语义保证**对 val 的写入在对 next/table 的写入之前就可见**。

---

## 3. 遍历时删除元素

### 问题

使用迭代器遍历 CHM 时，删除元素（`iterator.remove()` 或另一个线程删除）会怎样？

### 答案

**迭代器继续正常工作，不会抛异常，但删除的元素"可能"或"可能不"在后续遍历中出现，取决于删除位置与当前遍历指针的关系。**

### 情况分析

#### 情况一：删除**已遍历过**的元素
```java
// 当前已遍历到 "c"，"a" 已经输出过
// 另一个线程删除 "a" 或 iterator.remove() 删除当前元素
// → 不影响后续遍历，迭代器继续往后走
```

#### 情况二：删除**未遍历到**的元素
```java
// 当前已遍历到 "c"，"d" 还没走到
// 另一个线程删除 "d"
// → 迭代器走到 "d" 所在的链表位置时，节点已经不在了，自然跳过
```

#### 情况三：删除**当前**元素
```java
Iterator<String> it = map.keySet().iterator();
while (it.hasNext()) {
    String key = it.next();
    if (key.equals("c")) {
        it.remove(); // ✅ 正确删除当前元素
    }
    // 可以继续遍历剩余元素
}
```

`iterator.remove()` 内部调用的是 `ConcurrentHashMap.replaceNode()`，它会：
1. 同步锁住头节点
2. 遍历链表找到目标节点
3. 将 `prev.next` 指向 `target.next`（跳过当前节点）
4. 由于迭代器已经通过 `next()` 拿到了当前节点的引用，继续调用 `next()` 会走当前节点的 `next` 字段，**不受链表断开的影响**

### 与 HashMap 的对比

| 特性 | HashMap | ConcurrentHashMap |
|------|---------|-------------------|
| 遍历时修改 | 抛 ConcurrentModificationException | 不抛异常 |
| 迭代器行为 | fail-fast | 弱一致性 |
| 删除元素后的迭代 | 不可继续 | 可继续正常遍历 |
| 判断机制 | modCount 计数器 | 无计数器，基于 volatile next 遍历 |

### 注意事项

- `iterator.remove()` 在 CHM 中是**线程安全**的，不需要外部同步。
- 如果迭代器遍历过程中，另一个线程**清空**了 map（`clear()`），迭代器依然可以继续遍历它创建时的链表，不受影响。
- JDK 源码注释的原话：*"Iterators are designed to be used by only one thread at a time."* 虽然迭代器本身线程安全，但不建议多个线程共享同一个迭代器实例。

---

## 4. sizeCtl 控制变量

### 概述

`sizeCtl` 是 `ConcurrentHashMap` 中一个**多用途的 `volatile` 状态标志位**，不同数值代表不同含义。

```java
private transient volatile int sizeCtl;
```

它贯穿 CHM 的**初始化 → 正常写入 → 扩容 → 缩容**全生命周期，是理解 CHM 并发控制的关键入口。

### 取值含义速查表

| 取值 | 含义 |
|------|------|
| `0` | 默认值，table 未初始化，使用默认容量 |
| `-1` | table 正在被某个线程初始化（`initTable()` 中） |
| `-(1 + n)` | 有 n 个线程正在协同扩容（resize） |
| `> 0` | **初始化前**：目标容量；**初始化后**：下一次扩容阈值（`table.length * 0.75`） |

### 详细机制

#### 状态 ①：`sizeCtl = 0` —— 未初始化，使用默认容量

```java
// 无参构造器
public ConcurrentHashMap() {
}
```

构造器只设了一个空壳，`table` 为 `null`，`sizeCtl` 为默认值 `0`。第一次 put 时才真正分配数组。

#### 状态 ②：`sizeCtl = -1` —— 初始化独占锁

首次 put 时调用的 `initTable()` 用 CAS 争抢初始化权：

```java
private final Node<K,V>[] initTable() {
    Node<K,V>[] tab; int sc;
    while ((tab = table) == null || tab.length == 0) {
        // sizeCtl < 0 说明有别的线程在初始化，让出 CPU
        if ((sc = sizeCtl) < 0)
            Thread.yield(); // 自旋等待

        // CAS: 将 sizeCtl 从 sc 改为 -1，抢到初始化权
        else if (U.compareAndSetInt(this, SIZECTL, sc, -1)) {
            try {
                if ((tab = table) == null || tab.length == 0) {
                    int n = (sc > 0) ? sc : DEFAULT_CAPACITY;
                    Node<K,V>[] nt = (Node<K,V>[])new Node<?,?>[n];
                    table = tab = nt;
                    // sc = n - n/4 = 0.75n，即扩容阈值
                    sc = n - (n >>> 2);
                }
            } finally {
                sizeCtl = sc; // 释放锁：设为正数（扩容阈值）
            }
            break;
        }
    }
    return tab;
}
```

关键点：
- CAS 竞争：失败的线程不会阻塞，而是 **`Thread.yield()` 自旋等待**，直到成功线程完成初始化。
- 初始化完成后 `sizeCtl` 被设为 `0.75 * n`，之后进入正常扩容阈值模式。
- `finally` 中赋值保证即使初始化异常也不会死锁，这是**模仿双检锁（DCL）模式的经典 CAS 自旋实现**。

#### 状态 ③：`sizeCtl = -(1 + n)` —— 多线程协同扩容

扩容由 `addCount()` 触发（put 之后元素数量达到阈值），核心入口是 `transfer()`。扩容时 `sizeCtl` 的 CAS 变化：

```java
// addCount() 中判断需要扩容后：
private final void transfer(Node<K,V>[] tab, Node<K,V>[] nextTab) {
    int n = tab.length, stride;
    // 计算每个线程负责的槽位数（CPU 核数多时 stride=16，少时 ≈ n）
    // ...

    if (nextTab == null) { // 第一个线程触发扩容，创建新数组
        nextTab = new Node<?,?>[n << 1];
        // ...
    }

    // 关键：扩容开始时，sizeCtl 从正阈值 变为 -(1 + nThreads)
    // 通过 CAS 给 sizeCtl + 1 来"注册"一个参与线程
}
```

当其他线程执行 put 操作发现槽位是 `ForwardingNode` 时，会调用 `helpTransfer()` 加入扩容：

```java
final Node<K,V>[] helpTransfer(Node<K,V>[] tab, Node<K,V> f) {
    Node<K,V>[] nextTab; int sc;
    if (tab != null && (f instanceof ForwardingNode) &&
        (nextTab = ((ForwardingNode<K,V>)f).nextTable) != null) {

        // 当前扩容任务的时间戳标记
        int rs = resizeStamp(tab.length);

        // CAS: sizeCtl + 1，注册一个扩容线程
        while (nextTab == nextTable && table == tab &&
               (sc = sizeCtl) < 0) {
            // CAS 将 sizeCtl 从 sc 加到 sc + 1
            if (U.compareAndSetInt(this, SIZECTL, sc, sc + 1)) {
                transfer(tab, nextTab);
                break;
            }
        }
        return nextTab;
    }
    return table;
}
```

扩容结束时，最后一个退出 `transfer()` 的线程负责：
- 将 `nextTable` 赋给 `table`（新数组上位）
- 重置 `sizeCtl = 2 * n * 0.75`（新数组的扩容阈值）

`sizeCtl` 在扩容期间的**负值编码**：

```
sizeCtl = -(1 + nThreads)
         ↑        ↑
    标志位   当前参与扩容的线程数

例：sizeCtl = -3  表示当前有 2 个线程正在协同扩容
    sizeCtl = -1  表示 table 正在初始化
```

#### 状态 ④：`sizeCtl > 0` —— 扩容阈值

正常工作状态下，`sizeCtl` 代表下一次触发扩容的**元素个数阈值**：

```java
// 初始化完成后
sizeCtl = n - (n >>> 2);  // 即 n * 0.75

// 扩容完成后
sizeCtl = (n << 1) - (n >>> 1);  // 即 (2n) * 0.75
```

`addCount()` 每次 put 后检查：

```java
private final void addCount(long x, int check) {
    // ... CounterCell 计数 ...

    if (check >= 0) {
        Node<K,V>[] tab, nt; int n, sc;
        while (s >= (long)(sc = sizeCtl)
               && (tab = table) != null
               && (n = tab.length) < MAXIMUM_CAPACITY) {
            int rs = resizeStamp(n);
            if (sc < 0) {
                // 扩容已在其他线程进行中，协助或跳过
                // ...
            }
            // CAS: 将 sizeCtl 设为负数标记 rs | (1 << 15)，
            // 表示当前线程要启动扩容
            else if (U.compareAndSetInt(this, SIZECTL, sc,
                                         (rs << RESIZE_STAMP_SHIFT) + 2))
                transfer(tab, null);
            // ...
        }
    }
}
```

### 为什么 sizeCtl 的负值不直接用 "-nThreads" 而是用 stamp？

`resizeStamp()` 返回的是基于扩容前数组长度的**校验戳**：

```java
static final int resizeStamp(int n) {
    return Integer.numberOfLeadingZeros(n) | (1 << (RESIZE_STAMP_BITS - 1));
}
```

这样做是为了**避免新老扩容冲突**——如果两次扩容的数组长度不同，计算出的 stamp 不同，CAS 就不会混淆。确保线程只加入与自己看到的状态相匹配的扩容任务。

### 完整生命周期示例

假设一个 CHM 从创建到扩容的全过程：

```
步骤                          sizeCtl    含义
─────────────────────────────────────────────────
new ConcurrentHashMap()          0      未初始化
put → initTable()               -1      初始化中
初始化完成 → sizeCtl = 16*0.75   12     扩容阈值
put 4 个元素                     12     阈值不变
put 到第 13 个元素 → addCount()  负数     触发扩容
扩容中另一个线程 put → help      负数+1   加入扩容
扩容结束 → sizeCtl = 32*0.75     24     新阈值
```

### sizeCtl vs HashMap 的 threshold

| 特性 | HashMap | ConcurrentHashMap |
|------|---------|-------------------|
| 阈值变量 | `threshold`（int） | `sizeCtl`（volatile int） |
| 兼顾多用途 | 仅存储阈值 | 阈值 + 初始化标志 + 扩容线程计数 |
| 并发协调 | 无 | CAS + volatile 读写实现无锁状态切换 |

### 面试常问点

1. **问**：`sizeCtl = -1` 和 `sizeCtl = -(1+n)` 如何区分？
   **答**：`-1` 只出现在 `initTable()` 中的初始化阶段，其他负值全部表示扩容。（也可以通过 `resizeStamp` 解码校验。）

2. **问**：多个线程同时调用 `helpTransfer()`，谁来扩容？谁是新 table？
   **答**：第一个触发扩容的线程在 `transfer()` 中创建 `nextTab`，后续线程通过 CAS 将 `sizeCtl` 加 1 注册自己后，进入同一个 `transfer()` 领取不同的槽位范围。最后一个结束的线程负责收尾（将 `nextTab` 赋值给 `table`）。

3. **问**：`sizeCtl` 是 `volatile` 的，为什么 CAS 还要传本地变量 `sc`？
   **答**：因为 CAS 操作需要对比**期望值**和**内存值**，`sc` 就是期望值。每次循环重新读取 `sizeCtl` 到 `sc`，然后 CAS 尝试更新，保证正确性。

---

## 总结对比表

| 机制 | 原理简述 | 是否保证 |
|------|---------|---------|
| 弱一致性迭代器 | 基于 volatile next 链表遍历，不维护全局 modCount | 不保证看到所有最新修改 |
| get 可见性 | val 是 volatile，get 走无锁 volatile 读 | 保证读到最新已完成写入 |
| put 线程安全 | CAS + synchronized 锁槽位头节点 | 保证原子性和可见性 |
| 遍历时删除 | 迭代器基于链表引用遍历，链表断开不影响已拿到的节点 | 迭代器不受影响 |
| sizeCtl 状态控制 | CAS + volatile 实现无锁状态切换，兼顾阈值/初始化锁/扩容线程计数 | 保证多线程下状态转换的原子性 |
