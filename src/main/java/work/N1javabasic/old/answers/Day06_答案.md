# Day 06: Java 8+ 函数式编程新特性 - 完整答案

## 面试真题连环炮 - 详细解答

### 1. `Stream` 的并行流（parallelStream）底层是用什么实现的？有什么坑？

#### 核心原理

**一句话总结**：parallelStream 底层使用 ForkJoinPool 公共线程池实现，存在线程池共享、线程安全问题、性能陷阱等坑。

#### 详细分析

**底层实现**：

```java
// parallelStream 的创建
List<Integer> list = Arrays.asList(1, 2, 3, 4, 5);
Stream<Integer> parallelStream = list.parallelStream();

// 底层调用
@Override
public final Stream<E> parallelStream() {
    return stream().parallel();  // 标记为并行
}

// Stream 的处理过程
parallelStream.forEach(System.out::println);
```

**ForkJoinPool 实现**：

```java
// parallelStream 默认使用公共线程池
ForkJoinPool.commonPool()

// 公共线程池的默认大小
public static ForkJoinPool commonPool() {
    return common;
}

// 线程池大小 = CPU 核心数 - 1
private static final int commonMaxSpares = Runtime.getRuntime().availableProcessors() - 1;

// 例如：4 核 CPU，线程池大小 = 3
```

**Fork/Join 框架工作原理**：

```java
// 1. Fork（分叉）：将大任务拆分为小任务
// 2. Join（合并）：将小任务结果合并

// 示例：计算 1 到 100 的和
public class SumTask extends RecursiveTask<Integer> {
    private static final int THRESHOLD = 10;
    private int start, end;
    
    @Override
    protected Integer compute() {
        if (end - start <= THRESHOLD) {
            // 足够小，直接计算
            int sum = 0;
            for (int i = start; i <= end; i++) {
                sum += i;
            }
            return sum;
        } else {
            // 拆分任务
            int mid = (start + end) / 2;
            SumTask left = new SumTask(start, mid);
            SumTask right = new SumTask(mid + 1, end);
            
            left.fork();   // 异步执行左半部分
            int rightResult = right.compute();  // 同步执行右半部分
            int leftResult = left.join();       // 等待左半部分结果
            
            return leftResult + rightResult;
        }
    }
}

// Stream 的并行处理类似这个过程
```

**工作窃取算法（Work-Stealing）**：

```
ForkJoinPool 使用工作窃取算法提高性能：

线程 1 的任务队列：[Task1, Task2, Task3]
线程 2 的任务队列：[]  （空闲）

线程 2 发现线程 1 有很多任务，从队列尾部"窃取"一个任务执行

优势：
- 减少线程竞争（线程 1 从头部取，线程 2 从尾部取）
- 提高 CPU 利用率（空闲线程不会闲着）
```

---

#### 常见的坑

**坑 1：共享线程池导致性能问题**

```java
// 问题：所有 parallelStream 共享同一个公共线程池
public void badExample() {
    // 任务 1：耗时操作
    list1.parallelStream().forEach(item -> {
        Thread.sleep(1000);  // 模拟耗时操作
    });
    
    // 任务 2：也被阻塞
    list2.parallelStream().forEach(item -> {
        // 需要等待任务 1 释放线程
    });
}

// 解决方案：使用自定义线程池
public void goodExample() {
    ForkJoinPool customPool = new ForkJoinPool(10);
    customPool.submit(() -> 
        list.parallelStream().forEach(item -> {
            // 使用自定义线程池
        })
    ).get();
}
```

**坑 2：线程安全问题**

```java
// 错误示例：使用非线程安全的集合
List<Integer> list = new ArrayList<>();
IntStream.range(0, 1000).parallel().forEach(i -> {
    list.add(i);  // ArrayList 不是线程安全的！
});
// 可能抛出 ArrayIndexOutOfBoundsException 或数据丢失

// 正确示例 1：使用线程安全的集合
List<Integer> list = Collections.synchronizedList(new ArrayList<>());
IntStream.range(0, 1000).parallel().forEach(i -> {
    list.add(i);
});

// 正确示例 2：使用 collect（推荐）
List<Integer> list = IntStream.range(0, 1000)
    .parallel()
    .boxed()
    .collect(Collectors.toList());  // 线程安全
```

**坑 3：顺序敏感的操作**

