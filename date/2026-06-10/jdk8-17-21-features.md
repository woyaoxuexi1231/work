# JDK 8 → 17 → 21 新特性全览

> 按版本列出所有重要新特性，标注"正式"或"预览"状态。每个特性附最简代码示例。

---

## 一、JDK 8（2014 年发布）

JDK 8 是 Java 历史上最重要的一次升级，引入了函数式编程的核心能力。

### 1. Lambda 表达式
```java
// 匿名内部类 → 一行 Lambda
list.sort((a, b) -> a.compareTo(b));
executor.submit(() -> System.out.println("hello"));
```

### 2. Stream API
```java
List<String> names = users.stream()
    .filter(u -> u.getAge() > 18)
    .map(User::getName)
    .sorted()
    .collect(Collectors.toList());
```

### 3. Optional
```java
Optional<User> user = findById(id);
user.ifPresent(u -> sendEmail(u));
String name = user.map(User::getName).orElse("unknown");
```

### 4. 默认方法（接口中可以有实现）
```java
public interface List<E> {
    default void forEach(Consumer<E> action) {
        for (E e : this) action.accept(e);
    }
}
```

### 5. 方法引用
```java
// Lambda 的简写形式
list.forEach(System.out::println);
Supplier<List<String>> factory = ArrayList::new;
```

### 6. 新日期时间 API（java.time）
```java
LocalDateTime now = LocalDateTime.now();
LocalDate date = LocalDate.of(2024, 1, 1);
Duration between = Duration.between(start, end);
```

### 7. 重复注解
```java
@Repeatable(Authors.class)
@interface Author { String name(); }

@Author(name = "Alice")
@Author(name = "Bob")
class Book {}
```

### 8. CompletableFuture（异步编程）
```java
CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> callApi())
    .thenApply(result -> result.toUpperCase())
    .exceptionally(e -> "error");
```

### 9. 类型注解
```java
// 注解可以用在任何使用类型的地方
List<@NonNull String> list;
```

---

## 二、JDK 9 ~ 16（JDK 8 → 17 之间的过渡版本）

这些特性在 JDK 17 中都可以使用（因为 17 是 LTS 长期支持版）。

### JDK 9
| 特性 | 示例 |
|------|------|
| **模块化系统（Jigsaw）** | `module com.example { requires java.sql; exports com.api; }` |
| **JShell（交互式编程）** | 命令行直接执行 Java 代码片段 |
| **集合工厂方法** | `List.of("a", "b", "c")` / `Map.of("k", "v")` |
| **Optional 增强** | `ifPresentOrElse()` / `or()` / `stream()` / `isEmpty()` |
| **接口私有方法** | `private void helper() {}` 在接口内部 |
| **响应式流（Flow API）** | `Flow.Publisher` / `Flow.Subscriber` |

### JDK 10
| 特性 | 示例 |
|------|------|
| **局部变量类型推断 var** | `var list = new ArrayList<String>();` // 不需要写左边类型 |
| **集合 copyOf()** | `List.copyOf(mutableList)` → 不可变副本 |

### JDK 11（LTS）
| 特性 | 示例 |
|------|------|
| **HTTP Client（正式版）** | `HttpClient.newHttpClient().send(request, BodyHandlers.ofString())` |
| **String 新方法** | `isBlank()` / `strip()` / `lines()` / `repeat(n)` |
| **单文件运行** | `java Hello.java`（不需要先 javac） |
| **Lambda 中使用 var** | `(var x, var y) -> x + y` |

### JDK 12 ~ 13
| 特性 | 示例 |
|------|------|
| **Switch 表达式（预览）** | `var r = switch(x) { case 1 -> "a"; case 2 -> "b"; default -> "c"; };` |
| **Text Blocks（预览）** | `"""` 多行字符串 |

### JDK 14
| 特性 | 示例 |
|------|------|
| **instanceof 模式匹配（预览）** | `if (obj instanceof String s) { s.length(); }` |
| **Record（预览）** | `record Point(int x, int y) {}` |
| **NullPointerException 增强** | `Cannot invoke "String.length()" because "a.b.name" is null`（精准指出变量名） |
| **Switch 表达式（正式版）** | 从预览变为正式 |

