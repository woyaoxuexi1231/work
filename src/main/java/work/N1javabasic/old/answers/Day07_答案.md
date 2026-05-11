# Day 07: 第一周综合复盘与 LRU Cache 实现 - 完整答案

## LRU Cache 实现详解

### 题目：如果让你实现一个 LRU Cache，你会选择哪种集合？为什么？

#### 核心答案

**一句话总结**：选择 `LinkedHashMap`，因为它内部维护了双向链表，可以按访问顺序排序，天然支持 LRU（最近最少使用）淘汰策略。

#### 详细分析

**LRU 算法原理**：

```
LRU（Least Recently Used）：最近最少使用
- 当缓存满时，淘汰最久未被访问的元素
- 核心思想：如果数据最近被访问过，那么将来被访问的概率也更高

示例：
缓存容量 = 3

操作序列：
1. put(1, A)  → 缓存：[1]
2. put(2, B)  → 缓存：[1, 2]
3. put(3, C)  → 缓存：[1, 2, 3]
4. get(1)     → 缓存：[2, 3, 1]  （1 被访问，移到末尾）
5. put(4, D)  → 缓存：[3, 1, 4]  （缓存满，淘汰最久未使用的 2）
```

---

#### 为什么选择 LinkedHashMap？

**LinkedHashMap 的优势**：

```java
// LinkedHashMap 继承自 HashMap，额外维护了一个双向链表
public class LinkedHashMap<K,V> extends HashMap<K,V> {
    
    // 双向链表的头尾节点
    transient LinkedHashMap.Entry<K,V> head;
    transient LinkedHashMap.Entry<K,V> tail;
    
    // 访问顺序开关
    final boolean accessOrder;  // true=访问顺序，false=插入顺序
    
    static class Entry<K,V> extends HashMap.Node<K,V> {
        Entry<K,V> before, after;  // 双向链表指针
    }
}
```

**两种排序模式**：

```java
// 模式 1：插入顺序（默认）
Map<String, Integer> map = new LinkedHashMap<>();
map.put("A", 1);
map.put("B", 2);
map.put("C", 3);

// 迭代顺序：A → B → C（按插入顺序）

// 模式 2：访问顺序（LRU 需要）
Map<String, Integer> map = new LinkedHashMap<>(16, 0.75f, true);
map.put("A", 1);
map.put("B", 2);
map.put("C", 3);

map.get("A");  // 访问 A，A 移到末尾

// 迭代顺序：B → C → A（按访问顺序，最近访问的在末尾）
```

---

#### 完整实现

**实现 1：基于 LinkedHashMap 的 LRU Cache**

```java
public class LRUCache<K, V> extends LinkedHashMap<K, V> {
    
    private final int capacity;
    
    public LRUCache(int capacity) {
        // 调用父类构造方法
        // initialCapacity: 初始容量
        // loadFactor: 负载因子（设为 1，避免扩容）
        // accessOrder: true 表示按访问顺序排序
        super(capacity, 1.0f, true);
        this.capacity = capacity;
    }
    
    /**
     * 重写 removeEldestEntry 方法
     * 当缓存超过容量时，删除最老的元素
     */
    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > capacity;
    }
    
    // 使用示例
    public static void main(String[] args) {
        LRUCache<Integer, String> cache = new LRUCache<>(3);
        
        cache.put(1, "A");
        cache.put(2, "B");
        cache.put(3, "C");
        System.out.println(cache);  // {1=A, 2=B, 3=C}
        
        cache.get(1);  // 访问 1，1 移到末尾
        System.out.println(cache);  // {2=B, 3=C, 1=A}
        
        cache.put(4, "D");  // 缓存满，淘汰最老的 2
        System.out.println(cache);  // {3=C, 1=A, 4=D}
    }
}
```

**时间复杂度**：
```
- get(key)：O(1)  （HashMap 查找 + 链表移动）
- put(key, value)：O(1)  （HashMap 插入 + 链表插入）
- 淘汰元素：O(1)  （直接删除链表头部）
```

