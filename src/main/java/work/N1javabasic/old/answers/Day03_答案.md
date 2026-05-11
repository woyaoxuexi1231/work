# Day 03: HashMap 源码地狱级解析 - 完整答案

## 面试真题连环炮 - 详细解答

### 1. 1.7 的死循环是怎么产生的？1.8 是如何解决的？

#### 核心原理

**一句话总结**：JDK 1.7 使用头插法导致链表反转，多线程并发扩容时形成环形链表；JDK 1.8 改用尾插法避免链表反转，从而解决死循环问题。

#### 详细分析

**JDK 1.7 死循环产生的完整过程**：

**1. 背景知识：头插法**
```java
// JDK 1.7 的 transfer 方法（数据迁移）
void transfer(Entry[] newTable, boolean rehash) {
    int newCapacity = newTable.length;
    for (Entry<K,V> e : table) {
        while(null != e) {
            Entry<K,V> next = e.next;  // 保存下一个节点
            
            // 头插法：新节点插入链表头部
            e.next = newTable[i];  // 新节点的 next 指向原来的头节点
            newTable[i] = e;       // 新节点成为头节点
            
            e = next;  // 处理下一个节点
        }
    }
}
```

**2. 死循环产生的时间线**

假设初始状态：
- HashMap 容量 = 2，负载因子 = 0.75
- 阈值 = 2 × 0.75 = 1.5（取整为 1）
- 当前有 2 个元素：Node-A 和 Node-B，都映射到索引 0
- 旧链表：Node-B → Node-A → null（头插法插入顺序）

**并发扩容过程**：

```
时间线                    线程1                              线程2
─────────────────────────────────────────────────────────────────────
T1:  开始扩容
     遍历旧数组 table[0]
     链表：Node-B → Node-A
     
T2:  读取第一个节点
     e = Node-B
     next = Node-A
     
T3:  迁移 Node-B
     头插法：newTable[0] = Node-B
     准备处理下一个节点
     e = next = Node-A
     
T4:  线程1 被挂起！                                              
     局部变量保存：
     e = Node-A
     next = null（Node-A.next）
     
T5:                                     线程2 开始扩容
                                        遍历旧数组 table[0]
                                        e = Node-B, next = Node-A
                                        
T6:                                     迁移 Node-B
                                        newTable[0] = Node-B
                                        e = Node-A, next = null
                                        
T7:                                     迁移 Node-A
                                        newTable[0] = Node-A
                                        Node-A.next = Node-B（头插法）
                                        链表：Node-A → Node-B → null
                                        
T8:                                     扩容完成
                                        table = newTable
                                        旧数组被回收

T9:  线程1 恢复执行
     继续使用旧的局部变量
     e = Node-A（注意：这个 Node-A
        已经在线程2的新数组中
        且 Node-A.next = Node-B）
     
T10: 迁移 Node-A（第二次！）
      头插法：
      newTable[0] = Node-A
      Node-A.next = 原来的头节点
                  = Node-B
      
T11: 处理下一个节点
      e = next = null（旧引用）
      但新数组中：
      Node-A.next = Node-B
      Node-B.next = Node-A（形成环！）
      
      环形链表：
      Node-A → Node-B → Node-A → ...
```

**3. 死循环触发**
```java
// 后续调用 get 方法
public V get(Object key) {
    int hash = hash(key.hashCode());
    int index = indexFor(hash, table.length);
    
    for (Entry<K,V> e = table[index]; e != null; e = e.next) {
        // 如果 table[index] 是环形链表，这里会无限循环！
        if (e.hash == hash && equals(e.key, key)) {
            return e.value;
        }
    }
    return null;
}
```

**4. 为什么叫"死循环"而不是"死锁"？**
- 死循环：CPU 无限循环，占用率飙升到 100%
- 死锁：多个线程互相等待，程序卡住但不占用 CPU

---

#### JDK 1.8 的解决方案

**核心改进：改用尾插法**