```java
// 问题：并行流不保证顺序
List<Integer> list = Arrays.asList(1, 2, 3, 4, 5);

// forEach 不保证顺序
list.parallelStream().forEach(System.out::println);
// 输出可能是：3, 1, 5, 2, 4

// 需要保持顺序，使用 forEachOrdered
list.parallelStream().forEachOrdered(System.out::println);
// 输出：1, 2, 3, 4, 5

// 但 forEachOrdered 会失去并行性能优势
```

**坑 4：性能不一定好**

```java
// 不是所有场景都适合并行流

// 场景 1：数据量小
List<Integer> list = Arrays.asList(1, 2, 3, 4, 5);
list.parallelStream().map(i -> i * 2).collect(Collectors.toList());
// 并行开销 > 计算收益，反而更慢

// 场景 2：操作本身很快
list.parallelStream().map(i -> i + 1);
// 简单的加法操作，并行化得不偿失

// 场景 3：IO 密集型操作
list.parallelStream().map(i -> {
    // 网络请求、文件读取等 IO 操作
    return httpClient.get(i);
});
// IO 操作会阻塞线程，并行流使用 ForkJoinPool，不适合阻塞操作

// 适合并行的场景：
// 1. 数据量大（通常 > 10000）
// 2. CPU 密集型计算
// 3. 操作耗时较长
```

**坑 5：调试困难**

```java
// 并行流的异常堆栈信息复杂
List<Integer> list = Arrays.asList(1, 2, null, 4, 5);
list.parallelStream().map(i -> i + 1).collect(Collectors.toList());
// NullPointerException，但堆栈信息包含多个线程，难以定位

// 建议：开发和测试阶段使用串行流，确认无误后再改为并行
```

---

### 2. `Optional` 真的能完全解决 NPE 吗？正确的使用姿势是什么？

#### 核心原理

**一句话总结**：Optional 不能完全解决 NPE，它是一种设计意图的表达工具，正确使用可以提升代码可读性和安全性。

#### 详细分析

**Optional 的设计初衷**：

```java
// Java 8 之前：返回 null 表示"无值"
public User findUserById(Long id) {
    User user = userDao.findById(id);
    return user;  // 可能返回 null
}

// 调用者容易忘记检查 null
User user = findUserById(1L);
user.getName();  // NullPointerException！

// Java 8 之后：使用 Optional 明确表示"可能有值，可能无值"
public Optional<User> findUserById(Long id) {
    return Optional.ofNullable(userDao.findById(id));
}

// 调用者必须处理空值情况
Optional<User> opt = findUserById(1L);
opt.ifPresent(user -> System.out.println(user.getName()));
```

---

#### Optional 不能完全解决 NPE 的原因

**原因 1：Optional 本身可能为 null**

```java
// 错误用法
public Optional<User> findUserById(Long id) {
    return null;  // 直接返回 null，违背 Optional 的初衷
}

Optional<User> opt = findUserById(1L);
opt.isPresent();  // NullPointerException！

// 正确用法：永远不要返回 null 的 Optional
public Optional<User> findUserById(Long id) {
    return Optional.ofNullable(userDao.findById(id));
}
```

**原因 2：get() 方法可能抛异常**

```java
Optional<User> opt = Optional.empty();
User user = opt.get();  // NoSuchElementException！

// get() 方法源码
public T get() {
    if (value == null) {
        throw new NoSuchElementException("No value present");
    }
    return value;
}
```

**原因 3：无法序列化**

```java
// Optional 没有实现 Serializable 接口
public class User {
    private Optional<String> email;  // 不推荐
}

// 如果 User 需要序列化，会抛出 NotSerializableException
```

**原因 4：不适合字段类型**

```java
// 错误用法
public class User {
    private Optional<String> name;  // 不推荐
    
    public String getName() {
        return name.orElse("Anonymous");
    }
}

// 问题：
// 1. 增加内存开销（每个字段多一个 Optional 对象）
// 2. 代码不优雅，每次都要调用 get()
// 3. 序列化问题

// 正确用法：只在返回值使用 Optional
public class User {
    private String name;
    
    public Optional<String> getName() {  // 推荐
        return Optional.ofNullable(name);
    }
}
```

---

#### 正确的使用姿势

**姿势 1：orElse / orElseGet / orElseThrow**

