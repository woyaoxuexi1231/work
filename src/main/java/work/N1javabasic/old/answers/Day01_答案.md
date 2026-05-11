# Day 01: 核心基础：Object、String 与泛型 - 完整答案

## 面试真题连环炮 - 详细解答

### 1. 为什么重写 `equals` 必须重写 `hashCode`？不重写会有什么后果？

#### 核心原理

**equals 和 hashCode 的契约关系**：
Java 的 `Object` 类中规定了 `equals` 和 `hashCode` 的约定：
- 如果两个对象通过 `equals()` 判断为相等，那么它们的 `hashCode()` 必须相同
- 如果两个对象的 `hashCode()` 相同，它们不一定通过 `equals()` 判断为相等（哈希冲突）

**不重写的后果**：

1. **在 HashMap/HashSet 中无法正常工作**
```java
// 示例：只重写 equals，不重写 hashCode
public class Person {
    private String id;
    private String name;
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Person person = (Person) o;
        return Objects.equals(id, person.id);
    }
    // 忘记重写 hashCode
}

// 使用时会出现问题
Person p1 = new Person("001", "张三");
Person p2 = new Person("001", "张三");

System.out.println(p1.equals(p2)); // true - 逻辑上相等

HashMap<Person, String> map = new HashMap<>();
map.put(p1, "数据1");
System.out.println(map.get(p2)); // null! 因为 hashCode 不同，找不到
```

2. **原因分析**：
   - HashMap 存储时：先计算 `hashCode` 确定桶位置，再用 `equals` 比较键
   - 如果只重写 `equals`，两个逻辑相等的对象会有不同的 `hashCode`
   - 导致它们在 HashMap 中被存入不同的桶，永远无法通过 `equals` 比较

**正确的写法**：
```java
public class Person {
    private String id;
    private String name;
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Person person = (Person) o;
        return Objects.equals(id, person.id) && 
               Objects.equals(name, person.name);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id, name); // 必须使用相同的字段
    }
}
```

**底层源码解析**（HashMap.get 方法）：
```java
// HashMap 的 get 方法执行流程
public V get(Object key) {
    Node<K,V> e;
    return (e = getNode(hash(key), key)) == null ? null : e.value;
}

final Node<K,V> getNode(int hash, Object key) {
    Node<K,V>[] tab; Node<K,V> first, e; int n; K k;
    if ((tab = table) != null && (n = tab.length) > 0 &&
        (first = tab[(n - 1) & hash]) != null) { // 先用 hashCode 找桶
        if (first.hash == hash && // 比较 hash
            ((k = first.key) == key || (key != null && key.equals(k)))) // 再用 equals
            return first;
        // ... 遍历链表或红黑树
    }
    return null;
}
```

---

### 2. `String s = new String("abc")` 到底创建了几个对象？

#### 详细分析

**答案：可能创建 1 个或 2 个对象**

**情况 1：创建 2 个对象**（字符串常量池中没有 "abc"）
```java
String s = new String("abc");
```
执行过程：
1. 首先在**字符串常量池**中查找是否有 "abc"
2. 如果没有，在常量池中创建 "abc" 对象（第1个对象）
3. 然后在**堆内存**中通过 `new` 创建一个新的 String 对象（第2个对象）
4. 变量 `s` 指向堆内存中的对象

**情况 2：创建 1 个对象**（字符串常量池中已有 "abc"）
```java
String s1 = "abc";  // 常量池中创建 "abc"
String s2 = new String("abc"); // 只在堆中创建新对象
```
执行过程：
1. "abc" 已经在常量池中存在
2. `new String()` 只在堆内存中创建一个新对象
3. 变量 `s2` 指向堆内存中的对象

**内存结构图解**：
```
堆内存 (Heap)                字符串常量池 (String Pool)
┌──────────────┐            ┌──────────────┐
│ String 对象   │──value──→ │   "abc"      │
│ (s 指向这里)  │           │  (在常量池中) │
└──────────────┘            └──────────────┘
```