```java
// JDK 1.8 的 transfer 方法（简化版）
final Node<K,V>[] resize() {
    Node<K,V>[] oldTab = table;
    Node<K,V>[] newTab = (Node<K,V>[]) new Node[newCap];
    table = newTab;
    
    if (oldTab != null) {
        for (int j = 0; j < oldCap; ++j) {
            Node<K,V> e;
            if ((e = oldTab[j]) != null) {
                oldTab[j] = null;
                
                if (e.next == null) {
                    // 只有一个节点，直接计算新位置
                    newTab[e.hash & (newCap - 1)] = e;
                } else if (e instanceof TreeNode) {
                    // 红黑树拆分
                    ((TreeNode<K,V>)e).split(this, newTab, j, oldCap);
                } else {
                    // 链表拆分：使用尾插法
                    Node<K,V> loHead = null, loTail = null; // 低位桶
                    Node<K,V> hiHead = null, hiTail = null; // 高位桶
                    Node<K,V> next;
                    
                    do {
                        next = e.next;
                        
                        // 判断元素去低位桶还是高位桶
                        if ((e.hash & oldCap) == 0) {
                            // 低位桶：保持在原索引
                            if (loTail == null)
                                loHead = e;
                            else
                                loTail.next = e;  // 尾插法！
                            loTail = e;
                        } else {
                            // 高位桶：索引 + oldCap
                            if (hiTail == null)
                                hiHead = e;
                            else
                                hiTail.next = e;  // 尾插法！
                            hiTail = e;
                        }
                    } while ((e = next) != null);
                    
                    // 设置链表尾部为 null
                    if (loTail != null) {
                        loTail.next = null;
                        newTab[j] = loHead;
                    }
                    if (hiTail != null) {
                        hiTail.next = null;
                        newTab[j + oldCap] = hiHead;
                    }
                }
            }
        }
    }
    return newTab;
}
```

**尾插法 vs 头插法对比**：

| 特性 | 头插法（JDK 1.7） | 尾插法（JDK 1.8） |
|------|------------------|------------------|
| 插入位置 | 链表头部 | 链表尾部 |
| 链表顺序 | 反转 | 保持原顺序 |
| 多线程扩容 | 可能形成环形链表 | 不会形成环形链表 |
| 性能 | 稍快（无需遍历到尾部） | 稍慢（需要维护 tail 指针） |

**为什么尾插法不会死循环**：
```
扩容前：Node-A → Node-B → null

线程1 和线程2 并发扩容，都使用尾插法：

线程1：
  loHead = Node-A
  loTail = Node-A
  loTail.next = Node-B
  loTail = Node-B
  结果：Node-A → Node-B → null

线程2：
  即使同时执行，也是按顺序追加
  结果：Node-A → Node-B → null

不会产生环形链表！
```

---

### 2. 为什么负载因子默认是 0.75？为什么红黑树转换阈值是 8？

#### 负载因子 0.75 的数学原理

**核心原理**：0.75 是时间和空间成本的折中，基于泊松分布的统计学计算。

**详细分析**：

**1. HashMap 的负载因子定义**
```java
public class HashMap<K,V> {
    static final float DEFAULT_LOAD_FACTOR = 0.75f; // 默认负载因子
    
    int threshold;  // 扩容阈值
    int size;       // 当前元素数量
    
    // 扩容阈值 = 容量 × 负载因子
    threshold = capacity * loadFactor;
}
```

**2. 为什么不是其他值？**

| 负载因子 | 空间利用率 | 哈希冲突概率 | 查询性能 | 评价 |
|---------|-----------|-------------|---------|------|
| 0.5 | 50% | 很低 | O(1) | 浪费空间 |
| **0.75** | **75%** | **较低** | **O(1)** | **最佳平衡** |
| 1.0 | 100% | 较高 | O(log n) | 性能下降 |
| 2.0 | 200% | 很高 | O(n) | 严重退化 |

**3. 泊松分布的数学证明**

HashMap 的哈希函数理想情况下应该均匀分布，元素落入每个桶的概率服从泊松分布：

```
P(k) = (e^(-λ) * λ^k) / k!

其中：
- λ = 负载因子（平均每个桶的元素数量）
- k = 某个桶中的元素数量
- e ≈ 2.71828（自然常数）
```

**当 λ = 0.75 时**：
```
P(0) = e^(-0.75) * 0.75^0 / 0! ≈ 0.472    // 47.2% 的桶为空
P(1) = e^(-0.75) * 0.75^1 / 1! ≈ 0.354    // 35.4% 的桶有 1 个元素
P(2) = e^(-0.75) * 0.75^2 / 2! ≈ 0.133    // 13.3% 的桶有 2 个元素
P(3) = e^(-0.75) * 0.75^3 / 3! ≈ 0.033    // 3.3% 的桶有 3 个元素
P(4) = e^(-0.75) * 0.75^4 / 4! ≈ 0.006    // 0.6% 的桶有 4 个元素
P(5) = e^(-0.75) * 0.75^5 / 5! ≈ 0.001    // 0.1% 的桶有 5 个元素
P(6) = e^(-0.75) * 0.75^6 / 6! ≈ 0.0001   // 0.01% 的桶有 6 个元素
P(7) = e^(-0.75) * 0.75^7 / 7! ≈ 0.00001  // 0.001% 的桶有 7 个元素
P(8) = e^(-0.75) * 0.75^8 / 8! ≈ 0.000001 // 0.0001% 的桶有 8 个元素
```