```java
Optional<User> opt = findUserById(1L);

// orElse：无论 Optional 是否为空，都会创建默认对象
User user1 = opt.orElse(new User("Anonymous"));

// orElseGet：只有 Optional 为空时才创建默认对象（推荐）
User user2 = opt.orElseGet(() -> new User("Anonymous"));

// orElseThrow：Optional 为空时抛出自定义异常
User user3 = opt.orElseThrow(() -> new UserNotFoundException(1L));
```

**性能对比**：
```java
// 场景：Optional 有值
Optional<String> opt = Optional.of("value");

// orElse：即使有值，也会执行 new User()
String result = opt.orElse(expensiveOperation());  // 浪费性能

// orElseGet：只有为空时才执行
String result = opt.orElseGet(() -> expensiveOperation());  // 推荐
```

---

**姿势 2：map / flatMap 链式调用**

```java
// 传统写法（容易 NPE）
User user = findUserById(1L);
if (user != null) {
    Address address = user.getAddress();
    if (address != null) {
        City city = address.getCity();
        if (city != null) {
            System.out.println(city.getName());
        }
    }
}

// Optional 写法（优雅）
Optional<User> opt = findUserById(1L);
opt.map(User::getAddress)
   .map(Address::getCity)
   .map(City::getName)
   .ifPresent(System.out::println);

// map vs flatMap：
// map：返回值会被包装为 Optional
// flatMap：返回值已经是 Optional，不再包装

// 示例
Optional<User> opt = ...;

// 错误：map 会返回 Optional<Optional<City>>
Optional<Optional<City>> city1 = opt.map(User::getCity);

// 正确：flatMap 返回 Optional<City>
Optional<City> city2 = opt.flatMap(User::getCity);
```

---

**姿势 3：filter 过滤**

```java
Optional<User> opt = findUserById(1L);

// 过滤：只有满足条件的才执行
opt.filter(user -> user.getAge() >= 18)
   .ifPresent(user -> System.out.println("成年人"));

// 等价于
if (opt.isPresent() && opt.get().getAge() >= 18) {
    System.out.println("成年人");
}
```

---

**姿势 4：ifPresent / ifPresentOrElse**

```java
Optional<User> opt = findUserById(1L);

// ifPresent：有值时执行
opt.ifPresent(user -> saveToCache(user));

// ifPresentOrElse（Java 9+）：有值/无值时分别执行
opt.ifPresentOrElse(
    user -> saveToCache(user),
    () -> log.warn("User not found")
);

// Java 8 的替代方案
opt.map(user -> saveToCache(user))
   .orElseGet(() -> {
       log.warn("User not found");
       return null;
   });
```

---

**姿势 5：Optional 作为方法参数（不推荐）**

```java
// 错误用法
public void processUser(Optional<User> user) {
    user.ifPresent(u -> ...);
}

// 问题：
// 1. 调用者需要额外包装
// 2. 方法签名不清晰

// 正确用法：使用方法重载
public void processUser(User user) {
    Objects.requireNonNull(user);
    // 处理逻辑
}

public void processUser() {
    // 处理无用户的情况
}
```

---

### 3. 接口的默认方法（default method）冲突了怎么办？

#### 核心原理

**一句话总结**：当一个类实现多个接口，且这些接口有相同签名的 default 方法时，必须显式覆盖冲突方法。

#### 详细分析

**冲突场景 1：两个接口有相同的 default 方法**

```java
interface InterfaceA {
    default void sayHello() {
        System.out.println("Hello from A");
    }
}

interface InterfaceB {
    default void sayHello() {
        System.out.println("Hello from B");
    }
}

// 编译错误：类从类型 'InterfaceB' 和 'InterfaceA' 中继承了 sayHello() 的不相关默认值
class MyClass implements InterfaceA, InterfaceB {
}

// 解决方案：显式覆盖
class MyClass implements InterfaceA, InterfaceB {
    @Override
    public void sayHello() {
        System.out.println("Hello from MyClass");
    }
}
```

---

**冲突场景 2：选择调用特定接口的方法**

```java
interface InterfaceA {
    default void sayHello() {
        System.out.println("Hello from A");
    }
}

interface InterfaceB {
    default void sayHello() {
        System.out.println("Hello from B");
    }
}

class MyClass implements InterfaceA, InterfaceB {
    @Override
    public void sayHello() {
        // 调用 InterfaceA 的默认方法
        InterfaceA.super.sayHello();
        
        // 或者调用 InterfaceB 的默认方法
        // InterfaceB.super.sayHello();
    }
}
```

---

**冲突场景 3：类继承 vs 接口默认方法**

