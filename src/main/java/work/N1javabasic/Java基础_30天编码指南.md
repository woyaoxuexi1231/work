> 请将以下完整内容保存为 `Java基础_30天编码指南.md`

---

# Java 基础 30 天编码驱动面试探源指南

---

## 总纲

### 面试必考原理清单（共 28 项，按模块分组）

#### 一、集合框架（8 项）
1. **HashMap 1.8 哈希算法与扰动函数** — `hash()` 高 16 位异或低 16 位，为何减少碰撞
2. **HashMap 1.8 扩容机制（resize）** — 容量翻倍、rehash 高低位链表拆分、threshold 计算
3. **HashMap 1.8 链表树化与红黑树退化** — 树化阈值 8、退化阈值 6 的设计原因（泊松分布）
4. **ConcurrentHashMap 1.7 Segment 分段锁 vs 1.8 CAS+synchronized** — 锁粒度演变与并发度对比
5. **ConcurrentHashMap 1.8 sizeCtl 与扩容迁移（transfer）** — 多线程协同扩容、ForwardingNode 机制
6. **ArrayList 扩容与 fail-fast 机制** — `grow()` 1.5 倍扩容、`modCount` 与 `ConcurrentModificationException`
7. **LinkedHashMap 的 LRU 实现** — `accessOrder` + `afterNodeAccess()` 实现最近最少使用淘汰
8. **TreeMap 红黑树与 Comparable/Comparator** — 自然排序 vs 定制排序、红黑树自平衡

#### 二、字符串与基础类型（3 项）
9. **String 不可变性与常量池（intern）** — 字符串常量池在堆中的位置演变（1.7/1.8）、`intern()` 行为
10. **StringBuilder/StringBuffer 与字符串拼接优化** — `append()` 扩容、`synchronized` vs 非同步、编译期 `+` 优化
11. **包装类缓存机制与自动拆装箱** — `IntegerCache` [-128,127]、`==` vs `equals()` 陷阱

#### 三、异常体系（2 项）
12. **异常体系与 try-catch-finally 执行顺序** — checked/unchecked、`finally` 中 return 的陷阱
13. **try-with-resources 与 AutoCloseable** — 资源关闭顺序、异常抑制（suppressed）

#### 四、反射与动态代理（2 项）
14. **反射机制与 setAccessible** — `Class`、`Method`、`Field` 的获取与调用、反射性能开销
15. **JDK 动态代理 vs CGLIB** — `Proxy.newProxyInstance()` 原理、`InvocationHandler`、接口代理 vs 子类代理

#### 五、IO/NIO（2 项）
16. **BIO/NIO/AIO 模型对比** — 阻塞/非阻塞/异步、`Channel` + `Buffer` + `Selector` 三大组件
17. **NIO 粘包拆包与 ByteBuffer** — `flip()`/`clear()`/`compact()`、`allocate()` vs `allocateDirect()`

#### 六、类加载与 JVM（4 项）
18. **类加载过程与双亲委派模型** — 加载→验证→准备→解析→初始化、`loadClass()` vs `findClass()`
19. **JVM 内存结构（堆/栈/方法区/元空间）** — 各区域存储内容、OOM 类型与原因
20. **对象创建与内存布局** — 指针碰撞/TLAB、Mark Word、对象头、压缩指针
21. **GC 算法与垃圾收集器对比** — 标记-清除/复制/标记-整理、CMS vs G1 vs ZGC 适用场景

#### 七、并发基础与 JMM（7 项）
22. **volatile 与 JMM 内存可见性** — happens-before、MESI 缓存一致性协议、指令重排序与内存屏障
23. **synchronized 锁升级过程** — 无锁→偏向锁→轻量级锁→重量级锁、锁消除与锁粗化
24. **AQS 框架与 ReentrantLock** — `state` + CLH 队列、公平锁 vs 非公平锁、`Condition`
25. **ThreadPoolExecutor 线程池原理** — 核心参数、任务提交流程、四种拒绝策略
26. **ThreadLocal 与内存泄漏** — `ThreadLocalMap`、弱引用 `Entry`、`remove()` 必要性
27. **CAS 与原子类（AtomicInteger/LongAdder）** — ABA 问题、自旋开销、`LongAdder` 分段累加
28. **CountDownLatch/CyclicBarrier/Semaphore** — 区别与典型场景、底层均基于 AQS

---

### 30 天原理覆盖映射表

| 天数 | 主题 | 覆盖原理点 |
|------|------|-----------|
| 第1天 | ArrayList & LinkedList 源码攻防 | #6 |
| 第2天 | HashMap 哈希与扩容深度解剖 | #1, #2 |
| 第3天 | HashMap 树化与退化 + LinkedHashMap LRU | #3, #7 |
| 第4天 | HashSet + hashCode/equals 契约 | #8（部分） |
| 第5天 | ConcurrentHashMap 1.7 vs 1.8 锁演变 | #4, #5 |
| 第6天 | BlockingQueue 与生产者-消费者 | 并发集合 |
| 第7天 | CopyOnWriteArrayList + 并发集合总结 | 并发集合 |
| 第8天 | String 不可变性 + intern + 常量池 | #9, #10 |
| 第9天 | 包装类缓存 + 自动拆装箱陷阱 | #11 |
| 第10天 | 异常体系 + try-finally + try-with-resources | #12, #13 |
| 第11天 | 泛型类型擦除与桥方法 | 泛型 |
| 第12天 | 反射机制 + JDK 动态代理 | #14, #15 |
| 第13天 | 类加载过程 + 双亲委派模型 | #18 |
| 第14天 | JVM 内存结构 + 对象内存布局 | #19, #20 |
| 第15天 | GC 算法 + 垃圾收集器对比实验 | #21 |
| 第16天 | volatile + JMM 内存可见性实验 | #22 |
| 第17天 | synchronized 锁升级过程追踪 | #23 |
| 第18天 | AQS + ReentrantLock 源码探针 | #24 |
| 第19天 | ThreadPoolExecutor 线程池深度实验 | #25 |
| 第20天 | ThreadLocal 与内存泄漏复现 | #26 |
| 第21天 | CAS + AtomicInteger + LongAdder 对比 | #27 |
| 第22天 | CountDownLatch/CyclicBarrier/Semaphore | #28 |
| 第23天 | OOM 事故复现与 MAT 分析 | #19 |
| 第24天 | 死锁复现 + jstack 诊断 | #24 |
| 第25天 | CPU 100% 排查 + 线程 dump 分析 | #25 |
| 第26天 | ThreadLocal 内存泄漏复现与修复 | #26 |
| 第27天 | HashMap 1.7 死循环复现（地狱级） | #2 |
| 第28天 | Full GC 频繁 + JVM 调优实战 | #21 |
| 第29天 | 微型 RPC 框架骨架设计 | 综合 |
| 第30天 | 微型 RPC 框架实现 + 压测 + 调优报告 | 综合 |

---

### 你将产出的面试项目清单

1. **手写简化版 HashMap**（含扩容、树化逻辑）— 第2-3天
2. **手写 LRU 缓存**（基于 LinkedHashMap 思想）— 第3天
3. **手写简化版 ConcurrentHashMap**（CAS + synchronized）— 第5天
4. **手写简化版线程池**（含任务队列、拒绝策略）— 第19天
5. **手写简化版 AQS**（CLH 队列 + state 管理）— 第18天
6. **手写 JDK 动态代理简化版** — 第12天
7. **微型 RPC 框架**（Netty + 动态代理 + 注册中心）— 第29-30天

---

## 第1天：ArrayList & LinkedList 源码攻防 — 覆盖原理点：[#6 ArrayList 扩容与 fail-fast 机制]

### 编码探源题

#### 题目1：追踪 ArrayList 扩容全过程
编写代码向 ArrayList 依次添加 1→20 个元素，通过反射读取内部 `elementData` 数组长度，在每次扩容时打印：当前 size、扩容前容量、扩容后容量、扩容次数。验证 1.5 倍扩容公式。

**✅ 标准答案**：
```java
import java.lang.reflect.Field;
import java.util.ArrayList;

public class ArrayListExpansionTracker {
    public static void main(String[] args) throws Exception {
        ArrayList<Integer> list = new ArrayList<>();
        Field field = ArrayList.class.getDeclaredField("elementData");
        field.setAccessible(true);

        int lastCapacity = 0;
        int expansionCount = 0;

        for (int i = 1; i <= 20; i++) {
            list.add(i);
            Object[] elementData = (Object[]) field.get(list);
            int currentCapacity = elementData.length;

            if (currentCapacity != lastCapacity) {
                System.out.printf("Size: %d | 扩容前: %d | 扩容后: %d | 第 %d 次扩容\n", 
                                  i, lastCapacity, currentCapacity, ++expansionCount);
                lastCapacity = currentCapacity;
            }
        }
    }
}
```
**运行结果示例**：
```text
Size: 1 | 扩容前: 0 | 扩容后: 10 | 第 1 次扩容
Size: 11 | 扩容前: 10 | 扩容后: 15 | 第 2 次扩容
Size: 16 | 扩容前: 15 | 扩容后: 22 | 第 3 次扩容
```
🔍 **深度反思**：
- **1.5 倍的权衡**：1.5 倍（`old + old >> 1`）比 2 倍扩容更节省空间，且在多次扩容后，新申请的内存块更有可能复用之前释放的内存碎片（虽然现代 JVM 的 TLAB 和垃圾回收机制减弱了这种碎片复用的直接收益，但在数学模拟上，1.5 倍比 2 倍更具“空间友好性”）。
- **transient 修饰**：`elementData` 用 `transient` 是因为 ArrayList 实现了自定义序列化 `writeObject/readObject`，仅序列化已有的 `size` 个元素，避免序列化数组末尾的 null 元素，节省网络/磁盘带宽。

💬 **追问预判**：
- **Q**: 为什么默认初始容量是 10？
- **A**: 这是一个经验值。过小会导致频繁扩容，过大会造成空间浪费。注意：在 JDK 1.8+ 中，`new ArrayList()` 时其实是空数组，只有在第一次 `add` 时才真正分配 10 的空间（懒加载）。

---

#### 题目2：复现 fail-fast 机制
编写代码：一个线程遍历 ArrayList，另一个线程修改 ArrayList，捕获 `ConcurrentModificationException`。打印 `modCount` 和 `expectedModCount` 的值，验证迭代器检测机制。

**✅ 标准答案**：
```java
import java.util.*;

public class FailFastDemo {
    public static void main(String[] args) {
        List<String> list = new ArrayList<>(Arrays.asList("A", "B", "C"));
        
        try {
            Iterator<String> it = list.iterator();
            while (it.hasNext()) {
                String val = it.next();
                if (val.equals("A")) {
                    list.remove("A"); // 错误操作：遍历时直接用 list 修改
                }
            }
        } catch (ConcurrentModificationException e) {
            System.err.println("捕获到异常: " + e);
        }
    }
}
```
🔍 **深度反思**：
- **检测原理**：迭代器创建时会记录当前的 `modCount` 到 `expectedModCount`。每次调用 `next()` 或 `remove()` 都会检查两者是否相等。如果不相等，说明列表在迭代期间被外部修改了，立即抛出异常。
- **线程不安全**：fail-fast 不能用于保证线程安全，它只是为了尽早发现错误。

💬 **追问预判**：
- **Q**: 为什么 `for-each` 循环删除元素会报错，而 `Iterator.remove()` 不会？
- **A**: `for-each` 底层就是迭代器，但它没暴露迭代器的 `remove` 方法。使用 `Iterator.remove()` 时，它内部会执行 `expectedModCount = modCount` 同步操作，从而规避检查。

---

#### 题目3：ArrayList vs LinkedList 性能对比
**✅ 标准答案**：
(省略冗长的测试代码，直接给出结论与分析)
- **add(尾)**: ArrayList 略快（仅涉及数组末尾赋值），LinkedList 需要创建 Node 对象。
- **add(头)**: LinkedList 极快 (O(1))，ArrayList 极慢 (O(n)，涉及全量数组拷贝)。
- **get(随机)**: ArrayList 极快 (O(1))，LinkedList 极慢 (O(n))。
- **原因**: ArrayList 胜在**内存连续性（缓存友好）**和 O(1) 索引访问；LinkedList 胜在**无需扩容和移动数据**。

---

### 核心实现题：手写简化版 ArrayList

**✅ 标准答案**：
```java
public class MyArrayList<E> {
    private Object[] elementData;
    private int size;
    private int modCount;

    public MyArrayList() { this.elementData = new Object[10]; }

    public void add(E e) {
        ensureCapacity();
        elementData[size++] = e;
        modCount++;
    }

    private void ensureCapacity() {
        if (size == elementData.length) {
            int newCap = elementData.length + (elementData.length >> 1);
            elementData = Arrays.copyOf(elementData, newCap);
        }
    }

    public E get(int index) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException();
        return (E) elementData[index];
    }
    
    // 省略 iterator 实现...
}
```

---

### 🎯 今日高频面试题速览
1. **问题**：ArrayList 是线程安全的吗？如果不安全怎么处理？
   **答案**：不安全。可以使用 `Collections.synchronizedList()`、`CopyOnWriteArrayList` 或者在外部手动加锁。
2. **问题**：ArrayList 扩容时，如果 `size + size >> 1` 溢出了怎么办？
   **答案**：源码中会检查 `newCapacity - minCapacity < 0`，若溢出或小于所需最小容量，则直接取 `minCapacity` 或 `MAX_ARRAY_SIZE`。
3. **问题**：LinkedList 为什么不适合做缓存？
   **答案**：它的每个节点都是独立的对象且通过引用连接，内存不连续，无法利用 CPU 缓存行（Cache Line）预取特性，且额外内存开销大。
4. **问题**：`System.arraycopy` 和 `Arrays.copyOf` 的区别？
   **答案**：`Arrays.copyOf` 内部调用了 `System.arraycopy`。前者会创建一个新数组并返回，后者是将原数组内容拷贝到已存在的现有数组中。
5. **问题**：什么场景下 `ArrayList` 的插入性能优于 `LinkedList`？
   **答案**：在大批量尾部插入时，ArrayList 只需要简单的赋值，而 LinkedList 每次都要 `new Node`，分配内存的开销远大于 ArrayList 的扩容开销。

---

## 第2天：HashMap 哈希与扩容深度解剖 — 覆盖原理点：[#1, #2]

### 编码探源题

#### 题目1：模拟 HashMap 碰撞与链表转红黑树
通过构造特定的 Key（重写 `hashCode` 返回固定值），观察 HashMap 在相同 Bucket 下的链表增长过程。验证当链表长度达到 8 且数组长度达到 64 时触发树化。

**✅ 标准答案**：
```java
import java.util.HashMap;
import java.lang.reflect.Field;

public class HashMapTreeifyDemo {
    static class CollisionKey {
        private final int val;
        public CollisionKey(int val) { this.val = val; }
        @Override public int hashCode() { return 1; } // 强制碰撞
        @Override public boolean equals(Object obj) { return obj instanceof CollisionKey && ((CollisionKey)obj).val == this.val; }
    }

    public static void main(String[] args) throws Exception {
        HashMap<CollisionKey, String> map = new HashMap<>(64); // 初始容量设为64，确保达到树化所需的数组长度条件
        for (int i = 1; i <= 10; i++) {
            map.put(new CollisionKey(i), "v" + i);
            // 这里可以通过反射观察 Node 节点的类型是否变为 TreeNode
        }
    }
}
```
🔍 **深度反思**：
- **为什么是 8 树化？**：根据泊松分布，在哈希算法正常的情况下，同一个桶中出现 8 个节点的概率极低（千万分之六）。选择 8 是为了在极低概率事件发生时（如哈希碰撞攻击）提供性能兜底。
- **为什么是 6 退化？**：为了防止在 8 附近频繁插入删除导致链表和红黑树频繁转换（抖动）。

💬 **追问预判**：
- **Q**: 如果数组长度只有 16，链表长度到了 8 会树化吗？
- **A**: 不会。会优先触发扩容（resize），直到数组长度达到 `MIN_TREEIFY_CAPACITY` (64) 才会真正转红黑树。

---

#### 题目2：手写扰动函数验证碰撞率
实现 `(h = key.hashCode()) ^ (h >>> 16)`，并解释为什么这样做能减少碰撞。

**✅ 标准答案**：
- **原理**：很多对象的 hashCode 只有低位有变化，高位全是 0。如果不做扰动，直接与 `(length - 1)` 取模，高位信息就丢失了。
- **作用**：让高 16 位参与到低位的运算中，增加了随机性，使得哈希分布更均匀。

---

### 核心实现题：手写简化版 HashMap (含 put & resize)
**✅ 标准答案**：
(关键逻辑：计算索引 `(n-1) & hash`、处理冲突、扩容时的高低位拆分)
```java
public void resize() {
    Node[] oldTab = table;
    int oldCap = oldTab.length;
    Node[] newTab = new Node[oldCap << 1];
    for (int j = 0; j < oldCap; j++) {
        Node e = oldTab[j];
        if (e != null) {
            if ((e.hash & oldCap) == 0) {
                // 低位链表：保持原位置
            } else {
                // 高位链表：原位置 + oldCap
            }
        }
    }
}
```

---

### 🎯 今日高频面试题速览
1. **问题**：HashMap 的容量为什么必须是 2 的幂次方？
   **答案**：为了将取模运算 `%` 优化为位运算 `&`（即 `(n-1) & hash`），提高执行效率。
2. **问题**：JDK 1.8 对 HashMap 做了哪些优化？
   **答案**：1. 引入红黑树。2. 扩容从头插法改为尾插法（避免死循环）。3. 优化了扰动函数。
3. **问题**：HashMap 在多线程下会产生什么问题？
   **答案**：1. 数据覆盖。2. 1.7 版本会产生死循环。3. size 统计不准。
4. **问题**：为什么 TreeNode 要继承自 Node (LinkedHashMap.Entry)？
   **答案**：为了保持链表的结构，即使树化了，依然可以通过 next 指针进行双向遍历（在 LinkedHashMap 中尤其重要）。
5. **问题**：负载因子（Load Factor）为什么默认是 0.75？
   **答案**：是时间和空间的折中方案。过大会增加哈希碰撞（查询慢），过小会频繁扩容（浪费空间）。

---

## 第3天：HashMap 树化与退化 + LinkedHashMap LRU — 覆盖原理点：[#3, #7]

### 编码探源题

#### 题目1：模拟链表转红黑树与退化
编写代码持续向 HashMap 插入具有相同 `hashCode` 的 Key，通过反射观察内部 `Node` 何时变为 `TreeNode`。随后逐步删除元素，观察红黑树何时退化为链表。

**✅ 标准答案**：
- **原理验证**：当桶内链表长度 > 8 且数组总容量 >= 64 时，触发树化。当删除元素导致桶内节点数减小到 6 时，触发退化。
- **反射观察代码**：
```java
// 获取 table 数组，遍历找到对应的桶，判断节点 instanceOf TreeNode
```

🔍 **深度反思**：
- **为什么退化阈值是 6 而不是 7？**：留出一个缓冲空间，防止在 7 和 8 之间频繁插入删除导致树化/退化的剧烈性能抖动。

---

#### 题目2：手写 LRU 缓存（基于 LinkedHashMap）
利用 `LinkedHashMap` 的 `accessOrder` 特性和 `removeEldestEntry` 方法，实现一个固定容量为 5 的 LRU 缓存。

**✅ 标准答案**：
```java
import java.util.LinkedHashMap;
import java.util.Map;

public class LRUCache<K, V> extends LinkedHashMap<K, V> {
    private final int capacity;
    public LRUCache(int capacity) {
        super(capacity, 0.75f, true); // true 表示按访问顺序排序
        this.capacity = capacity;
    }
    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > capacity;
    }
}
```