---

**实现 2：手动实现 LRU Cache（不使用 LinkedHashMap）**

```java
public class ManualLRUCache<K, V> {
    
    // 双向链表节点
    class Node {
        K key;
        V value;
        Node prev, next;
        
        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }
    
    private final int capacity;
    private final Map<K, Node> map;
    private final Node head, tail;  // 虚拟头尾节点
    
    public ManualLRUCache(int capacity) {
        this.capacity = capacity;
        this.map = new HashMap<>();
        
        // 初始化虚拟头尾节点
        this.head = new Node(null, null);
        this.tail = new Node(null, null);
        head.next = tail;
        tail.prev = head;
    }
    
    /**
     * 获取元素
     */
    public V get(K key) {
        if (!map.containsKey(key)) {
            return null;
        }
        
        Node node = map.get(key);
        moveToTail(node);  // 移到末尾（最近使用）
        return node.value;
    }
    
    /**
     * 插入元素
     */
    public void put(K key, V value) {
        if (map.containsKey(key)) {
            // 已存在，更新值并移到末尾
            Node node = map.get(key);
            node.value = value;
            moveToTail(node);
        } else {
            // 新元素
            if (map.size() == capacity) {
                // 缓存满，淘汰头部元素（最久未使用）
                Node eldest = head.next;
                removeNode(eldest);
                map.remove(eldest.key);
            }
            
            // 插入新节点到末尾
            Node node = new Node(key, value);
            map.put(key, node);
            addToTail(node);
        }
    }
    
    /**
     * 将节点移到链表末尾
     */
    private void moveToTail(Node node) {
        removeNode(node);
        addToTail(node);
    }
    
    /**
     * 从链表中删除节点
     */
    private void removeNode(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }
    
    /**
     * 将节点添加到链表末尾
     */
    private void addToTail(Node node) {
        node.prev = tail.prev;
        node.next = tail;
        tail.prev.next = node;
        tail.prev = node;
    }
}
```

**为什么手动实现复杂**：
```
1. 需要维护双向链表的所有操作
2. 容易出错（指针操作）
3. LinkedHashMap 已经封装好了这些逻辑
4. 实际项目中推荐使用 LinkedHashMap
```

---

#### LinkedHashMap 的底层原理

**accessOrder 的实现**：

```java
// get 方法会调用 afterNodeAccess
public V get(Object key) {
    Node<K,V> e;
    if ((e = getNode(hash(key), key)) == null)
        return null;
    if (accessOrder)
        afterNodeAccess(e);  // 将节点移到链表末尾
    return e.value;
}

void afterNodeAccess(Node<K,V> e) {
    if (accessOrder && (last = tail) != e) {
        // 将 e 从原位置删除
        (p = (b = e).before).after = a;
        if (a != null)
            a.before = p;
        else
            last = p;
        
        // 将 e 插入到末尾
        if (last == null)
            first = e;
        else {
            e.before = last;
            last.after = e;
        }
        tail = e;
        ++modCount;
    }
}
```

**put 方法的淘汰逻辑**：

```java
public V put(K key, V value) {
    return putVal(key, value, false);
}

final V putVal(K key, V value, boolean onlyIfAbsent) {
    // ... HashMap 的插入逻辑
    
    // 插入后检查是否需要删除最老元素
    if (removeEldestEntry(eldest)) {
        removeNode(eldest.key, null, false, true, true);
    }
}

// 子类重写这个方法
protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
    return false;  // LinkedHashMap 默认不删除
}

// LRUCache 重写
@Override
protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
    return size() > capacity;  // 超过容量时删除
}
```

---

## 第一周重点知识梳理

### Day 01-06 核心考点总结

#### 1. 集合框架体系