**验证代码**：
```java
String s1 = "abc";
String s2 = new String("abc");
String s3 = "abc";

System.out.println(s1 == s2);        // false - 不同对象
System.out.println(s1 == s3);        // true - 同一个常量池对象
System.out.println(s1.equals(s2));   // true - 值相同

// intern() 方法：将堆中的字符串引用放入常量池
String s4 = s2.intern();
System.out.println(s1 == s4);        // true - s4 指向常量池中的 "abc"
```

**底层原理**：
- `String` 内部维护了一个 `char[] value` 或 `byte[] value`（Java 9+）
- `new String("abc")` 会复制一份字符数组，创建新的 String 对象
- 字符串常量池是 JVM 的一个特殊区域（在堆中，Java 7+）

---

### 3. `String` 为什么要设计成不可变的？从安全和性能两方面回答

#### 安全性角度

**1. 字符串常量池的需要**
```java
String s1 = "abc";
String s2 = "abc";
// 如果 String 可变，修改 s1 会影响 s2，因为它们指向同一个对象
// 这会导致严重的数据不一致问题
```

**2. 安全性 - 防止恶意修改**
```java
// 数据库连接字符串
String dbUrl = "jdbc:mysql://localhost:3306/mydb?user=admin&password=123";
// 如果 String 可变，黑客可能在传递过程中修改连接信息
```

**3. 线程安全**
```java
// String 不可变，天然线程安全，多线程共享无需同步
public class SharedData {
    private static final String CONFIG = "config_value";
    // 多个线程同时读取 CONFIG，不会出现并发问题
}
```

**4. ClassLoader 安全**
```java
// 类加载时使用字符串指定类名
Class<?> clazz = Class.forName("com.example.MyClass");
// 如果 String 可变，可能在加载过程中被篡改类名
```

#### 性能角度

**1. 哈希码缓存**
```java
// String 的 hashCode 在第一次计算后会被缓存
public final class String {
    private int hash; // 缓存 hashCode
    
    public int hashCode() {
        int h = hash;
        if (h == 0 && value.length > 0) {
            hash = h = value[0] * 31 + ...; // 计算后缓存
        }
        return h;
    }
}
// HashMap 的 Key 经常使用 String，缓存 hashCode 大幅提升性能
```

**2. 字符串常量池节省内存**
```java
// 大量相同的字符串共享同一个对象
String s1 = "hello";
String s2 = "hello";
String s3 = "hello";
// 只占用一份内存，而不是三份
```

**3. 优化字符串操作**
```java
// substring、concat 等操作可以共享底层数组（Java 7 之前）
// Java 9+ 虽然改为复制，但不可变性仍允许各种优化
```

**不可变的实现方式**：
```java
public final class String implements Serializable, Comparable<String>, CharSequence {
    // Java 9+ 使用 byte 数组 + 编码标识
    private final byte[] value;
    private final byte coder;
    
    // 所有可能修改字符串的方法都返回新对象
    public String substring(int beginIndex) {
        // ... 创建新的 String 对象
    }
    
    public String replace(char oldChar, char newChar) {
        // ... 创建新的 String 对象
    }
}
```

---

### 4. 谈谈泛型擦除。既然擦除了，为什么 Java 还能在运行时通过反射获取泛型信息？

#### 泛型擦除原理

**什么是泛型擦除**：
Java 的泛型是**伪泛型**，在编译后类型信息会被擦除，替换为边界类型（或 Object）。

**擦除规则**：
```java
// 编译前（源代码）
public class Box<T> {
    private T value;
    public void set(T value) { this.value = value; }
    public T get() { return value; }
}

// 编译后（字节码）- 类型擦除
public class Box {
    private Object value;  // T 被替换为 Object
    public void set(Object value) { this.value = value; }
    public Object get() { return value; }
}

// 如果有边界
public class NumberBox<T extends Number> {
    private T value;
}
// 编译后
public class NumberBox {
    private Number value;  // T 被替换为边界类型 Number
}
```

