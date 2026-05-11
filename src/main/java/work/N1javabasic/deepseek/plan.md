> 请将以下完整内容保存为 `Java基础_30天编码指南.md`。

# Java 基础 · 30天编码驱动面试探源指南

**面试官导师寄语**：这不是一本八股文集，而是一张把 HashMap 扩容、synchronized 锁升级、GC 全过程写进代码里的作战地图。30天后，你不仅能回答“为什么”，还能掏出可运行的源码证明：“我写过，我压测过，我对比过不同版本的行为。”

***

## 面试必考原理清单（共 28 项，按模块分组）

### 一、集合框架（8 项）

1. **HashMap 1.8 数据结构与哈希扰动**：数组+链表+红黑树；`hash()` 扰动函数高16位异或。
2. **HashMap 扩容机制**：`resize()` 触发条件（size > threshold）、负载因子0.75、链表分裂与迁移。
3. **HashMap 树化与退树化**：链表长度≥8且数组长度≥64时树化；节点数≤6时退化为链表。
4. **ConcurrentHashMap 1.7 vs 1.8**：分段锁 vs CAS+synchronized；`sizeCtl` 含义；多线程协助扩容（`helpTransfer`）。
5. **ArrayList 扩容与 fail-fast**：`grow()`（约1.5倍扩容）；`modCount` 与 `ConcurrentModificationException`。
6. **LinkedList 与 ArrayDeque**：链表 vs 循环数组；头尾插入性能；队列/双端队列接口。
7. **TreeMap 红黑树排序**：红黑树性质；自然排序与比较器；`compareTo` 一致性。
8. **LinkedHashMap 与 LRU 实现**：双向链表维护顺序；`accessOrder=true` 实现LRU缓存淘汰。

### 二、并发基础与工具（8 项）

1. **synchronized 锁升级过程**：偏向锁→轻量级锁→重量级锁；对象头 Mark Word 变化；自适应自旋。
2. **volatile 可见性与禁止重排**：内存屏障 Happens-Before；MESI 嗅探；DCL 中 volatile 作用。
3. **AQS 框架原理**：CLH 队列变体；`state` 与 `acquire/release`；ReentrantLock 公平/非公平实现。
4. **ReentrantLock 与 Condition**：可重入实现；等待/通知机制；与 synchronized wait/notify 对比。
5. **线程池核心参数与流程**：corePoolSize、maximumPoolSize、keepAliveTime；`ctl` 线程状态位；四种拒绝策略。
6. **ThreadLocal 与内存泄漏**：`ThreadLocalMap` 的 WeakReference Key；`InheritableThreadLocal` 作用。
7. **CAS 与 ABA 问题**：`Unsafe.compareAndSwap` 底层；`AtomicStampedReference` 解决方案。
8. **CountDownLatch/CyclicBarrier/Semaphore**：底层均依赖 AQS；`state` 与共享/独占模式。

### 三、JVM 内存与 GC（6 项）

1. **JVM 内存结构**：堆、栈、方法区/元空间、程序计数器；HotSpot 对象分配过程（指针碰撞/空闲列表）。
2. **对象存活判定与引用类型**：可达性分析；强/软/弱/虚引用及 `ReferenceQueue` 使用。
3. **分代回收与算法**：标记-清除、标记-整理、复制；新生代 Eden/Survivor 比例。
4. **CMS 垃圾收集器**：初始标记、并发标记、重新标记、并发清除；优缺点及浮动垃圾。
5. **G1 垃圾收集器**：Region 划分、Mixed GC、SATB、Remembered Set；`-XX:MaxGCPauseMillis`。
6. **GC 日志解读与调参**：`-Xlog:gc*`（Java9+）或 `-XX:+PrintGCDetails`；`jstat` 实时查看。

### 四、IO/NIO 模型（3 项）

1. **BIO/NIO/AIO 模型与使用**：面向流 vs 面向缓冲；Selector 多路复用；`epoll` 的 LT/ET。
2. **NIO Buffer 与 Channel**：`ByteBuffer` 的 position/limit/capacity/flip；FileChannel 与内存映射。
3. **零拷贝实现**：`FileChannel.transferTo`（`sendfile`）与 `MappedByteBuffer`（`mmap`）的区别与局限性。

### 五、反射、代理与类加载（4 项）

1. **反射机制与开销**：`Class` 获取方式；`Method.invoke` 的 JNI 调用与 `setAccessible(true)`；新版 `MethodHandle`。
2. **JDK 动态代理与 CGLIB**：`Proxy.newProxyInstance` 与 `InvocationHandler`；CGLIB 基于 ASM 生成子类；final 方法限制。
3. **类加载与双亲委派**：`ClassLoader.loadClass()` 流程；破坏双亲委派的 SPI（`Thread Context ClassLoader`）；Tomcat 类加载隔离。

### 六、基础语法糖与异常（2 项，按需增添）

1. **自动装箱与拆箱缓存陷阱**：`Integer.valueOf()` 缓存 -128\~127；`==` 与 `equals` 引发的问题。
2. **异常体系与 finally 执行顺序**：checked/unchecked；`try-catch-finally` 中包含 return 时的执行与返回值。

> 以上 28 项严格映射到后续 30 天的每日覆盖中。每一天开篇都会标注当天的覆盖原理点。

***

## 30天原理覆盖映射表

| 天  | 主题                            | 覆盖原理编号         |
| -- | ----------------------------- | -------------- |
| 1  | 手写动态数组与 fail-fast             | 5, 29          |
| 2  | 手写 HashMap 基本版（数组+链表）         | 1, 2           |
| 3  | HashMap 树化与退化完整实现             | 3, 1           |
| 4  | LinkedHashMap 与 LRU 缓存        | 8              |
| 5  | TreeMap 与红黑树平衡探究              | 7              |
| 6  | ConcurrentHashMap 源码探针（1.8）   | 4              |
| 7  | ArrayList 源码攻防与扩容微基准          | 5, 29          |
| 8  | synchronized 锁升级实验（JOL 观察对象头） | 9              |
| 9  | volatile 与 DCL 正确/错误版本对比      | 10             |
| 10 | AQS 自实现与 ReentrantLock 探针     | 11, 12         |
| 11 | 线程池源码级实验 + 自定义拒绝策略            | 13             |
| 12 | ThreadLocal 内存泄漏复现与分析         | 14             |
| 13 | CAS 实战：无锁栈 + ABA 复现与解决        | 15             |
| 14 | 三大并发工具 CountDownLatch 等底层追踪   | 16             |
| 15 | JVM 内存结构实验：堆/栈/元空间溢出          | 17, 22         |
| 16 | 引用类型实验：软引用缓存、WeakHashMap      | 18             |
| 17 | GC 算法对比实验：不同收集器下日志分析          | 19, 20, 21, 22 |
| 18 | BIO/NIO 模型对比与小型聊天室            | 23             |
| 19 | NIO Buffer 与零拷贝性能对比           | 24, 25         |
| 20 | 反射方法调用与 MethodHandle 基准       | 26             |
| 21 | JDK 动态代理 vs CGLIB 源码生成剖析      | 27             |
| 22 | 自定义 ClassLoader 打破双亲委派        | 28             |
| 23 | 堆外内存泄漏与直接内存 OOM 复现            | 17, 22         |
| 24 | 死锁检测与 JStack 分析实战             | 11, 22         |
| 25 | CPU 100% 定位（死循环 + 线程转储）       | 9, 10, 22      |
| 26 | HashMap 并发死循环复现（Java7）与修复对比   | 2, 4           |
| 27 | 伪共享问题与 `@Contended` 实验        | 10, 15         |
| 28 | 故障注入：模拟长 GC 与线程阻塞             | 21, 13         |
| 29 | 微型 RPC 框架骨架（Day1）             | 全综合            |
| 30 | 微型 RPC 框架骨架（Day2）与压力面试        | 全综合            |

***

## 你将产出的面试项目清单

完成本计划后，你的 GitHub 上将多出以下可直接放在简历上的项目/组件：

1. **简化版 HashMap**：支持扩容、树化、LRU 淘汰。
2. **手写 AQS 与 ReentrantLock**：支持公平锁、Condition。
3. **手写线程池**：支持核心/最大线程、任务队列、拒绝策略。
4. **NIO 多路复用聊天室**：Selector + 非阻塞 IO。
5. **自定义类加载器与隔离容器**：打破双亲委派，加载同名类。
6. **微型 RPC 框架**：含动态代理、序列化、注册中心（Mock）、负载均衡、服务熔断。

***

# 第 1 天：手写动态数组与 fail-fast

本日掌握：从零实现自动扩容的数组容器，并亲手触发 ConcurrentModificationException，理解 fail‑fast 机制\
覆盖原理点：5 (ArrayList 扩容与 fail‑fast), 29 (自动装箱与拆箱缓存陷阱)\
阶段：使用期

## 🎯 今日目标

- 能写出一个 **不依赖 JDK 集合框架** 的动态数组，支持自动扩容、按索引存取。
- 能解释 **fail‑fast** 的本质，并在自己的迭代器中让它抛出 `ConcurrentModificationException`。
- 能识别 **自动装箱缓存** 带来的 `==` 陷阱，并在业务代码中避免。

***

## 📝 练习1：基础用法——自己动手写 IntArrayList（必做）

### 业务场景

假设我们正在开发一个 IoT 网关，需要把设备上报的温度值（int）顺序存入内存，等凑满一批再批量发送。\
内存资源有限，不能一开始就开辟 10 万大小的数组，要按需扩容。

### 你的任务

实现一个 **IntArrayList** 类，满足：

- 不含泛型，只处理 `int`（为了排除 JDK 泛型干扰，先聚焦结构）
- 构造时指定初始容量（若不给默认值则使用 10）
- `add(int value)`：将元素加入末尾
- `get(int index)`：返回指定下标元素，下标不合法时抛出 `IndexOutOfBoundsException`
- `size()`：返回当前元素个数
- 自动扩容：当元素数 >= 数组长度时，**扩容至原来长度的 1.5 倍**（向下取整，但不能比当前元素数 + 1 小，用 `Math.max` 兜底）

### ⚡ 关键提示

- 你可以用 `int[] data` 存储元素，一个 `int size` 跟踪实际元素数。
- 扩容时，计算新长度后调用 `Arrays.copyOf` 复制老数组。
- 边界检查：`index < 0 || index >= size` 才抛异常，`index = size` 不能 `get`。
- 先在纸上画一下数组长度和 size 的关系，再动手。

### ✍️ 动手写代码

```java
// 你的 IntArrayList 实现写在这里
```

### ✅ 自我检查