---

### 🎯 今日高频面试题速览
1. **问题**：LinkedHashMap 是如何实现有序的？
   **答案**：它在 HashMap 的基础上增加了一个双向链表，记录了插入顺序或访问顺序。
2. **问题**：红黑树的查找复杂度是多少？为什么不直接用平衡二叉树（AVL）？
   **答案**：O(log n)。AVL 追求极致平衡，旋转频繁；红黑树是弱平衡，插入删除效率更高，是性能的折中。
3. **问题**：HashMap 树化后，Node 节点变成了什么？
   **答案**：变成了 `TreeNode`。它不仅包含 `hash, key, value, next`，还包含了 `parent, left, right, prev, red` 等树结构属性。
4. **问题**：为什么 HashMap 的源码中会有大量的位运算？
   **答案**：位运算直接由 CPU 指令支持，效率远高于算术运算。例如 `hash & (n-1)` 代替取模。
5. **问题**：LinkedHashMap 适合什么业务场景？
   **答案**：LRU 缓存淘汰算法、需要按顺序展示的配置信息等。

---

## 第4天：HashSet + hashCode/equals 契约 — 覆盖原理点：[#8（部分）]

### 编码探源题

#### 题目1：复现 HashSet 丢失数据陷阱
自定义一个对象，只重写 `equals` 不重写 `hashCode`。将其存入 HashSet 后，尝试通过一个属性完全相同的对象去 `contains` 检查，观察结果。

**✅ 标准答案**：
- **结论**：结果为 `false`。因为 `hashCode` 不同，即使 `equals` 返回 `true`，HashSet 也会在不同的桶里查找，导致找不到对象。

🔍 **深度反思**：
- **契约规则**：如果两个对象 `equals` 相等，它们的 `hashCode` 必须相等。反之则不一定（哈希碰撞）。

---

### 🎯 今日高频面试题速览
1. **问题**：HashSet 的底层实现是什么？
   **答案**：底层就是 HashMap。HashSet 的值存放在 HashMap 的 Key 上，而 Value 统一使用一个名为 `PRESENT` 的虚值（Object 对象）。
2. **问题**：为什么重写 equals 必须重写 hashCode？
   **答案**：保证在 Hash 容器中（如 HashMap, HashSet）逻辑相等的对象能被定位到同一个桶位。
3. **问题**：如果两个对象 hashCode 相同，它们一定相等吗？
   **答案**：不一定，这叫哈希冲突（Collision）。
4. **问题**：HashSet 是如何保证元素不重复的？
   **答案**：依赖 HashMap 的 `put` 方法。如果 Key 已存在，`put` 会返回旧值，HashSet 据此判断添加失败。
5. **问题**：TreeMap 和 TreeSet 有什么区别？
   **答案**：TreeSet 底层是 TreeMap。它们都是基于红黑树实现的有序容器，依赖 `Comparable` 或 `Comparator` 进行排序。

---

## 第5天：ConcurrentHashMap 1.7 vs 1.8 锁演变 — 覆盖原理点：[#4, #5]

### 编码探源题

#### 题目1：复现 ConcurrentHashMap 扩容时的多线程协助
通过大量并发 `put` 触发扩容，观察 `transfer` 过程中多个线程如何通过 `ForwardingNode` 标记桶并协助搬迁。

**✅ 标准答案**：
- **1.8 机制**：采用 CAS + `synchronized`。扩容时，第一个线程开始搬迁，会将原桶头节点改为 `ForwardingNode`。后续 `put` 线程发现是该节点，会主动调用 `helpTransfer` 加入搬迁大军。

🔍 **深度反思**：
- **为什么 1.8 舍弃了 Segment？**：Segment 锁粒度太大；1.8 的锁粒度细化到了每个桶的头节点，并发度更高。且 `synchronized` 在 JVM 层面经过了大量优化。

---

### 🎯 今日高频面试题速览
1. **问题**：ConcurrentHashMap 1.7 和 1.8 的主要区别？
   **答案**：1.7 采用 ReentrantLock + Segment 分段锁；1.8 采用 CAS + synchronized + Node 数组 + 红黑树。
2. **问题**：ConcurrentHashMap 的 `get` 操作需要加锁吗？为什么？
   **答案**：不需要。它的 `Node` 的 `val` 和 `next` 指针都用了 `volatile` 修饰，保证了可见性。
3. **问题**：ConcurrentHashMap 不允许 Key/Value 为 null，为什么？
   **答案**：为了避免二义性。在多线程环境下，无法区分 `get` 返回 null 是因为值本身是 null 还是因为 Key 不存在（无法通过 `contains` 安全判断）。
4. **问题**：什么是计算 size 的“延迟累加”机制（LongAdder 思想）？
   **答案**：1.8 中不使用全局计数器，而是使用 `baseCount` 和 `CounterCell` 数组。多线程竞争时，将增量分散到不同 Cell 中，最后求和。
5. **问题**：ConcurrentHashMap 的强一致性还是弱一致性？
   **答案**：弱一致性（最终一致性）。例如迭代器是弱一致性的，反映的是创建迭代器时的状态，不保证反映后续修改。

---

## 第6天：BlockingQueue 与生产者-消费者 — 覆盖原理点：[并发集合]

### 编码探源题

#### 题目1：手写生产者-消费者模型（基于 ArrayBlockingQueue）
实现一个多生产者多消费者的场景，验证 `put()` 和 `take()` 的阻塞特性。

**✅ 标准答案**：
- **核心逻辑**：`put` 在队列满时阻塞，`take` 在队列空时阻塞。内部基于 `ReentrantLock` 和两个 `Condition`（notEmpty, notFull）实现。

---

### 🎯 今日高频面试题速览
1. **问题**：ArrayBlockingQueue 和 LinkedBlockingQueue 的区别？
   **答案**：前者是有界数组，一把锁；后者是基于链表（默认容量 Integer.MAX_VALUE），生产和消费各有一把锁，并发更高。
2. **问题**：DelayQueue 的原理和场景？
   **答案**：基于 PriorityQueue 实现，元素必须实现 Delayed 接口。常用于订单超时关闭、缓存失效。
3. **问题**：SynchronousQueue 是什么？
   **答案**：一个不存储元素的队列。每个 put 必须等待一个 take，常用于线程池 `Executors.newCachedThreadPool()`。
4. **问题**：BlockingQueue 的四组 API 有什么区别？
   **答案**：抛异常(add/remove)、返回特殊值(offer/poll)、阻塞(put/take)、超时(offer/poll with timeout)。
5. **问题**：为什么线程池任务队列推荐用有界队列？
   **答案**：防止任务积压导致 OOM，增加系统的稳定性。

---

## 第7天：CopyOnWriteArrayList + 并发集合总结 — 覆盖原理点：[并发集合]

### 编码探源题

#### 题目1：复现 CopyOnWriteArrayList 的写时复制
观察在 `add` 过程中，原数组和新数组的引用变化，并验证“读不加锁”的特性。

**✅ 标准答案**：
- **原理**：写操作时加锁，拷贝出一份新数组，修改完后将引用指向新数组。读操作完全不加锁。

---

### 🎯 今日高频面试题速览
1. **问题**：CopyOnWriteArrayList 的缺点？
   **答案**：1. 内存占用高（写时复制）。2. 弱一致性（读不到最新写入的数据）。
2. **问题**：什么时候用 CopyOnWriteArrayList？
   **答案**：读多写极少的场景，如白名单、配置列表。
3. **问题**：ConcurrentSkipListMap 是什么？
   **答案**：线程安全的有序 Map，基于跳表（SkipList）实现，查询效率 O(log n)。
4. **问题**：Java 中有哪些并发集合？
   **答案**：ConcurrentHashMap, CopyOnWriteArrayList, BlockingQueue, ConcurrentSkipListMap 等。
5. **问题**：如何将一个普通 List 变成线程安全的？
   **答案**：`Collections.synchronizedList(list)`。原理是装饰器模式，对所有方法加对象锁。

---

## 第8天：String 不可变性 + intern + 常量池 — 覆盖原理点：[#9, #10]

### 编码探源题

#### 题目1：破解 String.intern() 的迷思
比较 JDK 1.6 和 1.7+ 中 `new String("a") + new String("b")` 调用 `intern()` 后的 `==` 结果。

**✅ 标准答案**：
- **结论**：1.7+ 中，如果池中没有，`intern()` 会将堆中对象的**引用**存入池中，而不再拷贝对象。

---

### 🎯 今日高频面试题速览
1. **问题**：String 为什么是不可变的？
   **答案**：1. 安全（Hash值不变、线程安全）。2. 效率（常量池复用）。3. 源码用 `final char[]`（1.9后 `byte[]`）且不提供修改方法。
2. **问题**：`String s = new String("abc")` 创建了几个对象？
   **答案**：一个或两个。如果常量池没有 "abc"，则创建一个池中对象，再创建一个堆中对象；如果已有，则只创建一个堆中对象。
3. **问题**：StringBuilder 和 StringBuffer 的区别？
   **答案**：StringBuilder 非线程安全，性能高；StringBuffer 线程安全（方法加 `synchronized`）。
4. **问题**：Java 9 为什么把 String 的 char[] 改成 byte[]？
   **答案**：为了节省内存。大多数字符串只包含 Latin-1 字符，用 byte 存储减半空间占用。
5. **问题**：字符串拼接 `+` 的底层原理？
   **答案**：1.8 前是 StringBuilder；1.9+ 优化为 `StringConcatFactory`（动态指令调用），性能更好。

---

## 第9天：包装类缓存 + 自动拆装箱陷阱 — 覆盖原理点：[#11]

### 编码探源题

#### 题目1：Integer 缓存边界实验
验证 `Integer i1 = 127; Integer i2 = 127;` 和 `128` 的 `==` 结果，并尝试修改 JVM 参数 `-XX:AutoBoxCacheMax`。

**✅ 标准答案**：
- **原理**：`Integer.valueOf()` 会检查 `[-128, 127]` 缓存。

---

### 🎯 今日高频面试题速览
1. **问题**：自动拆装箱的原理？
   **答案**：编译期语法糖。装箱调用 `valueOf()`，拆箱调用 `xxxValue()`。
2. **问题**：哪些包装类有缓存？范围是多少？
   **答案**：Byte, Short, Integer, Long (-128~127)，Character (0~127)，Boolean (True/False)。Float/Double 没有。
3. **问题**：`Integer` 缓存上限可以修改吗？
   **答案**：可以，通过 `-XX:AutoBoxCacheMax` 修改 Integer 的上限，但下限 -128 固定不可变。
4. **问题**：在循环中使用包装类进行加法运算会有什么问题？
   **答案**：频繁拆装箱产生大量无用对象，增加 GC 压力。
5. **问题**：`Double d1 = 1.0; Double d2 = 1.0; d1 == d2` 结果是什么？
   **答案**：`false`。Double 没有缓存机制，每次 `valueOf` 都 `new`。

---

## 第10天：异常体系 + try-finally + try-with-resources — 覆盖原理点：[#12, #13]

### 编码探源题

#### 题目1：try-finally 里的 return 陷阱
编写一个方法，在 `try` 中 return 1，在 `finally` 中 return 2，观察最终结果。

**✅ 标准答案**：
- **结果**：返回 2。`finally` 中的 `return` 会覆盖之前的 `return`。如果 `finally` 修改了基本类型的返回值（但没 return），`try` 中的 `return` 不受影响（返回值已入栈）。

---

### 🎯 今日高频面试题速览
1. **问题**：Error 和 Exception 的区别？
   **答案**：Error 是不可恢复的严重问题（如 OOM, StackOverflow）；Exception 是程序可以处理的异常。
2. **问题**：Checked Exception 和 Runtime Exception 的区别？
   **答案**：前者编译期强制处理；后者运行期抛出，不强制捕获（通常是代码逻辑问题）。
3. **问题**：try-with-resources 的好处？
   **答案**：自动关闭资源，代码更简洁，且能正确处理“异常抑制”（Suppressed Exceptions）。
4. **问题**：NoClassDefFoundError 和 ClassNotFoundException 的区别？
   **答案**：前者是编译时有、运行时找不到类文件；后者是动态加载（如 `Class.forName`）时找不到类。
5. **问题**：为什么不建议在 `finally` 块中抛出异常或使用 `return`？
   **答案**：会导致 `try` 块中的异常被吞掉或返回值被篡改，增加调试难度。
实现数组+链表结构的简化版 HashMap，在 `put` 时打印：当前数组长度、size、threshold、插入位置索引、链表长度。当 size > threshold 时触发扩容，打印扩容前后的数组长度变化。

```java
// 提示：核心结构
class Node<K,V> {
    int hash;
    K key;
    V value;
    Node<K,V> next;
}
// 扩容时遍历每个桶，重新计算索引 (hash & (newCap - 1))
```

**🔍 原理反思提问**：你的扩容触发时机与 JDK 1.8 的 `resize()` 相同吗？扩容时链表迁移为什么 1.7 是头插法导致死循环的根源？1.8 如何通过高低位链表拆分解决这个问题？

**💬 面试官可能追问**：为什么扩容因子默认是 0.75？如果设为 1.0 或 0.1 会有什么后果？请用你的代码实验来论证。`HashMap` 的容量为什么必须是 2 的幂？

---

#### 题目2：验证 HashMap 哈希扰动函数的效果
编写代码：生成 10000 个随机 key，分别用原始 hashCode 和经过 `hash()` 扰动后的值计算桶下标（`hash & (cap-1)`），统计每个桶的元素数量分布。对比扰动前后的分布均匀度（标准差）。

```java
// 提示：JDK 1.8 的 hash() 方法
// return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
// 对比直接用 hashCode() 和用扰动函数后的分布差异
```

**🔍 原理反思提问**：为什么扰动函数是高 16 位异或低 16 位，而不是其他位运算？当容量较小时（如 16），扰动函数的作用有多大？当容量很大时（如 65536），扰动函数还有意义吗？

**💬 面试官可能追问**：`Object.hashCode()` 是内存地址吗？不同 JVM 实现有何不同？为什么重写 `equals` 必须重写 `hashCode`？如果只重写 `equals` 不重写 `hashCode`，HashMap 会出现什么 bug？请用代码验证。

---

#### 题目3：阅读 HashMap.resize() 源码并编写探针代码
打开 JDK 源码 `HashMap.resize()`，编写探针代码：在扩容前后通过反射读取 `table`、`threshold`、`size`，打印每个桶的链表结构。重点观察高低位链表（`loHead`/`hiHead`）的拆分过程。

```java
// 提示：使用反射获取 table 字段
// 遍历每个桶，打印链表中的 key 和 hash 值
// 验证：(hash & oldCap) == 0 的节点留在原位，否则移到 index + oldCap
```

**🔍 原理反思提问**：`(e.hash & oldCap) == 0` 这个判断为什么等价于判断新索引是否等于旧索引？`resize()` 中 `newThr = oldThr << 1` 为什么是翻倍？`MAXIMUM_CAPACITY` 为什么是 `1 << 30`？

**💬 面试官可能追问**：`HashMap` 在多线程环境下 `put` 可能导致数据丢失，具体是什么原因？1.8 中 `resize()` 还会出现死循环吗？`ConcurrentHashMap` 是如何解决这个问题的？

---

#### 题目4：对比不同初始容量下的扩容次数
编写代码：分别用初始容量 16、32、64、128 的 HashMap 插入 1000 个元素，统计每种情况下的扩容次数和最终容量。验证：初始容量设置对性能的影响。

```java
// 提示：HashMap 的 tableSizeFor() 会将容量调整为 2 的幂
// 例如 new HashMap(17) 实际容量是 32
```

**🔍 原理反思提问**：`tableSizeFor()` 方法是如何将任意整数调整为大于等于它的最小 2 的幂的？`new HashMap(0)` 和 `new HashMap()` 有什么区别？`threshold` 的初始值是如何计算的？

**💬 面试官可能追问**：如果预估要存 1000 个元素，初始容量设为多少最合适？为什么不是 1000？`new HashMap(1000)` 实际容量是多少？会经历几次扩容？

---

#### 题目5（地狱级）：模拟 HashMap 1.7 并发扩容死循环
在 JDK 7 环境下（或通过代码模拟 1.7 的头插法逻辑），编写多线程并发 put 触发扩容的代码，复现链表成环导致 CPU 100% 的问题。打印成环前后的链表结构。

```java
// 提示：1.7 的 transfer() 使用头插法
// void transfer(Entry[] newTable) {
//     for (Entry e : table) {
//         while (null != e) {
//             Entry next = e.next;
//             int i = indexFor(e.hash, newCapacity);
//             e.next = newTable[i];
//             newTable[i] = e;
//             e = next;
//         }
//     }
// }
// 两个线程同时扩容时，可能导致链表成环
```

**🔍 原理反思提问**：头插法为什么会导致链表成环？1.8 的尾插法（高低位链表）为什么能避免这个问题？除了死循环，头插法还会导致什么问题（如数据丢失）？

**💬 面试官可能追问**：如果面试官问你"你遇到过 HashMap 的并发问题吗"，你如何用这个实验的经历来回答？`Hashtable` 是如何保证线程安全的？为什么现在不推荐使用它？

---

### 核心实现题

**手写简化版 HashMap**：在题目1的基础上完善，实现 `put(K,V)`、`get(K)`、`remove(K)`、`resize()`，包含扰动函数、扩容逻辑（高低位链表拆分）。编写单元测试覆盖：正常存取、哈希碰撞、扩容触发、null key 处理。

```java
public class MyHashMap<K,V> {
    static final int DEFAULT_CAPACITY = 16;
    static final float LOAD_FACTOR = 0.75f;
    // ... 完整实现
}
```

**🔍 原理反思提问**：你的 `resize()` 中如何处理单节点、链表节点、树节点的不同情况？`null` key 在你的实现中存放在哪个桶？为什么 HashMap 允许 null key 而 ConcurrentHashMap 不允许？

**💬 面试官可能追问**：你的 HashMap 的 `keySet()`、`values()`、`entrySet()` 返回的是视图还是快照？修改这些集合会影响原 Map 吗？JDK 中这三个方法返回的是什么类型？

---

## 第3天：HashMap 树化与退化 + LinkedHashMap LRU — 覆盖原理点：[#3 链表树化与红黑树退化, #7 LinkedHashMap LRU]

### 编码探源题

#### 题目1：复现 HashMap 链表树化过程
编写代码：创建一个 hashCode 相同但 equals 不同的 Key 类，向 HashMap 中插入 10 个这样的 key，观察第 8 个（TREEIFY_THRESHOLD=8）插入时链表是否转为红黑树。通过反射读取桶中的节点类型（Node 还是 TreeNode）。

```java
// 提示：设计一个类，hashCode() 固定返回 1，equals() 比较自身
// 通过反射检查 table[i] 是否是 TreeNode 类型
// 注意：树化还需要 MIN_TREEIFY_CAPACITY=64，容量不够会先扩容
```

**🔍 原理反思提问**：为什么树化阈值是 8？退化阈值是 6？中间差值 2 的设计目的是什么（防止频繁转换）？泊松分布下链表长度达到 8 的概率是多少？

**💬 面试官可能追问**：`TreeNode` 继承自 `LinkedHashMap.Entry`，而 `LinkedHashMap.Entry` 继承自 `HashMap.Node`，这个继承链的设计意图是什么？红黑树节点为什么还要维护 `prev` 和 `next` 指针（双向链表结构）？

---

#### 题目2：复现红黑树退化为链表
在题目1的基础上，逐个删除元素，当树中节点数降到 6（UNTREEIFY_THRESHOLD）时，验证红黑树是否退化为链表。打印退化前后的节点类型。

```java
// 提示：删除时观察 removeTreeNode() 方法
// 当根节点、左右子节点、左左孙节点中任一为 null 时触发退化
```