```
Collection（接口）
├── List（接口）
│   ├── ArrayList（数组，随机访问快）
│   ├── LinkedList（双向链表，插入删除快）
│   └── Vector（线程安全，已淘汰）
│
├── Set（接口）
│   ├── HashSet（基于 HashMap）
│   ├── LinkedHashSet（基于 LinkedHashMap）
│   └── TreeSet（基于 TreeMap，有序）
│
└── Queue（接口）
    ├── PriorityQueue（优先队列）
    ├── ArrayBlockingQueue（阻塞队列）
    └── LinkedBlockingQueue（阻塞队列）

Map（接口）
├── HashMap（数组 + 链表/红黑树）
├── LinkedHashMap（HashMap + 双向链表）
├── TreeMap（红黑树，有序）
├── Hashtable（线程安全，已淘汰）
└── ConcurrentHashMap（线程安全，高并发）
```

---

#### 2. HashMap 核心考点

| 考点 | JDK 1.7 | JDK 1.8 |
|------|---------|---------|
| 数据结构 | 数组 + 链表 | 数组 + 链表 + 红黑树 |
| 插入方式 | 头插法 | 尾插法 |
| 死循环问题 | 有 | 无 |
| hash 计算 | 4 次位移 + 5 次异或 | 1 次位移 + 1 次异或 |
| 扩容时机 | 插入前检查 | 插入后检查 |
| 线程安全 | 否 | 否 |

---

#### 3. 并发容器对比

| 容器 | 锁机制 | 并发度 | 适用场景 |
|------|--------|--------|---------|
| Hashtable | synchronized 全表锁 | 1 | 不推荐使用 |
| Collections.synchronizedMap | synchronized 全表锁 | 1 | 低并发 |
| ConcurrentHashMap 1.7 | 分段锁（Segment） | 16 | 中并发 |
| ConcurrentHashMap 1.8 | CAS + synchronized | 数组长度 | 高并发 |

---

#### 4. 性能对比

| 操作 | ArrayList | LinkedList | HashMap |
|------|-----------|------------|---------|
| 查找 | O(1) | O(n) | O(1) |
| 插入（末尾） | O(1) | O(1) | O(1) |
| 插入（中间） | O(n) | O(1) | O(1) |
| 删除 | O(n) | O(1) | O(1) |

---

## 面试加分技巧

### LRU Cache 回答模板

**面试官**：如何实现 LRU Cache？

**回答结构**：
1. **直接回答**："使用 LinkedHashMap，设置 accessOrder=true，并重写 removeEldestEntry 方法"
2. **解释原理**："LinkedHashMap 内部维护了双向链表，accessOrder=true 时会按访问顺序排序，最近访问的元素在末尾"
3. **代码示例**："只需继承 LinkedHashMap，3 行代码即可实现"
4. **拓展延伸**："如果手动实现，需要 HashMap + 双向链表，get/put 时间复杂度都是 O(1)"

### 第一周复习建议

1. **重点掌握**：
   - HashMap 的底层原理和扩容机制
   - ConcurrentHashMap 的并发控制
   - ArrayList vs LinkedList 的使用场景

2. **理解透彻**：
   - equals 和 hashCode 的契约关系
   - 泛型擦除的原理
   - 动态代理的实现差异

3. **动手实践**：
   - 手写 LRU Cache
   - 实现简单的 HashMap
   - 使用动态代理实现 AOP

---

## 深入学习建议

1. **阅读源码**：
   - `java.util.LinkedHashMap` - 重点看 accessOrder 实现
   - `java.util.LinkedList` - 双向链表操作
   - `java.util.TreeMap` - 红黑树实现

2. **实践练习**：
   - 实现一个支持过期时间的 Cache
   - 实现 LFU（最不经常使用）Cache
   - 对比不同 Cache 淘汰策略的性能

3. **扩展阅读**：
   - 《算法导论》- 缓存淘汰算法
   - Redis 的 LRU 实现（近似 LRU）
   - Guava Cache 的实现原理