### JDK 15
| 特性 | 示例 |
|------|------|
| **Text Blocks（正式版）** | `"""..."""` 从预览变为正式 |
| **Sealed Classes（预览）** | `sealed class Shape permits Circle, Square {}` |
| **Hidden Classes** | 供框架使用的隐藏类 |

### JDK 16
| 特性 | 示例 |
|------|------|
| **Record（正式版）** | 从预览变为正式 |
| **instanceof 模式匹配（正式版）** | 从预览变为正式 |
| **Stream.toList()** | `list.stream().filter(...).toList()`（不需要 `.collect(Collectors.toList())`） |

---

## 三、JDK 17（2021 年发布，LTS）

> Spring Boot 3 的最低 JDK 要求。

### 1. Sealed Classes / Interfaces（密封类/接口）—— 正式
```java
// 限定只有特定类可以实现/继承
public sealed interface Shape permits Circle, Square, Triangle {}

record Circle(double radius) implements Shape {}
record Square(double side) implements Shape {}
record Triangle(double base, double height) implements Shape {}
```
**价值**：配合 switch 模式匹配，编译器知道所有子类 → 强制穷举，漏写编译报错。

### 2. Switch 表达式 + 模式匹配（预览）
```java
// 可以对类型做模式匹配（不只是值）
String desc = switch (shape) {
    case Circle c    -> "圆, 半径: " + c.radius();
    case Square s    -> "正方形, 边长: " + s.side();
    case Triangle t  -> "三角形";
    // 如果 Shape 是 sealed 的，不需要 default！编译器自动穷举
};
```

### 3. 强封装 JDK 内部 API
- 默认禁止通过反射访问 `sun.*` / `jdk.*` 内部包
- 需要显式 `--add-opens` 才能访问
- 影响：很多老框架（如旧版 Lombok、Mockito）需要升级

### 4. 其他重要改进

| 特性 | 说明 |
|------|------|
| **macOS 上支持 AArch64** | 原生支持 Apple Silicon（M1/M2） |
| **弃用 Applet API** | `java.applet` 标记为废弃 |
| **新的伪随机数生成器** | `RandomGenerator` 统一接口 |
| **增强的 NPE 信息** | JDK 14 引入，17 LTS 包含 |

---

## 四、JDK 18 ~ 20（JDK 17 → 21 之间的过渡版本）

### JDK 18
| 特性 | 示例 |
|------|------|
| **简单 Web 服务器** | `java -m jdk.httpserver` 一行命令启动静态文件服务器 |
| **UTF-8 默认编码** | 全平台默认字符集统一为 UTF-8 |

### JDK 19
| 特性 | 示例 |
|------|------|
| **Virtual Threads（预览）** | `Thread.startVirtualThread(() -> ...)` |
| **Structured Concurrency（孵化）** | `StructuredTaskScope` |

### JDK 20
| 特性 | 示例 |
|------|------|
| **Record Patterns（预览）** | `if (obj instanceof Point(var x, var y)) { ... }` |
| **Switch 模式匹配（第二次预览）** | 增强 switch 的类型模式匹配 |

---

## 五、JDK 21（2023 年发布，LTS）

> 被称为"Java 十年来最大更新"，虚线程彻底改变并发编程。

### 1. Virtual Threads（虚线程）—— 正式 🔥🔥🔥

**核心概念**：
- 平台线程：1:1 绑定 OS 线程，创建开销 ~1MB，通常限制几百个
- 虚线程：M:N 调度（JVM 调度到少量载体线程上），创建开销 ~1KB，可以创建百万个

```java
// 写法1：直接创建虚线程
Thread.startVirtualThread(() -> {
    System.out.println("我是虚线程: " + Thread.currentThread().isVirtual());
});

// 写法2：虚线程执行器（推荐）
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    Future<String> f1 = executor.submit(() -> callApi1());
    Future<String> f2 = executor.submit(() -> callApi2());
    Future<String> f3 = executor.submit(() -> callApi3());

    // 像同步代码一样简单，但实际是并发执行
    String r1 = f1.get();
    String r2 = f2.get();
    String r3 = f3.get();
}
// 自动关闭，不需要 executor.shutdown()

// 写法3：Spring Boot 3.2+ 一行配置
// spring.threads.virtual.enabled=true
// Tomcat 所有请求处理线程自动变成虚线程！
```