**关键结论**：
- 当负载因子 = 0.75 时，链表长度达到 8 的概率约为 **0.000001**（百万分之一）
- 这意味着在正常情况下，几乎不会出现长度为 8 的链表
- 如果出现了，说明哈希函数设计有问题或遭遇哈希碰撞攻击

**4. JDK 源码中的注释**
```java
/**
 * Ideally, under random hashCodes, the frequency of
 * nodes in bins follows a Poisson distribution
 * (http://en.wikipedia.org/wiki/Poisson_distribution) with a
 * parameter of about 0.5 on average for the default resizing
 * threshold of 0.75, although with a large variance because of
 * resizing granularity. Ignoring variance, the expected
 * occurrences of list size k are (exp(-0.5) * pow(0.5, k) / k!):
 *
 * 0:    0.60653066
 * 1:    0.30326533
 * 2:    0.07581633
 * 3:    0.01263606
 * 4:    0.00157952
 * 5:    0.00015795
 * 6:    0.00001316
 * 7:    0.00000094
 * 8:    0.00000006
 * more: less than 1 in ten million
 */
```

---

#### 红黑树转换阈值 8 的统计学依据

**为什么选择 8**：

**1. 统计学依据**
```
根据上面的泊松分布计算：
- 链表长度达到 8 的概率：0.00000006（千万分之六）
- 这个概率极小，说明如果出现长度为 8 的链表，
  几乎可以确定是异常情况（哈希冲突严重）
```

**2. 时间复杂度对比**

| 数据结构 | 时间复杂度 | 链表长度 8 时的比较次数 |
|---------|-----------|----------------------|
| 链表 | O(n) | 平均 4 次 |
| 红黑树 | O(log n) | 平均 3 次 |

**3. 为什么不是 6 或 10**：

```
阈值太小（如 6）：
  - 过早转换为红黑树
  - 红黑树节点占用空间是链表节点的 2 倍
  - 浪费内存

阈值太大（如 10）：
  - 链表过长，查询性能下降
  - 最坏情况需要比较 10 次
  
阈值 = 8：
  - 兼顾时间和空间
  - 概率极低（千万分之六）
  - 符合"异常检测"的设计理念
```

**4. 回退阈值为什么是 6**：
```java
static final int UNTREEIFY_THRESHOLD = 6; // 回退阈值

// 原因：避免频繁转换
// 如果阈值都是 8，会出现：
//   添加元素：7 → 8（转红黑树）
//   删除元素：8 → 7（转链表）
//   添加元素：7 → 8（转红黑树）
//   ... 频繁转换，性能差

// 使用 6 作为回退阈值，提供缓冲区间：
//   8 转红黑树，6 才转回链表
//   避免频繁的树化/反树化操作
```

---

### 3. 扰动函数（hash 方法）的作用是什么？为什么数组长度要是 2 的幂次方？

#### 扰动函数的作用

**一句话总结**：扰动函数通过高低位异或运算，让哈希值的高位也参与到索引计算中，减少哈希冲突。

**详细分析**：

**1. 问题背景**

HashMap 计算数组索引的公式：
```java
index = hash & (n - 1)  // n 是数组长度
```

如果数组长度 n 较小（如 16），那么 `n - 1 = 15（二进制 1111）`，只有低 4 位参与运算。

**问题**：如果对象的 `hashCode()` 方法实现不好，只改变了高位，低位都相同，会导致所有元素映射到同一个桶！

**示例**：
```java
class BadHashCode {
    private int id;
    
    @Override
    public int hashCode() {
        return id << 16;  // 糟糕的实现，只改变高 16 位
    }
}

// 假设 n = 16，n - 1 = 15（0000 0000 0000 1111）
BadHashCode obj1 = new BadHashCode(1);  // hashCode = 0x00010000
BadHashCode obj2 = new BadHashCode(2);  // hashCode = 0x00020000
BadHashCode obj3 = new BadHashCode(3);  // hashCode = 0x00030000

// 计算索引：
index1 = 0x00010000 & 15 = 0
index2 = 0x00020000 & 15 = 0
index3 = 0x00030000 & 15 = 0

// 全部映射到索引 0！严重哈希冲突！
```

**2. 扰动函数的解决方案**

```java
// JDK 1.8 的 hash 方法
static final int hash(Object key) {
    int h;
    // 高低位异或：让高位也参与运算
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}
```