- [ ] 添加 15 个元素后，`size()` 返回值是多少？
- [ ] 向默认构造的容器添加 11 个元素时，是否触发了扩容？内部数组长度变成了 15 吗？
- [ ] `get(0)` 和 `get(size()-1)` 都能正常返回吗？
- [ ] 传入负索引或 >=size 的索引时，异常信息是否清晰？

### 📖 参考实现（建议完成后再查看）

<details>
<summary>点击展开参考代码</summary>

```java
import java.util.Arrays;

public class IntArrayList {
    private static final int DEFAULT_CAPACITY = 10;
    private int[] data;
    private int size;

    public IntArrayList() {
        this(DEFAULT_CAPACITY);
    }

    public IntArrayList(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("initialCapacity cannot be negative");
        }
        this.data = new int[initialCapacity];
        this.size = 0;
    }

    public void add(int value) {
        ensureCapacity(size + 1);
        data[size++] = value;
    }

    public int get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(
                "Index: " + index + ", Size: " + size);
        }
        return data[index];
    }

    public int size() {
        return size;
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity <= data.length) {
            return;
        }
        int newCapacity = data.length + (data.length >> 1); // 1.5倍
        if (newCapacity < minCapacity) {
            newCapacity = minCapacity; // 兜底
        }
        data = Arrays.copyOf(data, newCapacity);
    }
}
```

**设计思路**

- `ensureCapacity` 只在需要时扩容，`minCapacity` 是当前 size+1，避免过早扩容。
- `newCapacity = data.length + (data.length >> 1)` 等价于 `data.length * 1.5`，速度快且避免浮点。
- 兜底逻辑用于应对初始容量为 0 或 1 的情况（0→0+0→需要兜底为 1）。
- 不对外暴露多余方法，先保持最简单，为后续 fail‑fast 留出空间。

</details>

### 🐞 常见错误预警

- **错误**：扩容后忘记更新 `data` 引用（没有 `data = Arrays.copyOf(data, newCapacity)`），后续添加仍然写入老数组。\
  → **发现方法**：添加超过初始容量的元素后，打印 `Arrays.toString(data)`，你会看到多出来的元素丢失了。
- **错误**：`size` 作为索引写入元素后未 `++`，导致所有元素覆盖在同一个位置。\
  → **发现方法**：遍历打印，发现只有最后一个元素出现多次。
- **错误**：扩容条件写成 `size >= data.length`，导致在 `size==data.length` 时仍不扩容，下一次 `add` 数组越界。\
  → **发现方法**：插入正好 10 个元素后再加一个，系统抛 `ArrayIndexOutOfBoundsException`。

***

## 📝 练习2：中级用法——添加迭代器与 fail‑fast

### 业务场景

现在你需要遍历刚才的 `IntArrayList` 来批量发送数据。但业务要求：**遍历过程中，如果有新的设备数据插入（调用 add），必须立即感知并终止遍历，抛出异常**，避免漏发或重复发送。

### 你的任务

- 在 `IntArrayList` 中添加一个内部类 `Itr` 实现迭代器（不用实现 `Iterator` 接口，方法命名为 `hasNext` 和 `next`）。
- 在 `IntArrayList` 中增加 `iterator()` 返回 `new Itr()`。
- 引入一个 **版本号** **`modCount`**，每次执行结构性修改（目前只有 `add`）时自增。
- 迭代器初始化时记录 `expectedModCount = modCount`；在 `next()` 开始时检查两者是否一致，不一致则抛出 **`ConcurrentModificationException`**。
- 编写测试：先遍历打印，再在遍历中途插入一个元素，看是否抛异常。

### ⚡ 关键提示

- `modCount` 是 **父容器** 的字段，迭代器内部持有父容器的引用或通过闭包访问。
- 检查失败抛异常时，可模仿 JDK 信息：`throw new ConcurrentModificationException();`
- 注意：迭代器的 `hasNext()` 内部也要检查吗？JDK 在 `checkForComodification` 只在 `next()` 和 `remove()` 里做，你也可以只在 `next()` 里检查，以先感受区别。
- 若你之后还想实现 `remove`（迭代器安全删除），需要更新 `expectedModCount`，但今天不强制。

### ✍️ 动手写代码

```java
// 在你的 IntArrayList 中加入 modCount、Itr，并写测试
public static void main(String[] args) {
    IntArrayList list = new IntArrayList();
    // ... 添加元素，创建迭代器，遍历一半再 add，观察是否抛异常
}
```

### ✅ 自我检查

- [ ] 正常遍历（不插入）能完整打印所有元素吗？
- [ ] 遍历途中调用 `add()` 后，下一次 `next()` 是否抛出 `ConcurrentModificationException`？
- [ ] 如果多次 `add()` 后才调用 `next()`，异常能否正常触发？
- [ ] `modCount` 在每次 `add` 后都自增了吗？打印 `modCount` 验证。

### 📖 参考实现（建议完成后再查看）

<details>
<summary>点击展开参考代码</summary>

```java
import java.util.ConcurrentModificationException;

public class IntArrayList {
    // ... 前面部分不变

    private int modCount; // 新增版本号

    public void add(int value) {
        ensureCapacity(size + 1);
        data[size++] = value;
        modCount++;        // 结构性修改
    }

    // 其他方法如果会造成结构性修改，也需要 modCount++

    public Itr iterator() {
        return new Itr();
    }

    // 迭代器内部类
    private class Itr {
        private int cursor;          // 下一个要返回的元素下标
        private int expectedModCount = modCount;

        public boolean hasNext() {
            return cursor < size;
        }

        public int next() {
            checkForComodification();
            if (cursor >= size) {
                throw new java.util.NoSuchElementException();
            }
            return data[cursor++];
        }

        private void checkForComodification() {
            if (modCount != expectedModCount) {
                throw new ConcurrentModificationException();
            }
        }
    }

    // 测试
    public static void main(String[] args) {
        IntArrayList list = new IntArrayList();
        for (int i = 0; i < 5; i++) {
            list.add(i);
        }

        Itr it = list.iterator();
        while (it.hasNext()) {
            System.out.println(it.next());
            if (list.size() == 3) {     // 读到第三个元素后插入
                list.add(100);
            }
        }
        // 预期：打印 0,1,2 后，下一次 next() 抛 ConcurrentModificationException
    }
}
```

**设计思路**

- `modCount` 是典型的 **乐观锁** 思想，迭代器期望遍历期间结构不变；任何改变立即导致下一操作失败，避免脏读。
- 检查放在 `next()` 开头而不是 `hasNext()`，是因为 `hasNext()` 一般不会暴露不一致数据，且 JDK 也是这样做的；这样你可以在循环体里调用多次 `hasNext()` 而不炸。
- `cursor` 直接在数组上移动，效率最高；因为我们不提供 `remove`，所以不用维护 lastRet。

</details>

***

## 📝 练习3：高级/探索用法——泛型化 + 自动装箱缓存陷阱

### 业务场景

数据不再只是温度，也可能包含设备 ID（`Integer`）。你需要一个 **通用容器** `MyArrayList<T>`。\
另外，我们在写业务逻辑时经常要判断“是否等于某个阈值”，整数用 `==` 有时会 bug。今天我们就来故意踩一次这个坑。

### 你的任务

1. 将 `IntArrayList` 改写为泛型 `MyArrayList<T>`，底层用 `Object[]`，添加 `add(T elem)` 和 `get(index)` 转型。
2. 迭代器也改为泛型版本。
3. 编写一个 **故意出错的业务场景**：遍历容器，删除所有值为 100 的元素（注意：Java 的 `Integer` 缓存 -128 到 127 会让 100 的 `==` 为 `true`），这会让你误删不该删的元素。请把元素换成 200，再用 `==` 比较，观察删除逻辑失效。最后用 `equals` 修正。

### ⚡ 关键提示

- `Object[]` 转型时需要 `(T) data[index]`，会有 unchecked cast 警告，用 `@SuppressWarnings("unchecked")` 忽略。
- 泛型数组不能用 `new T[]`，只能用 `(T[]) new Object[]`。
- **自动装箱缓存**：`Integer.valueOf(200)` 每次返回新对象，而 200 不在缓存池内，所以 `==` 判断两个 `Integer(200)` 是 `false`。而 100 在缓存范围内，`==` 是 `true`。这会导致你用 `==` 删除 100 时“看起来很成功”，但换成 200 就永远删不掉。
- 编写单元测试验证：容器内同时存在 200（两个不同的实例），期望全部删除，但使用 `==` 会失败；改用 `equals` 成功。

### ✍️ 动手写代码

```java
// MyArrayList<T> 泛型实现
// 测试：依次添加 100, 200, 100, 200，然后用错误的 == 和正确的 equals 分别删除值为 200 的元素，打印剩余列表。
```

### ✅ 自我检查

- [ ] 泛型容器能正确存取各种类型（String, Integer）吗？
- [ ] 用 `==` 删除 200 时，是否删不掉？用 `==` 删除 100 时，是否误删了很多？（100 在缓存中，所有 100 的包装对象是同一个）
- [ ] 改用 `equals` 后，删除 200 是否完全正确？

### 📖 参考实现（建议完成后再查看）

<details>
<summary>点击展开参考代码</summary>

```java
import java.util.Arrays;
import java.util.ConcurrentModificationException;

public class MyArrayList<T> {
    private static final int DEFAULT_CAPACITY = 10;
    private Object[] data;
    private int size;
    private int modCount;

    public MyArrayList() {
        data = new Object[DEFAULT_CAPACITY];
    }

    public void add(T value) {
        ensureCapacity(size + 1);
        data[size++] = value;
        modCount++;
    }

    @SuppressWarnings("unchecked")
    public T get(int index) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException();
        return (T) data[index];
    }

    public int size() { return size; }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity <= data.length) return;
        int newCap = data.length + (data.length >> 1);
        if (newCap < minCapacity) newCap = minCapacity;
        data = Arrays.copyOf(data, newCap);
    }

    // 迭代器略，可自行扩展

    // 测试自动装箱陷阱
    public static void main(String[] args) {
        MyArrayList<Integer> list = new MyArrayList<>();
        list.add(100);
        list.add(200);
        list.add(100);
        list.add(200);

        // 错误方式：用 == 删除 200
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i) == Integer.valueOf(200)) { // 每次装箱产生不同对象
                // 此处可能永远不成立，因为 200 不在缓存池
                System.out.println("找到并删除(==): " + i);
            }
        }
        System.out.println("用==后剩余: " + list.size()); // 仍然4

        // 正确方式：用 equals
        list = new MyArrayList<>();
        list.add(100); list.add(200); list.add(100); list.add(200);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).equals(200)) {
                System.out.println("找到并删除(equals): " + i);
                // 这里应该实现真正的 remove，此处仅示意
            }
        }
    }
}
```