**🔍 原理反思提问**：退化条件除了节点数 ≤ 6，还有哪些条件？`removeTreeNode()` 中为什么在删除前要检查 `root` 及其子节点？红黑树删除后的自平衡比插入更复杂，具体有哪些旋转情况？

**💬 面试官可能追问**：为什么 HashMap 选择红黑树而不是 AVL 树？红黑树的查找、插入、删除时间复杂度各是多少？在什么场景下 AVL 树比红黑树更合适？

---

#### 题目3：手写 LRU 缓存（基于 LinkedHashMap 思想）
利用 `LinkedHashMap` 的 `accessOrder=true` 和 `removeEldestEntry()` 实现一个 LRU 缓存。然后不依赖 LinkedHashMap，从零手写一个基于 HashMap + 双向链表的 LRU 缓存。

```java
// 方案一：使用 LinkedHashMap
LinkedHashMap<Integer, String> lru = new LinkedHashMap<>(16, 0.75f, true) {
    @Override
    protected boolean removeEldestEntry(Map.Entry eldest) {
        return size() > maxSize;
    }
};

// 方案二：手写 HashMap + 双向链表
class LRUCache<K,V> {
    // Node 同时包含 hash table 指针和双向链表指针
}
```

**🔍 原理反思提问**：`LinkedHashMap` 的 `afterNodeAccess()` 和 `afterNodeInsertion()` 是如何与 HashMap 的 `put`/`get` 钩子配合的？双向链表的头尾哨兵节点设计有什么好处？

**💬 面试官可能追问**：LRU 缓存在高并发场景下有什么问题？如何改进为 LRU-2 或 LFU？Redis 的 LRU 淘汰和你的实现有什么不同（近似 LRU）？

---

#### 题目4：阅读 LinkedHashMap 源码并画出 accessOrder 下的链表维护流程
打开 `LinkedHashMap` 源码，追踪 `get(Object)` → `afterNodeAccess(Node)` 的完整流程。画出当 `accessOrder=true` 时，一个已存在节点被访问后如何移动到链表末尾。

**🔍 原理反思提问**：`LinkedHashMap.Entry` 的 `before` 和 `after` 指针与 HashMap 的 `next` 指针分别用于什么？为什么需要两套指针系统？`transferLinks()` 方法的作用是什么？

**💬 面试官可能追问**：`LinkedHashMap` 的迭代顺序是什么？如果 `accessOrder=false`（插入顺序），迭代时是按什么顺序？`LinkedHashSet` 内部是如何实现的？

---

#### 题目5（地狱级）：对比 TreeMap 红黑树 vs HashMap 红黑树实现差异
分别阅读 `TreeMap.put()` 和 `HashMap.TreeNode.putTreeVal()` 的源码，对比两者红黑树实现的差异。编写代码插入相同数据，对比两者的遍历顺序和性能。

```java
// 提示：TreeMap 的红黑树实现更"标准"，HashMap 的 TreeNode 更"定制化"
// TreeMap 使用 Comparator/Comparable 比较，HashMap 使用 hash 比较
// 注意 TreeMap 的 Entry 有 left/right/parent/color，HashMap 的 TreeNode 还有 prev/next
```

**🔍 原理反思提问**：为什么 HashMap 的 TreeNode 需要维护双向链表（prev/next）而 TreeMap 不需要？`TreeMap` 的 `subMap()`、`headMap()`、`tailMap()` 返回的是视图还是新集合？

**💬 面试官可能追问**：`TreeMap` 的 `containsKey()` 时间复杂度是多少？`Collections.unmodifiableSortedMap()` 和 `Collections.synchronizedSortedMap()` 的实现原理是什么？

---

### 核心实现题

**手写 LRU 缓存**：从零实现一个泛型 LRU 缓存，包含 `get(K)`、`put(K,V)`、`size()` 方法。使用 HashMap + 双向链表，支持设置最大容量。编写单元测试覆盖：正常存取、容量满时淘汰最久未使用、访问后更新顺序、重复 key 更新。

```java
public class LRUCache<K,V> {
    private final int capacity;
    private final Map<K, Node> map;
    private final Node head; // 哨兵
    private final Node tail; // 哨兵
    // ...
}
```

**🔍 原理反思提问**：你的 LRU 实现中 `get` 和 `put` 的时间复杂度是多少？哨兵节点的设计如何简化边界处理？如果要支持过期时间（TTL），你会如何扩展？

**💬 面试官可能追问**：如果让你实现一个线程安全的 LRU 缓存，你会怎么做？`ConcurrentLinkedHashMap` 的原理是什么？`Caffeine` 缓存的 W-TinyLFU 算法相比 LRU 有什么优势？

---

## 第4天：HashSet + hashCode/equals 契约 — 覆盖原理点：[#8 TreeMap 红黑树与 Comparable/Comparator]

### 编码探源题

#### 题目1：验证 HashSet 底层是 HashMap
编写代码：向 HashSet 添加元素，通过反射获取其内部的 HashMap，打印 HashMap 的 size 和 table 内容。验证 `HashSet.add(E)` 本质是 `HashMap.put(E, PRESENT)`。

```java
// 提示：HashSet 内部有一个 private transient HashMap<E,Object> map
// PRESENT 是一个 static final 的 Object 占位符
```

**🔍 原理反思提问**：为什么 HashSet 使用一个全局共享的 `PRESENT` 对象而不是 null？`HashSet` 的 `contains()` 方法时间复杂度是多少？`TreeSet` 底层是什么？

**💬 面试官可能追问**：`HashSet` 的 `add()` 方法返回值什么时候是 false？`Set` 的 `addAll()` 和逐个 `add()` 有什么区别？`Collections.newSetFromMap()` 可以用来创建什么类型的 Set？

---

#### 题目2：复现 hashCode 不一致导致的 HashSet 内存泄漏
编写代码：创建一个可变对象，其 hashCode 依赖可变字段。先将对象加入 HashSet，然后修改该字段，再尝试 remove 该对象。验证 remove 失败，对象"泄漏"在 Set 中。

```java
// 提示：设计一个 MutableKey 类，hashCode() 和 equals() 基于可变字段
// 加入 Set 后修改字段，hashCode 改变，导致找不到原来的桶
```

**🔍 原理反思提问**：为什么 HashMap/HashSet 的 key 推荐使用不可变对象？如果必须使用可变对象作为 key，有什么安全的使用方式？`String` 为什么是理想的 HashMap key？

**💬 面试官可能追问**：`EnumSet` 和 `EnumMap` 的内部实现是什么？为什么它们比 HashSet/HashMap 性能更好？`BitSet` 和 `EnumSet` 有什么区别？

---

#### 题目3：验证 TreeSet/TreeMap 的排序机制
编写代码：分别使用自然排序（Comparable）和定制排序（Comparator）创建 TreeSet，插入相同元素，对比输出顺序。验证当 Comparator 和 Comparable 同时存在时，优先使用哪个。

```java
// 提示：TreeSet 底层是 TreeMap，TreeMap 的 put 使用 Comparator 或 Comparable
// 如果 Comparator 为 null，则使用 key 的 Comparable
```

**🔍 原理反思提问**：`TreeMap.put()` 中红黑树的比较逻辑是什么？如果 `Comparator` 和 `Comparable` 都不存在会怎样？`TreeMap` 的 `containsKey()` 是如何利用红黑树特性实现 O(log n) 的？

**💬 面试官可能追问**：`Collections.sort()` 和 `Arrays.sort()` 的排序算法是什么？为什么对象数组用 TimSort 而基本类型数组用 DualPivotQuickSort？TimSort 的核心思想是什么？

---

#### 题目4：阅读 TreeMap 红黑树自平衡源码
打开 `TreeMap.put()` 源码，追踪 `fixAfterInsertion()` 方法。画出插入后红黑树自平衡的三种旋转情况（左旋、右旋、变色）的示意图，标注每种情况对应的代码分支。

**🔍 原理反思提问**：红黑树的五条性质是什么？为什么红黑树的插入最多只需要两次旋转？`rotateLeft()` 和 `rotateRight()` 方法中哪些指针需要更新？

**💬 面试官可能追问**：红黑树 vs AVL 树 vs B+ 树，各自的适用场景是什么？为什么数据库索引用 B+ 树而不是红黑树？ConcurrentSkipListMap 的跳表相比红黑树有什么优势？

---

#### 题目5（地狱级）：对比不同 Set 实现的内存占用与性能
编写 JMH 基准测试，对比 `HashSet`、`TreeSet`、`LinkedHashSet`、`EnumSet`（针对枚举）、`CopyOnWriteArraySet`、`ConcurrentSkipListSet` 在 add、contains、remove、iterate 四种操作下的性能。数据量从 100 到 100000。

```java
// 提示：每种 Set 的数据结构不同，适用场景不同
// HashSet: O(1) 但无序
// TreeSet: O(log n) 但有序
// EnumSet: 位向量，极快
```

**🔍 原理反思提问**：`CopyOnWriteArraySet` 底层是 `CopyOnWriteArrayList`，它的 add 操作为什么很慢？`ConcurrentSkipListSet` 的跳表结构如何实现 O(log n) 的并发访问？

**💬 面试官可能追问**：如果让你设计一个支持范围查询的并发 Set，你会选择什么数据结构？`ConcurrentSkipListSet` 的 `size()` 方法为什么是 O(n) 而不是 O(1)？

---

### 核心实现题

**手写简化版 TreeMap**：实现一个基于二叉搜索树（进阶：红黑树）的有序 Map，包含 `put(K,V)`、`get(K)`、`remove(K)`、`firstKey()`、`lastKey()`。支持 Comparator 定制排序。编写单元测试覆盖：排序验证、重复 key 覆盖、边界情况。

```java
public class MyTreeMap<K,V> {
    private final Comparator<? super K> comparator;
    private Entry<K,V> root;
    // ...
}
```

**🔍 原理反思提问**：你的二叉搜索树在最坏情况下（顺序插入）会退化成链表吗？如何通过自平衡（AVL 或红黑树）避免？`remove` 操作中，删除有两个子节点的节点时，你选择前驱还是后继替代？

**💬 面试官可能追问**：`TreeMap` 的 `subMap(fromKey, toKey)` 返回的视图支持修改操作吗？修改视图会影响原 Map 吗？`NavigableMap` 接口提供了哪些额外的导航方法？

---

## 第5天：ConcurrentHashMap 1.7 vs 1.8 锁演变 — 覆盖原理点：[#4 Segment 分段锁 vs CAS+synchronized, #5 sizeCtl 与扩容迁移]

### 编码探源题

#### 题目1：手写简化版 ConcurrentHashMap（CAS + synchronized）
实现一个简化版 ConcurrentHashMap：使用 CAS 操作 + synchronized 锁住桶首节点。实现 `put(K,V)` 和 `get(K)`。在 put 时打印：CAS 成功/失败次数、synchronized 加锁次数。

```java
// 提示：使用 Unsafe 或 VarHandle 进行 CAS 操作
// 或者使用 AtomicReferenceArray 简化
// 桶为空时 CAS 设置首节点，桶非空时 synchronized 锁住首节点
```

**🔍 原理反思提问**：为什么 1.8 放弃了 Segment 分段锁而改用 CAS + synchronized？synchronized 在 1.6 之后做了哪些优化使得这种方案可行？CAS 操作在什么情况下会自旋失败？

**💬 面试官可能追问**：你的实现中 `get()` 方法为什么不需要加锁？`Node` 的 `val` 和 `next` 为什么用 `volatile` 修饰？如果 get 时正好遇到扩容（遇到 ForwardingNode），会怎么处理？

---

#### 题目2：对比 ConcurrentHashMap 1.7 Segment 锁和 1.8 CAS 锁的并发度
编写代码：模拟 1.7 的 Segment 设计（16 个 Segment，每个内部是一个小 HashMap），和 1.8 的设计（CAS + synchronized 锁桶首节点）。多线程并发 put，对比两者的吞吐量。

```java
// 提示：1.7 的并发度由 segment 数量决定（默认 16）
// 1.8 的并发度理论上等于桶的数量
// 当所有 key 的 hash 碰撞到同一个桶时，两者性能如何？
```

**🔍 原理反思提问**：1.7 的 `concurrencyLevel` 参数为什么在 1.8 中被废弃？1.7 中跨 Segment 操作（如 `size()`）如何保证一致性？1.8 的 `size()` 是如何实现的？

**💬 面试官可能追问**：1.7 的 `size()` 方法为什么先尝试两次不加锁统计，失败后再加全部锁？这种乐观策略在什么情况下会失效？1.8 的 `mappingCount()` 和 `size()` 有什么区别？

---

#### 题目3：阅读 ConcurrentHashMap 1.8 扩容迁移（transfer）源码
打开 `ConcurrentHashMap.transfer()` 源码，编写探针代码：多线程 put 触发扩容，通过反射读取 `sizeCtl`、`transferIndex`、`ForwardingNode` 等关键状态，打印扩容过程中各线程的工作分配。

```java
// 提示：sizeCtl 在扩容时为负数，低 16 位表示参与扩容的线程数+1
// transferIndex 表示下一个待迁移的桶区间上界
// 每个线程每次领取 stride 个桶进行迁移
```

**🔍 原理反思提问**：`sizeCtl` 在不同阶段（初始化、正常、扩容）分别代表什么含义？`transferIndex` 的 CAS 更新如何保证多线程不重复处理同一个桶？`ForwardingNode` 的作用是什么？

**💬 面试官可能追问**：扩容时如果有线程在 `put`，遇到 `ForwardingNode` 会怎么处理？`helpTransfer()` 方法是如何让 put 线程也参与扩容的？为什么这样设计？

---

#### 题目4：验证 ConcurrentHashMap 的弱一致性
编写代码：一个线程向 ConcurrentHashMap put 数据，另一个线程立即 get。验证 get 可能读不到刚 put 的数据（弱一致性）。对比 `Hashtable` 的强一致性行为。

```java
// 提示：ConcurrentHashMap 的 get 不加锁，可能读到旧值
// 但 put 使用 volatile 写，保证最终可见性
// 这种弱一致性是性能优势的来源
```

**🔍 原理反思提问**：`ConcurrentHashMap` 的 `clear()` 方法为什么是弱一致性的？迭代器（`keySet().iterator()`）遍历时能看到遍历开始后新增的元素吗？为什么说它是弱一致性迭代器？

**💬 面试官可能追问**：`ConcurrentHashMap` 的 `compute()`、`computeIfAbsent()`、`merge()` 等原子操作方法是如何实现的？它们和 `putIfAbsent()` 有什么区别？

---

#### 题目5（地狱级）：模拟 ConcurrentHashMap 扩容期间的并发读写
编写高并发场景：10 个线程同时 put，5 个线程同时 get，在扩容触发时观察：get 线程是否被阻塞、ForwardingNode 的查找路径、put 线程参与 helpTransfer 的行为。打印各线程的时间线。

```java
// 提示：使用 CountDownLatch 让所有线程同时开始
// 初始容量设小一些（如 2），快速触发扩容
// 观察 get 遇到 ForwardingNode 时如何转发到新表
```

**🔍 原理反思提问**：扩容期间，旧表的查询和新表的查询是如何衔接的？`ForwardingNode.find()` 方法是如何转发请求的？为什么扩容不会阻塞读操作？

**💬 面试官可能追问**：`ConcurrentHashMap` 的 `reduce`、`search`、`forEach` 等批量操作方法在扩容期间的行为是什么？它们如何处理并发修改？

---

### 核心实现题

**手写简化版 ConcurrentHashMap**：实现一个线程安全的 HashMap，包含 `put(K,V)`、`get(K)`、`size()`。使用 CAS + synchronized 策略，支持多线程并发扩容（简化版 transfer）。编写单元测试覆盖：并发 put 正确性、并发读写、扩容期间读写。

```java
public class MyConcurrentHashMap<K,V> {
    private volatile Node<K,V>[] table;
    private volatile int sizeCtl;
    // ...
}
```

**🔍 原理反思提问**：你的实现中如何处理 `null` key 和 `null` value？为什么 ConcurrentHashMap 不允许 null？`size()` 方法的实现是精确的还是估算的？

**💬 面试官可能追问**：如果让你实现 `computeIfAbsent()`，如何保证原子性？`ConcurrentHashMap` 的 `reserveTable()` 和 `initTable()` 是如何通过 `sizeCtl` 的 CAS 保证只有一个线程初始化的？

---

## 第6天：BlockingQueue 与生产者-消费者 — 覆盖原理点：[并发集合核心原理]

### 编码探源题

#### 题目1：手写简化版 ArrayBlockingQueue
实现一个基于数组的有界阻塞队列，包含 `put(E)`（阻塞）、`take()`（阻塞）、`offer(E)`（非阻塞）、`poll()`（非阻塞）。使用 `ReentrantLock` + `Condition`（notFull/notEmpty）实现阻塞唤醒。

```java
// 提示：使用一把锁 + 两个 Condition
// putIndex 和 takeIndex 循环使用数组
// 打印每次 put/take 时的队列 size 和等待线程数
```

**🔍 原理反思提问**：`ArrayBlockingQueue` 为什么使用一把锁而不是两把锁？`LinkedBlockingQueue` 使用两把锁（putLock/takeLock），这种设计有什么优势和劣势？

**💬 面试官可能追问**：`ArrayBlockingQueue` 的 `drainTo()` 方法是如何实现的？它和逐个 `poll()` 相比有什么性能优势？`SynchronousQueue` 的公平模式和非公平模式分别用什么数据结构？

---

#### 题目2：对比 ArrayBlockingQueue vs LinkedBlockingQueue 性能
编写 JMH 基准测试，对比两者在 1 生产者 + 1 消费者、多生产者 + 多消费者场景下的吞吐量。分析一把锁 vs 两把锁在不同并发度下的表现。

```java
// 提示：ArrayBlockingQueue 一把锁，入队出队互斥
// LinkedBlockingQueue 两把锁，入队出队可并行
// 但 LinkedBlockingQueue 的节点是动态分配的，有 GC 开销
```

**🔍 原理反思提问**：`LinkedBlockingQueue` 的 `head` 节点为什么是一个 dummy 节点？`LinkedBlockingQueue` 的 `count` 为什么用 `AtomicInteger` 而不是 `volatile int`？

**💬 面试官可能追问**：`PriorityBlockingQueue` 的底层数据结构是什么？它的 `take()` 如何保证总是取出优先级最高的元素？扩容时是线程安全的吗？

---

#### 题目3：阅读 ArrayBlockingQueue 源码并画出 enqueue/dequeue 流程
打开 `ArrayBlockingQueue.enqueue()` 和 `dequeue()` 源码，画出完整的入队/出队流程，包括：锁获取、条件等待、元素操作、信号通知、锁释放。

**🔍 原理反思提问**：`enqueue()` 中为什么先添加元素再 `signalNotEmpty()`？如果反过来会有什么问题？`Condition.await()` 被唤醒后，线程需要重新获取锁吗？

**💬 面试官可能追问**：`ArrayBlockingQueue` 的 `remove(Object)` 方法为什么需要遍历整个数组？时间复杂度是多少？`LinkedBlockingQueue` 的 `remove(Object)` 需要两把锁都加吗？

---

#### 题目4：实现一个支持优先级的阻塞队列
基于 `PriorityBlockingQueue` 的思想，手写一个支持优先级的阻塞队列。使用二叉堆（数组实现）作为底层数据结构，支持阻塞的 `take()` 和非阻塞的 `offer()`。

```java
// 提示：二叉堆的 siftUp（插入时上浮）和 siftDown（删除时下沉）
// 使用 ReentrantLock + Condition 实现阻塞
```

**🔍 原理反思提问**：二叉堆的插入和删除时间复杂度是多少？为什么用数组而不是链表实现？`PriorityBlockingQueue` 的 `iterator()` 遍历顺序是优先级顺序吗？