**工作原理**：
```
假设 hashCode = 0x00010000（二进制）
           = 0000 0000 0000 0001 0000 0000 0000 0000

步骤 1：h >>> 16（无符号右移 16 位）
           = 0000 0000 0000 0000 0000 0000 0000 0001

步骤 2：h ^ (h >>> 16)（异或运算）
  0000 0000 0000 0001 0000 0000 0000 0000
^ 0000 0000 0000 0000 0000 0000 0000 0001
= 0000 0000 0000 0001 0000 0000 0000 0001

步骤 3：index = hash & 15
  0000 0000 0000 0001 0000 0000 0000 0001
& 0000 0000 0000 0000 0000 0000 0000 1111
= 0000 0000 0000 0000 0000 0000 0000 0001
= 1

结果：索引 = 1，而不是 0！
```

**3. 扰动函数的效果**

```
没有扰动函数：
  obj1 → index 0
  obj2 → index 0  （冲突）
  obj3 → index 0  （冲突）

有扰动函数：
  obj1 → index 1
  obj2 → index 2
  obj3 → index 3
  （均匀分布）
```

**4. JDK 1.7 vs 1.8 的扰动函数**

```java
// JDK 1.7：4 次位移 + 5 次异或（更复杂）
final int hash(Object k) {
    int h = hashSeed;
    h ^= k.hashCode();
    h ^= (h >>> 20) ^ (h >>> 12);
    return h ^ (h >>> 7) ^ (h >>> 4);
}

// JDK 1.8：1 次位移 + 1 次异或（简化）
static final int hash(Object key) {
    int h;
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}

// 为什么简化？
// - JDK 1.7 的复杂扰动函数是为了解决当时 hashCode() 实现不好的问题
// - 现代 Java 类的 hashCode() 实现质量提高
// - 简化扰动函数提升性能（减少 8 次运算）
```

---

#### 数组长度为 2 的幂次方的原因

**一句话总结**：2 的幂次方可以使用位运算 `&` 替代取模运算 `%`，大幅提升性能，同时配合扰动函数保证均匀分布。

**详细分析**：

**1. 位运算优化**

```java
// 如果 n 是 2 的幂次方（如 16 = 2^4）
n = 16;          // 二进制：0001 0000
n - 1 = 15;      // 二进制：0000 1111

// 计算索引：
index = hash & (n - 1)

// 等价于：
index = hash % n

// 但位运算 & 比取模 % 快得多！
// & 只需要 1 个 CPU 周期
// % 需要 10-20 个 CPU 周期（除法运算）
```

**为什么 `& (n-1)` 等价于 `% n`？**

```
前提：n 必须是 2 的幂次方

示例：hash = 12345, n = 16

12345 % 16 = 12345 / 16 的余数 = 9

12345 的二进制：1100 0000 1110 01
15 的二进制：    0000 0000 0011 11

12345 & 15：
  1100 0000 1110 01
& 0000 0000 0011 11
= 0000 0000 0010 01
= 9

结果相同！
```

**数学原理**：
```
对于任意正整数 x 和 2 的幂次方 n = 2^k：
  x % n = x & (n - 1)

证明：
  x = a * n + b  （b 是余数，0 ≤ b < n）
  n = 2^k，二进制是 1 后面跟 k 个 0
  n - 1 的二进制是 k 个 1
  
  x & (n - 1) 就是取 x 的低 k 位，正好是余数 b
```

**2. 如果数组长度不是 2 的幂次方会怎样**：

```java
// 假设 n = 15（不是 2 的幂次方）
n = 15;          // 二进制：0000 1111
n - 1 = 14;      // 二进制：0000 1110

hash1 = 10;      // 二进制：0000 1010
hash2 = 11;      // 二进制：0000 1011

// 使用 & 运算：
index1 = 10 & 14 = 0000 1010 = 10
index2 = 11 & 14 = 0000 1010 = 10  // 冲突！

// 使用 % 运算：
index1 = 10 % 15 = 10
index2 = 11 % 15 = 11  // 不冲突

结论：如果 n 不是 2 的幂次方，& (n-1) 会导致大量冲突！
```

**3. HashMap 如何保证数组长度是 2 的幂次方**