**为什么设计成擦除**：
1. **向后兼容**：Java 5 之前没有泛型，擦除保证老代码能运行
2. **不需要修改 JVM**：避免 JVM 层面的大改动

**擦除带来的问题**：
```java
// 问题 1：不能用基本类型
List<int> list = new ArrayList<>(); // 错误，必须是 List<Integer>

// 问题 2：不能创建泛型数组
T[] array = new T[10]; // 编译错误

// 问题 3：instanceof 无法使用
if (obj instanceof List<String>) { } // 编译错误，只能是 List

// 问题 4：静态变量共享
public class Box<T> {
    private static T value; // 错误，静态变量在所有实例间共享
}
```

#### 为什么反射能获取泛型信息？

**关键点**：泛型信息并没有完全丢失，而是保存在了**字节码的签名属性**中！

**Signature 属性**：
```java
// 编译后的 class 文件中，会保留泛型签名信息
public class Box<T> {
    private T value;
}
// 字节码中会有：
// Signature: #23  // <T:Ljava/lang/Object;>Ljava/lang/Object;
```

**反射获取泛型信息的原理**：
```java
public class GenericTest {
    public static void main(String[] args) throws Exception {
        // 1. 获取类的泛型参数
        TypeVariable<Class<Box>>[] typeParameters = Box.class.getTypeParameters();
        System.out.println(typeParameters[0].getName()); // 输出: T
        
        // 2. 获取方法的泛型参数
        Method method = Box.class.getMethod("set", Object.class);
        Type[] genericParameterTypes = method.getGenericParameterTypes();
        System.out.println(genericParameterTypes[0]); // 输出: T
        
        // 3. 获取字段声明的泛型类型
        Field field = Box.class.getDeclaredField("value");
        Type genericType = field.getGenericType();
        System.out.println(genericType); // 输出: T
        
        // 4. 获取继承的泛型信息（最常用）
        class StringBox extends Box<String> {}
        Type genericSuperclass = StringBox.class.getGenericSuperclass();
        if (genericSuperclass instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) genericSuperclass;
            Type[] actualTypeArguments = pt.getActualTypeArguments();
            System.out.println(actualTypeArguments[0]); // 输出: class java.lang.String
        }
    }
}
```

**实际应用场景 - 通用 DAO**：
```java
public abstract class BaseDao<T> {
    private Class<T> entityClass;
    
    @SuppressWarnings("unchecked")
    public BaseDao() {
        // 通过反射获取子类指定的泛型类型
        Type genericSuperclass = getClass().getGenericSuperclass();
        if (genericSuperclass instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) genericSuperclass;
            Type[] actualTypeArguments = pt.getActualTypeArguments();
            this.entityClass = (Class<T>) actualTypeArguments[0];
        }
    }
    
    public T findById(Long id) {
        // 现在可以使用 entityClass 进行操作
        String tableName = entityClass.getSimpleName().toLowerCase();
        // SELECT * FROM table_name WHERE id = ?
    }
}

// 使用
public class UserDao extends BaseDao<User> {
    // entityClass 自动被设置为 User.class
}
```

**Type 体系**：
```
Type (接口)
├── Class (类，如 String.class)
├── ParameterizedType (参数化类型，如 List<String>)
├── TypeVariable (类型变量，如 T)
├── GenericArrayType (泛型数组，如 T[])
└── WildcardType (通配符，如 ? extends Number)
```

---

## 代码实战解析：IntegerExample.java

### Integer 缓存机制详解

**缓存范围**：-128 到 127

**缓存原理**：
```java
// Integer 内部类 IntegerCache
private static class IntegerCache {
    static final Integer cache[];
    
    static {
        // 默认缓存 -128 到 127
        final int low = -128;
        final int high = 127;
        
        cache = new Integer[(high - low) + 1];
        int j = low;
        for(int k = 0; k < cache.length; k++)
            cache[k] = new Integer(j++);
    }
}

// valueOf 方法会使用缓存
public static Integer valueOf(int i) {
    if (i >= IntegerCache.low && i <= IntegerCache.high)
        return IntegerCache.cache[i + (-IntegerCache.low)];
    return new Integer(i);
}
```