**设计思路**

- 泛型底层存储使用 `Object[]`，所有元素自动装箱后存入。
- `==` 比较引用，只有缓存范围内的 Integer（-128\~127）才会复用对象，超出范围每次 `Integer.valueOf` 或自动装箱都会 `new Integer`，导致物理地址不同。
- 业务代码中 **一律用** **`equals`** **比较对象内容**，这是 Java 程序员的基本修养。
- 边界情况：`null` 元素需要用 `Objects.equals` 或先判空，否则 `null.equals` 会 NPE。

</details>

***

## 🏢 大厂场景实战（使用期·简化版）

### 场景描述

某广告投放系统，有一个 **用户点击流队列**，每秒有上千条点击事件入队（调用 `add`）。在线服务需要**每 5 秒拉取一次快照**，遍历当前所有事件进行聚合计算（例如统计点击最多的广告位）。遍历期间，新的点击可能继续到达。\
直接遍历会引发 `ConcurrentModificationException`，不能要求上游停止写入。请设计你的容器策略。

### 约束条件

- 写入 QPS：1000
- 读频率：每 5 秒
- 不允许丢掉任何事件
- 读取者不能阻塞写入者太久

### 你的设计任务

请在下面写出你的方案（可用文字 + 关键伪代码或结构描述）。

```java
// 你的思路
```

### 设计决策点（引导思考）

- 如果每次读取都 `new` 一个新的容器副本，时间开销和内存开销可以接受吗？
- 有没有现成的并发容器可以借鉴？它们的原理是快照还是锁？
- 你能否在今天的 `MyArrayList` 基础上，给读取操作“偷”一个只读副本？

### 常见方案参考（完成后再看）

<details>
<summary>点击查看业界常用模式及其 trade-off</summary>

**方案 A：写时复制 (CopyOnWriteArrayList 思路)**

- 每次 `add` 都复制一份新数组，在新数组上修改，然后原子替换引用。
- 读操作不加锁，拿到当前数组引用直接遍历。
- 优点：读写完全并发，读无锁。缺点：写开销大，适合读远多于写。
- 反思：你这儿写 QPS 1000，每秒复制 1000 次数组？内存和 GC 压力巨大，不推荐。

**方案 B：分段快照 (epoch-based)**

- 维护一个“当前活跃段”列表，每 5 秒切换到一个新段，旧的段交给读取线程处理。
- 写入操作追加到最新段尾部，不加锁（可使用 `synchronized` 或分段锁保证段内计数安全）。
- 优点：写不需要复制，读也不阻塞写。缺点：需要协调段切换的时机，丢失窗口处理。
- 实际应用：Disruptor 的 Sequence 机制。

**方案 C：优雅降级——快照并允许少量复制**

- 读取时，`synchronized` 保护整个容器，快速复制 `Object[]` 副本后立即释放锁，遍历在副本上进行。
- 拷贝本身很快（只拷贝引用），遍历时长长但不持锁。
- 适合中等写入速度，只要拷贝时间远小于 5 秒间隔即可。
- 这是我们自己最容易实现的方案。

</details>

***

## 🃏 今日面试自测卡（先自答，再展开）

1. <br />

***

> 把今天的代码提交到你的 GitHub 仓库，明天我们将在它的基础上长出 **HashMap** 的链表。

继续加油，你正在从“会用集合”向“能造集合”的工程师进化。

# 第 2 天：手写 HashMap 基本版（数组+链表）

本日掌握：从零实现一个基于数组+链表、支持扩容的简易 HashMap，理解哈希扰动、负载因子与迁移细节\
覆盖原理点：1 (HashMap 数据结构与哈希扰动), 2 (HashMap 扩容机制)\
阶段：使用期

## 🎯 今日目标

- 能写出 `put`、`get` 方法，使用 **链表法** 解决哈希冲突。
- 能实现 **扩容** 并正确迁移所有节点，负载因子设为 0.75。
- 能写出混淆高低位的 **扰动函数** `hash()`，并解释它为什么能减少碰撞。
- 了解桶下标计算公式和哈希值为负数时的处理。

***

## 📝 练习1：基础用法——实现数组+链表的 put/get（必做）

### 业务场景

我们正在实现一个简单的配置中心，存储 `key=value` 的配置项。配置项不多，但希望获得 O(1) 的平均读取速度。先不用考虑并发。

### 你的任务

实现 `SimpleHashMap<K, V>`，满足：

- 构造时可传入初始桶容量（默认 16）
- `put(K key, V value)`：若 key 已存在，覆盖旧值并返回旧值；否则插入新节点并返回 `null`
- `get(K key)`：返回对应 value，key 不存在返回 `null`
- 使用 **单链表** 挂接冲突节点（Node 包含 hash、key、value、next）
- 定义一个 `hash(Object key)` 方法，直接返回 `key.hashCode()`（扰动今天先不加，练习2再改）
- 桶索引计算：`hash & (table.length - 1)`，要求处理 hashCode 为负数的情况
- 暂不实现扩容，先让基本功能跑通

### ⚡ 关键提示

- 数组长度一定是 2 的幂（16、32...），这样 `(length - 1)` 的二进制全是 1，等同于高效取模。
- 如果想在构造时允许传入非 2 的幂的容量，可以借助 `tableSizeFor` 方法算出大于等于该值的最小 2 次幂（可暂不实现，但心里要知道 JDK 是这样做的）。
- `hashCode()` 可能返回负数，而数组下标必须是非负整数。`hash & (table.length - 1)` 中 `hash` 是 int 类型，`&` 操作会将其视为有符号整数的二进制补码，依然可以得出有效下标（因为 length-1 也是正数且高位为 0）。但后续扰动后我们还是会保证高位参与。
- 画一个 bin 数组，每个 bin 指向链表头，思路就清晰了。

### ✍️ 动手写代码

```java
// 请在这里写出 SimpleHashMap 的 put / get 和内部类 Node
```

### ✅ 自我检查

- [ ] 存入 `"name" -> "Alice"`，能取回吗？
- [ ] 重复 put 相同的 key，`put` 返回旧值，且新值覆盖生效了吗？
- [ ] 构造两个不同的 key，但故意让它们的 `hashCode()` 对 16 取模相同（可自定义类重写 hashCode 返回固定值），观察它们是否挂在了同一个桶的链表上。
- [ ] 传入 `null` key 会怎样？先不处理，但思考 JDK HashMap 怎么做的（放到 0 号桶）。

### 📖 参考实现（建议完成后再查看）

<details>
<summary>点击展开参考代码</summary>

```java
public class SimpleHashMap<K, V> {
    static class Node<K, V> {
        final int hash;
        final K key;
        V value;
        Node<K, V> next;

        Node(int hash, K key, V value, Node<K, V> next) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;
        }
    }

    private Node<K, V>[] table;
    private int size;
    private static final int DEFAULT_CAPACITY = 16;

    @SuppressWarnings("unchecked")
    public SimpleHashMap(int capacity) {
        // 可在此处调用 tableSizeFor 保证是2的幂，此处简化为直接使用 capacity（要求调用者传入2的幂）
        this.table = (Node<K, V>[]) new Node[capacity];
    }

    public SimpleHashMap() {
        this(DEFAULT_CAPACITY);
    }

    // 直接返回对象的hashCode，暂不做扰动
    private int hash(Object key) {
        return key.hashCode();
    }

    public V put(K key, V value) {
        int h = hash(key);
        int index = h & (table.length - 1);
        Node<K, V> head = table[index];

        // 先检查链表中是否存在相同key
        for (Node<K, V> e = head; e != null; e = e.next) {
            if (e.hash == h && (e.key == key || (key != null && key.equals(e.key)))) {
                V old = e.value;
                e.value = value;
                return old;
            }
        }

        // 头插法添加（也可尾插，但头插最简单）
        table[index] = new Node<>(h, key, value, head);
        size++;
        return null;
    }

    public V get(K key) {
        int h = hash(key);
        int index = h & (table.length - 1);
        Node<K, V> e = table[index];
        while (e != null) {
            if (e.hash == h && (e.key == key || (key != null && key.equals(e.key)))) {
                return e.value;
            }
            e = e.next;
        }
        return null;
    }

    public int size() {
        return size;
    }
}
```

**设计思路**

- `Node` 存储 `hash` 字段是为了扩容迁移时不用重新计算哈希，也是比较时的快速筛选（先比 hash，再比 == 或 equals）。
- 头插法实现简单，但会打乱插入顺序（后面扩容时若使用头插法迁移，在并发环境下会导致死循环，Java8 已改为尾插，我们今天先实现简单版，扩容练习里会换为尾插）。
- 索引计算 `h & (table.length - 1)` 天然处理了负数（补码高位被 length-1 的 0 屏蔽）。
- 比较 key 时先检查 `e.hash == h` 加快排除不同哈希的对象。

</details>

### 🐞 常见错误预警

- **hashCode 为负数**：若直接用 `hash % table.length` 会得到负数下标，导致 `ArrayIndexOutOfBoundsException`。必须用 `& (length-1)` 或先取绝对值 `Math.abs`。我们的实现用 `&` 安全。
- **扩容后忘记重新计算索引**：如果直接复制链表头而不重新散列，之前桶下标相同的节点在新数组里可能还保持相同下标，但实际它们的索引需要根据新长度重新分配（高一位不同），会导致 `get` 找不到节点。今天还没写扩容，但预习一下。
- **key 为 null**：调用 `key.hashCode()` 会 NPE。JDK 将 null 键放在 0 号桶，且 hash 值为 0。我们先不处理，但知道这一点。

***

## 📝 练习2：中级用法——添加扩容与迁移

### 业务场景

配置中心上线后，用户狂加配置，导致链表越来越长，`get` 性能退化到 O(n)。我们需要在装填因子超过 0.75 时自动扩容，降低哈希冲突。

### 你的任务

- 定义一个 `loadFactor` 常量 0.75。
- 添加成员 `threshold = (int)(capacity * loadFactor)`，当 `size > threshold` 时触发 `resize()`。
- 编写 `resize()` 方法：
  - 新容量为旧容量的 2 倍。
  - 创建新数组，遍历旧数组的每个桶，将链表节点 **重新散列** 到新桶。
  - 重散列时利用一个性质：同一条链表上的节点，它们在新数组中的索引要么保持不变，要么变成 `原索引 + 旧容量`（因为扩容后长度翻倍，高一位多了个 1）。但今天我们先用最简单的方式：每个节点用 `newIndex = node.hash & (newTable.length - 1)` 计算，并用 **尾插法** 保持链表顺序（防止日后并发死循环的隐患）。