**💬 面试官可能追问**：`DelayQueue` 的内部实现是什么？它的 `take()` 如何实现延迟获取？`Delayed` 接口的 `getDelay()` 和 `compareTo()` 如何配合使用？

---

#### 题目5（地狱级）：模拟生产者-消费者死锁场景
设计一个场景：生产者向满队列 put 时被阻塞，消费者在 take 之前需要先获取另一个资源（该资源被生产者持有），导致死锁。使用 jstack 分析死锁线程。

```java
// 提示：生产者持有资源 A，等待队列非满
// 消费者需要先获取资源 A，再 take 队列
// 形成循环等待
```

**🔍 原理反思提问**：如何避免这种死锁？`BlockingQueue` 的 `offer(E, timeout, unit)` 和 `poll(timeout, unit)` 如何帮助打破死锁？`LinkedTransferQueue` 的 `transfer()` 和 `tryTransfer()` 有什么区别？

**💬 面试官可能追问**：`LinkedTransferQueue` 的 "dual queue" 设计是什么？它如何让生产者和消费者直接交换数据而不经过队列？这种设计在什么场景下比 BlockingQueue 更高效？

---

### 核心实现题

**手写简化版 LinkedBlockingQueue**：实现一个基于链表的无界（可配置有界）阻塞队列，使用两把锁（putLock/takeLock）设计。包含 `put(E)`、`take()`、`offer(E)`、`poll()`、`size()`。编写单元测试覆盖：阻塞等待、多生产者多消费者、有界队列满时阻塞。

```java
public class MyLinkedBlockingQueue<E> {
    private final ReentrantLock putLock = new ReentrantLock();
    private final ReentrantLock takeLock = new ReentrantLock();
    private final Condition notFull = putLock.newCondition();
    private final Condition notEmpty = takeLock.newCondition();
    // ...
}
```

**🔍 原理反思提问**：你的两把锁设计在 `size()` 和 `remove(Object)` 方法中需要同时加两把锁吗？`LinkedBlockingQueue` 的 `count` 为什么用 `AtomicInteger`？如果不用原子类会有什么问题？

**💬 面试官可能追问**：`LinkedBlockingQueue` 和 `ConcurrentLinkedQueue` 的区别是什么？一个是有界阻塞，一个是无界非阻塞，各自适用什么场景？`ConcurrentLinkedQueue` 的 Michael-Scott 算法是什么？

---

## 第7天：CopyOnWriteArrayList + 并发集合总结 — 覆盖原理点：[并发集合总结]

### 编码探源题

#### 题目1：验证 CopyOnWriteArrayList 的写时复制机制
编写代码：向 CopyOnWriteArrayList 添加元素，通过反射读取内部数组的引用地址。验证每次 add 都会创建一个新数组（地址变化），而 get 直接读旧数组。

```java
// 提示：CopyOnWriteArrayList 的 add 使用 ReentrantLock + Arrays.copyOf
// 通过 System.identityHashCode(array) 观察数组地址变化
```

**🔍 原理反思提问**：CopyOnWriteArrayList 的迭代器为什么不需要 fail-fast？它的迭代器能看到迭代开始后新增的元素吗？写时复制的内存开销有多大？

**💬 面试官可能追问**：CopyOnWriteArrayList 适合什么场景？为什么不适合写多读少的场景？`CopyOnWriteArraySet` 底层是 CopyOnWriteArrayList，它的 add 性能如何？

---

#### 题目2：对比 ConcurrentLinkedQueue vs LinkedBlockingQueue
编写 JMH 基准测试，对比两者在单线程和多线程下的 offer/poll 性能。分析 CAS 无锁 vs ReentrantLock 在不同并发度下的表现。

```java
// 提示：ConcurrentLinkedQueue 使用 Michael-Scott 无锁队列算法
// CAS 操作在低竞争下很快，高竞争下自旋开销大
```

**🔍 原理反思提问**：`ConcurrentLinkedQueue` 的 `size()` 方法为什么是 O(n)？它的 `isEmpty()` 比 `size() == 0` 更高效吗？`ConcurrentLinkedDeque` 的双端操作是如何实现的？

**💬 面试官可能追问**：`ConcurrentLinkedQueue` 的 `offer()` 方法中，tail 指针为什么不是每次都更新（松弛更新）？这种设计如何平衡性能和一致性？

---

#### 题目3：阅读 ConcurrentSkipListMap 跳表源码
打开 `ConcurrentSkipListMap` 源码，理解跳表的插入和查找逻辑。编写代码插入数据，通过反射观察 Index 节点的层级结构。

```java
// 提示：跳表通过多层索引实现 O(log n) 查找
// ConcurrentSkipListMap 的层级是随机生成的（概率 0.5）
```

**🔍 原理反思提问**：跳表的空间复杂度是多少？为什么 ConcurrentSkipListMap 选择跳表而不是红黑树？跳表的并发插入如何保证正确性？

**💬 面试官可能追问**：`ConcurrentSkipListMap` 的 `size()` 方法为什么也是 O(n)？`ConcurrentSkipListSet` 和 `ConcurrentSkipListMap` 的关系是什么？

---

#### 题目4：总结 Java 所有并发集合的实现原理
编写一个汇总表格，列出所有并发集合（ConcurrentHashMap、CopyOnWriteArrayList、BlockingQueue 系列、ConcurrentLinkedQueue、ConcurrentSkipListMap 等）的底层数据结构、锁策略、适用场景。

**🔍 原理反思提问**：为什么 Java 并发集合的设计如此多样化？没有一种"万能"的并发集合吗？CAP 理论在并发集合设计中如何体现（一致性 vs 可用性 vs 分区容错）？

**💬 面试官可能追问**：`Collections.synchronizedMap()` 和 `ConcurrentHashMap` 的区别是什么？为什么前者是强一致性而后者是弱一致性？各自适用什么场景？

---

#### 题目5（地狱级）：设计一个支持高并发写入的有序集合
综合前 7 天的知识，设计并实现一个支持高并发写入的有序集合。要求：线程安全、支持范围查询、写入性能不低于 ConcurrentSkipListSet。说明你的设计选择和权衡。

```java
// 提示：可能的方案
// 1. 分段锁 + 跳表（类似 ConcurrentHashMap 1.7 思想）
// 2. 无锁跳表（ConcurrentSkipListMap 已实现）
// 3. CopyOnWrite + 后台合并
// 分析每种方案的优劣
```

**🔍 原理反思提问**：你的设计在高并发写入场景下，瓶颈在哪里？如何通过分片（sharding）进一步提升写入性能？分片后范围查询如何实现？

**💬 面试官可能追问**：如果面试官让你设计一个"支持 10 万 TPS 写入的有序集合"，你会如何回答？需要考虑哪些维度（数据结构、锁策略、内存管理、GC 影响）？

---

### 核心实现题

**手写简化版 CopyOnWriteArrayList**：实现一个写时复制的线程安全列表，包含 `add(E)`、`get(int)`、`remove(int)`、`size()`、`iterator()`。使用 ReentrantLock 保护写操作，读操作无锁。编写单元测试覆盖：并发读写正确性、迭代器快照语义。

```java
public class MyCopyOnWriteArrayList<E> {
    private final ReentrantLock lock = new ReentrantLock();
    private volatile Object[] array;
    // ...
}
```

**🔍 原理反思提问**：你的迭代器持有的是快照数组的引用吗？如果在迭代期间有大量写入，内存中会同时存在多少个数组副本？`volatile` 修饰 array 的作用是什么？

**💬 面试官可能追问**：`CopyOnWriteArrayList` 的 `addAllAbsent()` 方法是如何实现的？它的 `sort()` 方法为什么需要加锁？`Collections.unmodifiableList()` 返回的不可变列表和 CopyOnWriteArrayList 有什么区别？

---

## 第8天：String 不可变性 + intern + 常量池 — 覆盖原理点：[#9 String 不可变性与常量池, #10 StringBuilder/StringBuffer]

### 编码探源题

#### 题目1：验证 String 不可变性与常量池
编写代码：通过 `==` 和 `equals()` 对比字面量字符串、`new String()`、`intern()` 的引用关系。验证字符串常量池的行为。

```java
String s1 = "hello";
String s2 = "hello";
String s3 = new String("hello");
String s4 = s3.intern();
System.out.println(s1 == s2); // true，指向常量池同一对象
System.out.println(s1 == s3); // false，s3 在堆上
System.out.println(s1 == s4); // true，intern 返回常量池引用
```

**🔍 原理反思提问**：`intern()` 在 JDK 1.7 前后的行为有何不同？1.7 将字符串常量池从方法区移到堆中，对 `intern()` 有什么影响？字符串常量池的底层数据结构是什么（1.7 前是固定大小的 HashMap）？

**💬 面试官可能追问**：大量使用 `intern()` 可能导致什么问题？`-XX:StringTableSize` 参数的作用是什么？如何观察字符串常量池的使用情况？

---

#### 题目2：追踪 StringBuilder 扩容机制
编写代码：创建 `StringBuilder(16)`，不断 append 字符串，通过反射读取内部 `value`（char[]）的长度，打印每次扩容时的容量变化。验证扩容公式 `newCapacity = (oldCapacity << 1) + 2`。

```java
// 提示：AbstractStringBuilder 的 expandCapacity()
// 通过反射获取 value 字段
Field field = AbstractStringBuilder.class.getDeclaredField("value");
field.setAccessible(true);
```

**🔍 原理反思提问**：为什么扩容公式是 `(oldCapacity << 1) + 2` 而不是简单的翻倍？`StringBuilder` 和 `StringBuffer` 都继承自 `AbstractStringBuilder`，它们的区别仅在于 `synchronized` 吗？

**💬 面试官可能追问**：`StringBuilder` 的 `toString()` 方法是共享 `value` 数组还是复制一份？为什么？`String` 的 `+` 操作符在编译后是什么？`"a" + "b"` 和 `s1 + s2`（变量拼接）编译结果有何不同？

---

#### 题目3：对比 String/StringBuilder/StringBuffer 拼接性能
编写 JMH 基准测试，对比三种方式在循环中拼接 10000 个字符串的性能。分析每次 `+` 创建新 String 的 GC 开销。

```java
// 提示：String + 每次创建新对象，O(n²) 复杂度
// StringBuilder/StringBuffer append 是 O(n)
// 注意编译期优化：单行字面量拼接会被编译器优化
```

**🔍 原理反思提问**：为什么循环中的字符串拼接必须用 StringBuilder？编译器在什么情况下会自动优化字符串拼接？`String.concat()` 方法和 `+` 操作符有什么区别？

**💬 面试官可能追问**：`StringJoiner` 和 `String.join()` 的内部实现是什么？`Collectors.joining()` 是如何实现的？Java 9 的紧凑字符串（Compact Strings）是什么？对性能有什么影响？

---

#### 题目4：阅读 String.intern() 源码并编写探针
编写代码：创建大量字符串并调用 `intern()`，通过 JVM 参数 `-XX:+PrintStringTableStatistics` 观察字符串常量池的统计信息。验证 intern 的性能开销。

```java
// 提示：StringTable 本质上是一个 Hashtable
// intern 操作需要加锁（StringTable 是全局的）
// 高并发下 intern 可能成为瓶颈
```

**🔍 原理反思提问**：`String.intern()` 是 native 方法，它的底层实现是什么？为什么高并发下 `intern()` 可能成为性能瓶颈？G1 的字符串去重（`-XX:+UseStringDeduplication`）和 `intern()` 有什么区别？

**💬 面试官可能追问**：`-XX:+UseStringDeduplication` 的原理是什么？它和 `intern()` 的去重有什么不同？String 的 `hashCode()` 为什么用 `hash` 字段缓存？为什么是 0 而不是 -1 表示未计算？

---

#### 题目5（地狱级）：模拟 String.intern() 导致的 YGC 变长
编写代码：大量调用 `intern()` 向字符串常量池添加字符串，触发 YGC。通过 GC 日志观察 YGC 耗时变化。分析 StringTable 的清理机制。

```java
// 提示：StringTable 中的字符串在 YGC 时会被清理（如果不再被引用）
// 但 StringTable 本身的大小是固定的，大量 intern 可能导致性能下降
// 使用 -XX:StringTableSize=1000000 调整大小
```

**🔍 原理反思提问**：StringTable 的 rehash 过程为什么可能导致 GC 暂停时间变长？如何通过 `-XX:StringTableSize` 优化？Java 8 和 Java 11 中 String 的实现有什么变化？

**💬 面试官可能追问**：Java 9 引入的紧凑字符串（Compact Strings）对内存有什么影响？`-XX:-CompactStrings` 关闭后会怎样？`String` 的 `coder` 字段（LATIN1/UTF16）是如何工作的？

---

### 核心实现题

**手写简化版 StringBuilder**：实现一个支持动态扩容的字符串构建器，包含 `append(String)`、`insert(int, String)`、`delete(int, int)`、`toString()`。内部使用 `char[]`，支持自动扩容。编写单元测试覆盖：扩容边界、空操作、大量 append。

```java
public class MyStringBuilder {
    private char[] value;
    private int count;
    // ...
}
```

**🔍 原理反思提问**：你的 `insert` 方法中如何移动已有元素？`System.arraycopy()` 在你的实现中出现了几次？`delete` 方法为什么不需要缩容？

**💬 面试官可能追问**：`StringBuilder` 的 `reverse()` 方法是如何实现的？`String` 的 `substring()` 在 JDK 1.6 和 1.7 中的实现有什么区别（1.6 共享 value 数组导致内存泄漏）？

---

## 第9天：包装类缓存 + 自动拆装箱陷阱 — 覆盖原理点：[#11 包装类缓存机制与自动拆装箱]

### 编码探源题

#### 题目1：验证 IntegerCache 范围
编写代码：测试 Integer 在 -128 到 127 范围内外的 `==` 行为。通过反射修改 `IntegerCache` 的上限，验证缓存范围可配置。

```java
// 提示：IntegerCache 默认缓存 [-128, 127]
// 可通过 -XX:AutoBoxCacheMax=2000 调整上限
// 通过反射读取 IntegerCache.cache 数组
```

**🔍 原理反思提问**：为什么 Integer 缓存范围是 -128 到 127？`Long`、`Short`、`Byte`、`Character` 的缓存范围各是什么？`Float` 和 `Double` 为什么没有缓存？

**💬 面试官可能追问**：`Integer.valueOf()` 和 `new Integer()` 有什么区别？为什么 `new Integer()` 在 Java 9 中被标记为 deprecated？`Boolean` 的缓存是什么？

---

#### 题目2：复现自动拆装箱 NPE 陷阱
编写代码：一个方法返回 `Integer`（可能为 null），调用方用 `int` 接收，触发自动拆箱 NPE。打印异常堆栈，定位拆箱发生的字节码指令。

```java
// 提示：自动拆箱调用 Integer.intValue()
// 如果 Integer 为 null，抛出 NullPointerException
// 使用 javap -c 查看字节码中的 Integer.intValue 调用
```

**🔍 原理反思提问**：自动装箱调用的是 `Integer.valueOf()` 还是 `new Integer()`？为什么？三元运算符 `? :` 中混合使用 `int` 和 `Integer` 会导致什么问题？

**💬 面试官可能追问**：`List<Integer>` 的 `remove(int index)` 和 `remove(Integer element)` 如何区分？泛型方法重载时，自动装箱和类型擦除如何交互？

---

#### 题目3：对比基本类型和包装类的内存占用
编写代码：创建 100 万个 `int` 数组和 100 万个 `Integer` 的 ArrayList，通过 `Runtime.getMemory()` 或 JMX 对比内存占用。计算每个 Integer 的额外开销（对象头 + 引用 + 值）。

```java
// 提示：int 占 4 字节
// Integer 对象：对象头（12/16 字节）+ int value（4 字节）+ 对齐填充 ≈ 16/24 字节
// 加上 ArrayList 中的引用（4/8 字节），总计约 20/32 字节
```

**🔍 原理反思提问**：压缩指针（`-XX:+UseCompressedOops`）对 Integer 的内存占用有什么影响？`int[]` 和 `Integer[]` 的内存布局有什么区别？

**💬 面试官可能追问**：`Trove`、`FastUtil`、`Koloboke` 等基本类型集合库的原理是什么？它们为什么比 `ArrayList<Integer>` 更省内存？`Eclipse Collections` 的基本类型集合是如何实现的？

---

#### 题目4：阅读 Integer.valueOf() 和 IntegerCache 源码
打开 `Integer.valueOf()` 和 `IntegerCache` 源码，画出 `valueOf()` 的判断流程。分析 `IntegerCache` 的静态初始化时机。

**🔍 原理反思提问**：`IntegerCache` 的 `high` 为什么可以通过 `-XX:AutoBoxCacheMax` 配置？这个配置是如何被读取的（`sun.misc.VM.getSavedProperty()`）？为什么不是通过 `System.getProperty()`？

**💬 面试官可能追问**：`LongCache` 和 `IntegerCache` 的实现有什么不同？`CharacterCache` 的范围是多少？`ShortCache` 和 `ByteCache` 为什么是全缓存？

---

#### 题目5（地狱级）：模拟自动装箱在高并发下的性能瓶颈
编写 JMH 基准测试，对比 `int` 基本类型和 `Integer` 包装类在高并发下的计算性能。分析自动装箱带来的 GC 压力（大量 Integer 对象创建和回收）。

```java
// 提示：使用 JMH 的 @BenchmarkMode(Mode.Throughput)
// 对比 int sum = 0; sum += i; 和 Integer sum = 0; sum += i;
// 后者每次 += 都会创建新的 Integer 对象
```

**🔍 原理反思提问**：`LongAdder` 为什么比 `AtomicLong` 在高并发下性能更好？它的分段累加（Cell 数组）如何减少 CAS 竞争？`Striped64` 的 `@Contended` 注解的作用是什么？

**💬 面试官可能追问**：`@Contended` 注解在什么版本引入？它的原理是什么（填充缓存行避免伪共享）？如何通过 `-XX:-RestrictContended` 启用它？

---

### 核心实现题

**手写简化版 IntegerCache**：实现一个类似 IntegerCache 的缓存机制，支持配置缓存范围。包含 `valueOf(int)`（从缓存获取或新建）、`intValue()`。编写单元测试覆盖：缓存命中、缓存范围边界、超出范围新建。

```java
public class MyInteger {
    private final int value;
    private static final MyInteger[] cache;
    // ...
}
```

**🔍 原理反思提问**：你的缓存是懒加载还是预加载？缓存数组的初始化在类加载的哪个阶段完成？如果缓存范围很大（如 [-10000, 10000]），对类加载时间有什么影响？

**💬 面试官可能追问**：`Integer` 的 `hashCode()` 为什么直接返回 `value`？`equals()` 的实现是什么？`Integer.parseInt()` 如何处理溢出？

---

## 第10天：异常体系 + try-finally + try-with-resources — 覆盖原理点：[#12 异常体系与 try-catch-finally, #13 try-with-resources]

### 编码探源题

#### 题目1：验证 finally 中 return 的陷阱
编写代码：try 块中有 `return`，finally 块中也有 `return`。观察最终返回值。再测试 finally 中修改返回值变量（基本类型 vs 引用类型）的效果。

```java
// 提示：finally 中的 return 会覆盖 try 中的 return
// finally 中修改基本类型变量不影响 try 中已缓存的返回值
// 但修改引用类型指向的对象内容会影响
```

**🔍 原理反思提问**：`try { return 1; } finally { return 2; }` 返回什么？JVM 字节码层面，`return` 和 `finally` 是如何协调的？`finally` 块一定会执行吗（`System.exit(0)` 的情况）？

