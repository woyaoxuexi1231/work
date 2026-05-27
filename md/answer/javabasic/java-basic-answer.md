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

以下是对代码的深度优化，**重点解决您的两个核心疑问**：

1. **明确标注适用场景**（基于实际生产经验）
2. **逐行解析线程安全实现原理**（带源码级注释，直指关键并发控制点）

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

以下是对代码的深度优化，**重点解决您的两个核心疑问**：

1. **明确标注适用场景**（基于实际生产经验）
2. **逐行解析线程安全实现原理**（带源码级注释，直指关键并发控制点）

***

# Caffeine vs Guava Cache

本地缓存技术的演进本质是**解决内存管理效率与并发安全性的矛盾**：`LinkedHashMap` 是开发者**误用基础数据结构**实现的简陋缓存，**缺乏自动淘汰机制**；Guava Cache 在 2010 年填补了 Java 本地缓存的标准化空白，但 **LRU 算法和分段锁在高并发场景存在瓶颈**；Caffeine 于 2014 年由 Guava Cache 核心贡献者 Ben Manes 开发，通过 **W-TinyLFU 淘汰算法和无锁并发模型**，将缓存命中率提升至 85%-95%，吞吐量达 Guava 的 4-10 倍。**新项目应优先选择 Caffeine，仅当需兼容 JDK 6/7 时才考虑 Guava Cache**。

***

## 一、技术演进背景：为什么需要专用缓存库？

### 1. 早期困境：开发者手动实现缓存的痛点

- **直接使用** **`HashMap`** **的致命缺陷**：
  - **无自动淘汰机制**：缓存无限增长，**必然导致内存溢出（OOM）**。
  - **非线程安全**：高并发下需手动加锁，**性能急剧下降**。
  - **无过期策略**：数据过期需开发者自行维护定时任务，**逻辑冗余且易出错**。
- **`LinkedHashMap`** **的“伪缓存”方案**：
  虽通过重写 `removeEldestEntry()` 方法可实现 **LRU 淘汰逻辑**，但存在严重问题：
  - **单线程安全**：需外层加锁（如 `Collections.synchronizedMap`），**高并发时锁竞争剧烈**。
  - **无细粒度过期控制**：仅支持全局过期时间，**无法按条目设置过期策略**。
  - **无统计监控**：无法获取命中率、加载耗时等关键指标，**运维盲盒化**。

> 此阶段（2010 年前）开发者常因缓存管理不当引发 **OOM 或性能雪崩**，亟需标准化解决方案。

***

## 二、技术演进时间线：从基础工具到专业缓存

### 1. `LinkedHashMap`：基础容器的“缓存化”尝试（JDK 1.4，2002 年）

- **定位**：**非缓存专用**，仅为维护插入/访问顺序的 `Map` 实现。
- **解决的核心问题**：
  - 提供 **LRU 排序能力**（通过 `accessOrder` 参数控制）。
  - 允许子类重写 `removeEldestEntry()` **实现简易淘汰逻辑**。
- **遗留问题**：
  - **无并发安全设计**：需开发者自行处理线程安全，**高并发场景性能差**。
  - **淘汰策略僵化**：仅支持基于条目数量的 LRU，**无法区分数据价值**（如高频 vs 低频访问）。
  - **无过期机制**：需额外线程扫描清理，**CPU 和内存开销不可控**。

> 此阶段开发者用 `LinkedHashMap` 模拟缓存属于 **“将基础工具硬套用”**，仅适用于低并发、低复杂度场景。

***

### 2. Guava Cache：首个标准化本地缓存（Google Guava 11.0，2010 年）

- **定位**：**首个提供完整缓存能力的 Java 库**，解决手动管理缓存的痛点。
- **解决的核心问题**：
  - **标准化缓存 API**：统一提供 `maximumSize`、`expireAfterWrite` 等配置，**避免重复造轮子**。
  - **线程安全封装**：基于 `ConcurrentHashMap` 分段锁实现并发控制，**简化开发者负担**。
  - **自动加载机制**：通过 `LoadingCache` **消除缓存未命中的** **`if-else`** **模板代码**。
- **遗留问题**：
  - **淘汰算法缺陷**：
    采用 **分段 LRU（Segmented LRU）**，**无法区分高频长期热点与一次性突发流量**，导致缓存命中率仅 **70%-80%**。例如：秒杀活动中临时页面会挤占商品信息，**增加数据库穿透风险**。
  - **并发性能瓶颈**：
    分段锁（`concurrencyLevel` 固定分段数）在高并发写时**锁竞争剧烈**，吞吐量显著下降。
  - **同步刷新阻塞**：
    `refreshAfterWrite` 触发时**阻塞读请求**，导致尖峰延迟。

> Guava Cache 是 **2010 年代的里程碑**，但其设计受限于当时技术条件，**高并发场景逐渐暴露短板**。

***

### 3. Caffeine：现代高性能缓存（2014 年发布）

- **定位**：**Guava Cache 的“精神续作”**，由同一核心贡献者 Ben Manes 开发，目标是 **“构建 Java 中性能最好的本地缓存”**。
- **解决的核心问题**：
  - **淘汰算法升级**：
    用 **W-TinyLFU（Window-TinyLFU）** 替代 LRU，**结合访问频率与时间维度**：
    - **窗口区（Window）**：用 LRU 过滤一次性热点（默认占 1% 容量）。
    - **主缓存区（TinyLFU）**：通过 **Count-Min Sketch 概率统计高频数据**，**命中率提升至 85%-95%**。
  - **无锁并发架构**：
    基于 `Striped Lock + CAS` **动态适配并发**，**消除固定分段锁竞争**，吞吐量达 Guava 的 **4-10 倍**。
  - **异步非阻塞刷新**：
    `refreshAfterWrite` **返回旧值并异步加载新值**，**彻底避免请求阻塞**。
- **关键优势**：
  - **完全兼容 Guava API**：迁移成本极低（仅需替换依赖和构建类）。
  - **Spring 官方背书**：Spring Boot 2.0+ **默认采用 Caffeine 作为本地缓存实现**。

> Caffeine 是 **针对 Guava Cache 瓶颈的精准优化**，尤其适合 **高并发、高命中率要求**的场景（如电商、风控）。

***

## 三、技术对比：核心问题解决能力

### 1. 淘汰算法：精准度决定缓存价值

#### ## 一、Guava Cache 的 LRU 局限

- **仅依赖访问时间**：无法识别“**高频但偶尔未访问**”的数据（如周期性报表）。
- **缓存污染严重**：突发流量（如秒杀页面）会挤占长期热点数据，**命中率下降 10%-20%**。

#### ## 二、Caffeine 的 W-TinyLFU 突破

- **频率感知**：通过 **Count-Min Sketch 用 4 位存储访问频率**，精准保留高频数据。
- **抗突发流量**：新数据先进入窗口区，**持续高频访问才进入主缓存**，避免误淘汰。
- **效果**：在电商大促场景中，**缓存穿透率降低 30% 以上**。

***

### 2. 并发模型：高并发下的性能分水岭

#### ## 一、Guava Cache 的分段锁瓶颈

- **固定分段数**：`concurrencyLevel` 需预设，**过小则锁竞争，过大则内存浪费**。
- **写操作阻塞读**：高并发写时，**吞吐量下降 50% 以上**。

#### ## 二、Caffeine 的无锁设计

- **动态适配并发**：`Striped Lock` **按需分配锁粒度**，无固定分段限制。
- **读写分离优化**：写操作通过 CAS 更新，**读请求完全无锁**，**16 线程混合读写吞吐量达 525 万 QPS**（Guava 仅 50 万）。

***

### 3. 生产级功能：从“能用”到“好用”

#### ## 一、Guava Cache 的短板

- **统计功能薄弱**：仅提供基础命中率，**无法定位性能瓶颈**。
- **刷新机制僵化**：同步刷新导致**尖峰延迟不可控**。

#### ## 二、Caffeine 的增强能力

- **精细化监控**：
  `recordStats()` 可统计**驱逐原因、加载耗时、内存占用**，用于动态调优。
- **灵活过期策略**：
  支持 `expireAfterAccess` 与 `expireAfterWrite` **组合配置**（取较早者生效）。
- **异步加载原生支持**：
  `AsyncLoadingCache` **基于** **`CompletableFuture`** **非阻塞加载**，避免主线程阻塞。

***

## 四、选型建议：按场景匹配技术

### 1. 优先选择 Caffeine 的场景

- **高并发/低延迟系统**：
  如支付、风控、秒杀，**要求命中率 >90% 或 QPS >10 万**。
- **需要异步刷新**：
  数据加载耗时长（如远程 RPC），**必须避免请求阻塞**。
- **新项目开发**：
  **JDK 8+ 环境下无理由不选 Caffeine**（Spring 生态已全面支持）。

### 2. 仍可考虑 Guava Cache 的场景

- **JDK 6/7 兼容需求**：
  Caffeine **要求 JDK 8+**（依赖 `CompletableFuture`）。
- **极简场景**：
  配置缓存等**低频访问（QPS <1 万）、低复杂度**场景。

### 3. 避免使用 `LinkedHashMap` 的场景

- **任何需生产级缓存能力的场景**：
  **无自动淘汰、无并发安全、无统计监控**，仅适用于单元测试或玩具项目。

***

## 总结

- **技术演进本质**：
  从 `LinkedHashMap`（**基础工具误用**）→ Guava Cache（**标准化但算法落后**）→ Caffeine（**算法与架构双重突破**），核心是**解决缓存污染与高并发性能的矛盾**。
- **关键结论**：
  - **Caffeine 是当前最优解**：W-TinyLFU 算法将命中率提升至 85%-95%，无锁模型实现 4-10 倍吞吐量优势。
  - **Guava Cache 仅适用于历史项目**：新项目无兼容性需求时，**应直接使用 Caffeine**。
  - **永远不要裸用** **`LinkedHashMap`** **实现缓存**：缺乏淘汰策略与并发控制，**生产环境风险极高**。
- **行动建议**：
  新项目引入 Caffeine 依赖后，**用 2 行代码替换 Guava 构建逻辑**即可享受性能红利，迁移成本接近于零。

本地缓存的核心价值是**避免重复计算或远程调用**，将高频访问的数据存储在应用进程内存中，**将访问延迟从毫秒级（数据库/Redis）降至纳秒级**。Guava Cache 是早期解决方案，但存在**命中率低、高并发性能差**等问题；Caffeine 通过 **W-TinyLFU 淘汰算法**和**无锁并发模型**解决了这些痛点，在**高并发场景下命中率提升 10%-20%，吞吐量达 Guava 的 4-10 倍**。新项目应**优先选择 Caffeine**，仅当需兼容 JDK 6/7 时才考虑 Guava Cache。

***

## 代码范例

下面是三个可直接运行的 Java 代码示例，分别用 `LinkedHashMap`、Guava Cache 和 Caffeine 实现本地缓存，场景均模拟真实大厂业务（如配置缓存、用户信息缓存、商品详情缓存），你可以复制到工程中直接运行。

每个示例均包含 `main` 方法，运行时会在控制台打印缓存行为。运行前请确保引入对应 Maven 依赖（已在代码注释中标明）。

***

### 1. `LinkedHashMap` 示例：系统配置缓存（简单 LRU，单线程场景）

**场景**：低频访问的字典配置，仅需限制最大条目数，无过期、无统计，适合极简场景。

```java
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 使用 LinkedHashMap 实现简单的 LRU 配置缓存
 * 
 * 依赖：无（JDK 内置）
 * 适用场景：单线程、低并发、仅需限制条目数（如系统字典）
 * 缺点：无过期机制、线程不安全、无监控
 */
public class LinkedHashMapCacheDemo {

    // 最大缓存条目数
    private static final int MAX_ENTRIES = 5;

    // 匿名内部类重写 removeEldestEntry 实现 LRU
    private static final Map<String, String> configCache = new LinkedHashMap<String, String>(
            16, 0.75f, true) {  // accessOrder=true 按访问顺序排序
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > MAX_ENTRIES; // 超出容量时自动删除最久未访问条目
        }
    };

    public static void main(String[] args) {
        // 模拟从数据库加载配置
        String[] keys = {"db.url", "db.user", "db.password", "app.timeout", "app.lang", "app.version", "app.name"};
        for (String key : keys) {
            String value = loadFromDB(key);  // 模拟加载
            configCache.put(key, value);
            System.out.printf("加载配置 [%s] -> [%s]，当前缓存大小: %d%n", key, value, configCache.size());
        }

        System.out.println("\n访问缓存 'db.url': " + configCache.get("db.url"));
        // 再次放入新配置，触发淘汰
        configCache.put("extra.config", "123");
        System.out.println("放入新配置后，缓存内容: " + configCache);
    }

    private static String loadFromDB(String key) {
        // 模拟耗时加载
        sleep(50);
        return "val_of_" + key;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
```

***

### 2. Guava Cache 示例：用户信息缓存（自动加载、过期、统计）

**场景**：用户信息读取，需要自动加载、写入后5分钟过期、最大容量限制、记录命中率。适合 JDK 6/7 或简单缓存需求。

```java
import com.google.common.cache.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 使用 Guava Cache 实现用户信息缓存
 * 
 * Maven 依赖:
 * <dependency>
 *   <groupId>com.google.guava</groupId>
 *   <artifactId>guava</artifactId>
 *   <version>31.1-jre</version>
 * </dependency>
 * 
 * 场景: 高并发用户查询，需自动加载、过期与统计
 */
public class GuavaCacheDemo {

    // 模拟数据库
    static final AtomicInteger dbLoadCount = new AtomicInteger(0);

    public static void main(String[] args) {
        LoadingCache<Long, String> userCache = CacheBuilder.newBuilder()
                .maximumSize(100)                        // 最大条目数
                .expireAfterWrite(5, TimeUnit.SECONDS)   // 写入后5秒过期（演示用）
                .recordStats()                           // 开启统计
                .build(new CacheLoader<Long, String>() {
                    @Override
                    public String load(Long userId) {    // 缓存未命中时自动加载
                        return loadFromDB(userId);
                    }
                });

        // 模拟多次并发查询（单线程演示）
        try {
            for (int i = 0; i < 5; i++) {
                long userId = i % 3 == 0 ? 1001 : 1002; // 交替访问两个用户
                String userInfo = userCache.get(userId);
                System.out.printf("查询用户 %d -> %s (数据库加载总次数: %d)%n",
                        userId, userInfo, dbLoadCount.get());
                Thread.sleep(500);  // 模拟请求间隔
            }
            // 等待6秒，让缓存过期
            System.out.println("\n等待6秒，数据过期...");
            Thread.sleep(6000);
            String userInfo = userCache.get(1001L);
            System.out.printf("过期后查询用户 1001 -> %s (数据库加载总次数: %d)%n",
                    userInfo, dbLoadCount.get());

            // 查看统计信息
            CacheStats stats = userCache.stats();
            System.out.printf("缓存统计: 命中率=%.2f%%, 加载耗时平均值=%.2fms%n",
                    stats.hitRate() * 100, stats.averageLoadPenalty() / 1_000_000.0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String loadFromDB(Long userId) {
        dbLoadCount.incrementAndGet();
        sleep(100); // 模拟数据库查询延迟
        return "UserInfo_" + userId;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
```

***

### 3. Caffeine 示例：商品详情缓存（异步刷新、高命中率、高性能）

**场景**：高并发商品详情页，需异步刷新避免阻塞、W-TinyLFU 算法防止缓存污染，并监控统计指标。适合新项目，Spring Boot 2.0+ 默认支持。

```java
import com.github.benmanes.caffeine.cache.*;
import org.checkerframework.checker.nullness.qual.NonNull;
import java.util.concurrent.*;

/**
 * 使用 Caffeine 实现高并发商品详情缓存
 * 
 * Maven 依赖:
 * <dependency>
 *   <groupId>com.github.ben-manes.caffeine</groupId>
 *   <artifactId>caffeine</artifactId>
 *   <version>3.1.6</version>
 * </dependency>
 * 
 * 场景: 商品详情接口 QPS >10万，需高命中率与无阻塞刷新
 */
public class CaffeineCacheDemo {

    // 模拟数据库查询延迟
    static final int DB_LOAD_DELAY_MS = 200;
    static final ConcurrentHashMap<Long, String> mockDB = new ConcurrentHashMap<>();

    static {
        // 预置两条商品数据
        mockDB.put(2001L, "iPhone 15 Pro 详细信息");
        mockDB.put(2002L, "MacBook Pro M3 详细信息");
    }

    public static void main(String[] args) throws Exception {
        // 异步加载缓存
        AsyncLoadingCache<Long, String> productCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(10, TimeUnit.MINUTES)   // 写入后10分钟过期
                .refreshAfterWrite(1, TimeUnit.MINUTES)   // 写入后1分钟异步刷新（非阻塞）
                .recordStats()                            // 开启详细统计
                .buildAsync(new CacheLoader<Long, String>() {
                    @Override
                    public @NonNull String load(@NonNull Long productId) throws Exception {
                        return loadFromDB(productId);
                    }
                });

        // 模拟高并发商品详情查询
        ExecutorService executor = Executors.newFixedThreadPool(4);
        for (int i = 0; i < 10; i++) {
            final long productId = i % 2 == 0 ? 2001L : 2002L; // 交替访问两个热门商品
            executor.submit(() -> {
                try {
                    // 异步获取，非阻塞
                    CompletableFuture<String> future = productCache.get(productId);
                    String detail = future.get(50, TimeUnit.MILLISECONDS); // 设置超时
                    System.out.printf("线程[%s] 获取商品 %d -> %s%n",
                            Thread.currentThread().getName(), productId, detail);
                } catch (Exception e) {
                    System.err.println("查询超时或异常: " + e.getMessage());
                }
            });
            Thread.sleep(30); // 模拟请求间隔
        }
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);

        // 输出统计信息
        com.github.benmanes.caffeine.cache.stats.CacheStats stats = productCache.synchronous().stats();
        System.out.printf("\nCaffeine 缓存统计: 命中率=%.2f%%, 加载成功次数=%d, 加载耗时平均=%.2fms%n",
                stats.hitRate() * 100, stats.loadSuccessCount(), stats.averageLoadPenalty() / 1_000_000.0);
    }

    private static String loadFromDB(Long productId) {
        sleep(DB_LOAD_DELAY_MS); // 模拟 DB 延迟
        String data = mockDB.getOrDefault(productId, "商品不存在或已下架");
        System.out.println(" >>> 触发数据库加载, productId=" + productId);
        return data;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
```

***

### 运行说明

- 直接复制上述三个类到你的 IDE 中，确保已添加注释中的 Maven 依赖（Guava 和 Caffeine）。
- 运行每个类的 `main` 方法即可观察缓存行为，控制台会输出加载过程、命中情况及统计信息。
- 三个示例分别展示了从“基础工具硬套”到“标准化缓存”再到“高性能现代缓存”的演进，可对比代码复杂度和功能丰富度。



# 第 5 天：TreeMap 与红黑树排序探究
本日掌握：彻底搞懂 TreeMap 的排序机制，手写 Comparator，复现 `compareTo` 不一致引发的诡异 bug，并初步触摸红黑树自平衡过程  
覆盖原理点：7 (TreeMap 红黑树排序)  
阶段：使用期

## 🎯 今日目标
- 能利用 `TreeMap` 自然排序与自定义 `Comparator` 实现任意维度的排序。
- 能解释 `TreeMap` 底层使用的红黑树性质，并说明为什么它不需要哈希。
- 能复现因 `compareTo` 与 `equals` 不一致导致的“丢失元素”或“多元素重复”故障。
- 能手写红黑树的左旋、右旋代码，并理解插入修正的核心流程。
- 能回答面试中关于 `TreeMap` 时间复杂度、key 约束、与 `HashMap` 对比的所有追问。

---

## 📝 练习1：基础用法——自然排序与比较器排序（必做）

### 业务场景
某交易系统需要存储每笔订单的 ID 到金额的映射，并支持**按金额升序**批量遍历订单，以及**按订单 ID 字母序**浏览。我们不需要哈希，因为需要顺序。

### 你的任务
1. 创建一个 `TreeMap<String, Double>`，以订单 ID 为 key（自然升序）。插入 5 条记录，打印整个 map（验证 key 有序）。
2. 创建一个 `TreeMap<String, Double>`，但通过自定义 `Comparator` 改为按 **金额升序，金额相同时按 ID 降序**。插入记录后打印，验证顺序。
   - 注意：key 仍然是 ID，但 Comparator 需要访问 value。由于 `TreeMap` 的 Comparator 只能比较 key，所以这次我们把 `value` 也放在 key 里：创建类 `OrderKey` 包含 ID 和金额，实现 `Comparable` 或传入 `Comparator`。
3. 使用 `firstEntry()`、`lastEntry()`、`subMap()`、`tailMap()` 体验区间查询。

### ⚡ 关键提示
- `TreeMap` 的 `Comparator`/`Comparable` 只作用于 key。如果排序依据包含 value，需要将排序所需字段合并到 key 中。
- `subMap` 返回视图，对它的修改会影响原 map，反之亦然；需要注意边界是否包含（可提供 `boolean` 参数：`subMap(fromKey, true, toKey, false)`）。
- `Comparator` 的 `compare(o1, o2)` 应返回负数、0、正数。若返回 0，TreeMap 视为相同 key，会覆盖旧值。
- 所有 key 不能为 null（除非 Comparator 支持 null 比较），但 value 可以为 null。

### ✍️ 动手写代码
```java
// 练习1.1 自然排序
TreeMap<String, Double> ordersById = new TreeMap<>();
ordersById.put("D0001", 150.0);
ordersById.put("A0002", 200.0);
ordersById.put("C0003", 150.0);
ordersById.put("B0004", 300.0);
System.out.println(ordersById);  // 验证输出顺序

// 练习1.2 按金额排序 - 定义 OrderKey
class OrderKey implements Comparable<OrderKey> {
    String id;
    double amount;
    // 构造、getter
    public int compareTo(OrderKey other) {
        int cmp = Double.compare(this.amount, other.amount);
        if (cmp != 0) return cmp;
        return other.id.compareTo(this.id); // 金额相同，ID降序
    }
}
TreeMap<OrderKey, String> ordersByAmount = new TreeMap<>();
// 使用 id 当作 value 存
ordersByAmount.put(new OrderKey("D0001", 150.0), "D0001");
ordersByAmount.put(new OrderKey("A0002", 200.0), "A0002");
ordersByAmount.put(new OrderKey("C0003", 150.0), "C0003");
System.out.println(ordersByAmount.keySet()); // 检查顺序
```

### ✅ 自我检查
- [ ] 自然排序时，key 的遍历顺序是否按 String 自然序（字典序）？
- [ ] 自定义排序时，金额相同的订单是否按 ID 降序正确放置？
- [ ] 使用 `firstKey()` 得到的最小 key 是否符合预期？
- [ ] `subMap` 的范围是否准确包含了边界（或不包含）？

### 📖 参考实现（直接展示）

```java
import java.util.*;

public class TreeMapDemo {
    public static void main(String[] args) {
        // 1. 自然排序
        TreeMap<String, Double> natural = new TreeMap<>();
        natural.put("D", 150.0);
        natural.put("A", 200.0);
        natural.put("C", 150.0);
        natural.put("B", 300.0);
        System.out.println("自然顺序: " + natural.keySet()); // [A, B, C, D]

        // 2. 自定义排序
        TreeMap<OrderKey, String> custom = new TreeMap<>();
        custom.put(new OrderKey("D", 150.0), "D");
        custom.put(new OrderKey("A", 200.0), "A");
        custom.put(new OrderKey("C", 150.0), "C");
        custom.put(new OrderKey("B", 300.0), "B");
        System.out.print("自定义排序: ");
        for (OrderKey k : custom.keySet()) {
            System.out.print(k + " ");
        }
        // 预期: [C:150.0, D:150.0, A:200.0, B:300.0]  金额升序，同金额 ID 降序

        // 3. 区间操作
        System.out.println("\nfirstEntry: " + custom.firstEntry().getKey());
        System.out.println("lastEntry: " + custom.lastEntry().getKey());
        // 金额在 [150, 200) 的订单（不含200）
        NavigableMap<OrderKey, String> sub = custom.subMap(
            new OrderKey("", 150.0), true,
            new OrderKey("", 200.0), false
        );
        System.out.println("区间 [150,200): " + sub.keySet());
    }
}

class OrderKey implements Comparable<OrderKey> {
    String id;
    double amount;
    OrderKey(String id, double amount) { this.id = id; this.amount = amount; }
    public int compareTo(OrderKey o) {
        int cmp = Double.compare(amount, o.amount);
        if (cmp != 0) return cmp;
        return o.id.compareTo(id); // 金额相同，ID降序
    }
    public String toString() { return id + ":" + amount; }
}
```

**设计思路**  
- `OrderKey.compareTo` 实现了复合排序：先金额升序，再 ID 降序。`Double.compare` 避免了 `==` 浮点精度问题。  
- `subMap` 使用 `OrderKey("", 150.0)` 作为边界时，因为排序先看金额，ID 空串不影响比较，故能涵盖所有金额 150.0 的订单。  
- 区间范围利用 `boolean` 参数精确控制开闭，这是 `TreeMap` 比 `SortedMap` 进步的地方。

### 🐞 常见错误预警
- **错误**：`Comparator` 实现时忘记处理相等情况，直接返回 `1` 或 `-1`，导致 TreeMap 认为所有 key 都不等，无法覆盖旧值。  
  → **发现**：`put` 同一个 ID 两次，`size()` 增加了，而不是替换。检查 `compare` 是否返回 0。
- **错误**：`subMap` 修改了原 map 但没有重新获取视图，导致遍历微妙的并发修改异常（单线程修改也会影响视图）。  
  → **排查**：始终在操作视图前确认原始 map 不变，或先复制视图：`new TreeMap<>(subMap)`。
- **错误**：key 为 null 时，自然排序直接 NPE。如果必须支持 null，可提供 `Comparator.nullsFirst()` 包装。

---

## 📝 练习2：中级用法——`compareTo` 与 `equals` 不一致的陷阱（必做）

### 业务场景
你设计了一个 `Person` 类作为 key，按名字长度排序。两个人名字长度相同但内容不同，`compareTo` 返回 0，但 `equals` 返回 false。这会导致 TreeMap 视作同一个人，丢失数据。

### 你的任务
1. 定义 `Person` 类：`name` 字段，`compareTo` 只比较 `name.length()`；当长度相等时返回 0。`equals` 按标准重写（比较名字内容）。
2. 创建 `TreeMap<Person, String>`，插入两个名字长度相同但名字不同的人（如 "Alice" 和 "Bobbi"），观察结果。查询其中一个，打印 size，看是否只有一个元素。
3. 修改 `compareTo`，使其长度相同时继续比较字符串内容，使一致性问题消失，验证结果。

### ⚡ 关键提示
- `TreeMap` 只依赖 `compareTo`/`Comparator` 判断 key 的唯一性。若 `compareTo` 返回 0，则视为同一 key，会替换旧值，即使 `equals` 认为不同。
- 正确实现要求：`(compareTo(a, b) == 0) == (a.equals(b))`，否则将违反 `SortedMap` 规范。
- 如果业务上不能保证严格一致，可考虑使用 `HashMap` 并用 `List` 维护顺序，或编写包装器。

### ✍️ 动手写代码
```java
class Person implements Comparable<Person> {
    String name;
    Person(String name) { this.name = name; }
    @Override
    public int compareTo(Person o) {
        // 错误实现：仅比长度
        return Integer.compare(this.name.length(), o.name.length());
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Person)) return false;
        Person person = (Person) o;
        return name.equals(person.name);
    }
    @Override
    public int hashCode() { return name.hashCode(); }
    @Override
    public String toString() { return name; }
}
// 测试
TreeMap<Person, String> map = new TreeMap<>();
map.put(new Person("Alice"), "A");
map.put(new Person("Bobbi"), "B"); // 长度同5，compareTo返回0
System.out.println(map.size());    // 期望：2（实际：1，因为失去一个）
System.out.println(map.get(new Person("Alice"))); // 可能返回 "B"
```

### ✅ 自我检查
- [ ] 插入两个姓名长度相同的 Person，`size()` 是不是 1？
- [ ] 如果 `compareTo` 修正为长度相同再比较 `name`，`size()` 是否变 2？
- [ ] HashMap 能正常区分这两个 key 吗？（能，因为 hashcode/equals 不同）
- [ ] 你是否理解了为何 TreeMap 不应使用与 equals 不一致的比较器？

### 📖 参考实现（直接展示）

```java
import java.util.*;

public class CompareToEqualsIssue {
    static class Person implements Comparable<Person> {
        String name;
        Person(String n) { name = n; }

        // 错误版：仅比长度
        @Override
        public int compareTo(Person o) {
            return Integer.compare(name.length(), o.name.length());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Person)) return false;
            return name.equals(((Person)o).name);
        }
        @Override public int hashCode() { return name.hashCode(); }
        @Override public String toString() { return name; }
    }

    public static void main(String[] args) {
        TreeMap<Person, String> tree = new TreeMap<>();
        tree.put(new Person("Alice"), "A");
        tree.put(new Person("Bobbi"), "B"); // 长度5，compareTo=0
        System.out.println("TreeMap size: " + tree.size()); // 1，Bobbi 覆盖了 Alice
        System.out.println("Value for 'Alice': " + tree.get(new Person("Alice"))); // 输出 B

        // 修正版：长度相同时比较字符串
        TreeMap<Person, String> fixed = new TreeMap<>((a, b) -> {
            int cmp = Integer.compare(a.name.length(), b.name.length());
            return cmp != 0 ? cmp : a.name.compareTo(b.name);
        });
        fixed.put(new Person("Alice"), "A");
        fixed.put(new Person("Bobbi"), "B");
        System.out.println("Fixed size: " + fixed.size()); // 2
    }
}
```

**设计思路**  
- 通过故意错误实现，直观展现 TreeMap 使用 `compareTo` 判断唯一性的后果。  
- 修正版 Comparator 保证了与 `equals` 的一致性，即 `compare == 0` 当且仅当 `name` 相等。  
- 这是面试中常考的陷阱，理解它有助于在业务代码中正确使用 `SortedSet`/`SortedMap`。

### 🐞 常见错误预警
- 即使 `compareTo` 正确，但如果你在排序中使用 `hashCode` 或随机元素，也会导致 `compare` 不一致，可能引起无法预测的行为。
- 在自定义对象时，务必同步调整 `compareTo`、`equals` 和 `hashCode`，除非对象专门用于 TreeMap 且不用于 HashMap，但这很危险。

---

## 📝 练习3：高级/探索用法——模拟红黑树插入平衡（左旋、右旋）

### 业务场景
为了彻底理解 TreeMap 的插入为什么是 O(log n)，你需要模拟红黑树的旋转与颜色变换过程，这样可以自信地回答“红黑树如何维持平衡”的面试题。

### 你的任务
1. 创建一个简化红黑树类 `SimpleRBTree<K,V>`，节点包含 `parent, left, right, color(RED/BLACK)`。
2. 实现 **左旋** `rotateLeft(Entry<K,V> p)` 和 **右旋** `rotateRight(Entry<K,V> p)` 方法，调用后正确调整父子指针和根节点。
3. 实现插入方法 `put(K key, V value)` 仅完成 BST 插入部分。
4. 在 BST 插入之后，**加入红黑树修正逻辑** `fixAfterInsertion(Entry<K,V> x)`，至少处理叔叔节点为红色的情况，其余情况可作框架但先不实现（可留空，但整体结构要对）。
5. 写一个简单的可视化：打印树结构（横版或竖版缩进）以及颜色。

### ⚡ 关键提示
- 红黑树性质：根必黑、红节点子必黑、任一节点到叶子路径黑节点数相同。
- 插入节点默认红色，然后向上修正。
- 左旋：以某节点为支点，其右子变父，左旋后原支点变右子的左子。
- 右旋同理。
- 可参考 `java.util.TreeMap.Entry` 结构，或用独立类。
- 可视化打印可先不完美，用递归缩进即可。

### ✍️ 动手写代码
```java
class RBEntry<K, V> {
    K key; V value; RBEntry<K,V> left, right, parent;
    boolean color; // true=RED, false=BLACK
    // 构造、toString
}
class SimpleRBTree<K extends Comparable<K>, V> {
    private RBEntry<K,V> root;
    // 左旋
    void rotateLeft(RBEntry<K,V> p) { ... }
    // 右旋
    void rotateRight(RBEntry<K,V> p) { ... }
    // BST插入
    void put(K key, V value) { ... }
    // 修正
    void fixAfterInsertion(RBEntry<K,V> x) { ... }
    // 打印
    void print() { ... }
}
```

### ✅ 自我检查
- [ ] 左旋后树的中序遍历顺序是否保持不变？
- [ ] 插入连续递增的键（如1,2,3,4,5），是否触发了旋转？树的高度是否远小于5？
- [ ] 根节点颜色始终为黑色吗？
- [ ] 是否存在两个连续的红节点？

### 📖 参考实现（直接展示）

```java
public class SimpleRBTree<K extends Comparable<K>, V> {
    private static final boolean RED = true;
    private static final boolean BLACK = false;

    static class Entry<K, V> {
        K key;
        V value;
        Entry<K, V> left, right, parent;
        boolean color = RED;
        Entry(K key, V value, Entry<K, V> parent) {
            this.key = key;
            this.value = value;
            this.parent = parent;
        }
    }

    private Entry<K, V> root;

    public void put(K key, V value) {
        Entry<K, V> t = root;
        if (t == null) {
            root = new Entry<>(key, value, null);
            root.color = BLACK;
            return;
        }
        int cmp;
        Entry<K, V> parent;
        do {
            parent = t;
            cmp = key.compareTo(t.key);
            if (cmp < 0) t = t.left;
            else if (cmp > 0) t = t.right;
            else { t.value = value; return; }
        } while (t != null);

        Entry<K, V> e = new Entry<>(key, value, parent);
        if (cmp < 0) parent.left = e;
        else parent.right = e;
        fixAfterInsertion(e);
    }

    // 左旋
    private void rotateLeft(Entry<K, V> p) {
        if (p == null) return;
        Entry<K, V> r = p.right;
        p.right = r.left;
        if (r.left != null) r.left.parent = p;
        r.parent = p.parent;
        if (p.parent == null) root = r;
        else if (p.parent.left == p) p.parent.left = r;
        else p.parent.right = r;
        r.left = p;
        p.parent = r;
    }

    // 右旋
    private void rotateRight(Entry<K, V> p) {
        if (p == null) return;
        Entry<K, V> l = p.left;
        p.left = l.right;
        if (l.right != null) l.right.parent = p;
        l.parent = p.parent;
        if (p.parent == null) root = l;
        else if (p.parent.right == p) p.parent.right = l;
        else p.parent.left = l;
        l.right = p;
        p.parent = l;
    }

    // 插入修正（核心）
    private void fixAfterInsertion(Entry<K, V> x) {
        x.color = RED;
        while (x != null && x != root && x.parent.color == RED) {
            if (x.parent == x.parent.parent.left) {
                Entry<K, V> uncle = x.parent.parent.right;
                if (uncle != null && uncle.color == RED) {
                    // 情况1：叔叔红
                    x.parent.color = BLACK;
                    uncle.color = BLACK;
                    x.parent.parent.color = RED;
                    x = x.parent.parent;
                } else {
                    if (x == x.parent.right) {
                        // 情况2：LR，先左旋
                        x = x.parent;
                        rotateLeft(x);
                    }
                    // 情况3：LL，右旋染色
                    x.parent.color = BLACK;
                    x.parent.parent.color = RED;
                    rotateRight(x.parent.parent);
                }
            } else { // 对称
                Entry<K, V> uncle = x.parent.parent.left;
                if (uncle != null && uncle.color == RED) {
                    x.parent.color = BLACK;
                    uncle.color = BLACK;
                    x.parent.parent.color = RED;
                    x = x.parent.parent;
                } else {
                    if (x == x.parent.left) {
                        x = x.parent;
                        rotateRight(x);
                    }
                    x.parent.color = BLACK;
                    x.parent.parent.color = RED;
                    rotateLeft(x.parent.parent);
                }
            }
        }
        root.color = BLACK;
    }

    // 简单打印
    public void printTree() {
        print(root, "", true);
    }
    private void print(Entry<K, V> node, String indent, boolean last) {
        if (node != null) {
            System.out.print(indent);
            System.out.print(last ? "└── " : "├── ");
            System.out.println((node.color ? "R " : "B ") + node.key);
            indent += last ? "    " : "│   ";
            print(node.left, indent, node.right == null);
            print(node.right, indent, true);
        }
    }

    public static void main(String[] args) {
        SimpleRBTree<Integer, String> tree = new SimpleRBTree<>();
        int[] nums = {10, 5, 15, 3, 7, 13, 18, 1, 20};
        for (int n : nums) tree.put(n, "");
        tree.printTree();
    }
}
```

**设计思路**  
- 旋转操作直接参考 TreeMap 源码，但去除了泛型噪音，保留了核心指针重连。  
- `fixAfterInsertion` 实现了最经典的几种情况，未包含删除修正，但插入修正是面试必问。  
- 打印函数方便肉眼验证红黑树性质（根黑、无连续红、大致平衡）。  
- 如果时间有限，至少实现框架和旋转，其余用注释标注思路，表明你懂流程。

### 🐞 常见错误预警
- 旋转后忘记更新 `parent` 指针，导致子节点指向孤儿。尤其要处理当前节点是根的情况。
- 修正循环中，`x = x.parent.parent` 可能上溯到 null，要加 null 判断。
- 颜色常量定义为 boolean 容易混淆，可定义静态常量 `RED/BLACK`。

---

## 🏢 大厂场景实战：IP 地理位置查询系统

### 场景描述
某网络安全系统需要根据外部 IP 快速判断所在地区（如“北京市”“上海市”），类似 GeoIP。拥有约 50 万条 IP 段记录，包含起始 IP、结束 IP、地理位置。每次请求一个 IP，需要返回对应区域，若未匹配返回“未知”。

### 约束条件
- 请求 QPS 峰值 10000
- 内存占用尽量小
- 数据集更新频率低（每天一次），可以在内存中重建索引

### 你的设计任务
使用 TreeMap 作为核心数据结构，设计 IP 查询引擎。写出关键类/方法伪代码，并说明时间复杂度。

### 设计决策点（引导思考）
- IP 地址如何转为可比较的键？`String` 比较行吗？需要转换为整数或 `long`。
- 如何存储 IP 段？用 TreeMap 保存**每个起始 IP** 到 `(结束IP, 地理信息)` 的映射。查询时，使用 `floorEntry(IP)` 找到最后一个起始 IP ≤ 查询 IP 的段，然后判断该段的结束 IP 是否 >= 查询 IP。
- 如果使用 `subMap` 呢？可以更快？不一定，`floorEntry` 是 O(log n)。
- 内存优化：IPv4 可以用 `int`，IPv6 可以用 `BigInteger` 或自定义 128 位结构。

### 常见方案参考及其取舍分析（直接展示）

**方案：TreeMap + floorEntry**  
```java
TreeMap<Integer, IPRange> ranges = new TreeMap<>();
// IPRange 包含 int endIp, String location
ranges.put(startIp1, new IPRange(endIp1, "北京"));
ranges.put(startIp2, new IPRange(endIp2, "上海"));

public String getLocation(String ip) {
    int intIp = ipToInt(ip);
    Map.Entry<Integer, IPRange> entry = ranges.floorEntry(intIp);
    if (entry != null && intIp <= entry.getValue().endIp) {
        return entry.getValue().location;
    }
    return "未知";
}
```
- **优点**：O(log N) 查询，实现简单，无需外部依赖，50 万条内存占用约几十 MB（每个条目约几十字节）。  
- **缺点**：更新时需重建整个 TreeMap（每日一次可接受）。  
- **对比**：若用二分查找数组，占用内存更小，但更新需重排；TreeMap 代码更简洁。对于纯 IPv4，`int` 范围完全满足，`TreeMap` 是极佳选择。

---

## 🏆 大厂面试题

### 面试题1：TreeMap 底层数据结构是什么？为什么不用 AVL 树？
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：

- **核心概念**：TreeMap 基于**红黑树**，一种自平衡二叉搜索树。红黑树在插入/删除时的旋转操作比AVL树少，统计性能更优。
- **工作流程**：每次插入/删除后，通过变色和树旋转（左旋/右旋）来保证五个性质，从而确保最长路径不超过最短路径的2倍，高度 O(log n)。
- **关键点**：红黑树在频繁插入/删除的场景下，旋转次数少（最多3次），而 AVL 树由于要求更严格的平衡（高度差 ≤1），旋转可能更多，导致写性能稍弱。但 AVL 读操作稍快（更平衡）。Java 官方选择红黑树是基于实际测试：大多数 Map 操作读多写少，但红黑树的综合性能最优。
- **常见追问**：“那 HashMap 用了红黑树为什么还用链表？”  
  → 回答：链表转红黑树是为了防止哈希碰撞攻击导致 O(n)；常态下表小数据少时，红黑树维护成本高于链表，所以需要阈值控制。
- **易错提醒**：很多人以为红黑树和 AVL 一样严格平衡，实则红黑树是“黑色完美平衡”，允许局部不平衡。记住它通过染色控制黑高度，而 AVL 通过高度差。
- **自我反思**：我可以清晰地画出红黑树的五个性质吗？能否口头描述插入导致连续红节点时的三种修复情形？

---

### 面试题2：TreeMap 是如何保证 key 有序的？时间复杂度是多少？
**难度**：⭐️⭐️⭐️

**参考答案**：

- **核心概念**：TreeMap 通过在插入时比较 key 的 `compareTo`（或 Comparator）结果，将其放到二叉搜索树的合适位置，并调整平衡。中序遍历树得到有序序列。
- **工作流程**：`put` 从根开始比较，小则左走，大则右走，找到合适叶子插入，之后自底向上修复平衡。`get`、`containsKey` 类似二分查找。`subMap` 寻找边界后，中序遍历中间节点。
- **时间复杂度**：`get`、`put`、`remove`、`containsKey` 均为 O(log n)。`firstKey`、`lastKey` 也是 O(log n)（需要找到最左或最右叶子，但通常有指针优化）。遍历整个 Map 是 O(n)，因为中序遍历每个节点。
- **关键点**：如果 Comparator 不满足自反、对称、传递性，树会混乱，可能导致元素丢失或死循环。
- **常见追问**：“遍历时修改 TreeMap 会怎样？” → 如果通过非迭代器方式修改结构，迭代器会 fail-fast，抛出 `ConcurrentModificationException`。即便是同一个线程，在遍历过程中 `put` 或 `remove` 也是不安全的，应使用迭代器的 `remove` 方法。
- **易错提醒**：有人把 TreeMap 的遍历复杂度误认为 O(n log n)，实则每个节点只访问一次，所以是 O(n)。
- **自我反思**：能否自己实现一个简单的二分查找在有序数组上，对比 TreeMap 的 get 源码？理解为什么数组查找 O(log n) 但插入 O(n)，而 TreeMap 插入 O(log n)。

---

### 面试题3：在 TreeMap 中使用自定义对象作为 key，必须满足什么条件？
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：
- **核心概念**：必须实现 `Comparable` 接口或向 TreeMap 构造器提供一个 `Comparator`。且 `compareTo`/`compare` 必须与 `equals` 保持一致，同时满足 Comparator 的约定（自反性、对称性、传递性）。
- **工作流程**：如果 `compare` 返回 0，TreeMap 认为 key 相等，会覆盖旧值。如果与 `equals` 不一致，一个 key 在逻辑上不同但 `compare` 返回 0，就会在 map 里互相覆盖。此外，如果 Comparator 不满足传递性，可能破坏树的搜索结构，导致查找错误。
- **关键点**：很多 bug 源于 `compareTo` 只比较部分字段，而 `equals` 比较所有字段。建议使用 `Comparator.comparing` 或 `Guava` 的 `ComparisonChain` 构建完整比较，确保 `compare == 0` 等价于 `equals`。
- **常见追问**：“如果我想按多个属性排序，比如先年龄再姓名，怎么写？” → 可使用 `Comparator.comparing(Person::getAge).thenComparing(Person::getName)`。
- **易错提醒**：`compareTo` 中使用减法可能导致整数溢出。应用 `Integer.compare` 或 `Long.compare`。
- **自我反思**：是否检查过自己项目中所有 TreeMap 的 key 是否满足一致性？有没有可能在 compareTo 中使用 `hashCode` 或随机值导致返回 0 的情况？

---

### 面试题4：HashMap 和 TreeMap 的区别？什么时候用 TreeMap？
**难度**：⭐️⭐️⭐️

**参考答案**：
- **核心区别**：
  - 数据结构：HashMap 数组+链表/红黑树；TreeMap 红黑树。
  - 顺序：HashMap 无序；TreeMap 按键有序。
  - 时间复杂度：HashMap 平均 O(1)（发生碰撞或扩容时退化）；TreeMap 严格 O(log n)。
  - 对 key 的要求：HashMap 的 key 需要正确实现 `hashCode` 和 `equals`；TreeMap 的 key 需要实现 `Comparable` 或提供 `Comparator`。
  - 内存占用：TreeMap 每个节点要存储左右父母和颜色指针，占用更大。
- **使用场景**：
  - 需要按键的排序遍历 → TreeMap。
  - 需要范围查询（如价格区间） → TreeMap 的 `subMap` 极快 O(log n + k)，HashMap 需要遍历全表 O(n)。
  - 需要顺序处理一批数据 → TreeMap。
  - 否则默认用 HashMap 即可。
- **常见追问**：“如果我只需要恒定时间的读，但偶尔需要范围查找，怎么办？” → 可以同时维护 HashMap 和 TreeMap，但双倍内存；或者用并发跳表 `ConcurrentSkipListMap`。
- **易错提醒**：有些人误以为 TreeMap 的迭代顺序是插入顺序，实际上它是 key 的排序顺序。插入顺序是 LinkedHashMap 的特色。
- **自我反思**：如果项目中有大量按时间顺序查找的日志，用 TreeMap 存 `Long timestamp` 作为 key 是否合适？考虑用 `TreeMap` 的 `tailMap` 快速获取某时间点之后的日志。

---

### 面试题5：请解释红黑树的五个性质，并说明插入一个新节点为什么是红色
**难度**：⭐️⭐️⭐️⭐️⭐️

**参考答案**：
- **五个性质**：
  1. 节点是红色或黑色。
  2. 根节点是黑色。
  3. 所有叶子（NIL）都是黑色。
  4. 每个红色节点的两个子节点都是黑色。
  5. 从任一节点到其每个叶子的简单路径上包含相同数目的黑色节点。
- **插入红色原因**：
  插入红色节点不会违反性质5（黑色高度不变），但可能违反性质4（红红相连）。相比插入黑色节点必然违反性质5（导致整棵树黑高度不一致且难以修复），修正红红相连更简单（变色或旋转），代价更小。因此选择插入红色，然后通过 `fixAfterInsertion` 消除可能的红红冲突。
- **关键点**：修正过程可能出现“叔叔节点是红色”情况，此时只需变色上溯；而“叔叔是黑色”则依靠旋转一次解决。最多旋转两次即可恢复平衡，效率极高。
- **常见追问**：“插入后修正的循环什么时候停止？” → 当 x 是根节点或 x 的父节点是黑色时停止。最后强制根节点为黑。
- **易错提醒**：很多人认为红黑树插入修正最坏 O(log n) 旋转，实际上通过删除连续红节点最多只需 O(log n) 次颜色变换和最多 2 次旋转（插入情况）。这是面试常考细节。
- **自我反思**：我能不看书在纸上画出插入 1,2,3,4,5 的红黑树构建过程吗？如果卡住，就回去再写一遍。

---

> 今天你动手体验了 TreeMap 的排序力量，又亲手触碰到红黑树的平衡之美。明天我们将迎来重量级嘉宾：ConcurrentHashMap，揭开 JDK 1.8 的 CAS 与 synchronized 协奏。继续保持手感，你的集合框架模块即将结束。





# 树的发展

树结构的演进本质是**为解决数据有序存储与高效查询的矛盾而逐步优化的过程**。其核心逻辑链是：**普通树 → 二叉树 → 二叉搜索树（BST）→ 平衡二叉树（AVL）→ 红黑树**。每一步演进都针对前一阶段的致命缺陷进行改进，最终形成兼顾查询效率与动态操作成本的工程解决方案。以下按历史逻辑顺序详解：

------

## 一、树的原始概念：为什么需要树？

### 1. **基础需求**

- **问题**：数组查询快（$O(1)$）但插入/删除慢（$O(n)$）；链表插入/删除快（$O(1)$）但查询慢（$O(n)$）。
- **目标**：设计一种**兼顾查询与动态操作效率**的数据结构。

### 2. **树的诞生**

- **定义**：树是一种**分层的非线性结构**，由根节点、子节点和叶节点组成，满足：
  - 有且仅有一个根节点；
  - 除根节点外，每个节点有且仅有一个父节点；
  - 无环路。
- 

------

## 二、二叉树：结构简化的第一步

### 1. **定义与特性**

- 每个节点**最多有两个子节点**（左子树、右子树）。
- **关键性质**：
  - 
  - 

### 2. **局限性**

- **无序性**：普通二叉树**不保证节点值的顺序**，查询仍需遍历所有节点（$O(n)$）。
- **退化风险**：若插入顺序不理想，可能退化为链表（如始终插入右子节点）。

> **关键转折**：为解决无序问题，**二叉搜索树（BST）** 在二叉树基础上引入**节点值的顺序约束**。

------

## 三、二叉搜索树（BST）：引入有序性

### 1. **核心定义**

- 对任意节点 $x$：
  - ；
  - 。
- **直接优势**：
  - 中序遍历输出**严格递增序列**；
  - （假设数据随机分布）。

### 2. **致命缺陷：退化问题**

- **场景**：若按**升序或降序插入节点**（如插入序列 `1, 2, 3, 4, 5`）。
- **结果**：树退化为**单链表**，所有操作时间复杂度恶化至 $O(n)$。
- **本质原因**：BST **缺乏动态平衡机制**，完全依赖输入数据的随机性。

> **关键转折**：为解决退化问题，**平衡二叉树（AVL树）** 通过**严格高度约束**强制维持平衡。

------

## 四、平衡二叉树（AVL树）：严格平衡的尝试

### 1. **核心改进**

- **新增约束**：任意节点的**左右子树高度差 ≤ 1**（平衡因子 ∈ {-1, 0, 1}）。
- **平衡机制**：插入/删除后通过**旋转操作**（LL/RR/LR/RL）局部修复失衡。

### 2. **优势与代价**

- **优势**：
  - ；
  - 
- **致命代价**：
  - 
  - **高更新成本**：在**高频插入/删除场景**（如实时系统）中性能显著下降。

> **关键转折**：AVL 树的严格平衡导致**动态操作成本过高**，需一种**更宽松的平衡策略**——红黑树应运而生。

------

## 五、红黑树：工程最优解的诞生

### 1. **设计哲学**

- **核心目标**：在**平衡性**与**动态操作成本**间取得折衷。
- **关键突破**：用**颜色标记**替代严格高度约束，允许**近似平衡**（最长路径 ≤ 2 × 最短路径）。

### 2. **五大性质与作用**

- **性质1**：节点非红即黑 → **引入颜色标记机制**。
- **性质2**：根节点为黑色 → **统一基准**。
- **性质3**：空叶子节点（NIL）为黑色 → **简化边界条件**。
- **性质4**：**红色节点子节点必为黑色**（无连续红节点）→ **防止局部过度倾斜**。
- **性质5**：**任意节点到叶子的黑节点数相同**（黑高一致）→ **保证全局近似平衡**。

### 3. **为何能解决前代问题？**

- **避免退化**：性质4+5 **确保最长路径 ≤ 2 × 最短路径**，树高上限为 $2 \log_2(n+1)$。
- **降低调整成本**：
  - 
  - **默认插入红色节点**：仅可能违反性质4（连续红节点），修复比违反黑高一致（性质5）更简单。

### 4. **核心优势总结**

- **动态操作友好**：在**高频插入/删除场景**（如系统调度、内存管理）中性能显著优于 AVL 树。
- **工程实用性**：
  - 仅需 **1 位存储颜色**，空间开销极小；
  - 旋转逻辑**局部性强**，易于实现并发控制（如 Linux 内核的无锁调度）。

------

## 六、演进逻辑的终极总结

1. **树 → 二叉树**：通过限制子节点数量简化结构，但**未解决查询效率问题**。
2. 
3. **BST → AVL 树**：通过**严格高度平衡**解决退化，但**动态操作成本过高**。
4. **AVL 树 → 红黑树**：用**颜色约束替代高度约束**，以**可控的平衡损失**换取**显著降低的调整成本**，成为**工业级动态数据管理的事实标准**。

> **关键结论**：红黑树并非“更高级”的 AVL 树，而是针对**不同场景的权衡选择**——  
>
> - **读多写少**（如静态词典）：选 AVL 树；  
> - **写操作频繁**（如实时系统）：**红黑树是工程最优解**，因其在  与 **低动态调整成本** 间取得了最佳平衡。





# 第 6 天：ConcurrentHashMap 源码探针（1.8）
本日掌握：深入 JDK 1.8 ConcurrentHashMap 的核心设计，手写一个简化版的并发哈希表，理解 CAS、synchronized、sizeCtl 与多线程协助扩容  
覆盖原理点：4 (ConcurrentHashMap 1.7 vs 1.8)  
阶段：使用期

## 🎯 今日目标
- 能画出 ConcurrentHashMap 1.8 的数组+链表/红黑树结构，并解释与 HashMap 的根本不同。
- 能使用 CAS + synchronized 实现一个支持并发 put 的简化版 CHM。
- 能解释 `sizeCtl` 字段的多重含义（初始化门槛、扩容阈值、扩容线程数）。
- 能通过源码探针或日志观察多线程下协助扩容（helpTransfer）的行为。
- 能手写一个多线程安全计数，对比 `size()` 在 CHM 中的实现（baseCount + CounterCell）。

---

## 📝 练习1：基础用法——创建并观察 ConcurrentHashMap 行为（必做）

### 业务场景
我们需要一个线程安全的全局配置容器，多个线程可能同时读取和更新配置项。你决定使用 `ConcurrentHashMap` 而不是 `Hashtable`，并需要验证其并发性能优于 `Collections.synchronizedMap`。

### 你的任务
1. 创建一个 `ConcurrentHashMap<String, String>` 对象，用 10 个线程并发写入 10000 条数据（key 为 “key” + i）。
2. 同样用 `Collections.synchronizedMap(new HashMap<>())` 做相同操作，记录两个版本的写入耗时。
3. 验证所有 key 是否写入成功（`size()` 是否为 10000）。
4. 尝试在遍历 `ConcurrentHashMap` 的过程中修改它（如删除元素），观察迭代器是否抛出 `ConcurrentModificationException`，并与 HashMap 比较。
5. 使用 `searchEntries` 或 `forEach` 等方法进行并行查找，体验 JDK 8 提供的函数式操作。

### ⚡ 关键提示
- `ConcurrentHashMap` 的迭代器是 **弱一致性** 的，它遍历的是某个时间点的快照或部分视图，可能不会反映最新的修改，也**不会抛出 ConcurrentModificationException**。这与 HashMap 的 fail-fast 不同。
- 进行并发写测试时，使用 `CountDownLatch` 或 `CyclicBarrier` 同时起跑，避免线程启动耗时偏差。
- 使用 `System.nanoTime()` 计时。
- 静态方法 `ConcurrentHashMap.newKeySet()` 可以创建一个并发集合，但今天我们聚焦 Map。

### ✍️ 动手写代码
```java
ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();
// 启动10个线程，每个线程插入1000条
CountDownLatch latch = new CountDownLatch(10);
long start = System.nanoTime();
for (int t = 0; t < 10; t++) {
    final int threadId = t;
    new Thread(() -> {
        for (int i = 0; i < 1000; i++) {
            map.put("key" + threadId + "_" + i, "val");
        }
        latch.countDown();
    }).start();
}
latch.await();
long end = System.nanoTime();
System.out.println("CHM time: " + (end - start) / 1_000_000 + "ms, size=" + map.size());
```

### ✅ 自我检查
- [ ] CHM 和 synchronizedMap 的写入耗时是否有明显差异？
- [ ] 遍历 CHM 时删除元素，迭代器是否还继续正常？
- [ ] 多个线程同时 `get` 和 `put` 时，会不会出现读到脏数据或 null 的情况？（通常不会，但需要理解弱一致性的语义）
- [ ] `size()` 返回的值是否精确？它是如何做到的？

### 📖 参考实现（直接展示）

```java
import java.util.*;
import java.util.concurrent.*;

public class CHMBasicTest {
    public static void main(String[] args) throws InterruptedException {
        // 准备两个Map
        ConcurrentHashMap<String, String> chm = new ConcurrentHashMap<>();
        Map<String, String> syncMap = Collections.synchronizedMap(new HashMap<>());

        // 测试写入性能
        long chmTime = concurrentWrite(chm, 10, 1000);
        long syncTime = concurrentWrite(syncMap, 10, 1000);
        System.out.println("ConcurrentHashMap time: " + chmTime + "ms, size=" + chm.size());
        System.out.println("SynchronizedMap time: " + syncTime + "ms, size=" + syncMap.size());

        // 测试弱一致性迭代
        System.out.println("\n--- 弱一致性迭代测试 ---");
        chm.clear();
        for (int i = 0; i < 5; i++) chm.put("key" + i, "val");
        for (String key : chm.keySet()) {
            if (key.equals("key2")) {
                chm.put("key_new", "new_val"); // 迭代过程中修改
                chm.remove("key3");
            }
            System.out.println("Iterating: " + key);
        }
        System.out.println("After iteration: " + chm.keySet());
        // 与 HashMap 对比：HashMap 会抛 ConcurrentModificationException
        // 这里不会抛异常，且可能遍历到或遍历不到新插入的 key_new
    }

    private static long concurrentWrite(Map<String, String> map, int threads, int perThread)
            throws InterruptedException {
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(threads);
        Thread[] workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            final int t = i;
            workers[i] = new Thread(() -> {
                try { startSignal.await(); } catch (InterruptedException ignored) {}
                for (int j = 0; j < perThread; j++) {
                    map.put("t" + t + "_k" + j, "v");
                }
                doneSignal.countDown();
            });
            workers[i].start();
        }
        long start = System.currentTimeMillis();
        startSignal.countDown();
        doneSignal.await();
        return System.currentTimeMillis() - start;
    }
}
```

**设计思路**  
- `concurrentWrite` 里通过 `CountDownLatch` 保证所有线程同时开始，准确测试并发写入性能。  
- 弱一致性迭代演示了 CHM 允许在遍历过程中修改，不会抛出异常，这是牺牲强一致性换取高并发。  
- 你可能会发现输出中 `key_new` 有时出现有时不出现，这就是弱一致性的体现。

### 🐞 常见错误预警
- **误以为 CHM 所有操作都是无锁的**：实际上 `put` 在发生桶冲突时会用 `synchronized` 锁住桶头节点。所以无锁不代表零锁。
- **误以为 CHM 是完全强一致的**：`get` 可能读不到刚刚 `put` 的值，因为 `get` 不加锁。这需要根据业务判断是否可接受。
- **混淆 `size()` 的实现**：它并非直接返回 `size` 字段，而是基于分段计数器，因此较慢。不要频繁调用。

---

## 📝 练习2：中级用法——手写简易并发哈希表（CAS + synchronized）

### 业务场景
为了真正掌握 CHM 的内部原理，你需要在不使用 `ConcurrentHashMap` 的情况下，自己写一个支持并发的 `MiniConcurrentHashMap`，要求准确使用 CAS 来无锁化读和部分写，并处理扩容时的并发迁移。

### 你的任务
实现 `MiniConcurrentHashMap<K,V>`：
- 使用 `volatile Node<K,V>[] table` 保证数组引用的可见性。
- `Node` 包含 hash, key, value, next。value 和 next 都用 `volatile` 修饰。
- `put` 操作流程：
  1. 计算 hash，若 table 未初始化，则 CAS 初始化数组（借鉴 `sizeCtl` 思路，用一个 `volatile int sizeCtl` 控制，-1 代表正在初始化）。
  2. 若桶为空，直接用 `Unsafe.compareAndSwapObject`（或 `AtomicReferenceArray`）尝试将新节点设为桶头。失败则循环重试。
  3. 若桶不为空且头节点的 hash 不等于 MOVED（表示正在扩容），则 `synchronized(head)` 锁住该桶，进行链表插入（或覆盖），同 HashMap。
  4. 简化版可先不处理树化和扩容。
- `get` 操作不加锁，直接读 volatile 字段。
- 加入简化的 `sizeCtl` 机制：初始化时 CAS 将其设为 -1；扩容阈值设为 `table.length * 0.75`，当元素数超过阈值时触发 `resize`（可只保留框架，迁移用单线程）。

### ⚡ 关键提示
- 使用 `VarHandle` 或 `Unsafe` 进行 CAS 操作，但为了简化，可以用 `AtomicReferenceArray<Node<K,V>>` 存储桶，它的 `compareAndSet` 可直接使用。
- `synchronized(head)` 是 JDK 1.8 CHM 的真实做法，注意 synchronized 持有的是桶的第一个节点。
- 必须确保 `table` 的 **volatile** 修改对所有线程立即可见。
- 初始化过程要避免多个线程同时初始化，可以采用 `sizeCtl` 的竞态标记。

### ✍️ 动手写代码
```java
public class MiniConcurrentHashMap<K, V> {
    static class Node<K, V> {
        final int hash;
        final K key;
        volatile V value;
        volatile Node<K, V> next;
        Node(int hash, K key, V value, Node<K, V> next) {
            this.hash = hash; this.key = key; this.value = value; this.next = next;
        }
    }
    private volatile Node<K, V>[] table;
    private volatile int sizeCtl;
    // ... initTable, put, get
}
```

### ✅ 自我检查
- [ ] 多个线程同时 `put` 不同 key 时，桶头为 null 的桶能否通过 CAS 成功竞争而不阻塞？
- [ ] 桶头不为 null 时，`synchronized` 锁住头节点，其它线程是等待还是能操作其他桶？
- [ ] `get` 方法能否在 `put` 尚未释放锁时读到部分修改？(这是弱一致性的来源)
- [ ] 初始化时 `sizeCtl` 是否成功阻止多个线程初始化，且只初始化一次？

### 📖 参考实现（直接展示）

```java
import java.util.concurrent.atomic.AtomicReferenceArray;

public class MiniConcurrentHashMap<K, V> {
    private static final int DEFAULT_CAPACITY = 16;
    private static final float LOAD_FACTOR = 0.75f;
    private volatile AtomicReferenceArray<Node<K,V>> table;
    private volatile int sizeCtl;

    static class Node<K, V> {
        final int hash;
        final K key;
        volatile V value;
        volatile Node<K, V> next;
        Node(int h, K k, V v, Node<K,V> n) { hash=h; key=k; value=v; next=n; }
    }

    public MiniConcurrentHashMap() {
        sizeCtl = DEFAULT_CAPACITY;
    }

    private final AtomicReferenceArray<Node<K,V>> initTable() {
        AtomicReferenceArray<Node<K,V>> tab;
        while ((tab = table) == null) {
            if (sizeCtl < 0) { // 有其他线程在初始化
                Thread.yield();
                continue;
            }
            // CAS 抢初始化权
            if (U.compareAndSwapInt(this, SIZECTL, sizeCtl, -1)) {
                try {
                    if ((tab = table) == null) {
                        tab = new AtomicReferenceArray<>(DEFAULT_CAPACITY);
                        table = tab;
                        sizeCtl = (int)(DEFAULT_CAPACITY * LOAD_FACTOR); // 阈值
                    }
                    return tab;
                } finally {
                    // 不需要复原，sizeCtl 已变成阈值
                }
            }
        }
        return tab;
    }

    public V put(K key, V value) {
        int hash = key.hashCode();
        AtomicReferenceArray<Node<K,V>> tab = table;
        if (tab == null) tab = initTable();
        int n = tab.length();
        int index = hash & (n - 1);
        
        while (true) {
            Node<K,V> f = tab.get(index);
            if (f == null) {
                Node<K,V> newNode = new Node<>(hash, key, value, null);
                if (tab.compareAndSet(index, null, newNode)) {
                    // 成功插入，待处理计数和扩容
                    break;
                }
                // 失败则重试
                continue;
            }
            // 桶不空，锁住头节点
            synchronized (f) {
                if (tab.get(index) == f) { // 检查头节点未变
                    // 查找是否存在相同key
                    Node<K,V> e = f;
                    boolean exists = false;
                    while (e != null) {
                        if (e.hash == hash && (e.key == key || key.equals(e.key))) {
                            e.value = value; // volatile 写
                            exists = true;
                            break;
                        }
                        e = e.next;
                    }
                    if (!exists) {
                        Node<K,V> tail = f;
                        while (tail.next != null) tail = tail.next;
                        tail.next = new Node<>(hash, key, value, null);
                    }
                    break;
                }
            }
        }
        // 简化：忽略扩容协助
        return null;
    }

    public V get(K key) {
        int hash = key.hashCode();
        AtomicReferenceArray<Node<K,V>> tab = table;
        if (tab == null) return null;
        int index = hash & (tab.length() - 1);
        Node<K,V> e = tab.get(index);
        while (e != null) {
            if (e.hash == hash && (e.key == key || key.equals(e.key)))
                return e.value;
            e = e.next;
        }
        return null;
    }

    // Unsafe 相关
    private static final sun.misc.Unsafe U;
    private static final long SIZECTL;
    static {
        try {
            java.lang.reflect.Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            U = (sun.misc.Unsafe) f.get(null);
            SIZECTL = U.objectFieldOffset(MiniConcurrentHashMap.class.getDeclaredField("sizeCtl"));
        } catch (Exception e) { throw new Error(e); }
    }
}
```

**设计思路**  
- 使用 `AtomicReferenceArray` 替代 `volatile Node[]` 以获得 CAS 支持，避免引入 `Unsafe` 操作数组元素。  
- 初始化时通过 `sizeCtl` 的 CAS 保证只有一个线程创建数组。  
- `put` 中桶为空用 CAS 原子设置，桶不空用 `synchronized` 锁头节点，保证同一个桶内串行。  
- `get` 无锁，依赖 `volatile` 保证可见性，所以可能读到较旧的值。  
- 这个版本缺少树的转换、扩容迁移、多线程协助，但这些作为原理篇的部分可留到第16天后。

### 🐞 常见错误预警
- **CAS 失败后忘记重试**：`put` 中如果 `compareAndSet` 返回 false，必须循环重新获取桶头，不能直接丢掉操作。
- **锁头节点后未检查头节点是否被其他线程修改**：在 `synchronized` 之前可能发生扩容移动了头节点，所以拿到锁后需再次判断 `tab.get(index) == f`。
- **`sizeCtl` 管理不当**：初始化后应设为阈值，而不是保持 -1。

---

## 📝 练习3：高级/探索用法——探究 sizeCtl 与协助扩容

### 业务场景
你需要在你的迷你版中加入简单的计数（模仿 `baseCount + CounterCell` 避免 CAS 竞争严重），并实现多线程协助扩容，即当线程发现 `size > threshold` 时，不自己完成整个扩容，而是将数据分块，每个线程领取一段进行迁移。

### 你的任务
1. 在 `MiniConcurrentHashMap` 中加入 `volatile int sizeCtl` 的扩容控制：当 `put` 后检查元素数超过阈值，调用 `transfer`。在 `transfer` 中，将 `sizeCtl` 设置为 `(n << 1) - (n >>> 1)` 这样的负值表示正在扩容，并记录线程数。
2. 实现简化的 `transfer`：步长 stride 为桶数 / CPU 核数。每个线程从尾部开始，通过 CAS 领取一段桶（`transferIndex`），然后对每个桶加锁迁移。
3. `helpTransfer`：其他线程 `put` 时若发现头节点的 hash 为特殊值比如 -1（代表 ForwardingNode），则加入协助扩容。
4. 测试：多个线程同时插入大量数据，观察扩容是否由多线程协作完成，可用打印语句证实。

### ⚡ 关键提示
- ForwardingNode 是一个特殊的占位节点，hash 设为 MOVED = -1。它表示该桶已完成迁移，查找时需要转发到新数组。
- `transferIndex` 用 `volatile` 控制，并用 CAS 让线程争抢处理区间。
- 协助扩容是 JDK 1.8 CHM 的最大亮点，有效利用 CPU 并行迁移。

### ✍️ 动手写代码
```java
// 在 MiniConcurrentHashMap 中增加 ForwardingNode、transfer 方法、helpTransfer 等
```

### ✅ 自我检查
- [ ] 插入过程中，使用 debug 打印转移日志，能看到多个线程参与 `transfer` 吗？
- [ ] 扩容完成后，所有数据在新表中都能通过 `get` 获取吗？
- [ ] 扩容期间的插入是否正确？（可先锁住整个表简化）

### 📖 参考实现（核心框架）
由于完整代码过长，这里只展示关键部分的设计模式：

```java
static final int MOVED = -1;
static class ForwardingNode<K,V> extends Node<K,V> {
    final AtomicReferenceArray<Node<K,V>> nextTable;
    ForwardingNode(AtomicReferenceArray<Node<K,V>> nt) {
        super(MOVED, null, null, null);
        nextTable = nt;
    }
}

private void transfer(AtomicReferenceArray<Node<K,V>> tab, AtomicReferenceArray<Node<K,V>> nextTab) {
    int stride = Math.max(tab.length() >> 3, 1); // 步长
    int n = tab.length();
    int i = n; // 从尾部开始
    boolean advance = true;
    while (i >= 0) {
        // CAS 争抢区间
        // ...
        synchronized (tab.get(i)) {
            // 迁移该桶
        }
    }
    table = nextTab;
    sizeCtl = (int)(n * 2 * 0.75);
}
```

**设计思路**  
- `ForwardingNode` 指向新数组，`get` 发现 hash == MOVED 就会去新表查找。  
- `transferIndex` 是全局共享的，通过 CAS 减少，每个线程抢到一个步长的桶进行迁移，并行加速。  
- 全部迁移完后，`nextTable` 晋升为 `table`。

---

## 🏢 大厂场景实战：实时黑名单过滤服务

### 场景描述
一个反欺诈系统需要维护一个**全局黑名单集合**，用于过滤交易请求。黑名单包含数百万个用户 ID，更新频繁（每秒可能有数十次增删），并要求读取性能极高（每次交易都要查询），同时需要支持范围查询（如拉黑时间超过7天的 ID 清理）。

### 约束条件
- 内存占用尽量低
- 查询 QPS 可达 50000+
- 写入 QPS 约 50
- 需要定期清理过期黑名单条目

### 你的设计任务
基于今天学习的 `ConcurrentHashMap`，设计黑名单存储结构，并写出关键操作（添加、查询、定期清理）的伪代码。考虑是否需要在 CHM 基础上包装过期时间。

### 设计决策点
- CHM 本身无法自动过期，需要为此如何包装？
- 清理过期数据时，需要遍历整个 CHM，这会阻塞写吗？
- 是否需要结合 `DelayQueue` 或后台线程定期扫描？

### 常见方案参考及其取舍分析（直接展示）

**方案：CHM + 后台线程**  

```java
ConcurrentHashMap<String, Long> blacklist = new ConcurrentHashMap<>(); // ID -> 过期时间戳
// 添加: blacklist.put(userId, System.currentTimeMillis() + 7*86400000L);
// 查询: Long expire = blacklist.get(userId); if (expire != null && expire > now) return true; else if (expire != null) blacklist.remove(userId); return false;
// 后台线程：每隔一段时间遍历 entrySet，移除已过期的。遍历使用弱一致性迭代器，不影响并发写。
```
- **优点**：可以直接利用 CHM 的高并发读；惰性清除 + 定期清除保证内存不胀。  
- **缺点**：惰性删除可能导致已过期用户多活一小段时间；遍历时通过弱一致性，不影响写入性能。  
- **另一种方案**：使用 `Caffeine` 或 `Cache2k` 等专业缓存，但本题旨在于理解 CHM 基础上扩展。  
- **扩展**：如果还需要按加入时间排序，可在 CHM 外维护一个 `ConcurrentSkipListMap<Long, String>`（时间->ID），方便范围取出最早过期的。

---

## 🏆 大厂面试题

### 面试题1：ConcurrentHashMap 1.7 和 1.8 在实现上最大的区别是什么？
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：
- **核心区别**：JDK 1.7 使用 **分段锁（Segment）**，继承 `ReentrantLock`，每段锁住一定范围的桶。JDK 1.8 放弃了分段锁，改为 **CAS + synchronized** 直接锁住桶的第一个节点。
- **工作流程**：
  - 1.7：整个哈希表被分成多个 Segment，每个 Segment 是一个小的 HashMap，并发度等于 Segment 数量（默认 16）。put 时先经过一次哈希找到 Segment，再在该 Segment 内部加锁进行第二次哈希。
  - 1.8：引入红黑树和协助扩容。put 时通过 CAS 尝试插入桶头，若桶已存在则 synchronized 锁住头节点进行链表/树操作。扩容时多线程协作迁移，效率大幅提升。
- **性能与内存对比**：1.8 在并发度上更灵活（不再是固定的段数），锁粒度更细，且 tree bin 避免了碰撞攻击。内存上 1.8 省去了 Segment 的开销，但增加了 ForwardingNode 和 TreeNode。
- **常见追问**：“为什么 1.8 不用分段锁而用 synchronized？”  
  → JDK 1.6 后 synchronized 大幅优化（偏向锁、轻量锁、重量锁），性能与 Lock 接近，且更简洁。且 CAS 失败后只能 synchronized 一个桶，开销可控。分段锁在低并发时反而会浪费内存和增加复杂度。
- **易错提醒**：有些人认为 1.8 之后完全没有锁，其实 synchronized 依旧存在，只是用在了桶级别。
- **自我反思**：能否画出 1.7 和 1.8 两次哈希的流程对比？是否理解 1.7 的并发度是如何限制最大并发线程数的？

---

### 面试题2：ConcurrentHashMap 的 size() 方法是如何实现线程安全的？
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：
- **核心概念**：CHM 的 size() 并不维护一个全局的计数变量，因为多个线程同时修改时 CAS 竞争激烈。它使用**分段计数**：`baseCount`（无竞争时 CAS 更新）+ `CounterCell[]` 数组（竞争时分散计数）。
- **工作流程**：
  - 增加计数：`addCount` 首先 CAS 更新 `baseCount`，如果失败说明有竞争，则随机选择一个 CounterCell，对其 `value` 做 CAS 加法。
  - 获取总数：`size()`（实际上是 `mappingCount()` 返回 long）对 `baseCount` 和所有 CounterCell 求和。
- **关键点**：`CounterCell` 通过 `@sun.misc.Contended` 避免伪共享。扩容时也会更新计数，避免死锁。
- **常见追问**：“为什么不用 `AtomicLong`？” → 因为高并发下大量 CAS 自旋消耗 CPU。分散热点到多个 Cell 后，单个 CAS 竞争减少，类似 `LongAdder`。
- **易错提醒**：`size()` 返回值是 int（最大 Integer.MAX_VALUE），若超过要用 `mappingCount()` 返回 long。
- **自我反思**：这个模式其实与 `LongAdder` 同源。能解释 `LongAdder` 和 `AtomicLong` 的区别吗？

---

### 面试题3：ConcurrentHashMap 的 get 方法为什么不需要加锁？
**难度**：⭐️⭐️⭐️

**参考答案**：
- **核心原因**：Node 的 `val` 和 `next` 字段都声明为 `volatile`，保证了内存可见性。数组引用 `table` 也是 `volatile`，任何线程都能看到最新的数组。因此读操作可以无锁进行，但可能读到的是稍旧的值（弱一致性）。
- **特殊情况**：当发生扩容时，桶可能被替换为 `ForwardingNode`，其 `find` 方法会去新数组查找，所以读也不会阻塞。
- **可见性保证**：`put` 中的 volatile 写 happens-before `get` 中的 volatile 读，因此至少能保证对同一个 key 的修改最终可见。
- **常见追问**：“get 是否会读到一半的修改？” → 不会，因为 volatile 保证了引用的原子性。即使是红黑树节点，查找期间可能进行平衡，但由于 TreeNode 的 `left`、`right` 也是 volatile，读取不会出现断裂指针。
- **易错提醒**：`get` 虽然不加锁，但不能保证读到的一定是最新值（另一个线程刚 put，可能因为 volatile 写未刷新到主存），所以是弱一致性的。
- **自我反思**：为什么 `HashMap` 的 `get` 可能出现死循环（Java 7 并发下），而 `CHM` 不会？因为 CHM 的写有锁和 CAS 保护，不会形成环。

---

### 面试题4：什么情况下 ConcurrentHashMap 会出现扩容？多线程如何协助扩容？
**难度**：⭐️⭐️⭐️⭐️⭐️

**参考答案**：

- **触发条件**：当 `put` 后发现 size 超过阈值（`sizeCtl > 0` 时为阈值），或者链表长度 ≥8 且数组长度 < 64 时（优先扩容而非树化）。
- **多线程协助**：
  1. 第一个触发扩容的线程会通过 CAS 将 `sizeCtl` 设为负值，表示正在扩容，并创建一个新数组（大小为原2倍）。
  2. 该线程从原数组尾部向前逐步迁移桶，使用 `transferIndex` 全局变量和 CAS 让每个线程领取迁移区间（stride）。
  3. 其他线程在 `put` 或 `get` 时，若发现某个桶已经是 `ForwardingNode`（hash==MOVED），说明正在扩容，它们会调用 `helpTransfer` 加入迁移工作。
  4. 迁移完成的桶被标记为 ForwardingNode，指向新数组。
- **关键点**：迁移过程中，写操作仍然可以进行：若目标桶未迁移，则锁住桶后插入；若已迁移，则在 newTable 上插入。这保证了扩容期间服务不阻塞。
- **常见追问**：“如果迁移过程中来了新的 put 请求且正好命中未迁移的桶，是否需要等待？” → 需要，因为该桶被迁移线程锁住，put 线程会在 `synchronized` 处阻塞，但迁移很快完成，阻塞时间短。
- **易错提醒**：协助扩容不是必选项，但在高并发插入时可以极大加速，把多线程的压力转化为迁移动力。
- **自我反思**：如果由你来设计，你会如何避免迁移过程中所有线程都试图抢同一个区间导致的 CAS 竞争？JDK 的 `transferIndex` 是如何做到比较平均的？

---

### 面试题5：ConcurrentHashMap 和 Hashtable 性能差距体现在哪些地方？
**难度**：⭐️⭐️⭐️

**参考答案**：

- **锁粒度**：Hashtable 锁住整个表（在方法上加 `synchronized`），任何操作都互斥。ConcurrentHashMap 1.8 锁住单个桶，读取无锁，并发度高达桶数。
- **迭代**：Hashtable 的迭代器是 fail-fast 的（要求整个迭代期间不能修改），且全程锁表。CHM 是弱一致性的，迭代不需要锁，也不会抛出 `ConcurrentModificationException`。
- **扩容**：Hashtable 扩容时，所有读写都阻塞，单线程迁移。CHM 多线程协助扩容，写操作仍可进行。
- **细节实现**：Hashtable 不允许 null 键值，CHM 也不允许（因为它使用 null 作为特殊标记）。Hashtable 继承 Dictionary 类，较老；CHM 实现 `ConcurrentMap` 接口，提供丰富原子方法（`computeIfAbsent` 等）。
- **常见追问**：“`Collections.synchronizedMap` 和 Hashtable 哪个更快？” → 差不多，都是全表锁。但前者可以包装任何 Map。
- **易错提醒**：即使 CHM 性能高，但它的 `size()` 是近似值吗？不是，它也是精确值（只是计算开销比普通 HashMap 稍高）。
- **自我反思**：在只需要一次性的批量加载，之后只读的场景，用 `Collections.unmodifiableMap` 比 CHM 更省内存，你能想到吗？

---

> 今天你将 CAS 和 synchronized 完美融合，真正触摸到了工业级并发的艺术。明天我们将更进一步，用 JOL 观察 synchronized 的锁升级过程，亲眼看到对象头的变化。这是理解 JVM 层面并发的关键一步！







# 第 7 天：ArrayList 源码攻防与扩容微基准
本日掌握：深入 JDK ArrayList 源码，掌握扩容、modCount、迭代器陷阱，并通过微基准测试证明扩容策略的性能影响  
覆盖原理点：5 (ArrayList 扩容与 fail-fast), 29 (自动装箱与拆箱缓存陷阱)  
阶段：使用期

## 🎯 今日目标
- 能逐行分析 `ArrayList.grow()` 的扩容细节，包括 1.5 倍算法、数组越界、溢出处理。
- 能复现并解决 `toArray()` 类型转换异常、`subList` 导致的 fail-fast、`removeIf` 的并发修改感知。
- 能运用 JMH 或简单基准测试，量化初始容量指定对性能的影响，并给出生产环境建议。
- 能识别自动装箱/拆箱在集合中的 **性能陷阱** 和 **NPE 陷阱**，并给出优化方案。

---

## 📝 练习1：基础用法——ArrayList 源码常见攻防点（必做）

### 业务场景
在 CRUD 中，你经常用 `ArrayList` 但很少关注其“脾气”。今天我们从源码角度揪出五个常见故障点：扩容不可见性、`toArray` 陷阱、`remove` 的索引偏移、`subList` 内存泄漏、`sort` 与 `Comparator` 不一致。

### 你的任务
针对每个点，编写测试代码触发故障，然后修复。
1. **扩容时机陷阱**：构造一个初始容量为 2 的 `ArrayList`，连续插入 3 个元素，然后在另一个线程（若单线程则无需）观察其内部数组长度（通过反射获取 `elementData.length`）证明扩容后的长度。验证 `grow` 使用 `oldCapacity + (oldCapacity >> 1)` 即 1.5 倍。
2. **toArray 类型转换异常**：`Object[] toArray()` 返回 `Object[]`，不能强转为 `String[]`；而要使用 `toArray(new String[0])`。演示强转抛出的 `ClassCastException`。
3. **remove 索引偏移**：列表 `[A, B, C, D]`，想要删除所有长度大于1的元素，如果使用普通的索引遍历并直接 `remove(i)`，会漏检。给出正确的 **倒序删除** 或 **迭代器 `remove`** 方法。
4. **subList 的 fail-fast 和内存泄漏**：通过 `subList` 获取一个视图，然后修改原 `ArrayList`，对子列表的任何操作都会抛 `ConcurrentModificationException`；另外，即使原列表不再被引用，只要子列表还被引用，原列表的 `elementData` 不会 GC。请演示此行为。
5. **sort 使用自定义比较器不满足传递性**：演示一个比较器返回不一致的结果（如根据条件返回 1、-1、0 但不符合自反性），导致排序后顺序诡异甚至抛出异常。

### ⚡ 关键提示
- 反射获取 `elementData` 时，需要 `setAccessible(true)`，并处理 `NoSuchFieldException`。
- `toArray(T[] a)` 当 a 长度小于 size 时，会分配一个新数组返回；否则将元素填充到 a 中，超过尺寸的地方设为 null。
- 倒序删除：`for (int i = list.size() - 1; i >= 0; i--) { if(...) list.remove(i); }`
- `subList` 只是原列表的一个视图，其 `modCount` 检查机制会阻止并发修改，并且它持有原列表引用。
- Comparator 约定：`sgn(compare(x,y)) == -sgn(compare(y,x))`，且传递性：若 `compare(x,y)>0 && compare(y,z)>0` 则 `compare(x,z)>0`。

### ✍️ 动手写代码
```java
// 1. 观察扩容
ArrayList<Integer> list = new ArrayList<>(2);
// 反射查看 elementData.length ...

// 2. toArray 陷阱
ArrayList<String> strList = new ArrayList<>();
strList.add("a");
Object[] arr = strList.toArray();
// String[] strs = (String[]) arr; // 异常
String[] correct = strList.toArray(new String[0]);

// 3. 删除陷阱
// 错误：
for (int i = 0; i < strList.size(); i++) { if (strList.get(i).length() > 1) strList.remove(i); }
// 正确：倒序或迭代器
```

### ✅ 自我检查
- [ ] 扩容后容量是否为 2 + 2/2 = 3？
- [ ] `toArray(new String[0])` 是否不抛出异常？
- [ ] 使用倒序删除，所有目标元素是否被删除？
- [ ] 修改原列表后，调用 `subList.get(0)` 是否抛 CME？
- [ ] 不满足传递性的 Comparator 是否可能导致 `IllegalArgumentException`？

### 📖 参考实现（直接展示）

```java
import java.lang.reflect.Field;
import java.util.*;

public class ArrayListDefense {
    public static void main(String[] args) throws Exception {
        // 1. 扩容观察
        ArrayList<Integer> list = new ArrayList<>(2);
        list.add(1); list.add(2);
        printCapacity(list); // 2
        list.add(3); // 触发扩容
        printCapacity(list); // 3 (2 + 2/2 = 3)

        // 2. toArray 类型转换
        ArrayList<String> strs = new ArrayList<>(Arrays.asList("x", "y"));
        // String[] wrong = (String[]) strs.toArray(); // ClassCastException
        String[] right = strs.toArray(new String[0]);
        System.out.println("toArray: " + Arrays.toString(right));

        // 3. 删除陷阱
        ArrayList<String> list2 = new ArrayList<>(Arrays.asList("A", "BB", "C", "DD"));
        for (int i = list2.size() - 1; i >= 0; i--) {
            if (list2.get(i).length() > 1) list2.remove(i);
        }
        System.out.println("After remove: " + list2); // [A, C]

        // 4. subList 陷阱
        ArrayList<String> parent = new ArrayList<>(Arrays.asList("a", "b", "c", "d"));
        List<String> sub = parent.subList(1, 3); // [b, c]
        parent.add("e"); // 结构性修改
        try {
            System.out.println(sub.get(0)); // 触发 CME
        } catch (ConcurrentModificationException e) {
            System.out.println("subList CME caught");
        }

        // 5. sort 比较器陷阱
        ArrayList<Integer> nums = new ArrayList<>(Arrays.asList(1, 2, 3));
        Comparator<Integer> badCmp = (a, b) -> {
            if (a == 1) return 1;  // 1 > 所有
            if (b == 1) return -1;
            return 0;
        };
        try {
            nums.sort(badCmp); // 可能抛出 IllegalArgumentException
        } catch (IllegalArgumentException e) {
            System.out.println("Comparator contract violation: " + e.getMessage());
        }
    }

    private static void printCapacity(ArrayList<?> list) throws Exception {
        Field f = ArrayList.class.getDeclaredField("elementData");
        f.setAccessible(true);
        Object[] data = (Object[]) f.get(list);
        System.out.println("容量: " + data.length);
    }
}
```

**设计思路**  
- 使用反射检查 `elementData.length` 直观证明扩容倍数。  
- `toArray(T[])` 利用零长度数组传入类型信息，可获得类型安全的数组，无需强制转型。  
- 列表删除时倒序避免了正序删除导致的索引前移和漏检。  
- `subList` 内存泄漏是因为 `SubList` 内部持有父列表的 `elementData` 引用，即使父列表不再使用，只要子列表存在，数组就不会被回收。  
- 排序比较器必须满足传递性，否则 JDK 排序算法（TimSort）可能会检测到并抛出 `IllegalArgumentException`。

### 🐞 常见错误预警
- `list.remove(int)` 和 `list.remove(Object)` 重载混淆：如果列表是 `Integer`，`remove(1)` 可能是按下标删除，需要显式调用 `remove(Integer.valueOf(1))` 来删除对象。
- `Arrays.asList()` 返回的列表大小固定，不支持 `remove` 和 `add`。
- 修改通过 `subList` 获得的视图会同步修改原列表，但修改原列表会使得子列表失效。

---

## 📝 练习2：中级用法——fail-fast 深度攻防（modCount 与迭代器）

### 业务场景
多步业务逻辑中，你可能会在增强 for 循环内修改列表，或者在 `removeIf` 中悄然打破 fail-fast。你需要彻底理解 fail-fast 的边界并安全驾驭它。

### 你的任务
1. 证明普通 for-each 编译后使用迭代器，所以在循环内调用 `list.remove` 会抛 `ConcurrentModificationException`，而调用 `iterator.remove` 则不会。
2. 探索 `ArrayList.removeIf` 的内部实现：它使用 `Iterator` 且 `remove` 会触发 `modCount++`，但为什么 `removeIf` 不会抛 CME？写个例子说明。
3. 演示 `ListIterator` 可以安全地添加元素（`add` 方法）而不会破坏迭代。
4. 复现多线程下 fail-fast 导致的非确定性 CME。用两个线程，一个遍历一个删除，多次运行观察概率。

### ⚡ 关键提示
- for-each 是语法糖，反编译后就是 `Iterator` + while 循环。
- `removeIf` 内部调用 `iterator.remove()`，并同步更新 `modCount`，所以能安全删除。
- `ListIterator.add` 也是在迭代过程中安全插入。
- 多线程 fail-fast 只需一个线程在遍历（持有迭代器），另一个线程修改结构（add/remove），就可能导致迭代器检测到 `modCount` 变化，因其非线程安全。

### ✍️ 动手写代码
```java
// 1. for-each 中 remove 触发 CME
List<String> list = new ArrayList<>(Arrays.asList("a", "b", "c"));
for (String s : list) {
    if (s.equals("b")) list.remove(s); // CME
}

// 2. removeIf 无 CME
list.removeIf(s -> s.equals("b"));

// 3. ListIterator 安全添加
ListIterator<String> it = list.listIterator();
it.add("x"); // 直接插入到迭代器游标之前

// 4. 多线程
Thread writer = new Thread(() -> {
    for (int i = 0; i < 100; i++) {
        list.add("x");
        sleep(1);
    }
});
Thread reader = new Thread(() -> {
    for (String s : list) {
        sleep(1);
    }
});
```

### ✅ 自我检查
- [ ] for-each 删除是否稳定抛出 CME？
- [ ] `removeIf` 后列表元素是否被正确删除，且无异常？
- [ ] `ListIterator.add` 后列表的改动是否反映在了迭代器后续遍历中？
- [ ] 多线程下，是否偶尔抛出 CME？为什么不是每次都抛？

### 📖 参考实现（直接展示）

```java
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class FailFastDeep {
    public static void main(String[] args) throws InterruptedException {
        // 1. for-each CME
        List<String> list = new ArrayList<>(Arrays.asList("a", "b", "c"));
        try {
            for (String s : list) {
                if (s.equals("b")) list.remove(s);
            }
        } catch (ConcurrentModificationException e) {
            System.out.println("for-each CME caught");
        }

        // 2. removeIf 安全
        List<String> list2 = new ArrayList<>(Arrays.asList("a", "b", "c"));
        list2.removeIf(s -> s.equals("b"));
        System.out.println("removeIf result: " + list2); // [a, c]

        // 3. ListIterator 安全添加
        List<String> list3 = new ArrayList<>(Arrays.asList("x", "y", "z"));
        ListIterator<String> lit = list3.listIterator();
        while (lit.hasNext()) {
            String s = lit.next();
            if (s.equals("y")) lit.add("INSERTED");
        }
        System.out.println("After ListIterator add: " + list3); // [x, y, INSERTED, z]

        // 4. 多线程 fail-fast
        List<String> shared = new ArrayList<>();
        for (int i = 0; i < 1000; i++) shared.add("item" + i);
        AtomicInteger cmes = new AtomicInteger(0);
        for (int trial = 0; trial < 100; trial++) {
            List<String> copy = new ArrayList<>(shared);
            Thread writer = new Thread(() -> {
                for (int i = 0; i < 100; i++) copy.add("x");
            });
            Thread reader = new Thread(() -> {
                try {
                    for (String s : copy) { Thread.sleep(1); }
                } catch (ConcurrentModificationException e) {
                    cmes.incrementAndGet();
                } catch (InterruptedException ignored) {}
            });
            writer.start(); reader.start();
            writer.join(); reader.join();
        }
        System.out.println("多线程 CME 发生次数: " + cmes.get() + "/100");
    }
}
```

**设计思路**  
- for-each 无法直接修改列表，`removeIf` 则内部使用 `Iterator` 并且是在同一个线程中执行，因此不会并发修改。  
- `ListIterator` 的 `add` 方法会在当前游标位置插入，迭代器继续遍历不会遗漏。  
- 多线程下，ArrayList 完全没有同步，任何一致性控制都缺失，因此 fail-fast 检测是尽力而为的，并不保证一定会抛异常，但大概率会。  
- 从原理上，`modCount` 只是一个版本号，迭代器在 `next()` 时检查，如果两条线程交替执行，可能恰好在检查后修改，从而不触发异常，但依然可能引发索引错误。

### 🐞 常见错误预警
- `removeIf` 如果在 lambda 中抛异常，可能导致列表处于中间状态，但由于 fail-fast 只在迭代器操作时检查，异常后列表可能不一致。
- 多线程环境使用 `Collections.synchronizedList` 包装，但包装后的迭代器仍需手动同步，否则仍会 CME。

---

## 📝 练习3：高级/探索用法——扩容微基准与自动装箱性能陷阱

### 业务场景
生产环境接口响应慢了，排查发现大量 `ArrayList` 在不断扩容，且存储的是 `Long` 类型，涉及频繁装箱/拆箱。你将通过基准测试量化这两者的开销，并给出优化方案。

### 你的任务
1. **扩容基准测试**：编写一个测试，分别用默认构造 和 预分配正确容量（比如已知要插入 100 万个元素）的 ArrayList，统计其 `add` 过程耗时，并打印扩容次数（通过继承或反射监听 `grow` 调用）。
2. **自动装箱开销测试**：对比 `ArrayList<Long>` 与手写 long 数组（或使用 `TLongArrayList` 若你有），向两者各插入 1000 万个随机 `long` 值，记录耗时和内存占用（可通过 Runtime 粗略计算）。
3. **缓存陷阱再探**：演示 `Integer` 在 -128~127 外的装箱会导致每次新建对象，将大量不同的 `Integer` 值放入 ArrayList，使用 `jmap` 或 `jstat` 观察堆中 `Integer` 对象的个数。

### ⚡ 关键提示
- 用 `System.nanoTime()` 计时多线程或单线程累加，确保 JIT 预热（可先执行几遍忽略）。
- 重写 `ArrayList` 的 `grow` 方法来计数扩容次数。
- `ArrayList<Long>` 存储的是 `Long` 对象，每次 `add(long)` 都会自动装箱成 `Long`，产生大量临时对象，影响 GC。
- 使用 `-XX:AutoBoxCacheMax=2000` 可扩大缓存范围，但不适合所有情况，且只是减少部分新建。
- 内存测量可用 `Runtime.getRuntime().totalMemory()` 和 `freeMemory()` 之差。

### ✍️ 动手写代码
```java
// 1. 继承 ArrayList 打点扩容次数
class MonitoredArrayList extends ArrayList<Object> {
    int growCount = 0;
    @Override
    protected void grow(int minCapacity) {
        super.grow(minCapacity);
        growCount++;
    }
}
// 2. 测试
MonitoredArrayList list = new MonitoredArrayList();
long start = System.nanoTime();
for (int i = 0; i < 1_000_000; i++) list.add(i);
long end = System.nanoTime();
System.out.println("默认容量，耗时: " + (end-start)/1e6 + "ms, 扩容次数: " + list.growCount);

// 预分配容量
list = new MonitoredArrayList(1_000_000);
// ...
```

### ✅ 自我检查
- [ ] 默认容量下扩容次数是否约为 `log2(1000000/10)` 次左右？
- [ ] 预分配容量后的耗时对比默认构造是否有数量级提升？
- [ ] `ArrayList<Long>` 对比 `long[]` 在插入速度和内存上是否有显著劣势？
- [ ] 当值在缓存内 vs 缓存外，堆中对象数是否明显不同？

### 📖 参考实现（直接展示）

```java
import java.util.*;
import java.util.stream.IntStream;

public class ArrayListMicroBench {
    // 可监听扩容的 ArrayList
    static class MonitoredArrayList<E> extends ArrayList<E> {
        int growCount = 0;
        public MonitoredArrayList() { super(); }
        public MonitoredArrayList(int cap) { super(cap); }
        @Override
        protected Object[] grow(int minCapacity) {
            growCount++;
            return super.grow(minCapacity);
        }
    }

    public static void main(String[] args) {
        // 预热
        IntStream.range(0, 10000).forEach(i -> {});
        // 测试1：扩容开销
        for (int size : new int[]{10_000, 100_000, 1_000_000}) {
            MonitoredArrayList<Object> defaultList = new MonitoredArrayList<>();
            long t1 = System.nanoTime();
            for (int i = 0; i < size; i++) defaultList.add(null);
            long t2 = System.nanoTime();
            System.out.printf("默认容量插入 %,d: %7.2f ms, 扩容 %d 次%n",
                    size, (t2-t1)/1e6, defaultList.growCount);

            MonitoredArrayList<Object> sizedList = new MonitoredArrayList<>(size);
            long t3 = System.nanoTime();
            for (int i = 0; i < size; i++) sizedList.add(null);
            long t4 = System.nanoTime();
            System.out.printf("预分配容量插入 %,d: %7.2f ms, 扩容 %d 次%n",
                    size, (t4-t3)/1e6, sizedList.growCount);
        }

        // 测试2：装箱开销
        long startMem = usedMemory();
        long startTime = System.nanoTime();
        ArrayList<Long> boxed = new ArrayList<>(1_000_000);
        for (long i = 0; i < 1_000_000; i++) boxed.add(i);
        long endTime = System.nanoTime();
        long endMem = usedMemory();
        System.out.printf("ArrayList<Long> 插入100万: %7.2f ms, 内存增加: %d bytes%n",
                (endTime-startTime)/1e6, (endMem-startMem));

        // 对比原生 long 数组
        startMem = usedMemory();
        startTime = System.nanoTime();
        long[] primitive = new long[1_000_000];
        for (long i = 0; i < 1_000_000; i++) primitive[(int)i] = i;
        endTime = System.nanoTime();
        endMem = usedMemory();
        System.out.printf("long[] 插入100万: %7.2f ms, 内存增加: %d bytes%n",
                (endTime-startTime)/1e6, (endMem-startMem));
    }

    static long usedMemory() {
        Runtime r = Runtime.getRuntime();
        return r.totalMemory() - r.freeMemory();
    }
}
```

**设计思路**  
- 通过继承重写 `grow` 方法，可以统计扩容次数。注意 JDK 9+ `grow` 方法签名是 `protected Object[] grow(int minCapacity)`。  
- 预分配容量避免了多次数组复制，时间差距可达数倍。  
- `ArrayList<Long>` 相比基本类型数组，有对象头、引用等开销，内存约三倍以上，且每次 `add` 都会自动装箱生成新的对象。  
- 通过 `usedMemory()` 可粗略观察堆内存变化，但不够精确，生产推荐 JMH。

### 🐞 常见错误预警
- `grow` 方法在 JDK 8 中名为 `private void grow(int minCapacity)`，无法直接覆写，需通过反射或使用更高版本。练习中假设 Java 8+ 环境。
- 使用 `System.nanoTime()` 计时需要预热与排除 GC 干扰，简单测试结果波动大，只能体现趋势。
- 自动装箱引发的 NPE：如果泛型方法返回 null，而拆箱赋值给基本类型，会抛出 `NullPointerException`。

---

## 🏢 大厂场景实战：批量导入时的内存与性能优化

### 场景描述
一个数据中台服务，需要从 CSV 文件批量导入 500 万条用户数据到内存中，进行格式转换后批量入库。当前实现：一行行读取，`List<UserDTO>` 不断 `add`，导致频繁扩容和大量 Full GC。

### 约束条件
- 文件大小约 1 GB
- 服务器堆内存 4 GB，但需要给其他业务留空间
- 解析速度要求 30 秒内完成
- DTO 对象字段较多（约 20 个）

### 你的设计任务
提出优化方案，至少包含：
- 如何避免 ArrayList 频繁扩容？
- 如何降低对象创建数量和内存占用？
- 是否可以分批处理，而不必一次性装载所有？

### 设计决策点
- 如果已知行数，如何精确分配 ArrayList 容量？
- 如果使用分批处理，每批多大合适？如何控制 GC？
- 是否可以使用池化对象或复用对象以减少创建？有什么风险？

### 常见方案参考及其取舍分析（直接展示）

**方案A：一次性装载 + 精确预分配**  
- 先快速扫描文件行数（如使用 `LineNumberReader`），然后 `new ArrayList<>(lineCount)`。  
- 优点：简单，避免扩容。  
- 缺点：仍需将全部 500 万 DTO 对象保留在堆中，内存占用可能超过 2 GB，有 OOM 风险。

**方案B：分批处理 + 流式操作**  

- 设置批大小 10000，使用 `BufferedReader` 逐行读取，攒满一批就进行转换并入库，然后 `batch.clear()`。  
- 优点：内存占用恒定（每批对象用完即可 GC 或复用），不会 OOM。  
- 缺点：需要处理批间事务边界。

**方案C：对象复用池**  
- 维护一个 DTO 对象池（如 `ObjectPool`），每行解析时从池中获取空对象，填充后加入批次，入库后重置并归还。  
- 优点：几乎零对象分配，停顿极小。  
- 缺点：实现复杂，需要注意字段清零、线程安全。

**推荐**：对于一般导入服务，**方案B** 是最佳平衡，简单可靠且显著降低 GC 压力。

---

## 🏆 大厂面试题

### 面试题1：请详细描述 ArrayList 的扩容机制，并说明为什么扩容因子是 1.5 而不是 1.5 倍以上？
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：

- **扩容机制**：当 `add` 导致 `size` 超过当前 `elementData.length` 时，调用 `grow(minCapacity)`。新容量 = `oldCapacity + (oldCapacity >> 1)` 即旧容量的 1.5 倍，再与 minCapacity 比较取最大值。若新容量超过 `MAX_ARRAY_SIZE` (Integer.MAX_VALUE - 8) 则进行大容量处理。
- **为什么是 1.5**：
  1. **时间空间权衡**：扩容倍增时，若倍数太大（如 2 倍），可能浪费大量空间；太小（如 1.1 倍）则需要频繁扩容，复制开销大。1.5 倍是实测较好的折衷。
  2. **避免内存碎片**：倍增且为 2 的幂时，可能导致频繁的 old+new 恰好超过老年代剩余连续空间，引发 GC。
  3. **历史原因**：早期 Vector 扩容是 2 倍（可指定增量），ArrayList 采用 1.5 以达到更优的内存利用率。
- **关键代码**：`int newCapacity = oldCapacity + (oldCapacity >> 1);`
- **常见追问**：“如果我要插入大量数据，如何优化？” → 使用 `ensureCapacity(int minCapacity)` 或构造时指定足够大的初始容量，减少扩容次数。
- **易错提醒**：扩容时调用 `Arrays.copyOf`，底层 `System.arraycopy` 是浅拷贝，只复制引用，不复制对象本身。
- **自我反思**：能否手写一个自动扩容数组，并说明 1.5 倍扩容在遇到极小初始容量（如 0）时的兜底处理？

---

### 面试题2：ArrayList 的 fail-fast 是如何实现的？什么情况下会失效？
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：
- **实现**：`ArrayList` 内部维护 `modCount` 字段，每次结构修改（add/remove/trimToSize等）都会自增。迭代器创建时保存 `expectedModCount = modCount`，在 `next()`、`remove()` 等方法执行前检查两者是否相等，不相等就抛出 `ConcurrentModificationException`。
- **失效情况**：
  1. 单线程中，使用迭代器遍历，但在循环体中使用 `list.remove()` 等修改，会触发 CME。
  2. 多线程中，一个线程遍历，另一线程修改，fail-fast 是尽力而为的，可能由于竞态条件未检测到，导致 CME 不抛但数据错乱。
  3. 使用 `subList` 时，修改原列表会导致子列表迭代器失效。
  4. 如果修改发生在 `checkForComodification()` 与 `next()` 之间的空窗，可能漏检。
- **常见追问**：“为什么 JDK 不采用更安全的并发修改检测？” → 因为 fail-fast 只是设计来帮助尽早发现并发 bug，并不能保证正确性和检测所有情况，这是弱一致语义。对并发场景，应使用同步或并发集合。
- **易错提醒**：很多人以为 fail-fast 是线程安全的机制，不是；它不以正确性为代价，仅用于调试。
- **自我反思**：如果让你实现一个线程安全且不会抛出 CME 的 ArrayList 迭代器，你会怎么做？可能需要 MVCC 或快照迭代器。

---

### 面试题3：`toArray()` 和 `toArray(T[] a)` 的区别是什么？为什么 `(String[]) list.toArray()` 会抛异常？
**难度**：⭐️⭐️⭐️

**参考答案**：
- **区别**：
  - `Object[] toArray()` 返回 `Object[]` 类型，不能强制转为具体类型数组（如 `String[]`），因为数组的实际运行时类型是 `Object[]`，不是 `String[]`，强制转换会抛 `ClassCastException`。
  - `<T> T[] toArray(T[] a)` 利用传入的类型参数，返回具体类型的数组。若传入数组长度足够，就填充到该数组中；不足则创建一个同类型的新数组返回。
- **用法**：建议使用 `list.toArray(new String[0])` 或预先分配大小的 `new String[list.size()]`。
- **常见追问**：“`toArray(new String[0])` 和 `toArray(new String[list.size()])` 哪个更好？”  
  → 现代 JVM 优化下，零长度数组开辟更快，且 `toArray` 内部会判断传入数组长度不够时会自己分配新数组，所以 `new String[0]` 更简洁且性能不差。
- **易错提醒**：误以为 `toArray()` 返回的是原始数组引用，从而修改返回数组会影响原列表。实则返回的是新数组，修改不影响原列表。
- **自我反思**：若你需要将列表转为 `int[]`，有什么高效方法？只能循环拆箱，需注意拆箱 NPE。

---

### 面试题4：ArrayList 和 LinkedList 的随机访问和插入删除性能有何差异？从内存层面分析原因
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：
- **随机访问**：ArrayList 基于数组，`get(index)` 直接通过基地址偏移 O(1)，且 CPU 缓存行局部性好。LinkedList 需要双向遍历 O(n)，节点分散在堆内存中，缓存局部性差。
- **插入/删除**：
  - 在列表头部或尾部：ArrayList 尾插 O(1) 均摊（扩容除外），头插 O(n) 需移动所有元素；LinkedList 头尾均为 O(1)。
  - 在中间：ArrayList 需要 `System.arraycopy` 移动元素，O(n)；LinkedList 只需修改指针 O(1)，但定位到该位置需要 O(n) 遍历。所以实际差别并非绝对。
- **内存层面**：
  - ArrayList 存储引用数组，一个 `int[]` 基础类型则连续内存；`LinkedList` 每个节点要额外存储前后指针（约 24 字节/节点），且对象不连续，浪费内存且对 GC 不友好。
  - 当元素数量较多或遍历频繁时，ArrayList 更高效；频繁在中间插入删除，LinkedList 优势才显现，但需遍历优势才成立。
- **常见追问**：“为什么 Java 很少用 LinkedList？” → 多数场景下 ArrayList 综合性能更优，且 CPU 缓存命中和内存占用优势巨大。很多开发者误以为 LinkedList 适合中间插入，却忽略了定位开销。
- **易错提醒**：测试插入删除时，如果都是在头部，LinkedList 确实快，但实际业务中往往需要先用索引找到位置。
- **自我反思**：有没有一种数据结构兼具数组的随机访问和链表的快速插入？ArrayDeque 在两端操作高效，中间不行。

---

### 面试题5：在 ArrayList 中存储大量 Integer，如何避免自动装箱导致的内存和性能问题？
**难度**：⭐️⭐️⭐️

**参考答案**：
- **问题**：`ArrayList<Integer>` 存储的每个元素都是 `Integer` 对象，每个至少占 16 字节（对象头+int+对齐），比基本类型 4 字节多出数倍。且每次 `add(int)` 都会 `Integer.valueOf`，产生大量对象，加重 GC 频率和停顿。
- **解决方案**：
  1. 使用专门的基本类型集合库，如 `Trove4j` 的 `TIntArrayList` 或 `fastutil` 的 `IntArrayList`，内部使用 `int[]` 存储，无装箱。
  2. 如果必须用对象集合，可尝试利用 `Integer` 缓存（-128~127 可调大），减少小数值的创建，但数值一多依然无效。
  3. 使用数组 `int[]` 然后手动封装（如 `Arrays.asList` 只能转 `List<int[]>` 不可行），需用 `IntStream` 产生装箱列表。
  4. 针对大量稀疏或范围整数，考虑使用 `IntArrayList` 等，或自己写工具类批量转换。
- **常见追问**：“Java 将来会有值类型（ValueType）吗？” → 未来可能引入，这会消除对象头，性能大增。
- **易错提醒**：从 `ArrayList<Integer>` 中 `get` 赋值给 `int` 时，如果元素为 null，拆箱会 NPE。
- **自我反思**：你的项目中，是否有很多集合存储着不必要的包装类型？能否换成基础类型特化集合？

---

> 今天你深入 ArrayList 的每一行关键源码，并通过基准测试证明了“小优化累积成大性能”。明天你将进入 JVM 的核心领域，用 JOL 观察对象头在 synchronized 锁升级过程中的变化，亲眼见证偏向锁、轻量锁、重量锁的切换。





# 第 8 天：synchronized 锁升级实验（JOL 观察对象头）
本日掌握：使用 JOL 亲眼观察偏向锁、轻量级锁、重量级锁的对象头变化，理解锁升级全过程  
覆盖原理点：9 (synchronized 锁升级过程)  
阶段：使用期

## 🎯 今日目标
- 能用 JOL (Java Object Layout) 工具打印出对象在无锁、偏向锁、轻量锁、重量锁状态下的 Mark Word。
- 能编写一段代码准确触发锁升级的全过程，并用 JOL 验证每一步的状态。
- 能解释偏向锁延迟、偏向锁撤销的原因，以及 `-XX:BiasedLockingStartupDelay` 的作用。
- 能结合实际场景分析锁升级对性能的影响，并在面试中从容应对锁升级相关追问。

---

## 📝 练习1：基础用法——初识 JOL 与无锁 / 偏向锁状态观察（必做）

### 业务场景
你的团队在排查高并发接口性能时，发现大量 synchronized 竞争。你想用 JOL 直观查看对象锁状态，并发掘 JVM 默认开启偏向锁但延迟启动的特性。

### 你的任务
1. 在项目中引入 JOL 依赖（或使用 Maven/Gradle），打印一个普通 `Object` 的布局，观察 Mark Word 的前 64 位（或 32 位）的十六进制内容。
2. 分别在以下场景中打印对象头，记录锁状态位的特征：
   - 刚 `new` 出来的对象（无锁）
   - 在 `synchronized(obj)` 块内打印的对象（偏向锁或轻量锁？）
   - 主线程退出同步块后，再次打印该对象（偏向锁是否保持？）
3. 注意 JVM 默认偏向锁启动有 4 秒延迟（`-XX:BiasedLockingStartupDelay=4000`），所以刚启动就运行可能会看到轻量锁而非偏向锁。请用 `-XX:BiasedLockingStartupDelay=0` 关闭延迟来确保偏向锁立即生效。

### ⚡ 关键提示
- JOL 核心类：`org.openjdk.jol.info.ClassLayout`，调用 `ClassLayout.parseInstance(obj).toPrintable()` 得到布局。
- Mark Word 在 x64 下占据 8 个字节，末尾 3 位表示锁状态和是否偏向。各状态的二进制模式：
  - 无锁：`...001`（倒数三位 001），偏向标记为 0。
  - 偏向锁：`...101`（倒数三位 101），偏向标记为 1。
  - 轻量锁：`...00`（倒数两位 00），表示锁记录指针。
  - 重量锁：`...10`（倒数两位 10），表示 monitor 指针。
- 直接看十六进制不太直观，可配合 JOL 解析的 `ClassLayout` 打印的“MARKWORD”行，关注 `biased_lock` 和 `lock` 2 位的标记说明。

### ✍️ 动手写代码
```java
import org.openjdk.jol.info.ClassLayout;

public class JOLBasic {
    public static void main(String[] args) {
        Object obj = new Object();
        System.out.println("无锁状态:");
        System.out.println(ClassLayout.parseInstance(obj).toPrintable());

        synchronized (obj) {
            System.out.println("同步块内 (可能偏向/轻量):");
            System.out.println(ClassLayout.parseInstance(obj).toPrintable());
        }

        System.out.println("退出同步块后:");
        System.out.println(ClassLayout.parseInstance(obj).toPrintable());
    }
}
```

### ✅ 自我检查
- [ ] 无锁时，Mark Word 是否显示为 non-biasable 或 biasable？
- [ ] 加了 `-XX:BiasedLockingStartupDelay=0` 后，同步块内的 Mark Word 中 biased_lock 是否为 1？lock 是否为 101？
- [ ] 退出同步块后，偏向锁是否依然保留在对象头上（即偏向从未撤销）？
- [ ] 不设延迟启动时，初次 bias 可能看到轻量锁（biased_lock=0, lock=00），符合预期吗？

### 📖 参考实现（直接展示）

```java
import org.openjdk.jol.info.ClassLayout;

public class JOLBasicDemo {
    public static void main(String[] args) {
        // 最好通过 VM 参数 -XX:BiasedLockingStartupDelay=0 来关闭偏向延迟
        Object obj = new Object();
        System.out.println("1. 无锁状态：");
        System.out.println(ClassLayout.parseInstance(obj).toPrintable());

        synchronized (obj) {
            System.out.println("2. 进入同步块：");
            System.out.println(ClassLayout.parseInstance(obj).toPrintable());
        }

        System.out.println("3. 退出同步块：");
        System.out.println(ClassLayout.parseInstance(obj).toPrintable());

        // 观察 hash 调用对偏向的影响
        obj.hashCode();
        System.out.println("4. 调用 hashCode 后（偏向锁被撤销）：");
        System.out.println(ClassLayout.parseInstance(obj).toPrintable());
    }
}
```

**设计思路**  
- 先打印无锁状态，Mark Word 最末三位通常是 001，且偏向标记 0。  
- `-XX:BiasedLockingStartupDelay=0` 确保偏向模式立即启用，synchronized 块内可直接看到偏向锁（biased=1, lock=101），且会记录当前线程 ID。  
- 退出同步块后，偏向锁不撤销，对象头仍保留偏向标记和线程 ID。  
- 调用 `hashCode()` 会导致偏向锁撤销，因为偏向锁的 Mark Word 中没有空间存储哈希码，需要膨胀到轻量锁来腾出空间放置 hash。

### 🐞 常见错误预警
- 没有关偏向延迟，导致首次进入 synchronized 时直接是轻量锁，误以为偏向不生效。解决：启动参数加 `-XX:BiasedLockingStartupDelay=0`。
- 在 synchronized 块内打印布局时，如果块内调用了 `System.identityHashCode(obj)`，会立即撤销偏向，从而看到轻量锁。请避免在块内调用 `hashCode`。
- JOL 版本不同，输出格式略有差异，但关键行“biased_lock”和“lock”状态位不变。

---

## 📝 练习2：中级用法——手动触发锁升级全过程（偏向→轻量→重量）

### 业务场景
为了直观看到锁膨胀，你需要故意制造轻量锁竞争，并最终膨胀为重量锁。通过 JOL 记录下每个阶段的对象头变化。

### 你的任务
编写一段程序，演示如下过程：
1. 偏向锁阶段：主线程加锁一次，释放，确认偏向。
2. 轻量锁阶段：创建一个新线程，该线程尝试持有同一个对象的锁，由于指向不同线程，偏向锁会被撤销并升级为轻量级锁（通过 CAS 竞争锁记录）。用 JOL 打印此期间的对象头。
3. 重量锁阶段：让两个线程同时争抢锁，当 CAS 自旋一定次数失败后，轻量锁膨胀为重量锁。打印重量锁下的 Mark Word。
4. 特别：在轻量锁阶段调用对象的 `hashCode()`，观察到偏向锁膨胀为轻量锁以便存储 hash。

### ⚡ 关键提示
- 轻量锁竞争：一个线程持有锁，另一个线程尝试获取时，偏向会撤销，并会在线程栈上创建锁记录（Lock Record），Mark Word 中存储指向该记录的指针（最后两位 00）。
- 重量锁膨胀：当轻量锁竞争剧烈（如 CAS 失败多次），会通过 `inflate` 生成一个 ObjectMonitor，Mark Word 低两位 10，指针指向该 monitor。
- 可以用 `Thread.sleep` 和 `join` 来控制时序，确保打印时机准确。
- 调用 `obj.hashCode()` 在偏向锁状态下会导致膨胀，因为偏向的 Mark Word 中没有 hash 存储位。

### ✍️ 动手写代码
```java
import org.openjdk.jol.info.ClassLayout;

public class LockUpgrade {
    public static void main(String[] args) throws InterruptedException {
        Object obj = new Object();
        // 先由主线程偏向
        synchronized (obj) {
            System.out.println("=== 偏向锁 ===");
            System.out.println(ClassLayout.parseInstance(obj).toPrintable());
        }
        // 调用 hashCode 促使膨胀到轻量锁
        obj.hashCode();
        System.out.println("=== 偏向撤销 -> 轻量锁 (因hashCode) ===");
        System.out.println(ClassLayout.parseInstance(obj).toPrintable());

        // 现在制造重量锁竞争
        Thread t1 = new Thread(() -> {
            synchronized (obj) {
                System.out.println("=== T1 获得锁 (轻量) ===");
                System.out.println(ClassLayout.parseInstance(obj).toPrintable());
                try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            }
        });
        Thread t2 = new Thread(() -> {
            synchronized (obj) {
                System.out.println("=== T2 获得锁 (可能膨胀为重量) ===");
                System.out.println(ClassLayout.parseInstance(obj).toPrintable());
            }
        });
        t1.start();
        Thread.sleep(50); // 确保 t1 先拿到锁
        t2.start();
        t1.join(); t2.join();
    }
}
```

### ✅ 自我检查
- [ ] 在偏向锁阶段，是否能观察到高 54 位存有线程 ID，且 biased=1, lock=101？
- [ ] 调用 `hashCode` 后，偏向是否被撤销，对象头转换为不可偏向的无线程 ID，锁状态变为 001？
- [ ] 轻量锁竞争时，Mark Word 是否变为指向栈锁记录的指针，最后两位 00？
- [ ] 当 T2 等待 T1 释放锁时，锁是否膨胀为重量锁，Mark Word 指向 ObjectMonitor，最后两位 10？

### 📖 参考实现（直接展示）

```java
import org.openjdk.jol.info.ClassLayout;

public class LockUpgradeDemo {
    public static void main(String[] args) throws Exception {
        // 关闭偏向延迟，避免干扰
        Object obj = new Object();
        // 偏向
        synchronized (obj) {
            System.out.println("1. 主线程偏向锁");
            System.out.println(ClassLayout.parseInstance(obj).toPrintable());
        }
        // 通过 hashCode 撤销偏向
        System.out.println("hashCode: " + obj.hashCode());
        System.out.println("2. 调用hashCode后 -> 无锁 (不可偏向)");
        System.out.println(ClassLayout.parseInstance(obj).toPrintable());

        // 轻量锁
        synchronized (obj) {
            System.out.println("3. 轻量锁 (主线程 re-bias 失败，走轻量)");
            System.out.println(ClassLayout.parseInstance(obj).toPrintable());
        }

        // 重量锁竞争
        Thread t = new Thread(() -> {
            synchronized (obj) {
                System.out.println("4. 其他线程持有锁 (轻量)");
                System.out.println(ClassLayout.parseInstance(obj).toPrintable());
                // 让主线程也来抢
                new Thread(() -> {
                    synchronized (obj) {
                        System.out.println("5. 第二个竞争线程 -> 重量锁");
                        System.out.println(ClassLayout.parseInstance(obj).toPrintable());
                    }
                }).start();
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            }
        });
        t.start();
        t.join();
    }
}
```

**设计思路**  
- 先让主线程持有偏向锁并打印。  
- 调用 `hashCode` 迫使偏向锁撤销，因为偏向状态下没有空间存放哈希码，必须膨胀至无锁（不可偏向）状态。  
- 之后主线程再次 synchronize 无法偏向（因此时对象头哈希已经存在），只能走轻量锁流程。  
- 创建另一个线程竞争锁时，轻量锁可能膨胀为重量锁，可以通过 JOL 观察 pointer 变化。

### 🐞 常见错误预警
- 没有调用 `hashCode` 或 `notify/wait` 等，导致锁始终为偏向，无法观察到轻量锁。
- 线程调度不确定，可能 JOL 打印时锁刚好被释放，可适当增加 sleep 或使用 CyclicBarrier 同步。
- 注意 OpenJDK 15 以后偏向锁默认被禁用（`-XX:-UseBiasedLocking`），请确保在 Java 8/11 并开启偏向锁。

---

## 📝 练习3：高级/探索用法——偏向锁批量重偏向与撤销实验

### 业务场景
在一些长时间运行的服务中，JVM 会对偏向锁进行批量重偏向和撤销优化，以避免频繁的单次撤销带来的性能损耗。你将通过特定模式触发批量重偏向或批量撤销，并用 JOL 观察全局效果。

### 你的任务
1. 使用 `-XX:BiasedLockingBulkRebiasThreshold=10` 和 `-XX:BiasedLockingBulkRevokeThreshold=20` 等参数，设置较低阈值方便触发。
2. 模拟一个场景：多个线程对一系列对象交替加锁，触发偏向撤销，直到达到批量重偏向阈值，观察后续对象是否重新获得偏向。
3. 使用 `-XX:+TraceBiasedLocking`（Java 8 可用）或 `-Xlog:biasedlocking=trace`（Java 11+）来在 GC 日志中看到偏向锁操作。
4. 记录并解释：当撤销次数超过 `BiasedLockingBulkRevokeThreshold` 后，整个类或偏向会被永久禁用，不再尝试偏向。

### ⚡ 关键提示
- 偏向锁批量重偏向：当一个类的大量实例的偏向锁频繁撤销，JVM 会判断撤销次数超过阈值，便在安全点对该类的所有对象进行一次批量重偏向，以避免逐个撤销的开销。
- 批量撤销：若重偏向后仍然大量撤销，则彻底禁用该类的偏向能力。
- 这些参数在 JDK 8 中生效，JDK 15 往后偏向锁被移除，请使用 JDK 8 或 11 进行实验。
- 可以用循环创建多个对象，并在不同线程中同步，统计 JOL 打印的对象头偏向标记变化。

### ✍️ 动手写代码
```java
import org.openjdk.jol.info.ClassLayout;
import java.util.ArrayList;
import java.util.List;

public class BiasedBulk {
    public static void main(String[] args) throws Exception {
        // 设置 JVM 参数 -XX:BiasedLockingStartupDelay=0 -XX:BiasedLockingBulkRebiasThreshold=10 -XX:BiasedLockingBulkRevokeThreshold=20
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Object o = new Object();
            synchronized (o) {} // 给主线程偏向
            list.add(o);
        }
        System.out.println("初始偏向:");
        System.out.println(ClassLayout.parseInstance(list.get(0)).toPrintable());

        // 用另一个线程对这些对象进行同步，触发偏向撤销
        Thread revoker = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                synchronized (list.get(i)) {}
            }
        });
        revoker.start();
        revoker.join();

        System.out.println("撤销后 (可能批量重偏向或不可偏向):");
        System.out.println(ClassLayout.parseInstance(list.get(0)).toPrintable());
        System.out.println(ClassLayout.parseInstance(list.get(50)).toPrintable());
    }
}
```

### ✅ 自我检查
- [ ] 达到批量重偏向阈值后，新对象再次被主线程同步时，是否又出现了偏向？
- [ ] 日志中是否出现了 “Biased locking is not optimal and is being disabled” 等消息？
- [ ] JOL 显示的 Mark Word 最后三位是否从 101 变成 001（不可偏向）？

### 📖 参考实现与解释

参考代码运行后，你可以观察 JOL 输出：初始时所有对象都偏向了主线程。稍后另一个线程去同步它们，触发了偏向撤销。当撤销次数达到 `BiasedLockingBulkRevokeThreshold` 时，可能整个类被标记为不可偏向，之后的对象头将永远是 non-biasable。通过调整阈值参数，可以控制行为。

**设计思路**  
- 批量撤销和重偏向是 JVM 对偏向锁的一种全局优化，防止因个别实例的竞争导致整个偏向机制性能下降。  
- 此实验演示了偏向锁并非永久存在，在竞争激烈的类上会被直接禁用，从而省去不必要的撤销开销。  
- 理解这一点可以帮助你在面试中解释“为什么在高并发场景下偏向锁可能并不会生效”。

### 🐞 常见错误预警
- 忘记设置批量阈值参数 JVM 参数，使用默认值（阈值很高）导致实验难以触发。
- 在 Java 15+ 环境偏向锁已移除，无法实验。请使用 Java 8/11。

---

## 🏢 大厂场景实战：秒杀场景下的锁膨胀优化

### 场景描述
一个电商秒杀活动，大量线程同时争抢少量库存对象上的 synchronized 锁。一开始系统运行良好，随着流量增加，RT 突然飙升，CPU 消耗高。通过线程栈发现大量 BLOCKED 线程，怀疑锁升级导致性能骤降。

### 约束条件
- 单机库存扣减操作必须串行（用 synchronized 保护）
- 并发线程数可能达到数百
- 要求延时在 10ms 以内

### 你的设计任务
分析锁升级对性能的影响路径，并提出优化方案。至少考虑：
- 如何避免锁频繁膨胀成重量锁导致上下文切换？
- 是否可以控制锁竞争范围，减少重量锁带来的线程阻塞？
- 轻量锁的自旋是否是双刃剑？

### 设计决策点
- 如果系统预期就是高竞争，应该是否关闭偏向锁，减少撤销开销？
- 轻量锁自旋次数如何调整（`-XX:PreBlockSpin`）？
- 是否可以使用 JUC 下的 `ReentrantLock` 等显式锁来替代 synchronized，以获得更多控制？
- 能否通过更细粒度的分段锁来降低竞争？

### 常见方案参考及其取舍分析（直接展示）

**方案A：关闭偏向锁 + 适当自旋**  

- 设置 `-XX:-UseBiasedLocking` 避免无谓偏向和撤销，直接进入轻量锁竞争。  
- 调整自旋次数 `-XX:PreBlockSpin=20`（默认10），使其在膨胀前更努力地自旋，避免进入重量锁。  
- **优点**：简单，轻量锁在没有真正冲突时很快，减少阻塞。  
- **缺点**：自旋会消耗 CPU，如果大量线程长时间自旋无果，CPU 会爆满。

**方案B：使用 ReentrantLock（公平或非公平）**  
- ReentrantLock 支持 tryLock、定时获取，且底层基于 AQS，可以自定义阻塞策略。  
- **优点**：控制更灵活，可以设置等待超时，避免无限阻塞；可中断。  
- **缺点**：需要显式释放，代码复杂。

**方案C：细粒度锁（分段）**  
- 将库存分到多个桶，每个桶用不同的锁对象保护，如 `ConcurrentHashMap` 的锁粒度。  
- **优点**：显著降低竞争，提高并发度。  
- **缺点**：库存总数需要额外聚合计算。

**推荐**：秒杀场景下竞争必然激烈，推荐**方案C（分段锁）** + **方案A（关闭偏向）**。分段极大减少竞争，关闭偏向避免额外开销。

---

## 🏆 大厂面试题

### 面试题1：请描述 synchronized 的锁升级过程，从无锁到重量锁的状态转换机制。
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：

- **核心概念**：synchronized 在 HotSpot 中经历了偏向锁 → 轻量级锁 → 重量级锁的升级过程，这是 JVM 对锁的优化，用于在无竞争或轻度竞争时避免系统调用开销。
- **升级过程**：
  1. 初始对象处于无锁状态（Mark Word 低 3 位 001，偏向标记 0）。
  2. 当第一个线程进入同步块时，JVM 尝试将对象头偏向该线程，在 Mark Word 中存入线程 ID（低 3 位变为 101）。
  3. 当另一个线程尝试获取这个已偏向的锁时，偏向会被撤销，升级为轻量级锁。撤销过程需要在安全点进行，检查原偏向线程是否还持有锁。轻量级锁通过 CAS 在对象头设置指向栈中锁记录的指针，低 2 位变为 00。
  4. 如果轻量级锁竞争激烈（自旋 CAS 失败次数过多），锁会膨胀为重量级锁。JVM 会创建一个 ObjectMonitor，并将对象头的指针指向它，低 2 位变为 10。此时等待的线程会被挂起 (park)，避免空转。
- **关键点**：偏向锁是为了解决一个线程多次获取同一把锁的场景；轻量锁假设竞争不会太激烈，通过自旋避免挂起；重量锁则彻底交给操作系统调度。
- **常见追问**：“偏向锁撤销的开销大吗？” 答：需要 Stop-The-World 安全点，如果频繁撤销会严重影响性能，这也是 JDK 15 以后默认关闭偏向锁的原因。
- **易错提醒**：很多人以为锁膨胀是单向的，重量锁永远不会降级为轻量锁。这是对的，一旦膨胀成重量锁，即使没有竞争也会保持重量锁，不会降级。
- **自我反思**：你能画出一张 Mark Word 在不同锁状态下的位布局图吗？特别是 32 位与 64 位的区别。

---

### 面试题2：偏向锁的延迟启动是什么意思？为什么要延迟？
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：
- **含义**：JVM 启动后，默认不会立即启用偏向锁，而是经过 4 秒（`-XX:BiasedLockingStartupDelay=4000`）后才开始对新创建的对象启用偏向模式。在延迟期间，所有 synchronized 直接走轻量级锁。
- **原因**：JVM 启动过程中会发生大量锁竞争（例如类加载、反射、IO 等），如果启用偏向锁，会引发频繁的偏向撤销和重偏向，带来额外开销。延迟启动可以跳过启动期的高竞争阶段，等系统相对稳定后，再对那些真正适合偏向的类生效。
- **影响**：短生命周期的应用可能永远享受不到偏向锁；可以通过 `-XX:BiasedLockingStartupDelay=0` 关闭延迟。
- **常见追问**：“怎么判断一个应用是否适合偏向锁？” → 若大量对象仅由单个线程操作（如线程局部缓存），且同步块短，适合偏向锁；反之多线程高竞争的应用则应禁用偏向锁。
- **易错提醒**：延迟期间创建的对象，即使后来线程同步，也不会成为偏向锁，因为它们的 Mark Word 已经置为不可偏向（001）。
- **自我反思**：用 JOL 观察一个启动后立即创建的对象和一个延迟后创建的对象，它们的 Mark Word 是否不同？延迟期间的对象是 non-biasable，延迟后是 biasable。

---

### 面试题3：轻量级锁 CAS 自旋失败多少次才会膨胀为重量锁？如何调整？
**难度**：⭐️⭐️⭐️

**参考答案**：
- **默认机制**：轻量级锁膨胀并没有固定的“失败次数”，而是基于自适应自旋 (Adaptive Spinning) 策略。JVM 根据之前该锁的自旋成功概率动态决定自旋次数，如果历史成功率高，会多自旋一会；如果低，会很快膨胀。
- **调整参数**：早期 JDK 版本有 `-XX:PreBlockSpin=10` 设置固定自旋次数，但现代 JVM 大多忽略该参数，转而使用自适应策略。可以通过 `-XX:+UseSpinning` 开启（默认），`-XX:PreBlockSpin` 作为参考。
- **另外**，轻量锁在如下情况也会膨胀：调用 `Object.wait/notify` 时，因为需要 monitor 支持等待队列；或者当前线程需要获取锁但发现对象头已指向栈锁记录，即产生竞争。
- **常见追问**：“自适应自旋有什么好处？” → 避免频繁的线程挂起和唤醒（开销大），在竞争短暂时自旋成功，提高性能；竞争长期时快速膨胀，减少 CPU 空转。
- **易错提醒**：自适应自旋是在多核机器上才有效，单核下会直接膨胀。
- **自我反思**：是否可以通过 `-XX:PreBlockSpin` 手动控制？一般建议保持默认，让 JVM 自行决定。

---

### 面试题4：synchronized 和 ReentrantLock 在锁升级机制上有何本质不同？
**难度**：⭐️⭐️⭐️⭐️⭐️

**参考答案**：
- **核心区别**：synchronized 的锁升级过程是 JVM 内建的、自动的、黑箱的；ReentrantLock 基于 AQS，通过改变同步状态（state）和 CLH 队列实现，没有偏向、轻量、重量这种硬件层面的锁升级，但逻辑上也有从乐观 CAS 到线程挂起的过程。
- **具体对比**：
  - synchronized 在无竞争时使用偏向锁/轻量锁，底层依赖 CAS 和对象头；ReentrantLock 一开始就通过 CAS 尝试设置 state，失败则立刻进入 AQS 队列（类似轻量直接膨胀？但实际比重量轻），并可能先自旋再 park。
  - ReentrantLock 的队列是显式的 Node 节点排队，可以中断、超时，而 synchronized 重量锁基于 ObjectMonitor 的内部等待队列，语义更单一。
  - 性能上，低竞争情况下两者接近；高竞争时 ReentrantLock 因可定时的好处，可能更可控；但 synchronized 的偏向/轻量在特定场景可能有优势。
- **常见追问**：“JDK 1.6 之后 synchronized 性能大幅优化，为什么还需要 ReentrantLock？” → 因为后者支持公平锁、条件变量 (Condition)、可中断、超时等高级特性，在复杂同步逻辑下仍是刚需。
- **易错提醒**：不少人误以为 ReentrantLock 完全没有“升级”，其实重入时只是 state 增加，没有偏向那种基于线程的开销。
- **自我反思**：能否用 JOL 观察 ReentrantLock 内部的状态变化？不行，因为它使用的是 AQS 的抽象状态，而不是对象头。

---

### 面试题5：在什么情况下偏向锁会被撤销？如何避免频繁撤销？
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：
- **撤销情况**：
  1. 另一个线程尝试获取已偏向的锁时，会触发偏向撤销，升级为轻量锁。
  2. 在偏向锁状态下调用对象的 `hashCode()` 或 `System.identityHashCode()`，因为偏向时对象头存的是线程 ID，没有空间存哈希，需要撤销膨胀。
  3. 调用 `notify` / `wait` 也会导致膨胀为重量锁。
  4. 达到批量撤销阈值后，整个类的偏向被永久禁用。
- **避免策略**：
  - 如果对象需要存储哈希码，且会频繁被其他线程访问，考虑不要在一个线程里长期持有偏向锁。
  - 可以通过 `-XX:-UseBiasedLocking` 直接关闭偏向锁，消除撤销开销。
  - 尽量减少在偏向锁期间调用 `hashCode`。
- **常见追问**：“为什么 `hashCode` 会影响偏向锁？” → 对象默认 hashCode 是计算后写入 Mark Word 的，偏向锁状态下 Mark Word 无可用空间，必须转为无锁（或轻量锁）才能存哈希。
- **易错提醒**：很多人会在循环中对同一个同步对象多次调用 `hashCode`，导致频繁膨胀。
- **自我反思**：如果业务代码中无法避免，是否应该考虑使用 `ReentrantLock`，避免偏向锁带来的不确定性？

---

> 今天你亲手将 JVM 的锁升级过程可视化，从 Mark Word 的二进制中读出了 JVM 的智慧。明天我们将深入 volatile 的内存屏障与 DCL 正确实现，继续夯实并发基础。





# 第 9 天：volatile 与 DCL 正确/错误版本对比
本日掌握：亲手验证 volatile 的可见性与禁止指令重排，写出正确的 DCL 单例，剖析 volatile 在其中的关键作用  
覆盖原理点：10 (volatile 可见性与禁止重排；DCL 中 volatile 作用)  
阶段：使用期

## 🎯 今日目标
- 能写出一个示例证明 volatile 变量的可见性，并用 `jstack` 或代码观察到非 volatile 下的“死循环”。
- 能正确实现双重检查锁定（DCL）单例，并解释为什么必须给 instance 加 volatile。
- 能说出 volatile 的内存屏障语义（LoadLoad、StoreStore、LoadStore、StoreLoad）及其在 DCL 中的位置。
- 能写出反例：不加 volatile 的 DCL 如何被指令重排破坏，导致返回未初始化完毕的对象。
- 能自信应对面试中关于 volatile 底层、Happens-Before、DCL、单例破坏方式的所有追问。

---

## 📝 练习1：基础用法——证明 volatile 的可见性（必做）

### 业务场景
有一个开关变量 `running`，主线程将其设为 false，但另一个线程始终跳不出循环。加 volatile 后恢复正常。我们编写一个 demo 来亲眼证实这一现象。

### 你的任务
1. 编写一个程序，共享一个 `boolean running = true`（非 volatile）。一个子线程不断检查 running，若为 false 则停止；主线程 sleep 1 秒后设置 running = false，并等待子线程结束。
2. 运行程序，观察子线程是否能在合理时间内退出（大概率不会）。
3. 将 `running` 加上 `volatile`，再运行，观察子线程立即退出。
4. （可选）使用 `jstack` 或 `jconsole` 查看线程堆栈，观察卡住的线程是否一直在 RUNNABLE。

### ⚡ 关键提示
- JIT 编译器会将非 volatile 变量的循环内判断优化为寄存器比较，不再从主存读取，导致即使主线程修改也看不见。`volatile` 保证了写直接刷新到主存，读每次都从主存取。
- 为了更明显，可在循环体内加空语句或仅仅 `if (!running) break;`。有时 `println` 内部有同步会意外刷新缓存，干扰实验，请避免。
- 必须在多线程下运行，单线程看不到效果。

### ✍️ 动手写代码
```java
public class VolatileVisibility {
    private /* volatile */ boolean running = true;

    public static void main(String[] args) throws InterruptedException {
        VolatileVisibility demo = new VolatileVisibility();
        Thread t = new Thread(() -> {
            while (demo.running) {
                // 空循环，不打印，不加锁
            }
            System.out.println("子线程退出");
        });
        t.start();
        Thread.sleep(1000);
        demo.running = false;
        System.out.println("主线程已将 running 设为 false");
        t.join(2000);
        if (t.isAlive()) {
            System.out.println("子线程未退出，可见性问题！");
        }
    }
}
```
**运行两次**：一次保留注释 `/* volatile */`，一次加上 `volatile`。

### ✅ 自我检查
- [ ] 不加 volatile 时，子线程是否一直循环无法退出？主线程打印“子线程未退出”？
- [ ] 加了 volatile 后，子线程是否在 1 秒后很快退出？
- [ ] 你能否画出 volatile 写和读之间的 happens-before 边？
- [ ] 你能解释 JIT 为什么会优化成死循环吗？

### 📖 参考实现（直接展示）

```java
public class VolatileDemo {
    // 初始为 true，不 volatile
    private boolean running = true;

    public static void main(String[] args) throws InterruptedException {
        VolatileDemo demo = new VolatileDemo();
        Thread worker = new Thread(() -> {
            int i = 0;
            while (demo.running) {
                i++; // 为了避免完全空循环被优化掉，可留一个操作，但不能有同步
            }
            System.out.println("线程结束, i=" + i);
        });
        worker.start();

        Thread.sleep(1000);
        System.out.println("主线程准备修改 running=false");
        demo.running = false;
        worker.join(3000);
        if (worker.isAlive()) {
            System.out.println("子线程仍然存活，发生可见性问题，running 未读到新值");
        } else {
            System.out.println("子线程正常退出");
        }
    }
}
```

**设计思路**  
- 使用了无同步的循环，线程可能将 running 缓存到 CPU 缓存或寄存器，看不到主内存的更改。  
- `volatile` 强制读写主存，并插入内存屏障保证可见性。  
- 为了确保 JIT 不消除循环，内部放一个递增变量 `i`，但不使用 `System.out.println`（它自带同步，会意外刷新缓存）。  
- 运行 `java -Xint VolatileDemo` 可以关闭 JIT 以观察差异，但这会导致性能下降，只用于实验。

### 🐞 常见错误预警
- 在循环体内打印 `System.out.println`，其 `synchronized` 会刷新缓存，导致可见性问题消失。请务必避免。
- 使用 `-Xint` 完全解释执行，此时每次都会从主存取，看不出问题。要用默认混合模式。
- 将 `running` 声明在方法内部或局部变量，则由于其逃逸分析可能根本不会被多线程感知。要使用**成员变量**。

---

## 📝 练习2：中级用法——双重检查锁定（DCL）的正确与错误版本（必做）

### 业务场景
单例模式中，为了保证多线程安全且懒加载，我们使用双重检查锁定。但经常写出有 bug 的版本，导致返回未初始化完成的对象。我们需要模拟错误版本、使用 volatile 修复，并解释原因。

### 你的任务
1. 编写一个**错误的** DCL 单例：`instance` 不是 volatile，但使用了 `if (instance == null)` + `synchronized` + 二次检查。提供一个 `getInstance()` 方法。
2. 编写**正确的** DCL 单例：`instance` 加上 `volatile`。
3. 创建多个线程并发获取单例，并让单例构造函数中做一些较重的初始化（比如填充一个数组），观察错误版本是否可能出现获取到未初始化完成的对象（目前很难直接复现，但可说明理论：构造函数可能被重排，`instance` 赋值可能发生在构造函数完成之前）。
4. 通过分析字节码或解释“对象半初始化”来证明 volatile 的作用：`instance = new Singleton()` 可分解为：分配内存 -> 调用构造 -> 赋值给引用。重排序后可能变成：分配内存 -> 赋值给引用 -> 调用构造。另一个线程此时读到非空引用，但构造未完成。

### ⚡ 关键提示
- DCL 必须使用 `volatile` 的原因是禁止指令重排序：volatile 写前的操作不会排到写之后，写之后的操作不会排到写之前。对于 `instance = new Singleton()`，内存屏障确保了对象构造完成后再将引用写回主存。
- 如果仍想复现错误，可以尝试：在构造方法中让线程 sleep 一段时间，模拟慢初始化，然后用两个线程几乎同时调用 `getInstance`，在没有 volatile 时，极低概率会出现拿到实例但字段未初始化，但概率极低。
- `synchronized` 保证了同步块内原子性和可见性，但 DCL 中第一次 null 检查在同步块外，没有 volatile 的话，另一个线程可能看到“非 null 但构造未完成”的引用。

### ✍️ 动手写代码
```java
// 错误版
class WrongDCL {
    private static WrongDCL instance; // 无 volatile
    private int[] data;

    private WrongDCL() {
        // 模拟耗时初始化
        data = new int[1000000];
        // 可能发生构造未完成，instance 已被赋值
    }

    public static WrongDCL getInstance() {
        if (instance == null) {                // 第一次检查
            synchronized (WrongDCL.class) {
                if (instance == null) {        // 第二次检查
                    instance = new WrongDCL(); // 危险：可能指令重排
                }
            }
        }
        return instance;
    }
}

// 正确版
class CorrectDCL {
    private static volatile CorrectDCL instance;
    private int[] data;

    private CorrectDCL() { data = new int[1000000]; }

    public static CorrectDCL getInstance() {
        if (instance == null) {
            synchronized (CorrectDCL.class) {
                if (instance == null) {
                    instance = new CorrectDCL();
                }
            }
        }
        return instance;
    }
}
```

### ✅ 自我检查
- [ ] 你能解释 DCL 中两次判空各有什么作用？（第一次提升性能，第二次保证单例）
- [ ] 如果去掉 volatile，对象创建流程的三步重排是什么？如何导致问题？
- [ ] `volatile` 在这里阻止了哪种重排序？插入的是什么内存屏障？
- [ ] 在 JDK 5 之前，volatile 不能保证这个，因此 DCL 失效，JDK 5+ 修复了 volatile 的语义。

### 📖 参考实现与字节码分析（直接展示）

```
// 伪字节码
0: new #2                  // 分配内存
3: dup
4: invokespecial #3        // 调用构造
7: putstatic #4            // 将引用赋值给 instance
```
如果 4 和 7 被重排，变成先 putstatic 再 invokespecial，其他线程就会拿到未初始化的对象。volatile 写时，会在 putstatic 之前加入 StoreStore 屏障，禁止前面的写（构造）与后面的写（volatile 写）重排；之后加入 StoreLoad 屏障，确保后续读能看见最新值。

### 🐞 常见错误预警
- 误以为 `synchronized` 能解决所有问题：它只保证了同步块内的互斥和可见性，但第一次检查在块外，读的是共享变量，没有 volatile 则读不到最新值或看到构造一半的对象。
- 使用 `final` 字段会有限制吗？如果单例的字段都是 final，那么构造完成后的对象对所有线程可见，但 DCL 问题在于 `instance` 引用本身可能被提前发布，而不是字段不可见。所以即使字段是 final，也需要 volatile 来阻止引用的提前赋值。

---

## 📝 练习3：高级/探索用法——内存屏障与防止 DCL 被反射/序列化破坏

### 你的任务
1. 在正确 DCL 的基础上，编写一个测试：通过**反射**获取单例类的私有构造器，创建新实例，破坏单例。然后通过**序列化/反序列化**破坏单例。讨论如何防御（enum 实现单例）。
2. 使用 `VarHandle` 或 `Unsafe` 来手动插入内存屏障（如 `loadFence()`, `storeFence()`），并观察其效果（高级，可作为思考）。
3. 分析为什么 `enum` 是实现单例的最佳方式：自带序列化安全、反射安全、线程安全。

### ⚡ 关键提示
- 反射防御：可以在构造函数中判断 instance 是否非 null，若已存在则抛出异常。
- 序列化防御：实现 `readResolve()` 方法返回已有实例。
- Enum 单例：`INSTANCE;` 既是常量，序列化只会序列化名称，反序列化时 `valueOf` 得到单例。

### ✍️ 动手写代码
```java
// 防御反射：构造中抛异常
private Singleton() {
    if (instance != null) {
        throw new RuntimeException("单例已存在");
    }
    // 初始化
}

// 防御序列化
private Object readResolve() {
    return instance;
}

// 或者使用枚举
public enum Singleton {
    INSTANCE;
    public void doSomething() {}
}
```

### 📖 参考实现（直接展示）

```java
import java.io.*;

public class DCLSingleton implements Serializable {
    private static volatile DCLSingleton instance;
    private DCLSingleton() {
        if (instance != null) throw new RuntimeException("单例已存在，反射攻击失败");
    }
    public static DCLSingleton getInstance() {
        if (instance == null) {
            synchronized (DCLSingleton.class) {
                if (instance == null) instance = new DCLSingleton();
            }
        }
        return instance;
    }
    // 保证序列化不破坏单例
    private Object readResolve() {
        return instance;
    }
}
```

---

## 🏢 大厂场景实战：高并发计数器

### 场景描述
实现一个全局计数器，多个线程对其递增，需要高性能且线程安全。讨论使用 `volatile` + `synchronized`、`AtomicInteger`、`LongAdder` 的优劣。

### 约束条件
- 写 QPS 极高（10 万+）
- 读 QPS 极高，读要求稍弱一致性
- 内存占用尽量小

### 你的设计任务
分析不同方案的利弊，给出最终实现。

### 常见方案参考
- `volatile` 不能保证原子性，需要配合锁或 CAS。
- `AtomicInteger`：CAS 自旋在极高竞争下 CPU 消耗大。
- `LongAdder`：热点分散，高吞吐，但 `sum` 操作有合并开销，读不是瞬时。适合高并发写。
- 业务要求弱一致读，`LongAdder` 最合适。

---

## 🏆 大厂面试题

### 面试题1：volatile 如何保证可见性和禁止指令重排？背后的内存屏障是什么？
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：
- **可见性**：volatile 变量的写会立即刷新到主内存，并使其他 CPU 缓存中的该变量缓存行失效（MESI 协议嗅探）。读时从主内存重新读取。这就实现了变量的可见性。
- **禁止重排**：JMM 在 volatile 写之前插入 StoreStore 屏障，禁止前面的普通写与 volatile 写重排；写之后插入 StoreLoad 屏障，防止后面的 volatile 读/写与当前写重排。volatile 读之后插入 LoadLoad 和 LoadStore 屏障。
- **关键点**：StoreLoad 屏障是最重的屏障，它能保证在其之前的所有写都对之后的读可见，具有全局性。这也是 DCL 中 volatile 起作用的核心。
- **常见追问**：“如果没有 volatile，单单 synchronized 也能保证可见性吗？” 答：能，因为 synchronized 的释放和获取之间建立了 happens-before，保证可见性，但 DCL 中第一次检查在同步块外，所以必须 volatile。
- **易错提醒**：volatile 不保证原子性，如 `i++` 不是线程安全的。
- **自我反思**：能否列举出 volatile 的三个适用场景：状态标记、DCL、独立观察（如并发容器中的 volatile 字段）。

---

### 面试题2：详细分析 DCL（双重检查锁定）中 volatile 的作用，以及不加 volatile 的问题
**难度**：⭐️⭐️⭐️⭐️⭐️

**参考答案**：
- **作用**：禁止 `instance = new Singleton()` 过程中指令重排序。对象创建分三步：分配内存、执行构造（初始化对象）、将引用赋值给 instance。若重排序为分配-赋值-构造，另一个线程可能在第一次检查 instance 非空，但对象尚未初始化完成，导致读取到未完成的对象。
- **不加 volatile 的后果**：另一个线程拿到一个半初始化对象，其字段为默认值，使用时会出错。
- **volatile 的屏障**：在 volatile 写 `instance = ref` 之前插入 StoreStore 屏障，确保构造完成再 volatile 写；之后插入 StoreLoad 屏障，保证后续读能见到最新值。
- **常见追问**：“如果在 `getInstance` 方法上加 synchronized 就能解决，为什么还要 DCL？” → 因为方法加锁性能差，DCL 只在第一次创建时加锁，后续读取无锁。
- **易错提醒**：有人以为 `instance` 的引用本身是原子更新的（是的），但指令重排可能先更新引用再执行构造，所以仅仅是引用原子性不足以保证安全。
- **自我反思**：如果面试官让你手写一个不需要 volatile 的 DCL（使用局部变量、Holder 模式等），你能给出几种替代方案？

---

### 面试题3：`happens-before` 规则中，volatile 写-读是如何建立关系的？
**难度**：⭐️⭐️⭐️

**参考答案**：
- **规则**：对一个 volatile 变量的写 happens-before 于后续对这个 volatile 变量的读。也就是说，如果线程 A 写 volatile 变量，线程 B 读同一个 volatile 变量，那么 A 写之前的所有操作对 B 读之后的所有操作都可见。
- **运用**：DCL 中，volatile 写 instance 后，其他线程读 instance 非空，就能取到构造完整的对象。并发容器中大量使用 volatile 保证状态变量可见。
- **关键点**：happens-before 具有传递性，结合其他规则可以建立更复杂的可见性链。
- **常见追问**：“两个 volatile 变量之间怎么保证顺序？” → 单个 volatile 变量的写-读具备同步语义，但多个 volatile 变量之间的整体顺序不保证，除非使用锁或原子类。
- **易错提醒**：误以为 volatile 读和普通读一样轻，其实它会在某些 CPU 上触发缓存一致性操作，成本高于普通读。
- **自我反思**：能否用 happens-before 解释 DCL 中第一次检查为什么可能不可见？因为没有 volatile，写 instance 和读 instance 之间没有 happens-before 关系。

---

### 面试题4：单例模式有哪些线程安全的实现方式？DCL、静态内部类、枚举各有什么优缺点
**难度**：⭐️⭐️⭐️

**参考答案**：
- **DCL + volatile**：懒加载，实例域延迟初始化，适合复杂初始化。缺点：需要小心实现，反射、序列化可能破坏单例。
- **静态内部类**：利用类加载机制的线程安全保证，`getInstance` 触发内部类加载，JVM 保证类初始化线程安全。懒加载，无需加锁，简洁，非常推荐。缺点：无法传递参数初始化。
- **枚举单例**：利用 JVM 对枚举常量的唯一性保证，绝对抗反射、序列化，代码最少。缺点：不能懒加载（类加载即创建），不可继承。
- **常见追问**：“为什么说枚举单例是最好的？” → 因为它提供了对线程安全、反射、序列化的天然保护，且写法极简。
- **易错提醒**：使用静态内部类时，如果构造函数抛异常，会导致 `NoClassDefFoundError` 且无法重试。
- **自我反思**：你能否根据场景选择合适的单例实现？比如需要带参数初始化，则选择 DCL；若无需参数，用枚举更安全。

---

### 面试题5：Java 中除了 volatile 和锁，还有哪些保证可见性的方式？
**难度**：⭐️⭐️⭐️

**参考答案**：

- **`final` 字段**：在构造函数中对 final 字段的赋值，构造函数结束后，对象引用赋给一个其他线程能访问的变量，那么其他线程能看到正确初始化的 final 字段，但要求 this 引用没有在构造函数中逸出。
- **`synchronized` 与 `Lock`**：锁的释放 happens-before 锁的获取。
- **`Atomic` 类**：如 `AtomicInteger` 的 `get` 和 `set` 具有 volatile 语义。
- **`Thread.start()` / `join()`**：线程启动前的所有操作 happens-before 线程启动后的操作；线程 join 后，被 join 线程的所有操作 happens-before 主线程后续操作。
- **并发工具类**：如 `CountDownLatch` 的 `countDown` 和 `await` 之间的 happens-before。
- **常见追问**：“`final` 字段如何保证可见性？” → JMM 规定，构造函数中对 final 域的写入，与构造函数结束后对象的引用赋值给其他线程之间，插入 StoreStore 屏障。
- **易错提醒**：this 在构造函数中逸出会破坏 final 的可见性保证，例如在构造函数中启动线程或发布对象。
- **自我反思**：你的项目中是否用到了这些可见性保证？还是在无感知地依赖锁？理解可见性有助于写出更高效的无锁代码。

---

> 今天你揭开了 volatile 的神秘面纱，并见证了 DCL 的致命陷阱。从此你的并发工具箱里多加了一把精细的可见性螺丝刀。明天我们将直面 AQS 框架的心脏，亲手实现一个 ReentrantLock！





# 第 10 天：AQS 自实现与 ReentrantLock 探针
本日掌握：手写一个简化版 AQS，实现公平/非公平 ReentrantLock 与 Condition，理解 CLH 队列、state 管理、独占与共享模式的本质  
覆盖原理点：11 (AQS 框架原理), 12 (ReentrantLock 与 Condition)  
阶段：使用期

## 🎯 今日目标
- 能手写一个支持公平/非公平的简易版 `MiniReentrantLock`，底层基于自己实现的 `MyAQS`。
- 能解释 AQS 的 `state` 语义，以及 CLH 队列变体如何实现线程阻塞与唤醒。
- 能实现 `Condition` 的 `await/signal`，并与 `synchronized` 的 `wait/notify` 做对比。
- 能画出锁获取、释放的流程，并分析公平锁与非公平锁在实现上的唯一区别。
- 能回答面试中关于 AQS 的所有核心追问，包括为什么它能支持共享模式（如 Semaphore）。

---

## 📝 练习1：基础用法——实现 CLH 队列与 CAS 状态管理（必做）

### 业务场景
我们要实现一个自己的锁，它不依赖 `synchronized`，而是像 `ReentrantLock` 那样基于一个同步状态和线程队列。先搭架子：定义 `MyAQS` 类，完成 CAS 更新 state、节点排队、阻塞/唤醒。

### 你的任务
1. 创建 `MyAQS` 抽象类，包含：
   - `volatile int state` 表示同步状态。
   - `compareAndSetState(int expect, int update)` 方法，使用 `Unsafe` 或 `AtomicIntegerFieldUpdater` 实现 CAS 更新 state。
   - 内部类 `Node`，包含线程引用、前置节点、后继节点、`waitStatus`（可选 CANCELLED/SIGNAL/CONDITION 等，今天先用 SIGNAL 和 0）。
   - 一个同步队列（双向链表），`head` 和 `tail`，使用 CAS 操作 `tail`。
   - 模板方法 `acquire(int arg)`：先尝试 `tryAcquire(arg)`，成功则返回；失败则创建 Node 入队，并在循环中尝试获取，可能需要阻塞。
   - `release(int arg)`：调用 `tryRelease(arg)`，成功则唤醒后继节点。
2. `MyAQS` 中需要提供 `park` 和 `unpark` 方法封装 `LockSupport`。

### ⚡ 关键提示
- 我们直接使用 `LockSupport.park()` 和 `unpark(thread)` 来做线程阻塞唤醒。
- 入队使用自旋 + CAS 设置 tail，并维护双向链表。需要注意多线程下的尾结点更新。
- `acquire` 中，节点入队后，只有前驱是 head 且 tryAcquire 成功才返回；否则考虑是否阻塞。
- `shouldParkAfterFailedAcquire` 用来设置前驱节点的 waitStatus 为 SIGNAL，表示它有责任唤醒自己。
- 先画出 CLH 队列图，再动手。

### ✍️ 动手写代码
```java
public abstract class MyAQS {
    private volatile int state;
    // 节点定义...
    // acquire, release, compareAndSetState
}
```

### ✅ 自我检查
- [ ] 能否用 CAS 原子地修改 state，并且 volatile 保证可见性？
- [ ] 两个线程同时尝试获取锁，一个成功，另一个是否成功加入队列并阻塞？
- [ ] 释放锁后，队列中的线程是否被唤醒并成功获取锁？
- [ ] `head` 和 `tail` 的初始化、移动是否符合 CLH 逻辑？

### 📖 参考实现（直接展示）

```java
import sun.misc.Unsafe;
import java.lang.reflect.Field;
import java.util.concurrent.locks.LockSupport;

public abstract class MyAQS {
    private volatile int state;
    private volatile Node head;
    private volatile Node tail;

    static final class Node {
        volatile Thread thread;
        volatile Node prev;
        volatile Node next;
        volatile int waitStatus; // 0 或 SIGNAL
        Node() {}
        Node(Thread t) { thread = t; }
        Node(Thread t, int ws) { thread = t; waitStatus = ws; }
    }

    protected MyAQS() { head = tail = new Node(); } // 哑节点

    protected boolean compareAndSetState(int expect, int update) {
        return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
    }

    protected int getState() { return state; }
    protected void setState(int s) { state = s; }

    // 入队并获取
    public final void acquire(int arg) {
        if (!tryAcquire(arg)) {
            Node node = addWaiter();
            acquireQueued(node, arg);
        }
    }

    private Node addWaiter() {
        Node node = new Node(Thread.currentThread());
        for (;;) {
            Node t = tail;
            node.prev = t;
            if (compareAndSetTail(t, node)) {
                t.next = node;
                return node;
            }
        }
    }

    final boolean acquireQueued(Node node, int arg) {
        boolean interrupted = false;
        try {
            for (;;) {
                Node p = node.prev;
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    return interrupted;
                }
                if (shouldParkAfterFailedAcquire(p, node))
                    LockSupport.park(this);
            }
        } catch (RuntimeException ex) {
            cancelAcquire(node);
            throw ex;
        }
    }

    static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
        int ws = pred.waitStatus;
        if (ws == Node.SIGNAL) return true;
        if (ws > 0) { // CANCELLED，跳过
            do { pred = pred.prev; node.prev = pred; } while (pred.waitStatus > 0);
            pred.next = node;
        } else {
            compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
        }
        return false;
    }

    public final boolean release(int arg) {
        if (tryRelease(arg)) {
            Node h = head;
            if (h != null && h.waitStatus != 0)
                unparkSuccessor(h);
            return true;
        }
        return false;
    }

    private void unparkSuccessor(Node node) {
        int ws = node.waitStatus;
        if (ws < 0) compareAndSetWaitStatus(node, ws, 0);
        Node s = node.next;
        if (s == null || s.waitStatus > 0) {
            s = null;
            for (Node t = tail; t != null && t != node; t = t.prev)
                if (t.waitStatus <= 0) s = t;
        }
        if (s != null) LockSupport.unpark(s.thread);
    }

    private void setHead(Node node) {
        head = node;
        node.thread = null;
        node.prev = null;
    }

    // 需要子类实现
    protected abstract boolean tryAcquire(int arg);
    protected abstract boolean tryRelease(int arg);
    // 省略 cancelAcquire...

    // Unsafe 相关
    private static final Unsafe unsafe;
    private static final long stateOffset;
    private static final long tailOffset;
    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = (Unsafe) f.get(null);
            stateOffset = unsafe.objectFieldOffset(MyAQS.class.getDeclaredField("state"));
            tailOffset = unsafe.objectFieldOffset(MyAQS.class.getDeclaredField("tail"));
        } catch (Exception e) { throw new Error(e); }
    }
    private boolean compareAndSetTail(Node expect, Node update) {
        return unsafe.compareAndSwapObject(this, tailOffset, expect, update);
    }
    private static boolean compareAndSetWaitStatus(Node node, int expect, int update) {
        // 简化：不考虑异常
        return unsafe.compareAndSwapInt(node, waitStatusOffset, expect, update);
    }
    private static final long waitStatusOffset;
    static {
        try {
            waitStatusOffset = unsafe.objectFieldOffset(Node.class.getDeclaredField("waitStatus"));
        } catch (Exception e) { throw new Error(e); }
    }
}
```

**设计思路**  
- 采用哑节点 `head` 简化了边界处理，队列始终非空。  
- `acquire` 中自旋检查前驱是否为头结点，实现公平语义（若为非公平锁则子类在 tryAcquire 时直接抢）。  
- `shouldParkAfterFailedAcquire` 确保前驱的 SIGNAL 状态设置好，保证自己能安全阻塞。  
- `release` 只唤醒后继，并辅助清理取消节点。  
- 整个框架已将排队、阻塞、唤醒逻辑封装，留下 `tryAcquire/tryRelease` 给子类，这就是 AQS 的精髓。

### 🐞 常见错误预警
- 忘记在入队时更新双向链表的 next 指针，导致从 head 遍历时出现断链，唤醒失败。
- 在 `acquireQueued` 中获取锁失败后未判断是否阻塞就直接 `park`，可能导致忙等。
- CAS 更新 tail 成功后，设置老 tail.next 时可能老 tail 已被其他线程修改，需注意顺序（但本例先 CAS 后设 next 是安全的，因为还没有线程向前看）。
- 释放锁时未清理 `head` 引用，可能产生内存泄漏。

---

## 📝 练习2：中级用法——基于 AQS 实现公平与非公平 ReentrantLock

### 业务场景
使用我们刚才的 `MyAQS` 实现一个可重入互斥锁，支持公平与非公平模式，并测试其可重入性、互斥性。

### 你的任务
1. 实现 `MiniReentrantLock` 类，内部定义 `Sync` 继承 `MyAQS`。
2. `tryAcquire(int acquires)` 实现：判断 state 是否为 0，若为 0 尝试获取；如果当前线程已持有锁，则增加 state（可重入）；否则失败。
3. 在公平锁模式下，只有当等待队列为空或当前线程是队列第一个等待者时，才能去竞争；非公平模式直接 CAS 竞争。
4. 实现 `lock()`、`unlock()`、`newCondition()` 方法（Condition 稍后练习再做）。
5. 测试：两个线程交替加锁释放，检查计数器是否累加。以及同一个线程多次获取锁，看 state 增加。

### ⚡ 关键提示
- 记录持有锁的线程：用 `Thread exclusiveOwnerThread`，在 tryAcquire 成功时设置。
- `tryRelease` 减少 state，减为 0 时释放锁并清空 exclusiveOwnerThread。
- 公平锁在 `tryAcquire` 时增加 `hasQueuedPredecessors()` 判断，非公平锁直接 CAS。
- 可重入意味着如果当前线程就是 `exclusiveOwnerThread`，则 `state += acquires`，不需要 CAS。

### ✍️ 动手写代码
```java
class MiniReentrantLock {
    private final Sync sync;
    public MiniReentrantLock(boolean fair) {
        sync = fair ? new FairSync() : new NonfairSync();
    }
    public void lock() { sync.acquire(1); }
    public void unlock() { sync.release(1); }
    // Condition 稍后
}
```

### ✅ 自我检查
- [ ] 线程 A 连续获取锁两次，state 是否为 2？释放两次后 state 为 0，其他线程能否获取？
- [ ] 公平模式下，先阻塞的线程是否在释放后先拿到锁？非公平模式下，可能出现插队吗？
- [ ] 非公平锁在高并发下吞吐量是否更高？为什么？（减少了线程切换）
- [ ] `hasQueuedPredecessors` 需要检查队列中是否有非当前线程的节点在等待。

### 📖 参考实现（直接展示）

```java
public class MiniReentrantLock {
    private final Sync sync;
    public MiniReentrantLock(boolean fair) { sync = fair ? new FairSync() : new NonfairSync(); }
    public void lock() { sync.acquire(1); }
    public void unlock() { sync.release(1); }
    public boolean tryLock() { return sync.tryAcquire(1); }

    abstract static class Sync extends MyAQS {
        Thread owner;
        abstract boolean hasQueuedPredecessors();

        @Override
        protected boolean tryAcquire(int arg) {
            int s = getState();
            if (s == 0) {
                if (!hasQueuedPredecessors() && compareAndSetState(0, arg)) {
                    owner = Thread.currentThread();
                    return true;
                }
            } else if (owner == Thread.currentThread()) {
                setState(s + arg); // 可重入
                return true;
            }
            return false;
        }

        @Override
        protected boolean tryRelease(int arg) {
            int s = getState() - arg;
            if (owner != Thread.currentThread())
                throw new IllegalMonitorStateException();
            setState(s);
            if (s == 0) {
                owner = null;
                return true;
            }
            return false;
        }
    }

    static class NonfairSync extends Sync {
        @Override boolean hasQueuedPredecessors() { return false; }
    }

    static class FairSync extends Sync {
        @Override boolean hasQueuedPredecessors() {
            // 检查队列中是否有等待线程在当前线程之前
            Node h = head, t = tail, s;
            return h != t &&
                   ((s = h.next) == null || s.thread != Thread.currentThread());
        }
    }
}
```

**设计思路**  
- 核心是 AQS 框架，我们只是填充了 `tryAcquire/tryRelease`。公平与非公平的区别仅在于 `hasQueuedPredecessors` 是否返回 true。  
- 可重入通过判断当前线程是否为持有者来实现，state 递增。  
- 非公平锁的 `tryAcquire` 在 `acquire` 中会先进行一次 CAS 抢锁，抢不到才入队，这就是“插队”行为。

### 🐞 常见错误预警
- 释放锁时忘记判断当前线程是否是持有者，导致非法释放。
- 公平锁的 `hasQueuedPredecessors` 实现错误，可能导致永远无法获取锁。
- 在 `tryAcquire` 中可重入情况下使用了 CAS，这是多余的，因为只有持有锁的线程才能执行到这里。

---

## 📝 练习3：高级/探索用法——实现 Condition 等待/通知机制

### 业务场景
类似 `synchronized` 的 `wait/notify`，我们需要在 ReentrantLock 里提供条件等待，让线程可以等待某个条件成立。实现 `Condition` 接口的关键方法 `await` 和 `signal`。

### 你的任务
1. 在 `MiniReentrantLock` 中添加 `newCondition()` 返回一个自定义的 `ConditionObject`。
2. `ConditionObject` 维护一个等待队列（单向链表即可），节点复用 AQS 的 Node 或自己定义。
3. `await()`：将当前线程加入条件队列，完全释放锁，然后 `park`，被唤醒后重新获取锁。
4. `signal()`：将条件队列的头部节点移动到同步队列中，并唤醒其线程（如果该线程在条件队列中）。
5. 保证条件等待和 signal 的语义：signal 唤醒的线程必须重新竞争锁。

### ⚡ 关键提示
- 使用 `LockSupport.park/unpark`。
- 释放锁需要多次调用 `release(fully)` 因为可能存在重入，需要释放所有重入次数，然后被唤醒后再重新获取相同数量的锁。
- 条件队列节点需要标记为 CONDITION waitStatus，以便区分。
- `signal` 时，将节点从条件队列移到同步队列尾部，等待被唤醒竞争锁。

### ✍️ 动手写代码
```java
public class ConditionObject {
    // 等待队列头尾
    // await, signal, signalAll
}
```

### ✅ 自我检查
- [ ] 线程 A 调用 `condition.await()` 后是否释放锁并阻塞？线程 B 能否获取锁并 `signal`？
- [ ] `signal` 后，等待的线程是否在 `await` 返回前重新获取了锁？
- [ ] 多个线程等待，`signalAll` 是否将它们全部移入同步队列？

### 📖 参考实现（简化版，直接展示）

```java
public class ConditionObject {
    public final MiniReentrantLock lock;
    private Node firstWaiter;
    private Node lastWaiter;

    public ConditionObject(MiniReentrantLock lock) { this.lock = lock; }

    public void await() throws InterruptedException {
        Node node = new Node(Thread.currentThread(), Node.CONDITION);
        // 加入条件队列
        if (lastWaiter == null) firstWaiter = node;
        else lastWaiter.next = node;
        lastWaiter = node;

        // 完全释放锁（重入次数）
        int savedState = lock.getState();
        lock.unlock(savedState); // 假设提供 unlock(int) 或者直接设置 state=0 并唤醒下一个
        // 简化：直接释放全部
        lock.sync.release(savedState); // 实际需要获取 Sync 实例

        // 阻塞直到被 signal
        while (isOnConditionQueue(node)) {
            LockSupport.park();
        }
        // 重新获取锁
        lock.lock();
    }

    public void signal() {
        Node first = firstWaiter;
        if (first != null) {
            // 从条件队列移除
            firstWaiter = first.next;
            if (firstWaiter == null) lastWaiter = null;
            // 移到同步队列
            lock.sync.enq(first); // 复用 MyAQS 的入队操作
            LockSupport.unpark(first.thread);
        }
    }

    // 检测节点是否还在条件队列（signal会移除，因此不在则说明被唤醒）
    private boolean isOnConditionQueue(Node node) {
        // 简化：判断 waitStatus != CONDITION 并且不在同步队列就返回false
        return node.waitStatus == Node.CONDITION;
    }
    // 需要在 MyAQS.Node 中添加 CONDITION 常量
}
```

**设计思路**  
- 条件等待队列只由 Condition 维护，节点通过 `next` 单向链接（AQS 中的条件队列也是单向的）。  
- `await` 必须释放锁才能让其他线程进入；signal 唤醒后重新获取锁，保证调用完 signal 后等待线程并不会立刻运行，而是进入同步队列排队。  
- 当前实现非常简化，未处理中断、超时、多 signal 竞争，但已展示 Condition 如何基于 AQS 工作。

### 🐞 常见错误预警
- `await` 释放锁时如果只调用一次 `unlock` 而锁是重入的，导致锁未完全释放，其他线程永远拿不到锁。
- `signal` 未从条件队列移除节点就直接放入同步队列，导致 `await` 循环无法退出。
- 忘记在 `await` 重新获取锁时恢复重入计数。

---

## 🏢 大厂场景实战：限流器实现

### 场景描述
实现一个简易的滑动窗口限流器，控制每秒钟最多 10 个请求通过。可以使用锁和条件来协调。

### 约束条件
- 使用 ReentrantLock 和 Condition 等待及唤醒。
- 记录时间戳，允许在窗口内通过。

### 你的设计任务
用伪代码或 Java 片段展示核心逻辑。

### 常见方案参考
```java
class RateLimiter {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final Queue<Long> queue = new LinkedList<>();
    private final int maxPermits;
    public void acquire() throws InterruptedException {
        lock.lock();
        try {
            long now = System.currentTimeMillis();
            while (!queue.isEmpty() && now - queue.peek() > 1000) queue.poll();
            while (queue.size() >= maxPermits) {
                condition.await();
            }
            queue.add(now);
        } finally {
            lock.unlock();
        }
    }
}
```

---

## 🏆 大厂面试题

### 面试题1：AQS 的 state 在互斥模式和共享模式下分别代表什么？请举例说明
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：

- **互斥模式**：state 通常表示锁的持有状态：0 表示未锁定，大于 0 表示被持有。如果是可重入锁，state 代表重入次数。如 `ReentrantLock`。
- **共享模式**：state 代表可用的资源数量。如 `Semaphore` 中 state 表示剩余许可数；`CountDownLatch` 中 state 表示尚需等待的事件数量。共享模式下多个线程可以同时获取 state，获取时 CAS 减少，释放时增加。
- **关键点**：AQS 通过 `tryAcquireShared` 返回负数失败，0 成功但无剩余，正数成功且有剩余，从而控制并发数量。
- **常见追问**：“共享模式和互斥模式可以共存于同一个 AQS 中吗？” 可以，如 `ReentrantReadWriteLock` 内部使用同一个 AQS，高 16 位表示读状态（共享），低 16 位表示写状态（独占）。
- **易错提醒**：共享模式下释放 resource 时可能会唤醒多个线程，独占模式只唤醒一个。
- **自我反思**：能否画出 `Semaphore` 的 `acquire/release` 状态流转图？

---

### 面试题2：公平锁和非公平锁在 AQS 的实现上有什么不同？各自优缺点？
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：
- **实现区别**：非公平锁在 `lock()` 方法中，一上来就 CAS 抢锁，抢不到才进入 `acquire` 流程，在那里 `tryAcquire` 仍然可能再次 CAS 抢锁（不检查队列）。公平锁的 `tryAcquire` 会先调用 `hasQueuedPredecessors()` 确保没有等待更久的线程。
- **优缺点**：
  - 非公平锁：吞吐量更高，减少了线程上下文切换，因为可能刚释放锁的线程立刻再次获取，无需唤醒等待线程。但可能导致某些线程饥饿。
  - 公平锁：严格保证 FIFO，不会饥饿，但性能较低，因为每次都要唤醒队头线程并切换上下文。
- **常见追问**：“`tryLock` 方法在 ReentrantLock 中是公平的还是非公平的？” `tryLock` 不分公平，直接 CAS 抢夺。
- **易错提醒**：使用非公平锁时不能假设获取顺序，可能产生“饥饿”现象，虽然 ReentrantLock 的非公平锁不会让线程永远饥饿，因为唤醒后线程会入队排队。
- **自我反思**：在 AQS 的源码中，非公平锁的 `tryAcquire` 实际上调用了 `nonfairTryAcquire`，公平锁的 `tryAcquire` 才实现排队检查。能否记住这两段关键代码？

---

### 面试题3：Condition 的 `await` 和 `signal` 与 `Object.wait`、`notify` 有什么本质区别？
**难度**：⭐️⭐️⭐️

**参考答案**：
- **原理**：两者都是在等待特定条件。但 `Object.wait` 要求必须持有对象 monitor，且能配合任意对象的 `synchronized` 块。`Condition` 必须与 `Lock` 配合，一个锁可以有多个 `Condition`，实现更细粒度的线程控制。
- **功能**：`Condition.await` 支持不响应中断、超时、截止时间等丰富变体；`signal` 可以唤醒指定条件的线程，不像 `notify` 只能唤醒一个等待当前对象的线程，或 `notifyAll` 唤醒所有。
- **实现**：`Condition` 内部维护自己的等待队列，唤醒时线程移到 AQS 同步队列重新排队获取锁，避免 `notify` 那种所有等待线程同时竞争 monitor 的内耗。
- **常见追问**：“为什么 `Object.wait` 必须在同步块里调用？” 因为需要获取对象 monitor，否则抛出 `IllegalMonitorStateException`。这是 JVM 级别的强制。
- **易错提醒**：`Condition.await` 后需要重新检查条件，因为可能存在“虚假唤醒”，这一点和 `Object.wait` 一样。
- **自我反思**：如果你需要实现一个有界阻塞队列，用 `Lock` 加两个 `Condition`（notFull, notEmpty）会比 `synchronized + wait/notifyAll` 更高效，原因是什么？因为可以精确定向唤醒生产者或消费者。

---

### 面试题4：AQS 的 CLH 队列变体与标准 CLH 锁的区别是什么？
**难度**：⭐️⭐️⭐️⭐️⭐️

**参考答案**：
- **标准 CLH 锁**：是一个基于隐式链表的自旋锁，每个线程在前驱节点的某标志位上自旋等待。节点被释放时，前驱将标志位置为 true，后继看到后继续执行。保持自旋，不阻塞线程。
- **AQS 的变体**：
  1. 使用双向链表（有 prev 和 next），而不是单向隐式链表。
  2. 使用 `LockSupport.park` 阻塞线程，而非自旋（除了在获取锁前有限的自旋尝试），因此是**阻塞**队列。
  3. 节点中有 `waitStatus` 信号机制，允许后继通知前驱它需要被唤醒。
  4. 可以处理取消、中断和超时等复杂语义。
- **为什么改变**：标准 CLH 是自旋锁，适用于 SMP 系统且临界区极短的场景。而 AQS 要实现通用同步器，临界区可能很长，自旋浪费 CPU，阻塞更合适。
- **常见追问**：“CLH 队列为什么叫隐式链表？” 因为每个线程只持有前驱的引用，自旋在前驱的标志上，没有显式的 next 指针。
- **易错提醒**：AQS 的节点有 `prev` 和 `next`，`next` 主要用于唤醒后继，`prev` 用于处理取消和重试。
- **自我反思**：如果你要设计一个高性能的自旋锁，会在哪些场景选择 CLH 锁而不是 AQS 的阻塞队列？

---

### 面试题5：如何用 AQS 实现一个 Semaphore？简述 `acquire` 和 `release` 的 AQS 调用流程
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：
- **实现**：`Semaphore` 内部定义一个 `Sync` 继承 AQS，使用共享模式。
  - `acquire()`：调用 `sync.acquireSharedInterruptibly(1)`。
  - `tryAcquireShared(int acquires)`：自旋 + CAS 减少 state，如果剩余许可不足则返回负数，进入等待队列。
  - `release()`：调用 `sync.releaseShared(1)`。
  - `tryReleaseShared(int releases)`：自旋 CAS 增加 state。
- **关键点**：释放成功后，AQS 会唤醒队列中的后继节点（共享模式可能会传播唤醒，连续唤醒多个等待的线程）。
- **常见追问**：“如果信号量的许可数很大，CAS 自旋会有什么问题？” 竞争激烈时可能导致 CAS 失败频繁。Java 使用 `LongAdder` 的思路或改变设计。
- **易错提醒**：`release` 可能被没有 `acquire` 的线程调用，因此 `Semaphore` 没有持有者概念，这不同于 `ReentrantLock`。
- **自我反思**：`Exchanger` 的底层是不是也用到了 AQS？不是，它用 `LockSupport` 和一些原子操作，但也可用类似 CLH 队列的思路。

---

> 今天你亲手剥开了 AQS 的心脏，并借此造出了自己的锁和条件队列。AQS 是 JUC 的基石，从此你看 `ReentrantReadWriteLock`、`Semaphore`、`CountDownLatch` 都将是透明的。明天我们走进线程池的源码，去定制一个真正属于自己的线程池。







# 🚀 ReentrantLock 实战速学：一个电商秒杀系统搞定全部知识点

我给你设计一个**「双十一秒杀系统」**的完整案例，把 **公平/非公平锁 + Condition条件 + 可重入** 三大核心一网打尽。

------

## 📋 场景设定

> 双十一零点，10000人同时抢100台iPhone。系统需要：
>
> - ✅ 扣库存（互斥访问）
> - ✅ 库存为0时，线程等待，有补货时通知（**Condition**）
> - ✅ 对比公平锁 vs 非公平锁的表现差异

------

## 🔧 第一步：基础版 —— 可重入锁（先热身）

```java
javaclass Stock {
    private int count = 100;
    private final ReentrantLock lock = new ReentrantLock(); // 默认非公平锁

    public void deduct() {
        lock.lock();
        try {
            count--;
            System.out.println(Thread.currentThread().getName() 
                + " 抢到！剩余: " + count);
        } finally {
            lock.unlock();
        }
    }

    // 🔑 可重入演示：同一个线程可以重复加锁
    public void reentrantDemo() {
        lock.lock();
        try {
            System.out.println("第一次加锁，state=" + lock.getHoldCount());
            lock.lock(); // 同一个线程再次加锁 → 不会死锁！
            try {
                System.out.println("第二次加锁，state=" + lock.getHoldCount());
            } finally {
                lock.unlock();
            }
        } finally {
            lock.unlock();
        }
    }
}
```

> 💡 **可重入** = 同一个线程可以多次加锁，每次 `lock()` state+1，每次 `unlock()` state-1，state归0才真正释放。

------

## 🔥 第二步：Condition 条件 —— 库存为0时等待，补货时唤醒

这是 **ReentrantLock 最杀手级的功能**，比 `synchronized + wait/notify` 强大得多 👇

```java
javaclass StockWithCondition {
    private int count = 10; // 只有10台！
    private final ReentrantLock lock = new ReentrantLock();
    // 🔑 关键：一个锁可以绑定多个Condition！
    private final Condition notEmpty  = lock.newCondition(); // 库存>0，通知抢购线程
    private final Condition notFull   = lock.newCondition(); // 库存<100，通知补货线程

    // 📦 抢购方法
    public void buy(String user) throws InterruptedException {
        lock.lock();
        try {
            // ⭐ 条件等待：while循环防止虚假唤醒（必须用while，不能用if！）
            while (count <= 0) {
                System.out.println(user + " 库存不足，进入等待...");
                notEmpty.await(); // 🔥 释放锁 + 挂起线程（两个动作原子性完成！）
            }
            count--;
            System.out.println("🎉 " + user + " 抢到！剩余 " + count);
            // 抢完通知补货线程：库存不满了，快来补货
            notFull.signal();
        } finally {
            lock.unlock();
        }
    }

    // 🚚 补货方法
    public void restock(int num) throws InterruptedException {
        lock.lock();
        try {
            while (count >= 100) {
                System.out.println("📦 仓库已满，补货线程等待...");
                notFull.await();
            }
            int add = Math.min(num, 100 - count);
            count += add;
            System.out.println("📦 补货 +" + add + "，当前库存: " + count);
            // 补完通知抢购线程：有货了，快来抢！
            notEmpty.signalAll(); // 🔥 唤醒所有等待的抢购线程
        } finally {
            lock.unlock();
        }
    }
}
```

### 🧠 Condition vs Object.wait() 的核心区别

| 特性         | `synchronized + wait()`       | `ReentrantLock + Condition`                |
| ------------ | ----------------------------- | ------------------------------------------ |
| 等待队列数量 | **只有1个**（所有线程挤一起） | **可以多个**（notEmpty、notFull 各自独立） |
| 唤醒精度     | 只能 `notifyAll()` 全部唤醒   | 可以 `signal()` 精确唤醒指定条件的线程     |
| 虚假唤醒处理 | 需要自己处理                  | 一样需要while循环                          |
| 释放锁       | wait()会释放锁                | await()会释放锁                            |

> 🎯 **一句话记住**：Condition 让你能说「**只叫醒在等A条件的线程**」，而不是把所有线程全叫醒。

------

## & 第三步：公平锁 vs 非公平锁 —— 真实性能对比

这是你最想搞懂的部分，直接上**可运行的对比代码**：

```java
javapublic class FairVsUnfairDemo {

    // 🔥 公平锁：先来后到，严禁插队
    private static final ReentrantLock fairLock = new ReentrantLock(true);
    // ⚡ 非公平锁（默认）：允许插队，性能更高
    private static final ReentrantLock unfairLock = new ReentrantLock(false);

    public static void main(String[] args) throws InterruptedException {
        System.out.println("===== 公平锁测试 =====");
        testLock(fairLock, "公平锁");

        Thread.sleep(2000);

        System.out.println("\n===== 非公平锁测试 =====");
        testLock(unfairLock, "非公平锁");
    }

    private static void testLock(ReentrantLock lock, String name) throws InterruptedException {
        lock.lock(); // 先让一个线程持有锁

        Runnable task = () -> {
            try {
                Thread.sleep(100); // 模拟先做点事再来抢锁
            } catch (InterruptedException e) { }

            lock.lock();
            try {
                System.out.println(Thread.currentThread().getName() 
                    + " 拿到了" + name + " ✅");
            } finally {
                lock.unlock();
            }
        };

        // 启动5个线程，它们几乎同时到达
        for (int i = 0; i < 5; i++) {
            new Thread(task, "Thread-" + i).start();
        }

        Thread.sleep(500); // 等所有线程都到了
        lock.unlock(); // 释放锁，让它们抢！
    }
}
```

### 📊 运行结果对比

```
===== 公平锁测试 =====
Thread-0 拿到了公平锁 ✅      ← 先来的先拿到
Thread-1 拿到了公平锁 ✅      ← 严格按顺序
Thread-2 拿到了公平锁 ✅
Thread-3 拿到了公平锁 ✅
Thread-4 拿到了公平锁 ✅

===== 非公平锁测试 =====
Thread-2 拿到了非公平锁 ✅    ← 乱序！后来的插队了
Thread-0 拿到了非公平锁 ✅
Thread-4 拿到了非公平锁 ✅
Thread-1 拿到了非公平锁 ✅
Thread-3 拿到了非公平锁 ✅
```

### 🧠 用一张图彻底搞懂原理

```
【非公平锁的"插队"发生在哪一步？】

线程A持锁 → A释放锁 → 锁变为空闲(state=0)
                              │
              ┌───────────────┴───────────────┐
              ▼                               ▼
     等待队列里的B、C想抢              新来的D也想抢
              │                               │
              ▼                               ▼
         B、C 还在排队                    D 直接 CAS 抢锁 ← 🔥 插队！
         
     结果：D可能比B、C先拿到锁！

【公平锁为什么不会插队？】

线程A持锁 → A释放锁 → 锁变为空闲(state=0)
                              │
                              ▼
                    hasQueuedPredecessors() 检查队列
                    → 发现B、C在排队！
                    → D 必须去队尾排队，不许抢！← 🚫 严禁插队
```

### 📈 性能差异（实测数据参考）

| 指标     | 公平锁             | 非公平锁                      |
| -------- | ------------------ | ----------------------------- |
| 吞吐量   | ⭐⭐                 | ⭐⭐⭐⭐⭐                         |
| 延迟     | 较高（要排队）     | 较低（可以抢）                |
| 公平性   | ✅ 严格FIFO         | ❌ 可能饥饿                    |
| 适用场景 | 金融交易、任务调度 | **99%的业务场景（默认选它）** |

> 💡 **实战结论**：除非你在做银行转账系统（必须按顺序处理），否则 **无脑用非公平锁（默认）**，性能高很多。

------

## 🎯 终极整合：完整秒杀系统（三者合一）

```java
public class SecKillSystem {
    private int stock = 5;
    private final ReentrantLock lock = new ReentrantLock(false); // 非公平锁，性能优先
    private final Condition hasStock = lock.newCondition();

    public void seckill(String user) throws InterruptedException {
        lock.lock();
        try {
            while (stock <= 0) {
                System.out.println("😩 " + user + " 没货了，等待补货...");
                hasStock.await(); // Condition等待
            }
            stock--;
            System.out.println("🏆 " + user + " 秒杀成功！库存剩余: " + stock);
        } finally {
            lock.unlock();
        }
    }

    public void restock(int num) throws InterruptedException {
        lock.lock();
        try {
            stock += num;
            System.out.println("📦 补货完成，当前库存: " + stock);
            hasStock.signalAll(); // Condition唤醒
        } finally {
            lock.unlock();
        }
    }

    public static void main(String[] args) {
        SecKillSystem system = new SecKillSystem();

        // 100个用户抢购，只有5个库存
        for (int i = 0; i < 100; i++) {
            final int id = i;
            new Thread(() -> {
                try {
                    system.seckill("用户" + id);
                } catch (InterruptedException e) { }
            }).start();
        }

        // 1秒后补货
        new Thread(() -> {
            try { Thread.sleep(1000); } catch (Exception e) {}
            try {
                system.restock(10);
            } catch (InterruptedException e) { }
        }).start();
    }
}
```

------

## 📝 速记口诀（背下来就忘不了）

| 知识点               | 一句话记忆                                                   |
| -------------------- | ------------------------------------------------------------ |
| **可重入**           | 同一个线程，锁可以叠着加，unlock叠着减                       |
| **Condition**        | `await()` 释放锁并等待，`signal()` 精确叫醒该醒的线程        |
| **公平锁**           | 排队买票，先来先得，不许插队（`new ReentrantLock(true)`）    |
| **非公平锁**         | 公交车抢座，谁快谁上，允许插队（`new ReentrantLock(false)` 默认） |
| **为什么非公平更快** | 少了排队检查的开销，CPU不空转                                |

> 🔥 **最终建议**：日常开发 **99%用非公平锁 + Condition**，只有金融/排队场景才用公平锁。

把上面的代码复制跑一遍，比看10篇文章都管用 💪







# 第 11 天：线程池源码级实验 + 自定义拒绝策略
本日掌握：彻底搞懂线程池的核心参数、ctl 状态位、工作流程，并能手写一个带拒绝策略的简易线程池  
覆盖原理点：13 (线程池核心参数与流程)  
阶段：使用期

## 🎯 今日目标
- 能解释 `ThreadPoolExecutor` 每个构造参数的含义，以及它们如何协作。
- 能通过源码分析 `ctl` 的高 3 位存状态、低 29 位存工作线程数的设计。
- 能实现一个自定义拒绝策略，并应用到线程池中观察效果。
- 能手写一个简化版线程池，包含核心线程、最大线程、工作队列、拒绝策略。
- 能自信应对面试中关于线程池的连环追问：核心线程回收、`allowCoreThreadTimeOut`、四种拒绝策略差异、动态调整。

---

## 📝 练习1：基础用法——分析线程池参数与执行流程（必做）

### 业务场景
你维护的一个后端服务需要批量处理用户请求，为了控制资源，你使用 `ThreadPoolExecutor` 手动创建线程池，而不是 `Executors`。你需要完全掌握每个参数对行为的影响，并打印出线程池的内部状态（如核心线程数、活动线程数、队列大小）来验证流程。

### 你的任务
1. 创建一个 `ThreadPoolExecutor`，参数为：
   - 核心线程数：2
   - 最大线程数：4
   - 保活时间：10 秒
   - 工作队列：`LinkedBlockingQueue`（容量 2）
   - 拒绝策略：`AbortPolicy`（默认）
2. 提交 7 个任务（每个任务打印当前线程名并 sleep 2 秒），在提交前、中、后打印线程池的 `getPoolSize()`、`getActiveCount()`、`getQueue().size()`、`getCompletedTaskCount()`。
3. 观察任务执行顺序：是否前 2 个任务直接运行？随后 2 个进入队列？再创建 2 个新线程（达到最大）？最后一个被拒绝？
4. 改变队列容量为 `SynchronousQueue`，再次观察：是否会立即创建非核心线程直到达到最大？并体会 `SynchronousQueue` 不存储任务的特性。

### ⚡ 关键提示
- `execute()` 提交任务后，适当 sleep 等待线程池状态变化便于观察。
- `LinkedBlockingQueue` 若不指定容量，默认 `Integer.MAX_VALUE`，会导致最大线程数几乎不生效（队列极大）。
- `SynchronousQueue` 是容量为 0 的队列，每个插入操作必须等待一个相应的删除操作，否则会尝试创建新线程，直到达到最大线程数，然后执行拒绝策略。
- 使用 `Thread.sleep` 在任务内模拟耗时操作，延长任务时间以便观察并发情况。

### ✍️ 动手写代码
```java
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    2, 4, 10, TimeUnit.SECONDS,
    new LinkedBlockingQueue<>(2),
    new ThreadPoolExecutor.AbortPolicy()
);
for (int i = 0; i < 7; i++) {
    final int n = i;
    try {
        executor.execute(() -> {
            System.out.println(Thread.currentThread().getName() + " 执行任务 " + n);
            try { Thread.sleep(2000); } catch (InterruptedException e) {}
        });
    } catch (RejectedExecutionException e) {
        System.out.println("任务 " + n + " 被拒绝");
    }
    // 打印状态
}
```

### ✅ 自我检查
- [ ] 观察打印日志，确认前 2 个任务立即执行，第 3、4 个进入队列，第 5、6 个创建新线程执行，第 7 个被拒绝。
- [ ] 任务完成数是否递增？
- [ ] 使用 `SynchronousQueue` 替换后，是否没有任务排队，直接创建线程直到最大，然后拒绝？
- [ ] 你能在脑海中画出线程池处理任务的流程图吗？

### 📖 参考实现（直接展示）

```java
import java.util.concurrent.*;

public class ThreadPoolParamDemo {
    public static void main(String[] args) throws InterruptedException {
        // 改为 SynchronousQueue 以观察差异，这里先用 LinkedBlockingQueue
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                2, 4, 10, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(2),
                new ThreadPoolExecutor.AbortPolicy()
        );
        printStatus(pool, "初始化");
        for (int i = 0; i < 7; i++) {
            final int n = i;
            try {
                pool.execute(() -> {
                    System.out.println(Thread.currentThread().getName() + " exec task " + n);
                    try { Thread.sleep(2000); } catch (InterruptedException e) {}
                });
                System.out.println("提交任务 " + n + " 成功");
            } catch (RejectedExecutionException e) {
                System.out.println("任务 " + n + " 被拒绝！");
            }
            Thread.sleep(100); // 等待任务分配
            printStatus(pool, "提交第 " + (i + 1) + " 个任务后");
        }
        Thread.sleep(5000);
        printStatus(pool, "最终状态");
        pool.shutdown();
    }

    private static void printStatus(ThreadPoolExecutor pool, String tag) {
        System.out.printf("%s: poolSize=%d, active=%d, queueSize=%d, completed=%d%n",
                tag, pool.getPoolSize(), pool.getActiveCount(),
                pool.getQueue().size(), pool.getCompletedTaskCount());
    }
}
```

**设计思路**  
- 核心线程 2，最大 4，队列容量 2。根据线程池逻辑：
  1. 任务 1、2：创建核心线程立即执行。
  2. 任务 3、4：核心线程忙，放入队列。
  3. 任务 5、6：队列满，创建新线程（非核心）直到达到最大 4。
  4. 任务 7：线程数已最大，队列满，触发拒绝策略。
- 使用 `printStatus` 实时查看内部计数器，验证与预期一致。
- 更换为 `SynchronousQueue`，所有任务在核心线程满后直接创建新线程，无缓冲，经常导致拒绝。

### 🐞 常见错误预警
- 忘记 `shutdown` 或 `shutdownNow`，导致线程池无法退出，JVM 一直运行。
- 误认为核心线程创建后始终存活，其实核心线程默认不被回收，除非设置 `allowCoreThreadTimeOut(true)`。
- `getPoolSize()` 是在池中当前线程数（包括核心和临时线程），`getActiveCount()` 是正在执行任务的大致数量，不精确。

---

## 📝 练习2：中级用法——自定义线程池与拒绝策略

### 业务场景
你的应用里，当线程池满载时，不能直接丢弃任务，而是需要把任务持久化到数据库或重试队列，再或者由调用线程自己执行。你必须实现一个自定义的 `RejectedExecutionHandler`。

### 你的任务
1. 实现一个 `LogAndRetryPolicy`，当任务被拒绝时：
   - 记录日志（含任务信息）。
   - 将该任务塞入一个重试队列（可用另一个线程池定期重试，简写即可）。
2. 另一种策略：实现 `CallerRunsPolicy` 的增强版，让调用者线程执行任务但限制执行时间，超时则放弃。
3. 测试你的自定义策略，提交大量任务，观察拒绝时触发的逻辑。
4. 在自定义策略中统计被拒绝次数，并通过 JMX 或简单原子变量暴露出来。

### ⚡ 关键提示
- 实现 `RejectedExecutionHandler` 接口，重写 `rejectedExecution(Runnable r, ThreadPoolExecutor executor)` 方法。
- 在方法内可获取到被拒绝的任务 `r`，可以记录、存储、或者使用 `r.run()` 直接在当前线程运行（注意这会让提交任务的线程阻塞）。
- 如果要做重试，可以使用一个单独的队列和单线程池定期消费。
- 可以用 `AtomicInteger` 统计拒绝次数。

### ✍️ 动手写代码
```java
class LogAndRetryPolicy implements RejectedExecutionHandler {
    private final BlockingQueue<Runnable> retryQueue = new LinkedBlockingQueue<>();
    private final ThreadPoolExecutor retryExecutor = ...;

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        retryQueue.offer(r);
        System.out.println("任务被拒绝，加入重试队列，当前队列长度: " + retryQueue.size());
        // 定期重试逻辑省略
    }
}
```

### ✅ 自我检查
- [ ] 当线程池满后，自定义策略是否被触发？日志是否打印？
- [ ] 重试队列中的任务是否最终被执行？
- [ ] 调用者运行策略下，提交任务的线程是否阻塞？超时是否处理？

### 📖 参考实现（直接展示）

```java
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CustomRejectionDemo {
    public static void main(String[] args) {
        AtomicInteger rejectCount = new AtomicInteger();

        RejectedExecutionHandler handler = (r, executor) -> {
            rejectCount.incrementAndGet();
            System.err.println("任务 " + r + " 被拒绝，当前拒绝次数: " + rejectCount.get());
            // 执行“调用者运行”策略：由提交线程运行
            if (!executor.isShutdown()) {
                r.run();
            }
        };

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1),
                handler
        );

        for (int i = 0; i < 5; i++) {
            final int n = i;
            pool.execute(() -> {
                System.out.println(Thread.currentThread().getName() + " executing " + n);
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            });
        }
        pool.shutdown();
    }
}
```

**设计思路**  
- 使用 Lambda 表达式简化自定义策略：统计拒绝次数并让提交任务的线程自己执行。  
- 这种策略适合让用户线程承担部分压力，避免丢弃任务。  
- 如果任务需要持久化，可替换为写入重试表或 MQ。

### 🐞 常见错误预警
- 在拒绝策略中调用 `executor.execute(r)` 会导致无限递归，因为此时线程池仍满，会再次拒绝。
- 如果使用调用者运行策略，且该线程本身在锁内，可能产生死锁。
- 重试队列需要定期消费，否则内存泄漏。

---

## 📝 练习3：高级/探索用法——探究 ctl 与线程池状态转换，模拟核心逻辑

### 业务场景
为了彻底理解 `ThreadPoolExecutor` 的精密控制，你要手写一个 `MiniThreadPool`，模拟：
- 用一个 `AtomicInteger ctl` 同时存储运行状态和线程数。
- 核心线程、最大线程、工作队列的交互流程。
- 线程的 `addWorker` 和 `runWorker` 循环取任务。

### 你的任务
1. 实现 `MiniThreadPool` 类，字段 `ctl`（`AtomicInteger`）高 3 位存状态（RUNNING、SHUTDOWN、STOP、TIDYING、TERMINATED），低 29 位存 `workerCount`。
2. 实现 `execute(Runnable command)`：
   - 若 workerCount < corePoolSize，则直接 `addWorker`。
   - 否则尝试 `workQueue.offer(command)`，成功后仍需要二次检查线程池状态。
   - 如果入队失败，尝试 `addWorker`，失败则执行拒绝策略。
3. `addWorker` 创建一个新线程（包装成 Worker，Worker 实现 Runnable），启动它。Worker 循环从队列取任务（`getTask`），并执行它。
4. `getTask` 从队列取任务，可能因为超时或状态改变而返回 null 使 worker 退出。
5. 提供简化的拒绝策略接口。
6. 编写测试，验证与标准线程池行为相似。

### ⚡ 关键提示
- 状态常量：`RUNNING = -1 << COUNT_BITS`（`COUNT_BITS = Integer.SIZE - 3`）等，类似 JDK 源码。
- `ctl.get()` & `~CAPACITY` 得到状态，`ctl.get() & CAPACITY` 得到 workerCount。
- CAS 更新 ctl，失败重试。
- 使用 `ReentrantLock` 和 `Condition` 控制线程的生产消费，但为了简单可直接使用 `BlockingQueue`。
- Worker 必须持有线程引用，并在内部循环获取任务，当 `getTask` 返回 null 时，worker 退出循环并线程结束。

### ✍️ 动手写代码
```java
public class MiniThreadPool {
    private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));
    private static final int COUNT_BITS = Integer.SIZE - 3;
    private static final int CAPACITY = (1 << COUNT_BITS) - 1;
    private static final int RUNNING = -1 << COUNT_BITS;
    // ...其他状态
    // execute, addWorker, getTask, etc.
}
```

### ✅ 自我检查
- [ ] 能否正确计算状态和 workerCount 的打包拆包？
- [ ] 核心线程数达到后，新任务是否入队？
- [ ] 队列满后，是否创建非核心线程直到最大线程数，然后拒绝？
- [ ] `shutdown` 后线程池是否不再接受新任务，但执行完队列中的任务？

### 📖 参考实现（直接展示）

```java
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class MiniThreadPool {
    private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));
    private static final int COUNT_BITS = Integer.SIZE - 3;
    private static final int CAPACITY   = (1 << COUNT_BITS) - 1;
    private static final int RUNNING    = -1 << COUNT_BITS;
    private static final int SHUTDOWN   =  0 << COUNT_BITS;
    private static final int STOP       =  1 << COUNT_BITS;
    private static final int TIDYING    =  2 << COUNT_BITS;
    private static final int TERMINATED =  3 << COUNT_BITS;

    private static int ctlOf(int rs, int wc) { return rs | wc; }
    private static int runStateOf(int c)     { return c & ~CAPACITY; }
    private static int workerCountOf(int c)  { return c & CAPACITY; }

    private final BlockingQueue<Runnable> workQueue;
    private final int corePoolSize, maxPoolSize;
    private final RejectedExecutionHandler handler;
    private final ReentrantLock mainLock = new ReentrantLock();

    public MiniThreadPool(int core, int max, BlockingQueue<Runnable> queue, RejectedExecutionHandler handler) {
        this.corePoolSize = core;
        this.maxPoolSize = max;
        this.workQueue = queue;
        this.handler = handler;
    }

    public void execute(Runnable command) {
        int c = ctl.get();
        if (workerCountOf(c) < corePoolSize) {
            if (addWorker(command, true)) return;
            c = ctl.get();
        }
        if (isRunning(c) && workQueue.offer(command)) {
            int recheck = ctl.get();
            if (!isRunning(recheck) && remove(command))
                reject(command);
            else if (workerCountOf(recheck) == 0)
                addWorker(null, false);
        } else if (!addWorker(command, false)) {
            reject(command);
        }
    }

    private boolean addWorker(Runnable firstTask, boolean core) {
        retry:
        for (;;) {
            int c = ctl.get();
            int rs = runStateOf(c);
            if (rs >= SHUTDOWN && !(rs == SHUTDOWN && firstTask == null && !workQueue.isEmpty()))
                return false;
            for (;;) {
                int wc = workerCountOf(c);
                if (wc >= CAPACITY || wc >= (core ? corePoolSize : maxPoolSize))
                    return false;
                if (compareAndIncrementWorkerCount(c))
                    break retry;
                c = ctl.get();
                if (runStateOf(c) != rs) continue retry;
            }
        }
        boolean workerStarted = false;
        try {
            Worker w = new Worker(firstTask);
            final Thread t = w.thread;
            if (t != null) {
                mainLock.lock();
                try {
                    int c = ctl.get();
                    int rs = runStateOf(c);
                    if (rs == RUNNING || (rs == SHUTDOWN && firstTask == null)) {
                        workers.add(w);
                        t.start();
                        workerStarted = true;
                    }
                } finally {
                    mainLock.unlock();
                }
            }
        } finally {
            if (!workerStarted) {
                decrementWorkerCount();
            }
        }
        return workerStarted;
    }

    private void runWorker(Worker w) {
        Runnable task = w.firstTask;
        w.firstTask = null;
        while (task != null || (task = getTask()) != null) {
            try { task.run(); } finally { task = null; }
        }
    }

    private Runnable getTask() {
        boolean timedOut = false;
        for (;;) {
            int c = ctl.get();
            int rs = runStateOf(c);
            if (rs >= SHUTDOWN && (rs >= STOP || workQueue.isEmpty())) {
                decrementWorkerCount();
                return null;
            }
            int wc = workerCountOf(c);
            boolean timed = wc > corePoolSize;
            try {
                Runnable r = timed ?
                    workQueue.poll(1, TimeUnit.SECONDS) :
                    workQueue.take();
                if (r != null) return r;
                timedOut = true;
            } catch (InterruptedException retry) {
                timedOut = false;
            }
        }
    }

    private void reject(Runnable command) {
        handler.rejectedExecution(command, null);
    }
    // 其他辅助方法省略（workers 容器、cas 操作等）
}
```

**设计思路**  
- 借鉴 JDK 源码，使用 `ctl` 原子变量打包状态和计数，通过位操作分离，保证一致性。  
- `addWorker` 通过双层循环 CAS 增加计数，并加锁启动线程。  
- `getTask` 根据是否超过核心线程数决定是 `poll` 还是 `take`，实现核心线程保活、非核心超时回收。  
- 这是一次对线程池源码的深度还原，完成后你对 ctl 的理解会极其深刻。

### 🐞 常见错误预警
- CAS 更新 ctl 时忘记处理状态变化，可能导致 `addWorker` 在 SHUTDOWN 后仍添加。
- Worker 线程启动后若 worker 未从 workers 集合移除，会导致内存泄漏。
- 在 `getTask` 中 `decrementWorkerCount` 的时机必须正确，否则 workerCount 和实际线程数不一致。

---

## 🏢 大厂场景实战：动态线程池监控与调优

### 场景描述
生产环境有一个异步处理线程池（核心 10，最大 20，队列长度 200），在高峰时经常触发拒绝策略。需要设计一个动态调整线程池大小的方案，并实时监控队列长度和拒绝次数。

### 约束条件
- 不能重启服务。
- 需提供 HTTP 接口查看线程池状态，并支持动态修改核心线程和最大线程。
- 必须保证调整的安全性（不能大于最大容量等）。

### 你的设计任务
1. 封装一个 `DynamicThreadPool` 继承 `ThreadPoolExecutor`，提供 `setCorePoolSize`、`setMaximumPoolSize` 同步调整。
2. 提供 REST 端点（如 Spring Actuator + JMX）打印线程池指标。
3. 实现告警：当队列使用率超过 80% 或拒绝次数超过阈值时，打印日志或发钉钉消息。

### 常见方案参考
- 直接使用 `ThreadPoolExecutor` 自带的 `setCorePoolSize` 和 `setMaximumPoolSize`，它们是安全的。
- 监控：通过 `getQueue().size()` 和自定义的拒绝策略里累加计数。
- 调优策略：先调大最大线程，再视情况调大核心线程，避免频繁创建销毁线程。

---

## 🏆 大厂面试题

### 面试题1：ThreadPoolExecutor 的 `ctl` 字段是如何设计的？为什么要这样设计？
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：
- **设计**：`ctl` 是一个 `AtomicInteger`，高 3 位表示线程池运行状态（RUNNING、SHUTDOWN、STOP、TIDYING、TERMINATED），低 29 位表示有效工作线程数量。
- **打包/拆包**：`ctlOf(rs, wc) = rs | wc`，取状态用 `ctl & ~CAPACITY`，取数量用 `ctl & CAPACITY`。
- **原因**：为了用 CAS 原子地同时更新状态和线程数，避免竞态条件。例如，从 RUNNING 变为 SHUTDOWN 同时需要保证线程数正确。这种复合操作需要放在一个原子变量中。
- **常见追问**：“29 位够用吗？” 2^29-1 ≈ 5.3 亿，远超单机线程数量极限，所以够用。
- **易错提醒**：有人在代码中直接拼接整型，忘记使用 `CAPACITY` 掩码，导致状态被污染。
- **自我反思**：如果让你为另一个同步器设计类似的复合状态，你会用 `AtomicLong` 还是 `AtomicInteger` + 分割？

---

### 面试题2：线程池的工作流程（从 execute 提交到任务执行的整个路径）？
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：
1. 检查当前工作线程数 < 核心线程数？是则创建新核心线程执行任务（addWorker）。
2. 如果核心线程满了，尝试将任务放入工作队列 `workQueue.offer(command)`。
3. 入队成功，还需二次检查线程池状态（可能已 shutdown），以及如果工作线程数为0，添加一个空任务的线程。
4. 入队失败，尝试创建非核心线程执行（addWorker）。
5. 如果达到最大线程数且队列满，执行拒绝策略。
- **关键参数**：核心线程数、最大线程数、空闲线程存活时间、工作队列类型（有界/无界/同步）。
- **常见追问**：“核心线程会被回收吗？” 默认不会，但设置了 `allowCoreThreadTimeOut(true)` 后，核心线程空闲超时可以回收。
- **易错提醒**：使用无界队列时，最大线程数参数相当于失效，因为任务永远入队成功。
- **自我反思**：当队列选择 `SynchronousQueue` 时，流程有何不同？会跳过入队，直接尝试创建非核心线程，直到最大。

---

### 面试题3：四种拒绝策略分别是什么？如何选择？
**难度**：⭐️⭐️⭐️

**参考答案**：
1. `AbortPolicy`：直接抛 `RejectedExecutionException`，默认。
2. `CallerRunsPolicy`：由提交任务的线程自己执行，减缓提交速度。
3. `DiscardPolicy`：静默丢弃，不抛异常。
4. `DiscardOldestPolicy`：丢弃队列中最老的任务（队头），然后尝试重新提交当前任务。
- **选择原则**：
  - 绝不能丢：`CallerRunsPolicy` 或自定义重试。
  - 允许丢失且通知：`AbortPolicy`。
  - 可以丢且不需通知：`DiscardPolicy`。
  - 优先保证新数据：`DiscardOldestPolicy`。
- **常见追问**：“有哪个策略会导致死锁？” `CallerRunsPolicy` 如果提交线程持有锁，而执行的任务又需要该锁，可能死锁。
- **易错提醒**：`DiscardOldestPolicy` 并不丢弃当前任务，而是丢弃队伍最前面的任务，这可能造成旧任务的丢失。
- **自我反思**：你的应用中有没有自定义拒绝策略的必要？比如结合 MQ 异步重试。

---

### 面试题4：如何监控线程池的运行状态？你通常会关注哪些指标？
**难度**：⭐️⭐️⭐️

**参考答案**：

- **指标**：核心线程数、最大线程数、当前活动线程数、历史最大线程数、队列当前大小、队列剩余容量、完成任务总数、拒绝次数。
- **获取方式**：`ThreadPoolExecutor` 提供了 `getPoolSize()`、`getActiveCount()`、`getLargestPoolSize()`、`getQueue().size()`、`getCompletedTaskCount()` 等方法。
- **监控工具**：JMX（`ThreadPoolExecutor` 自动注册 MBean）、Spring Actuator、Micrometer、自定义定时任务打印或推送到监控系统。
- **告警**：队列使用率 > 80% 或拒绝次数增长时预警。
- **常见追问**：“为什么 `getActiveCount()` 是一个近似值？” 因为它是非同步的，在统计时可能有线程刚启动/结束。
- **易错提醒**：`getQueue().size()` 如果队列是 `DelayQueue` 等复杂实现，性能可能不佳。
- **自我反思**：能否写出一个简单的自定义健康检查端点，输出线程池的核心指标？

---

### 面试题5：为什么不推荐使用 `Executors` 创建线程池，而要使用 `ThreadPoolExecutor` 构造？
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：
- `Executors.newFixedThreadPool(n)` / `newCachedThreadPool()` 等内部使用了无界队列（`LinkedBlockingQueue` 默认容量 `Integer.MAX_VALUE`）或 `SynchronousQueue`，容易因无界队列导致 OOM（任务堆积），或因为不限制线程数导致 OOM（`Cached` 最大线程为 `Integer.MAX_VALUE`）。
- `ThreadPoolExecutor` 手动构造可以明确设置队列容量、拒绝策略、线程工厂（命名），更贴近资源管控。
- **阿里巴巴规范** 明确要求手动创建线程池，以便让开发者清晰掌握资源限制和拒绝策略。
- **常见追问**：“`ScheduledThreadPoolExecutor` 呢？” 它最大线程也是 `Integer.MAX_VALUE`，也存在 OOM 风险，建议用 `ScheduledExecutorService` 包装并设置合理大小。
- **易错提醒**：使用 `Executors` 返回的线程池默认未被监控和调优，更容易在极端情况下崩溃。
- **自我反思**：你是否已经在自己项目中用 `ThreadPoolExecutor` 替换了所有 `Executors`？

---

> 今天你深入了线程池的核心，从参数、状态到源码级别的 ctl 都已掌握。线程池是并发编程中最常用的基建，务必反复练习。明天我们将揭密 ThreadLocal 的内存泄漏与最佳实践。







# 第 12 天：ThreadLocal 内存泄漏复现与分析
本日掌握：亲手复现 ThreadLocal 内存泄漏，理解 `ThreadLocalMap` 的 WeakReference 设计，掌握 `InheritableThreadLocal` 使用场景  
覆盖原理点：14 (ThreadLocal 与内存泄漏)  
阶段：使用期

## 🎯 今日目标
- 能清晰画出 `Thread`、`ThreadLocalMap`、`Entry`（WeakReference）之间的引用链。
- 能通过代码故意制造内存泄漏并使用 `jmap`/`jconsole` 观察堆内存增长。
- 能解释为什么 `ThreadLocal` 的 Entry 的 key 是弱引用，value 是强引用，以及这样设计的意图和副作用。
- 能使用 `InheritableThreadLocal` 在线程池中正确传递上下文，并了解其局限性。
- 能自信回答面试中“ThreadLocal 使用后为什么要 remove”等高频追问。

---

## 📝 练习1：基础用法——正确与错误使用 ThreadLocal（必做）

### 业务场景
在 Web 项目中，你使用 `ThreadLocal` 存储当前请求的用户信息（如用户 ID），以方便 DAO 层获取。但如果忘记在请求结束时清理，可能会导致严重的问题。

### 你的任务
1. 创建一个 `UserContext` 类，包含 `ThreadLocal<Long> currentUserId`。
2. 在一个线程中设置用户 ID，模拟处理请求，然后（正确做法）调用 `remove()` 清除。
3. 不使用 `remove()` 的情况，线程结束后（但线程可能在线程池中复用），查看效果。
4. 使用 `ThreadLocal.withInitial(...)` 支持默认值。
5. 解释 `ThreadLocalMap` 是存储在 `Thread` 对象里的，同一个 `ThreadLocal` 实例可以在不同线程中存储不同的值。

### ⚡ 关键提示
- `ThreadLocal.get()` 若之前未设置值且未提供初始值，返回 `null`。
- 每个线程持有一个 `ThreadLocalMap`，其中 Entry 的 key 是 `ThreadLocal` 弱引用，value 是强引用。所以如果 `ThreadLocal` 对象失去强引用，key 可能被 GC，但 value 依然存在 -> 内存泄漏。
- `remove()` 会彻底清除 key 为 null 的 Entry，以及当前 key 对应的 Entry。
- 在 Tomcat 等线程池环境中，线程复用，如果不 `remove()`，该线程再次处理其他请求时会看到旧值（脏数据）。

### ✍️ 动手写代码
```java
public class UserContext {
    public static final ThreadLocal<Long> userId = new ThreadLocal<>();

    // 使用：
    public static void set(Long id) { userId.set(id); }
    public static Long get() { return userId.get(); }
    public static void clear() { userId.remove(); }
}

// 模拟请求处理
public void handleRequest(long userId) {
    UserContext.set(userId);
    try {
        // 业务逻辑...
        System.out.println("当前用户: " + UserContext.get());
    } finally {
        UserContext.clear(); // 必须清理
    }
}
```

### ✅ 自我检查
- [ ] 忘记 `remove()` 的线程，在下次请求是否会读到旧值？
- [ ] 线程被回收后，`ThreadLocalMap` 中的 Entry 还在吗？
- [ ] `withInitial` 是否避免了 NPE？

### 📖 参考实现（直接展示）

```java
public class ThreadLocalDemo {
    static final ThreadLocal<String> CONTEXT = ThreadLocal.withInitial(() -> "default");

    public static void main(String[] args) throws InterruptedException {
        Runnable task = () -> {
            System.out.println(Thread.currentThread().getName() + " 初始值: " + CONTEXT.get());
            CONTEXT.set("Hello");
            System.out.println(Thread.currentThread().getName() + " 设置后: " + CONTEXT.get());
            // 故意不 remove
        };
        Thread t1 = new Thread(task);
        t1.start();
        t1.join(); // 线程结束，但如果是线程池，线程不会死

        // 主线程
        System.out.println("主线程: " + CONTEXT.get()); // 仍然 default，不受影响
        CONTEXT.set("World");
        System.out.println("主线程修改后: " + CONTEXT.get());
        CONTEXT.remove();
    }
}
```

**设计思路**  
- `ThreadLocal.withInitial` 确保即使没有 `set`，`get` 也不会返回 null，提供了默认工厂。  
- 每个线程对同一个 `ThreadLocal` 实例操作互不干扰，因为值存在线程自身的 `ThreadLocalMap` 里。  
- 如果线程来自线程池且未调用 `remove()`，则该线程下次执行其他任务时将保留上次设置的值，产生脏数据。  
- 所以 `remove()` 是必须的良好实践。

### 🐞 常见错误预警
- 误以为 `ThreadLocal` 是全局变量：实际上它只是 key，数据存在线程内部。
- 用 `InheritableThreadLocal` 传递上下文到子线程，但子线程修改不会影响父线程。
- 在线程池线程复用时，`InheritableThreadLocal` 只在线程创建时传递一次，后续复用不会再传递，需要改用阿里 `TransmittableThreadLocal` 或手动 copy。

---

## 📝 练习2：中级用法——故意制造内存泄漏并观察

### 业务场景
在线程池环境下，`ThreadLocal` 实例本身不再被引用（比如类卸载），但线程由于复用一直存在，会导致 entry 中的 value 无法被 GC，造成内存泄漏。你将在测试中复现并亲眼看到堆内存上升。

### 你的任务
1. 创建一个线程池（固定大小 1）。
2. 定义一个较大的对象（如 `byte[10 * 1024 * 1024]`）作为 value。
3. 每次往线程池提交任务时，使用一个新的 `ThreadLocal` 实例设置这个大对象，并记下引用，但很快失去对 `ThreadLocal` 实例的强引用（比如局部变量结束）。
4. 在任务内**不调用 `remove()`**。
5. 持续提交多次，使用 `jstat -gc` 或 `jmap -histo:live` 观察老年代/堆使用不断上升，最终 OOM 或 GC 频繁。
6. 验证：如果在线程池任务最后调用 `remove()`，堆使用量保持稳定。

### ⚡ 关键提示
- 因为 `ThreadLocal` 实例失去强引用（变成 WeakReference 且可能被 GC），但线程的 Map 中 value 仍然是强引用，所以无法回收。
- 可以使用 `-Xmx100M` 限制堆大小以便快速复现。
- 另一个观察方法：调用 `System.gc()` 后查看 `ThreadLocalMap` 中是否存在 key 为 null 的 Entry（通过反射查看 `Thread.threadLocals`）。
- 为了触发清理，可在每次提交后手工调用 `System.gc()`（仅试验，生产勿用），但也不会清理 value，除非调用 `ThreadLocal` 的 `expungeStaleEntry` 系列方法，而这些方法只在 `get`/`set`/`remove` 中惰性触发。

### ✍️ 动手写代码
```java
ExecutorService pool = Executors.newFixedThreadPool(1);
for (int i = 0; i < 100; i++) {
    // 每次创建新的 ThreadLocal，并失去引用
    ThreadLocal<byte[]> tl = new ThreadLocal<>();
    tl.set(new byte[10 * 1024 * 1024]); // 10MB
    pool.execute(() -> {
        // 使用 tl，但不 remove
        System.out.println("task done");
    });
}
// 不断提交...
```

### ✅ 自我检查
- [ ] 不加 `remove()` 时，堆内存是否持续上涨？
- [ ] 调用 `System.gc()` 后，Entry key 弱引用被清理，但 value 是否仍然存在？
- [ ] `get()` 或 `set()` 是否有时能清理部分脏 Entry？为什么还是在泄漏？
- [ ] 如果主动 `remove()` 后，是否立即释放内存？

### 📖 参考实现（直接展示）

```java
import java.util.concurrent.*;

public class ThreadLocalLeak {
    public static void main(String[] args) throws InterruptedException {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>()
        );
        // 限制堆大小: -Xmx64M
        for (int i = 0; i < 1000; i++) {
            int finalI = i;
            // 每次循环创建新的 ThreadLocal 实例，使用后即丢失强引用
            ThreadLocal<byte[]> tl = new ThreadLocal<>();
            tl.set(new byte[5 * 1024 * 1024]); // 5 MB 对象
            pool.execute(() -> {
                System.out.println("任务 " + finalI + " 执行, 值大小: " + tl.get().length);
                // 故意不调用 tl.remove()
            });
            Thread.sleep(20); // 给 GC 一点时间
        }
        System.out.println("任务提交完毕，观察堆内存...");
        // 此时你会发现 OOM 或频繁 GC
        pool.shutdown();
    }
}
```

**设计思路**  
- 每个任务内使用一个全新 `ThreadLocal` 实例，这样 `ThreadLocalMap` 中会不断累积 key 为 null 的 Entry，但 value 仍被线程强引用。  
- 因为没有调用 `remove`，也没有后续对该 `ThreadLocal` 的 `get`/`set` 操作（这些操作可能触发惰性清理），所以脏 Entry 越积越多，最终内存泄露。  
- 实际项目中如果 `ThreadLocal` 是静态变量，key 永远不会被回收，则不会出现 key null 的 entry，但如果忘记 remove，value 依然会像强引用一样存在直到线程结束。

### 🐞 常见错误预警
- 认为 `ThreadLocal` 使用后会自己清理：不是，必须显式 `remove()` 或线程死亡。
- 把大对象放进 ThreadLocal 且永不清理，导致内存浪费。
- 复杂的继承关系中，子线程复制了父线程的 InheritableThreadLocal 值，但未在自己的 finally 中清理。

---

## 📝 练习3：高级/探索用法——InheritableThreadLocal 与线程池上下文丢失问题

### 业务场景
你的服务在接收到请求后，主线程将 traceId 放入 `InheritableThreadLocal`，然后异步线程（线程池）需要打印相同的 traceId。但你发现线程池里的线程往往拿到的是旧的 traceId 或者 null。需要理解其原因并给出解决方案。

### 你的任务
1. 使用 `InheritableThreadLocal` 存储 traceId，主线程设置，然后提交任务给线程池。观察子线程是否能获取到（第一次创建线程时可以，但线程复用后可能获取的是旧值）。
2. 演示问题：主线程多次设置不同 traceId，线程池线程复用时却读到上一个请求的 traceId。
3. 给出解决方案：使用阿里巴巴开源的 `TransmittableThreadLocal`（TTL）或自己写包装器在线程池提交时复制上下文。
4. 实现一个简化版的 `TransmittableThreadLocal`，核心思路：在 `execute` 时捕获当前上下文，并在任务执行前设置、执行后清理。

### ⚡ 关键提示
- `InheritableThreadLocal` 原理：在线程创建时，子线程会将父线程的 `inheritableThreadLocals` 全部复制一份。但如果线程复用，只在首次创建时复制。
- 利用装饰器模式包装 `Runnable`：在执行前将父线程的 `ThreadLocal` 值 set 到子线程，执行后 `remove`。
- 实际推荐直接用 TTL，而不要自己造。

### ✍️ 动手写代码
```java
public class InheritableThreadLocalIssue {
    static InheritableThreadLocal<String> traceId = new InheritableThreadLocal<>();

    public static void main(String[] args) {
        ExecutorService pool = Executors.newFixedThreadPool(1);
        for (int i = 0; i < 3; i++) {
            String trace = "trace-" + i;
            traceId.set(trace);
            pool.execute(() -> {
                System.out.println("子线程获取 traceId: " + traceId.get());
            });
            // 期望子线程输出 trace-0, trace-1, trace-2
            // 实际输出可能都是 trace-0（因为线程复用，复制只在第一次发生）
        }
    }
}
```

### ✅ 自我检查
- [ ] 线程池下 `InheritableThreadLocal` 传递是否正确？为什么？
- [ ] 自己实现的包装器是否在线程池的每次任务中都正确捕获了上下文？
- [ ] 是否理解 TTL 比 `InheritableThreadLocal` 强在哪里？

### 📖 参考实现：自定义上下文传递包装器（直接展示）

```java
import java.util.Map;
import java.util.concurrent.*;

public class ContextCopyingDecorator {
    // 保存 ThreadLocal 实例，这里演示一个
    static InheritableThreadLocal<String> tl = new InheritableThreadLocal<>();

    static class ContextRunnable implements Runnable {
        private final Runnable task;
        private final String contextValue; // 也可以捕获多个
        ContextRunnable(Runnable task) {
            this.task = task;
            this.contextValue = tl.get(); // 在提交线程中捕获
        }
        @Override
        public void run() {
            tl.set(contextValue); // 设置到执行线程
            try {
                task.run();
            } finally {
                tl.remove(); // 清理
            }
        }
    }

    public static ExecutorService wrap(ExecutorService pool) {
        return new AbstractExecutorService() {
            // ... 委托所有方法，并在 execute 时包装
            @Override
            public void execute(Runnable command) {
                pool.execute(new ContextRunnable(command));
            }
            // 省略其他方法
        };
    }
}
```

**设计思路**  
- 在提交任务时捕获父线程的 `InheritableThreadLocal` 值，序列化到任务对象中。  
- 任务执行时先恢复到当前线程的 `ThreadLocal`，执行结束清除，避免脏数据。  
- 这样即使线程池线程复用，每次任务也都能获得正确的上下文。  
- 生产环境建议使用成熟的 TTL 库，它会更完善地处理 `Callable`、`Timer` 等。

### 🐞 常见错误预警
- 忘记在 finally 中 remove，导致子线程的 ThreadLocal 污染。
- 捕获上下文时使用了浅拷贝，如果存的是对象引用，仍可能被修改，需要深拷贝或不可变对象。
- 对原生 `ExecutorService` 包装时，要注意覆盖所有提交方法（`submit`, `invokeAll` 等）。

---

## 🏢 大厂场景实战：全链路跟踪系统中 ThreadLocal 的设计

### 场景描述
你的公司要做一个全链路追踪系统，每个请求进入网关时生成全局 `traceId`，并在调用的所有下游服务、数据库、缓存、线程池中传递。要求：
- 在 HTTP 请求处理、MQ 消费、定时任务等不同入口处设置。
- 线程池异步任务中必须能够获取到正确的 `traceId`。
- 不能对业务代码侵入太强。

### 约束条件
- 框架：Spring Boot + 自定义 Starter
- 线程池：多个业务线程池
- 需要自动传递，不可要求开发者手动设置。

### 你的设计任务
请给出设计方案，包括核心类、过滤器/拦截器、线程池装饰器的实现思路。

### 常见方案参考（直接展示）
- 统一 `TraceContext` 类持有 `ThreadLocal` 或者用 TTL。
- 在 Web 层使用 `Filter` 或 `Interceptor` 提取请求头的 `traceId` 并设置。
- 在 `@Async` 或自定义线程池处，通过 `TaskDecorator` 或者包装 `Executor` 复制上下文。
- 在 RPC 框架（Feign/Dubbo）的过滤器中将 `traceId` 透传。
- 日志框架（Logback/Log4j2）中配置 `%traceId` 占位符，通过 MDC 或自定义 Converter 读取。

---

## 🏆 大厂面试题

### 面试题1：为什么 ThreadLocal 的 Entry 的 key 设计成 WeakReference？
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：

- **原因**：如果 key 是强引用，一旦外部的 `ThreadLocal` 实例不再使用（没有强引用），由于 `ThreadLocalMap` 还持有该 key 的强引用，会导致 `ThreadLocal` 对象无法被 GC，因而 Entry 整个无法回收，造成内存泄漏。
- **使用弱引用**：外部的 `ThreadLocal` 没有强引用后，GC 会回收它，使 Entry 的 key 变为 null。这样在后续对 `ThreadLocal` 的 `get`/`set`/`remove` 操作中，可以惰性检测到 key 为 null 的 Entry 并清除它们。
- **副作用**：value 仍是强引用，所以如果线程不死且不调用上述方法，value 依然泄漏。这就是为什么必须手动 `remove()`。
- **常见追问**：“那 value 为什么不用弱引用？” 因为 value 是业务数据，没有其他强引用，如果 value 是弱引用，很可能在 get 之前就被 GC 了，业务无法正常使用。
- **易错提醒**：很多人以为弱引用 key 就万事大吉，忽略了惰性清理的条件。
- **自我反思**：你是否在项目中每次使用 ThreadLocal 都在 finally 块中 remove？如果有一个工具能自动检测，会减少多少隐患？

---

### 面试题2：在线程池环境中使用 ThreadLocal 为什么需要 remove？具体会引发什么问题？
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：
- **脏数据**：线程池的线程处理完一个任务后，如果不清除 ThreadLocal，该线程处理下一个任务时仍会看到上一个任务设置的值，导致数据错乱（比如用户 A 看到了用户 B 的信息）。
- **内存泄漏**：线程生命周期长，ThreadLocalMap 中的 Entry 会随任务不断增加，导致内存持续上升（尤其是 value 是大对象时）。
- **清理时机**：必须在每个任务的 `finally` 块中显式 `remove()`。
- **常见追问**：“如果 ThreadLocal 是 static 的，remove 能解决问题吗？” 完全可以，remove 会将当前线程对应的 Entry 彻底清除，key 仍然是 static 的，下次 set 会重新创建 Entry。
- **易错提醒**：有些开发者用 ThreadLocal 的 `set(null)` 代替 `remove()`，这不清洁 entry，key 仍存在，只是 value 为 null，不算彻底清除。
- **自我反思**：阿里规约强制要求线程池中的 ThreadLocal 必须 remove，你所在项目是否能通过静态检查保证？

---

### 面试题3：`InheritableThreadLocal` 的实现原理？在线程池下有什么缺陷？如何解决？
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：

- **原理**：`Thread` 对象还有一个 `inheritableThreadLocals` 字段。当创建新线程时，`Thread.init()` 方法中会将父线程的 `inheritableThreadLocals` 深拷贝一份给子线程。具体可见 `ThreadLocal.createInheritedMap`。
- **缺陷**：拷贝时机是在线程**创建**时。线程池的线程是复用的，只在首次创建时拷贝一次父线程的上下文，之后线程再复用时不会重新拷贝，导致上下文不是最新。
- **解决方案**：
  1. 阿里的 `TransmittableThreadLocal` (TTL)，在使用线程池时结合 `TtlRunnable` 包装，能在每次任务执行前捕获并设置上下文。
  2. 自定义 `TaskDecorator` 或装饰器模式包装 `Runnable`，在提交时捕获，执行时恢复。
- **常见追问**：“如果不用 TTL，怎么手动做到类似效果？” → 在 `Runnable` 中获得当前 traceId，然后在线程中重新设置。
- **易错提醒**：不能依赖 `InheritableThreadLocal` 在 Executors 中传递上下文，这是一个经典错误。
- **自我反思**：全链路追踪系统中，你是直接用了 Sleuth 还是自己实现了 TraceContext？它如何跨线程池传递？

---

### 面试题4：如何定位和排查 ThreadLocal 内存泄漏问题？
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：

- **排查方法**：
  1. 通过 `jmap -histo:live <pid> | grep` 找到大对象或者业务可疑类的实例数。
  2. `jstack <pid>` 寻找长时间存活的线程（如线程池）。
  3. 对怀疑的 ThreadLocal 类，通过反射查看线程的 `ThreadLocalMap` 内容。可以写一段工具代码 dump 所有线程的 ThreadLocal 值。
  4. Heap dump 后用 MAT (Memory Analyzer Tool) 查看 Path to GC Roots，看是否有线程 → ThreadLocalMap → Entry → value 的引用链。
- **关键特征**：Entry 的 `referent` (key) 为 null，但 value 不为 null，且线程存活。
- **预防**：代码规范强约束，使用 Sonar 或 IDE 插件提醒 remove。
- **常见追问**：“有没有办法自动清理？” Java 会在 `ThreadLocal.get/set/remove` 时惰性清理一部分 null key entry，但如果永远不调用这些方法就无效。
- **易错提醒**：使用 `System.gc()` 可以加速弱引用回收用于测试，但生产环境不能依赖。
- **自我反思**：你的项目是否有监控线程池的 ThreadLocal 泄漏？比如利用字节码增强或 JMX 指标。

---

### 面试题5：ThreadLocal 在 Spring 框架中有哪些典型应用？
**难度**：⭐️⭐️⭐️

**参考答案**：
- `RequestContextHolder`：基于 ThreadLocal 保存当前 HTTP 请求的 `RequestAttributes`。
- `TransactionSynchronizationManager`：使用 ThreadLocal 存储当前事务的资源和同步器，如 DB 连接持有者 (`ConnectionHolder`)。
- `LocaleContextHolder`：保存当前线程的 `LocaleContext`。
- `SecurityContextHolder`（Spring Security）：存储当前认证信息，默认策略是 ThreadLocal。
- `DateTimeContextHolder`：Joda-Time 等时间上下文。
- **共同特点**：都提供了 `reset` 或 `remove` 方法，并在请求完成时由过滤器自动清理。
- **常见追问**：“为什么 Spring 能用 ThreadLocal 而不怕泄漏？” 因为它们有严格的清理机制，通常通过 `OncePerRequestFilter` 在 request 结束的 finally 中强制清除。
- **易错提醒**：如果你在 Spring 管理的线程池 @Async 任务中依赖这些 Holder，可能取不到值，因为上下文未传递。需要使用 `DelegatingSecurityContextAsyncTaskExecutor` 等包装。
- **自我反思**：是否清楚项目中 Spring 的 ThreadLocal 清理是在哪个 Filter 做的？如果自定义了 Filter 顺序，是否有清理遗漏风险？

---

> 今天你亲手制造并分析了 ThreadLocal 的内存泄漏，这是无数生产事故的根源。把 `remove()` 刻入肌肉记忆，从此你的并发代码将再无泄漏之忧。明天我们将转向 CAS 实战，自己写一个无锁栈并出击 ABA 问题。





# 第 13 天：CAS 实战：无锁栈 + ABA 复现与解决
本日掌握：用 `Unsafe` 或 `AtomicReference` 实现一个线程安全的无锁栈，亲手制造 ABA 问题并用 `AtomicStampedReference` 解决  
覆盖原理点：15 (CAS 与 ABA 问题)  
阶段：使用期

## 🎯 今日目标
- 能手写一个基于 CAS 的无锁并发栈（Treiber Stack），并测试其线程安全性。
- 能构造一个业务场景，故意制造 ABA 问题，并观察到数据错误或丢失。
- 能使用 `AtomicStampedReference`（或 `AtomicMarkableReference`）解决 ABA 问题，并验证修复后的正确性。
- 能解释 `Unsafe.compareAndSwapInt` 底层（CMPXCHG 指令）以及与 `AtomicInteger` 的关系。
- 能应对面试中关于 CAS 的三大问题：ABA、自旋开销、只能保证一个共享变量的原子性。

---

## 📝 练习1：基础用法——手写基于 CAS 的无锁栈（必做）

### 业务场景
你需要一个高性能的线程安全栈，不允许使用锁（如 `synchronized` 或 `ReentrantLock`），而是依靠 CAS 指令实现无锁并发。它将用于缓存空闲连接池，需要频繁出栈入栈。

### 你的任务
实现一个 `ConcurrentStack<T>` 类，支持 `push(T item)` 和 `T pop()`，满足：
- 使用单向链表存储节点（Node 包含 value 和 next）。
- 使用 `AtomicReference<Node<T>>` 作为栈顶引用 `top`。
- `push`：创建新节点，CAS 将 top 设置为新节点，同时新节点 next 指向当前 top。
- `pop`：获取当前 top，若为空返回 null，否则 CAS 将 top 设为 top.next。
- CAS 操作需要自旋直到成功。
- 线程安全：多个线程并发 push/pop，数据不丢失，无重复或空值错误。
- 不允许使用锁或 `synchronized`。

### ⚡ 关键提示
- `java.util.concurrent.atomic.AtomicReference` 提供 CAS 操作：`compareAndSet(expect, update)`。
- 无锁栈经典算法：Treiber Stack。
- CAS 可能因 ABA 问题出错，但现在先不考虑 ABA，后续练习会专门涉及。
- Node 类可以定义为内部静态类，要有 `final T value` 和 `Node<T> next`。
- 场景：连接池回收，pop 获取空闲连接，push 归还连接。

### ✍️ 动手写代码
```java
import java.util.concurrent.atomic.AtomicReference;

public class ConcurrentStack<T> {
    private static class Node<T> {
        final T value;
        Node<T> next;
        Node(T value) { this.value = value; }
    }
    private final AtomicReference<Node<T>> top = new AtomicReference<>();

    public void push(T item) {
        Node<T> newNode = new Node<>(item);
        Node<T> oldTop;
        do {
            oldTop = top.get();
            newNode.next = oldTop;
        } while (!top.compareAndSet(oldTop, newNode));
    }

    public T pop() {
        Node<T> oldTop;
        Node<T> newTop;
        do {
            oldTop = top.get();
            if (oldTop == null) return null;
            newTop = oldTop.next;
        } while (!top.compareAndSet(oldTop, newTop));
        return oldTop.value;
    }
}
```

### ✅ 自我检查
- [ ] 多个线程同时 push 1000 个元素，最后 pop 出的总数是否等于总 push 数？
- [ ] pop 空栈是否返回 null 而不是抛出异常？
- [ ] 并发 pop 时，是否可能出现两个线程 pop 到同一个元素？
- [ ] 执行期间没有使用任何锁，CPU 自旋是否可控？

### 📖 参考实现（直接展示）

完整的可运行测试：

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class TreiberStack<T> {
    static final class Node<T> {
        final T value;
        Node<T> next;
        Node(T value) { this.value = value; }
    }

    private final AtomicReference<Node<T>> top = new AtomicReference<>();

    public void push(T value) {
        Node<T> node = new Node<>(value);
        Node<T> oldTop;
        do {
            oldTop = top.get();
            node.next = oldTop;
        } while (!top.compareAndSet(oldTop, node));
    }

    public T pop() {
        Node<T> oldTop, newTop;
        do {
            oldTop = top.get();
            if (oldTop == null) return null;
            newTop = oldTop.next;
        } while (!top.compareAndSet(oldTop, newTop));
        return oldTop.value;
    }

    public boolean isEmpty() {
        return top.get() == null;
    }

    // 测试
    public static void main(String[] args) throws InterruptedException {
        TreiberStack<Integer> stack = new TreiberStack<>();
        int threads = 4;
        int perThread = 10000;
        CountDownLatch latch = new CountDownLatch(threads);
        // 并发push
        for (int t = 0; t < threads; t++) {
            final int start = t * perThread;
            new Thread(() -> {
                for (int i = 0; i < perThread; i++) {
                    stack.push(start + i);
                }
                latch.countDown();
            }).start();
        }
        latch.await();
        // 统计 pop 的数量
        int count = 0;
        Integer v;
        while ((v = stack.pop()) != null) count++;
        System.out.println("Total pushed: " + (threads * perThread) + ", popped: " + count);
    }
}
```

**设计思路**  
- `push` 和 `pop` 都使用典型的 CAS 自旋模式：获取当前快照，构造期望的新值，CAS 尝试更新，失败则重试。  
- `AtomicReference` 扮演了 `volatile` + CAS 的角色，保障可见性和原子更新。  
- 注意 `node.next = oldTop` 在 CAS 之前设定，如果 CAS 失败，`node` 会被丢弃，这是安全的，因为它是线程局部对象。  
- 这种无锁栈在低到中竞争时性能极佳，但高竞争时自旋可能会消耗 CPU，此时 `LongAdder` 之类的优化或等待策略需要考虑。

### 🐞 常见错误预警
- **错误**：`pop` 中先 `top.get()` 再 `top.compareAndSet`，期间栈可能已经被其他线程更改（可能有元素被 push 或 pop），但 CAS 会检查到 `oldTop` 与当前 top 不同而重试。这是正确的。但如果 pop 拿到 `oldTop` 后，读取 `oldTop.next` 前，该节点可能被其他线程 pop 并复用（内存重用），这就会产生 ABA 问题。今天的栈暂不处理 ABA。
- **错误**：`push` 中 `newNode.next = oldTop` 后，CAS 失败，但 newNode 仍被丢弃，下一次重试会用新的 `newNode`（因为 `oldTop` 变了），所以旧 newNode 只是被 GC 回收，不会影响栈。这是正确的。
- **错误**：忘记 `pop` 判空，在并发下 `top.get()` 可能在判空后变为非空，或者反之。CAS 会正确处理，因为 CAS 期望值就是 `oldTop`，如果 `oldTop` 被修改，CAS 失败重试。

---

## 📝 练习2：中级用法——构造 ABA 问题并复现

### 业务场景
你的无锁栈在高并发下运行良好，但某天运维反馈弹出了重复的节点（同一个对象出现两次）。经过日志分析，怀疑是 ABA 问题。你需要复现这个经典问题。

### 你的任务
1. 在无锁栈中故意制造 ABA 场景：线程 1 准备 pop 节点 A，但在 CAS 之前被挂起。线程 2 在此期间将 A pop 出来，然后又 push 了一个新的 B，接着再次修改某个指针或复用节点 A 的内存（例如将 A 的 value 改变后重新 push）。线程 1 醒来后继续 CAS，它看到的 top 依然是 A（因为 A 又被 push 了回来），CAS 成功，但此时 A 的 next 可能已经被更改，导致栈结构破坏。
2. 使用 `AtomicStampedReference` 或 `AtomicMarkableReference` 保证不仅检查引用，还检查版本号（时间戳），来解决 ABA。
3. 修改栈实现，用 `AtomicStampedReference<Node<T>>` 替换 `AtomicReference`，push 和 pop 时携带时间戳，每次修改 CAS 时都要求时间戳匹配并同时更新。
4. 编写测试，使用多线程和人为的延时（如 `Thread.sleep` 或 `CountDownLatch` 控制时序）来复现 ABA 问题，并展示未加版本号时出现错误，加入版本号后正确。

### ⚡ 关键提示
- ABA 问题：CAS 只比较当前值与期望值是否相等，但一个值从 A 变为 B 再变回 A 的过程，CAS 无法察觉。这在涉及链表等动态内存重用的结构中尤其危险。
- `AtomicStampedReference` 内部维护 `pair` (reference, int stamp)。`compareAndSet(V expectedReference, V newReference, int expectedStamp, int newStamp)`。
- 要复现 ABA，需要精确控制线程调度，通常在线程内部设置 `Thread.sleep` 或在安全点用锁（违背无锁初衷，仅用于测试）制造窗口。可以设计如下场景：
  - 初始栈：A -> B
  - 线程1 读 top = A，next = B，准备 CAS 替换 top 为 B，但在 CAS 前暂停。
  - 线程2 pop A，pop B，然后 push A 回来（值可能是新对象或原 A），此时栈顶又是 A，但 next 可能已变（原来 A.next 是 B，现在 A.next 是 null 或别的）。
  - 线程1 CAS 将 top 从 A 改为 B（`oldTop=A, newTop=B`），成功！因为 top 依然是 A。但此时栈中实际上不存在 B（B 已被线程2 pop 并丢弃），导致 B 又被错误地压回栈，且 B 的 next 可能是旧值等，引发错误。
- 用 `AtomicStampedReference`，每次修改 stamp 加 1，这样线程1 看到的 top 虽然等于 A，但 stamp 已经变了，CAS 检查 stamp 不匹配而失败。

### ✍️ 动手写代码
```java
// 使用 AtomicStampedReference 实现栈
public class ABAFreeStack<T> {
    private static class Node<T> {
        final T value;
        Node<T> next;
        Node(T value) { this.value = value; }
    }
    private final AtomicStampedReference<Node<T>> top = new AtomicStampedReference<>(null, 0);

    public void push(T value) {
        Node<T> node = new Node<>(value);
        int[] stampHolder = new int[1];
        Node<T> oldTop;
        do {
            oldTop = top.get(stampHolder);
            node.next = oldTop;
        } while (!top.compareAndSet(oldTop, node, stampHolder[0], stampHolder[0] + 1));
    }

    public T pop() {
        int[] stampHolder = new int[1];
        Node<T> oldTop, newTop;
        do {
            oldTop = top.get(stampHolder);
            if (oldTop == null) return null;
            newTop = oldTop.next;
        } while (!top.compareAndSet(oldTop, newTop, stampHolder[0], stampHolder[0] + 1));
        return oldTop.value;
    }
}
```

### ✅ 自我检查
- [ ] 使用普通 `AtomicReference` 栈，在复现场景下是否出现了数据不一致或弹出预期外的元素？
- [ ] 使用 `AtomicStampedReference` 后，同样场景是否不会出现错误？
- [ ] 版本号是否每次 push/pop 都正确递增？
- [ ] 能否画出 ABA 问题的详细时序图？

### 📖 参考实现与复现场景（直接展示）

**复现 ABA 的测试**（可能需要 JDK8 特定环境，主要是理解概念）：

```java
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicStampedReference;

public class ABADemo {
    static class Node {
        int value;
        Node next;
        Node(int v) { value = v; }
    }

    public static void main(String[] args) throws InterruptedException {
        // 使用标准栈（无版本号）
        TreiberStack<Node> stack = new TreiberStack<>();
        Node a = new Node(1);
        Node b = new Node(2);
        stack.push(b);
        stack.push(a); // 栈顶 a -> b

        Thread t1 = new Thread(() -> {
            Node top = stack.top.get(); // a
            Node next = top.next;       // b
            // 暂停，让线程2操作
            try { Thread.sleep(100); } catch (InterruptedException e) {}
            // 此时 top 可能还是 a（因为线程2把 a 又 push 回来了）
            boolean ok = stack.top.compareAndSet(top, next); // 期望 a，改为 b
            System.out.println("Thread1 CAS result: " + ok);
        });
        Thread t2 = new Thread(() -> {
            Node popped1 = stack.pop(); // 弹出 a
            Node popped2 = stack.pop(); // 弹出 b
            stack.push(popped1);        // 重新 push a （此时栈顶 a，next 为 null）
            // 栈顶变为 a -> null
        });
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        // 打印栈内容
        System.out.print("Stack: ");
        Node cur = stack.top.get();
        while (cur != null) {
            System.out.print(cur.value + " ");
            cur = cur.next;
        }
        // 可能看到错误的内容，比如包含 b，但 b 已经被 pop 掉
    }
}
```

之后将 `TreiberStack` 的 `AtomicReference` 替换成 `AtomicStampedReference`，并增加 stamp 操作，再次运行，会看到 CAS 失败，栈结构保持正确。

**设计思路**  
- 人为制造延时让线程2有机会执行，复现经典ABA。  
- 带版本的 CAS 通过 stamp 捕捉到状态变化，使 CAS 失败，避免错误更新。  
- 实际上 JDK 的 `ConcurrentLinkedQueue` 等都没有使用 `AtomicStampedReference`，而是通过其他方法避免ABA（如使用不断新增节点，不重用对象）。因为 `AtomicStampedReference` 需要额外存储 stamp，且 CAS 更复杂。

### 🐞 常见错误预警
- 在复现 ABA 时，直接用相同对象 a 重新 push，使得 top 引用又变回 a。注意栈里之前 a 的 next 可能已经改变（例如 a.next 原为 b，线程2 pop b 后没有改变 a.next 仍然指向 b）。但线程2在 push a 时，`node.next = oldTop`，如果 oldTop 是 null，则 a.next 被设置为 null，覆盖了原来的 b 引用。这样 a 的 next 就变了，这就是产生的隐患。
- 使用 `AtomicStampedReference` 时，每次 CAS 都需要提供期望的时间戳，而时间戳需要从当前 `top` 获取，通过 `get(stampHolder)` 方法同时返回引用和时间戳。

---

## 📝 练习3：高级/探索用法——CAS 的自旋性能与 `LongAdder` 对比

### 业务场景
你已经理解了 CAS 和 ABA。现在需要探究 CAS 在高竞争下的自旋开销，并对比使用 `AtomicLong` 和 `LongAdder` 在递增计数器时的吞吐量。

### 你的任务
1. 编写一个基准测试：多个线程对一个 `AtomicLong` 进行 `incrementAndGet`，记录总耗时。
2. 同样使用 `LongAdder` 进行 `increment`，记录耗时并最后调用 `sum()`。
3. 在不同线程数（1, 2, 4, 8, 16）下测试，用 `CountDownLatch` 控制同时开始，比较吞吐量。
4. 解释为什么 `LongAdder` 在高竞争下更快（热点分散）。
5. 思考 CAS 自旋在什么情况下会成为瓶颈，以及如何优化（如使用 `Unsafe` 结合 `yield` 或 `sleep`）。

### ⚡ 关键提示
- `AtomicLong` 单一变量，所有线程 CAS 竞争同一个内存地址，失败频繁。
- `LongAdder` 内部有一个 base 和 Cell 数组，每个线程映射到不同的 Cell 进行 CAS 加法，极大减少竞争。
- 测试时务必确保线程同时运行，使用 `CyclicBarrier` 或 `CountDownLatch`。
- 如果想看自旋，可以读取 `AtomicLong` 失败次数（需自定义工具），但简单对比耗时即可。

### ✍️ 动手写代码
```java
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class CASPerformance {
    public static void main(String[] args) throws Exception {
        for (int threads : new int[]{1, 2, 4, 8, 16}) {
            testAtomicLong(threads, 1_000_000);
            testLongAdder(threads, 1_000_000);
        }
    }

    private static void testAtomicLong(int nThreads, int iterations) throws InterruptedException {
        AtomicLong counter = new AtomicLong();
        ExecutorService pool = Executors.newFixedThreadPool(nThreads);
        CountDownLatch latch = new CountDownLatch(nThreads);
        long start = System.nanoTime();
        for (int i = 0; i < nThreads; i++) {
            pool.execute(() -> {
                for (int j = 0; j < iterations; j++) counter.incrementAndGet();
                latch.countDown();
            });
        }
        latch.await();
        long end = System.nanoTime();
        System.out.printf("AtomicLong (%2d threads): %10d ops in %6d ms%n", nThreads, nThreads * iterations, (end - start) / 1_000_000);
        pool.shutdown();
    }

    private static void testLongAdder(int nThreads, int iterations) throws InterruptedException {
        LongAdder adder = new LongAdder();
        ExecutorService pool = Executors.newFixedThreadPool(nThreads);
        CountDownLatch latch = new CountDownLatch(nThreads);
        long start = System.nanoTime();
        for (int i = 0; i < nThreads; i++) {
            pool.execute(() -> {
                for (int j = 0; j < iterations; j++) adder.increment();
                latch.countDown();
            });
        }
        latch.await();
        long end = System.nanoTime();
        System.out.printf("LongAdder  (%2d threads): %10d ops in %6d ms, sum=%d%n", nThreads, nThreads * iterations, (end - start) / 1_000_000, adder.sum());
        pool.shutdown();
    }
}
```

### ✅ 自我检查
- [ ] 是否随着线程数增加，`AtomicLong` 的耗时大幅增长？
- [ ] `LongAdder` 在高线程下是否保持较低耗时？
- [ ] `LongAdder` 的 `sum()` 是否返回正确总和？
- [ ] 你能否解释 `LongAdder` 是如何通过 cell 分散热点的？

---

## 🏢 大厂场景实战：无锁队列实现

### 场景描述
一个金融交易系统，订单撮合需要极低延迟的无锁队列。使用 CAS 实现一个简单的无锁有界循环队列（数组+头尾指针 CAS）。线程安全，不能使用锁。

### 约束条件
- 队列容量固定（如 1024）。
- 支持入队、出队，并发线程数可能为 4。
- 不允许使用 `BlockingQueue`，必须基于 CAS 自旋。

### 你的设计任务
写出核心数据结构 `ConcurrentArrayQueue<T>`，包含 `offer(T)` 和 `poll()`，使用 `AtomicInteger` 或 `AtomicLong` 作为头尾指针，并处理满和空的情况。

### 常见方案参考
- 使用两个 `AtomicInteger`：`head` 和 `tail`。
- CAS 自旋：`offer` 时获取当前 tail，如果 (tail - head) == capacity，则队列满返回 false；否则 CAS 设置 tail+1，成功后将元素放入 slot[tail % capacity]。
- 注意指令重排序，需要变量可见性，可使用 `AtomicIntegerArray` 存储元素引用，或者使用 `volatile` 数组 + `Unsafe`。
- 一种简化实现：基于 `AtomicReferenceArray` 存储，元素存取用 `get`/`set`（volatile），头尾指针 CAS。

---

## 🏆 大厂面试题

### 面试题1：什么是 CAS？它的底层原理是什么？有哪些缺点？
**难度**：⭐️⭐️⭐️

**参考答案**：
- **CAS (Compare-And-Swap)**：一条原子指令，包含三个操作数：内存位置 V、预期的旧值 A、新值 B。如果 V 处的值等于 A，则将该位置的值更新为 B，否则不做任何操作。无论是否成功，都会返回 V 的旧值。
- **底层原理**：在 x86 CPU 上对应 `cmpxchg` 指令。JVM 通过 `Unsafe.compareAndSwapInt()` 等方法提供，这些方法直接调用 native 代码，映射到 CPU 原子指令。对于对象引用有 `compareAndSwapObject`。
- **缺点**：
  1. **ABA 问题**：值从 A 变成 B 再变回 A，CAS 无法感知。
  2. **自旋开销**：CAS 失败时通常自旋重试，高竞争下会消耗 CPU。
  3. **只能保证一个共享变量的原子操作**：不能同时 CAS 多个变量（除非用 `AtomicReference` 包装对象或使用锁）。
- **常见追问**：“Java 的 `synchronized` 底层也用到 CAS 吗？” 是的，轻量级锁和偏向锁的撤销/膨胀过程使用 CAS 操作对象头。
- **自我反思**：能否手写一段汇编伪代码展示 CAS 的流程？

### 面试题2：什么是 ABA 问题？Java 中如何解决？
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：
- **ABA 问题**：线程 1 读取变量 A，在计算新值前，线程 2 将 A 改为 B，然后又改回 A。线程 1 执行 CAS 时发现值仍为 A，CAS 成功。但状态实际上经过了 B，可能导致不一致。
- **解决**：使用版本号或时间戳。`AtomicStampedReference` 同时维护引用和整数 stamp；`AtomicMarkableReference` 维护一个布尔标志。CAS 更新时同时比较引用和 stamp，避免 ABA。
- **常见追问**：“为什么 `ConcurrentLinkedQueue` 没有用 `AtomicStampedReference`？” 因为它通过**节点生命周期管理**避免 ABA：节点一旦被踢出队列则会被 GC 或永不重用，因此不会出现 A 被重用的情况。
- **易错提醒**：在某些内存管理自行处理的语言（如 C++）中，ABA 可能会导致指针悬空等严重后果；Java 的 GC 避免了部分 ABA，但如果逻辑上重用对象（如内存池），则必须考虑 ABA。
- **自我反思**：你能否写一个 ABA 问题的实际 bug 案例，并演示修复？

### 面试题3：`AtomicInteger` 和 `LongAdder` 的区别？分别适用于什么场景？
**难度**：⭐️⭐️⭐️

**参考答案**：
- **区别**：`AtomicInteger` 所有操作基于单个 `volatile int` 字段和 CAS，高并发时大量 CAS 自旋失败，性能下降。`LongAdder` 内部使用 base + Cell 数组，将热点分散到多个 cell 上，多个线程并发更新时映射到不同 cell 进行 CAS 或简单的加法（如 `Unsafe` 直接加），仅在获取值 `sum()` 时才合并，因此写吞吐量高。
- **适用**：`AtomicInteger` 适合低并发或需要精确瞬时值的场景（如序列号生成器）。`LongAdder` 适合高并发统计计数（如 QPS 计数器、访问量统计），但对实时精确值要求不高（`sum()` 是合并的快照）。
- **常见追问**：“`LongAccumulator` 呢？” 它是更通用的累加器，可以自定义累加函数。
- **易错提醒**：`LongAdder` 的 `sum()` 在进行累加时不会有并发锁，所以返回的值可能不是精确的当前值。
- **自我反思**：在你的项目中，那些使用 `AtomicInteger` 的地方是否可以用 `LongAdder` 优化？

### 面试题4：CAS 自旋失败过多会有什么影响？如何优化？
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：
- **影响**：自旋过多导致 CPU 空转，浪费处理器时间，可能拖慢其他线程，系统吞吐量下降。在严重竞争下，甚至引发“总线风暴”。
- **优化手段**：
  1. **减少竞争**：使用分散热点（如 `LongAdder` 的分段思想）。
  2. **自适应自旋**：JVM 的 synchronized 轻量级锁采取自适应自旋，根据历史成功概率动态决定自旋次数。
  3. **退避策略**：在 CAS 循环中加入 `Thread.yield()` 或短暂的 `LockSupport.parkNanos()`，避免空耗 CPU。
  4. **改用其他同步机制**：对于复杂数据结构，无锁实现复杂度太高时，可以考虑细粒度锁。
- **常见追问**：“JDK 的 `StampedLock` 有使用 CAS 吗？” 是的，它内部用 CAS 更新状态，但同时也结合了排队机制，避免了纯粹自旋。
- **自我反思**：如果让你为轻量级的自旋锁设计一个退避策略，你会怎么做？指数退避、随机退避？

### 面试题5：能否用 CAS 实现一个线程安全的链表？需要注意什么？
**难度**：⭐️⭐️⭐️⭐️⭐️

**参考答案**：

- **可行**：典型的无锁链表如 `ConcurrentLinkedQueue` 使用 CAS 实现。插入和删除的核心是 Cas 操作节点的 `next` 指针或 `item` 字段。
- **注意点**：
  1. **ABA**：如果节点被删除后又可能被重用（如内存池），需要版本号或不重用。Java 的 GC 允许我们通过不再重用已删除节点来规避 ABA。
  2. **多步更新**：链表操作往往需要同时更新两个指针（如删除节点要同时更新前驱的 next 和被删除节点的指针），单个 CAS 无能为力。这时需要使用 `AtomicMarkableReference` 或逻辑删除标记（如 `ConcurrentLinkedQueue` 先将节点 item 置 null，再 CAS 更新 next）。
  3. **迭代器弱一致性**：无锁链表的迭代器一般是弱一致性的，遍历时可能看到不一致的快照。
  4. **内存屏障**：需要保证对节点内部的写入不会被重排到 CAS 发布之后，通常 `volatile` 或 `VarHandle` 提供屏障。
- **常见追问**：“`ConcurrentLinkedQueue` 是如何防止 ABA 的？” 它使用 `AtomicReference` 更新 next 指针，但节点一旦从队列中删除，就不会再被重新链接回队列，因此不会出现同一个引用被再利用的 ABA 问题。
- **自我反思**：尝试阅读 `ConcurrentLinkedQueue.offer()` 源码，是否能理解其两阶段 CAS（设置 next，然后推进 tail）？

---

> 今天你亲手经历了一场 CAS 的极限挑战：从搭建无锁栈，到亲手触发 ABA 幽灵，再到用版本号驱散它。CAS 是构建高性能并发的原子砖块，你已经掌握了它的脾气。明天我们将用 CountDownLatch / CyclicBarrier / Semaphore 来一场多线程协作的实战演习。





# 第 14 天：三大并发工具 CountDownLatch 等底层追踪
本日掌握：彻底搞懂 CountDownLatch、CyclicBarrier、Semaphore 的用法与底层 AQS 联系，能根据场景选择合适的同步工具  
覆盖原理点：16 (CountDownLatch/CyclicBarrier/Semaphore)  
阶段：使用期

## 🎯 今日目标
- 能使用 `CountDownLatch` 实现一等多、多等一的线程协作。
- 能使用 `CyclicBarrier` 实现分阶段并行任务，并解释其 `barrier` 打破和重置机制。
- 能使用 `Semaphore` 实现流量控制，并解释公平模式与非公平模式。
- 能画出这三个工具内部的 AQS `state` 语义（CountDownLatch 的 state 代表计数，CyclicBarrier 内部使用 ReentrantLock + Condition，Semaphore 直接实现 AQS 共享模式）。
- 能回答面试中关于它们的区别、底层以及可能出问题的追问。

---

## 📝 练习1：基础用法——CountDownLatch 实现并发起跑与等待（必做）

### 业务场景
在性能测试中，你需要让多个工作线程同时开始执行，并且主线程要等待所有工作线程都完成后再统计结果。`CountDownLatch` 恰好可以一次性地完成这两个需求。

### 你的任务
1. 创建一个 `CountDownLatch startSignal = new CountDownLatch(1);` 作为发令枪。
2. 创建一个 `CountDownLatch doneSignal = new CountDownLatch(N);` N 为工作线程数。
3. 每个工作线程启动后调用 `startSignal.await()` 等待起跑信号，执行任务后调用 `doneSignal.countDown()`。
4. 主线程初始化所有线程后，调用 `startSignal.countDown()` 唤醒所有工作线程，然后 `doneSignal.await()` 等待全部完成。
5. 写一段代码模拟这个流程，确保所有线程几乎同时开始，主线程最后统计耗时。

### ⚡ 关键提示
- CountDownLatch 是一次性的，计数器减到0后不能重置，再次 await 直接通过。
- `countDown()` 不会阻塞，`await()` 会阻塞直到计数器归零。
- 注意异常处理：如果某个工作线程中途异常，必须在 finally 中 `countDown()`，否则主线程可能永远等待。
- 底层基于 AQS 共享模式，state 初始化为 count 值，`countDown` 调用 `releaseShared(1)` 减少 state，`await` 调用 `acquireSharedInterruptibly(1)` 等待 state 为 0。

### ✍️ 动手写代码
```java
int N = 5;
CountDownLatch startSignal = new CountDownLatch(1);
CountDownLatch doneSignal = new CountDownLatch(N);
for (int i = 0; i < N; i++) {
    new Thread(() -> {
        try {
            startSignal.await(); // 等待起跑
            // 执行任务
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            doneSignal.countDown();
        }
    }).start();
}
// 主线程发令
startSignal.countDown();
doneSignal.await();
```

### ✅ 自我检查
- [ ] 所有工作线程是否在 `startSignal.countDown()` 后几乎同时开始？
- [ ] 如果某个工作线程抛异常，主线程能否终止等待？（需在 finally 中 countDown）
- [ ] `startSignal` 的计数初始值为 1，能否在其他场景设为更大值？
- [ ] `doneSignal` 的 `await` 返回后，计数器是否变成了 0？

### 📖 参考实现（直接展示）

```java
import java.util.concurrent.CountDownLatch;

public class CountDownLatchDemo {
    public static void main(String[] args) throws InterruptedException {
        int workerCount = 5;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(workerCount);

        for (int i = 0; i < workerCount; i++) {
            final int id = i;
            new Thread(() -> {
                try {
                    startGate.await();          // 等待起跑
                    System.out.println("Worker " + id + " started");
                    Thread.sleep((long)(Math.random() * 1000));
                    System.out.println("Worker " + id + " finished");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneGate.countDown();       // 通知完成
                }
            }).start();
        }

        System.out.println("All threads ready, starting...");
        long start = System.currentTimeMillis();
        startGate.countDown();                 // 发令
        doneGate.await();                       // 等待全部完成
        long end = System.currentTimeMillis();
        System.out.println("All workers done in " + (end - start) + " ms");
    }
}
```

**设计思路**  
- 两个 CountDownLatch：一个用于“起跑”，一个用于“等待完成”。经典的“发令枪”模式。  
- `startGate` 计数 1，主线程调用 `countDown()` 后所有工作线程瞬间同时通过 `await()`，实现同时开始。  
- `doneGate` 计数等于线程数，每个线程结束后 `countDown`，主线程 `await` 确保全完成。  
- `finally` 中的 `countDown()` 保证即使异常也不会导致主线程死等。

### 🐞 常见错误预警
- **忘记在 finally 中 countDown**：线程异常可能导致计数器永远不为零，主线程死锁。
- **计数器初始值设错**：如果 `startGate` 初始值大于 1，则需要多次 `countDown` 才能让等待线程通过，导致启动不齐。
- **重用 CountDownLatch**：计数器归零后不能重置，如果业务需要重复使用，考虑 `CyclicBarrier`。

---

## 📝 练习2：中级用法——CyclicBarrier 与 CountDownLatch 对比（必做）

### 业务场景
你有一个并行计算任务，需要将一个大数组分成几段，每个线程计算一段，所有线程计算完毕后，汇总结果进入下一阶段（如再分段排序）。这个过程需要多次同步，且每次同步后计数器自动重置。

### 你的任务
1. 使用 `CyclicBarrier` 实现：N 个线程，每个线程执行部分计算，调用 `barrier.await()` 等待所有线程到达，然后所有线程同时进入下一阶段。
2. 构造一个需要两阶段聚合的例子：
   - 阶段1：各个线程生成一个随机数，在 barrier 处等待，随后由一个线程计算总和（可在 `barrier` 的回调 `Runnable` 中完成）。
   - 阶段2：各个线程根据总和调整自己的数据，再次 barrier 等待。
3. 对比 `CyclicBarrier` 和 `CountDownLatch`：
   - CountDownLatch：一次性，不可重置，主线程等待其他线程。
   - CyclicBarrier：可循环使用，参与者互相等待，支持回调。
4. 故意让某个线程异常退出，观察 `barrier` 是否被打破，其他线程是否抛出 `BrokenBarrierException`。

### ⚡ 关键提示
- `CyclicBarrier` 构造可传入 `parties` 和可选的 `barrierAction`（到达屏障时执行的动作，由最后一个到达的线程执行）。
- `await()` 会阻塞直到所有 parties 都调用该方法，或超时，或被中断，或 barrier 被打破。
- 如果某个线程因异常离开，barrier 会被打破，其他线程抛出 `BrokenBarrierException`。
- 底层实现：`CyclicBarrier` 内部使用 `ReentrantLock` 和 `Condition`，维护一个 count 计数。每次 `await` 减少计数，当减到 0 时执行 `barrierAction` 并更新 generation 以重置屏障。

### ✍️ 动手写代码
```java
int parties = 3;
CyclicBarrier barrier = new CyclicBarrier(parties, () -> {
    System.out.println("所有线程到达屏障，执行回调...");
});
for (int i = 0; i < parties; i++) {
    new Thread(() -> {
        try {
            // 阶段1
            System.out.println(Thread.currentThread().getName() + " 完成阶段1");
            barrier.await();
            // 阶段2
            System.out.println(Thread.currentThread().getName() + " 完成阶段2");
            barrier.await();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }).start();
}
```

### ✅ 自我检查
- [ ] 是否所有线程在阶段1后都打印了回调信息，然后才进入阶段2？
- [ ] 如果某个线程在阶段1中抛异常，其他线程是否收到 `BrokenBarrierException`？
- [ ] `CyclicBarrier` 是否可以重用？在本例中第二阶段的 `await` 是否无需重新创建？
- [ ] 你能说出 `CyclicBarrier` 和 `CountDownLatch` 在底层 AQS 使用上的不同吗？（CyclicBarrier 使用 Lock 和 Condition，CountDownLatch 内部使用 AQS 共享模式）

### 📖 参考实现（直接展示）

```java
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.BrokenBarrierException;

public class CyclicBarrierDemo {
    public static void main(String[] args) {
        int parties = 3;
        CyclicBarrier barrier = new CyclicBarrier(parties, () -> {
            // 最后一个到达的线程执行此回调
            System.out.println("汇总: 所有线程完成本阶段");
        });

        for (int i = 0; i < parties; i++) {
            final int id = i;
            new Thread(() -> {
                try {
                    // 阶段1
                    int val = (int)(Math.random() * 100);
                    System.out.println("Thread " + id + " 阶段1 值=" + val);
                    Thread.sleep(val);
                    barrier.await(); // 等待其他线程完成阶段1

                    // 阶段2
                    System.out.println("Thread " + id + " 阶段2 开始");
                    barrier.await(); // 等待其他线程完成阶段2
                    System.out.println("Thread " + id + " 完成全部");
                } catch (InterruptedException | BrokenBarrierException e) {
                    System.out.println("Thread " + id + " 被中断或屏障打破");
                }
            }).start();
        }
    }
}
```

**设计思路**  
- `CyclicBarrier` 的 `parties` 设置为 3，每个线程两次 `await`，彼此等待，满足同步需要。  
- 回调 `barrierAction` 由最后到达屏障的线程执行，适合做汇总或初始化下一阶段状态。  
- 底层使用 `ReentrantLock` 加 `Condition` 管理等待，一个 `generation` 标记屏障状态，打破后重置新 generation。

### 🐞 常见错误预警
- **忘记重置**：如果某线程离开时未通知 barrier，将会打破屏障，其他线程抛出 `BrokenBarrierException`。确保每个线程正确调用 `await`。
- **屏障回调中抛异常**：如果 `barrierAction` 抛出异常，屏障会被打破，所有等待线程收到异常。
- **重用时的线程数量**：必须与初始 `parties` 一致，否则永远等待。若想支持可变数量，需在 `barrierAction` 中重新初始化或使用 `Phaser`。

---

## 📝 练习3：高级/探索用法——Semaphore 限流与公平性分析

### 业务场景
一个数据库连接池，最多允许 5 个线程同时获取连接。当连接用完时，其他线程必须等待。你决定使用 `Semaphore` 来实现这一控制。

### 你的任务
1. 创建一个 `Semaphore`，许可数为 5。
2. 模拟 10 个线程竞争连接，每个线程 `acquire()` 获取许可，处理后 `release()`。
3. 测试公平模式和非公平模式的区别：
   - 非公平模式（默认）：可能导致某些线程饥饿。
   - 公平模式（`new Semaphore(permits, true)`）：按 FIFO 分配许可。
4. 编写一个简单统计：记录每个线程的等待时间，看在公平模式下是否更均衡。
5. 观察 Semaphore 的 `availablePermits`、`queueLength` 等方法了解内部状态。
6. 分析底层 AQS 共享模式：`state` 表示剩余许可数，`acquire` 调用 `acquireSharedInterruptibly(1)`，`release` 调用 `releaseShared(1)`；公平与非公平的 `tryAcquireShared` 实现不同。

### ⚡ 关键提示
- `acquire()` 会获取一个许可，若无可用许可则阻塞；`release()` 归还许可。
- `Semaphore` 内部实现了 AQS 同步器（Sync），分 FairSync 和 NonfairSync。
- 公平模式下，`tryAcquireShared` 会先检查队列中是否有等待线程。
- 为了观察等待时间，可以在 `acquire` 前后计时。
- 注意 `release` 可以由任何线程调用，没有持有者概念，因此也容易因 `release` 过多而 state 超过初始值。

### ✍️ 动手写代码
```java
Semaphore semaphore = new Semaphore(5, true); // 公平模式
for (int i = 0; i < 10; i++) {
    final int id = i;
    new Thread(() -> {
        try {
            long start = System.nanoTime();
            semaphore.acquire();
            long waitTime = System.nanoTime() - start;
            System.out.printf("Thread %2d 获取许可, 等待 %d ms%n", id, waitTime / 1_000_000);
            Thread.sleep(500); // 模拟处理
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            semaphore.release();
        }
    }).start();
}
```

### ✅ 自我检查
- [ ] 同时最多有多少线程在处理？是否永远不超过 5？
- [ ] 公平模式下，先等待的线程是否优先获取许可？
- [ ] 如果某线程忘记 `release`，后续线程是否会永远阻塞？
- [ ] `availablePermits()` 返回的是瞬时值吗？

### 📖 参考实现（直接展示）

```java
import java.util.concurrent.Semaphore;

public class SemaphoreDemo {
    public static void main(String[] args) throws InterruptedException {
        // 公平模式 vs 非公平模式：修改 true/false 对比等待时间分布
        Semaphore sem = new Semaphore(5, true);

        for (int i = 0; i < 10; i++) {
            final int id = i;
            new Thread(() -> {
                try {
                    long t0 = System.nanoTime();
                    sem.acquire();
                    long waited = System.nanoTime() - t0;
                    System.out.printf("Thread %2d 获取许可, 等待 %d ms%n", id, waited / 1_000_000);
                    Thread.sleep(200 + (long)(Math.random() * 300));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    sem.release();
                }
            }).start();
            Thread.sleep(50); // 错开启动，方便观察公平性
        }
    }
}
```

**设计思路**  
- `Semaphore` 是典型的共享模式 AQS 实现。公平模式确保先进先出，非公平模式许可可能被“插队”。  
- 通过记录等待时间，可以观察到在公平模式下，线程获取许可的顺序与启动顺序大致相同，等待时间相对均衡。  
- 信号量非常适合实现连接池、限流器等资源控制场景。

### 🐞 常见错误预警
- **许可数超发**：错误地多次 `release` 可能让许可数大于初始值，导致后续不阻塞。应在 `release` 前确保确实持有许可。
- **异常后未释放**：在 `acquire` 后如果发生异常，必须在 finally 中 `release`。
- **死锁**：需要多个许可时，使用 `acquire(n)` 可能因许可不足而长期阻塞，而此时其他线程也阻塞，形成死锁。可以考虑 `tryAcquire` 超时机制。

---

## 🏢 大厂场景实战：数据迁移中的多阶段并行

### 场景描述
你需要将 1000 万条订单数据从 MySQL 迁移到 Elasticsearch。采用分批读取、多线程转换、多线程写入。设计一个多阶段同步方案：
- 阶段1：多个线程并行读取 MySQL 分页数据，汇总到一个共享列表中。
- 阶段2：多个线程并行转换格式。
- 阶段3：多个线程并行批量写入 ES。
- 要求所有线程完成上一阶段后，才能开始下一阶段；读取阶段需要等所有线程数据准备好。

### 约束条件
- 不能使用复杂的流式框架（如 Storm）。
- 数据量不能一次性全部载入内存，需分小批执行（如每批 1 万条）。

### 你的设计任务
基于今天学习的工具，设计一个 `CyclicBarrier` 或 `CountDownLatch` + `Semaphore` 的方案，控制批内阶段同步和同时写 ES 的并发数。

### 常见方案参考（直接展示）
```java
int batchSize = 10000;
int readerThreads = 4;
int writerThreads = 3;
Semaphore writePermits = new Semaphore(writerThreads); // 限制写入并发
// 对每个批次：
CountDownLatch readDone = new CountDownLatch(readerThreads);
// 读取线程完成后 countDown
// 主线程 await，然后启动一批转换线程，再用另一个 latch 等待转换完成
// 最后写入线程并发执行，许可控制并发
```

这样可以清晰控制阶段并发与同步。对于复杂的动态阶段，`Phaser` 更合适。

---

## 🏆 大厂面试题

### 面试题1：CountDownLatch 和 CyclicBarrier 有什么区别？分别适用于什么样的场景？
**难度**：⭐️⭐️⭐️

**参考答案**：

- **区别**：
  - CountDownLatch：计数器不可重置，只能使用一次；一般是一个线程或多个线程 `await`，其他线程 `countDown`；底层基于 AQS 共享模式，state 表示计数。
  - CyclicBarrier：计数器可循环使用，自动重置；所有参与线程互相等待，当全部线程到达屏障时一起继续执行；可以注册一个 `Runnable` 在屏障打开时执行；底层使用 `ReentrantLock` + `Condition` 实现，不是直接 AQS。
- **场景**：
  - CountDownLatch：主线程等待多个工作线程完成（如服务启动等待依赖资源就绪），或一个线程阻塞等待外部事件并发起。
  - CyclicBarrier：多线程分阶段并行处理，在阶段点需要同步（如并行排序的归并阶段、并行计算的分段汇总）。
- **常见追问**：“为什么 CyclicBarrier 不使用 AQS 共享模式？” 因为它需要可重置和屏障回调，AQS 的共享模式一旦 state 为 0 全部通过，不容易重置，而使用 Lock + Condition 可以方便地重置 generation。
- **自我反思**：如果我要设计一个可重置的 CountDownLatch，可以用 CyclicBarrier 封装吗？或者自己实现 AQS 子类并管理重置逻辑。

---

### 面试题2：Semaphore 的公平模式与非公平模式是如何实现的？与 ReentrantLock 的公平锁有何异同？
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：

- **实现**：Semaphore 内部有 FairSync 和 NonfairSync 两个 AQS 子类。
  - 非公平模式：`tryAcquireShared` 直接 CAS 减少 state，不检查队列。
  - 公平模式：`tryAcquireShared` 会先调用 `hasQueuedPredecessors()` 查看是否有等待更久的线程，若有则返回 -1，排队。
- **与 ReentrantLock 公平锁相同**：都是通过检查同步队列中是否有前驱节点来决定是否插队。二者都依赖于 AQS 的队列。
- **不同点**：
  - ReentrantLock 是独占模式，state 表示持有锁的重入次数；Semaphore 是共享模式，state 表示剩余许可数。
  - ReentrantLock 公平锁的 `tryAcquire` 不仅检查队列，还要考虑如果当前线程已经持有锁则可重入，无需排队；Semaphore 公平锁没有可重入概念。
- **常见追问**：“Semaphore 释放许可时可以不被谁限制，为什么不会出错？” 因为它不记录持有者，任何线程都可以释放许可，设计如此。这需要使用者自行保证业务逻辑的正确性。
- **自我反思**：如果信号量被某个线程 `acquire` 后，忘记 `release`，其他线程可能永远阻塞。如何用 try-with-resources 模式简化？

---

### 面试题3：请解释 CountDownLatch 内部的 AQS 共享模式是如何工作的？
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：
- **AQS 共享模式**：`state` 初始化为 count。`await()` 调用 `acquireSharedInterruptibly(1)`，内部调用 `tryAcquireShared`（由 CountDownLatch.Sync 实现），如果 state 不为 0 则返回 -1，进入等待队列。`countDown()` 调用 `releaseShared(1)`，其中 `tryReleaseShared` 自旋 CAS 减少 state，当 state 减为 0 时返回 true，触发 `doReleaseShared` 唤醒所有等待中的线程。
- **关键点**：共享模式的传播唤醒：当 state 变为 0 时，不仅唤醒第一个等待节点，还会依次传播唤醒后续节点，确保所有等待线程都通过。
- **常见追问**：“如果 CountDownLatch 的 state 已经为 0，再调用 `await` 会怎样？” 直接通过，因为 `tryAcquireShared` 返回 1（成功）。
- **易错提醒**：`countDown` 是无阻塞的，不会因为计数器已经为 0 而抛异常；`await` 是阻塞的，直到计数为 0 或被中断。
- **自我反思**：如果让你扩展 CountDownLatch 支持重置，你会怎么做？可能需要添加版本号，并在重置时生成新的 generation，类似 CyclicBarrier。

---

### 面试题4：如何在多线程环境中保证 `SimpleDateFormat` 的线程安全？可以用 `ThreadLocal`，也可以用 `Semaphore`？
**难度**：⭐️⭐️⭐️

**参考答案**：

- **ThreadLocal**：每个线程持有一个 `SimpleDateFormat` 实例，彻底避免共享，但线程数多时可能创建大量对象，且需要显式清理。
- **Semaphore**：共享一个实例，通过信号量许可控制同时访问的线程数为 1（或 N 个副本）。例如 `new Semaphore(1)`，`acquire` 后使用，`release` 后归还。这其实退化成了锁，不如直接用 `synchronized`。
- **推荐**：JDK 8 + `DateTimeFormatter` 已经是线程安全的，无需额外处理。若必须用 `SimpleDateFormat`，ThreadLocal 或同步块均可。
- **常见追问**：“既然 Semaphore 可以做锁，为什么还需要 ReentrantLock？” 因为 Semaphore 不能记录持有者，不可重入，也无法提供条件变量，因此需要互斥锁场景下 ReentrantLock 更合适。
- **自我反思**：考虑一个场景：有 10 个文件需要并发解析，但单线程解析内存很大，限制最多 3 个并发。用 Semaphore 管理许可数就是完美的流量控制。

---

### 面试题5：用 Semaphore 实现一个简单的连接池，需要考虑哪些点？
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：
- **基本结构**：Semaphore 控制连接数上限，用一个并发安全的集合（如 `BlockingQueue`）存储空闲连接。
- **获取连接**：`semaphore.acquire()` 获取许可，然后从队列取连接；如果队列为空（可能是并发误取），需要循环等待或创建新连接（但受限于许可数）。
- **归还连接**：将连接放回队列，然后 `semaphore.release()`。
- **考虑点**：
  1. **异常处理**：如果连接在任务中损坏（如网络断），不应放回队列，而应关闭并释放许可时需小心许可数（需补 `release` 或 `reducePermits`）。
  2. **超时等待**：`tryAcquire(timeout)` 防止死等。
  3. **公平性**：根据业务选择公平或非公平等待。
  4. **动态调整**：连接池大小通常固定，如需动态调整，需要修改 `Semaphore` 许可数，但 `Semaphore` 没有直接减少许可的线程安全方法，可以用 `reducePermits()`（protected）或重新创建。
- **常见追问**：“如果数据库连接的真实最大数小于信号量许可数，会导致什么？” 会导致线程获取连接失败，需要业务层捕获异常并释放许可，否则许可会被浪费。
- **自我反思**：你在项目中是否自己用 Semaphore 实现过连接池？与使用现成的 HikariCP 等相比，自己实现更容易出 bug，生产环境建议使用成熟连接池。

---

> 今天你掌握了并发协作的三大利器，并且看到了它们大同小异的 AQS 内核。明天我们将进入 JVM 内存结构实验，亲自动手让堆、栈、元空间溢出，从此对内存区域的划分烂熟于心。





# 第 15 天：JVM 内存结构实验：堆/栈/元空间溢出
本日掌握：亲手触发堆、栈、元空间/方法区的 OOM，并通过 jstat/jmap 分析内存分布，理解 HotSpot 对象分配过程  
覆盖原理点：17 (JVM 内存结构), 22 (GC 日志解读与调参)  
阶段：使用期

## 🎯 今日目标
- 能通过代码故意制造堆内存溢出、栈内存溢出、元空间溢出，并观察 JVM 报错信息和 dump 文件。
- 能使用 `jstat`、`jmap`、`jstack` 实时查看堆各区使用量、线程堆栈和内存泄漏情况。
- 能解释新生代 Eden + Survivor 的分代结构，以及指针碰撞与空闲列表的对象分配方式。
- 能读懂简单的 GC 日志，并基于日志调整 `-Xmx`、`-Xss`、`-XX:MaxMetaspaceSize` 等参数。
- 能应对面试中关于 JVM 内存模型、OOM 排查与对象分配流程的追问。

---

## 📝 练习1：基础用法——故意制造堆溢出（Heap OOM）并观察（必做）

### 业务场景
你的线上服务偶尔出现 `java.lang.OutOfMemoryError: Java heap space`，你需要复现并学会分析 dump 文件来定位大对象。今天我们就在本地做一次。

### 你的任务
1. 编写一个程序，不断创建对象并持有引用（例如不停的 `list.add(new byte[1024*1024])`），直到堆溢出。
2. 启动时添加 JVM 参数：
   - `-Xmx128m`（最大堆 128M）
   - `-XX:+HeapDumpOnOutOfMemoryError`（OOM 时自动生成 dump）
   - `-XX:HeapDumpPath=./heapdump.hprof`（指定 dump 路径）
   - `-XX:+PrintGCDetails` 或 Java 9+ 的 `-Xlog:gc*:file=gc.log`（记录 GC 日志）
3. 运行程序，观察控制台报错，检查生成的 `heapdump.hprof` 文件是否存在。
4. 使用 `jstat -gcutil <pid> 1000` 在运行过程中监控堆各个区域的使用百分比和 GC 次数。
5. （可选）用 MAT 或 jhat 打开 dump 文件，查找最大的对象。

### ⚡ 关键提示
- 可以使用 `jps` 先获取 Java 进程的 PID，再使用 `jstat -gc <pid> 1000` 实时查看 E（Eden）、S（Survivor）、O（Old）、M（Metaspace）等。
- OOM 时日志会出现 `java.lang.OutOfMemoryError: Java heap space`。
- 注意区分堆 OOM 和 GC overhead limit exceeded（GC 占用太多时间但仍回收很少内存）。
- 如果堆设置的过大，可能很久才溢出，可以适当调小 `-Xmx`。

### ✍️ 动手写代码
```java
import java.util.ArrayList;
import java.util.List;

public class HeapOOM {
    public static void main(String[] args) {
        List<byte[]> list = new ArrayList<>();
        while (true) {
            list.add(new byte[10 * 1024 * 1024]); // 每次分配10MB
        }
    }
}
```
**运行**：`java -Xmx128m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./heapdump.hprof HeapOOM`

### ✅ 自我检查
- [ ] 程序是否抛出 `OutOfMemoryError: Java heap space`？
- [ ] 是否在当前目录生成了 `heapdump.hprof`？
- [ ] `jstat -gcutil` 是否能看到老年代（O）逐渐填满，并发生多次 Full GC？
- [ ] 如果改变 `-Xmx256m`，溢出时间明显延长吗？

### 📖 参考实现与日志分析（直接展示）

```java
// HeapOOM.java 同上
```

**运行并观察 GC 日志**（以 Java 11+ 为例）：
```bash
java -Xmx128m -XX:+UseG1GC -Xlog:gc*:file=gc.log:time,uptime,level,tags HeapOOM
```
打开 `gc.log` 可看到类似：
```
[1234.567ms] GC(10) Pause Young (Normal) (G1 Evacuation Pause) ...
[5678.901ms] GC(38) Pause Full (G1 Evacuation Pause) ...
...
[8901.234ms] java.lang.OutOfMemoryError: Java heap space
```
通过日志能看出年轻代 GC 和老年代 GC 的频率。

**设计思路**  
- 通过死循环快速塞满老年代，迫使 Full GC 后依旧无法分配，触发 OOM。  
- 启动参数确保了 dump 文件生成，这是事故后最重要的排查入口。  
- `jstat` 实时监控让我们看到 Eden 区的快速更替和老年代的逐步上升。

### 🐞 常见错误预警
- **错误**：只配置了 `-Xms` 而没有 `-Xmx`，导致堆会扩大，难于溢出。
- **错误**：使用 `System.gc()` 强制触发 GC 可能会减缓 OOM 的出现，尽量不主动调用。
- **错误**：未设置 `-XX:+HeapDumpOnOutOfMemoryError`，导致事故后无法分析内存快照。

---

## 📝 练习2：中级用法——栈溢出与元空间溢出（必做）

### 业务场景
线程调用栈过深（如无限递归）会导致 `StackOverflowError`；而动态生成大量类（如 CGLib 代理）会导致元空间 `OutOfMemoryError: Metaspace`。你需要分别复现这两种溢出。

### 你的任务
1. **栈溢出**：编写一个递归方法 `stackLeak()`，递归次数极大（例如 50000 次），每次递归分配一些局部变量。JVM 参数设置 `-Xss256k`（线程栈大小 256k）来更快溢出。
2. **元空间溢出**：使用 CGLIB 或 Javassist 动态生成大量类，并加载它们。JVM 参数设置 `-XX:MaxMetaspaceSize=64m`（元空间上限 64M）以触发溢出。如不想引入第三方库，也可以用 `java.lang.reflect.Proxy` 创建大量代理类。
3. 观察各自的错误信息：`StackOverflowError` 和 `OutOfMemoryError: Metaspace`。
4. 使用 `jstack <pid>` 查看栈溢出时的线程堆栈，可以看到非常深的递归调用栈。
5. 使用 `jstat -gc <pid>` 观察元空间溢出时 MC 列的增长。

### ⚡ 关键提示
- 栈溢出是线程请求分配栈帧超过栈深度限制，通常是递归太深或大量局部变量。每个线程的栈空间由 `-Xss` 决定。
- 元空间保存类的元数据，当动态类加载过多且类加载器未被回收时，会导致元空间溢出。注意元空间在 JDK 8+ 替代了永久代。
- 对于元空间溢出，可以使用 `-XX:+TraceClassLoading` 和 `-XX:+TraceClassUnloading` 观察类加载卸载过程，并配合 `-XX:MaxMetaspaceSize=64m` 限制。

### ✍️ 动手写代码
```java
// 栈溢出
public class StackSOF {
    private int depth = 0;
    public void method() {
        depth++;
        method();
    }
    public static void main(String[] args) {
        StackSOF sof = new StackSOF();
        try {
            sof.method();
        } catch (StackOverflowError e) {
            System.out.println("递归深度: " + sof.depth);
            throw e;
        }
    }
}
```

```java
// 元空间溢出（使用 CGLIB）
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;

public class MetaspaceOOM {
    public static void main(String[] args) {
        while (true) {
            Enhancer enhancer = new Enhancer();
            enhancer.setSuperclass(OOMObject.class);
            enhancer.setUseCache(false);
            enhancer.setCallback((MethodInterceptor) (obj, method, args1, proxy) -> proxy.invokeSuper(obj, args1));
            enhancer.create();
        }
    }
    static class OOMObject {}
}
```

### ✅ 自我检查
- [ ] 栈溢出时，控制台是否打印 `StackOverflowError`？递归深度是否在几千到几万？
- [ ] 元空间溢出时，是否抛出 `OutOfMemoryError: Metaspace`？
- [ ] 使用 `jstack` 查看栈溢出线程的堆栈，是否有大量重复的调用帧？
- [ ] `jstat -gc` 中 `MC`（Metaspace Capacity）/ `MU`（Metaspace Used）是否接近设置的上限？

### 📖 参考实现与运行命令（直接展示）

**栈溢出运行**：
```bash
java -Xss256k StackSOF
```

**元空间溢出运行**（需引 CGLIB 包）：
```bash
java -XX:MaxMetaspaceSize=64m -cp .:cglib-3.3.0.jar MetaspaceOOM
```
如果无 CGLIB，也可用 JDK 动态代理大量生成类，但效果稍慢。

**设计思路**  
- 栈溢出通过无出口递归实现，设置较小栈空间快速复现，方便观察栈深度与 `-Xss` 的关系。  
- 元空间溢出通过不断生成新类并阻止类卸载（通过持有类引用或使用不同类加载器）实现，模拟了 CGLIB 代理大量 Bean 时的典型泄漏。  
- `jstack` 分析栈溢出，`jstat` 分析元空间上升，都是线上故障排查的标准动作。

### 🐞 常见错误预警
- 栈溢出实验时，递归方法内若有大量局部变量且栈空间不小，可能需要较深递归，建议调小 `-Xss` （如128k）或直接在方法内定义很多 `long` 变量。
- 元空间溢出需要类加载器无法卸载类，若使用相同类加载器重复加载同一个类会复用，需使用不同类加载器或增强器禁用缓存。
- 确保 `-XX:MaxMetaspaceSize` 设置足够小以快速溢出，否则可能把机器内存耗尽。

---

## 📝 练习3：高级/探索用法——使用 jhsdb / MAT 分析内存泄漏

### 业务场景
生产环境出现缓慢内存泄漏，你需要在测试环境复现，并用 `jmap` 导出 dump，用 MAT 分析大对象引用链。

### 你的任务
1. 模拟一个内存泄漏：静态 Map 不断添加对象但不清理。
2. 运行一段时间后，用 `jmap -dump:live,format=b,file=leak.hprof <pid>` 导出 dump。
3. 下载 Eclipse MAT (Memory Analyzer Tool)，打开 dump 文件，找到内存泄漏的嫌疑对象，分析 GC Root 引用链。
4. 使用 `jmap -histo:live <pid>` 查看堆中对象直方图，快速找到异常多的对象类型。
5. 结合监控工具如 `jconsole` 或 `JVisualVM`，观察堆内存曲线。

### ⚡ 关键提示
- `jmap -histo:live <pid>` 输出存活对象的数量与占用空间，能快速排查是哪个类的实例膨胀。
- `MAT` 中 Leak Suspects Report 可自动分析可疑泄漏对象，并给出引用链。
- 模拟泄漏：`static Map<Integer, byte[]> map = new HashMap<>();` 不断 put 新对象。
- 生产环境 dump 时注意应用暂停，`-F` 参数强制 dump 可能导致数据不完整。

### ✍️ 动手写代码
```java
import java.util.HashMap;
import java.util.Map;

public class MemoryLeak {
    static Map<Integer, byte[]> leakMap = new HashMap<>();
    public static void main(String[] args) throws InterruptedException {
        int i = 0;
        while (true) {
            leakMap.put(i++, new byte[1024 * 1024]); // 1MB each
            Thread.sleep(200);
        }
    }
}
```
运行后通过 `jps` 获得 PID，然后 `jmap -dump:live,format=b,file=leak.hprof <PID>`。

### ✅ 自我检查
- [ ] `jmap -histo:live` 是否显示 `byte[]` 或 `HashMap$Node` 实例数持续上升？
- [ ] MAT 打开的 dump 中，能否找到 `leakMap` 的引用链？
- [ ] 如果加上 `-XX:+HeapDumpOnOutOfMemoryError`，是否在 OOM 时自动生成 dump？

### 📖 参考排查流程（直接展示）
1. `jps -l` 找到进程。
2. `jstat -gcutil <pid> 1000` 监控各代使用百分比，关注 O 列持续上升。
3. `jmap -histo:live <pid> | head -20` 查对象直方图。
4. `jmap -dump:live,format=b,file=/tmp/leak.hprof <pid>` 导出。
5. MAT 打开，点击 "Open Dominator Tree"，找到大对象，右键 "Path To GC Roots" -> "exclude all phantom/weak/soft references"，看出谁在持有。

**设计思路**  
- 内存泄漏排查是每个 Java 程序员必须会的硬技能。掌握 jstat/jmap 和 dump 分析是整个职业生涯都会受益的。

---

## 🔷 原理探究：堆对象分配的“指针碰撞”与“空闲列表”

### 探究问题
HotSpot 在新生代使用“指针碰撞”（Bump the Pointer）分配对象，而在老年代（CMS/G1）可能使用“空闲列表”（Free List）。这是什么意思？如何区分？

### 验证方法
观察不同的 GC 策略下的分配行为，并查阅 Java 白皮书或源码注释。

### 引导性问题
- 为什么新生代用指针碰撞更快？
- 碎片化怎么影响分配方式？

### 原理解读（直接展示）
- **指针碰撞**：堆内存是规整的连续空间，分配时只需将指针向后移动对象大小。要求内存绝对规整，无碎片。用于 Serial、ParNew 等年轻代收集器（搭配 Mark-Compact 或 Copy 算法）。
- **空闲列表**：堆内存中空闲空间可能不连续，JVM 维护一个空闲块列表，分配时查找合适大小的块。用于 CMS 老年代并发收集等会产生碎片的老年代。
- 这决定了 GC 算法选择：标记-清除会产生碎片，只能用空闲列表；标记-整理和复制算法保持规整，能用指针碰撞。

---

## 🏢 大厂场景实战：线上频繁 Full GC 导致超时

### 场景描述
一个订单服务晚高峰频繁出现超时，业务日志没有明显异常，监控显示服务 Stop-The-World 时间较长，CPU 毛刺高。通过 GC 日志发现老年代频繁增长并触发 Full GC 但回收效果差。

### 约束条件
- 堆大小 4G（`-Xmx4096m`）
- 当前使用 CMS 收集器
- 环境 JDK 8
- 需要快速止血并给出合理参数调优方案

### 你的设计任务
1. 分析 Full GC 可能的原因（大对象直接进入老年代、元空间不足、内存泄漏、CMS concurrent mode failure 等）。
2. 写出如何查看 GC 日志、堆 dump、元空间的命令。
3. 给出调优的建议参数（如适当调大新生代、设置 `-XX:CMSInitiatingOccupancyFraction`、调整 `-XX:MaxMetaspaceSize` 等）。

### 常见方案参考（直接展示）
- 紧急止血：`jmap -dump` 分析是否内存泄漏；如果泄漏，先重启；若非泄漏，则调优。
- 参数调优：
  - 若 YGC 频繁且对象很多早逝，增大新生代 `-Xmn`。
  - 若 CMS concurrent mode failure 频繁，降低 `-XX:CMSInitiatingOccupancyFraction=70`（提早启动并发收集），或换用 G1。
  - 若元空间不足，调大 `-XX:MaxMetaspaceSize`。
  - 检查代码中是否有 `System.gc()` 触发 Full GC。

---

## 🏆 大厂面试题

### 面试题1：JVM 堆内存是如何划分的？JDK 8 与之前有什么不同？
**难度**：⭐️⭐️⭐️

**参考答案**：
- **堆划分**：逻辑上分为新生代（Young Generation）和老年代（Old Generation）。新生代又分为 Eden 区和两个 Survivor 区（S0, S1），默认比例为 8:1:1。物理上，内存可以是连续的，也可以不连续。
- **JDK 8 改变**：移除了永久代（PermGen），引入了元空间（Metaspace）。元空间使用本地内存（Native Memory），不再受限于 `-XX:MaxPermSize`，默认无限增长直到系统可用内存，可用 `-XX:MaxMetaspaceSize` 限制。
- **常见追问**：“为什么移除永久代？” 主要因为永久代难以调优（大小难定）、字符串常量池移入堆、类元数据加载卸载复杂等。使用本地内存更灵活。
- **自我反思**：画出堆的分代结构图，并标注默认比例和常用参数（`-Xms`, `-Xmx`, `-Xmn`, `-XX:SurvivorRatio` 等）。

---

### 面试题2：StackOverflowError 和 OutOfMemoryError: Java heap space 的根本区别是什么？什么情况会同时出现？
**难度**：⭐️⭐️⭐️

**参考答案**：
- **StackOverflowError**：线程请求栈深度超过虚拟机允许的最大深度（递归层次太深），或者线程栈帧太大，超过了单个线程栈容量（`-Xss` 设置）。内存总量可能还很充裕。
- **OOM Java heap space**：堆内存不足，无法分配对象，且经过 Full GC 后仍然空间不够。堆内存耗尽。
- **同时出现场景**：创建大量线程同时分配堆对象，每个线程栈都要占用内存，可能导致堆还没满但本地内存不足，报 `unable to create new native thread` 或栈+堆的混合 OOM。或者无限递归并每次创建大对象。
- **常见追问**：“`-Xss` 设置过小会怎样？” 容易栈溢出；但设置过大可能导致能创建的线程数减少（因为每个线程栈占用内存）。
- **自我反思**：为何在递归算法中，用尾递归优化可以减少栈溢出？Java 不支持尾递归优化。

---

### 面试题3：对象从创建到进入老年代的过程是怎样的？大对象直接分配在哪里？
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：
1. **优先分配 Eden**：大多数对象在新生代 Eden 区分配。
2. **Eden 满时触发 Minor GC**：存活对象进入 Survivor 区（From），并标记年龄 1。
3. **经历 Minor GC**：在 Survivor 区每熬过一次 Minor GC，年龄 +1，直到达到指定年龄（默认 15，`-XX:MaxTenuringThreshold`），晋升老年代。
4. **动态年龄判定**：若 Survivor 空间中相同年龄所有对象大小总和 > Survivor 空间的一半，年龄 >= 该年龄的对象直接进入老年代。
5. **大对象直接进老年代**：可以通过 `-XX:PretenureSizeThreshold` 设置超过一定大小的对象直接在老年代分配（仅对 Serial 和 ParNew 有效）。这是为了避免大对象在 Eden 和 Survivor 之间来回复制。
- **常见追问**：“长期存活对象一定到 15 岁才晋升吗？” 不一定，动态年龄规则可能提前晋升。
- **易错提醒**：大对象直接进入老年代可能加速老年代占满和 Full GC。
- **自我反思**：可以用 `-XX:+PrintTenuringDistribution` 观察晋升年龄。

---

### 面试题4：元空间（Metaspace）什么时候会发生 OOM？如何排查和解决？
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：
- **发生场景**：加载的类数量太多（如大量动态代理、Groovy/CGLib 生成类），且类加载器未释放，导致元空间不断增长直到本地内存不足，或达到 `-XX:MaxMetaspaceSize` 限制。
- **排查**：`jstat -gc <pid>` 观察 `MU` 和 `MC` 列不断上升；`jmap -clstats <pid>` 查看类加载器统计；dump 后用 MAT 查看 Duplicate Classes；GC 日志中会有 “Full GC (Metadata GC Threshold)” 。
- **解决**：增大 `-XX:MaxMetaspaceSize`；检查代码中是否有不必要的动态类生成或类加载器泄漏；升级到 JDK 8 高版本，有些版本对 Metaspace 回收策略有改进。
- **常见追问**：“类加载器泄漏是什么？” 如自定义类加载器加载了类后，类加载器仍被引用，其所加载的类无法被卸载，占着元空间不放。
- **自我反思**：Spring Boot 应用常见“Metadata GC threshold”告警，通常是因为内嵌 Tomcat 频繁创建 JSP 的类，优化 JSP 编译或调大元空间。

---

### 面试题5：如何通过 GC 日志判断是堆溢出还是内存泄漏？
**难度**：⭐️⭐️⭐️⭐️⭐️

**参考答案**：
- **内存泄漏**：GC 日志中老年代使用量持续线性或单调上升，每次 Full GC 后回收的内存很少，最终 OOM。`jmap -histo:live` 显示某类对象数量异常多。
- **堆溢出（容量不足）**：GC 日志中老年代曲线有起伏，但总体使用量趋向满，且每次 Full GC 能回收较多内存（但很快又填满）。可能是并发量突然增大或请求对象较大，需要扩大堆。
- **关键指标**：Full GC 后老年代的使用百分比（OU），如果每次都回落到很低但很快又上升，通常是正常业务高峰；如果每次都很高且逐步攀升，泄漏可能性大。
- **常见追问**：“如何用 `jstat` 快速判断？” 观察 `O` 列（Old 使用百分比），在 Full GC 发生后，即 `FGCT` 计数增加且 `OU` 瞬间下降，看下降幅度。若经常触发 Full GC 且 `OU` 居高不下，趋向泄漏。
- **自我反思**：有没有在项目中遇到过由于 ThreadLocal 存放大对象未 remove 导致的内存泄漏？这与堆泄漏还是堆溢出更相关？显然是泄漏。

---

> 今天你亲自导演了三场 OOM 大戏，并发掘了定位这些事故的工具链。从此以后，你对 JVM 内存的各个区域不再陌生，能从容面对生产环境的内存故障。明天我们将继续深入引用类型和 WeakHashMap，探索软/弱/虚引用在缓存及内存敏感场景的应用。









# 第 16 天：引用类型实验：软引用缓存、WeakHashMap
本日掌握：亲手实现基于软引用的内存敏感缓存，写出 WeakHashMap 的自动清理逻辑，理解虚引用与 ReferenceQueue 的监控能力  
覆盖原理点：18 (对象存活判定与引用类型)  
阶段：原理期

## 🎯 今日目标
- 能使用 `SoftReference` 实现一个内存敏感的 LRU 缓存，在 OOM 之前自动释放缓存。
- 能解释 `WeakHashMap` 的工作原理：如何在 key 不再被强引用时自动删除条目，并写实验验证。
- 能利用 `PhantomReference` + `ReferenceQueue` 监控对象的濒死时刻，理解其用于资源释放的机制。
- 能对比强、软、弱、虚四种引用的特性、GC 时机和用途，回答面试连环追问。

---

## 📝 练习1：基础用法——实现基于 SoftReference 的内存敏感缓存（必做）

### 业务场景
一个图片服务需要在内存中缓存最近加载的图片，但又不希望在堆内存紧张时导致 OOM。你选择使用 `SoftReference` 实现一个内存敏感的缓存：当内存充足时，缓存可以一直存在；当即将 OOM 时，GC 会自动清理软引用，释放内存。

### 你的任务
1. 创建一个简易缓存 `SoftCache<K, V>`，内部使用 `HashMap<K, SoftReference<V>>` 存储。
2. 提供 `put(K key, V value)` 和 `get(K key)` 方法。
3. `get` 时检查软引用是否为 null 或引用对象是否被回收，若已回收则返回 null。
4. 故意在缓存中放入大量大对象，然后通过 `-Xmx` 限制堆大小并申请新的大对象，触发 GC 后观察缓存中的软引用是否被自动清理。
5. 对比如果换成 `HashMap<K, V>` 强引用，能否在 OOM 前自动释放。

### ⚡ 关键提示
- `SoftReference` 在内存不足时会被 GC 回收，但具体时机由 JVM 决定。可以设置 `-XX:SoftRefLRUPolicyMSPerMB=1000`（每 MB 堆空间的软引用存活时间）来控制行为。
- 缓存中可能残留 key 对应引用对象为 null 的 entry，需要惰性清理或定期清理。
- 测试时限制堆大小 `-Xmx128m`，并在缓存中放入几十 MB 的对象，然后尝试分配一个比剩余堆大的硬引用数组，触发 GC 并观察软引用缓存是否被清空。

### ✍️ 动手写代码
```java
public class SoftCache<K, V> {
    private final Map<K, SoftReference<V>> cache = new HashMap<>();

    public void put(K key, V value) {
        cache.put(key, new SoftReference<>(value));
    }

    public V get(K key) {
        SoftReference<V> ref = cache.get(key);
        return (ref != null) ? ref.get() : null;
    }
}
```

### ✅ 自我检查
- [ ] 放入缓存的对象在内存紧张时是否被回收，`get` 返回 null？
- [ ] 如果没有额外触发 OOM，缓存是否一直存在？（软引用的正常行为）
- [ ] 缓存中 key 对应的 `SoftReference` 仍然存在，但 `get()` 返回 null 时，是否需要清理 entry？
- [ ] 强引用缓存是否会直接抛出 OOM？（换成普通 HashMap 对照）

### 📖 参考实现与测试（直接展示）

```java
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;

public class SoftReferenceDemo {
    static class SoftCache<K, V> {
        private final Map<K, SoftReference<V>> map = new HashMap<>();
        public void put(K k, V v) { map.put(k, new SoftReference<>(v)); }
        public V get(K k) {
            SoftReference<V> ref = map.get(k);
            return ref == null ? null : ref.get();
        }
    }

    public static void main(String[] args) {
        SoftCache<String, byte[]> cache = new SoftCache<>();
        // -Xmx128m
        for (int i = 0; i < 10; i++) {
            cache.put("img" + i, new byte[10 * 1024 * 1024]); // 10MB each
        }
        // 此时可能已经发生 GC，一些软引用被清除
        for (int i = 0; i < 10; i++) {
            byte[] img = cache.get("img" + i);
            System.out.println("img" + i + " : " + (img != null ? "存活" : "被回收"));
        }
        // 尝试分配一个强引用大数组，触发 OOM
        try {
            byte[] huge = new byte[100 * 1024 * 1024]; // 100MB
        } catch (OutOfMemoryError e) {
            System.out.println("OOM，软引用缓存应该被清理干净");
            for (int i = 0; i < 10; i++) {
                System.out.println("After OOM img" + i + " : " + (cache.get("img" + i) != null));
            }
        }
    }
}
```

**设计思路**  
- 通过循环向缓存中添加大对象，并很快超过堆容量，触发 GC，观察软引用是否被清理。  
- 最终主动分配一个超大强引用数组触发 OOM，确保所有软引用都会被果断回收。  
- 实际开发中，`SoftCache` 常配合 `ReferenceQueue` 异步清理被回收的 entry，避免 map 无限膨胀。

### 🐞 常见错误预警
- **忘记检查 ref 为 null**：`SoftReference.get()` 可能返回 null，直接使用会 NPE。
- **缓存不清理已清除的 entry**：key 还会残留在 map 中，时间长了 map 膨胀，可以结合 `ReferenceQueue` 定时清理。
- **误认为软引用会立即回收**：只有在内存不够时才会被回收，平时和强引用无区别。

---

## 📝 练习2：中级用法——WeakHashMap 自动清理验证

### 业务场景
你需要一个缓存，当 key 对象不再被业务代码引用时，对应的缓存条目自动删除，避免内存泄漏。`WeakHashMap` 正好满足这一特性，它通过弱引用的 key 实现自动清理。

### 你的任务
1. 创建一个 `WeakHashMap<Key, String>`，Key 是一个自定义类（必须正确实现 `equals` 和 `hashCode`）。
2. 放入三个 key-value 对，然后立即调用 `System.gc()`（或故意让 key 失去强引用），观察 map 大小是否自动减小。
3. 对比 `HashMap`：key 失去引用后，map 中的条目是否还在。
4. 探究 `WeakHashMap` 内部是如何实现自动清理的（它使用 `WeakReference` 和 `ReferenceQueue`，在每次 `get/put/size` 时惰性清理）。
5. 写一个循环证明：如果只通过 `System.gc()` 而不调用 map 任何方法，`WeakHashMap` 的大小不会立刻改变，因为清理发生在惰性操作时。只有调用 `size()` 等方法时才会 expunge。

### ⚡ 关键提示
- `WeakHashMap` 的 key 是弱引用，当没有强引用指向 key 时，GC 会回收 key，然后 `WeakHashMap` 会在后续操作中清理相应的 entry。
- `ReferenceQueue`：`WeakHashMap` 内部有一个 `ReferenceQueue`，当 key 被 GC 回收时，弱引用会被放入该队列，地图在访问时据此清理。
- 测试时务必移除 key 的强引用（如设为 null），并调用 `System.gc()` 建议 GC，但不要依赖它一定立即发生。可以多次请求 GC。

### ✍️ 动手写代码
```java
Map<Key, String> weakMap = new WeakHashMap<>();
Key k1 = new Key(1);
Key k2 = new Key(2);
Key k3 = new Key(3);
weakMap.put(k1, "first");
weakMap.put(k2, "second");
weakMap.put(k3, "third");
System.out.println("初始大小: " + weakMap.size()); // 3

k1 = null; // 移除强引用
System.gc(); // 建议回收
Thread.sleep(500);
System.out.println("GC后大小: " + weakMap.size()); // 可能变为2，因为k1的entry被惰性清理
```

### ✅ 自我检查
- [ ] key 置 null 并 GC 后，`WeakHashMap.size()` 是否最终减少？（需要调用 map 方法来触发清理）
- [ ] 如果使用普通的 `HashMap`，`size()` 是否不变？
- [ ] `WeakHashMap` 的 value 如果也持有 key 的强引用，会阻止 key 被回收吗？（是的）

### 📖 参考实现与内部机制说明（直接展示）

```java
import java.util.Map;
import java.util.WeakHashMap;

public class WeakHashMapDemo {
    static class Key {
        int id;
        Key(int id) { this.id = id; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key)) return false;
            return id == ((Key)o).id;
        }
        @Override public int hashCode() { return id; }
    }

    public static void main(String[] args) throws InterruptedException {
        Map<Key, String> weakMap = new WeakHashMap<>();
        Key k1 = new Key(1);
        Key k2 = new Key(2);
        Key k3 = new Key(3);
        weakMap.put(k1, "A");
        weakMap.put(k2, "B");
        weakMap.put(k3, "C");
        System.out.println("Before GC, size=" + weakMap.size()); // 3

        k1 = null;
        System.gc();
        Thread.sleep(200);

        // 注意：必须调用 weakMap 的方法才触发清理
        System.out.println("After GC, before get/put size=" + weakMap.size()); // 这里就可能触发清理，输出2或3
        // 强制触发清理：调用任何方法
        weakMap.get(new Key(2));
        System.out.println("After get, size=" + weakMap.size()); // 2
    }
}
```

**设计思路**  
- `WeakHashMap` 内部 Entry 继承 `WeakReference<Object>`，构造时将 key 作为弱引用，并注册到 `ReferenceQueue`。  
- GC 回收 key 后，弱引用进入队列，后续在 `get/put/size` 等操作中，`expungeStaleEntries` 方法会清除这些 entry。  
- 因此，依赖 `WeakHashMap` 的对象清理由访问方法触发，若不访问方法，内存可能残留，但线程安全。

### 🐞 常见错误预警
- `WeakHashMap` 的 value 强引用 key，导致 key 无法回收。比如 value 持有 key 的引用。
- `WeakHashMap` 的 key 使用字符串常量池或 Integer 缓存，导致这些 key 永远有强引用，无法自动回收。
- 忘了 `GC` 并非即时，实验需多次尝试或给 GC 时间。

---

## 📝 练习3：高级/探索用法——PhantomReference 监控对象最终化

### 业务场景
你需要精确知道一个大对象何时被 GC 真正回收（finalize 之后），并执行额外的资源清理（如释放堆外内存）。使用 `PhantomReference` + `ReferenceQueue` 实现这一监控。

### 你的任务
1. 创建一个自定义类 `Resource`，持有堆外内存模拟（如 `ByteBuffer.allocateDirect`），并在 `finalize` 或清理方法中释放资源。
2. 使用 `PhantomReference` 包装该对象，并注册到 `ReferenceQueue`。
3. 启动一个守护线程，从队列中取出 `PhantomReference`，调用其 `clear()` 并执行自定义清理（如释放直接缓冲区）。
4. 验证：当强引用置 null 并 GC 后，守护线程是否很快从 queue 中取出该 PhantomReference，并执行清理。
5. 解释为什么 `PhantomReference.get()` 永远返回 null，它与 `SoftReference`/`WeakReference` 的本质区别。

### ⚡ 关键提示
- `PhantomReference` 构造时必须传入 `ReferenceQueue`，否则无意义。它不用于访问对象，而用于接收对象被回收的通知。
- 对象真正被 GC 内存回收前，`PhantomReference` 会被加入队列。这发生在 finalize 之后。
- 实际开发中常由 `Cleaner`（基于虚引用）管理释放，如 NIO 的 `DirectByteBuffer` 就是使用 `Cleaner` 释放堆外内存。
- 守护线程需要一直轮询队列，`remove()` 会阻塞直到有对象进入队列。

### ✍️ 动手写代码
```java
import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;

public class PhantomDemo {
    static class Resource {
        long[] data = new long[100000];
        @Override
        protected void finalize() throws Throwable {
            System.out.println("Resource finalized");
            super.finalize();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        ReferenceQueue<Resource> queue = new ReferenceQueue<>();
        Resource resource = new Resource();
        PhantomReference<Resource> phantomRef = new PhantomReference<>(resource, queue);
        resource = null;

        // 守护线程不断从队列中取
        Thread cleaner = new Thread(() -> {
            try {
                Reference<?> ref;
                while ((ref = queue.remove()) != null) {
                    System.out.println("Cleaning resource...");
                    ref.clear();
                    // 执行释放堆外内存等操作
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        cleaner.setDaemon(true);
        cleaner.start();

        System.gc(); // 触发 GC 和 finalize
        Thread.sleep(1000);
        System.out.println("End of main");
    }
}
```

### ✅ 自我检查
- [ ] 虚引用队列是否在 GC 后收到了对象？
- [ ] `phantomRef.get()` 是否始终返回 null？
- [ ] `finalize` 是否比虚引用通知更早执行？
- [ ] 如果没有 `reference.clear()`，虚引用对象本身是否被回收？

### 📖 参考实现与说明（直接展示）

虚引用主要用于更精细的资源释放，其 `get` 永远返回 null，只是作为一个通知机制。在本实验中，GC 触发后，虚引用对象从队列取出，代表堆内存即将被回收，我们可以在此刻安全释放关联的外部资源。`Cleaner` 类封装了此模式，在 NIO 中被广泛使用。

---

## ⚙️ 性能实验：对比 HashMap 与 WeakHashMap 在长期运行中的内存占用

### 实验目标
对比 HashMap 强引用存储和 WeakHashMap 存储在长期运行服务中，当 key 不再需要后的内存占用差异。

### 引导步骤
1. 准备两个 Map，一个 `HashMap<Key, byte[]>`，一个 `WeakHashMap<Key, byte[]>`。
2. 每个 map 放入 10000 个键值对，value 都是 1KB 的 byte 数组。
3. 对 HashMap 的所有 key 解除强引用（置为 null），对 WeakHashMap 同样处理。
4. 多次调用 `System.gc()` 并检查两个 map 的 `size()` 和实际存活对象（通过 `jmap -histo` 对比 `byte[]` 数量）。
5. 记录内存占用变化，观察 HashMap 是否仍占用大量内存，而 WeakHashMap 最终被清空。

### 预期现象和解释（直接展示）
- **HashMap**：即便 key 已无外部强引用，但由于 HashMap 内部 Entry 持有强引用，key 和 value 都不会被回收，`size()` 不变，`byte[]` 仍占大量内存，导致内存泄漏。
- **WeakHashMap**：GC 回收 key 后，在后续 map 操作方法下会清理条目，最终 `size()` 变为 0，内存释放。说明弱引用适合实现缓存，当 key 不再使用时自动清理。

---

## 🔷 原理探究

### 探究问题
`WeakHashMap` 的惰性清理机制会带来什么问题？为什么高并发下通常用 `ConcurrentHashMap` 加弱引用包装而不是直接使用 `WeakHashMap`？

### 验证方法
写一个多线程测试，不断往 `WeakHashMap` 中添加并移除 key，同时另一个线程循环的 `size()`，观察可能出现并发修改异常或不一致。

### 引导性问题
- `WeakHashMap` 是非线程安全的，为何？
- 在清理过程中如果修改了 Map，会导致什么异常？

### 原理解读（直接展示）
`WeakHashMap` 在每次公共方法内都会调用 `expungeStaleEntries`，该方法遍历 ReferenceQueue 并删除对应 entry，同时修改桶链表。这一过程没有同步，如果在迭代或与其他修改操作并发执行，可能导致 `ConcurrentModificationException` 或数据丢失。因此，在多线程环境需要手动同步包装，或使用支持并发的缓存库（如 Guava Cache）。

---

## 🏢 大厂场景实战：本地缓存设计

### 场景描述
一个接口需要查询用户权限，权限数据较稳定但可能不定期变更，每次访问数据库压力大。要求设计一个本地缓存，既要保证数据不经常查询数据库，又要在堆内存紧张时自动清理最不常用的权限数据，且支持权限变更时主动失效。

### 约束条件
- 数据量约 10 万用户
- 单个用户权限对象约 2KB
- 堆内存总大小 2GB，分给缓存的最大 200MB
- 缓存命中率期望 95%以上

### 你的设计任务
利用今天的知识，设计缓存框架。可以结合 `SoftReference` 和 `WeakHashMap` 特性，或采用更成熟的 Guava/Caffeine。请描述如何实现内存敏感淘汰。

### 常见方案参考及其取舍分析（直接展示）
**方案A：纯 SoftReference 缓存**  
- 用 `ConcurrentHashMap<String, SoftReference<UserPermissions>>`。
- 内存充足时保留所有；紧张时 GC 自动清理部分。
- **优点**：实现简单，自适应内存。  
- **缺点**：淘汰不可控，可能清掉热点数据；不能设置最大容量。

**方案B：Caffeine 配置软引用值**  
- 使用 Caffeine 缓存，设置 `softValues()` 和最大权重。  
- **优点**：可配置容量、过期时间、淘汰策略（如 W-TinyLFU）。  
- **缺点**：引入外部依赖。

**方案C：LRU + SoftReference**  
- 自己实现 LRU 缓存，内部存储 `SoftReference`，超过容量淘汰链表尾部。  
- **优点**：能控制容量和内存敏感双重保障。  
- **缺点**：实现稍复杂。

推荐生产使用 Caffeine，它已成熟且高效。

---

## 🏆 大厂面试题

### 面试题1：Java 中四种引用分别是什么？各自什么时候被回收？主要用途是什么？
**难度**：⭐️⭐️⭐️

**参考答案**：
- **强引用 (Strong)**：最常见的引用，`Object o = new Object()`。只要强引用存在，对象永不回收。
- **软引用 (Soft)**：通过 `SoftReference` 创建。在内存充足时不会被回收，只有在堆内存即将溢出时，GC 才回收软引用指向的对象。适用于实现内存敏感缓存。
- **弱引用 (Weak)**：通过 `WeakReference` 创建。只要发生 GC，无论内存是否充足，弱引用指向的对象都会被回收。适用于实现 `WeakHashMap` 等映射表，key 不使用时自动删除。
- **虚引用 (Phantom)**：通过 `PhantomReference` 创建。它的 `get` 始终返回 null，仅用于在对象被回收后收到一个系统通知，用于精细的资源释放（如释放堆外内存）。必须与 `ReferenceQueue` 联合使用。
- **常见追问**：“软引用回收时机与内存的关系？” 可以调节 `-XX:SoftRefLRUPolicyMSPerMB` 参数控制软引用的存活时间占空堆的每 MB 值。
- **易错提醒**：SoftReference 在内存不够时回收，不是内存一少就立即回收，具体由 JVM 策略决定。
- **自我反思**：列举项目中实际使用到了哪种引用？如果没有，是否有潜在的优化场景？

---

### 面试题2：WeakHashMap 是如何工作的？它和 HashMap 的最大区别是什么？
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：
- **原理**：WeakHashMap 内部 Entry 继承 `WeakReference`，其构造将 key 包装为弱引用并注册到内部的 `ReferenceQueue`。当 key 失去外部强引用且 GC 发生后，该弱引用进入队列。WeakHashMap 在后续的 `get`、`put`、`size` 等操作中调用 `expungeStaleEntries` 检查队列，移除对应的 entry，从而使得 key 和 value 都能被 GC 回收。
- **区别**：HashMap 的 Entry 对 key 和 value 都是强引用，只要 Map 在，key 和 value 就永远不会被回收，容易造成内存泄漏。WeakHashMap 可以自动回收无用 key 的条目。
- **关键限制**：value 不能强引用 key，否则 key 无法回收；WeakHashMap 非线程安全；其清理是惰性的，不访问可能不清理。
- **常见追问**：“为什么 value 会被回收？” 因为 WeakHashMap 的清理方法不仅移除 key 对应的 Entry，同时也使 value 失去强引用（只要没有其他强引用），从而 value 也可被 GC。
- **易错提醒**：如果 key 是字符串常量或整数缓存值，它们终生存在，WeakHashMap 也就退化成了普通 HashMap。
- **自我反思**：是否在项目中遇到过由于缓存没有清理导致 OOM？WeakHashMap 或 Guava Cache 是否可以帮助优化？

---

### 面试题3：虚引用和弱引用有什么区别？各有什么应用场景？
**难度**：⭐️⭐️⭐️

**参考答案**：
- **区别**：
  - **弱引用**：可以拿到关联的对象 (`ref.get()`)，用于实现缓存，当对象仅被弱引用可达时，GC 会回收它；回收时引用进入队列。
  - **虚引用**：`get()` 永远返回 null，不能通过它访问对象；仅用于在对象被回收后获得通知。它在 finalize 之后内存即将回收前进入队列。
- **应用场景**：
  - 弱引用：`WeakHashMap`、`ThreadLocal.ThreadLocalMap` 中 key 的弱引用(防止 `ThreadLocal` 实例无法 GC)。
  - 虚引用：监控大对象的回收时间、管理直接内存 (`Cleaner` 基于虚引用释放 NIO DirectByteBuffer 的堆外内存)。
- **常见追问**：“`ThreadLocal` 内部为什么用弱引用而不是虚引用？” 因为它需要在 `ThreadLocal` 失去强引用后，执行惰性清理旧 entry，`get` 的时候可能还需要读取值，不能像虚引用那样完全拿不到。
- **自我反思**：是否用过或听说过 `Cleaner`？了解一下 `ByteBuffer.allocateDirect()` 的释放原理。

---

### 面试题4：ThreadLocalMap 的 Entry 为什么 key 是弱引用？如果改成强引用会怎样？
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：
- **原因**：ThreadLocalMap 的 key 是 ThreadLocal 实例。如果 key 是强引用，当业务代码不再持有 ThreadLocal 的强引用时，由于 Map 还有强引用，ThreadLocal 永远不会被 GC，导致内存泄漏。改为弱引用后，ThreadLocal 可以被回收，然后 Entry 的 key 变为 null，后续有机会惰性清理。
- **后果**：虽然 key 被回收，value 仍然是强引用，所以如果不手动 `remove()`，value 依旧泄漏。因此弱引用只是缓解，配合 `remove()` 才是安全。
- **常见追问**：“为什么 value 不改弱引用？” 因为 value 是业务数据，如果改成弱引用，在没有其他强引用时可能随时被 GC，导致取到 null 丢失数据。
- **自我反思**：写一段代码复现 ThreadLocal 泄漏并分析 Entry 状态，之前第 12 天已经做过，再回顾一下。

---

### 面试题5：使用 `ReferenceQueue` 能够实现什么？请举例
**难度**：⭐️⭐️⭐️

**参考答案**：
- **作用**：当引用（软/弱/虚）所指向的对象被 GC 回收时，这个引用对象本身会被放入与之关联的 `ReferenceQueue`。我们可以从队列中获取这些引用，进行资源清理或统计。
- **例子**：
  - `WeakHashMap`：内部使用 `ReferenceQueue` 存储 key 被回收的 Entry，并在方法调用时 expunge。
  - 自定义缓存清理线程：为软引用缓存关联队列，守护线程轮询，一旦获取到引用就清除缓存中的对应条目。
  - `Cleaner`（虚引用）：对象被回收后，从队列中取出虚引用，释放关联的堆外资源。
  - 内存泄漏监控：为关键对象创建弱引用并注册队列，当队列收到引用时记录日志，表明对象已被回收。
- **常见追问**：“`remove()` 和 `poll()` 区别？” `remove()` 阻塞直到有可用元素，`poll()` 非阻塞立即返回。
- **自我反思**：你能为昨天写的简单连接池加上利用虚引用追踪连接泄漏吗？

---

> 今天你掌握了 Java 引用体系的全部招式，从软引用缓存到虚引用资源管理，构筑了内存安全的铜墙铁壁。明天我们将进入 GC 算法的世界，亲自对比不同垃圾收集器的行为与日志。





# 第 17 天：GC 算法对比实验：不同收集器下日志分析
本日掌握：亲手启动不同 GC 收集器，分析日志理解标记-清除、复制、标记-整理算法，掌握 CMS 和 G1 的核心流程与调优参数  
覆盖原理点：19 (分代回收与算法), 20 (CMS 垃圾收集器), 21 (G1 垃圾收集器), 22 (GC 日志解读与调参)  
阶段：原理期

## 🎯 今日目标
- 能动手切换 Serial、Parallel、CMS、G1 收集器，并解读各自 GC 日志中的关键事件。
- 能解释新生代为何用复制算法，老年代为何用标记-清除或标记-整理算法。
- 能画出 CMS 的四个主要阶段，说出“浮动垃圾”和 concurrent mode failure 的成因及应对。
- 能画出 G1 的 Region 划分、Mixed GC 过程，理解 Remembered Set 和 SATB 的作用。
- 能根据业务场景（吞吐量优先/响应时间优先）合理选择收集器并给出 JVM 参数。

---

## 📝 练习1：基础用法——不同收集器的 GC 日志初体验（必做）

### 业务场景
你接手了一个老服务，需要评估当前 GC 策略是否合理。第一步是让服务在不同收集器下运行，收集 GC 日志，对比行为。

### 你的任务
1. 创建一个简单的 Java 程序 `GCLogProducer`，它持续分配大对象（例如在 List 中不断添加 1MB 的 `byte[]`），并偶尔释放一些引用（循环控制），以便产生连续的 Young GC 和 Full GC。
2. 分别在下面四种收集器下运行该程序，并生成 GC 日志（参数以 JDK 11+ 的 `-Xlog` 为例，JDK 8 可用 `-XX:+PrintGCDetails`）：
   - **Serial GC**：`-XX:+UseSerialGC`
   - **Parallel GC**：`-XX:+UseParallelGC`
   - **CMS (Concurrent Mark Sweep)**：`-XX:+UseConcMarkSweepGC` (JDK 8，已废弃，但仍可实验)
   - **G1 GC**：`-XX:+UseG1GC`
3. 在每个日志中识别出：Minor GC (Young GC)、Major GC (老年代GC)/Mixed GC、Full GC 的事件，记录暂停时间和回收效果。
4. 限制堆大小 `-Xms128m -Xmx128m` 以加速 GC 产生。
5. 使用工具 `jstat -gcutil <pid> 1000` 同时观察各代使用率变化。

### ⚡ 关键提示
- 不同收集器的日志格式略有不同，但都包含 GC 类型、回收前大小 -> 回收后大小、暂停时间。
- 对于 G1，日志中会出现 `Pause Young (Normal)`、`Pause Mixed`、`Pause Full` 等。
- CMS 日志会有 `Initial Mark`、`Concurrent Mark`、`Remark`、`Concurrent Sweep`。
- 建议使用 JDK 11+ 进行实验，G1 日志更丰富。

### ✍️ 动手写代码
```java
// GCLogProducer.java
import java.util.ArrayList;
import java.util.List;

public class GCLogProducer {
    public static void main(String[] args) throws Exception {
        List<byte[]> list = new ArrayList<>();
        int count = 0;
        while (true) {
            list.add(new byte[1024 * 1024]); // 1MB
            count++;
            if (count % 50 == 0) {
                // 释放引用，让部分对象变为垃圾
                list.subList(0, 10).clear();
            }
            if (count % 100 == 0) {
                Thread.sleep(200); // 放慢速度观察
            }
        }
    }
}
```
**运行示例**（以 Serial GC 为例）：
```bash
java -Xms128m -Xmx128m -XX:+UseSerialGC -Xlog:gc*:file=gc_serial.log:time,uptime,level,tags GCLogProducer
```

### ✅ 自我检查
- [ ] 是否在日志中看到了多次 `Pause Young` 和可能的 `Pause Full`？
- [ ] 回收暂停时间大约在什么范围（如 Serial 的 Young GC 可能几 ms）？
- [ ] 对比 Serial 与 Parallel，哪个的 Young GC 吞吐量更高（Parallel 多线程）？
- [ ] G1 日志里出现了哪些类型 Pause？

### 📖 参考日志片段（直接展示）

**Serial GC** 典型日志（JDK 11+）：
```
[0.123s][info][gc,start    ] GC(0) Pause Young (Allocation Failure)
[0.125s][info][gc,heap     ] GC(0) DefNew: 16384K->2048K(18432K)
[0.125s][info][gc,heap     ] GC(0) Tenured: 0K->256K(82944K)
[0.125s][info][gc,metaspace] GC(0) Metaspace: 500K->500K(1056768K)
[0.125s][info][gc          ] GC(0) Pause Young (Allocation Failure) 16M->2M(98M) 2.345ms
[0.126s][info][gc,cpu      ] GC(0) User=0.01s Sys=0.00s Real=0.00s
```

**G1 GC** 典型日志：
```
[1.234s][info][gc,start     ] GC(10) Pause Young (Normal) (G1 Evacuation Pause)
[1.245s][info][gc,heap      ] GC(10) Eden regions: 24->0(30)
[1.245s][info][gc,heap      ] GC(10) Survivor regions: 2->3(5)
[1.245s][info][gc,heap      ] GC(10) Old regions: 10->12
[1.245s][info][gc           ] GC(10) Pause Young (Normal) (G1 Evacuation Pause) 128M->120M(512M) 10.234ms
[1.246s][info][gc,cpu       ] GC(10) User=0.05s Sys=0.01s Real=0.01s
```
出现 `Pause Full` 时表明 G1 未能及时回收，需要调优。

**设计思路**  
- 通过限制堆大小，制造大量 Minor GC 和可能的 Full GC，充分观察各收集器差异。  
- 对比 Serial（单线程）和 Parallel（并行）的回收暂停时间和回收量差异。

### 🐞 常见错误预警
- CMS 在 JDK 14 后被正式移除，若使用高版本请改用其他收集器实验，或仅查阅日志样例理解。
- `Xlog` 参数位置放错可能导致日志无输出，确保放在类名前。
- 实验时若堆过大，GC 不频繁，可增大 `-Xmx` 或加大分配速度。

---

## 📝 练习2：中级用法——CMS 收集器四阶段与浮动垃圾分析

### 业务场景
你维护的服务使用 CMS，但偶尔出现长时间停顿的 Full GC (Serial Old)，日志中出现 `concurrent mode failure`。你需要理解 CMS 的四阶段，并复现与解决该问题。

### 你的任务
1. 编写一个程序，交替分配大对象和释放（制造老年代碎片），同时分配大量存活对象让老年代慢慢涨。
2. 使用 CMS 收集器运行，参数如：
   - `-XX:+UseConcMarkSweepGC`
   - `-XX:CMSInitiatingOccupancyFraction=70` （老年代占 70% 时开始并发收集）
   - `-XX:+UseCMSInitiatingOccupancyOnly` （严格按阈值触发）
   - 添加 `-XX:+PrintGCDetails` 或 `-Xlog:gc*` 观察日志。
3. 从日志中找出 CMS 的四个阶段：
   - **Initial Mark**（STW，标记 GC Roots 直接引用，暂停短）
   - **Concurrent Mark**（并发，遍历对象图，与应用线程并行）
   - **Remark**（STW，修正并发标记期间的变化，暂停比初始标记长）
   - **Concurrent Sweep**（并发清理）
4. 故意制造 `concurrent mode failure`：在老年代接近满时仍快速分配对象，使得 CMS 还没完成并发清理就被迫触发 Full GC。日志中会看到 `concurrent mode failure` 然后是 `Full GC (Allocation Failure)`。
5. 解决：降低 `CMSInitiatingOccupancyFraction` 提早开始并发收集，或加大堆，或换用 G1。

### ⚡ 关键提示
- CMS 是标记-清除算法，会产生内存碎片。当碎片化严重，大对象分配可能直接触发 Full GC（带整理的 Serial Old）。
- 浮动垃圾：并发标记期间应用程序新产生的垃圾，只能等到下一次 GC 回收，因此需要预留空间。
- `Remark` 阶段为了加速，JDK 8+ 默认使用 `-XX:+CMSScavengeBeforeRemark`，在 Remark 前强制一次 Young GC。
- 实验时可通过 `-XX:CMSInitiatingOccupancyFraction=50` 让 CMS 早早开始。

### ✍️ 动手写代码
```java
// 模拟对象分配
import java.util.ArrayList;
import java.util.List;
public class CMSTest {
    public static void main(String[] args) throws Exception {
        List<Object> list = new ArrayList<>();
        int count = 0;
        while (true) {
            list.add(new byte[5 * 1024 * 1024]); // 5MB
            count++;
            if (count % 10 == 0) {
                list.subList(0, 2).clear(); // 让部分对象变成浮动垃圾
            }
            Thread.sleep(100);
        }
    }
}
```
运行（JDK 8）：
```bash
java -Xms256m -Xmx256m -XX:+UseConcMarkSweepGC 
     -XX:CMSInitiatingOccupancyFraction=70 -XX:+UseCMSInitiatingOccupancyOnly 
     -XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc:cms.log CMSTest
```

### ✅ 自我检查
- [ ] 日志中是否出现了 `CMS-initial-mark`、`CMS-concurrent-mark`、`CMS-remark`、`CMS-concurrent-sweep`？
- [ ] 如果触发了 `concurrent mode failure`，后面是否跟了一个 `Full GC (Allocation Failure)` 由 Serial Old 完成？
- [ ] 降低阈值后，是否减少了 Full GC？

### 📖 参考日志与解释（直接展示）

**CMS 正常流程日志片段**：
```
2026-05-13T10:16:00.123-0000: 5.456: [GC (CMS Initial Mark) [1 CMS-initial-mark: 56789K(102400K)] ...
2026-05-13T10:16:00.125-0000: 5.458: [CMS-concurrent-mark-start]
2026-05-13T10:16:01.234-0000: 5.567: [CMS-concurrent-mark: 1.234/2.345 secs] [Times: user=2.30 sys=0.10, real=2.35 secs]
2026-05-13T10:16:01.234-0000: [GC (CMS Remark) [1 CMS-remark: 78912K] ...
2026-05-13T10:16:01.456-0000: [CMS-concurrent-sweep-start]
2026-05-13T10:16:02.123-0000: [CMS-concurrent-sweep: 0.667/0.789 secs]
```

**故障日志**：
```
[GC (Allocation Failure) 2026-05-13T10:18:30.456-0000: [CMS (concurrent mode failure): 101234K->101234K(102400K) ...
[Full GC (Allocation Failure)  101234K->3450K(102400K), 0.4567890 secs]
```

**设计思路**  
- 通过快速分配大对象并偶尔释放，制造老年代碎片和浮动垃圾，增加 `concurrent mode failure` 概率。  
- 观察日志对比成功和失败情况，理解 CMS 的脆弱性。

### 🐞 常见错误预警
- CMS 已废弃，JDK 14+ 需要特殊参数才能启用，建议用 JDK 8 或直接分析历史日志。
- 碎片的形成：CMS 采用标记-清除，不压缩，导致内存越来越碎，`-XX:+UseCMSCompactAtFullCollection` 可以让 Full GC 做压缩，但是 STW 长。

---

## 📝 练习3：高级/探索用法——G1 Mixed GC 与 Remembered Set 探究

### 业务场景
你的服务转向使用 G1 收集器，但发现偶尔出现长时间的 Mixed GC 或 Full GC。你需要理解 G1 的 Region、Remembered Set (RSet)、SATB，并调整 `-XX:MaxGCPauseMillis` 目标。

### 你的任务
1. 运行相同的 `GCLogProducer` 程序，使用 G1 收集器。
2. 添加参数：
   - `-XX:+UseG1GC`
   - `-XX:MaxGCPauseMillis=50` （目标暂停 50ms）
   - `-XX:G1HeapRegionSize=4m` （设置 Region 大小）
   - `-XX:+PrintAdaptiveSizePolicy` 或使用 `-Xlog:gc+ergo=debug` 观察 G1 的自适应调节。
3. 观察日志中 G1 的 Evacuation Pause (Young GC) 和 Mixed GC (包含回收部分老年代 Region)。Mixed GC 会选出垃圾最多的几个 Old Region 进行回收。
4. 尝试调整 `-XX:MaxGCPauseMillis` 到极小值（如 10ms），看 G1 是否会频繁暂停，但仍尽量达成目标。如果达不到，可能退化为 Full GC。
5. 使用 `jstat -gcutil <pid>` 观察 G1 各代的百分比变化，以及 `GCT` 时间。

### ⚡ 关键提示
- G1 将堆划分为 Region，每个 Region 大小固定（1~32MB，2的幂），由 `G1HeapRegionSize` 指定。
- **RSet**：每个 Region 都有一个 RSet，记录其他 Region 对本 Region 内的引用（“谁指着我”），避免全堆扫描。
- **SATB**：Snapshot-At-The-Beginning，一种并发标记算法，保证并发标记开始时对象图的一致性。
- Mixed GC 不仅回收新生代，还回收部分老年代 Region，遵循目标暂停时间。
- 使用 `-Xlog:gc+remset=trace` 可查看 RSet 维护开销（级别高，慎用）。

### ✍️ 动手写代码
```java
// 复用 GCLogProducer
```
**运行命令示例**（JDK 11+）：
```bash
java -Xms256m -Xmx256m -XX:+UseG1GC -XX:MaxGCPauseMillis=50 
     -XX:G1HeapRegionSize=4m 
     -Xlog:gc*:file=g1.log:time,uptime,level,tags 
     GCLogProducer
```

### ✅ 自我检查
- [ ] 日志中是否看到了 `Pause Young (Normal)` 和 `Pause Mixed`？
- [ ] `MaxGCPauseMillis` 是否影响了 Mixed GC 的频率和选择回收的 Region 数？
- [ ] 如果设置极短的暂停目标（如 5ms），是否会经常触发 Full GC？
- [ ] 能否解释 G1 的 `Remembered Set` 如何避免全堆扫描？

### 📖 参考日志与解释（直接展示）

G1 Mixed GC 日志片段：
```
[12.345s][info][gc,start     ] GC(20) Pause Mixed (G1 Evacuation Pause)
[12.355s][info][gc,heap      ] GC(20) Eden regions: 10->0(15)
[12.355s][info][gc,heap      ] GC(20) Survivor regions: 3->2
[12.355s][info][gc,heap      ] GC(20) Old regions: 150->130
[12.355s][info][gc           ] GC(20) Pause Mixed (G1 Evacuation Pause) 200M->180M(256M) 9.876ms
```
Mixed GC 同时回收了 Young 和部分 Old。G1 通过选择垃圾最多的 Old Region 来在暂停目标内达到最佳回收效果。

**设计思路**  
- G1 通过拆分 Region 避免全堆整理，Mixed GC 是它的核心特色，兼顾吞吐量和延迟。  
- 调整目标暂停时间可以直接观察到其对堆形状和 GC 频率的影响。

### 🐞 常见错误预警
- Region 大小设置不当会影响 RSet 开销：太大则回收粒度粗，太小则 RSet 内存占用大。
- `MaxGCPauseMillis` 并不是硬承诺，G1 尽力而为，若无法达成可能导致触发 Full GC。

---

## ⚙️ 性能实验：对比 Serial / Parallel / CMS / G1 的吞吐量与暂停

### 实验目标
使用同一测试负载，对比四种收集器在相同堆大小下的小暂停时间、总吞吐量和 Full GC 次数。

### 引导步骤
1. **准备测试程序**：使用 `GCLogProducer` 或写一个模拟业务负载（持续分配、释放，类似上面）。
2. **固定堆大小** 512m (`-Xms512m -Xmx512m`)。
3. **分别运行**四种收集器，并导出 GC 日志。每种运行 60 秒。
4. **解析日志**：提取平均暂停时间，最大暂停时间，总 GC 时间，GC 次数。
5. 使用 `jstat` 辅助记录。
6. **总结**哪种收集器给出最低的平均暂停（适合响应时间敏感），哪种给出最高吞吐量（适合批处理）。

### 预期现象和解释（直接展示）

| 收集器   | 平均暂停 (Young) | 最大暂停           | 总 GC 时间    | 适用场景     |
| -------- | ---------------- | ------------------ | ------------- | ------------ |
| Serial   | 低 (单线程)      | 中                 | 高            | 客户端、小堆 |
| Parallel | 较低 (并行)      | 中                 | 较低 (高吞吐) | 后台计算     |
| CMS      | 低 (并发)        | 可能高 (Full GC时) | 增加 CPU 开销 | 低延迟 Web   |
| G1       | 可预测 (软实时)  | 较可控             | 适中          | 大堆、低延迟 |

- **解释**：Parallel 使用多线程并行回收，吞吐量最高（CPU 被有效利用），但暂停时间不可控。CMS 和 G1 都追求低延迟，G1 通过分 Region 和 Mixed GC 提供了更可预测的暂停，CMS 因碎片和 concurrent mode failure 可能产生长时间 Full GC。

---

## 🔷 原理探究

### 探究问题
为什么新生代必须用复制算法，而老年代不能用复制算法？

### 验证方法
分析 GC 日志中输入口的对象大小和存活比例；在程序运行时通过 `jstat` 查看新生代中每次 Minor GC 的晋升量。

### 引导性问题
- Eden/ Survivor 的默认比例 8:1 意味着什么？
- 如果大多数对象朝生夕死，复制算法为什么高效？

### 原理解读（直接展示）
- **新生代特性**：绝大多数对象生命周期短（朝生夕死），Minor GC 时只有少量对象存活。复制算法只需要把存活对象拷贝到 Survivor 区或老年代，然后一次性清理 Eden 和 From Survivor，速度快且无碎片。
- **老年代特性**：对象存活率高，如果使用复制算法，需要 1:1 的额外空间，且每次回收都要拷贝大量存活对象，极不划算。因此老年代采用标记-清除或标记-整理算法。
- 因此分代回收是算法对对象生命周期分布差异的精准利用。

---

## 🏢 大厂场景实战：选择 GC 收集器并调参

### 场景描述
一个大型电商的后台服务，使用 16 核、64GB 内存的服务器，堆大小设置为 32GB。对接口延迟要求 99线 小于 100ms，允许偶尔的 Young GC 但频繁 Full GC 不得忍。请选择合适的收集器并给出参数初设。

### 约束条件
- 堆大小 32GB
- 对象平均存活时间中等
- 大堆可能导致 G1 的停顿偏长需调优

### 你的设计任务
1. 选择 G1 收集器，并说明理由（大堆适合 G1，避免 CMS 碎片，软实时目标）。
2. 设置 `-XX:MaxGCPauseMillis=80` （目标暂停 80ms）。
3. 设置 `-XX:G1HeapRegionSize=16m` （考虑堆大，Region 大小调整以减少对象复制开销）。
4. 启用 `-XX:+PrintAdaptiveSizePolicy` 监控自适应调节。
5. 给出如何使用 `jstat` 和 GC 日志持续观察调优的步骤。

### 常见方案参考及其取舍分析（直接展示）
- **为什么不用 CMS**：32GB 大堆下 CMS 碎片严重，容易 concurrent mode failure，且已废弃。
- **为什么不用 Parallel**：延迟敏感型应用，Parallel 的长暂停不可接受。
- **为什么不用 ZGC/Shenandoah**：若 JDK 版本支持，ZGC 暂停极低（<10ms），也是大堆优秀选择，但部分公司未升级。
- **调优重心**：关注 Mixed GC 的频率和每次回收的老年代区域数，确保暂停时间在目标内；观察 `Pause Full` 次数，及时调整 `-XX:InitiatingHeapOccupancyPercent`（G1 启动并发标记的堆占用阈值，默认 45%）。

---

## 🏆 大厂面试题

### 面试题1：请解释标记-清除、标记-整理、复制算法各自优缺点和适用场景
**难度**：⭐️⭐️⭐️

**参考答案**：
- **标记-清除**：先标记所有存活对象，然后清除未标记的对象。优点：无需移动对象。缺点：产生内存碎片。适用于老年代，如 CMS。
- **标记-整理**：标记后让存活对象向一端移动，然后清理边界以外的内存。优点：无碎片。缺点：移动对象需要 STW 且耗时。适用于老年代（如 Serial Old、Parallel Old）。
- **复制算法**：将内存分为两块，只使用一块，回收时将存活对象复制到另一块，然后整体清理当前块。优点：无碎片，速度快。缺点：内存利用率低（50%）。适用于新生代（对象朝生夕死，只需保留少量的存活对象，并配合 Eden 的大比例分配）。
- **常见追问**：“CMS 为什么不整理内存？” 因为它的目标就是在并发清理阶段不长时间 STW，整理需要移动对象，必须 STW，所以仅 Full GC 时才整理。
- **自我反思**：画出新生代 Eden + 两个 Survivor 的工作流程图，标注每次 Minor GC 后对象流向。

---

### 面试题2：CMS 收集器的四个阶段是什么？浮动垃圾和 concurrent mode failure 是怎么产生的？
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：
- **四个阶段**：
  1. Initial Mark (STW)：标记 GC Roots 能直接关联的对象，暂停时间短。
  2. Concurrent Mark：并发遍历整个对象图，与应用线程并发，耗时最长。
  3. Remark (STW)：修正并发标记期间因用户程序继续运行而导致的标记变动，暂停比初始标记长一些。
  4. Concurrent Sweep：并发清除未标记对象，回收内存。
- **浮动垃圾**：并发标记阶段用户线程仍在运行，新产生的垃圾对象在这次 GC 中无法被标记，只能等到下一次 GC。因此 CMS 需要预留一部分老年代空间供这些浮动垃圾存活。
- **concurrent mode failure**：并发标记和清理期间，老年代剩余空间不足以存放新晋升对象或大对象，导致 CMS 回退，触发 Serial Old 的 Full GC，进行单线程标记-整理，暂停时间很长。原因：CMS 启动阈值太高或对象分配速度过快。
- **常见追问**：“如何减少 concurrent mode failure？” 降低 `CMSInitiatingOccupancyFraction`，增加预留空间；或者使用 G1。
- **易错提醒**：CMS 的 Remark 阶段可能因为扫描年轻代跨代引用而耗时，可通过 `-XX:+CMSScavengeBeforeRemark` 在 Remark 前做一次 Young GC 减少跨代表。
- **自我反思**：CMS 浮动垃圾如果很多，会不会造成下一次并发标记启动更早？会的，因为老年代占用高。

---

### 面试题3：G1 收集器的 Region 是什么？Remembered Set（RSet）和 SATB 的作用？
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：
- **Region**：G1 把 Java 堆划分成多个大小相等的 Region（默认约 2048 个，大小从 1MB 到 32MB），每个 Region 可以充当 Eden、Survivor 或 Old 区，不再像传统那样物理固定的连续代。这允许 G1 灵活地只回收部分区域（Mixed GC）。
- **RSet（记忆集）**：每个 Region 都有一个 RSet，记录了其他 Region 指向本 Region 内部的引用。在 Minor GC 或 Mixed GC 时只要扫描本 Region 的 RSet，就能知道哪些外部对象引用了本区域对象，无需全堆扫描，极大加速 GC。
- **SATB（快照-在-开始）**：G1 并发标记阶段使用的算法。在并发标记开始时，它认为此时的对象图是一个逻辑快照，标记过程中新分配的对象都认为是活的，确保不丢失对象。为了解决并发修改，使用 pre-write barrier 记录下变动。
- **常见追问**：“RSet 占用内存多吗？如何调优？” 占用内存可能较大，特别是跨 Region 引用多时。可以通过 `-XX:G1RSetRegionEntries` 等参数调整，但一般默认就好。如果滥用大对象可能增加 RSet 压力。
- **自我反思**：G1 的 Mixed GC 是怎么选出需要回收的 Old Region？通过并发标记阶段的统计，垃圾最多的 Region 优先回收。

---

### 面试题4：什么时候选择 Parallel GC，什么时候选择 G1？
**难度**：⭐️⭐️⭐️

**参考答案**：
- **Parallel GC**：追求高吞吐量，暂停时间不敏感的场景，如后台数据处理、科学计算、批量作业。可以充分利用多 CPU 并行收集，设置 `-XX:MaxGCPauseMillis` 提供一个软目标，但不保证。如果堆小（<4GB）且非响应优先，Parallel 表现很好。
- **G1 GC**：追求可控的延迟，提供较可预测的暂停（通过 `MaxGCPauseMillis` 设定目标）。适用于大堆（6GB+），内存占用中等，要求 99 线暂停时间在百毫秒内的服务端应用。G1 也适合替代 CMS 由于 CMS 的碎片和废弃。JDK 9+ 默认就是 G1。
- **关键权衡**：吞吐量 vs 延迟。Parallel 的吞吐量通常高于 G1，因为 G1 多维护了 RSet 和并发标记的开销（大约额外 5-10% CPU）。
- **常见追问**：“如果堆只有 2GB，用 G1 合适吗？” 不太合适，G1 的最小堆建议 4GB+，小堆 Region 划分细碎，维护成本可能超过收益。
- **自我反思**：你能解释 `-XX:MaxGCPauseMillis` 设置太小会导致什么？导致 G1 收集的 Old Region 太少，老年代逐渐满，最终 Full GC。

---

### 面试题5：如何分析 GC 日志？出现什么现象说明需要调优？
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：
- **分析指标**：
  - **GC 频率**：Young GC 是否过于频繁（如每秒几次），说明新生代可能太小。
  - **晋升量**：每次 Young GC 后老年代增长量，若持续增长超过预期，可能存在过早晋升或大对象过多。
  - **Full GC / Mixed GC**：频繁 Full GC 且回收效果差（老年代使用率居高不下），可能是内存泄漏；若 CMS/G1 频繁发生 concurrent mode failure 或 Full GC，需调低启动阈值或加大堆。
  - **暂停时间**：实际暂停是否大幅超过设定的目标 (`MaxGCPauseMillis`)。
- **典型需调优现象**：
  - 持续增长的 Full GC 后老年代占用依然高 → **内存泄漏**。
  - CMS `concurrent mode failure` 或 G1 `Pause Full` 频发 → **增加堆 / 降低 Initiating Heap Occupancy / 调整 Region 大小**。
  - 系统吞吐量下降，GC 占用 CPU 高 → **可能是并发收集线程太多或堆过小导致频繁 GC**。
- **工具**：`GCViewer`、`GCEasy`、`jstat`、日志可视化。
- **自我反思**：你所在项目的 GC 日志是否被集中采集和监控？如何快速基于日志判断是否发生内存泄漏？

---

> 今天你化身 GC 调优师，从 Serial 到 G1 遍历了一遍 JVM 垃圾收集器演进史。记住，没有银弹收集器，只有与业务场景最匹配的那一款。明天我们将用这些知识走进 BIO/NIO 模型，手写一个非阻塞聊天室。





## 四种垃圾收集器对比

为了让你真正“看一遍就会”，我把这四种收集器的内部原理拆解得非常细。我们按照它们最经典的工作模式来讲解：**Serial 对应新生代的复制算法 + 老年代的标记-整理；Parallel 对应多线程版的复制/整理；CMS 对应并发标记清除；G1 则是区域化并发回收**。我们一步一步看。

---

### 🟢 一、Serial 收集器——最简单纯粹的单线程收集器

Serial 是“单线程”的意思，工作期间必须 **“Stop The World”**（暂停所有用户线程）。它分为两个搭档：

*   **Serial**（新生代）：使用**标记-复制**算法。
*   **Serial Old**（老年代）：使用**标记-整理**算法。

我通过一次完整的 **Minor GC** + **Full GC** 来描述它的原理。

#### 1. 新生代 Minor GC 全过程（复制算法）

假设新生代内存布局为：一个 `Eden` 区和两个 `Survivor` 区（`From` 和 `To`），默认比例 `8:1:1`。新对象都在 Eden 区分配。

*   **触发时机**：Eden 区满了，再分配新对象时触发 Minor GC。
*   **Stop The World**：所有 Java 应用线程全部暂停，直到 GC 结束。
*   **单线程工作流程**：
    1.  **标记存活对象**：从 GC Roots（栈帧中的本地变量、静态变量等）出发，找到 Eden 区和当前 `From` Survivor 区里所有被引用的对象，将它们标记为“存活”。
    2.  **复制存活对象**：把这些存活对象**逐个**复制到空闲的 `To` Survivor 区，并**保持紧凑排列**（没有碎片）。同时，每复制一次，将该对象的“年龄”字段 +1。
    3.  **处理年龄达标对象**：如果某个对象年龄超过了 `-XX:MaxTenuringThreshold`（默认 15），则直接复制到**老年代**，而不是 `To` Survivor。
    4.  **清理**：清空整个 Eden 区和 `From` Survivor 区（里面剩下的都是垃圾，直接抹掉）。
    5.  **角色互换**：原来的 `To` Survivor 区变成新的 `From`，原来的 `From` 区变成新的 `To`，下一次 Minor GC 时，新 `To` 区是空的。
*   **分配担保**：如果在复制时 `To` Survivor 区空间不够，或者某些对象年龄太大，这些对象会通过**空间分配担保**机制直接进入老年代。担保失败则可能触发 Full GC。

整个过程很清晰：**把活对象紧凑复制到一块空区域，然后原区域全部清空**。因为只有一根线程在做，所以没有任何锁竞争，**对单核 CPU 非常高效**，但在多核下就浪费了其他核心。

#### 2. 老年代 Full GC 全过程（标记-整理算法）

当老年代空间不足、元空间（方法区）不足或担保失败时，会触发 Full GC（或叫 Major GC），Serial Old 上场。

*   **Stop The World**：同样全程暂停用户线程。
*   **单线程工作流程**：
    1.  **标记**：从 GC Roots 出发，遍历整个老年代中的对象图，标记所有存活对象。老年代里全是有可能活很久的对象。
    2.  **整理（滑动压缩）**：将所有存活对象向内存的**一端**移动，并紧凑排列。例如移动到老年代的起始位置，过程会更新所有指向这些对象的引用。
    3.  **清除边界**：清理掉边界以外的所有空间。此时老年代剩余空间是连续的，无碎片。

**缺陷**：
*   单线程，在大内存下停顿时间会非常长（可能几十秒）。
*   复制和整理都需要大量移动对象，STW 时间与堆中存活对象数量成正比。

**适用场景**：桌面 UI 应用（Client 模式），或微型服务。现在几乎只作为其他收集器的后备方案。

---

### 🔵 二、Parallel 收集器——追求吞吐量的多线程收集器

Parallel 可以理解为 **Serial 的多线程版本**，同样需要 STW，但新生代和老年代的 GC 都使用**多条线程并行执行**。它的组合是：

*   **Parallel Scavenge**（新生代）：多线程**标记-复制**。
*   **Parallel Old**（老年代）：多线程**标记-整理**。

它的目标是 **吞吐量** = 运行用户代码时间 / (运行用户代码时间 + GC 时间)，可以配置 `-XX:MaxGCPauseMillis` 和 `-XX:GCTimeRatio`。

#### 1. 新生代 Parallel Scavenge 原理

和 Serial 的复制算法类似，只是所有步骤都使用多线程并发（仍然是 STW）。

*   **多线程工作**：将 Eden 区和 `From` Survivor 区的存活对象**复制**到 `To` Survivor 区或老年代时，使用多个 GC 线程**并行**进行标记和复制。因为复制时需要将对象移动到新地址，会有线程安全的问题，所以实际上会将工作拆分为**任务**，每个线程负责一批对象，使用**工作窃取**等机制来平衡。
*   **自适应调节**：Parallel Scavenge 有一个重要特性 **Adaptive Size Policy**。如果开启 `-XX:+UseAdaptiveSizePolicy`，JVM 会根据你设置的暂停时间和吞吐量目标，**动态调整**新生代大小（`-Xmn`）、Eden 与 Survivor 比例（`-XX:SurvivorRatio`）以及对象晋升老年代的年龄阈值等。这是 Parallel 区别于其他收集器的一大特点。

#### 2. 老年代 Parallel Old 原理

和 Serial Old 的标记-整理类似，也是多线程并行。

*   **多线程标记**：从 GC Roots 出发，多个线程同时遍历老年代对象图，标记存活对象。
*   **多线程整理**：将存活对象向老年代一端移动。这个过程需要分成多个区域，每个线程负责一个区域的整理，并处理边界引用。

**总结**：Parallel 在 STW 期间用更多 CPU 资源来**快速完成 GC**，从而让应用尽快恢复运行，**追求的是单位时间内应用运行时间最大化**（吞吐量）。但它依然存在**长时间 STW** 的问题，因为当堆很大时，并行标记和整理仍然会暂停很久。

**适用场景**：批处理、科学计算、后台服务等对停顿时间不敏感但要求高吞吐量的场景。

---

### 🟡 三、CMS 收集器——最短停顿时间的并发收集器

CMS（Concurrent Mark Sweep）是老年代收集器，新生代必须配合 **ParNew**（多线程复制，STW）。CMS 的设计思想是：让 GC 线程和用户线程**大部分时间并发执行**，从而大幅缩短 STW。

CMS 老年代回收使用**标记-清除**算法，分四个主要阶段（以及一个可选的预清理阶段）。下面详细走一遍。

#### 阶段 1：初始标记（Initial Mark）—— STW
*   **目的**：标记从 **GC Roots 能直接引用到的老年代对象**，以及被年轻代引用的老年代对象。
*   **过程**：仅扫描 GC Roots 直接关联的对象，速度非常快。
*   **暂停**：需要 STW，但时间极短。

#### 阶段 2：并发标记（Concurrent Mark）—— 并发执行
*   **目的**：从初始标记找到的“根对象”出发，**并发地遍历整个老年代对象图**，标记所有存活对象。
*   **过程**：GC 线程和应用线程同时运行，耗时最长（例如遍历整个堆）。在这个阶段，因为用户线程还在跑，**可能会产生新的垃圾（浮动垃圾），或者某些对象的引用关系发生变化**。
*   **核心问题**：如何应对并发期间引用变化？**三色标记法**和**写屏障 + 卡表**。
    *   在并发标记期间，对象被标记为黑色（自身及字段已扫描）、灰色（自身已扫描，但字段未扫描）、白色（未扫描）。如果用户线程在标记期间将一个黑色对象的引用指向一个白色对象，且没有灰色路径可达，白色对象可能会被漏标记（存活对象被错误回收）。
    *   CMS 通过 **增量更新（Incremental Update）** 打破这个条件：当黑色对象新增一个指向白色对象的引用时，通过**写屏障**将黑色对象重新标记为灰色，并记录到一个 **mod-union table** 中，这样在重新标记阶段会重新扫描这些对象。写屏障会记录跨代引用的卡表标记为脏。
*   **注意**：这个阶段会产生**浮动垃圾**，因为用户线程在标记结束后还会产生新垃圾，本次 GC 无法回收，只能等下次。

#### 阶段 3：重新标记（Remark）—— STW
*   **目的**：修正并发标记期间，由于用户线程运行而导致的**漏标、错标**问题。
*   **过程**：STW，但通常可使用多线程并行（`-XX:+CMSParallelRemarkEnabled`）以缩短时间。它会扫描：
    *   新生代的所有对象（因为年轻代可能会引用老年代对象，导致并发标记时没有标记到）。
    *   之前在并发标记阶段记录的 `mod-union table`（那些被写屏障标记为脏的卡），重新扫描发生变更的引用。
*   **耗时**：比初始标记长，但比并发标记短很多。

#### 可选阶段：并发预清理（Concurrent Preclean）
在重新标记之前，CMS 可以并发地做一些工作，以减少重新标记时要扫描的新生代范围。它扫描并发标记阶段被标记为脏的卡，尝试预先处理一些引用变化，这样重新标记时新生代扫描量变小。

#### 阶段 4：并发清除（Concurrent Sweep）—— 并发执行
*   **目的**：将标记为死亡的对象清除，回收空间。
*   **过程**：GC 线程与应用线程同时运行，将未标记的对象空间回收，生成空闲列表（free list）。
*   **后果**：由于是标记-清除，**不会移动存活对象**，导致老年代内存空间**产生大量碎片**。分配大对象时，可能找不到连续空间，从而提前触发 **Full GC（Serial Old 做后备）**，导致长时间 STW。

#### CMS 的致命缺陷
1.  **内存碎片**：只能靠 Full GC 的标记-整理来解决，那恰恰是想避免的。
2.  **对 CPU 资源敏感**：并发阶段占用 CPU 核心，会与应用线程争抢，在低核心数服务器上吞吐量下降明显。
3.  **浮动垃圾**：必须留出老年代的部分空间给并发阶段新产生的对象。如果预留不足，可能触发**并发模式失败（Concurrent Mode Failure）**，JVM 会立即用 Serial Old 进行一次 Full GC，导致大停顿。
4.  **扫描新生代**：重新标记时，为了找到老年代中被年轻代引用的对象，需要扫描整个新生代。如果新生代很大，重新标记停顿会变长。

**适用场景**：强调低停顿的互联网 Web 应用，但已被 G1 取代。

---

### 🟠 四、G1 收集器——可预测停顿的区域化收集器

G1（Garbage-First）完全改变了堆的布局。它把堆划分为**许多大小相等的 Region**（可通过 `-XX:G1HeapRegionSize` 指定，必须是 2 的次幂，1~32MB）。每个 Region 逻辑上可以属于 Eden、Survivor、Old 或 Humongous（存放大对象）。G1 依然是**分代收集**，但不再物理连续。

#### G1 的两个核心特性
1.  **垃圾优先**：它会追踪每个 Region 里垃圾堆积的价值（回收能获得多少空间及所需时间），优先回收价值最高的 Region。
2.  **可预测的停顿**：你可以用 `-XX:MaxGCPauseMillis=200` 指定目标停顿时间，G1 会尽量在该时间内只回收一部分老年代 Region，而不是全堆回收。

#### 关键数据结构
*   **Remembered Set (RSet)**：每个 Region 都有一个 RSet，记录了**其他 Region 指向本 Region 内部的引用**。例如，老年代 Region A 的一个对象引用了年轻代 Region B 的对象，那么 Region B 的 RSet 会记录下 Region A 的哪些 Card 包含了这个引用。这样在回收年轻代时，不需要扫描整个老年代，只需要扫描 RSet 即可找到 GC Roots，极大减少扫描时间。RSet 是通过**写后屏障**来维护的。
*   **Card Table**：全局的卡表，粒度更粗，是 RSet 的基础。

#### G1 的回收动作

G1 有几种类型的 GC：**Young GC**、**Mixed GC**，以及后备的 **Full GC**。

##### 1. Young GC（STW，多线程复制）
*   **触发**：Eden Region 满了。
*   **过程**：
    1.  **扫描 GC Roots** 和 **RSet**：从 GC Roots 以及年轻代各 Region 的 RSet 中记录的来自老年代/其他区域的引用，找出所有存活对象。
    2.  **复制**：将存活对象复制到空闲的 Survivor Region（或老年代 Region，若年龄达到），并**紧凑排列**。
    3.  **更新引用**：处理复制后的引用，包括更新老年代引用这些年轻代对象的指针，通过写屏障和 RSet 维护一致性。
    4.  **回收 Region**：清空原来的 Eden 和部分 Survivor Region，变成空闲。
*   这与普通的复制算法类似，但因为有 RSet，避免了全堆扫描老年代。

##### 2. 并发标记周期（Concurrent Marking）
当老年代整体占用达到 `-XX:InitiatingHeapOccupancyPercent`（默认 45%）时，G1 会启动并发标记周期，目的是**为 Mixed GC 找到要回收的高价值老年代 Region**。

采用 **SATB（Snapshot-At-The-Beginning）** 算法，保证并发标记的正确性：
*   **思想**：在并发标记开始前，对当时的对象图做一个逻辑“快照”，保证在这个快照中存活的对象都能在本次标记中被标记为活。如果并发期间某个对象被断开了引用，但根据快照它是活的，那它不会被当垃圾（可能成为浮动垃圾，但安全）。SATB 通过写前屏障将旧值记录到 SATB 队列。
*   这样重新标记阶段只需要处理 SATB 队列，不需要像 CMS 那样重新扫描整个新生代，**停顿更短**。

**并发标记步骤：**
1.  **初始标记**（STW）：借道一次 Young GC，在这次 YGC 的 STW 中完成对老年代的根扫描，速度极快。
2.  **根区域扫描**（并发）：扫描 Survivor Region 中对老年代的引用。
3.  **并发标记**（并发）：遍历对象图，利用 SATB 记录引用变化。
4.  **重新标记**（STW）：处理 SATB 队列中的记录，完成最终标记，**此阶段很短**。
5.  **筛选回收**（Cleanup，STW）：统计每个 Region 的存活对象，计算回收价值和成本，**决定要回收哪些老年代 Region**（构建回收集合，Collection Set，简称 CSet），并清空一些纯垃圾 Region。

##### 3. Mixed GC（STW，多线程复制/整理）
*   **触发**：并发标记周期结束后，G1 根据目标停顿时间，**分多次**执行 Mixed GC。
*   **过程**：一次 Mixed GC 除了回收所有年轻代 Region，还会从 CSet 中挑选一部分**高价值的老年代 Region** 进行回收。
*   **回收方式**：把选定老年代 Region 中的存活对象**复制**到新的空闲 Region，并压缩，然后回收这些老年代 Region。整个过程 STW，但**只针对部分老年代**，所以停顿可以控制在目标内。多次 Mixed GC 后，老年代垃圾基本回收完毕，然后进入下一个并发标记周期。

#### 巨型对象（Humongous Object）
占用超过单个 Region 一半大小的对象。G1 会为它们分配连续的多个 Region 作为 Humongous Region。这类对象直接分配在老年代，只有在 Full GC 或 Mixed GC 时才能回收，容易造成碎片。

#### Full GC
当内存碎片严重、无法分配巨对象，或 Mixed GC 跟不上分配速度时，G1 会回退到 **Serial Old** 进行单线程 Full GC，STW 极长。所以要避免。

**G1 相比 CMS 的改进：**
*   **无内存碎片**：通过复制和整理，保证不会产生碎片。
*   **可预测停顿**：通过只回收部分 Region 来控制停顿。
*   **无需扫描整个新生代**：重新标记使用 SATB，不需要全新生代扫描，停顿更可控。

**G1 的缺点：**
*   维持 RSet 的写屏障开销比较高，会占用一些 CPU 和内存（通常占堆的 10-20%）。
*   在极低延迟场景（如要求 10ms 以内）下，Mixed GC 的 STW 依然可能偏长，这才催生了后续的 ZGC 和 Shenandoah。

---

### 💎 四种收集器的核心原理对比

| 收集器       | 新生代算法              | 老年代算法                                 | 并发/并行              | 关键机制                        | 核心目标     |
| :----------- | :---------------------- | :----------------------------------------- | :--------------------- | :------------------------------ | :----------- |
| **Serial**   | 单线程复制 (STW)        | 单线程标记-整理 (STW)                      | 无并发，全 STW         | 简单直接，无锁                  | 单核高效     |
| **Parallel** | 多线程复制 (STW)        | 多线程标记-整理 (STW)                      | 多线程并行，全 STW     | 自适应策略 (Adaptive Size)      | 高吞吐量     |
| **CMS**      | ParNew 多线程复制 (STW) | 并发标记-清除                              | 老年代并发，缩短 STW   | 三色标记 + 增量更新 + 写屏障    | 最低停顿     |
| **G1**       | 多线程复制 (STW)        | 并发标记 + 多线程复制/整理 (STW, 部分回收) | 并发标记，部分区域 STW | Region + RSet + SATB + 停顿模型 | 可预测的停顿 |

希望这次足够详细，能帮你打好基础。如果你想模拟某个 GC 的完整过程，我也可以带你一步步推演。



是的，业界和社区存在大量公开的实验数据，对不同JVM垃圾收集器的性能指标进行了详尽的测试和对比。这些实验的核心结论是：**没有“最好”的收集器，只有在特定场景下“最合适”的选择**，其本质是在**吞吐量（Throughput）**、**延迟（Latency）** 和**内存占用（Footprint）** 三者之间进行权衡。

为了衡量这些指标，通常会采用性能测试工具（如`wrk`、`ab`）或行业标准基准（Benchmark，如衡量综合性能的SPECjbb® 2015，以及衡量Web应用性能的Renaissance等），并结合JVM日志、`jstat`等工具进行监控。

下面结合经典实验数据，总结四种GC在不同维度的性能表现与差异。

### 📊 综合性能对比一览

下表整合了不同GC在不同场景下的关键性能数据，有助于你对它们的表现有个直观印象。

| 收集器 (GC)  | 典型应用场景                           | 堆大小 (Heap Size) | 吞吐量 (Throughput)   | 平均/最大暂停时间 (Pause Time) | 内存占用特点 (Footprint) | 关键性能数据 (源自实验)                                      |
| :----------- | :------------------------------------- | :----------------- | :-------------------- | :----------------------------- | :----------------------- | :----------------------------------------------------------- |
| **Serial**   | 客户端应用、小型服务、嵌入式设备       | < 256 MB           | 高 (CPU密集场景)      | 平均: 0-50ms, 最大: ~320ms     | 较低，相对固定           | 在某实验中，-Xmx1G时RPS为4283.8，最大停顿21ms                |
| **Parallel** | 后台计算、批处理任务、科学计算         | < 4 GB             | **极高** (吞吐量优先) | 平均: 180-220ms, 最大: 数秒    | 随堆大小增加             | 在某实验中，-Xmx1G时RPS为5294.5，停顿达223ms                 |
| **CMS**      | 对响应时间敏感的Web应用、交互式系统    | 1 - 8 GB           | 较高 (89%+)           | 平均: ~30ms, **最大: 12秒**    | 相对固定，易产生内存碎片 | 在某次高流量实验中，CMS表现出92%的吞吐量，但99分位响应时间飙升至12秒 |
| **G1**       | 大内存服务端应用、需要可预测停顿的场景 | > 4 GB (推荐)      | 较高 (94%+)           | 平均: 50-200ms, 最大: ~250ms   | 较高，随堆大小增加而增加 | 在某实验中，-Xmx1G时RPS为5661.6，停顿达248ms；在32GB堆时，吞吐量比ParallelOld高15% |

> 注意：上表中的性能数据源自特定实验环境，仅用于量化对比，实际表现会因JDK版本、应用代码等因素而异。
>
> *   **RPS (Requests Per Second)**：指每秒处理的请求数，是衡量吞吐量的一个直观指标。
> *   **STW (Stop-The-World)**：指GC事件中，应用线程被完全暂停的阶段，是衡量停顿时间的关键。

### 🟢 详解：不同场景下的性能差异

为了让结论更具参考性，以下数据选取自多个标准化的基准测试和社区实验。

#### 1. Serial GC：单核时代与小内存场景的效率之王

尽管在多核时代显得“过时”，但在特定场景下，Serial GC因无多线程调度开销而效率极高。

*   **小内存吞吐量出色**：在256MB以下的堆内存中，其单线程的简单性带来了非常高的吞吐量，甚至可能超过更复杂的收集器。
*   **实际压测数据**：在1GB堆内存、单线程压测的场景下，Serial GC的吞吐量（RPS）达到了4283.8，最大暂停时间仅为21毫秒，表现稳定。
*   **内存占用固定**：在某次64GB/128GB/200GB大内存对比测试中，Serial GC的内存占用始终固定在32GB左右，不随分配内存增大而增加。

**小结**：Serial GC的优势在于**极小内存和单CPU核心**下的高吞吐量和稳定表现，其劣势是在多核和大内存下无法利用硬件资源，导致长时间停顿。

#### 2. Parallel GC：追求极致吞吐量的计算利器

Parallel GC的设计哲学是“不惜一切代价完成GC”，以最大化应用吞吐量。

*   **吞吐量之王**：在2019年对Spark大数据应用的学术评估中，Parallel GC相比CMS和G1，展现出了最高的吞吐量。另一个基准测试也明确指出，在JDK8中，**ParallelGC在计算密集型任务下是最快的**。
*   **以“长停顿”换“高吞吐”**：它的高吞吐量是以长时间STW为代价的。在1GB堆内存的压测中，其吞吐量（RPS）为5294.5，但最大暂停时间达到了**223毫秒**。在生产环境的极端案例中，其Full GC甚至可能导致**长达15秒**的停顿。
*   **内存随堆增长**：其内存占用会随着`-Xmx`配置的增加而线性增加。

**小结**：Parallel GC是**对停顿不敏感的后台任务**的最佳选择，能为计算密集型应用榨取最高的吞吐量，不适合对响应时间有严格要求的场景。

#### 3. CMS GC：低延迟的先驱及其历史局限性

作为首个并发收集器，CMS的目标是实现最短的回收停顿时间，但它也带来了新的问题。

*   **内存碎片——阿喀琉斯之踵**：CMS使用“标记-清除”算法，不进行内存压缩，长时间运行后会导致严重的内存碎片。
*   **从低延迟到灾难性停顿**：内存碎片在关键时刻会引发灾难性的Full GC。一篇关于高流量系统的回顾文章提到，CMS虽然能实现92%的吞吐量，但在GC期间，其**99分位响应时间会飙升至惊人的12秒**。
*   **内存与CPU的隐性成本**：CMS的内存占用相对固定，约43GB。但它的并发标记和清理阶段会与应用线程争抢CPU资源，导致应用吞吐量降低。

**小结**：CMS在常规运行下能提供较低的延迟，但**内存碎片导致的不可预测的长停顿是其致命缺陷**，这也是它在JDK14中被彻底移除的主要原因。

#### 4. G1 GC：平衡与可预测的艺术

G1旨在吸取CMS的教训，在提供低延迟的同时，解决内存碎片问题，并提供更“可预测”的性能表现。

*   **可预测的停顿模型**：这是G1的核心优势。用户可通过`-XX:MaxGCPauseMillis`设定目标，G1会尽力调整回收行为来满足该目标。在一次实验中，当堆内存为1GB时，其最大暂停时间为248ms；在另一个对比实验中，它的平均暂停时间约为54ms。
*   **内存碎片问题的终结者**：G1通过将堆划分为多个Region，并以“复制-整理”的方式回收，从根本上解决了CMS的内存碎片问题。
*   **吞吐量与延迟的权衡**：G1的吞吐量虽非最高，但在大堆上表现优异。有实验称在32GB堆下，其吞吐量比ParallelOld高15%。但同时，G1需要维护复杂的RSet，这会带来额外CPU开销，因此在CPU密集的小堆场景下，其吞吐量可能垫底。
*   **能充分利用大内存**：与Serial和CMS的内存占用趋于固定不同，G1能随着`-Xmx`的增加而使用更多内存，以实现更好的性能。在200GB的超大堆测试中，它的响应时间是最低的，表现出了最佳性能。

**小结**：G1是**目前服务器端应用的主流选择**，它在吞吐量、延迟和内存占用之间取得了很好的平衡，并解决了CMS的内存碎片问题，但为了维护其预测模型，会消耗额外的CPU和内存资源。

### 🚀 进阶视角：后起之秀的冲击

在追求极致性能的路上，ZGC和Shenandoah作为新一代收集器，表现出了颠覆性的能力。

*   **ZGC：吞吐量与延迟的微妙权衡**
    *   **亚毫秒级延迟**：ZGC的目标是让GC停顿时间**低于10毫秒**，且不随堆大小增加而增加。
    *   **吞吐量代价**：这种极致低延迟的代价是约**5%-15%的吞吐量损失**，且内存占用略高，约为堆大小的1.3倍。

*   **Shenandoah：ZGC的有力竞争者**
    *   **同样极致的低延迟**：Shenandoah的目标与ZGC相似，也是实现亚毫秒级的停顿。
    *   **不同场景互有胜负**：与ZGC相比，各有千秋。一项针对Apache Cassandra的基准测试显示，**Shenandoah的99分位延迟最低**，但在高吞吐率下性能衰减较快。

业界已涌现出许多更具针对性的基准测试，从多维度评估不同GC的实际表现，你可以持续关注这些最新的测试方法和结论。

### 💎 总结：选择最适合你的“引擎”

这些实验数据清晰地揭示了GC的选择没有“银弹”，一切都与你的应用场景有关。为了帮助你更好地进行选型，这里有一份简单的决策指南：

*   **如果你是客户端应用或微型服务**：追求资源的最小化占用，**Serial GC** 是稳妥的选择。
*   **如果你的应用是后台批处理任务**：对停顿时间不敏感但追求极致的计算吞吐量，**Parallel GC** 是最佳选择。
*   **如果你的应用是4GB以上的服务端应用**：需要平衡吞吐量和延迟，避免不可控的长停顿，**G1 GC** 是当前主流和首推的方案。
*   **如果你的应用是TB级内存的实时系统**：对延迟有亚毫秒级要求，愿意接受部分吞吐量牺牲，**ZGC** 或 **Shenandoah** 是必选项。





# 第 18 天：BIO/NIO 模型对比与小型聊天室
本日掌握：手写一个基于 NIO Selector 的非阻塞聊天室，对比 BIO 的阻塞模型，理解 `epoll` 的 LT/ET 及多路复用原理  
覆盖原理点：23 (BIO/NIO/AIO 模型与使用)  
阶段：使用期

## 🎯 今日目标
- 能写出一个多线程 BIO 服务端和客户端，感受阻塞式 IO 的线程资源消耗。
- 能基于 `Selector`、`ServerSocketChannel`、`SocketChannel` 实现一个单线程的非阻塞聊天室。
- 能解释 `Selector` 多路复用、`SelectionKey` 的事件类型（OP_ACCEPT/READ/WRITE）。
- 能说出 `epoll` 的水平触发（LT）与边缘触发（ET）的区别，并映射到 Java NIO 中的行为。
- 能在面试中清晰对比 BIO、NIO、AIO 的优缺点及适用场景。

---

## 📝 练习1：基础用法——传统 BIO 服务端与客户端（必做）

### 业务场景
你需要搭建一个简单的 TCP 回声服务器，客户端发送消息，服务端收到后原样返回。先用 BIO 实现，体会每连接一线程的弊端。

### 你的任务
1. 实现 `BioServer` 类：绑定端口，`accept()` 阻塞等待客户端连接，为每个连接创建一个新线程处理。线程内使用 `BufferedReader` 和 `PrintWriter` 进行读取和回写。
2. 实现 `BioClient` 类：连接服务器，发送几条消息，接收并打印服务器的回声。
3. 启动服务端，然后用多个客户端（或多线程模拟）并发连接，观察服务端创建的线程数量。
4. 尝试连接数超过一定量（如 1000）时，线程数爆炸导致系统资源紧张甚至 OOM。
5. （可选）用 `jstack` 查看线程，大量线程阻塞在 `read()` 上。

### ⚡ 关键提示
- `ServerSocket.accept()` 阻塞直到有新连接。
- `InputStream.read()` 阻塞直到有数据可读。
- 每个连接一个线程：`new Thread(handler).start()`。
- 注意在 finally 中关闭 socket，避免资源泄露。
- BIO 模型简单，但无法支撑高并发，因为线程调度和内存开销大。

### ✍️ 动手写代码
```java
// BioServer.java
ServerSocket serverSocket = new ServerSocket(8888);
while (true) {
    Socket socket = serverSocket.accept(); // 阻塞
    new Thread(() -> {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            String msg;
            while ((msg = in.readLine()) != null) {
                out.println("ECHO: " + msg);
            }
        } catch (IOException e) { ... }
    }).start();
}
```

### ✅ 自我检查
- [ ] 启动服务端和多个客户端，服务端是否为每个连接创建了一个线程？
- [ ] 若客户端不发送数据，线程是否一直阻塞在 `readLine()` 上？
- [ ] 如果大量客户端只连接不发数据，服务器线程数会不会暴增？

### 📖 参考实现（直接展示）

**BIO 服务端：**
```java
import java.io.*;
import java.net.*;

public class BioServer {
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(8888);
        System.out.println("BIO Server started on port 8888");
        while (true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("New client: " + clientSocket.getRemoteSocketAddress());
            new Thread(() -> {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                     PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {
                    String line;
                    while ((line = in.readLine()) != null) {
                        System.out.println("Received: " + line);
                        out.println("Server Echo: " + line);
                    }
                } catch (IOException e) {
                    System.err.println("Client disconnected");
                }
            }).start();
        }
    }
}
```

**BIO 客户端：**
```java
import java.io.*;
import java.net.*;

public class BioClient {
    public static void main(String[] args) throws IOException {
        Socket socket = new Socket("localhost", 8888);
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
        String userInput;
        while ((userInput = console.readLine()) != null) {
            out.println(userInput);
            System.out.println(in.readLine());
        }
    }
}
```

**设计思路**  
- BIO 模型为每个连接分配一个线程，线程阻塞在 `read()` 上，CPU 利用率低，并发连接数受限于线程数和内存。  
- 适用于连接数少且稳定的场景，例如早期的企业内部系统。

### 🐞 常见错误预警
- 忘记关闭 socket 导致文件描述符泄漏。
- 客户端使用 `readLine()` 要求服务端发送换行符，否则客户端会一直阻塞。

---

## 📝 练习2：中级用法——NIO 单线程多路复用回声服务器（必做）

### 业务场景
为了实现高并发连接，我们改用 NIO，让单个线程管理多个连接，通过 `Selector` 监听多个 Channel 的事件。

### 你的任务
1. 实现 `NioServer`：
   - 打开 `ServerSocketChannel`，绑定端口，设为非阻塞。
   - 打开 `Selector`，将 ServerSocketChannel 注册到 Selector，监听 `OP_ACCEPT`。
   - 循环调用 `selector.select()` 获取就绪的事件集合。
   - 如果是 `OP_ACCEPT`：接受连接，将新 `SocketChannel` 设为非阻塞，注册到 Selector，监听 `OP_READ`。
   - 如果是 `OP_READ`：从 Channel 读取数据，并回写（可监听 `OP_WRITE` 或直接写）。注意处理连接关闭。
2. 实现 NIO 客户端（可复用 BIO 客户端，因为客户端通常是阻塞的）。
3. 测试：启动服务器，用多个客户端连接并发消息，验证单线程处理所有连接。

### ⚡ 关键提示
- `ServerSocketChannel.accept()` 在非阻塞下，若无连接返回 null，不会阻塞。
- `SocketChannel.read(ByteBuffer)` 返回 -1 表示对端关闭连接。
- 写入前可能需要调用 `buffer.flip()`，写入后 `buffer.compact()` 或 `clear()`。
- 注意处理粘包/半包：可简单按行读取，或使用固定长度。
- Java NIO Selector 在 Linux 上基于 `epoll`，默认水平触发（LT）。

### ✍️ 动手写代码
```java
// NioServer.java
Selector selector = Selector.open();
ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
serverSocketChannel.bind(new InetSocketAddress(8888));
serverSocketChannel.configureBlocking(false);
serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
while (true) {
    selector.select();
    Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
    while (keyIterator.hasNext()) {
        SelectionKey key = keyIterator.next();
        keyIterator.remove();
        if (key.isAcceptable()) { ... }
        else if (key.isReadable()) { ... }
    }
}
```

### ✅ 自我检查
- [ ] 服务端是否只使用了一个线程（main 线程）处理所有客户端？
- [ ] 多个客户端同时发送消息，服务端是否能正确接收并回显？
- [ ] 客户端关闭连接后，服务端是否正确捕获 `-1` 并关闭对应的 Channel？

### 📖 参考实现（直接展示）

```java
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;

public class NioServer {
    public static void main(String[] args) throws IOException {
        Selector selector = Selector.open();
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(8888));
        serverChannel.configureBlocking(false);
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        System.out.println("NIO Server started on port 8888");

        while (true) {
            selector.select(); // 阻塞直到有事件
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();

                if (key.isAcceptable()) {
                    ServerSocketChannel server = (ServerSocketChannel) key.channel();
                    SocketChannel client = server.accept();
                    if (client != null) {
                        client.configureBlocking(false);
                        client.register(selector, SelectionKey.OP_READ);
                        System.out.println("New connection: " + client.getRemoteAddress());
                    }
                } else if (key.isReadable()) {
                    SocketChannel client = (SocketChannel) key.channel();
                    ByteBuffer buffer = ByteBuffer.allocate(256);
                    try {
                        int bytesRead = client.read(buffer);
                        if (bytesRead == -1) {
                            client.close();
                            System.out.println("Client disconnected");
                        } else {
                            buffer.flip();
                            // 回显
                            client.write(buffer);
                            buffer.clear();
                        }
                    } catch (IOException e) {
                        client.close();
                    }
                }
            }
        }
    }
}
```

**设计思路**  
- 单线程通过 `Selector` 监听所有 Channel，事件驱动的方式避免了大量线程阻塞。  
- `select()` 返回就绪的通道数，遍历 `selectedKeys`，处理完后必须手动移除 key。  
- `ByteBuffer` 作为数据容器，读写模式需使用 `flip`/`clear`。  
- 这是 Reactor 模型的单线程版本，适合连接数多但每个连接负载不大的场景。

### 🐞 常见错误预警
- **忘记移除 selectedKey**：下次循环该 key 可能仍被处理，导致重复操作或异常。
- **在 OP_READ 中直接写入可能阻塞**：如果写缓冲区满，可注册 `OP_WRITE`，在可写时再写，避免死等。
- **ByteBuffer 容量不足**：超过限制时需动态扩容或解析处理。

---

## 📝 练习3：高级/探索用法——NIO 小型聊天室（注册中心+广播）

### 业务场景
在回声服务器基础上增加功能：维护所有在线客户端的 `SocketChannel`，当一个客户端发送消息时，服务器将消息广播给所有其他客户端（群聊）。

### 你的任务
1. 在 `NioServer` 中维护 `Set<SocketChannel>` 客户端集合。
2. 当读事件就绪时，读取消息并遍历集合，将消息写入每个客户端（排除发送者，或者包括自己）。
3. 注意线程安全：Selector 线程是单线程，操作集合不需要额外同步。
4. 编写客户端程序，允许多个用户输入昵称并发送消息，接收服务器广播。
5. 测试多个客户端互相聊天，观察消息是否广播成功。

### ⚡ 关键提示
- 发送消息时，可能因对方写缓冲区满而无法一次写完，可以简单处理：封装 `write` 方法循环写入，或注册 `OP_WRITE` 监听到可写时再发送（较复杂）。
- 为每个客户端关联一个用户名字符串，可以在 `SelectionKey.attachment` 中存储。
- 客户端退出时，从集合中移除，并关闭通道。

### ✍️ 动手写代码
```java
// NioChatServer.java
Set<SocketChannel> clients = new HashSet<>();
// ...
if (key.isAcceptable()) {
    SocketChannel client = serverChannel.accept();
    clients.add(client);
    // register...
}
else if (key.isReadable()) {
    SocketChannel client = (SocketChannel) key.channel();
    // read msg
    // broadcast to clients
    for (SocketChannel other : clients) {
        other.write(buffer);
    }
}
```

### ✅ 自我检查
- [ ] 一个客户端发送消息，其他客户端是否都能收到？
- [ ] 客户端断开后，服务器是否从集合中移除？
- [ ] 若某客户端发送消息时写缓冲区满，服务器是否会阻塞？（可能需优化）

### 📖 参考实现（展示核心逻辑）

```java
// 在 NioServer 基础上修改
Set<SocketChannel> clients = new HashSet<>();

// accept 时
client.configureBlocking(false);
client.register(selector, SelectionKey.OP_READ);
clients.add(client);

// read 时
ByteBuffer buffer = ByteBuffer.allocate(1024);
int bytes = client.read(buffer);
if (bytes == -1) {
    clients.remove(client);
    client.close();
    return;
}
buffer.flip();
// 广播
for (SocketChannel other : clients) {
    if (other != client) {
        buffer.rewind(); // 重绕缓冲区以便多次写入
        other.write(buffer);
    }
}
buffer.clear();
```

**设计思路**  
- 群聊核心是维护活跃连接列表，广播消息。  
- 直接写可能阻塞，可使用非阻塞写 + 写缓冲区队列，或简单方式在连接数少的场景直接 `write`。  
- 本实现未处理写半包，生产环境需要加以完善。

### 🐞 常见错误预警
- `buffer.rewind()` 可多次读同一数据，用于多客户端写。
- 直接 `write(buffer)` 可能一次不写完，剩余数据需要保存并注册写事件。

---

## ⚙️ 性能实验：BIO vs NIO 线程数与连接数压力测试

### 实验目标
对比 BIO 和 NIO 在不同并发连接数下的线程占用和响应吞吐量。

### 引导步骤
1. 编写一个测试客户端，可模拟大量连接（例如 1000），发送一条消息并等待响应。
2. 分别启动 BIO 服务端（每个连接一线程）和 NIO 服务端（单线程），使用上述测试客户端连接。
3. 使用 `jconsole` 或 `jstack` 查看活动线程数。
4. 记录 BIO 是否在 500 连接时出现资源瓶颈，NIO 是否平稳。
5. 使用 `top` 观察 CPU 和内存使用。

### 预期现象和解释（直接展示）
- **BIO**：线程数与连接数成正比，当连接数 1000 时，线程数也接近 1000，系统内存大幅消耗（每个线程栈约 1MB），CPU 上下文切换频繁，部分连接可能超时。
- **NIO**：线程数固定为少数几个（如 1-2 个），内存占用平稳，能轻松处理数千连接，CPU 使用率低。但每个连接的处理吞吐量受限于单线程，不适合计算密集型任务。

---

## 🔷 原理探究

### 探究问题
Java NIO 的 `Selector` 在 Linux 上基于 `epoll`，水平触发（LT）和边缘触发（ET）有何区别？Java NIO 默认是哪种？

### 验证方法
查看 OpenJDK 源码中 `EPollSelectorImpl` 的实现，或写测试程序观察在没有消耗数据前，是否每次 `select` 都会返回可读。

### 引导性问题
- LT 模式下，数据未读完是否会反复通知？
- ET 模式下，只通知一次，如何保证数据读完？

### 原理解读（直接展示）
- **水平触发 (LT)**：只要文件描述符仍有数据可读，`epoll_wait` 就会持续通知。Java NIO `Selector` 默认是 LT。优点是不易漏掉事件，编程简单。
- **边缘触发 (ET)**：仅在状态发生变化时通知一次（如数据到达），如果应用程序没有一次读完，后续就不会再通知，直到有新数据到达。Java NIO 也支持 ET，但必须使用非阻塞模式并循环读取直到返回 -1 或 `EWOULDBLOCK`，否则容易丢失数据。
- Java 的 `SelectorProvider` 可配置 ET 模式，但普通 API 默认使用 LT，以简化编程。

---

## 🏢 大厂场景实战：网关服务设计

### 场景描述
设计一个 API 网关，需要同时代理数百个后端服务，维持大量长连接，并对请求进行转发。要求低延迟和高并发。

### 约束条件
- 服务器 CPU 8 核
- 最大并发连接 10000+
- 每个请求延迟要求 < 5ms 转发

### 你的设计任务
选择 IO 模型，设计线程模型（Reactor 多线程），说明如何利用 NIO 和 Selector 实现。

### 常见方案参考及其取舍分析（直接展示）
- **模型选择**：采用 NIO + Reactor 主从多线程模型。Main Reactor 负责接受连接，Sub Reactor 线程池负责读写和转发。
- **实现**：`ServerSocketChannel` 注册到 main Selector，accept 后将 `SocketChannel` 分配到某个 sub Selector 线程，避免单线程瓶颈。
- **优点**：充分利用多核，减少线程切换，高吞吐低延迟。Netty 就是基于此模型。

---

## 🏆 大厂面试题

### 面试题1：BIO、NIO、AIO 的区别是什么？分别适用于什么场景？
**难度**：⭐️⭐️⭐️

**参考答案**：
- **BIO**：同步阻塞，每个连接一个线程，当连接数少且稳定时简单易用。
- **NIO**：同步非阻塞，基于 `Selector` 多路复用，单线程管理多个连接，适合连接数多但数据量小的场景（如即时通讯）。
- **AIO**：异步非阻塞（NIO 2.0），基于回调或 Future，读写都由操作系统完成后通知应用程序，适合连接数多且数据量大、避免用户线程阻塞的复杂场景（如大文件传输）。Java 中 AIO 并不完全成熟，使用较少。

---

### 面试题2：Java NIO 的 Selector 是如何实现多路复用的？
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：
- 操作系统提供 `select`、`poll`、`epoll`（Linux）或 `kqueue`（Mac）等系统调用。Java `Selector.open()` 会根据系统返回对应的实现。
- 在 Linux 上，`Selector` 内部使用 `epoll` 机制：将多个 Channel 的文件描述符注册到 `epoll` 实例，调用 `select()` 时阻塞等待事件通知，内核只会返回那些就绪的 fd，避免了用户态遍历所有 fd。
- 关键点：无差别轮询 vs 事件通知，`epoll` 基于回调效率高。

---

### 面试题3：NIO 中 ByteBuffer 的 position、limit、capacity 的含义？flip 和 clear 做了什么？
**难度**：⭐️⭐️⭐️

**参考答案**：
- `capacity`：缓冲区总容量，不变。
- `limit`：可读写的上界，初始等于 capacity。写模式下 limit=capacity；读模式下 limit=写入的字节数。
- `position`：下一个读写位置。
- `flip()`：从写模式切换到读模式，`limit = position`，`position = 0`。
- `clear()`：准备再次写入，`limit = capacity`，`position = 0`（数据未清除，只是可以被覆盖）。

---

### 面试题4：NIO 中如何处理半包/粘包问题？
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：
- 定义一个消息边界，如定长消息、特殊分隔符、消息头+消息体（带长度字段）。
- 在读取时，将数据累积到每个连接的 `ByteBuffer`，然后循环尝试解析完整消息，解析成功则移除已用数据，`compact()` 保留剩余部分。
- `compact()` 将未处理数据移至 buffer 头部，并调整 position 和 limit 准备继续写。

---

### 面试题5：Reactor 模型和 Proactor 模型分别对应 Java 中的什么？各有什么优缺点？
**难度**：⭐️⭐️⭐️⭐️⭐️

**参考答案**：

- **Reactor**：同步非阻塞模型，应用主动轮询并处理就绪事件（NIO）。Java NIO 的 `Selector` 就是 Reactor 实现。优点：程序控制力强；缺点：复杂的编程模型。
- **Proactor**：异步非阻塞模型，操作系统完成后回调通知（AIO）。Java NIO 2.0 提供 `AsynchronousChannelGroup`。优点：编程更简单，读写完成自动回调；缺点：操作系统支持差异大，Linux 下真正的 AIO 不完善，Java 使用线程池模拟，性能未必优于 NIO。

---

> 今天你亲手构建了一个非阻塞聊天室，深刻体会到 NIO 的威力与复杂性。明天我们将继续深入 NIO Buffer 与零拷贝，感受高性能传输的极限。





# 第 19 天：NIO Buffer 与零拷贝性能对比
本日掌握：掌握 `ByteBuffer` 底层操作、直接内存映射（`MappedByteBuffer`）与 `FileChannel.transferTo` 实现零拷贝，通过压测对比传统 IO 与零拷贝的性能差异  
覆盖原理点：24 (NIO Buffer 与 Channel), 25 (零拷贝实现)  
阶段：使用期

## 🎯 今日目标
- 能熟练操作 `ByteBuffer` 的 position/limit/capacity 三剑客，并正确使用 `flip`、`compact`、`slice`。
- 能用 `FileChannel` 搭配直接内存缓冲区或 `MappedByteBuffer` 实现文件拷贝，理解它与普通 `FileInputStream` 的差异。
- 能用 `transferTo`/`transferFrom` 实现零拷贝文件传输，并说出它和 `mmap` 的区别。
- 能通过微基准测试证明零拷贝在吞吐量和 CPU 占用上的优势，并在面试中解释其底层原理。

---

## 📝 练习1：基础用法——ByteBuffer 的三种模式与常用操作（必做）

### 业务场景
在 NIO 编程中，你频繁使用 `ByteBuffer` 作为数据容器。它有三个重要属性，切换模式时容易写错。今天我们从零开始熟悉它的所有关键 API。

### 你的任务
1. 编写一个测试方法，分配一个 16 字节的 `ByteBuffer`。
2. 依次执行以下操作并打印 position、limit、capacity：
   - 写入 10 个字节（`put`）
   - 调用 `flip()` 进入读模式
   - 读取 5 个字节
   - 调用 `compact()` 保留未读数据并切换回写模式
   - 再写入 6 个字节
   - 再次 `flip` 并一次性读完所有数据
3. 演示 `mark()` 和 `reset()` 的使用：标记某位置，读取部分后回退。
4. 演示 `slice()` 和 `duplicate()` 与原始 Buffer 共享数据的特性（修改子缓冲区，原缓冲区可见）。

### ⚡ 关键提示
- `allocate(16)` 创建堆内存 Buffer，`allocateDirect(16)` 创建直接内存 Buffer。
- `flip()`：`limit = position, position = 0`。
- `compact()`：将未读数据移至开头，`position = remaining()`，`limit = capacity`。
- `slice()` 创建子缓冲区，与原缓冲区共享部分数组；`duplicate()` 共享全部。
- 直接 Buffer 的分配和回收成本高，适合长生命周期大缓冲区。

### ✍️ 动手写代码
```java
ByteBuffer buf = ByteBuffer.allocate(16);
// ... 执行操作并打印状态
```

### ✅ 自我检查
- [ ] 写模式下 position 是不是已写入的字节数？
- [ ] flip 后 limit 是否等于之前 position？
- [ ] compact 后，未读数据是否被拷贝到缓冲区开头？
- [ ] 通过 slice 修改数据，原缓冲区查看对应位置是否变化？

### 📖 参考实现（直接展示）

```java
import java.nio.ByteBuffer;

public class ByteBufferDemo {
    public static void main(String[] args) {
        // 分配
        ByteBuffer buffer = ByteBuffer.allocate(16);
        print("初始", buffer);

        // 写10个字节
        buffer.put("hello world".getBytes());
        print("写入10字节", buffer);

        // flip 读模式
        buffer.flip();
        print("flip后", buffer);

        // 读5个字节
        byte[] dst = new byte[5];
        buffer.get(dst);
        System.out.println("读取到的: " + new String(dst));
        print("读取5字节后", buffer);

        // compact 切换写模式
        buffer.compact();
        print("compact后", buffer);

        // 再写6字节
        buffer.put(" NIO!!".getBytes());
        print("写入6字节", buffer);

        // flip 再读
        buffer.flip();
        print("第二次flip后", buffer);

        // 读完
        byte[] all = new byte[buffer.remaining()];
        buffer.get(all);
        System.out.println("最终数据: " + new String(all));
        print("读完", buffer);

        // mark & reset
        ByteBuffer buf2 = ByteBuffer.allocate(16);
        buf2.put("abcdefgh".getBytes());
        buf2.flip();
        System.out.print("原始: ");
        while (buf2.hasRemaining()) System.out.print((char) buf2.get());
        System.out.println();
        // 重新读，使用 mark
        buf2.rewind();
        System.out.print("前4: ");
        for (int i = 0; i < 4; i++) System.out.print((char) buf2.get());
        buf2.mark(); // 标记
        System.out.print(" 后4: ");
        while (buf2.hasRemaining()) System.out.print((char) buf2.get());
        buf2.reset(); // 回退到 mark
        System.out.print("\n从mark开始: ");
        while (buf2.hasRemaining()) System.out.print((char) buf2.get());
        System.out.println();

        // slice 共享
        ByteBuffer original = ByteBuffer.allocate(10);
        original.put(new byte[]{0,1,2,3,4,5,6,7,8,9});
        original.flip();
        ByteBuffer slice = original.slice(); // 从当前位置开始共享
        slice.put(0, (byte) 99);
        original.rewind();
        System.out.println("修改 slice 后 original[0] = " + original.get(0));
    }

    static void print(String msg, ByteBuffer buf) {
        System.out.printf("%s: position=%d, limit=%d, capacity=%d%n", msg, buf.position(), buf.limit(), buf.capacity());
    }
}
```

**设计思路**  
- 通过逐步打印 position/limit/capacity，直观展示 `flip`、`compact` 对这三个值的影响。  
- `mark/reset` 允许在缓冲区中回溯，是网络解析常用技巧。  
- `slice` 和 `duplicate` 是零拷贝的体现：共享底层内存，不额外分配新数组。

### 🐞 常见错误预警
- **翻转错误**：读数据后忘记 `compact` 就继续 `put`，导致覆盖未读数据。
- **直接缓冲区越界**：`allocateDirect` 创建的缓冲区，`array()` 不可用，会抛 `UnsupportedOperationException`。
- **`slice` 后 position/limit 独立**：修改子缓冲区会影响原缓冲区，可能造成意外污染。

---

## 📝 练习2：中级用法——传统 IO vs NIO FileChannel vs MappedByteBuffer 文件拷贝（必做）

### 业务场景
你需要实现一个高性能的文件拷贝工具，对比传统 `FileInputStream`/`FileOutputStream`、NIO `FileChannel` 使用堆缓冲区、`FileChannel` 使用直接缓冲区、以及 `MappedByteBuffer`（内存映射）四种方式的性能。

### 你的任务
1. 准备一个测试文件（例如 100MB 随机数据）。
2. 分别用以下方式实现拷贝，并测量耗时：
   - **传统 IO**：`FileInputStream` + `FileOutputStream`，每次读 4KB。
   - **NIO Heap Buffer**：`FileChannel.read(byteBuffer)` + `write`，使用 `ByteBuffer.allocate(8192)`。
   - **NIO Direct Buffer**：同上，但使用 `ByteBuffer.allocateDirect(8192)`。
   - **MappedByteBuffer**：使用 `FileChannel.map(FileChannel.MapMode.READ_WRITE, 0, fileSize)` 映射整个文件，然后直接 `put` 到目标文件的映射区，或通过循环复制。
3. 打印每种方式的耗时和吞吐量（MB/s），比较内存占用（通过 `Runtime` 粗略观察）。
4. 分析：直接缓冲区为什么快？内存映射为什么适合大文件？

### ⚡ 关键提示
- `MappedByteBuffer` 不是完全的内存拷贝，而是将文件直接映射到虚拟内存，操作系统负责页面调度，避免用户空间到内核空间的拷贝。
- 直接缓冲区分配在堆外，写操作不经过 JVM 堆，减少了 GC 影响，且可在本地 IO 中直接使用。
- 实验时先运行几次预热，排除 JIT 干扰。
- 大文件使用 `map` 需注意，小文件映射开销可能大于收益。

### ✍️ 动手写代码
```java
// 框架：
long start = System.nanoTime();
// 拷贝操作
long end = System.nanoTime();
System.out.println("耗时: " + (end - start) / 1_000_000 + " ms");
```

### ✅ 自我检查
- [ ] NIO Direct Buffer 是否比 Heap Buffer 更快？为什么？
- [ ] MappedByteBuffer 在处理大文件时是否表现出极高的吞吐？
- [ ] 如果文件太大无法一次映射（超过 2GB），`map` 的限制是什么？

### 📖 参考实现（直接展示）

```java
import java.io.*;
import java.nio.*;
import java.nio.channels.*;

public class FileCopyBenchmark {
    public static void main(String[] args) throws IOException {
        File src = new File("test.dat");
        // 生成测试文件，如果不存在则生成
        if (!src.exists()) {
            System.out.println("生成 100MB 测试文件...");
            byte[] data = new byte[1024 * 1024];
            try (FileOutputStream fos = new FileOutputStream(src)) {
                for (int i = 0; i < 100; i++) fos.write(data);
            }
        }

        File dst1 = new File("dst_io.dat");
        File dst2 = new File("dst_nio_heap.dat");
        File dst3 = new File("dst_nio_direct.dat");
        File dst4 = new File("dst_mmap.dat");

        // 1. 传统 IO
        long t1 = System.nanoTime();
        try (FileInputStream fis = new FileInputStream(src);
             FileOutputStream fos = new FileOutputStream(dst1)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) != -1) fos.write(buf, 0, n);
        }
        long t2 = System.nanoTime();
        System.out.printf("传统 IO: %d ms%n", (t2 - t1) / 1_000_000);

        // 2. NIO Heap Buffer
        long t3 = System.nanoTime();
        try (FileChannel srcCh = new FileInputStream(src).getChannel();
             FileChannel dstCh = new FileOutputStream(dst2).getChannel()) {
            ByteBuffer buf = ByteBuffer.allocate(8192);
            while (srcCh.read(buf) != -1) {
                buf.flip();
                dstCh.write(buf);
                buf.compact();
            }
        }
        long t4 = System.nanoTime();
        System.out.printf("NIO Heap: %d ms%n", (t4 - t3) / 1_000_000);

        // 3. NIO Direct Buffer
        long t5 = System.nanoTime();
        try (FileChannel srcCh = new FileInputStream(src).getChannel();
             FileChannel dstCh = new FileOutputStream(dst3).getChannel()) {
            ByteBuffer buf = ByteBuffer.allocateDirect(8192);
            while (srcCh.read(buf) != -1) {
                buf.flip();
                dstCh.write(buf);
                buf.compact();
            }
        }
        long t6 = System.nanoTime();
        System.out.printf("NIO Direct: %d ms%n", (t6 - t5) / 1_000_000);

        // 4. MappedByteBuffer (整个文件映射)
        long t7 = System.nanoTime();
        try (FileChannel srcCh = new FileInputStream(src).getChannel();
             FileChannel dstCh = new RandomAccessFile(dst4, "rw").getChannel()) {
            long size = srcCh.size();
            MappedByteBuffer srcMap = srcCh.map(FileChannel.MapMode.READ_ONLY, 0, size);
            MappedByteBuffer dstMap = dstCh.map(FileChannel.MapMode.READ_WRITE, 0, size);
            dstMap.put(srcMap); // 利用 put(MappedByteBuffer) 进行批量复制
        }
        long t8 = System.nanoTime();
        System.out.printf("MappedByteBuffer: %d ms%n", (t8 - t7) / 1_000_000);
    }
}
```

**设计思路**  
- 统一 8KB 缓冲区，对比不同 IO 模式的性能差异。  
- `MappedByteBuffer.put(srcMap)` 是将映射区的内容整体拷贝，操作系统会利用页表高效传输，但存在地址空间限制。  
- 直接缓冲区因避免了堆拷贝，性能优于堆缓冲区。

### 🐞 常见错误预警
- `FileChannel.map` 映射的大小不能超过 `Integer.MAX_VALUE`（约 2GB），超大文件需分段映射。
- `MappedByteBuffer` 不会自动释放，直到 GC 回收或显式调用 `((DirectBuffer) buffer).cleaner().clean()`，否则可能导致内存占用高。
- 测试结果会受文件系统缓存影响，可先清除缓存或多次运行取平均。

---

## 📝 练习3：高级/探索用法——零拷贝 `transferTo` / `transferFrom` 及底层原理

### 业务场景
在 Web 服务器或文件服务器中，将磁盘文件直接发送给客户端，无需经过用户态拷贝。`FileChannel.transferTo` 使用了底层的 `sendfile` 系统调用，实现零拷贝。

### 你的任务
1. 使用 `FileChannel.transferTo` 实现一个简易的 HTTP 文件下载服务器（只发送文件内容，忽略 HTTP 头），绑定端口，接受连接后直接将文件发送到 `SocketChannel`。
2. 对比传统方式：读文件到字节数组，再写入 OutputStream。
3. 测量两种方式传输同一个大文件（如 1GB）的 CPU 占用（粗略通过观察 `top`）和时间。
4. 解释 `transferTo` 底层如何通过 `sendfile` 避免内核态和用户态的数据拷贝。
5. （选做）使用 `transferFrom` 实现文件上传零拷贝。

### ⚡ 关键提示
- `transferTo(long position, long count, WritableByteChannel target)` 直接将文件通道中的数据发送到目标通道，中间不经过用户空间。
- 在 Linux 内核 >= 2.6.33 上，`sendfile` 甚至可以在 DMA 引擎协助下将数据从磁盘直接复制到网卡，仅需两次复制（磁盘到内核缓冲区，内核缓冲区到网卡），而传统需要四次。
- 注意 `transferTo` 不一定一次传输全部数据，可能返回小于 count 的值，需要循环处理。
- `SocketChannel` 必须非阻塞？可以阻塞。

### ✍️ 动手写代码
```java
// 文件服务器演示 transferTo
ServerSocketChannel server = ServerSocketChannel.open();
server.bind(new InetSocketAddress(8080));
while (true) {
    SocketChannel client = server.accept();
    FileChannel fileChannel = FileChannel.open(Paths.get("largefile.dat"));
    long size = fileChannel.size();
    long pos = 0;
    while (pos < size) {
        pos += fileChannel.transferTo(pos, size - pos, client);
    }
    fileChannel.close();
    client.close();
}
```

### ✅ 自我检查
- [ ] 使用 `transferTo` 后，CPU 占用率是否显著低于传统 read/write？
- [ ] 传输耗时是否更短？
- [ ] 是否能解释 `sendfile` 和 `mmap+write` 的区别？

### 📖 参考实现与原理（直接展示）

**零拷贝服务器关键代码**：
```java
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.nio.file.*;

public class ZeroCopyServer {
    public static void main(String[] args) throws Exception {
        ServerSocketChannel server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress(8080));
        System.out.println("Zero-copy file server on 8080");
        FileChannel fc = FileChannel.open(Paths.get("largefile.dat"), StandardOpenOption.READ);
        long size = fc.size();
        while (true) {
            try (SocketChannel sc = server.accept()) {
                long pos = 0;
                while (pos < size) {
                    long sent = fc.transferTo(pos, size - pos, sc);
                    if (sent <= 0) break;
                    pos += sent;
                }
                System.out.println("File sent, bytes: " + pos);
            }
        }
    }
}
```

**设计思路**  
- `transferTo` 利用操作系统 `sendfile` 系统调用，数据从文件系统内核缓存直接传递到 socket 缓冲区，无需用户态缓冲区中转，节省了两次数据拷贝和上下文切换。  
- 传统 `FileInputStream`→`OutputStream` 的 `read/write` 循环：磁盘 → 内核缓冲区 → 用户空间缓冲区 → 内核 socket 缓冲区，共四次拷贝和四次上下文切换。  
- `mmap` 方式（映射到共享内存）也是零拷贝，但需要处理映射开销，`sendfile` 在发送文件时更专一。

### 🐞 常见错误预警
- `transferTo` 可能一次传不完，必须循环直到 `position + sent == size`。
- 某些平台 `transferTo` 不支持超过 2GB 的数据，需分段处理。
- 零拷贝并非完全没有 CPU 开销，DMA 操作仍需 CPU 启动，但大幅减少数据搬运的 CPU 消耗。

---

## ⚙️ 性能实验：零拷贝 vs 传统拷贝 CPU 与吞吐对比

### 实验目标
量化 `transferTo` 相对于 `FileInputStream` + `Socket` 写入的性能提升。

### 引导步骤
1. **准备**：生成一个 500MB 的随机文件 `test.data`。
2. **服务器**：分别实现传统拷贝服务器（`FileInputStream` 读，`OutputStream` 写 socket）和零拷贝服务器。
3. **客户端**：编写一个客户端只接收并丢弃数据，计时。
4. **分别运行**，通过 `top -p <pid>` 观察 Java 进程 CPU 使用率，用 `time` 记录完成时间。
5. **记录并对比** CPU 使用率（%CPU）和总耗时。
6. 可增加并发客户端（如 2-4 个同时下载）观察零拷贝的扩展性。

### 预期现象和解释（直接展示）

| 方式            | 传输时间 | CPU 占用 (粗略) |
| --------------- | -------- | --------------- |
| 传统 read/write | 12 秒    | 高 (40-60%)     |
| transferTo      | 10 秒    | 低 (10-20%)     |

- **解释**：零拷贝省去了内核和用户空间的数据拷贝，减少了 CPU 的数据搬运，使得 CPU 可处理更多连接。在大文件传输场景，提升明显。此外，零拷贝也减少了上下文切换次数。

---

## 🔷 原理探究

### 探究问题
`FileChannel.transferTo` 底层调用的 `sendfile` 和 `mmap+write` 分别适用于什么场景？为什么 `sendfile` 在 Java 中一次调用只能传输不到 2GB？

### 验证方法
查阅 Linux man page `sendfile`，编写 Java 代码多次调用 `transferTo` 处理大文件，并测试性能；或者使用 `strace` 跟踪系统调用。

### 引导性问题
- `sendfile` 需要从哪个 fd 向哪个 fd 传？其输入 fd 必须是 `mmap` 兼容的吗？
- `sendfile` 最大传输量为何受 `size_t` 限制？

### 原理解读（直接展示）
- `sendfile` 直接在内核态将文件内容从 Page Cache 传到 socket buffer，不需要映射到用户空间，适合文件不修改的直接发送，如 Web 服务器静态文件。限制：源必须是文件描述符，目标必须是 socket。
- `mmap+write`：将文件映射到用户空间内存，然后调用 `write`，减少了内核到用户空间的拷贝，但仍需要一次用户态到 socket 缓冲区的拷贝。适合需要修改文件数据再发送的场景。
- Java 的 `transferTo` 内部调用 `sendfile`，由于 `sendfile` 的 `count` 参数通常是 `size_t` 类型，在 64 位平台为 8 字节，但 Java 的 `long` 转为 `size_t` 时可能会截断，且某些操作系统限制了单次传输上限（如 2GB），故需分段。

---

## 🏢 大厂场景实战：文件存储网关

### 场景描述
一个企业网盘系统，需要将用户上传的文件持久化存储到后端对象存储（如 MinIO）。网盘服务作为网关接收文件流，再转发到对象存储。要求尽可能降低网关的内存占用和 CPU 开销。

### 约束条件
- 文件大小可达 10GB
- 并发上传数高（如 100 个并发）
- 网关服务器 8 核 16GB 内存

### 你的设计任务
设计文件转发模块，结合 NIO 和零拷贝技术，写出关键接口和流程。

### 常见方案参考及其取舍分析（直接展示）
- **方案A：全内存缓冲**：将上传流全部读入 `byte[]`，再发到后端。→ 大文件 OOM，否决。
- **方案B：NIO 逐块拷贝**：使用 `FileChannel` 或 `SocketChannel` 循环 `read/write` 4KB → 低内存，但 CPU 占用高。
- **方案C：零拷贝中介**：若后端支持 HTTP PUT 或类似，可在网关文件暂存磁盘后，通过 `transferTo` 将临时文件直接发送到后端的 `SocketChannel`。或者利用 Nginx 之类做零拷贝代理。→ 理想方案，但要求网关和后端在同一内核可共享文件页缓存。
- **生产推荐**：使用 Netty 并开启 `FileRegion` 零拷贝发送，或者直接用 Nginx 进行文件上传代理，避免应用层拷贝。

---

## 🏆 大厂面试题

### 面试题1：ByteBuffer 的 position、limit、capacity 含义及 flip 和 compact 的区别
**难度**：⭐️⭐️⭐️

**参考答案**：
- `capacity`：缓冲区总容量，不可变。
- `limit`：第一个不可读写的字节索引。
- `position`：下一个要读写的字节索引。
- `flip()`：写模式 → 读模式，`limit = position`, `position = 0`。
- `compact()`：读模式 → 写模式，将未读数据拷贝到头部，`position` 设为未读数据长度，`limit = capacity`，适合处理半包。

---

### 面试题2：直接缓冲区（Direct Buffer）和堆缓冲区（Heap Buffer）的区别？各有什么优劣？
**难度**：⭐️⭐️⭐️

**参考答案**：
- 堆缓冲区在 JVM 堆内分配，受 GC 管理，拷贝到内核需经过直接内存。直接缓冲区在堆外分配，避免内核-用户态拷贝，IO 效率高。
- 创建和销毁直接缓冲区开销大于堆缓冲区，因为堆外内存不受 GC 直接控制，可能引起内存泄漏（需手动释放或等 `Cleaner` 回收）。
- 使用场景：大文件、高吞吐 IO 用直接缓冲区；小数据临时操作用堆缓冲区。

---

### 面试题3：什么是零拷贝？Java 中如何实现？底层原理是什么？
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：
- 零拷贝指避免 CPU 将数据从一处内存拷贝到另一处内存的技术。在 IO 中，主要指减少内核空间和用户空间之间的数据拷贝。
- Java 实现方式：`FileChannel.transferTo/transferFrom`（调用 `sendfile`）和 `MappedByteBuffer`（调用 `mmap`）。
- `sendfile` 原理（Linux）：文件数据从磁盘到内核缓冲区，然后通过 DMA 直接到网卡缓冲区（或到 socket 缓冲区），不再经过用户空间，极大降低 CPU 开销。
- `mmap` 原理：将文件映射到虚拟内存地址，用户空间和内核空间共享同一页缓存，减少一次拷贝，但仍需调用 `write` 把数据发到 socket。

---

### 面试题4：`FileChannel.transferTo` 有什么局限性？
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：
- 一次调用可能传输不完全（返回实际传输字节数），需循环调用。
- 在部分平台，`transferTo` 传输量有限制（如 2GB - 1 或最多 2GB），超大文件需分段传输。
- 目标 Channel 必须是可写入的 `SocketChannel` 或 `FileChannel`，不能是自定义的 `WritableByteChannel`（某些实现可能不支持）。
- 文件数据如果在传输过程中被修改，可能导致发送不一致（因为 `sendfile` 是基于页缓存快照）。

---

### 面试题5：如何处理 NIO 中的半包和粘包？给出一种设计
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：
- 定义消息边界：固定长度、特殊分隔符、或消息头包含长度字段。
- 处理流程：每个连接维护一个累积缓冲区。从 SocketChannel 读数据追加到缓冲区，然后循环解析完整消息。
  - 若为长度头：先读取 4 字节头获得长度 L，等待缓冲区数据 >= L+4，截取一条消息，`compact` 剩余数据。
- 零拷贝在此处不适用，因为需要解析数据。可使用堆缓冲区处理，避免频繁释放直接缓冲区。

---

> 今天你把 NIO 的 Buffer 玩得炉火纯青，又用零拷贝狠狠压榨了操作系统的 IO 性能。明天我们将探索反射的 Performance，对比 `Method.invoke` 与 `MethodHandle`。





# 第 20 天：反射方法调用与 MethodHandle 基准
本日掌握：深入理解反射的底层开销，对比 `Method.invoke`、`setAccessible`、`MethodHandle` 及直接调用的性能差异，掌握如何在实际项目中优化反射调用  
覆盖原理点：26 (反射机制与开销)  
阶段：原理期

## 🎯 今日目标
- 能使用 `Class` 获取类的各种信息，并通过 `Method.invoke` 调用方法。
- 能解释反射调用的性能开销来源：访问检查、包装/拆箱、JIT 内联限制等。
- 能通过 `setAccessible(true)` 关闭安全检查，提升反射调用效率。
- 能使用 `MethodHandle` 替代传统反射，实现接近原生调用的性能。
- 能设计 JMH 微基准测试，量化不同调用方式的吞吐量，并给出优化建议。
- 能回答面试中关于反射原理、`MethodHandle` 与 `Method` 的区别、反射应用场景及局限的连环追问。

---

## 📝 练习1：基础用法——反射获取类信息与方法调用（必做）

### 业务场景
你正在编写一个通用工具，需要动态地传入类名、方法名和参数，就能调用目标对象的方法。先用 `java.lang.reflect.Method` 实现这一功能。

### 你的任务
1. 创建一个目标类 `Target`，包含方法 `public String sayHello(String name)` 和 `private int add(int a, int b)`。
2. 通过反射获得 `Target.class`：
   - 打印所有声明的方法名。
   - 获取指定方法 `sayHello`，并调用它，打印结果。
   - 获取私有方法 `add`，尝试调用，预期抛出 `IllegalAccessException`。
   - 调用 `setAccessible(true)` 后再次调用私有方法，观察是否成功。
3. 对比正常直接调用和反射调用的代码差异，思考性能影响。

### ⚡ 关键提示
- `Class.getDeclaredMethod(name, parameterTypes)` 获取方法。
- `method.invoke(instance, args)` 执行。
- 反射调用受访问控制限制，私有方法需要 `setAccessible(true)`。
- 每次 `invoke` 都会进行一定的安全检查（除非设置为可访问且缓存 Method 对象）。
- 反射可能抛出 `InvocationTargetException`，它包装了目标方法抛出的异常，需通过 `getCause()` 获取原始异常。

### ✍️ 动手写代码
```java
class Target {
    public String sayHello(String name) { return "Hello " + name; }
    private int add(int a, int b) { return a + b; }
}

public class ReflectBasic {
    public static void main(String[] args) throws Exception {
        Class<?> clazz = Target.class;
        // 打印方法
        for (Method m : clazz.getDeclaredMethods()) {
            System.out.println(m.getName());
        }
        Target target = new Target();
        // 调用 sayHello
        Method sayHello = clazz.getDeclaredMethod("sayHello", String.class);
        String result = (String) sayHello.invoke(target, "world");
        System.out.println(result);
        // 调用私有 add
        Method add = clazz.getDeclaredMethod("add", int.class, int.class);
        // 尝试直接调用会抛异常
        // add.invoke(target, 3, 4);
        add.setAccessible(true);
        int sum = (int) add.invoke(target, 3, 4);
        System.out.println(sum);
    }
}
```

### ✅ 自我检查
- [ ] 反射能否获取到继承自父类的方法？用 `getMethods()` 与 `getDeclaredMethods()` 有什么区别？
- [ ] 为什么反射调用比直接调用慢？至少说出两个原因。
- [ ] `setAccessible(true)` 除了允许访问私有成员，还有什么性能上的作用？

### 📖 参考实现（直接展示）

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ReflectionDemo {
    static class Target {
        public String sayHello(String name) {
            return "Hello " + name;
        }
        @SuppressWarnings("unused")
        private int add(int a, int b) {
            return a + b;
        }
    }

    public static void main(String[] args) throws Exception {
        Class<Target> clazz = Target.class;
        // 打印方法
        System.out.println("Declared methods:");
        for (Method m : clazz.getDeclaredMethods()) {
            System.out.println("  " + m.getName());
        }

        Target obj = new Target();
        // 正常调用
        long start = System.nanoTime();
        String directResult = obj.sayHello("World");
        long end = System.nanoTime();
        System.out.println("直接调用结果: " + directResult + " 耗时: " + (end - start) + "ns");

        // 反射调用 sayHello
        Method sayHello = clazz.getDeclaredMethod("sayHello", String.class);
        start = System.nanoTime();
        String reflectResult = (String) sayHello.invoke(obj, "World");
        end = System.nanoTime();
        System.out.println("反射调用结果: " + reflectResult + " 耗时: " + (end - start) + "ns");

        // 调用私有方法
        Method add = clazz.getDeclaredMethod("add", int.class, int.class);
        add.setAccessible(true);
        int sum = (int) add.invoke(obj, 3, 4);
        System.out.println("反射调用私有 add(3,4) = " + sum);
    }
}
```

**设计思路**  
- 直接比较调用耗时，显然反射慢很多。原因：方法检查、包装/拆箱参数、数组包装、JIT 优化困难。
- `setAccessible(true)` 不仅绕过 Java 访问控制，还绕过了部分安全检查，性能会好一些。
- 实际开发中应缓存 `Method` 对象，避免重复查找。

### 🐞 常见错误预警
- 调用 `invoke` 时参数类型不匹配（如 int 传入 Integer），会抛出 `IllegalArgumentException`。
- `invoke` 返回值需要强制转型，若类型错误抛 `ClassCastException`。
- 目标方法抛出异常时，`InvocationTargetException.getCause()` 才是真实异常。
- 重复调用 `getDeclaredMethod` 会降低性能，应缓存。

---

## 📝 练习2：中级用法——MethodHandle 基本用法与性能对比

### 业务场景
Java 7 引入了 `MethodHandle`，旨在提供一种比反射更直接、更高效的动态调用机制，它被设计为可以被 JIT 优化到接近直接调用的程度。我们将用它重写前面的调用，并感受其用法。

### 你的任务
1. 使用 `MethodHandles.lookup()` 获取 Lookup 对象。
2. 分别找到 `sayHello` 和私有方法 `add` 的方法句柄。
   - 对于 `add`，需用 `findSpecial` 或 `unreflect`（从反射 Method 转换）。
3. 调用 `handle.invokeExact` 或 `handle.invoke` 执行方法。
4. 编写一个简单的性能对比：循环调用 10 万次，比较直接调用、反射调用（缓存 Method）、MethodHandle 调用的耗时。
5. 注意 `invokeExact` 要求参数和返回类型严格匹配，否则抛 `WrongMethodTypeException`。

### ⚡ 关键提示
- `MethodHandles.Lookup` 在 Java 8+ 中需权限，若访问私有方法，`findVirtual` 不能直接获取，可先用反射获取 `Method`，再 `unreflect`。
- `MethodHandle.invokeExact` 是最直接的方式，类型必须完全匹配；`invoke` 则会进行适配，类似反射但更高效。
- `MethodHandle` 是 JSR 292 的一部分，JVM 可以将其内联，性能接近原生调用。
- 预热非常重要：前几次调用 `invokeExact` 可能会因为链接而较慢。

### ✍️ 动手写代码
```java
import java.lang.invoke.*;
import java.lang.reflect.Method;

public class MethodHandleDemo {
    public static void main(String[] args) throws Throwable {
        Target target = new Target();
        // 获取 MethodHandle
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodType type = MethodType.methodType(String.class, String.class);
        MethodHandle sayHelloMH = lookup.findVirtual(Target.class, "sayHello", type);

        // 调用 invokeExact 需要返回类型严格一致
        String result = (String) sayHelloMH.invokeExact(target, "World");
        System.out.println(result);

        // 私有方法通过反射 Method unreflect
        Method addMethod = Target.class.getDeclaredMethod("add", int.class, int.class);
        addMethod.setAccessible(true);
        MethodHandle addMH = lookup.unreflect(addMethod);
        int sum = (int) addMH.invokeExact(target, 3, 4);
        System.out.println(sum);
    }
}
```

### ✅ 自我检查
- [ ] `invokeExact` 和 `invoke` 的区别是什么？
- [ ] MethodHandle 相比反射，主要在哪些环节减少了开销？
- [ ] 为什么 MethodHandle 能比反射更容易被 JIT 优化？

### 📖 参考实现与性能测试（直接展示）

```java
import java.lang.invoke.*;
import java.lang.reflect.Method;

public class MHBenchmark {
    static class Target {
        public String sayHello(String name) {
            return "Hello " + name;
        }
        @SuppressWarnings("unused")
        private int add(int a, int b) {
            return a + b;
        }
    }

    public static void main(String[] args) throws Throwable {
        Target obj = new Target();
        int iterations = 100_000;

        // 直接调用预热
        for (int i = 0; i < 1000; i++) obj.sayHello("warmup");
        // 反射预热
        Method method = Target.class.getDeclaredMethod("sayHello", String.class);
        for (int i = 0; i < 1000; i++) method.invoke(obj, "warmup");
        // MethodHandle 预热
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodHandle mh = lookup.findVirtual(Target.class, "sayHello",
                MethodType.methodType(String.class, String.class));
        for (int i = 0; i < 1000; i++) mh.invokeExact(obj, "warmup");

        // 基准
        long t0 = System.nanoTime();
        for (int i = 0; i < iterations; i++) obj.sayHello("World");
        long t1 = System.nanoTime();
        System.out.println("直接调用: " + (t1 - t0) / 1_000_000 + " ms");

        t0 = System.nanoTime();
        for (int i = 0; i < iterations; i++) method.invoke(obj, "World");
        t1 = System.nanoTime();
        System.out.println("反射调用: " + (t1 - t0) / 1_000_000 + " ms");

        t0 = System.nanoTime();
        for (int i = 0; i < iterations; i++) mh.invokeExact(obj, "World");
        t1 = System.nanoTime();
        System.out.println("MethodHandle: " + (t1 - t0) / 1_000_000 + " ms");
    }
}
```

**设计思路**  
- 预热确保 JIT 编译生效，避免测量编译开销。  
- 直接调用最快，MethodHandle 在多次调用后接近原生，反射仍然最慢。  
- `MethodHandle` 消除了反射的包装数组、参数校验等开销，并能被 JVM 内联。

### 🐞 常见错误预警
- `invokeExact` 返回类型必须与声明严格一致，如果返回类型是 `Object` 不能赋值给 `String`，需用 `invoke` 或显式转换。
- 使用 `findVirtual` 时，接收者类型需与 Lookup 类相同；若想调用私有方法，可用 `unreflect`。
- `MethodHandle` 调用前必须类型适配（`asType`），否则可能抛 `WrongMethodTypeException`。

---

## 📝 练习3：高级/探索用法——使用 JMH 进行权威基准测试（选做挑战）

### 业务场景
前面的简单测试受 JIT 死代码消除、循环展开等影响，结果不准确。我们需要用专业工具 JMH 来精确测量反射调用的性能开销。

### 你的任务
1. 搭建 JMH 环境（引入依赖或使用命令行生成项目）。
2. 编写基准测试类，包括以下状态和基准方法：
   - `directCall`：直接调用方法。
   - `reflectCall`：缓存 `Method` 并调用。
   - `mhCall`：缓存 `MethodHandle` 并通过 `invokeExact` 调用。
3. 使用 `@State(Scope.Thread)` 保存缓存对象，`@Benchmark` 标注测试方法。
4. 运行并分析输出，对比吞吐量 (ops/s)。
5. 解释为什么 `@CompilerControl(CompilerControl.Mode.DONT_INLINE)` 可能需要来防止某些优化导致误差。

### ⚡ 关键提示
- JMH 配置：`@BenchmarkMode(Mode.Throughput)`、`@Warmup(iterations = 3, time = 1)`、`@Measurement(iterations = 5, time = 1)` 等。
- 反射测试中，注意 `Method.invoke` 会产生 `Object` 返回，JMH 会处理。
- `MethodHandle` 测试使用 `invokeExact`，需要把返回值强行赋值给 `String`，避免死码消除：在方法中返回结果或通过 `Blackhole` 消耗。
- 可以加入 `Blackhole` 来防止 JIT 优化掉调用结果。

### ✍️ 动手写代码
```java
// 需使用 JMH，此处展示关键片段
@Benchmark
public String directCall() {
    return target.sayHello("World");
}

@Benchmark
public Object reflectCall() throws Exception {
    return method.invoke(target, "World");
}

@Benchmark
public String mhCall() throws Throwable {
    return (String) mh.invokeExact(target, "World");
}
```

### ✅ 自我检查
- [ ] JMH 输出中，MethodHandle 的吞吐量是否显著高于反射？
- [ ] 预热是否足够？
- [ ] 是否观察到直接调用与 MethodHandle 的差距在缩小？

### 📖 参考实现（直接展示 JMH 核心代码）

```java
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import java.lang.invoke.*;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class ReflectionJMH {
    private Target target;
    private Method method;
    private MethodHandle mh;

    @Setup
    public void setup() throws Exception {
        target = new Target();
        method = Target.class.getDeclaredMethod("sayHello", String.class);
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        mh = lookup.findVirtual(Target.class, "sayHello",
                MethodType.methodType(String.class, String.class));
    }

    @Benchmark
    public void directCall(Blackhole bh) {
        bh.consume(target.sayHello("World"));
    }

    @Benchmark
    public void reflectCall(Blackhole bh) throws Exception {
        bh.consume(method.invoke(target, "World"));
    }

    @Benchmark
    public void mhCall(Blackhole bh) throws Throwable {
        bh.consume((String) mh.invokeExact(target, "World"));
    }

    static class Target {
        public String sayHello(String name) { return "Hello " + name; }
    }
}
```

**设计思路**  
- JMH 提供标准化基准，避免了我们手动测试的各种坑（JIT、GC、死码消除）。  
- 通过 `Blackhole` 消费结果防止优化掉方法调用。  
- 结果证明 MethodHandle 性能明显优于反射，几乎接近直接调用。

### 🐞 常见错误预警
- 未使用 `Blackhole` 可能导致 JIT 消除未使用的结果，导致测试结果失真。
- JMH 需要正确的依赖和打包，否则可能无法运行。
- 反射调用未缓存 `Method` 对象会使测试结果严重偏差。

---

## ⚙️ 性能实验：反射优化实战——`setAccessible` 与 JNI 开销对比

### 实验目标
验证 `setAccessible(true)` 对反射调用的性能提升，以及理解 native 方法调用（JNI）开销在反射中的比重。

### 引导步骤
1. 编写一个私有方法 `doWork`，通过反射调用，分别测试不调用 `setAccessible`（会失败）与调用 `setAccessible` 后的性能。
2. 对比调用 `public` 方法（无需 setAccessible）与私有方法（setAccessible）的性能差异。
3. 使用 JMH 或简单循环测试，观察是否 `setAccessible` 仅仅减少了安全检查，但整体仍远低于直接调用。
4. 分析 `Method.invoke` 的源码，理解它最终调用 `native` 方法 `NativeMethodAccessorImpl.invoke0`。

### 预期现象和解释（直接展示）
- 设置 `setAccessible(true)` 后，反射调用性能比未设置时略好（因为跳过了语言级别的访问控制检查），但是仍然比直接调用慢很多，因为反射调用的瓶颈在于参数包装、返回类型转换以及最终的 JNI 调用。
- 即使是 public 方法，反射调用的速度也远不如直接调用。

---

## 🔷 原理探究

### 探究问题
为什么 `Method.invoke` 在大量调用时无法被 JIT 内联，而 `MethodHandle.invokeExact` 可以？

### 验证方法
使用 `-XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining` 打印 JIT 内联日志，观察直接调用、反射调用和 MethodHandle 调用的内联情况。

### 引导性问题
- 反射调用的目标方法在 JIT 看来是固定的吗？
- `MethodHandle` 是如何被 JVM 识别为“常量”的？

### 原理解读（直接展示）
- `Method.invoke` 每次调用都会经历 Method 对象的 accessor 检查，并且目标方法是通过 `MethodAccessor.invoke` 间接调用，JIT 很难将其识别为常量内联，因为 Method 和 MethodAccessor 是可能变化的（例如通过 `setAccessible` 改变或生成新的 accessor）。
- `MethodHandle` 设计为 JVM 内置的元对象，它通过 `invokeExact` 会直接生成一个特定类型的调用点，JVM 可以将其视为一个稳定的常量，从而在 JIT 编译时直接内联目标方法的代码，消除反射开销。

---

## 🏢 大厂场景实战：动态代理框架的性能优化

### 场景描述
你维护一个 RPC 框架，使用 JDK 动态代理为服务接口生成代理，每次调用时需要通过 `Method.invoke` 调用远程处理器。在压测中发现反射调用是性能瓶颈之一。

### 约束条件
- 代理接口可能有数百个方法。
- 单机 QPS 要求达到 10 万+。
- 不能直接使用 `MethodHandle` 替代动态代理（因为 JDK 动态代理必须实现 `InvocationHandler`）。

### 你的设计任务
如何优化代理中的反射调用？给出方案和关键代码思路。

### 常见方案参考及其取舍分析（直接展示）
- **方案A：缓存 `Method` 对象**：每次 `invoke` 时预存，避免查找（必须）。
- **方案B：在处理器内部，使用 `MethodHandle` 替代 `method.invoke` 调用真实服务**：在 `InvocationHandler.invoke` 里，预先为每个方法生 `MethodHandle`，然后使用 `handle.invokeExact` 调用真正的实现。这可以将反射开销集中在代理接口调用层，实际调用变为 MethodHandle。
- **方案C：字节码生成**：如 Javassist 或 ByteBuddy 直接生成代理类，完全消除反射。
- **方案D：使用 `LambdaMetafactory` 生成函数式接口替代反射调用**：进一步优化。
- **推荐**：方案B 简单有效，将 `Method.invoke` 替换为 `MethodHandle`，可提升约 50% 的性能，且不影响动态代理框架。

---

## 🏆 大厂面试题

### 面试题1：Java 反射的实现原理是什么？如何获得一个类的 Class 对象？
**难度**：⭐️⭐️⭐️

**参考答案**：
- **原理**：JVM 在加载类时，会在方法区（元空间）生成一个 `Class` 对象，该对象包含了类的完整结构信息。反射 API（`java.lang.reflect` 包）通过这个 Class 对象获取字段、方法、构造器等元数据，并允许在运行时调用。底层调用通常通过 JNI 委托给 JVM 内部的 native 方法，如 `NativeMethodAccessorImpl.invoke0`。
- **获得 Class 对象**：
  1. `类名.class`（编译时已知）
  2. `对象.getClass()`
  3. `Class.forName("全限定类名")`（动态加载，触发类初始化）
  4. 基本类型的 `TYPE`（如 `Integer.TYPE`）
- **常见追问**：“Class.forName 和 ClassLoader.loadClass 有什么区别？” `forName` 默认会执行类的静态初始化（`<clinit>`），`loadClass` 默认不执行。
- **自我反思**：为什么反射获取类需要全限定名？因为不同类加载器可能有同名类。

---

### 面试题2：`setAccessible(true)` 的作用是什么？除了可以访问私有成员，对性能有何影响？
**难度**：⭐️⭐️⭐️

**参考答案**：
- **访问控制**：默认反射调用受到 Java 访问控制的限制，调用私有成员会抛出 `IllegalAccessException`。调用 `setAccessible(true)` 可以绕过该检查，允许访问私有成员。
- **性能影响**：设置为 `true` 后，反射调用会减少一部分访问控制的检查（语言级别的安全检查），因此性能会有轻微提升。但是，并不能消除反射本身的包装、拆箱和 JNI 开销。实际提升幅度有限。
- **注意**：在模块化系统（JPMS）中，如果目标类在未开放的模块中，`setAccessible` 可能仍会失败，需要模块声明 `opens`。
- **常见追问**：“频繁 `setAccessible` 会影响性能吗？” 单次操作开销很小，但反复对同一个 `Method` 设置无意义，通常设置一次后重复使用。
- **自我反思**：你是否注意过 `setAccessible` 在 JDK 9+ 的 `AccessibleObject.canAccess` 方法中的新限制？

---

### 面试题3：`MethodHandle` 相比 `java.lang.reflect.Method` 有什么优势？为什么它更接近直接调用？
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：
- **性能**：`MethodHandle` 是 JSR 292 引入的动态类型调用设施，它直接对应 JVM 的 invokevirtual/ invokestatic 等指令，可以在 JIT 编译时被内联，消除方法调用开销。而 `Method.invoke` 是通过反射层的 accessor 间接调用，难以内联。
- **类型安全**：`MethodHandle` 通过 `MethodType` 严格描述参数和返回值类型，`invokeExact` 要求精确类型匹配，编译期就能发现类型错误。
- **使用灵活性**：`MethodHandle` 可以组合、适配（`asType`）、折叠等，功能更强大。
- **限制**：`MethodHandle` 不能完全替代反射，因为反射还提供类结构信息；`MethodHandle` 主要专注于调用。
- **常见追问**：“为什么 `MethodHandle` 不适合一次性获取类信息？” 因为 `MethodHandle` 只关心调用点，不提供 `getAnnotations` 等元数据查询功能。
- **自我反思**：你能否用 `LambdaMetafactory` 将一个 `MethodHandle` 转化为 `FunctionalInterface` 进一步提速？

---

### 面试题4：反射有什么缺点？有哪些替代方案？
**难度**：⭐️⭐️⭐️⭐️

**参考答案**：
- **缺点**：
  1. **性能损耗**：反射调用比直接调用慢数倍甚至数十倍。
  2. **类型安全缺失**：编译期无法检查，运行期可能抛出异常。
  3. **代码可读性差**：大量 try-catch 和类型转换。
  4. **安全限制**：可能破坏封装性，模块化系统需要额外配置。
- **替代方案**：
  1. **`MethodHandle`**：提升动态调用性能。
  2. **`LambdaMetafactory`**：将方法引用转化为函数式接口，极致性能。
  3. **字节码增强**：使用 ASM、ByteBuddy、Javassist 直接生成类，完全避免反射。
  4. **APT 编译时注解处理器**：在编译时生成代码，避免运行时反射。
- **常见追问**：“Spring 如何从反射转向更高效的方式？” Spring 5+ 大量使用 `LambdaMetafactory` 和 `MethodHandle` 替代反射来创建 Bean 实例和调用方法。
- **自我反思**：在你的项目中，是否有不必要的反射调用可以替换为 MethodHandle 或代码生成？

---

### 面试题5：如何用反射实现一个简单的依赖注入容器？
**难度**：⭐️⭐️⭐️⭐️⭐️

**参考答案**：
- **思路**：
  1. 扫描指定包下的类，通过 `Class.forName` 加载。
  2. 检查类上的注解（如 `@Component`, `@Autowired`）。
  3. 使用 `Class.newInstance` 或 `Constructor.newInstance` 创建实例。
  4. 通过 `Field.setAccessible(true)` 和 `field.set(instance, value)` 注入依赖。
  5. 单例管理：使用 `Map<Class<?>, Object>` 缓存。
- **关键点**：
  - 处理循环依赖：三级缓存。
  - 处理接口与实现类的映射。
  - 性能考虑：缓存反射对象，甚至可以用 `MethodHandle` 替代 `Field.set`。
- **常见追问**：“Spring 是如何解决反射性能问题的？” Spring 使用了 `ReflectionUtils` 缓存，以及在启动时一次性完成反射调用，运行时通过代理和 CGLIB 等转为直接调用。
- **自我反思**：如果让你手写一个最小 IOC 容器，你会为 `Field.set` 生成 `MethodHandle` 吗？

---

> 今天你看到了反射的便利与沉重，也感受到了 `MethodHandle` 的速度与优雅。在高性能框架的天地里，它们是搭建动态特性的基石。明天我们将正面迎战 JDK 动态代理与 CGLIB，解剖其源码生成的黑魔法。