- 修改 `put`，让它在插入前判断是否需要扩容 `if (size > threshold) resize();`。

### ⚡ 关键提示

- 尾插法迁移时，需要为每个桶维护 `head` 和 `tail` 指针。或者你可以遍历原链表，逐个节点计算新索引并插入到新桶的末尾。
- 如果你已经用了头插法，直接改为尾插法需要注意：如果简单地遍历并不断 `newTable[index] = new Node(..., head)` 其实又是头插。尾插要记录链表尾部。
- 每个节点的 `hash` 已经存好，迁移时直接拿过来用，不需要重新 `key.hashCode()`。
- 记得更新 `threshold` 为 `(int)(newCapacity * loadFactor)`。

### ✍️ 动手写代码

```java
// 在 SimpleHashMap 中增加 loadFactor、threshold、resize()
```

### ✅ 自我检查

- [ ] 创建容量=4 的 map（threshold = 4\*0.75=3，向下取整为 3），插入 4 个不同元素，是否触发了扩容？容量变成 8 了吗？
- [ ] 扩容后，所有旧 key 都能在新表中通过 `get` 取到吗？
- [ ] 检查链表顺序：插入两个落入同一桶的 key，扩容后顺序保持了吗？（写一个测试，将链表打印出来）
- [ ] `threshold` 更新是否正确？

### 📖 参考实现（建议完成后再查看）

<details>
<summary>点击展开参考代码</summary>

```java
public class SimpleHashMap<K, V> {
    // ... 前面的 Node 定义不变

    private int size;
    private int threshold;
    private final float loadFactor = 0.75f;

    public SimpleHashMap(int capacity) {
        this.table = (Node<K, V>[]) new Node[capacity];
        this.threshold = (int)(capacity * loadFactor);
    }

    public V put(K key, V value) {
        if (size > threshold) {
            resize();
        }
        int h = hash(key);
        int index = h & (table.length - 1);
        Node<K, V> head = table[index];

        for (Node<K, V> e = head; e != null; e = e.next) {
            if (e.hash == h && (e.key == key || (key != null && key.equals(e.key)))) {
                V old = e.value;
                e.value = value;
                return old;
            }
        }
        // 尾插法
        if (head == null) {
            table[index] = new Node<>(h, key, value, null);
        } else {
            Node<K, V> e = head;
            while (e.next != null) {
                e = e.next;
            }
            e.next = new Node<>(h, key, value, null);
        }
        size++;
        return null;
    }

    @SuppressWarnings("unchecked")
    private void resize() {
        int oldCap = table.length;
        int newCap = oldCap << 1;
        Node<K, V>[] newTable = (Node<K, V>[]) new Node[newCap];
        threshold = (int)(newCap * loadFactor);

        for (int i = 0; i < oldCap; i++) {
            Node<K, V> e = table[i];
            if (e == null) continue;
            // 拆分为两条链表：lo保持原索引，hi为原索引+oldCap
            Node<K, V> loHead = null, loTail = null;
            Node<K, V> hiHead = null, hiTail = null;
            while (e != null) {
                if ((e.hash & oldCap) == 0) { // 保留在原索引
                    if (loTail == null) {
                        loHead = e;
                    } else {
                        loTail.next = e;
                    }
                    loTail = e;
                } else {                       // 移动到新索引
                    if (hiTail == null) {
                        hiHead = e;
                    } else {
                        hiTail.next = e;
                    }
                    hiTail = e;
                }
                e = e.next;
            }
            // 尾部置空，避免残留指针
            if (loTail != null) loTail.next = null;
            if (hiTail != null) hiTail.next = null;
            // 设置新桶
            newTable[i] = loHead;
            newTable[i + oldCap] = hiHead;
        }
        table = newTable;
    }
}
```

**设计思路**

- `if (size > threshold)` 触发扩容，注意是 `>` 而不是 `>=`，JDK 源码使用 `> threshold`（threshold = capacity \* loadFactor），所以装填因子达到 0.75 时并不触发，超过才触发。具体可微调。
- 迁移时我们采用了 **高位判断法**：`e.hash & oldCap` 如果为 0，说明 hash 在扩容新增的高位 bit 上为 0，索引保持不变；否则索引为 `原索引 + oldCap`。这样避免了重新计算哈希，且天然保持链表相对顺序（因为我们是尾插）。
- 每个桶拆成两条链表后，一次挂载到新数组，效率高，避免了节点反复 insert。
- 尾插法实现长链表时使用了 `loTail` / `hiTail`，结束后将 `tail.next` 置 `null`，避免遗留指向旧节点的引用。

</details>

***

## 📝 练习3：高级/探索用法——增加扰动函数 & 探究负载因子影响

### 业务场景

我们发现某些对象的 `hashCode()` 低 16 位经常重复，导致桶下标碰撞严重。JDK 设计师采用 **高 16 位与低 16 位异或** 的扰动函数，让高位也参与索引计算，从而降低碰撞率。今天就来加上它，并做个简单的碰撞统计实验。

### 你的任务

1. 将 `hash()` 方法改为：
   ```java
   private int hash(Object key) {
       int h = key.hashCode();
       return h ^ (h >>> 16);
   }
   ```
2. 编写一个测试类，随机生成大量字符串（或使用 Integer 序列），分别用 **无扰动** 和 **有扰动** 的 hash 方法，统计在容量 64 下的桶分布标准差（或直接统计碰撞次数），感受扰动效果。
3. 修改负载因子为 2.0，重新运行扩容实验，观察扩容频率和链表长度变化。注意：`threshold` 要根据新因子重新计算。

### ⚡ 关键提示

- 生成大量字符串可以用 `java.util.UUID.randomUUID().toString()`。
- 统计碰撞：计算每个桶的链表长度，求和所有桶中长度>1 的部分的节点数作为碰撞数。
- 负载因子设大会导致扩容频率降低，但链表变长；设小则相反。你可以分别设置 0.5、0.75、1.0、2.0，记录插入 10000 个元素的耗时和平均链表长度。

### ✍️ 动手写代码

```java
// 实现扰动 hash，编写测试比较碰撞率
```

### ✅ 自我检查

- [ ] 扰动后的 hash 值是否实现了高 16 位与低 16 位的混合？
- [ ] 使用有扰动的 hash 后，桶分布是否更均匀（碰撞次数减少）？
- [ ] 负载因子增大时，扩容次数是否明显减少？链表长度增加了多少？

### 📖 参考实现（测试部分）

<details>
<summary>点击展开参考测试代码</summary>

```java
public class HashMapTest {
    static int hashNoDisturb(Object key) {
        return key.hashCode();
    }

    static int hashWithDisturb(Object key) {
        int h = key.hashCode();
        return h ^ (h >>> 16);
    }

    public static void main(String[] args) {
        int cap = 64;
        int total = 10000;
        int[] buckets1 = new int[cap];
        int[] buckets2 = new int[cap];

        for (int i = 0; i < total; i++) {
            String key = UUID.randomUUID().toString();
            int idx1 = hashNoDisturb(key) & (cap - 1);
            int idx2 = hashWithDisturb(key) & (cap - 1);
            buckets1[idx1]++;
            buckets2[idx2]++;
        }

        System.out.println("无扰动碰撞数：" + countCollisions(buckets1));
        System.out.println("有扰动碰撞数：" + countCollisions(buckets2));
    }

    private static int countCollisions(int[] buckets) {
        int sum = 0;
        for (int count : buckets) {
            if (count > 1) sum += (count - 1);
        }
        return sum;
    }
}
```

结果你将看到，有扰动时碰撞数量明显更低。

</details>

### 🐞 常见错误预警

- **扰动函数写错**：`h >>> 16` 是无符号右移，如果用 `>>` 会保留符号位导致高位填充 1，彻底混乱。
- **扩容时忘记重置阈值**：`threshold` 必须更新，否则后续插入不再扩容。
- **扩容时未将原链表尾的 next 置 null**：链表的最后一个节点还指向旧节点的引用，可能导致重复节点或引用残留。

***

## 🏢 大厂场景实战（使用期·扩容抖动观察）

### 场景描述

某业务缓存使用 HashMap 存储 100 万个用户 session。上线后发现频繁 Full GC。通过分析 GC 日志，发现 HashMap 频繁扩容导致大量临时 `Node` 对象产生。请提出优化方案。

### 约束条件

- 预估用户量 100 万。
- 当前初始化容量为 16（默认）。
- 内存允许适当增加。

### 你的设计任务

提出改造方案，用文字或伪代码描述，至少包含容量初始化和扩容优化两方面。

### 设计决策点

- 能否在构造时就指定一个大约足够的容量，避免前期频繁扩容？
- 估算所需容量：100 万 / 0.75 ≈ 1,333,334，最近的 2 次幂是 2^21=2097152。比默认的 16 大很多。
- 大容量下，`resize` 时的链表迁移成本如何？

### 常见方案参考

<details>
<summary>点击查看优化措施</summary>

**优化1：初始化容量**

```java
Map<String, Object> map = new HashMap<>(2097152);
```

或使用 `new HashMap<>(1350000)`，内部会通过 `tableSizeFor` 自动调整为 2097152。\
**优化2：自定义负载因子**\
如果内存富裕，可以适当降低负载因子（如 0.5），提高查找性能；如果内存紧张，可以提高到 0.9 以上，但需评估链表长度。\
**优化3：如果 key 可以转为整数，可使用** **`SparseArray`** **或** **`IntObjectHashMap`** **等专门结构，避免装箱开销**（不在今天范围）。\
**优化4：使用** **`ConcurrentHashMap`** **替代，因为其扩容是分段迁移，只是本场景并发要求不突出。**

</details>

***

## 🃏 今日面试自测卡

1. <br />

***

> 今天你亲手把一个只能存储的基本容器，升级成了具备工业级骨架的哈希表。明天我们将让它 **长出红黑树**，处理极端冲突场景。别忘了把你的 `SimpleHashMap` 上传到 GitHub 仓库。

# 第 3 天：HashMap 树化与退化完整实现

本日掌握：当链表太长时自动转为红黑树，在节点减少时退化为链表，理解树化的触发条件与代价\
覆盖原理点：3 (HashMap 树化与退树化), 1 (数据结构与扰动)\
阶段：使用期

## 🎯 今日目标