**自动装箱的陷阱**：
```java
Integer a = 100;  // 编译后：Integer.valueOf(100) - 使用缓存
Integer b = 100;
System.out.println(a == b); // true - 同一个缓存对象

Integer c = 200;  // 编译后：Integer.valueOf(200) - 超出缓存范围
Integer d = 200;
System.out.println(c == d); // false - 不同的对象

// 正确比较方式
System.out.println(a.equals(b)); // true
System.out.println(c.equals(d)); // true
```

**JVM 参数调整缓存范围**：
```bash
# 可以通过 JVM 参数调整上限（下限固定为 -128）
java -XX:AutoBoxCacheMax=1000 YourClass

# 源码中会读取这个参数
private static class IntegerCache {
    static {
        String cacheSizeProp = VM.getSavedProperty("java.lang.Integer.IntegerCache.size");
        // ... 解析参数并设置 high 值
    }
}
```

**为什么要设计缓存**：
1. **性能优化**：小整数使用频繁，缓存避免重复创建对象
2. **内存优化**：减少堆内存占用
3. **符合常识**：-128~127 是最常用的整数范围

**其他包装类的缓存**：
```java
// Boolean - 缓存 TRUE 和 FALSE
public static final Boolean TRUE = new Boolean(true);
public static final Boolean FALSE = new Boolean(false);

// Byte - 缓存全部（-128~127，只有 256 个值）
private static class ByteCache {
    static final Byte cache[] = new Byte[256];
    static {
        for(int i = 0; i < cache.length; i++)
            cache[i] = new Byte((byte)(i - 128));
    }
}

// Short、Character - 缓存 -128~127
// Long - 缓存 -128~127
// Float、Double - 没有缓存（浮点数范围太大）
```

---

## 面试加分技巧

### 回答模板

当面试官问到这些问题时，建议按以下结构回答：

1. **先说结论**（一句话总结）
2. **解释原理**（底层机制）
3. **举例说明**（代码示例）
4. **拓展延伸**（实际应用场景）

### 示例回答框架

**面试官**：为什么重写 equals 必须重写 hashCode？

**回答**：
> "这是因为 Java 规定了 equals 和 hashCode 的契约。如果两个对象 equals 相等，它们的 hashCode 必须相同。如果不重写，在 HashMap 中会导致逻辑相等的对象无法被正确查找，因为 HashMap 先用 hashCode 定位桶，再用 equals 比较。所以必须保证两者的一致性。"

---

## 常见错误回答（避免踩坑）

❌ **错误 1**："equals 比较的是值，hashCode 比较的是地址"
✅ **正确**：hashCode 返回的是哈希码，不是内存地址。默认实现可能基于地址，但重写后完全由你决定。

❌ **错误 2**："String 不可变是为了安全"
✅ **正确**：要从**安全性**和**性能**两个维度回答，包括常量池、线程安全、哈希缓存等。

❌ **错误 3**："泛型擦除后类型信息完全丢失"
✅ **正确**：泛型信息在字节码的 Signature 属性中保留，反射可以获取。

---

## 深入学习建议

1. **阅读源码**：
   - `java.lang.Object` - equals 和 hashCode 的默认实现
   - `java.lang.String` - 不可变性的实现
   - `java.util.HashMap` - 如何使用 equals 和 hashCode
   - `sun.reflect.generics` - 反射获取泛型的底层实现

2. **实践练习**：
   - 手写一个完整的 equals 和 hashCode 实现
   - 用反射编写通用的 DAO 基类
   - 使用 jclasslib 查看 class 文件的 Signature 属性

3. **扩展阅读**：
   - 《Effective Java》第 3 版 - Item 11, 12
   - JVM 规范 - Class 文件格式
   - Java 语言规范 - 泛型章节