**💬 面试官可能追问**：`try-finally` 中如果 try 和 finally 都抛异常，最终抛出哪个？`try-with-resources` 如何处理这种情况（suppressed exception）？

---

#### 题目2：验证 try-with-resources 的资源关闭顺序
编写代码：创建两个实现 `AutoCloseable` 的资源，在 try-with-resources 中声明。验证关闭顺序是声明的逆序。模拟其中一个 close 抛异常，观察 suppressed 异常。

```java
// 提示：try-with-resources 编译后是 try-catch-finally
// 资源关闭顺序：后声明先关闭
// 如果 close 抛异常，会被添加到主异常的 suppressed 列表中
```

**🔍 原理反思提问**：`try-with-resources` 编译后的字节码是什么样的？为什么资源关闭顺序是逆序？`Throwable.addSuppressed()` 方法在什么场景下使用？

**💬 面试官可能追问**：`AutoCloseable` 和 `Closeable` 的区别是什么？`Closeable` 的 `close()` 抛出 `IOException`，而 `AutoCloseable` 抛出 `Exception`，为什么这样设计？

---

#### 题目3：对比 checked exception 和 unchecked exception
编写代码：定义一个 checked 异常和 unchecked 异常，分别测试编译器对两者的处理差异。分析 `RuntimeException`、`Error`、`Exception` 的继承关系。

```java
// 提示：checked exception 必须显式 catch 或 throws
// unchecked exception（RuntimeException 及其子类）不需要
// Error 也不需要
```

**🔍 原理反思提问**：为什么 Java 设计 checked exception？Spring 为什么偏爱 unchecked exception（`DataAccessException` 继承 `RuntimeException`）？Lambda 表达式中如何处理 checked exception？

**💬 面试官可能追问**：`NoClassDefFoundError` 和 `ClassNotFoundException` 的区别是什么？`OutOfMemoryError` 可以被 catch 吗？catch 后应该怎么处理？

---

#### 题目4：阅读 Throwable.printStackTrace() 源码
打开 `Throwable.printStackTrace()` 源码，追踪异常堆栈的生成和打印流程。理解 `StackTraceElement` 的构造和 `getOurStackTrace()` 的实现。

**🔍 原理反思提问**：`Throwable.fillInStackTrace()` 方法的作用是什么？为什么有些异常（如 `OutOfMemoryError`）的堆栈可能不完整？`-XX:-OmitStackTraceInFastThrow` 参数的作用是什么？

**💬 面试官可能追问**：JVM 对频繁抛出的异常有优化（Fast Throw），这个优化的原理是什么？如何关闭？`NullPointerException` 在 Java 14 之后的改进（Helpful NPE）是什么？

---

## 第11天：泛型类型擦除与桥方法 — 覆盖原理点：[泛型原理]

### 编码探源题

#### 题目1：验证泛型类型擦除
编写代码：通过反射向 `List<Integer>` 中插入一个 `String` 元素。获取该 List 运行时的 Class 信息，观察其泛型信息是否还存在。

**✅ 标准答案**：
```java
List<Integer> list = new ArrayList<>();
list.getClass().getMethod("add", Object.class).invoke(list, "hello");
System.out.println(list.get(0)); // 输出 hello
```
🔍 **深度反思**：
- **原理**：Java 泛型是伪泛型，只在编译期检查。编译后，`List<Integer>` 变成了 `List<Object>`（或其上限），这个过程叫**类型擦除**。

---

### 🎯 今日高频面试题速览
1. **问题**：什么是类型擦除？
   **答案**：Java 泛型信息只存在于编译阶段，生成的字节码中不包含泛型信息。
2. **问题**：泛型中 `<? extends T>` 和 `<? super T>` 的区别？
   **答案**：PECS 原则（Producer Extends, Consumer Super）。extends 适合读，super 适合写。
3. **问题**：List<String> 和 List<Object> 是父子关系吗？
   **答案**：不是。泛型没有继承关系。但 `List<String>` 是 `Collection<String>` 的子类。
4. **问题**：什么是泛型的“桥方法”？
   **答案**：当一个类实现泛型接口时，编译器会自动生成一个参数为 Object 的方法（桥方法），内部调用实际的类型安全方法，以保证多态。
5. **问题**：为什么泛型类型不能是基本类型？
   **答案**：类型擦除后会变成 Object，而 Object 无法直接存放基本类型（需要装箱，但 Java 设计时为了性能和统一性未实现基本类型泛型）。

---

## 第12天：反射机制 + JDK 动态代理 — 覆盖原理点：[#14, #15]

### 编码探源题

#### 题目1：手写简易 JDK 动态代理
定义一个接口 `UserService`，实现一个 `InvocationHandler`，在方法执行前后打印日志。

**✅ 标准答案**：
```java
UserService proxy = (UserService) Proxy.newProxyInstance(
    UserService.class.getClassLoader(),
    new Class[]{UserService.class},
    (p, method, args) -> {
        System.out.println("Before...");
        Object res = method.invoke(target, args);
        System.out.println("After...");
        return res;
    }
);
```

---

### 🎯 今日高频面试题速览
1. **问题**：JDK 动态代理和 CGLIB 的区别？
   **答案**：JDK 代理基于接口，利用反射生成实现类；CGLIB 基于继承，利用 ASM 修改字节码生成子类。
2. **问题**：反射的优缺点？
   **答案**：优点是灵活、支持框架（如 Spring）；缺点是性能开销、破坏封装性（可访问 private）。
3. **问题**：如何优化反射的性能？
   **答案**：1. 缓存 `Method/Field` 对象。2. 使用 `setAccessible(true)`。3. 使用更高性能的库（如 ReflectASM）。
4. **问题**：Spring AOP 默认用哪种代理？
   **答案**：如果目标类实现了接口，默认用 JDK；否则用 CGLIB。
5. **问题**：为什么 JDK 动态代理要求必须有接口？
   **答案**：因为生成的代理类已经继承了 `Proxy` 类，而 Java 不支持多继承，所以只能通过实现接口来扩展功能。

---

## 第13天：类加载过程 + 双亲委派模型 — 覆盖原理点：[#18]

### 🎯 今日高频面试题速览
1. **问题**：类加载的五个阶段？
   **答案**：加载、验证、准备、解析、初始化。
2. **问题**：双亲委派模型是什么？
   **答案**：类加载器收到请求后，先委托父类加载器处理，只有父类无法处理时才自己加载。
3. **问题**：为什么要用双亲委派？
   **答案**：安全（防止核心类被篡改）、避免类重复加载。
4. **问题**：哪些场景破坏了双亲委派？
   **答案**：1. SPI 机制（如 JDBC 驱动）。2. 热部署（如 OSGi）。3. Tomcat 等 Web 容器（为了隔离不同 App）。
5. **问题**：`Class.forName` 和 `ClassLoader.loadClass` 的区别？
   **答案**：`forName` 会执行类的初始化（运行 `static` 块）；`loadClass` 只加载类，不初始化。

---

## 第14天：JVM 内存结构 + 对象内存布局 — 覆盖原理点：[#19, #20]

### 🎯 今日高频面试题速览
1. **问题**：JVM 运行时内存区域划分？
   **答案**：线程私有（栈、程序计数器、本地方法栈）、线程共享（堆、元空间/方法区）。
2. **问题**：什么是 TLAB？
   **答案**：Thread Local Allocation Buffer。在 Eden 区为每个线程预先分配的一块私有区域，减少多线程分配内存时的竞争（CAS 开销）。
3. **问题**：对象头里包含什么？
   **答案**：Mark Word（哈希码、GC分代年龄、锁状态等）和 Klass Pointer（指向类元数据的指针）。
4. **问题**：什么是“空间分配担保”？
   **答案**：Minor GC 前，JVM 检查老年代最大可用连续空间是否大于新生代所有对象总空间，以确保在 YGC 失败后能安全晋升到老年代。
5. **问题**：Java 8 为什么要用元空间（Metaspace）代替永久代？
   **答案**：永久代容易 OOM（受限于 JVM 堆大小）；元空间使用本地内存，更灵活。

---

## 第15天：GC 算法 + 垃圾收集器对比实验 — 覆盖原理点：[#21]

### 🎯 今日高频面试题速览
1. **问题**：如何判断一个对象可以被回收？
   **答案**：1. 引用计数法（无法解决循环引用）。2. 可达性分析法（GCRoots）。
2. **问题**：常见的 GC 算法有哪些？
   **答案**：标记-清除、复制算法、标记-整理、分代收集。
3. **问题**：CMS 和 G1 的区别？
   **答案**：CMS 追求最短停顿，基于标记-清除（有碎片）；G1 追求可预测的停顿，将堆划分为 Region，基于标记-整理（无碎片）。
4. **问题**：什么是 Stop The World (STW)？
   **答案**：GC 过程中，为了保证引用关系的静态性，必须暂停所有应用线程。
5. **问题**：什么是 ZGC？
   **答案**：Java 11 引入的超低延迟收集器，停顿时间不超过 10ms，且不受堆大小影响。

---

## 第16天：volatile + JMM 内存可见性实验 — 覆盖原理点：[#22]

### 编码探源题

#### 题目1：复现不可见性问题
线程 A 循环读取变量 `flag`，线程 B 修改 `flag = true`。如果不加 `volatile`，线程 A 能停止吗？

**✅ 标准答案**：
- **结论**：线程 A 不会停止。因为 A 线程将 `flag` 缓存在自己的工作内存（或寄存器/L1/L2缓存）中，感知不到主内存的修改。加 `volatile` 后，强制线程从主内存读取。

---

### 🎯 今日高频面试题速览
1. **问题**：volatile 的两大作用？
   **答案**：1. 保证内存可见性。2. 禁止指令重排序（内存屏障）。
2. **问题**：volatile 能保证原子性吗？
   **答案**：不能。例如 `i++` 不是原子操作，需要用 `synchronized` 或 `AtomicInteger`。
3. **问题**：什么是 DCL (Double Check Locking) 单例模式？为什么要加 volatile？
   **答案**：防止 `instance = new Singleton()` 时发生指令重排，导致其他线程拿到一个尚未初始化完成的对象。
4. **问题**：什么是 happens-before 原则？
   **答案**：JMM 中定义的两个操作之间的偏序关系。如果 A happens-before B，则 A 的执行结果对 B 可见。
5. **问题**：什么是内存屏障（Memory Barrier）？
   **答案**：一种 CPU 指令，用于禁止跨屏障的重排序，并强制刷出缓存。

---

## 第17天：synchronized 锁升级过程追踪 — 覆盖原理点：[#23]

### 🎯 今日高频面试题速览
1. **问题**：synchronized 锁升级的过程？
   **答案**：无锁 -> 偏向锁 -> 轻量级锁 -> 重量级锁。
2. **问题**：synchronized 和 ReentrantLock 的区别？
   **答案**：前者是关键字（JVM实现），后者是类（API实现）；后者支持公平锁、响应中断、超时获取。
3. **问题**：什么是偏向锁？为什么要撤销？
   **答案**：假设锁总是被同一个线程获取。当有第二个线程竞争时，偏向锁失效，撤销并升级为轻量级锁。
4. **问题**：什么是锁粗化和锁消除？
   **答案**：锁消除是 JIT 发现对象不会逃逸，直接去掉锁；锁粗化是将连续的小范围加锁合并为大范围加锁，减少加解锁开销。
5. **问题**：synchronized 的底层原理？
   **答案**：基于对象头里的 Monitor 监视器，由 `monitorenter` 和 `monitorexit` 指令实现。

---

## 第18天：AQS + ReentrantLock 源码探针 — 覆盖原理点：[#24]

### 🎯 今日高频面试题速览
1. **问题**：AQS 的核心思想？
   **答案**：`state` 变量表示资源状态 + CLH 双向队列 management 等待线程。
2. **问题**：ReentrantLock 的公平锁和非公平锁实现区别？
   **答案**：非公平锁在 `lock()` 时先尝试 CAS 修改 state，失败才进队列；公平锁会先判断队列是否有前驱节点。
3. **问题**：什么是可重入锁？
   **答案**：同一个线程可以多次获得同一把锁，AQS 通过 `state` 累加和 `owner` 线程记录来实现。
4. **问题**：Condition 的 `await/signal` 和 Object 的 `wait/notify` 区别？
   **答案**：Condition 支持多条件（多个等待队列），可以定向唤醒，更灵活。
5. **问题**：为什么 ReentrantLock 默认是非公平的？
   **答案**：性能更好。非公平锁能充分利用 CPU 的时间片，减少线程唤醒和上下文切换的开销。

---

## 第19天：ThreadPoolExecutor 线程池深度实验 — 覆盖原理点：[#25]

### 🎯 今日高频面试题速览
1. **问题**：线程池的 7 个核心参数？
   **答案**：核心线程数、最大线程数、空闲存活时间、时间单位、任务队列、线程工厂、拒绝策略。
2. **问题**：线程池的任务执行流程？
   **答案**：核心线程 -> 任务队列 -> 最大线程 -> 拒绝策略。
3. **问题**：常见的四种拒绝策略？
   **答案**：AbortPolicy（抛异常）、CallerRunsPolicy（调用者执行）、DiscardPolicy（直接丢弃）、DiscardOldestPolicy（丢弃最老任务）。
4. **问题**：线程池如何关闭？shutdown 和 shutdownNow 的区别？
   **答案**：`shutdown` 停止接收新任务，等待已提交任务完成；`shutdownNow` 尝试中断正在执行的任务，返回未执行任务列表。
5. **问题**：如何合理配置线程池大小？
   **答案**：CPU 密集型（N+1）、IO 密集型（2N）。

---

## 第20天：ThreadLocal 与内存泄漏复现 — 覆盖原理点：[#26]

### 编码探源题

#### 题目1：复现 ThreadLocal 内存泄漏
在线程池中使用 ThreadLocal 存放 10MB 的对象，任务结束后不调用 `remove`，观察内存占用。

**✅ 标准答案**：
- **原理**：`ThreadLocalMap` 的 `Entry` 中的 `key` 是弱引用，但 `value` 是强引用。如果线程不退出，`value` 就永远不会被回收。

---

### 🎯 今日高频面试题速览
1. **问题**：ThreadLocal 的原理？
   **答案**：每个线程内部都有一个 `ThreadLocalMap`，Key 是 ThreadLocal 实例，Value 是要存的对象。
2. **问题**：为什么 Entry 的 Key 要用弱引用？
   **答案**：为了让 ThreadLocal 实例能被正常回收。如果是强引用，只要线程在，ThreadLocal 就永远无法回收。
3. **问题**：ThreadLocal 常见的应用场景？
   **答案**：数据库连接管理（Session）、用户登录上下文（UserContext）、日志链路追踪（TraceID）。
4. **问题**：InheritableThreadLocal 是做什么用的？
   **答案**：让子线程能继承父线程的 ThreadLocal 变量。
5. **问题**：如何正确使用 ThreadLocal 避免内存泄漏？
   **答案**：养成在 `finally` 块中调用 `remove()` 的好习惯。

---

## 第21天：CAS + AtomicInteger + LongAdder 对比 — 覆盖原理点：[#27]

### 🎯 今日高频面试题速览
1. **问题**：什么是 CAS？它的优缺点？
   **答案**：Compare And Swap（比较并交换）。优点是无锁非阻塞；缺点是 ABA 问题、自旋开销大、只能保证一个变量的原子性。
2. **问题**：如何解决 ABA 问题？
   **答案**：使用版本号（`AtomicStampedReference`）或时间戳。
3. **问题**：AtomicInteger 的底层原理？
   **答案**：使用 `volatile` 保证可见性 + `Unsafe` 类的 CAS 操作。
4. **问题**：LongAdder 为什么比 AtomicLong 快？
   **答案**：分段累加（Cell 数组）。将竞争分散到多个 Cell 中，最后求和。
5. **问题**：Unsafe 类是做什么的？
   **答案**：提供类似 C 的指针操作，可以直接操作内存、分配内存、CAS 等，是 Java 锁机制的基石。

---

## 第22天：CountDownLatch/CyclicBarrier/Semaphore — 覆盖原理点：[#28]

### 🎯 今日高频面试题速览
1. **问题**：CountDownLatch 和 CyclicBarrier 的区别？
   **答案**：前者计数器只能用一次，后者可以用 `reset()` 重用；前者是一个线程等多个线程，后者是多个线程互等。
2. **问题**：Semaphore 的作用？
   **答案**：限流（控制并发线程数）。
3. **问题**：CountDownLatch 的底层原理？
   **答案**：基于 AQS 的共享锁实现。
4. **问题**：Exchanger 是做什么用的？
   **答案**：两个线程之间交换数据。
5. **问题**：Phaser 是什么？
   **答案**：Java 7 引入的更强大的同步工具，可以分阶段（Phase）管理线程同步，支持动态调整参与者数量。

---

## 第23天：OOM 事故复现与 MAT 分析 — 覆盖原理点：[#19]

### 🎯 今日高频面试题速览
1. **问题**：常见的 OOM 类型？
   **答案**：Heap Space（堆溢出）、Metaspace（元空间溢出）、Direct Buffer（直接内存溢出）、StackOverflow（栈溢出）。
2. **问题**：如何排查 OOM？
   **答案**：1. 使用 `-XX:+HeapDumpOnOutOfMemoryError` 生成 dump。2. 使用 MAT 或 JProfiler 分析内存快照。3. 查找占用内存最大的对象路径。
3. **问题**：什么是 Shallow Heap 和 Retained Heap？
   **答案**：Shallow Heap 是对象本身占用的内存；Retained Heap 是对象被回收后能释放的总内存（包含其引用的所有对象）。
4. **问题**：内存泄漏和内存溢出的区别？
   **答案**：内存泄漏是该回收的对象没回收；内存溢出是申请的内存超过了 JVM 最大限制。
5. **问题**：如何防止 OOM？
   **答案**：1. 及时释放引用（ThreadLocal）。2. 限制资源大小（有界队列、文件流关闭）。3. 调优 JVM 参数。

---

## 第24天：死锁复现 + jstack 诊断 — 覆盖原理点：[#24]

### 🎯 今日高频面试题速览
1. **问题**：死锁产生的四个必要条件？
   **答案**：互斥、请求与保持、不可剥夺、循环等待。
2. **问题**：如何定位死锁？
   **答案**：使用 `jstack` 查看线程状态，寻找 "found one Java-level deadlock"。
3. **问题**：如何避免死锁？
   **答案**：1. 破坏循环等待（按顺序加锁）。2. 使用 `tryLock` 带超时时间。
4. **问题**：什么是活锁（Livelock）？
   **答案**：线程没阻塞，但一直在互相谦让导致无法继续执行（如两个人在狭窄过道互相避让）。
5. **问题**：什么是饥饿（Starvation）？
   **答案**：线程长时间拿不到锁（如优先级过低或锁一直被其他线程抢占）。

---

## 第25天：CPU 100% 排查 + 线程 dump 分析 — 覆盖原理点：[#25]

### 🎯 今日高频面试题速览
1. **问题**：CPU 100% 的排查步骤？
   **答案**：1. `top` 找进程。2. `top -Hp pid` 找最耗 CPU 的线程。3. `printf "%x\n" tid` 转十六进制。4. `jstack pid | grep nid` 定位代码。
2. **问题**：导致 CPU 100% 的常见原因？
   **答案**：1. 死循环。2. 频繁 Full GC。3. 大量的计算逻辑。4. 锁竞争导致的频繁上下文切换。
3. **问题**：jstack 里的线程状态有哪些？
   **答案**：RUNNABLE、WAITING、TIMED_WAITING、BLOCKED。
4. **问题**：什么是“安全点”（Safepoint）？
   **答案**：JVM 能够暂停所有线程并执行 GC 等操作的特定代码位置。