- 能解释为什么链表长度≥8 且数组长度≥64 时才会树化。
- 能在自己的 `SimpleHashMap` 中添加树化逻辑，使用 `TreeNode` 并维护树结构。
- 能实现 `resize` 时树的拆分，以及节点数≤6时退化为链表。
- 通过对比实验，亲身体验树化对性能的改善和代价。

***

## 📝 练习1：基础用法——定义 TreeNode 并实现树化判断（必做）

### 业务场景

在昨天的配置中心里，如果大量 key 被故意构造出相同的哈希低 4 位（或通过哈希碰撞攻击），导致某些桶的链表长度超过 8，get() 性能将严重退化。我们需要引入红黑树来防御这种情况。

### 你的任务

1. 在你的 `SimpleHashMap`（扩容版）基础上，定义 `TreeNode<K,V>` 类，继承自 `Node<K,V>`。
   - TreeNode 需要额外的字段：parent, left, right, red, prev（用于维持双向链表顺序，方便退化）。
   - 暂时不需要实现完整的红黑树自平衡算法（练习3再做），今天只完成框架和树化/退化的条件与结构转换。
2. 修改 `put` 逻辑：当某个桶的链表长度达到 `TREEIFY_THRESHOLD = 8` 时，调用 `treeifyBin(tab, hash)`。
   - 在 `treeifyBin` 中：先检查数组长度是否 ≥ `MIN_TREEIFY_CAPACITY = 64`，如果不到，优先扩容（调用 resize）而不是树化。
   - 如果长度达标，将该桶的所有 `Node` 替换为 `TreeNode`，并将链表结构转换为红黑树结构（今天可先只转换为链表形式的 TreeNode 双向链表，不执行平衡）。
3. 实现退化方法 `untreeify`：当调用 `resize` 拆分树或删除节点导致节点数 ≤ `UNTREEIFY_THRESHOLD = 6` 时，将树节点转换回普通 Node 链表。
4. 在 `get` 中能够区分普通 Node 和 TreeNode，以处理树节点的查找（今可暂时遍历链表，因为树平衡未实现）。

### ⚡ 关键提示

- TreeNode 继承 Node，所以桶数组仍声明为 `Node<K,V>[]`。判断节点类型可以用 `instanceof TreeNode`。
- 红黑树是平衡二叉搜索树，查找 O(log n)。但在没有实现平衡之前，我们可以简单地把 TreeNode 排成二叉搜索树（按 hash 排序，hash 相同再按 key 的 Comparable 排序），虽然可能退化为单支，但至少验证了结构切换的流程。
- 维持双向链表：每个 TreeNode 都有 prev 和 next（继承自 Node 的 next 作为后继，prev 作为前驱）。这样退化时可以直接遍历链接还原为 Node 链表。
- `treeifyBin` 中，遍历原链表的每个 Node，用其数据创建 TreeNode，按 BST 规则插入（可参照 `TreeMap` 的 `put` 逻辑，但可以先按 key 的哈希值大小插入，不保证平衡）。

### ✍️ 动手写代码

```java
// 在 SimpleHashMap 中加入 TreeNode 定义、TREEIFY_THRESHOLD 等常量
// 实现 treeifyBin 和 untreeify
// 修改 resize 使得拆分树时也能调用 untreeify 或 TreeNode.split()
```

### ✅ 自我检查

- [ ] 创建容量 16 的 Map，连续插入 9 个不同 key（需要让它们落在同一个桶，可重写 hashCode 返回 1），是否触发了扩容而不是树化？容量扩大到 32。
- [ ] 继续插入到容量≥64，桶内节点≥9时，该桶是否变成了 TreeNode 链表？
- [ ] 删除该桶节点至 ≤6 时，是否退化为普通 Node 链表？
- [ ] get 仍然能正确取出所有 key-value。

### 📖 参考实现（建议完成后再查看）

<details>
<summary>点击展开参考代码</summary>

```java
public class SimpleHashMap<K, V> {
    static final int TREEIFY_THRESHOLD = 8;
    static final int UNTREEIFY_THRESHOLD = 6;
    static final int MIN_TREEIFY_CAPACITY = 64;

    static class Node<K, V> {
        final int hash;
        final K key;
        V value;
        Node<K, V> next;

        Node(int hash, K key, V value, Node<K, V> next) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;
        }
    }

    static final class TreeNode<K, V> extends Node<K, V> {
        TreeNode<K, V> parent;
        TreeNode<K, V> left;
        TreeNode<K, V> right;
        TreeNode<K, V> prev;   // for maintaining the linked list during treeification
        boolean red;

        TreeNode(int hash, K key, V value, Node<K, V> next) {
            super(hash, key, value, next);
        }

        // root 查找等将在后续完善
    }

    private Node<K, V>[] table;
    private int size;
    private int threshold;
    private final float loadFactor = 0.75f;

    // ... 构造函数、hash、resize 同前 ...

    // 树化入口
    private void treeifyBin(Node<K, V>[] tab, int index) {
        if (tab == null || tab.length < MIN_TREEIFY_CAPACITY) {
            resize();
            return;
        }
        Node<K, V> e = tab[index];
        if (e == null || e instanceof TreeNode) return;

        // 将链表 Node 转为 TreeNode 并建立双向链表
        TreeNode<K, V> head = null, tail = null;
        while (e != null) {
            TreeNode<K, V> p = new TreeNode<>(e.hash, e.key, e.value, null);
            if (tail == null) {
                head = p;
            } else {
                p.prev = tail;
                tail.next = p;
            }
            tail = p;
            e = e.next;
        }
        // 替换桶头
        tab[index] = head;

        // 构建红黑树（当前版本先不实现平衡，只转为链表结构）
        // 实际会调用 head.treeify(tab)，这里省略。
    }

    // 退化：将 TreeNode 链表转为普通 Node 链表
    private Node<K, V> untreeify(Node<K, V>[] tab, int index) {
        Node<K, V> e = tab[index];
        if (e == null || !(e instanceof TreeNode)) return e;
        TreeNode<K, V> head = (TreeNode<K, V>) e;
        Node<K, V> newHead = null, newTail = null;
        for (TreeNode<K, V> q = head; q != null; q = (TreeNode<K, V>) q.next) {
            Node<K, V> p = new Node<>(q.hash, q.key, q.value, null);
            if (newTail == null) {
                newHead = p;
            } else {
                newTail.next = p;
            }
            newTail = p;
        }
        tab[index] = newHead;
        return newHead;
    }

    // 删除后检查是否需要退化（在 remove 中调用）
    private void checkUntreeify(Node<K, V>[] tab, int index) {
        Node<K, V> e = tab[index];
        if (e instanceof TreeNode) {
            int count = 0;
            for (TreeNode<K, V> p = (TreeNode<K, V>) e; p != null; p = (TreeNode<K, V>) p.next) {
                count++;
            }
            if (count <= UNTREEIFY_THRESHOLD) {
                untreeify(tab, index);
            }
        }
    }
}
```

**设计思路**

- `treeifyBin` 优先扩容，因为数组较小时扩容同样能分散冲突节点，且开销比树化小得多。这是 JDK 的优化。
- TreeNode 之间通过 `prev/next` 维护了原链表顺序，这样迭代器还可以正常遍历，且退化时只需串联起来。
- `checkUntreeify` 在删除后调用，统计树节点数量，低于阈值即退化，避免树在节点减少时维持不必要的复杂度。
- 今天省略了红黑树的自平衡实现，以便聚焦在结构切换流程，练习3再补充。

</details>

***

## 📝 练习2：中级用法——实现 TreeNode.get 查找与遍历验证（必做）

### 你的任务

为 TreeNode 添加查找方法 `find(int h, Object k)`，利用二叉搜索树的性质（当前树未平衡，但按哈希大小构建了 BST）快速查找，否则回退到线性扫描。同时编写一段测试代码，往同一个桶放入大量元素（通过重写 hashCode 返回定值），比较 `get` 在纯链表和树化后的时间差。

### ⚡ 关键提示

- 如果树未平衡，BST 可能退化为链表，查找还是 O(n)。本节课可以先实现简单按 hash 比较查找。
- 测试：可以先构造碰撞 key，先不触发树化（限制数组容量很小），统计 get 耗时；再调整容量≥64 触发树化，再测 get 耗时。
- 可以用 `System.nanoTime()` 多次查询取平均值。

### ✍️ 动手写代码

```java
// 在 TreeNode 中实现 find 方法
final TreeNode<K, V> find(int h, Object k) {
    TreeNode<K, V> p = this;
    do {
        int ph = p.hash;
        K pk = p.key;
        if (ph > h) {
            p = p.left;
        } else if (ph < h) {
            p = p.right;
        } else if (pk == k || (k != null && k.equals(pk))) {
            return p;
        } else if (p.left == null) {
            p = p.right;
        } else if (p.right == null) {
            p = p.left;
        } else {
            // 当哈希相等但 key 不相等时，需要遍历两个子树
            TreeNode<K, V> q = p.right;
            while (q.left != null) q = q.left;  // find min in right subtree
            p = (q.hash <= h) ? p.left : p.right;
        }
    } while (p != null);
    return null;
}
```

### ✅ 自我检查

- [ ] 树化后 `get` 速度是否明显提升（尤其碰撞数量很大时）？
- [ ] 在树未平衡的情况下，退化查找与链表查找时间相当，但树结构框架正确。
- [ ] 删除节点后树节点数减少，最终退化为链表，再次 get 也正确。

***

## 📝 练习3：高级/探索用法——添加红黑树平衡（选做挑战）

### 你的任务

参考 JDK 源码的 `balanceInsertion`、`rotateLeft`、`rotateRight` 等方法，为你的 HashMap 实现完整的红黑树平衡。这是大工程，但完成后你将具有深度理解。可逐步完成：

1. 实现左旋、右旋方法。
2. 实现插入后平衡调整 `balanceInsertion(root, x)`。
3. 实现删除节点及平衡调整（最难，可仅做插入平衡）。
4. 修改 `treeify` 将链表 TreeNode 构建为平衡红黑树（调用 `balanceInsertion` 批量插入）。

### ⚡ 关键提示

- 红黑树性质：根黑色、红色节点的子节点必须黑色、从任一节点到叶子节点的路径包含相同数量的黑色节点。
- 插入节点默认为红色，然后向上调整。
- 用 `if` 处理不同叔叔节点颜色的情况。
- 可参考《算法导论》或 OpenJDK 的 `TreeNode.balanceInsertion` 代码。

### ✍️ 动手写代码

```java
// 实现 rotateLeft, rotateRight, balanceInsertion
```

### ✅ 自我检查