```java
class Parent {
    public void sayHello() {
        System.out.println("Hello from Parent");
    }
}

interface InterfaceA {
    default void sayHello() {
        System.out.println("Hello from InterfaceA");
    }
}

// 类的优先级高于接口默认方法
class Child extends Parent implements InterfaceA {
    // 自动继承 Parent 的 sayHello()
    // InterfaceA 的默认方法被忽略
}

Child child = new Child();
child.sayHello();  // 输出：Hello from Parent
```

**优先级规则**：
```
1. 类中声明的方法 > 接口的 default 方法
2. 父类的方法 > 接口的 default 方法
3. 如果两个接口有相同的 default 方法，必须显式覆盖
```

---

**冲突场景 4：接口继承**

```java
interface InterfaceA {
    default void sayHello() {
        System.out.println("Hello from A");
    }
}

interface InterfaceB extends InterfaceA {
    @Override
    default void sayHello() {
        System.out.println("Hello from B");
    }
}

// InterfaceB 继承了 InterfaceA，并覆盖了 sayHello
// 没有冲突，使用 InterfaceB 的方法
class MyClass implements InterfaceB {
}

MyClass obj = new MyClass();
obj.sayHello();  // 输出：Hello from B
```

---

#### 实际应用场景

**场景 1：向后兼容**

```java
// Java 8 之前
interface List<E> {
    E get(int index);
    void add(E e);
    // ... 其他方法
}

// Java 8 之后：添加默认方法，不破坏已有实现
interface List<E> {
    E get(int index);
    void add(E e);
    
    default void sort(Comparator<? super E> c) {
        Collections.sort(this, c);
    }
    
    default Spliterator<E> spliterator() {
        return Spliterators.spliterator(this, 0);
    }
}

// 已有的 ArrayList、LinkedList 等无需修改就能使用新方法
```

---

**场景 2：模板方法模式**

```java
interface DatabaseService {
    Connection getConnection();
    
    default <T> T findById(Class<T> clazz, Long id) {
        Connection conn = getConnection();
        // 通用查询逻辑
        return executeQuery(conn, "SELECT * FROM " + clazz.getSimpleName() + " WHERE id = ?", id);
    }
    
    default <T> List<T> findAll(Class<T> clazz) {
        Connection conn = getConnection();
        return executeQuery(conn, "SELECT * FROM " + clazz.getSimpleName());
    }
}

// 实现类只需提供 getConnection
class UserService implements DatabaseService {
    @Override
    public Connection getConnection() {
        return dataSource.getConnection();
    }
    
    // 自动拥有 findById 和 findAll 方法
}
```

---

## 面试加分技巧

### 回答模板

**面试官**：parallelStream 有什么坑？

**回答结构**：
1. **直接回答**："parallelStream 底层使用 ForkJoinPool 公共线程池，存在线程池共享、线程安全、性能陷阱等问题"
2. **举例说明**："多个 parallelStream 共享同一个线程池，如果一个流执行耗时操作，会阻塞其他流。另外，在 parallelStream 中使用 ArrayList 等非线程安全集合会抛异常"
3. **解决方案**："可以使用自定义 ForkJoinPool，或者使用 collect 等线程安全的收集器"
4. **使用场景**："parallelStream 适合数据量大、CPU 密集型的场景，不适合 IO 密集型或小数据量"

### 常见错误回答

❌ **错误 1**："Optional 可以完全避免 NPE"
✅ **正确**：Optional 不能完全避免 NPE，它只是明确表达"可能为空"的语义，仍需正确使用。

❌ **错误 2**："parallelStream 一定比串行流快"
✅ **正确**：parallelStream 有额外开销，数据量小或操作简单时反而更慢。

❌ **错误 3**："default 方法可以多继承"
✅ **正确**：Java 类仍然是单继承，default 方法只是提供了接口的默认实现，不是多继承。

---

## 深入学习建议

1. **阅读源码**：
   - `java.util.stream.Stream` - Stream 接口定义
   - `java.util.ForkJoinPool` - Fork/Join 框架
   - `java.util.Optional` - Optional 实现

2. **实践练习**：
   - 使用 parallelStream 处理大数据集，对比性能
   - 用 Optional 重构现有的 null 检查代码
   - 设计包含 default 方法的接口

3. **扩展阅读**：
   - 《Java 8 实战》- Stream 和 Lambda 章节
   - Fork/Join 框架官方文档