**适合场景**：I/O 密集型（HTTP 调用、数据库查询、文件读写）
**不适合**：CPU 密集型计算

### 2. StructuredTaskScope（结构化并发）—— 预览

```java
// 多个子任务的生命周期被绑定在一个 scope 内
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    Subtask<String> user  = scope.fork(() -> fetchUser(id));
    Subtask<Integer> order = scope.fork(() -> fetchOrder(id));

    scope.join();           // 等待全部完成
    scope.throwIfFailed();  // 任一失败 → 抛异常 + 自动取消其他任务

    return new UserProfile(user.get(), order.get());
}
// 对比 CompletableFuture：异常自动传播，任务自动取消，不用手动管理
```

### 3. Record Patterns（记录模式）—— 正式 🔥

```java
// JDK 17：instanceof 只能绑定整个对象
if (obj instanceof OrderDTO order) {
    order.orderNo();  // 还需手动调用 accessor
}

// JDK 21：Record Pattern 直接解构每个字段
if (obj instanceof OrderDTO(var id, var orderNo, var amount, var status, var time)) {
    // id, orderNo, amount 直接可用！
    System.out.println("订单号: " + orderNo + ", 金额: " + amount);
}

// 在 switch 中也能用
String desc = switch (obj) {
    case OrderDTO(var id, var no, var amount, var status, var time)
        -> "订单 " + no + ": " + amount;
    case User(var name, var email)
        -> "用户 " + name;
    default -> "未知";
};

// 嵌套解构
record Point(int x, int y) {}
record Line(Point start, Point end) {}

if (line instanceof Line(Point(var x1, var y1), Point(var x2, var y2))) {
    // 直接拿到嵌套 Record 的字段
    double length = Math.sqrt(Math.pow(x2-x1, 2) + Math.pow(y2-y1, 2));
}
```

### 4. Switch 模式匹配（正式版）
```java
// JDK 17 是预览，JDK 21 正式
String format(Object obj) {
    return switch (obj) {
        case Integer i    -> "整数: " + i;
        case String s     -> "字符串: " + s;
        case int[] arr    -> "数组长度: " + arr.length;  // 数组也能匹配！
        case null         -> "null";                      // null 匹配！
        default           -> "其他: " + obj;
    };
}
```

### 5. Sequenced Collections（有序集合）—— 正式
```java
// 统一了"有顺序的集合"的 API
SequencedCollection<String> list = new ArrayList<>(List.of("a", "b", "c"));

list.getFirst();   // "a"    —— 替代 list.get(0)
list.getLast();    // "c"    —— 替代 list.get(list.size()-1)
list.reversed();   // ["c", "b", "a"] —— 替代 Collections.reverse()
list.addFirst("z");
list.addLast("d");

// SequencedMap 也一样
SequencedMap<String, Integer> map = new LinkedHashMap<>();
map.firstEntry();    // 第一个键值对
map.lastEntry();     // 最后一个键值对
map.reversed();      // 反转的 Map
```

### 6. String Templates（字符串模板）—— 预览
```java
// 预览特性，需要 --enable-preview
String name = "Alice";
int age = 30;
// String msg = STR."Hello \{name}, you are \{age} years old";
// 支持表达式：STR."2+3 = \{2+3}"
// 支持多行：  STR."""
//              Hello \{name}
//              """;
```

### 7. Unnamed Patterns and Variables（匿名模式变量）—— 预览
```java
// 用 _ 表示不关心的变量
case OrderDTO(var id, var no, _, _, _) -> "订单号: " + no;

// catch 中不使用的异常
try { ... } catch (Exception _) { log.error("出错了"); }

// 增强 for 中不使用的变量
for (var _ : list) { count++; }
```

### 8. Scoped Values（作用域值）—— 预览
```java
// 替代 ThreadLocal 的更安全方案，配合虚线程使用
static final ScopedValue<User> CURRENT_USER = ScopedValue.newInstance();

ScopedValue.where(CURRENT_USER, user).run(() -> {
    processRequest();  // 内部通过 CURRENT_USER.get() 获取
});
```

## 