- [ ] 插入大量元素后，树的高度是否保持在 O(log n)？可以通过计算最深路径验证。
- [ ] 所有红黑树性质是否满足？（可编写断言检查）
- [ ] get 性能是否稳定且快速？

> **友情提示**：红黑树实现非常考验基本功，完成后你将透彻理解 TreeMap 和 HashMap 的 TreeNode。如果时间有限，可先搞定练习1和2，将树化流程走通，面试中能讲清树化条件与退化时机已足够出色。

***

## 🏢 大厂场景实战（原理期过渡）

### 场景描述

你维护的缓存服务被攻击者利用：攻击者故意构造大量哈希碰撞的字符串（Hash Collision DoS），导致 HashMap 桶内链表极长，CPU 100% 用于链表查找，服务不可用。你将如何防御？

### 约束条件

- 必须保持 O(1) 的平均性能。
- 不能更换键的类型。
- 必须线程安全（已有 ConcurrentHashMap）。

### 你的设计任务

给出防护方案，并说明为什么 TreeMap 的树化能缓解该攻击，以及还有什么额外措施。

### 常见方案参考

<details>
<summary>点击查看业界方案</summary>

**方案1：树化防御**\
HashMap 从 Java 8 开始，链表过长转为红黑树，将最坏 O(n) 降为 O(log n)。攻击者需要构造更多碰撞才能造成影响，成本指数级上升。

**方案2：限制请求频次与键长度**\
在网关层限制请求中携带参数的数量和键的长度。

**方案3：使用更安全的哈希函数**\
如 `murmurhash` 或随机种子，让攻击者难以预测碰撞。Java 的 `String.hashCode` 算法公开，可逆向构造碰撞。

**方案4：使用** **`ConcurrentHashMap`**\
虽然 JDK 8 的 CHM 也支持树化，但它还有分摊扩容等特性，避免单线程阻塞。

</details>

***

## 🃏 今日面试自测卡

1. <br />

***

> 今天你让 HashMap 获得了智能进化的能力。把今天的树化代码打上 tag，明天我们将基于 `LinkedHashMap` 实现 LRU 缓存淘汰，你会看到双向链表的力量。

# 第 4 天：LinkedHashMap 与 LRU 缓存

本日掌握：利用 LinkedHashMap 实现 LRU 缓存淘汰，并能手写 HashMap+双向链表版本，理解其与 HashMap 的结构差异\
覆盖原理点：8 (LinkedHashMap 与 LRU 实现)\
阶段：使用期

## 🎯 今日目标

- 能用 `LinkedHashMap` 的 `accessOrder` 参数和 `removeEldestEntry` 写出一个固定容量的 LRU 缓存。
- 能解释 LinkedHashMap 内部的双向链表如何维护插入顺序/访问顺序。
- 能不借助 JDK 集合，用 `HashMap` + 自实现双向链表写出一个工业可用的 LRU 缓存。
- 能够对比二者性能，并说出 LinkedHashMap 在迭代时为什么比 HashMap 更快。

***

## 📝 练习1：基础用法——用 LinkedHashMap 实现 LRU 缓存（必做）

### 业务场景

用户画像系统中，需要缓存最近访问过的 1000 个用户的基本信息。当缓存满时，自动淘汰最久未被访问的用户。要求 `get` 和 `put` 操作平均 O(1)。

### 你的任务

创建一个类 `LRUCache<K,V>` 继承 `LinkedHashMap<K,V>`，实现以下效果：

- 构造时传入最大容量 `maxCapacity`。
- 重写 `removeEldestEntry` 方法，使得 `size() > maxCapacity` 时返回 `true`。
- 开启访问顺序模式：在构造函数中调用 `super(maxCapacity, 0.75f, true)`。
- 提供 `get` 和 `put` 方法（直接继承或包装）。
- 写一段测试：插入 5 个元素，容量为 3，访问最早插入的 key，再插入新元素，观察淘汰的是哪个。

### ⚡ 关键提示

- `LinkedHashMap` 构造函数的第三个参数 `accessOrder` 设为 `true` 时，每次 `get` 或 `put` 都会把该条目移到双向链表尾部，表示最近访问。
- `removeEldestEntry` 默认返回 `false`，需要重写。它会在 `put`（和 `putAll`）之后被调用，如果返回 `true`，最老的条目（双向链表头部）会被移除。
- 注意并发场景：`LinkedHashMap` 非线程安全，多线程访问需使用 `Collections.synchronizedMap` 包装或改用 `ConcurrentHashMap` 的变体。
- 若 key 已存在，`put` 会更新值并将条目移到尾部；淘汰不会因此被跳过。
- 容量控制：`maxCapacity` 应存储为实例变量，在 `removeEldestEntry` 中比较 `size() > maxCapacity`。

### ✍️ 动手写代码

```java
// 请写出你的 LRUCache 类（继承 LinkedHashMap）
```

### ✅ 自我检查

- [ ] 插入第 4 个元素时，第 1 个未被访问的元素是否被自动删除？
- [ ] 如果先 `get` 一个老元素，再插入新元素，那么被 `get` 的老元素会保留，而另一个更老的会被淘汰吗？
- [ ] `size()` 是否从未超过容量？
- [ ] 对于多次 `put` 相同的 key，淘汰行为是否正确？

### 📖 参考实现（直接展示）

```java
import java.util.LinkedHashMap;
import java.util.Map;

public class LRUCache<K, V> extends LinkedHashMap<K, V> {
    private final int maxCapacity;

    public LRUCache(int maxCapacity) {
        // 初始容量为maxCapacity，负载因子0.75，开启访问顺序
        super(maxCapacity, 0.75f, true);
        this.maxCapacity = maxCapacity;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > maxCapacity;
    }

    // 测试
    public static void main(String[] args) {
        LRUCache<String, String> cache = new LRUCache<>(3);
        cache.put("a", "1");
        cache.put("b", "2");
        cache.put("c", "3");
        // 访问 a，使其变为最近使用
        cache.get("a");
        cache.put("d", "4");  // 淘汰最老的 b
        System.out.println(cache.keySet());  // 期望输出 [c, a, d]
    }
}
```

**设计思路**

- `accessOrder = true` 让 LinkedHashMap 维护访问顺序，链表尾部是最新访问的元素，头部是最老访问的元素。
- `removeEldestEntry` 在每次插入后触发，我们检查 `size() > maxCapacity` 而非 `>=`，这样可以保证容量刚刚好满时不会误删，但也可使用 `>=`，主要看业务需求，JDK 示例多用 `>`。
- 初始容量直接传 `maxCapacity`，避免扩容带来的性能损耗。
- 该方法实现的 LRU 淘汰只基于访问次数，不涉及过期时间；如需带 TTL 的 LRU，需进一步扩展。

### 🐞 常见错误预警

- **错误**：`removeEldestEntry` 中写成 `size() >= maxCapacity`，导致刚满容量时再 `put` 已存在的 key（更新值）也会淘汰一个元素。\
  → **发现方法**：插入 3 个不同 key 后，`put("a", "new")`，如果容量为 3，检查 `size()` 是否变为了 2。如果变 2，说明不期望的淘汰发生了。
- **错误**：忘记设置 `accessOrder=true`，默认是 `false`（插入顺序），那么 LRU 将退化为 FIFO。\
  → **发现方法**：使用 `get` 访问老元素后，再插入新元素，老元素仍被淘汰，说明未按访问顺序。
- **错误**：多线程同时操作 `LinkedHashMap`，导致内部双向链表损坏，抛出 `ConcurrentModificationException` 或死循环。\
  → **解决方案**：用 `Collections.synchronizedMap(new LRUCache(...))` 包装，或者使用 `ConcurrentLinkedHashMap` 等并发 LRU 实现。

***

## 📝 练习2：中级用法——手写 LRU 缓存（HashMap + 双向链表）

### 业务场景

为了更精细地控制内存和避免 `LinkedHashMap` 的某些隐蔽行为（如序列化时需特殊处理），你需要自己实现一个 LRU 缓存，底层使用 `HashMap` 和自定义双向链表，实现 O(1) 的 `get` 和 `put`。

### 你的任务

实现一个泛型类 `SimpleLRU<K, V>`：

- 使用 `HashMap<K, Node>` 存储键到节点的映射。
- 自定义双向链表节点 `Node`，包含 `key`、`value`、`prev`、`next`。
- 维护两个哨兵：`head` 和 `tail`，初始化时互相连接，构成空链表。新访问的节点移动到链表尾部（在 `tail` 之前）；淘汰时移除 `head.next`。
- `put(K key, V value)`：若 key 已存在，更新值并移动到尾部；若不存在，创建节点加到尾部，若 `size > capacity`，移除头节点。
- `get(K key)`：返回 value 并将节点移到尾部，不存在返回 null。
- 注意处理 null 值。
- 实现 `size()` 方法。

### ⚡ 关键提示

- 双向链表的哨兵技巧可以避免对 `head`、`tail` 为 null 的特判，极大简化代码。
  - 构造时：`head.next = tail; tail.prev = head;`
  - 添加到尾部：`node.prev = tail.prev; node.next = tail; tail.prev.next = node; tail.prev = node;`
  - 删除节点：`node.prev.next = node.next; node.next.prev = node.prev;`
- 移动到尾部可以先删除再添加到尾部。
- `HashMap` 的 `remove` 用于淘汰时移除键。
- 注意哈希冲突时链表的覆盖，HashMap 自然会处理。
- 先画一下双向链表结构，再写代码。

### ✍️ 动手写代码

```java
// 实现 SimpleLRU
```

### ✅ 自我检查

- [ ] 插入 3 条数据后，`head.next` 是最老的数据吗？
- [ ] 访问某个已存在的数据后，它是否被移到尾部（`tail.prev`）？
- [ ] 容量 3 时插入第 4 条，最老的数据是否被删除？`HashMap` 中是否也同步删除？
- [ ] `get` 一个不存在的 key 返回 null，链表不变。
- [ ] 更新已存在的 key，不增加新节点，只改值并移动位置。

### 📖 参考实现（直接展示）