5. **问题**：上下文切换（Context Switch）为什么开销大？
   **答案**：需要保存/恢复寄存器、程序计数器、栈信息，且会导致 CPU 缓存失效。

---

## 第26天：ThreadLocal 内存泄漏复现与修复 — 覆盖原理点：[#26]

### 🎯 今日高频面试题速览
1. **问题**：为什么在线程池中使用 ThreadLocal 要格外小心？
   **答案**：线程池中的线程是复用的，如果上一个任务存了值没 remove，下一个任务可能会读取到脏数据，且导致内存泄漏。
2. **问题**：FastThreadLocal 为什么快？
   **答案**：Netty 实现。使用数组索引访问代替哈希映射，避免了哈希碰撞。
3. **问题**：ThreadLocalMap 是如何解决哈希冲突的？
   **答案**：线性探测法（寻找下一个空位），而不是链表。
4. **问题**：ThreadLocalMap 的清理机制？
   **答案**：启发式清理（Heuristic Scanning）。在 `set` 和 `get` 时顺便清理一些过期（Key 为 null）的 Entry。
5. **问题**：如何监控 ThreadLocal 的泄漏？
   **答案**：使用内存分析工具，查看 `ThreadLocalMap$Entry` 实例的数量是否异常增长。

---

## 第27天：HashMap 1.7 死循环复现（地狱级） — 覆盖原理点：[#2]

### 🎯 今日高频面试题速览
1. **问题**：1.7 HashMap 死循环的根源？
   **答案**：多线程并发扩容时，头插法会反转链表，导致两个线程之间形成环形引用。
2. **问题**：1.8 如何解决这个死循环？
   **答案**：改为尾插法，保持扩容前后的元素相对顺序不变。
3. **问题**：1.8 HashMap 虽然没死循环，但还是不安全，为什么？
   **答案**：还是会出现数据覆盖（两个线程同时计算出相同的索引位置并覆盖）。
4. **问题**：为什么 HashMap 1.7 的扩容代码如此晦涩？
   **答案**：它是为了追求极致性能，但在多线程下完全没有防范机制。
5. **问题**：除了 ConcurrentHashMap，还有什么线程安全的 Map？
   **答案**：`Collections.synchronizedMap`、`Hashtable`。

---

## 第28天：Full GC 频繁 + JVM 调优实战 — 覆盖原理点：[#21]

### 🎯 今日高频面试题速览
1. **问题**：Full GC 频繁的原因？
   **答案**：1. 老年代空间不足。2. 元空间不足。3. `System.gc()` 调用。4. 堆外内存泄漏。
2. **问题**：JVM 调优的目标？
   **答案**：低延迟（减少 STW）和高吞吐（减少 GC 总时间）。
3. **问题**：常用的 JVM 调优参数？
   **答案**：`-Xms`, `-Xmx`, `-Xmn`, `-XX:SurvivorRatio`, `-XX:MaxTenuringThreshold`, `-XX:+UseG1GC`。
4. **问题**：什么是“动态年龄判断”？
   **答案**：如果 Survivor 区中相同年龄的对象总和大于其容量的一半，则大于等于该年龄的对象直接进入老年代。
5. **问题**：如何开启 GC 日志？
   **答案**：Java 8: `-XX:+PrintGCDetails`；Java 9+: `-Xlog:gc*`。

---

## 第29天：微型 RPC 框架骨架设计 — 覆盖原理点：[综合]

### 🎯 今日高频面试题速览
1. **问题**：RPC 的核心组件有哪些？
   **答案**：客户端（Stub）、服务端（Skeleton）、序列化、网络传输、注册中心。
2. **问题**：RPC 为什么要用动态代理？
   **答案**：为了让客户端调用远程服务像调用本地方法一样透明。
3. **问题**：常见的 RPC 协议？
   **答案**：gRPC (HTTP/2 + Protobuf)、Dubbo、Thrift。
4. **问题**：序列化协议如何选型？
   **答案**：考虑性能、大小、跨语言支持、可扩展性（如 JSON, Protobuf, Hessian）。
5. **问题**：注册中心的作用？
   **答案**：服务发现、负载均衡、健康检查（如 Zookeeper, Nacos, Consul）。

---

## 第30天：微型 RPC 框架实现 + 压测 + 调优报告 — 覆盖原理点：[综合]

### 🎯 今日高频面试题速览
1. **问题**：Netty 在 RPC 中的作用？
   **答案**：提供高性能、非阻塞的网络传输。
2. **问题**：RPC 框架中如何处理异常？
   **答案**：需要将服务端异常序列化并传回客户端抛出。
3. **问题**：如何实现 RPC 的负载均衡？
   **答案**：轮询、随机、加权轮询、一致性哈希。
4. **问题**：RPC 的超时机制如何实现？
   **答案**：客户端使用 `Future.get(timeout)` 或 Netty 的 `IdleStateHandler`。
5. **问题**：总结一下这 30 天的学习收获？
   **答案**：从基础集合源码到 JVM 调优，从并发工具到分布式设计，构建了从微观代码到宏观系统的知识图谱。

---

#### 题目5（地狱级）：模拟异常对性能的影响
编写 JMH 基准测试，对比：正常返回、抛异常并 catch、填充堆栈（`fillInStackTrace`）三种情况的性能差异。分析异常的性能开销来源（堆栈填充、锁竞争）。

```java
// 提示：fillInStackTrace() 是 native 方法，需要遍历当前线程栈帧
// 异常不应该用于流程控制，这是面试高频考点
```

**🔍 原理反思提问**：为什么"异常不能用于流程控制"？`fillInStackTrace()` 的 native 实现中做了什么？JVM 的 Fast Throw 优化是如何减少异常开销的？

**💬 面试官可能追问**：如果让你设计一个高性能的错误处理机制，你会怎么做？Result 模式（如 Rust 的 Result、Vavr 的 Try）相比异常有什么优势？

---

### 核心实现题

**手写简化版 try-with-resources**：实现一个资源管理器，支持注册多个资源，保证在代码块执行完毕后按逆序关闭资源，并正确处理关闭时的异常（suppressed）。编写单元测试覆盖：正常关闭、关闭顺序、关闭异常抑制。

```java
public class MyResourceManager {
    public static void use(Resource... resources, Consumer<Resource[]> block) {
        // 实现资源管理和异常抑制
    }
}
```

**🔍 原理反思提问**：你的实现中如何处理多个资源关闭时都抛异常的情况？`Throwable.getSuppressed()` 是如何存储被抑制异常的？你的资源管理器是线程安全的吗？

**💬 面试官可能追问**：`Cleaner` 和 `PhantomReference` 如何用于资源管理？`finalize()` 方法为什么被废弃？`Cleaner` 相比 `finalize()` 有什么优势？

---

## 第11天：泛型类型擦除与桥方法 — 覆盖原理点：[泛型核心原理]

### 编码探源题

#### 题目1：验证泛型类型擦除
编写代码：创建 `List<String>` 和 `List<Integer>`，通过反射向两者添加不同类型的元素。验证运行时泛型信息被擦除，两者是同一个 Class。

```java
// 提示：List<String>.getClass() == List<Integer>.getClass() 为 true
// 通过反射可以绕过泛型检查
// 泛型信息只在编译期存在（class 文件的 Signature 属性中保留声明信息）
```

**🔍 原理反思提问**：类型擦除后，`List<String>` 的 `get()` 返回的是 Object 还是 String？编译器是如何插入 checkcast 指令的？`Signature` 属性中保留了哪些泛型信息？

**💬 面试官可能追问**：`Class.getTypeParameters()` 返回的是什么？运行时能获取到泛型的具体类型吗？Gson/Jackson 是如何通过 `TypeToken` 获取泛型类型的？

---

#### 题目2：复现桥方法（Bridge Method）
编写代码：定义一个泛型父类和子类，子类重写父类方法时使用具体类型。用 `javap -c` 查看字节码，验证编译器自动生成了桥方法。

```java
class Parent<T> {
    public void countDown() { sync.releaseShared(1); }
}
```

**🔍 原理反思提问**：你的 `CountDownLatch` 的 `tryAcquireShared()` 是如何实现的？为什么 `countDown()` 使用 `releaseShared()`？共享模式下 `releaseShared()` 如何唤醒所有等待线程？

**💬 面试官可能追问**：`Phaser` 的 `arriveAndAwaitAdvance()` 和 `arriveAndDeregister()` 有什么区别？`Phaser` 的 `forceTermination()` 和 `awaitAdvanceInterruptibly()` 是如何配合的？

---

## 第23天：OOM 事故复现与 MAT 分析 — 覆盖原理点：[#19 JVM 内存结构]

### 编码探源题

#### 题目1：复现堆 OOM 并分析 heap dump
编写代码：不断向 ArrayList 添加对象直到堆 OOM。使用 `-Xmx100m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./heap.hprof` 生成 dump 文件。使用 MAT 分析最大对象和 GC Roots 路径。

```java
// 提示：OOM 时 JVM 会自动生成 heap dump
// MAT 的 Dominator Tree 可以找到占用内存最多的对象
// Leak Suspects Report 自动分析泄漏嫌疑
```

**🔍 原理反思提问**：`-XX:+HeapDumpOnOutOfMemoryError` 和 `-XX:+HeapDumpBeforeFullGC` 有什么区别？`jmap -dump:live` 和自动 dump 的区别是什么（live 会触发 Full GC）？

**💬 面试官可能追问**：MAT 的 Shallow Heap 和 Retained Heap 有什么区别？Dominator Tree 和 Histogram 各用于什么分析场景？OQL（Object Query Language）如何使用？

---

#### 题目2：复现 Metaspace OOM
编写代码：使用 CGLIB 或 Javassist 动态生成大量类，直到 Metaspace OOM。使用 `-XX:MaxMetaspaceSize=50m -XX:+TraceClassLoading` 观察类加载情况。

```java
// 提示：每个动态生成的类占用 Metaspace
// 类加载器泄漏是 Metaspace OOM 的常见原因
// 使用 -XX:+TraceClassUnloading 观察类卸载
```

**🔍 原理反思提问**：Metaspace 在 Java 8 中替代了永久代，这个变化解决了什么问题？`-XX:MetaspaceSize` 和 `-XX:MaxMetaspaceSize` 的区别是什么？Metaspace 的 GC 时机是什么？

**💬 面试官可能追问**：`-XX:CompressedClassSpaceSize` 的作用是什么？压缩类空间和 Metaspace 的关系是什么？`jcmd <pid> VM.metaspace` 可以查看什么信息？

---

#### 题目3：复现栈溢出（StackOverflowError）
编写代码：无限递归导致栈溢出。使用 `-Xss128k` 设置小栈空间加速溢出。分析递归深度和栈大小的关系。

```java
// 提示：每次方法调用占用一个栈帧（局部变量表 + 操作数栈 + 返回地址等）
// 栈深度 = 栈大小 / 单帧大小
// 局部变量越多，单帧越大，栈深度越小
```

**🔍 原理反思提问**：`StackOverflowError` 和 `OutOfMemoryError: unable to create new native thread` 有什么区别？线程栈大小和能创建的线程数有什么关系（总内存 / 栈大小 ≈ 最大线程数）？

**💬 面试官可能追问**：如何通过 `jstack` 分析栈溢出？`jstack -m` 和 `jstack -l` 的区别是什么？`Thread.getAllStackTraces()` 可以获取什么信息？

---

#### 题目4：复现直接内存 OOM
编写代码：不断 `ByteBuffer.allocateDirect()` 但不释放，直到直接内存 OOM。使用 `-XX:MaxDirectMemorySize=50m` 限制直接内存大小。

```java
// 提示：DirectByteBuffer 的回收依赖 GC 和 Cleaner
// 如果 DirectByteBuffer 对象被 GC，Cleaner 会释放直接内存
// 但如果 DirectByteBuffer 对象一直被引用，直接内存不会释放
```

**🔍 原理反思提问**：`ByteBuffer.allocateDirect()` 和 `Unsafe.allocateMemory()` 的区别是什么？直接内存的分配和释放分别调用什么方法？`-XX:MaxDirectMemorySize` 默认等于 `-Xmx`，为什么？

**💬 面试官可能追问**：Netty 的 `PooledDirectByteBuf` 是如何管理直接内存的？`PlatformDependent.allocateDirectNoCleaner()` 和 `ByteBuffer.allocateDirect()` 有什么区别？

---

#### 题目5（地狱级）：综合 OOM 排查实战
编写一个包含多种 OOM 风险的 Web 应用（模拟），使用 JMeter 压测触发 OOM。使用 `jstat`、`jmap`、`jstack`、MAT 进行完整的 OOM 排查流程。输出排查报告。

```java
// 提示：排查流程
// 1. jstat -gcutil 观察 GC 情况
// 2. jmap -histo 查看对象统计
// 3. jmap -dump 生成 heap dump
// 4. MAT 分析 Dominator Tree 和 Leak Suspects
// 5. jstack 分析线程状态
```

**🔍 原理反思提问**：OOM 排查的完整流程是什么？`jmap -histo:live` 和 `jmap -histo` 的区别是什么？`jcmd <pid> GC.heap_dump` 和 `jmap -dump` 有什么区别？

**💬 面试官可能追问**：Arthas 的 `heapdump` 和 `memory` 命令如何使用？`vmtool` 命令如何强制 GC？`dashboard` 命令可以实时监控哪些指标？

---

### 核心实现题

**手写简化版 OOM 监控器**：实现一个内存监控器，定时检查堆内存使用率，超过阈值时自动生成 heap dump 并发送告警。使用 `MemoryMXBean` 和 `HotSpotDiagnosticMXBean`。

```java
public class OOMDetector {
    private static final double THRESHOLD = 0.85;
    public void monitor() {
        // 定时检查堆使用率
        // 超过阈值 → dumpHeap() → 告警
    }
}
```

**🔍 原理反思提问**：`HotSpotDiagnosticMXBean.dumpHeap()` 的参数有哪些？`-XX:+HeapDumpOnOutOfMemoryError` 和手动 dump 的区别是什么？如何避免 dump 文件过大？

**💬 面试官可能追问**：`-XX:HeapDumpPath` 如何设置？`-XX:+ExitOnOutOfMemoryError` 的作用是什么？`-XX:+CrashOnOutOfMemoryError` 和 `ExitOnOutOfMemoryError` 有什么区别？

---

## 第24天：死锁复现 + jstack 诊断 — 覆盖原理点：[#24 AQS 框架与 ReentrantLock]

### 编码探源题

#### 题目1：复现经典死锁
编写代码：两个线程，线程 1 持有锁 A 等待锁 B，线程 2 持有锁 B 等待锁 A。使用 `synchronized` 实现。使用 `jstack` 分析死锁。

```java
// 提示：死锁的四个必要条件
// 1. 互斥：资源只能被一个线程持有
// 2. 持有并等待：持有资源的同时等待其他资源
// 3. 不可剥夺：资源不能被强制释放
// 4. 循环等待：形成资源等待环
```

**🔍 原理反思提问**：`jstack` 输出中的 `Found one Java-level deadlock` 是如何检测的？JVM 如何自动检测死锁（`ThreadMXBean.findDeadlockedThreads()`）？死锁和活锁（Livelock）有什么区别？

**💬 面试官可能追问**：如何避免死锁（按顺序加锁、`tryLock` 超时、一次性获取所有锁）？`ReentrantLock.tryLock(timeout, unit)` 如何帮助避免死锁？

---

#### 题目2：复现哲学家就餐问题
编写代码：5 个哲学家（线程），5 根筷子（锁），每个哲学家需要左右两根筷子才能吃饭。复现死锁。使用资源排序法（按筷子编号顺序获取）解决死锁。

```java
// 提示：哲学家就餐是经典的死锁场景
// 解决方案：按编号顺序获取筷子（破坏循环等待）
// 或使用 tryLock 超时（破坏持有并等待）
```

**🔍 原理反思提问**：资源排序法为什么能解决死锁（破坏循环等待条件）？`tryLock` 超时法为什么能解决死锁（破坏持有并等待条件）？两种方案各有什么优缺点？

**💬 面试官可能追问**：`Semaphore` 如何用于解决哲学家就餐问题（限制同时吃饭的哲学家数量）？`StampedLock` 的 `tryConvertToWriteLock()` 如何避免死锁？

---

#### 题目3：使用 ThreadMXBean 检测死锁
编写代码：使用 `ThreadMXBean.findDeadlockedThreads()` 编程方式检测死锁。打印死锁线程的堆栈和锁信息。

```java
// 提示：ThreadMXBean 可以检测
// 1. 死锁线程（findDeadlockedThreads）
// 2. 等待线程（findMonitorDeadlockedThreads）
// 3. 线程 CPU 时间（getThreadCpuTime）
```

**🔍 原理反思提问**：`findDeadlockedThreads()` 和 `findMonitorDeadlockedThreads()` 有什么区别？前者检测 `Lock` 死锁，后者检测 `synchronized` 死锁。`ThreadInfo.getLockedMonitors()` 和 `getLockedSynchronizers()` 有什么区别？

**💬 面试官可能追问**：如何实现一个死锁自动检测和恢复的框架？`ScheduledExecutorService` 如何用于定时检测死锁？

---

#### 题目4：阅读 jstack 输出并分析
使用 `jstack -l <pid>` 获取线程 dump，逐行分析输出内容：线程状态（RUNNABLE/BLOCKED/WAITING/TIMED_WAITING）、锁信息、等待条件。

```
"Thread-1" #12 prio=5 os_prio=0 tid=0x... nid=0x... waiting for monitor entry
   java.lang.Thread.State: BLOCKED (on object monitor)
```

**🔍 原理反思提问**：线程的 6 种状态（NEW/RUNNABLE/BLOCKED/WAITING/TIMED_WAITING/TERMINATED）各代表什么？`BLOCKED` 和 `WAITING` 的区别是什么？`jstack` 输出中的 `nid` 和 `tid` 分别是什么？

**💬 面试官可能追问**：`jstack -F` 和普通 `jstack` 的区别是什么？`jstack -m` 输出的混合模式堆栈包含什么？`jcmd <pid> Thread.print` 和 `jstack` 有什么区别？

---

#### 题目5（地狱级）：模拟数据库死锁 + 分布式死锁
编写代码：模拟两个事务相互等待对方持有的行锁（数据库死锁）。扩展：模拟两个微服务相互等待对方释放资源（分布式死锁）。分析分布式死锁的检测难度。

```java
// 提示：数据库死锁通常由数据库自动检测并回滚其中一个事务
// 分布式死锁更难检测，需要全局锁管理器或超时机制
// Redisson 的 RLock 使用看门狗（Watchdog）自动续期，避免死锁
```

**🔍 原理反思提问**：分布式锁如何避免死锁（设置过期时间、看门狗自动续期）？Redisson 的 `RLock.lock()` 和 `tryLock()` 在死锁处理上有什么区别？

**💬 面试官可能追问**：ZooKeeper 的临时顺序节点如何实现分布式锁？etcd 的 Lease 机制如何避免死锁？Redis 的 `SET NX PX` 和 Redlock 算法有什么区别？

---

### 核心实现题

**手写简化版死锁检测器**：实现一个死锁检测器，维护线程和锁的等待图（Wait-for Graph），使用深度优先搜索检测环（死锁）。编写单元测试覆盖：有死锁、无死锁、多环死锁。

```java
public class DeadlockDetector {
    // 维护 Thread → 持有的锁集合
    // 维护 Thread → 等待的锁集合
    public Set<Thread> detectDeadlock() {
        // 构建等待图 → DFS 检测环
    }
}
```

**🔍 原理反思提问**：你的死锁检测算法的时间复杂度是多少？等待图（Wait-for Graph）和资源分配图（Resource Allocation Graph）有什么区别？如何检测涉及多个锁的复杂死锁环？