```java
// 构造方法中调用 tableSizeFor
public HashMap(int initialCapacity) {
    this.threshold = tableSizeFor(initialCapacity);
}

// 将任意正整数转换为大于等于它的最小的 2 的幂次方
static final int tableSizeFor(int cap) {
    int n = cap - 1;
    n |= n >>> 1;   // 将最高位的 1 扩展到右边所有位
    n |= n >>> 2;
    n |= n >>> 4;
    n |= n >>> 8;
    n |= n >>> 16;
    return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
}

// 示例：cap = 10
n = 9;             // 0000 1001
n |= n >>> 1;      // 0000 1101
n |= n >>> 2;      // 0000 1111
n |= n >>> 4;      // 0000 1111
...
最终 n = 15;       // 0000 1111
return n + 1 = 16; // 2 的 4 次方
```

**4. 扩容时的优化**

```java
// JDK 1.8 扩容时的巧妙优化
// 扩容为原来的 2 倍：newCap = oldCap << 1

// 元素在新数组中的位置只有两种可能：
// 1. 保持原索引（低位桶）
// 2. 原索引 + oldCap（高位桶）

// 判断方法：
if ((e.hash & oldCap) == 0) {
    // 低位桶：保持原索引
    newTab[j] = e;
} else {
    // 高位桶：索引 + oldCap
    newTab[j + oldCap] = e;
}

// 示例：
oldCap = 16;  // 二进制：0001 0000
hash1 = 123;  // 二进制：0000 0111 1011
hash2 = 139;  // 二进制：0000 1000 1011

hash1 & 16 = 0;        // 低位桶，索引不变
hash2 & 16 = 16 != 0;  // 高位桶，索引 + 16

// 这种优化避免了重新计算 hash 和索引，大幅提升扩容性能！
```

---

## 代码实战解析：HashMapTest.java

### 核心要点解读

**1. HashMap 的基本使用**
```java
HashMap<String, String> hashMap = new HashMap<>();
hashMap.put("first", "first");
hashMap.put("second", "second");
```

**2. null 值的处理**
```java
// HashMap 允许 null 键和 null 值
hashMap.put(null, "null键对应的值");
hashMap.get(null);  // 返回 "null键对应的值"

// null 键固定存储在索引 0
// 查找时直接使用 hash = 0，效率 O(1)
```

**3. 线程安全问题演示**
```java
// 两个线程同时插入 10000 个元素
// 预期结果：20003 个元素（3 + 10000 + 10000）
// 实际结果：通常小于 20003，说明发生了元素丢失

// 原因：
// 1. put 操作不是原子操作
// 2. 多个线程同时计算索引，可能覆盖彼此的数据
// 3. size++ 操作不是原子的（读取-修改-写入）
```

---

## 面试加分技巧

### 回答模板

**面试官**：HashMap 1.7 的死循环是怎么产生的？

**回答结构**：
1. **直接回答**："JDK 1.7 使用头插法，在多线程并发扩容时会导致链表反转，形成环形链表"
2. **详细过程**："假设有两个线程同时扩容，线程 1 在处理 Node-A 时被挂起，线程 2 完成了扩容并将 Node-A 的 next 指向 Node-B。线程 1 恢复后继续使用旧的引用，再次插入 Node-A，最终形成 Node-A → Node-B → Node-A 的环形链表"
3. **解决方案**："JDK 1.8 改用尾插法，保持链表的原始顺序，避免了环形链表的产生"
4. **拓展延伸**："虽然 JDK 1.8 解决了死循环问题，但 HashMap 仍然不是线程安全的，在并发环境下应该使用 ConcurrentHashMap"

### 常见错误回答

❌ **错误 1**："HashMap 1.8 是线程安全的"
✅ **正确**：JDK 1.8 只是解决了死循环问题，但仍然存在数据覆盖、size 不准确等线程安全问题。

❌ **错误 2**："负载因子 0.75 是随便定的"
✅ **正确**：0.75 是基于泊松分布的数学计算，是时间和空间成本的最佳平衡点。

❌ **错误 3**："数组长度是 2 的幂次方只是为了位运算快"
✅ **正确**：除了位运算优化，还配合扰动函数保证哈希值的均匀分布，避免冲突。

---

## 深入学习建议

1. **阅读源码**：
   - `java.util.HashMap` - 重点看 put、get、resize、treeifyBin 方法
   - `java.util.TreeNode` - 红黑树实现
   - `java.lang.Integer.hashCode()` - 好的 hashCode 实现示例

2. **实践练习**：
   - 手写一个简单的 HashMap（数组 + 链表）
   - 实现一个自定义的 hashCode 方法并测试冲突率
   - 使用 JMH 测试不同负载因子下的性能差异

3. **扩展阅读**：
   - 《算法导论》- 散列表章节
   - 《数据结构与算法分析》- 红黑树章节
   - HashMap 源码解析博客（推荐）