```java
import java.util.HashMap;
import java.util.Map;

public class SimpleLRU<K, V> {
    private final int capacity;
    private final Map<K, Node> cache;
    private final Node head; // 双向链表哨兵头
    private final Node tail; // 双向链表哨兵尾

    private class Node {
        K key;
        V value;
        Node prev;
        Node next;
        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    public SimpleLRU(int capacity) {
        this.capacity = capacity;
        this.cache = new HashMap<>();
        this.head = new Node(null, null);
        this.tail = new Node(null, null);
        head.next = tail;
        tail.prev = head;
    }

    public V get(K key) {
        Node node = cache.get(key);
        if (node == null) return null;
        moveToTail(node);
        return node.value;
    }

    public void put(K key, V value) {
        Node node = cache.get(key);
        if (node != null) {
            node.value = value;
            moveToTail(node);
        } else {
            Node newNode = new Node(key, value);
            cache.put(key, newNode);
            addToTail(newNode);
            if (cache.size() > capacity) {
                removeEldest();
            }
        }
    }

    public int size() {
        return cache.size();
    }

    // 将节点添加到尾部（tail 之前）
    private void addToTail(Node node) {
        node.prev = tail.prev;
        node.next = tail;
        tail.prev.next = node;
        tail.prev = node;
    }

    // 从链表中删除节点
    private void removeNode(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    // 将节点移到尾部：先删后加
    private void moveToTail(Node node) {
        removeNode(node);
        addToTail(node);
    }

    // 删除最老节点（头部）
    private void removeEldest() {
        Node eldest = head.next;
        if (eldest == tail) return; // 空链表
        removeNode(eldest);
        cache.remove(eldest.key);
    }

    // 测试
    public static void main(String[] args) {
        SimpleLRU<String, String> lru = new SimpleLRU<>(3);
        lru.put("a", "1");
        lru.put("b", "2");
        lru.put("c", "3");
        lru.get("a");          // 移动 a 到尾部
        lru.put("d", "4");     // 淘汰 b
        // 顺序应为 c -> a -> d
        Node p = lru.head.next;
        while (p != lru.tail) {
            System.out.print(p.key + " ");
            p = p.next;
        }
    }
}
```

**设计思路**

- 双向链表加哨兵节点是标准操作，能保证在任何情况下添加/删除操作都不用判空，简洁且不易出错。
- 将**所有操作分解为原子链表动作**：`addToTail`、`removeNode`、`moveToTail`，提高可读性和复用性。
- HashMap 只存键到节点的映射，节点同时持有 key 是为了淘汰时能反向从链表中得到 key 以删除 HashMap 中的条目，这是一种空间换时间的典型手法。
- `removeEldest` 直接取 `head.next`，因为它就是对久未被访问的节点。由于有哨兵，`head.next` 不可能是 null（最差也是指向 tail）。
- 这种实现虽然比 `LinkedHashMap` 更冗长，但他完全暴露了数据结构，非常适合面试现场手写，也方便扩展（如增加 TTL）。

### 🐞 常见错误预警

- **忘记将淘汰的节点从 HashMap 中删除**：淘汰只删了链表节点，没调用 `cache.remove(eldest.key)`，导致内存泄漏且 `size()` 不正确。\
  → **排查**：淘汰后打印 `cache.size()` 与链表长度，不一致则说明漏删。
- **移动到尾部时先** **`removeNode`** **再** **`addToTail`，若顺序搞反或直接改引用而不调用函数，极易断开链表**。\
  → **建议**：全部操作都通过 `addToTail` 和 `removeNode` 完成，禁止直接操作 `prev/next`。
- **put 时先** **`addToTail`** **再判断淘汰，淘汰可能刚刚把自己加进去的节点删掉**，如果正好容量满且 key 是新的，会错误地移除刚加的节点。\
  → **正确顺序**：先加新节点，再判断 `size > capacity` 淘汰最老节点。这是安全的，因为新节点在尾部，淘汰的是头部。

***

## 📝 练习3：高级/探索用法——LinkedHashMap 的遍历顺序与序列化陷阱

### 业务场景

你需要将 LRU 缓存持久化到磁盘（序列化），并在重启后恢复。使用 Java 原生序列化时，发现 `LinkedHashMap` 内部的双向链表被破坏，恢复后顺序丢失。同时，迭代时如果使用 `keySet()` 返回的顺序和你期望的访问顺序不一致，可能导致业务逻辑错误。

### 你的任务

1. 验证 `accessOrder=true` 对迭代顺序的影响：使用 `LinkedHashMap`（accessOrder=true 和 false）分别插入若干元素，然后用 `for(Map.Entry entry : map.entrySet())` 打印，观察顺序。
2. 测试序列化/反序列化后的顺序：将一个 `LinkedHashMap` 对象序列化到文件，再反序列化，打印其顺序是否与原顺序相同（提示：默认序列化会保留链表，但需确保 `accessOrder` 也被保留）。
3. 思考为什么 `LinkedHashMap` 在重新插入已有 key 或 `get` 时，不会产生 `ConcurrentModificationException`？原来它的迭代器有什么特殊之处？（阅读源码注释）

### ⚡ 关键提示

- `LinkedHashMap.Entry` 有 `before`、`after` 字段，用于维护双向链表。序列化时会写出所有的 entry，并恢复链表。但如果你自定义了非序列化字段，需特殊处理。
- 如果需要一个保证顺序且线程安全的缓存，考虑用 `ConcurrentSkipListMap` 或自己封装。
- JDK 的 `LinkedHashMap` 迭代器实现是“快速失败”的，但它在 `containsValue` 等方法中可能使用 `LinkedHashIterator`。

### ✍️ 动手写代码

```java
// 示例：测试 accessOrder 对迭代顺序的影响
Map<String, Integer> map = new LinkedHashMap<>(16, 0.75f, true);
map.put("a", 1);
map.put("b", 2);
map.put("c", 3);
map.get("a");
System.out.println(map.keySet()); // accessOrder=true 期望输出 [b, c, a]
```

### ✅ 自我检查

- [ ] `accessOrder=true` 时，`get` 后迭代顺序是否反映了访问顺序？相反，`accessOrder=false` 是否是插入顺序？
- [ ] 序列化再反序列化后，顺序是否保持一致？如果 `accessOrder` 丢失，你可能需要重写 `readObject`/`writeObject`。
- [ ] 如果在迭代过程中调用 `put` 修改了结构，迭代器是否会抛出 `ConcurrentModificationException`？和昨天实现的 fail-fast 对比。

### 📖 参考实现（直接展示）

```java
import java.io.*;
import java.util.*;

public class LinkedHashMapOrderTest {
    public static void main(String[] args) throws Exception {
        // 验证访问顺序
        LinkedHashMap<String, Integer> map = new LinkedHashMap<>(16, 0.75f, true);
        map.put("a", 1);
        map.put("b", 2);
        map.put("c", 3);
        map.get("a");  // 移动 a
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            System.out.print(e.getKey() + " ");
        }
        // 预期输出 b c a

        // 序列化测试
        LinkedHashMap<String, Integer> original = new LinkedHashMap<>(16, 0.75f, true);
        original.put("x", 1);
        original.put("y", 2);
        original.put("z", 3);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(original);
        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bis);
        LinkedHashMap<String, Integer> restored = (LinkedHashMap<String, Integer>) ois.readObject();
        System.out.println("\n复原后顺序：");
        for (String key : restored.keySet()) {
            System.out.print(key + " ");
        }
        // 注意：LinkedHashMap 的 accessOrder 字段是非瞬态的，会被序列化，所以能正确保留。
    }
}
```

**设计思路**

- `accessOrder` 是 `LinkedHashMap` 的一个 boolean 字段，参与默认序列化，因此反序列化后仍能保持访问顺序模式。
- 但需要注意：如果缓存中有大量过期引用，序列化可能不必要地保存所有条目，可结合 `transient` 和自定义 `readObject` 来修剪。
- 在迭代过程中修改结构触发 `ConcurrentModificationException` 是 fail-fast 行为，这一点与 `HashMap` 相同；但 `LinkedHashMap` 迭代效率更高，因为它遍历的是双向链表，无需跳过空桶。

### 🐞 常见错误预警

- **序列化后链表断裂**：如果在反序列化期间有版本不匹配，或忽略了父类字段，可能恢复失败。确保所有节点只存储非瞬态字段，或使用 `readObject`/`writeObject` 显式保存。
- **多线程同时序列化和修改**：会抛出异常或导致序列化结果损坏。生产环境应使用快照或锁保护。

***

## 🔍 扩展思考

1. 如果我们需要缓存中不仅淘汰最久未访问的元素，还要淘汰**最久未修改且未来利用率低**的元素（类似 Redis 的 LFU），你该如何设计？能否在现有双向链表基础上增加一个访问频率计数器？
2. 对比我们手写的 LRU 和 JDK 的 `LinkedHashMap`，在内存占用上有什么差异？哨兵节点和内部类 Node 的开销是否值得？（可以画内存布局）

***

## 🏢 大厂场景实战

### 场景描述

一个社交 App 的会话列表缓存，需要本地缓存最近 200 个聊天会话，每个会话包含最后一条消息摘要、未读计数等。同时，当用户主动删除一个会话时，缓存需要立即移除该条目；每隔 30 分钟，缓存需要自动淘汰**超过 1 小时未被访问**的会话（即带 TTL 的 LRU）。实现一个 `TTL_LRU_Cache`。

### 约束条件

- 总容量 200，但也要淘汰超时条目（即使没达到容量）。
- 移除操作要 O(1)。
- 不能简单用 `LinkedHashMap`，因为需要每个条目单独记录最后访问时间戳。

### 你的设计任务

请写出你的设计方案（可用文字 + 关键代码框架）。

### 设计决策点（引导思考）

- 可以在节点内存储一个 `lastAccessTime` 字段，淘汰时多一个判断。
- 如何高效检查过期条目？是通过定期任务扫描，还是在每次 `get`/`put` 时惰性删除？
- 如果采用惰性删除，是否会导致某些从不被访问的过期条目一直占用内存？需要补充一个后台清理线程吗？

### 常见方案参考及其取舍分析（直接展示）

**方案A：惰性删除 + 定期扫描**

- 每个 Node 存储 `lastAccessTime`。
- `get` 时检查当前时间 - `lastAccessTime` 是否超过 TTL，若是则返回 null 并从链表中删除。
- 同时，单独启一个低优先级的守护线程，每隔一段时间（如 1 分钟）遍历链表，删除所有过期节点。
- **优点**：实现直观，保证内存不会无限增长。
- **缺点**：遍历开销 O(n)，如果缓存很大（10 万级）且 TTL 较短，遍历可能成为瓶颈。但本场景只有 200 个，完全可行。

**方案B：时间轮或分层时间桶**

- 构建多个 LRU 链表，按到期时间分桶（如 10 秒一个桶）。
- 到期时，直接丢弃整个桶的链表。
- **优点**：淘汰操作 O(1)，适合海量高吞吐。
- **缺点**：实现复杂，且粒度受限，对于 200 条数据杀鸡用牛刀。

**推荐**：对于 200 容量的场景，**方案A** 最合适，简洁且能满足所有需求。添加一个后台线程或利用 `ScheduledExecutorService` 每 30 秒清理一次，足以应对。