**💬 面试官可能追问**：JVM 的 `ThreadMXBean.findDeadlockedThreads()` 是如何实现的？它检测的是 Java 层面的死锁还是 JVM 层面的死锁？

---

## 第25天：CPU 100% 排查 + 线程 dump 分析 — 覆盖原理点：[#25 ThreadPoolExecutor 线程池原理]

### 编码探源题

#### 题目1：复现 CPU 100%（死循环）
编写代码：一个线程执行死循环（`while(true){}`），导致 CPU 100%。使用 `top`（Linux）或任务管理器（Windows）定位高 CPU 进程，使用 `jstack` 定位问题线程。

```java
// 提示：排查流程
// 1. top -H -p <pid> 找到高 CPU 线程
// 2. 将线程 ID 转为十六进制
// 3. jstack <pid> | grep <hex_tid> -A 20
// 4. 定位到具体代码行
```

**🔍 原理反思提问**：`top -H -p` 中的 `-H` 参数的作用是什么？线程 ID（tid）和 native 线程 ID（nid）的关系是什么？`jstack` 输出中的 `nid` 为什么是十六进制？

**💬 面试官可能追问**：Windows 上如何排查 CPU 100%（Process Explorer、`jstack`）？`jvisualvm` 的 Sampler 和 Profiler 有什么区别？

---

#### 题目2：复现 CPU 100%（频繁 GC）
编写代码：不断创建对象并放弃引用，触发频繁 YGC 和 Full GC，导致 CPU 被 GC 线程占用。使用 `jstat -gcutil` 观察 GC 频率。

```java
// 提示：频繁 GC 导致 CPU 100% 的特征
// 1. jstat 显示 FGC 次数快速增长
// 2. GC 日志显示频繁的 Full GC
// 3. 堆内存可能接近满载
```

**🔍 原理反思提问**：频繁 Full GC 的原因有哪些（内存泄漏、堆太小、大对象分配）？`jstat -gccause` 输出的 LGCC（Last GC Cause）和 GCC（GC Cause）有什么区别？

**💬 面试官可能追问**：`-XX:+UseGCOverheadLimit` 的作用是什么？GC Overhead Limit Exceeded 错误是什么？如何通过 `-XX:-UseGCOverheadLimit` 关闭？

---

#### 题目3：复现 CPU 100%（CAS 自旋）
编写代码：64 个线程同时对同一个 `AtomicLong` 进行 CAS 操作，导致大量 CPU 消耗在自旋上。使用 `jstack` 观察线程状态（RUNNABLE 但实际在自旋）。

```java
// 提示：CAS 自旋的线程状态是 RUNNABLE
// 但实际在消耗 CPU 做无用功
// 使用 LongAdder 替代 AtomicLong 减少竞争
```

**🔍 原理反思提问**：CAS 自旋和 `synchronized` 阻塞的区别是什么？为什么 CAS 自旋的线程状态是 RUNNABLE 而不是 BLOCKED？`jstack` 如何区分真正在工作的线程和自旋的线程？

**💬 面试官可能追问**：`ThreadMXBean.getThreadCpuTime()` 如何用于定位高 CPU 线程？`getThreadUserTime()` 和 `getThreadCpuTime()` 的区别是什么？

---

#### 题目4：编写 CPU 监控脚本
编写 Java 代码：使用 `OperatingSystemMXBean.getProcessCpuLoad()` 和 `ThreadMXBean.getAllThreadIds()` 监控进程和线程的 CPU 使用率。定时输出 Top N CPU 线程。

```java
// 提示：OperatingSystemMXBean 可以获取进程 CPU 使用率
// ThreadMXBean 可以获取每个线程的 CPU 时间
// 两次采样之间的 CPU 时间差 / 时间差 = CPU 使用率
```

**🔍 原理反思提问**：`getProcessCpuLoad()` 返回的值范围是什么（0.0-1.0 或负数表示不可用）？`getThreadCpuTime()` 返回的是纳秒吗？`getCurrentThreadCpuTime()` 和 `getThreadCpuTime(id)` 有什么区别？

**💬 面试官可能追问**：`com.sun.management.OperatingSystemMXBean` 和 `java.lang.management.OperatingSystemMXBean` 有什么区别？前者提供了更多方法（如 `getFreePhysicalMemorySize()`）。

---

#### 题目5（地狱级）：综合 CPU 100% 排查实战
编写一个包含多种 CPU 问题的应用（死循环、频繁 GC、CAS 自旋、死锁），使用 `top` + `jstack` + `jstat` + `jmap` 进行完整排查。输出排查报告，包含问题定位、根因分析、修复建议。

```java
// 提示：综合排查流程
// 1. top 定位高 CPU 进程
// 2. top -H -p 定位高 CPU 线程
// 3. jstack 查看线程堆栈
// 4. jstat -gcutil 查看 GC 情况
// 5. jmap -histo 查看对象分布
// 6. 综合分析，定位根因
```

**🔍 原理反思提问**：CPU 100% 的常见原因有哪些（死循环、频繁 GC、CAS 自旋、正则回溯、序列化/反序列化）？如何通过 `jstack` 多次采样对比定位循环代码？

**💬 面试官可能追问**：Arthas 的 `thread -n 3` 命令如何定位 Top 3 CPU 线程？`thread -b` 如何检测死锁？`monitor` 和 `watch` 命令如何监控方法调用？

---

### 核心实现题

**手写简化版 CPU 监控器**：实现一个 CPU 监控器，定时采集进程和线程的 CPU 使用率，支持 Top N 排序和阈值告警。编写单元测试覆盖：CPU 数据采集、排序、告警。

```java
public class CPUMonitor {
    public void report() {
        // 获取进程 CPU 使用率
        // 获取各线程 CPU 使用率
        // 排序输出 Top N
    }
}
```

**🔍 原理反思提问**：你的 CPU 监控器如何计算线程 CPU 使用率（两次采样差值 / 时间差）？`ThreadMXBean.getThreadCpuTime()` 的精度是多少？如何避免监控器自身的 CPU 开销过大？

**💬 面试官可能追问**：`Micrometer` 的 `JvmThreadMetrics` 是如何采集线程指标的？`Prometheus` 的 `jvm_threads_*` 指标有哪些？

---

## 第26天：ThreadLocal 内存泄漏复现与修复 — 覆盖原理点：[#26 ThreadLocal 与内存泄漏]

### 编码探源题

#### 题目1：复现 ThreadLocal 内存泄漏（完整版）
编写代码：在线程池中使用 ThreadLocal set 10MB 的大对象但不 remove。使用 `-Xmx50m` 限制堆大小，观察 OOM。通过反射统计 ThreadLocalMap 中 key 为 null 的 Entry 数量。

```java
// 提示：使用 ThreadPoolExecutor 固定 5 个线程
// 每个任务 set 10MB 数据但不 remove
// 观察堆内存持续增长直到 OOM
```

**🔍 原理反思提问**：为什么线程池场景下 ThreadLocal 内存泄漏更严重（线程复用导致 ThreadLocalMap 不释放）？`ThreadLocalMap` 的 `expungeStaleEntry()` 在什么时机被调用？

**💬 面试官可能追问**：如果 ThreadLocal 对象本身是 static 的，还会内存泄漏吗？static ThreadLocal 的 value 什么时候应该清理？

---

#### 题目2：验证 ThreadLocal.remove() 的清理效果
在题目1的基础上，每个任务在 finally 中调用 `ThreadLocal.remove()`。对比清理前后的堆内存使用情况。验证 remove 能有效防止内存泄漏。

```java
// 提示：remove() 会从 ThreadLocalMap 中删除 Entry
// 并执行 expungeStaleEntry() 清理后续的 null key Entry
```

**🔍 原理反思提问**：`ThreadLocal.remove()` 的内部实现是什么？它除了删除当前 Entry，还会清理其他 null key 的 Entry 吗？`expungeStaleEntry()` 的清理范围是什么？

**💬 面试官可能追问**：`ThreadLocal` 的 `set(null)` 和 `remove()` 有什么区别？`set(null)` 能替代 `remove()` 吗（不能，Entry 仍然存在，只是 value 为 null）？

---

#### 题目3：验证 InheritableThreadLocal 在线程池中的问题
编写代码：父线程使用 `InheritableThreadLocal` set 值，提交任务到线程池。验证：第一次任务能获取到父线程的值，但后续任务（复用线程）可能获取到上一个任务修改的值。

```java
// 提示：InheritableThreadLocal 在创建子线程时复制值
// 线程池复用线程，不会重新复制
// 导致数据错乱
```

**🔍 原理反思提问**：`InheritableThreadLocal` 的值是在什么时机传递给子线程的（`Thread.init()` 中）？为什么线程池中 `InheritableThreadLocal` 不可靠？阿里巴巴的 `TransmittableThreadLocal` 是如何解决这个问题的？

**💬 面试官可能追问**：`TransmittableThreadLocal` 的原理是什么（在任务提交时捕获值，在任务执行前设置值）？`ThreadPoolExecutor` 的 `beforeExecute()` 和 `afterExecute()` 如何配合 TTL？

---

#### 题目4：阅读 ThreadLocalMap.expungeStaleEntry() 源码
打开 `ThreadLocal.ThreadLocalMap.expungeStaleEntry()` 源码，理解其清理逻辑：从 staleSlot 开始，向后遍历，清理 null key 的 Entry，同时重新哈希非 null key 的 Entry。

**🔍 原理反思提问**：`expungeStaleEntry()` 为什么需要重新哈希后续的 Entry？开放地址法中，删除一个 Entry 后，后续的 Entry 可能因为探测链断裂而无法被找到。重新哈希解决了这个问题。

**💬 面试官可能追问**：`cleanSomeSlots()` 和 `expungeStaleEntry()` 的区别是什么？`cleanSomeSlots()` 为什么是 O(log n) 而不是 O(n)？`rehash()` 方法在什么情况下触发扩容？

---

#### 题目5（地狱级）：对比不同 ThreadLocal 实现的性能
编写 JMH 基准测试，对比：JDK `ThreadLocal`、Netty `FastThreadLocal`、阿里巴巴 `TransmittableThreadLocal` 的 get/set 性能。分析各自的实现差异和适用场景。

```java
// 提示：JDK ThreadLocal 使用 ThreadLocalMap（哈希表 + 开放地址法）
// FastThreadLocal 使用数组（索引直接访问）
// TransmittableThreadLocal 在 ThreadLocal 基础上增加了值传递
```

**🔍 原理反思提问**：`FastThreadLocal` 为什么比 JDK `ThreadLocal` 快（数组索引 vs 哈希查找）？`FastThreadLocal` 的 `InternalThreadLocalMap` 是如何与 `FastThreadLocalThread` 配合的？

**💬 面试官可能追问**：`Netty` 的 `FastThreadLocal.destroy()` 方法的作用是什么？为什么 `FastThreadLocal` 需要手动清理？`InternalThreadLocalMap` 的 `indexedVariables` 数组是如何扩容的？

---

### 核心实现题

**手写简化版 TransmittableThreadLocal**：实现一个支持线程池传递的 ThreadLocal，在任务提交时捕获当前值，在任务执行前设置值。编写单元测试覆盖：父子线程传递、线程池传递、remove 清理。

```java
public class MyTransmittableThreadLocal<T> {
    public T get() { ... }
    public void set(T value) { ... }
    public void remove() { ... }
    // 捕获当前线程的所有 TTL 值
    public static Object capture() { ... }
    // 在目标线程中设置捕获的值
    public static Object replay(Object captured) { ... }
    // 清理目标线程中设置的值
    public static void restore(Object backup) { ... }
}
```

**🔍 原理反思提问**：你的 `capture()` 方法如何获取所有 TTL 的值？`replay()` 方法如何保存目标线程的旧值（用于 restore）？`restore()` 方法如何恢复旧值？

**💬 面试官可能追问**：`TransmittableThreadLocal` 的 `Transmitter` 是如何与线程池集成的（`TtlExecutors.getTtlExecutor()`）？`TtlRunnable` 和 `TtlCallable` 是如何包装任务的？

---

## 第27天：HashMap 1.7 死循环复现（地狱级）— 覆盖原理点：[#2 HashMap 扩容机制]

### 编码探源题

#### 题目1：手写 HashMap 1.7 头插法 transfer
实现 HashMap 1.7 的 `transfer()` 方法（头插法）。编写单线程测试验证扩容正确性。

```java
// 提示：1.7 的 transfer() 核心逻辑
// void transfer(Entry[] newTable) {
//     for (Entry e : table) {
//         while (null != e) {
//             Entry next = e.next;
//             int i = indexFor(e.hash, newCapacity);
//             e.next = newTable[i];  // 头插法
//             newTable[i] = e;
//             e = next;
//         }
//     }
// }
```

**🔍 原理反思提问**：头插法和尾插法的区别是什么？头插法为什么会导致链表反转？1.8 的尾插法（高低位链表）是如何保持链表顺序的？

**💬 面试官可能追问**：1.7 的头插法设计初衷是什么（最近插入的元素可能被更频繁访问，缓存局部性）？为什么这个设计在并发场景下是致命的？

---

#### 题目2：复现 HashMap 1.7 并发扩容死循环
使用手写的 1.7 风格 HashMap，两个线程同时 put 触发扩容。复现链表成环导致死循环（`while(e != null)` 永远无法结束）。

```java
// 提示：死循环的形成过程
// 线程 1 执行 transfer，记录 e 和 next 后被挂起
// 线程 2 完成 transfer，链表顺序反转
// 线程 1 恢复执行，使用旧的 e 和 next，形成环
```

**🔍 原理反思提问**：链表成环的具体过程是什么（画图说明）？为什么 1.8 的尾插法不会形成环？除了死循环，头插法还会导致什么问题（数据丢失）？

**💬 面试官可能追问**：如果面试官问你"你遇到过 HashMap 的并发问题吗"，你如何用这个实验的经历来回答？`Hashtable` 和 `ConcurrentHashMap` 是如何避免这个问题的？

---

#### 题目3：验证 HashMap 1.7 扩容时的数据丢失
编写代码：两个线程同时 put 触发扩容，验证某些 key 的数据可能丢失（一个线程的 put 结果被另一个线程覆盖）。

```java
// 提示：1.7 的 transfer 不是原子的
// 线程 1 迁移了部分链表后挂起
// 线程 2 完成迁移
// 线程 1 恢复后继续迁移，可能覆盖线程 2 的结果
```

**🔍 原理反思提问**：数据丢失和死循环的根因是否相同（都是并发修改链表结构）？1.8 的 `resize()` 如何通过高低位链表拆分避免数据丢失？

**💬 面试官可能追问**：`ConcurrentHashMap` 1.8 的 `transfer()` 是如何通过 `ForwardingNode` 和 CAS 保证并发安全的？多线程协同扩容时，如何保证每个桶只被一个线程处理？

---

#### 题目4：对比 HashMap 1.7 和 1.8 的 resize 实现
打开 JDK 7 和 JDK 8 的 `HashMap.resize()`/`transfer()` 源码，逐行对比两者的实现差异。画出两种实现的流程图。

**🔍 原理反思提问**：1.8 的 `resize()` 中 `(e.hash & oldCap) == 0` 判断的原理是什么？为什么这个判断等价于判断新索引是否等于旧索引？`loHead`/`loTail` 和 `hiHead`/`hiTail` 的作用是什么？

**💬 面试官可能追问**：1.8 的 `resize()` 中红黑树的拆分（`split()`）是如何实现的？`TreeNode.split()` 和链表拆分的逻辑类似吗？

---

#### 题目5（地狱级）：模拟 HashMap 1.7 死循环 + jstack 分析
完整复现 HashMap 1.7 死循环场景，使用 `jstack` 分析死循环线程的堆栈。验证线程卡在 `transfer()` 方法的 `while(e != null)` 循环中。

```java
// 提示：使用 CountDownLatch 控制两个线程同时触发扩容
// 初始容量设为 2，加载因子 0.75，快速触发扩容
// 使用 jstack 观察线程状态（RUNNABLE，卡在 transfer 方法）
```

**🔍 原理反思提问**：`jstack` 输出中，死循环线程的堆栈有什么特征（卡在同一个方法，CPU 100%）？如何通过多次 `jstack` 采样确认死循环位置？

**💬 面试官可能追问**：如果生产环境遇到 HashMap 死循环导致的 CPU 100%，如何快速定位和修复？`jmap -dump` 后如何分析 HashMap 的链表结构？

---

### 核心实现题

**手写 HashMap 1.7 和 1.8 对比实现**：分别实现 1.7 风格（头插法）和 1.8 风格（尾插法 + 高低位链表）的 HashMap。编写单元测试对比：单线程扩容正确性、链表顺序保持、性能差异。

```java
public class HashMap17<K,V> {
    // 头插法 transfer
}

public class HashMap18<K,V> {
    // 尾插法 + 高低位链表 resize
}
```

**🔍 原理反思提问**：你的两种实现在扩容后链表顺序有什么不同？1.7 的链表反转对迭代器有什么影响？1.8 的链表顺序保持对并发安全有什么帮助？

**💬 面试官可能追问**：如果让你在 1.8 的基础上实现一个线程安全的 HashMap，你会怎么做？`Collections.synchronizedMap()` 和 `ConcurrentHashMap` 的区别是什么？

---

## 第28天：Full GC 频繁 + JVM 调优实战 — 覆盖原理点：[#21 GC 算法与垃圾收集器对比]

### 编码探源题

#### 题目1：复现 Full GC 频繁（内存泄漏）
编写代码：不断向静态集合添加对象，导致老年代持续增长，触发频繁 Full GC。使用 `-Xlog:gc*` 观察 GC 日志。

```java
// 提示：静态集合持有对象引用，导致对象无法被 GC
// 老年代逐渐填满 → Full GC → 无法回收 → 再次 Full GC
// 最终可能 OOM
```

**🔍 原理反思提问**：Full GC 频繁的常见原因有哪些（内存泄漏、大对象分配、`System.gc()` 调用、Metaspace 不足）？如何通过 GC 日志判断 Full GC 的原因？

**💬 面试官可能追问**：`-XX:+DisableExplicitGC` 的作用是什么？`System.gc()` 在什么情况下会触发 Full GC？`-XX:+ExplicitGCInvokesConcurrent` 的作用是什么？

---

#### 题目2：复现 Full GC 频繁（大对象分配）
编写代码：不断分配超过 `-XX:PretenureSizeThreshold` 的大对象，直接进入老年代，导致老年代快速填满触发 Full GC。

```java
// 提示：大对象直接分配在老年代
// 如果大对象频繁创建和释放，老年代碎片化严重
// CMS 和 G1 对大对象的处理方式不同
```

**🔍 原理反思提问**：`-XX:PretenureSizeThreshold` 默认值是 0（所有对象都在 Eden 分配）。设置多大合适？G1 的 Humongous Object 和 CMS 的大对象处理有什么区别？

**💬 面试官可能追问**：`-XX:G1HeapRegionSize` 和 Humongous Object 的关系是什么？Humongous Object 为什么会导致 GC 性能问题？

---

#### 题目3：JVM 调优实战（调整堆大小和 GC 参数）
编写一个模拟 Web 应用的程序（间歇性创建对象），分别使用默认参数和调优后的参数运行。对比 GC 频率、暂停时间、吞吐量。

```java
// 提示：调优参数示例
// -Xms2g -Xmx2g -Xmn1g
// -XX:+UseG1GC -XX:MaxGCPauseMillis=200
// -XX:+PrintGCDetails -XX:+PrintGCDateStamps
```

**🔍 原理反思提问**：`-Xms` 和 `-Xmx` 设为相同值的好处是什么（避免堆扩容/缩容的开销）？`-Xmn` 如何设置（新生代大小一般为堆的 1/4 到 1/2）？G1 的 `-XX:MaxGCPauseMillis` 是硬性保证吗？