***

## 🏆 大厂面试题

### 面试题1：LinkedHashMap 与 HashMap 数据结构上最大的区别是什么？它如何保证遍历顺序？

**难度**：⭐️⭐️⭐️

**参考答案**：

- **核心概念**：LinkedHashMap 在 HashMap 的数组+链表/红黑树基础上，为每个 Entry 增加了 `before` 和 `after` 两个引用，将所有 Entry 串联成一个双向链表。这个双向链表维护了迭代顺序，可以是插入顺序（默认）或访问顺序（构造函数中指定 `accessOrder=true`）。
- **工作流程**：遍历时，迭代器直接沿着双向链表从头部走到尾部，不需要像 HashMap 那样扫描整个数组并跳过空桶，因此**迭代速度更快**，特别是当容量大但元素少时，优势明显（因为遍历的是链表而非数组）。
- **关键点**：插入新节点时，会更新链表的尾部引用；访问（get）时，如果 `accessOrder=true`，会调用 `afterNodeAccess` 将该节点移到链表尾部。这要求链表维护操作要非常小心，避免产生环。
- **常见追问**：面试官可能会问“如果 `put` 一个已存在的 key，链表如何变化？”\
  → 回答：“首先在 HashMap 的桶中找到已有节点，更新 value，然后调用 `afterNodeAccess` 将其移到链表尾部（仅当 `accessOrder=true` 时），所以它不会产生新节点，但会改变顺序。”
- **易错提醒**：很多人以为 LinkedHashMap 的遍历是通过维护一个单独的队列，其实它是直接修改了 Entry 的结构，使其同时属于哈希桶的单链表（或树）和全局双向链表。这种双重身份容易在并发修改时导致两个结构不一致，引发死循环。正确理解是每个节点同时存在于两个链表中。
- **自我反思**：我能否画出 LinkedHashMap 内部数据结构的示意图？包括桶数组、两个节点在同一个桶内的单链关系以及它们之间的双向全局顺序链？如果不能，立刻画一遍。

***

### 面试题2：如何用 HashMap 和双向链表实现一个 LRU 缓存？要求 get/put 都是 O(1)。

**难度**：⭐️⭐️⭐️⭐️

**参考答案**：

- **核心概念**：利用 HashMap 存储 key → Node 的映射，实现 O(1) 查找；利用双向链表维护节点访问时序，头部是最近一次访问过的节点，尾部是最久未访问的节点（或反之，依据你如何移动）。每次访问都将节点移到链表头部（或尾部），淘汰时删除链表尾部节点。
- **工作流程**（以公认实现为例）：
  1. `get(key)`：从 map 中获取节点，若存在，将该节点从原链表中摘除，并插入到链表头部（表示最新），返回 value。
  2. `put(key, value)`：若 key 已存在，更新值，并将节点移到头部；若不存在，创建新节点加入 map 并插入链表头部；然后检查是否超过容量，若超过，则移除链表尾部的节点，并从 map 中删除。
- **关键点**：双向链表必须使用哨兵节点（dummy head 和 dummy tail），这样插入/删除操作无需判空，代码极简。移动节点时通常分步：`removeNode(node)` 和 `addToHead(node)`，保证指针正确。
- **常见追问**：
  - “为什么需要双向链表而不是单向链表？” → 因为删除任意节点（如淘汰尾部）需要找到前驱节点，双向链表可以直接通过 `node.prev` 定位，O(1)；单向链表必须遍历找到前驱，变成 O(n)。
  - “链表节点中为什么要存 key？” → 淘汰尾部节点时，我们需要从 HashMap 中删除对应的 key，因此节点必须持有 key，否则无法知道该删除哪个映射。
- **易错提醒**：很多手写代码会在删除链表尾部后忘记 `map.remove(node.key)`，导致缓存实际容量膨胀。同样，更新已存在 key 时，只改值不移到头部，也会破坏 LRU 语义。务必画图检查每个操作的指针变化。
- **自我反思**：如果我被要求实现 LFU（最不经常使用），只需将双向链表改成多个频度链表吗？还要添加什么？是否能从 LRU 实现中平滑扩展？

***

### 面试题3：`accessOrder=true` 的 LinkedHashMap 在多线程环境下安全吗？为什么？

**难度**：⭐️⭐️⭐️

**参考答案**：

- **核心概念**：不安全。LinkedHashMap 没有做任何同步，其迭代器是 fail-fast 的，但仍可能导致并发访问时内部双向链表被破坏，出现死循环或不一致。
- **工作流程**：当两个线程同时 `put` 或一个 `put` 一个 `get` 时，可能会同时修改双向链表的 `before`/`after` 指针，没有同步保护，极易导致节点丢失或产生环。例如，一个线程正在将节点 A 移到尾部，设置了 A.after = tail，但还没来得及修改 tail.prev，另一个线程也开始移动另一个节点，覆盖了部分指针，最终形成循环链表。
- **关键点**：即使使用 `Collections.synchronizedMap(new LinkedHashMap(...))` 包装，也只能保证单个方法调用的原子性，但不能保证复合操作（如“检查-然后-修改”）的原子性。例如，先 `containsKey` 再 `put` 的间隙，可能被其他线程插入。对于 LRU 场景，复合操作很常见。
- **常见追问**：“那如果用 `ConcurrentHashMap` 呢？” → ConcurrentHashMap 无法直接提供全局顺序，也没有内置 LRU 淘汰。你可以自己基于 ConcurrentHashMap 和并发队列实现，但复杂度高。
- **易错提醒**：很多人以为用了 `synchronizedMap` 就万事大吉，但它只是在每个方法上加了 `synchronized` 锁，依然可能因迭代期间的并发修改抛出 `ConcurrentModificationException`。正确做法是使用并发缓存库（如 Caffeine、Ehcache），或自己用 `ReadWriteLock` 等精细控制。
- **自我反思**：能不能基于上面手写的 LRU 加上 `ReentrantReadWriteLock` 实现线程安全？分析读写锁在不同操作下的互斥关系。

***

### 面试题4：Caffeine 或 Guava Cache 中的 LRU 与 LinkedHashMap 的 LRU 有什么质的区别？为什么要自己实现？

**难度**：⭐️⭐️⭐️⭐️

**参考答案**：

- **核心概念**：LinkedHashMap 的 LRU 是基于**单链表**（严格的双向链表）维护顺序，淘汰策略只能是最久未访问（LRU）。而 Guava Cache 和 Caffeine 实现了**更高级的淘汰策略**，如基于频率的 LFU、基于大小的权重、基于时间的过期（读写不同计时）等，并且做了很多并发优化。
- **工作流程**：Caffeine 使用 **W-TinyLFU** 算法，结合了 LRU 和 LFU 的优势，使用 Count-Min Sketch 记录访问频率，并用一个主 LRU 链表和一个保护队列。它还将缓存拆分为多个段，使用类似 `ConcurrentHashMap` 的分段方式实现高并发。
- **关键点**：LinkedHashMap 淘汰仅发生在 put 时，且单线程下 O(1)。但它没有内置的 TTL（需结合 `removeEldestEntry` 增加时间判断），缺乏并发支持和容量弹性。而 Caffeine 可以自动刷新、异步加载、基于引用回收等。
- **常见追问**：“如果让你优化 LinkedHashMap 的并发性，你会怎么做？”\
  → 回答：可以采用分段锁，将桶数组分成多个段，每个段维护自己的 LRU 双向链表，并定期合并统计信息。或者使用无锁队列加上原子变量，但复杂度极高，通常直接使用 Caffeine 等成熟框架。
- **易错提醒**：很多开发者在只需要简单 LRU 时，直接引入 Guava 或 Caffeine 导致过度设计。正确做法应根据需求判断：容量小、无并发、无过期 → 就使用 `LinkedHashMap`；需要过期、异步加载、统计 → 使用 Caffeine。
- **自我反思**：你清楚 Caffeine 的 `expireAfterWrite` 和 `expireAfterAccess` 的区别吗？它们分别对应什么业务场景？尝试用我们手写的代码模拟一下策略差异。

***

### 面试题5：如果让你设计一个支持过期时间（TTL）且容量限制的缓存，在 LinkedHashMap 的基础上如何扩展？

**难度**：⭐️⭐️⭐️⭐️⭐️

**参考答案**：

- **核心概念**：在原有的 `removeEldestEntry` 逻辑上，不仅要检查 `size() > capacity`，还要检查**最老条目是否已过期**。注意，最老条目可能不是过期的那个，因为某些条目虽然时间上最老，但可能还在 TTL 内。因此不能完全依赖头部淘汰。
- **工作流程**：一种方式是为每个 Entry 额外存储一个 `expireTime`。每次 `get` 时检查是否过期，过期则返回 null 并惰性删除。`put` 时也要循环检查头部条目是否过期，如果是则删除，直到找到一个未过期的头部，然后再判断容量。但这样可能需要遍历链表，会破坏 O(1)。
- **关键点**：要维持 O(1) 淘汰，可以**维护两条双向链表**：一条按访问顺序（LRU），一条按过期时间排序（类似于最小堆）。这样淘汰时，只需要检查过期链表头部，如果过期就删除它。删除时需同时从两个链表中移除。这会增加节点开销和同步复杂度。
- **常见追问**：
  - “如果不用两条链表，有其他地方优化吗？” → 可以基于时间轮算法，将过期任务放入时间槽中，淘汰时只处理到期的槽，复杂度接近 O(1)。
  - “在 `removeEldestEntry` 中直接遍历链表删除所有过期节点，虽然单次最坏 O(n)，但在有限容量下可接受吗？” → 实际上，对于容量不大的缓存（如 1000 以内），简单遍历是完全可行的，也是性能与实现成本的最佳平衡。
- **易错提醒**：很多人以为 LinkedHashMap 的 `removeEldestEntry` 只能移除一个，其实它可以返回 true 多次，只要调用一次 `put` 可能会连续淘汰多个（因为 `put` 内部会循环调用 `removeEldestEntry` 直到容量合规）。因此可以直接在里面实现“淘汰所有过期条目+容量超标淘汰最老条目”。
- **自我反思**：这种扩展后，缓存的 `size()` 可能因为惰性删除不准确，你该如何解决？是不是需要一个原子计数器或单独维护有效容量？

***

> 今天你为自己的工具库增添了 LRU 缓存，既掌握了“偷懒”使用 LinkedHashMap 的方式，又深刻理解了 HashMap+双向链表的组合拳。继续加油，明天我们将探索 TreeMap 和红黑树的排序性能。