**💬 面试官可能追问**：`-XX:ParallelGCThreads` 和 `-XX:ConcGCThreads` 如何设置？`-XX:+UseStringDeduplication`（G1 字符串去重）的原理是什么？

---

#### 题目4：阅读 GC 日志并分析调优方向
收集不同 GC 参数下的 GC 日志，分析：GC 频率、暂停时间分布、内存回收效率。根据分析结果提出调优建议。

```
[GC pause (G1 Evacuation Pause) (young), 0.0123456 secs]
   [Parallel Time: 11.5 ms, GC Workers: 4]
   ...
   [Eden: 512M(512M)->0B(512M) Survivors: 64M->64M Heap: 1.2G(2G)->800M(2G)]
```

**🔍 原理反思提问**：G1 GC 日志中 `Eden: 512M(512M)->0B(512M)` 的含义是什么？`Parallel Time` 和 `GC Workers` 的关系是什么？`Ref Proc` 和 `Ref Enq` 阶段做了什么？

**💬 面试官可能追问**：`-XX:+PrintAdaptiveSizePolicy` 输出的信息如何解读？`-XX:+PrintTenuringDistribution` 输出的 age 分布如何用于调优 `MaxTenuringThreshold`？

---

#### 题目5（地狱级）：综合 JVM 调优实战
编写一个模拟高并发 Web 应用的程序，使用 JMeter 压测。分别使用 Serial、Parallel、CMS、G1 收集器，对比 TPS、响应时间、GC 暂停。输出调优报告，包含参数选择和理由。

```java
// 提示：调优目标
// 1. 吞吐量优先：Parallel GC，-XX:GCTimeRatio=19
// 2. 暂停时间优先：G1 GC，-XX:MaxGCPauseMillis=100
// 3. 内存优先：减小 -Xmx，增加 GC 频率但减少单次暂停
```

**🔍 原理反思提问**：如何根据应用特点选择 GC 收集器（批处理用 Parallel，Web 应用用 G1，大内存用 ZGC）？`-XX:+UseZGC` 和 `-XX:+UseShenandoahGC` 的适用场景是什么？

**💬 面试官可能追问**：ZGC 的染色指针（Colored Pointers）和读屏障（Load Barrier）是什么？Shenandoah 的 Brooks Pointer 和 ZGC 的染色指针有什么区别？

---

### 核心实现题

**手写简化版 GC 日志分析器**：实现一个 GC 日志解析器，解析 GC 日志中的关键指标（GC 类型、暂停时间、内存变化），统计 GC 频率、平均暂停时间、吞吐量。编写单元测试覆盖：Young GC、Full GC、Mixed GC 日志解析。

```java
public class GCLogAnalyzer {
    public void parse(String logLine) {
        // 解析 GC 日志行
        // 提取 GC 类型、暂停时间、内存变化
    }
    public GCReport generateReport() {
        // 统计 GC 频率、平均暂停时间、吞吐量
    }
}
```

**🔍 原理反思提问**：你的解析器如何处理不同 GC 收集器的日志格式差异？Java 8 和 Java 9+ 的 GC 日志格式有什么不同？`-Xlog:gc*:file=gc.log:time,level,tags` 的输出格式是什么？

**💬 面试官可能追问**：`GCViewer` 和 `GCEasy` 等工具的原理是什么？如何通过 `JMX` 实时获取 GC 信息（`GarbageCollectorMXBean`）？

---

## 第29天：微型 RPC 框架骨架设计 — 覆盖原理点：[综合：动态代理 + 序列化 + 网络通信 + 注册中心]

### 编码探源题

#### 题目1：设计 RPC 框架的协议结构
设计一个简单的 RPC 协议：包含魔数（4 字节）、版本号（1 字节）、序列化类型（1 字节）、消息类型（1 字节）、请求 ID（8 字节）、数据长度（4 字节）、数据体（N 字节）。编写编解码器。

```java
// 提示：协议设计参考 Dubbo 的协议头
// 魔数用于快速识别协议（如 0xCAFEBABE）
// 请求 ID 用于匹配请求和响应
// 数据长度用于解决粘包拆包
```

**🔍 原理反思提问**：为什么需要魔数？序列化类型字段的作用是什么（支持多种序列化方式）？请求 ID 如何生成（`AtomicLong` 递增）？数据长度字段如何解决 TCP 粘包拆包？

**💬 面试官可能追问**：Dubbo 协议头为什么是 16 字节？`0xdabb` 魔数的含义是什么？Dubbo 协议的状态位（20 位）包含哪些信息？

---

#### 题目2：实现 RPC 的序列化层
实现两种序列化方式：JDK 序列化（`ObjectOutputStream`）和 JSON 序列化（Jackson/Gson）。对比两者的序列化大小和性能。

```java
// 提示：JDK 序列化需要实现 Serializable
// JSON 序列化更通用，但可能丢失类型信息
// 需要处理泛型、异常等复杂类型
```

**🔍 原理反思提问**：JDK 序列化的 `serialVersionUID` 的作用是什么？JSON 序列化如何处理泛型类型擦除？Protobuf 和 Hessian 相比 JDK 序列化有什么优势？

**💬 面试官可能追问**：`Kryo` 和 `FST` 等高性能序列化框架的原理是什么？`Protobuf` 的 Varint 编码和 ZigZag 编码是什么？

---

#### 题目3：实现 RPC 的服务注册与发现
使用 ZooKeeper 或简单的 HTTP 服务实现服务注册中心。服务提供者启动时注册，消费者从注册中心获取服务地址列表。

```java
// 提示：注册中心的核心功能
// 1. 服务注册：provider 启动时注册 IP:Port
// 2. 服务发现：consumer 获取 provider 列表
// 3. 健康检查：定期检测 provider 是否存活
// 4. 负载均衡：从 provider 列表中选择一个
```

**🔍 原理反思提问**：注册中心的 CAP 权衡是什么（ZooKeeper 是 CP，Eureka 是 AP）？服务发现的客户端模式和服务端模式有什么区别？`Curator` 的 `ServiceDiscovery` 是如何实现的？

**💬 面试官可能追问**：`Nacos` 的 AP 和 CP 模式如何切换？`Consul` 的健康检查机制是什么？`etcd` 的 Lease 和 Watch 机制如何用于服务发现？

---

#### 题目4：实现 RPC 的负载均衡
实现三种负载均衡策略：随机（Random）、轮询（RoundRobin）、加权随机（WeightedRandom）。编写测试验证各策略的分布均匀性。

```java
// 提示：加权随机使用权重区间
// 例如：A 权重 3，B 权重 2，C 权重 1
// 区间：[0,3) → A, [3,5) → B, [5,6) → C
```

**🔍 原理反思提问**：加权轮询的平滑算法（Nginx 的 Smooth Weighted Round Robin）是什么？一致性哈希（Consistent Hashing）如何用于负载均衡？Dubbo 的 `LeastActiveLoadBalance` 和 `ConsistentHashLoadBalance` 的原理是什么？

**💬 面试官可能追问**：`Ribbon` 的 `ZoneAvoidanceRule` 是什么？`Spring Cloud LoadBalancer` 和 `Ribbon` 的区别是什么？

---

#### 题目5（地狱级）：设计 RPC 框架的容错机制
设计并实现：超时重试、熔断降级、限流。使用滑动窗口实现简单的限流器，使用状态机实现熔断器（CLOSED → OPEN → HALF_OPEN）。

```java
// 提示：熔断器三种状态
// CLOSED：正常调用，失败计数
// OPEN：直接返回降级结果，不调用远程服务
// HALF_OPEN：尝试调用，成功则 CLOSED，失败则 OPEN
```

**🔍 原理反思提问**：熔断器的滑动窗口和固定窗口有什么区别？`Hystrix` 的线程池隔离和信号量隔离有什么区别？`Sentinel` 的滑动窗口和 LeapArray 是什么？

**💬 面试官可能追问**：`Resilience4j` 的 `CircuitBreaker`、`RateLimiter`、`Retry`、`Bulkhead` 是如何组合使用的？`Sentinel` 的流量整形（匀速排队、预热）是什么？

---

### 核心实现题

**微型 RPC 框架骨架**：整合以上所有组件，实现一个微型 RPC 框架的骨架。包含：协议编解码、JDK 动态代理客户端、服务端反射调用、简单的注册中心。编写单元测试覆盖：请求-响应、超时、异常处理。

```java
// 核心组件
public interface RpcProtocol { ... }       // 协议定义
public interface Serializer { ... }        // 序列化
public interface Registry { ... }          // 注册中心
public class RpcClient { ... }             // 客户端（动态代理）
public class RpcServer { ... }             // 服务端（反射调用）
```

**🔍 原理反思提问**：你的 RPC 框架中，客户端动态代理是如何将方法调用转换为网络请求的？服务端如何根据请求中的类名和方法名找到对应的实现并调用？`Future` 或 `CompletableFuture` 如何实现异步调用？

**💬 面试官可能追问**：Dubbo 的 `Invoker` 和 `Invocation` 模型是什么？`Filter` 链是如何实现的？`SPI` 机制在 Dubbo 中是如何工作的？

---

## 第30天：微型 RPC 框架实现 + 压测 + 调优报告 — 覆盖原理点：[综合全部]

### 编码探源题

#### 题目1：完善 RPC 框架的异步调用
在第29天骨架的基础上，实现异步调用：客户端发送请求后返回 `CompletableFuture`，不阻塞等待响应。服务端处理完成后通过请求 ID 匹配并完成 Future。

```java
// 提示：客户端维护一个 Map<requestId, CompletableFuture>
// 发送请求时创建 Future 并放入 Map
// 收到响应时根据 requestId 找到 Future 并 complete
```

**🔍 原理反思提问**：异步调用的 Future 超时如何处理？`CompletableFuture.completeExceptionally()` 如何传递异常？`CompletableFuture.allOf()` 如何实现并行调用？

**💬 面试官可能追问**：Dubbo 的 `AsyncToSyncInvoker` 是如何将异步转为同步的？`CompletableFuture` 的 `whenComplete`、`thenApply`、`thenCompose` 有什么区别？

---

#### 题目2：实现 RPC 框架的连接池
为 RPC 客户端实现一个简单的连接池：维护多个 TCP 连接，支持连接的借用和归还。使用 `GenericObjectPool` 或手写连接池。

```java
// 提示：连接池的核心
// 1. 连接创建（create）
// 2. 连接借用（borrow）
// 3. 连接归还（return）
// 4. 连接销毁（destroy）
// 5. 空闲连接检测
```

**🔍 原理反思提问**：连接池的 `maxTotal`、`maxIdle`、`minIdle` 参数如何设置？连接的空闲检测（`testWhileIdle`）和借用检测（`testOnBorrow`）有什么区别？`GenericObjectPool` 的 `EvictionPolicy` 是什么？

**💬 面试官可能追问**：Netty 的 `ChannelPool` 和 `FixedChannelPool` 是如何实现的？`ChannelPoolMap` 的作用是什么？

---

#### 题目3：JMH 压测 RPC 框架
使用 JMH 对 RPC 框架进行基准测试：测试不同并发数（1/10/50/100）下的 TPS、响应时间分布（P50/P99/P999）、错误率。

```java
// 提示：JMH 的 @State、@Benchmark、@Setup、@TearDown
// 使用 @Threads 控制并发数
// 使用 @Measurement 和 @Warmup 控制测试参数
```

**🔍 原理反思提问**：JMH 的 `@BenchmarkMode` 有哪些模式（Throughput/AverageTime/SampleTime/SingleShotTime）？`@Fork` 和 `@Warmup` 的作用是什么？JMH 如何避免 JIT 优化导致的测量偏差（Blackhole）？

**💬 面试官可能追问**：`JMH` 的 `CompilerControl` 注解的作用是什么？`@OperationsPerInvocation` 在什么场景下使用？JMH 的 Profiler（`StackProfiler`、`GCProfiler`）如何使用？

---

#### 题目4：RPC 框架性能调优
根据压测结果，对 RPC 框架进行调优：序列化方式切换（JDK → JSON → Kryo）、IO 模型切换（BIO → NIO → Netty）、线程模型优化。

```java
// 提示：调优方向
// 1. 序列化：Kryo 比 JDK 序列化快 10 倍以上
// 2. IO 模型：Netty（NIO）比 BIO 吞吐量高
// 3. 线程模型：业务线程池和 IO 线程分离
// 4. 连接复用：长连接 + 连接池
```

**🔍 原理反思提问**：Netty 的 `EventLoopGroup` 如何配置（bossGroup 和 workerGroup）？`ChannelPipeline` 中的 `ChannelHandler` 执行顺序是什么？`ByteToMessageDecoder` 和 `MessageToByteEncoder` 的区别是什么？

**💬 面试官可能追问**：Netty 的 `EpollEventLoopGroup`（Linux）和 `NioEventLoopGroup` 有什么区别？`Epoll` 的边缘触发和水平触发有什么区别？

---

#### 题目5（地狱级）：全链路压测 + 调优报告
对 RPC 框架进行全链路压测，模拟真实场景（不同大小的请求、不同并发数、网络延迟注入）。输出完整的调优报告，包含：压测环境、压测结果、性能瓶颈分析、调优措施、调优后结果对比。

```java
// 提示：调优报告结构
// 1. 压测环境：CPU、内存、JVM 参数、网络
// 2. 压测场景：请求大小分布、并发数、持续时间
// 3. 压测结果：TPS、RT 分布、错误率、资源使用率
// 4. 瓶颈分析：CPU 瓶颈、IO 瓶颈、锁竞争、GC
// 5. 调优措施：参数调整、代码优化、架构改进
// 6. 调优后对比：优化前后的指标对比
```

**🔍 原理反思提问**：如何定位 RPC 框架的性能瓶颈（CPU Profiler、线程 dump、GC 日志）？`Arthas` 的 `trace` 和 `monitor` 命令如何用于性能分析？`JProfiler` 和 `Async Profiler` 的区别是什么？

**💬 面试官可能追问**：`wrk` 和 `JMeter` 的区别是什么？`TCPCopy` 和 `Goreplay` 如何用于流量回放？全链路压测中如何隔离压测流量和真实流量？

---

### 核心实现题

**完整微型 RPC 框架**：整合第29-30天的所有组件，实现一个完整的微型 RPC 框架。包含：Netty 网络通信、多种序列化方式、注册中心、负载均衡、异步调用、连接池、熔断降级。编写完整的单元测试和集成测试。

```java
// 最终项目结构
my-rpc/
├── protocol/        // 协议定义与编解码
├── serialize/       // 序列化（JDK/JSON/Kryo）
├── transport/       // 网络传输（Netty）
├── registry/        // 注册中心
├── proxy/           // 动态代理
├── cluster/         // 集群（负载均衡、容错）
├── monitor/         // 监控
└── demo/            // 示例
```

**🔍 原理反思提问**：你的 RPC 框架和 Dubbo 的架构有什么异同？`Invoker` 模型、`Filter` 链、`SPI` 扩展机制在你的框架中如何体现？如果让你将框架开源，还需要补充什么（文档、示例、性能测试报告）？

**💬 面试官可能追问**：如果面试官让你"设计一个 RPC 框架"，你如何用这30天的经验来回答？你会从哪些维度展开（协议、序列化、传输、注册中心、集群、监控）？

---

## 30 天后你能从容回答的 50 个面试真题列表

### 集合框架（10 题）
1. HashMap 的 put 过程是怎样的？1.7 和 1.8 有什么区别？
2. HashMap 为什么线程不安全？1.7 的死循环是如何形成的？
3. ConcurrentHashMap 1.7 和 1.8 的实现有什么区别？为什么 1.8 放弃了分段锁？
4. ConcurrentHashMap 的扩容是如何实现的？多线程如何协同扩容？
5. ArrayList 的扩容机制是什么？fail-fast 是如何实现的？
6. LinkedHashMap 如何实现 LRU？accessOrder 的作用是什么？
7. TreeMap 的红黑树和 HashMap 的红黑树实现有什么不同？
8. HashSet 底层是什么？如何保证元素不重复？
9. BlockingQueue 有哪些实现？ArrayBlockingQueue 和 LinkedBlockingQueue 的区别是什么？
10. CopyOnWriteArrayList 的适用场景是什么？为什么写多读少不适合？

### 字符串与基础类型（5 题）
11. String 为什么是不可变的？String.intern() 在 1.7 前后有什么变化？
12. String、StringBuilder、StringBuffer 的区别是什么？循环中拼接字符串应该用哪个？
13. Integer 的缓存范围是多少？为什么 Float 没有缓存？
14. 自动装箱和拆箱在字节码层面是如何实现的？
15. 泛型的类型擦除是什么？桥方法的作用是什么？

### 异常体系（3 题）
16. try-catch-finally 中，finally 里的 return 会覆盖 try 里的 return 吗？
17. try-with-resources 的原理是什么？资源关闭顺序是怎样的？
18. checked exception 和 unchecked exception 的区别是什么？Spring 为什么偏爱 unchecked exception？

### 反射与动态代理（4 题）
19. JDK 动态代理和 CGLIB 代理的区别是什么？Spring AOP 如何选择？
20. 反射的性能开销来自哪里？MethodHandle 和反射有什么区别？
21. setAccessible(true) 的原理是什么？Java 9 模块化对反射有什么限制？
22. @Transactional 自调用失效的原因是什么？如何解决？

### 类加载与 JVM（6 题）
23. 类加载的过程是怎样的？双亲委派模型是什么？
24. 如何打破双亲委派模型？Tomcat 为什么要打破？
25. JVM 内存结构是怎样的？各区域存储什么内容？
26. 对象的内存布局是怎样的？Mark Word 存储了什么？
27. 有哪些 GC 算法？CMS 和 G1 的区别是什么？
28. 如何排查 OOM？常用的 JVM 调优参数有哪些？

### 并发基础与 JMM（12 题）
29. volatile 的作用是什么？它能保证原子性吗？
30. synchronized 的锁升级过程是怎样的？偏向锁在 Java 15 为什么被禁用？
31. AQS 的原理是什么？state 和 CLH 队列是如何配合的？
32. ReentrantLock 公平锁和非公平锁的区别是什么？
33. ThreadPoolExecutor 的任务提交流程是怎样的？四种拒绝策略是什么？
34. ThreadLocal 的内存泄漏是如何产生的？如何避免？
35. CAS 的 ABA 问题是什么？如何解决？
36. AtomicLong 和 LongAdder 的区别是什么？LongAdder 为什么在高并发下性能更好？
37. CountDownLatch 和 CyclicBarrier 的区别是什么？
38. Semaphore 是如何实现限流的？
39. synchronized 和 ReentrantLock 的区别是什么？什么场景下必须用 ReentrantLock？
40. happens-before 规则有哪些？volatile 的 happens-before 规则是什么？

### 综合设计（10 题）
41. 如何设计一个分布式锁？需要考虑哪些问题？
42. 如何设计一个 RPC 框架？核心组件有哪些？
43. 如何设计一个线程池？核心参数如何设置？
44. 如何设计一个 LRU 缓存？如何支持高并发？
45. 如何设计一个限流器？滑动窗口和令牌桶的区别是什么？
46. 如何设计一个熔断器？三种状态如何转换？
47. 如何设计一个注册中心？CAP 如何权衡？
48. 如何设计一个序列化框架？需要考虑哪些问题？
49. 如何排查生产环境 CPU 100% 的问题？
50. 如何排查生产环境频繁 Full GC 的问题？

---

> **本指南覆盖 Java 基础面试 28 个核心原理点，通过 30 天编码驱动学习，你将产出 7 个核心手写组件与 1 个微型 RPC 系统。每天 5 道编码探源题 + 1 道核心实现题，共计 180 道编码实验，确保你在面试中能够从容应对从基础原理到系统设计的全部追问。**