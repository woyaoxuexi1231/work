# Day 02: 反射与动态代理 - 完整答案

## 面试真题连环炮 - 详细解答

### 1. 反射为什么慢？有哪些优化手段？

#### 为什么反射慢？

**核心原因：反射绕过了编译期优化，在运行时需要额外的检查和操作**

**详细分析**：

**1. 方法调用开销对比**
```java
// 直接调用（快）
obj.method();
// 编译后直接生成 invokevirtual 字节码指令
// JVM 可以进行内联优化

// 反射调用（慢）
Method method = obj.getClass().getMethod("method");
method.invoke(obj);
// 需要：权限检查、参数验证、方法查找、包装/解包装
```

**2. 反射调用的额外步骤**
```java
public Object invoke(Object obj, Object... args) {
    // 步骤 1：权限检查（访问控制）
    if (!override) {
        if (!Reflection.quickCheckMemberAccess(clazz, modifiers)) {
            checkAccess(caller, clazz, obj, modifiers);
        }
    }
    
    // 步骤 2：参数验证和包装
    Object[] argsCopy = copyArguments(args); // 基本类型需要包装
    
    // 步骤 3：方法查找和验证
    MethodAccessor ma = getMethodAccessor(); // 获取方法访问器
    
    // 步骤 4：实际调用
    return ma.invoke(obj, argsCopy);
}
```

**3. 性能测试对比**
```java
public class ReflectionPerformanceTest {
    public void testMethod() {
        int result = 1 + 1;
    }
    
    public static void main(String[] args) throws Exception {
        ReflectionPerformanceTest obj = new ReflectionPerformanceTest();
        Method method = ReflectionPerformanceTest.class.getMethod("testMethod");
        
        int times = 10000000; // 1000万次调用
        
        // 直接调用
        long start1 = System.currentTimeMillis();
        for (int i = 0; i < times; i++) {
            obj.testMethod();
        }
        long time1 = System.currentTimeMillis() - start1;
        System.out.println("直接调用: " + time1 + "ms"); // 约 5-10ms
        
        // 反射调用
        long start2 = System.currentTimeMillis();
        for (int i = 0; i < times; i++) {
            method.invoke(obj);
        }
        long time2 = System.currentTimeMillis() - start2;
        System.out.println("反射调用: " + time2 + "ms"); // 约 500-1000ms
        
        // 性能差距：100-200倍！
    }
}
```

**4. 具体性能损耗来源**

| 损耗来源 | 说明 | 占比 |
|---------|------|------|
| 访问权限检查 | 每次调用都检查方法可见性 | ~30% |
| 参数包装/解包装 | 基本类型与包装类转换 | ~20% |
| 方法查找 | 从方法表中查找目标方法 | ~25% |
| 无法内联优化 | JVM 无法进行方法内联 | ~25% |

---

#### 反射优化手段

**优化 1：setAccessible(true) - 跳过权限检查**
```java
Method method = clazz.getMethod("privateMethod");
method.setAccessible(true); // 关键优化！

long start = System.currentTimeMillis();
for (int i = 0; i < times; i++) {
    method.invoke(obj);
}
// 性能提升：30-50%
```

**优化 2：MethodHandle - Java 7+ 推荐方式**
```java
// 使用 MethodHandle 替代 Method.invoke
MethodHandles.Lookup lookup = MethodHandles.lookup();
MethodType methodType = MethodType.methodType(void.class);
MethodHandle handle = lookup.findVirtual(
    ReflectionPerformanceTest.class, 
    "testMethod", 
    methodType
);

long start = System.currentTimeMillis();
for (int i = 0; i < times; i++) {
    handle.invokeExact(obj); // 类型安全，性能更好
}
// 性能提升：接近直接调用的 2-3 倍
```

**优化 3：缓存 Method 对象**
```java
// 错误做法：每次都查找方法
for (int i = 0; i < times; i++) {
    Method method = obj.getClass().getMethod("testMethod"); // 很慢！
    method.invoke(obj);
}

// 正确做法：缓存 Method 对象
Method method = obj.getClass().getMethod("testMethod");
for (int i = 0; i < times; i++) {
    method.invoke(obj); // 复用 Method 对象
}
```

**优化 4：使用字节码生成库**
```java
// 使用 CGLIB、ByteBuddy 等库在运行时生成代理类
// 避免反射，直接调用生成的字节码

// 示例：ByteBuddy
new ByteBuddy()
    .subclass(ReflectionPerformanceTest.class)
    .method(named("testMethod"))
    .intercept(MethodDelegation.to(Interceptor.class))
    .make()
    .load(getClass().getClassLoader());
// 性能：接近直接调用
```

**优化 5：JIT 的反射内联优化（Java 8+）**
```java
// Java 8 以后，JVM 会对频繁调用的反射方法进行优化
// MethodAccessor 会从 Native 实现切换为字节码实现

// 阈值：默认 15 次调用后触发优化
// 可以通过 -Dsun.reflect.inflationThreshold=0 禁用
```

---

### 2. JDK 动态代理为什么要求必须有接口？CGLIB 是如何实现代理的？

#### JDK 动态代理 - 为什么必须有接口？

**核心原因**：JDK 动态代理是通过 `java.lang.reflect.Proxy` 类在运行时**动态生成一个实现指定接口的代理类**来实现的。

**底层原理**：

**1. 代理类生成过程**
```java
// JDK 动态代理的核心代码
public static Object newProxyInstance(
    ClassLoader loader,           // 类加载器
    Class<?>[] interfaces,        // 接口数组（必须有！）
    InvocationHandler h           // 调用处理器
) {
    // 1. 生成代理类的字节码
    byte[] proxyClassFile = ProxyGenerator.generateProxyClass(
        proxyName, interfaces, accessFlags
    );
    
    // 2. 定义代理类
    Class<?> proxyClass = defineClass0(loader, proxyName, 
                                       proxyClassFile, 0, proxyClassFile.length);
    
    // 3. 创建代理实例
    return proxyClass.getConstructor(InvocationHandler.class)
                     .newInstance(new Object[]{h});
}
```

**2. 生成的代理类结构**
```java
// JDK 生成的代理类（伪代码）
public final class $Proxy0 extends Proxy implements UserService {
    private static Method m1; // equals
    private static Method m2; // toString
    private static Method m3; // hashCode
    private static Method m4; // 你的接口方法，如 getUserById
    
    static {
        m4 = Class.forName("UserService").getMethod("getUserById", Long.class);
    }
    
    public User getUserById(Long id) {
        try {
            // 调用 InvocationHandler.invoke
            return (User) super.h.invoke(this, m4, new Object[]{id});
        } catch (Throwable e) {
            throw new UndeclaredThrowableException(e);
        }
    }
}
```

**3. 为什么必须有接口？**
```java
// Java 是单继承，Proxy 类已经继承了 Proxy
public class Proxy {
    protected InvocationHandler h;
}

// 生成的代理类：
public final class $Proxy0 extends Proxy implements UserService {
    // 如果 UserService 不是接口而是类，就无法实现
    // 因为 Java 不支持多继承
}
```

**对比示例**：
```java
// ✅ 正确：基于接口
public interface UserService {
    User getUserById(Long id);
}
UserService proxy = (UserService) Proxy.newProxyInstance(
    classLoader, 
    new Class[]{UserService.class}, 
    handler
);

// ❌ 错误：基于类
public class UserServiceImpl {  // 不是接口
    public User getUserById(Long id) { ... }
}
// 无法用 JDK 动态代理，必须用 CGLIB
```

---

#### CGLIB 实现原理

**核心思想**：通过**继承**目标类，生成子类作为代理类，重写父类方法实现增强。

**底层原理**：

**1. CGLIB 生成代理类**
```java
// CGLIB 使用示例
public class UserServiceImpl {
    public User getUserById(Long id) {
        // 业务逻辑
        return new User(id, "张三");
    }
}

// 创建代理
Enhancer enhancer = new Enhancer();
enhancer.setSuperclass(UserServiceImpl.class); // 设置父类
enhancer.setCallback(new MethodInterceptor() {
    @Override
    public Object intercept(Object obj, Method method, Object[] args, 
                           MethodProxy proxy) throws Throwable {
        System.out.println("前置增强");
        Object result = proxy.invokeSuper(obj, args); // 调用父类方法
        System.out.println("后置增强");
        return result;
    }
});

UserServiceImpl proxy = (UserServiceImpl) enhancer.create();
```

**2. CGLIB 生成的代理类结构**
```java
// CGLIB 生成的代理类（伪代码）
public class UserServiceImpl$$EnhancerByCGLIB$$12345678 extends UserServiceImpl {
    private MethodInterceptor CGLIB$CALLBACK_0;
    
    public User getUserById(Long id) {
        if (CGLIB$CALLBACK_0 != null) {
            return (User) CGLIB$CALLBACK_0.intercept(
                this,
                UserServiceImpl.class.getMethod("getUserById", Long.class),
                new Object[]{id},
                MethodProxy.create(...) // 方法代理
            );
        }
        return super.getUserById(id);
    }
}
```

**3. CGLIB vs JDK 动态代理对比**

| 特性 | JDK 动态代理 | CGLIB |
|------|-------------|-------|
| **实现方式** | 实现接口 | 继承目标类 |
| **目标要求** | 必须有接口 | 目标类不能有 final |
| **性能** | 方法调用稍慢 | 方法调用快，但创建慢 |
| **JVM 限制** | 无 | 无法代理 final 类/方法 |
| **底层技术** | Java 反射 | ASM 字节码操作框架 |
| **Spring 默认** | 目标有接口时使用 | 目标无接口时使用 |

**4. CGLIB 的 FastClass 机制**
```java
// CGLIB 优化：使用 FastClass 避免反射
// FastClass 为每个方法分配索引，直接调用
public class UserServiceImpl$$FastClassByCGLIB$$87654321 {
    public Object invoke(int index, Object obj, Object[] args) {
        UserServiceImpl instance = (UserServiceImpl) obj;
        switch(index) {
            case 1: return instance.getUserById((Long) args[0]);
            // ... 其他方法
        }
    }
    
    // 通过方法签名获取索引
    public int getIndex(String methodName, Class[] parameterTypes) {
        // ... 映射逻辑
    }
}

// 使用时
int index = fastClass.getIndex("getUserById", new Class[]{Long.class});
fastClass.invoke(index, target, args); // 无反射，性能更好
```

---

### 3. 动态代理在 Spring AOP 中是如何切换的？

#### Spring AOP 的代理选择策略

**核心逻辑**：Spring AOP 根据目标对象是否实现接口，自动选择 JDK 动态代理或 CGLIB。

**判断流程**：
```java
// Spring AOP 的代理创建逻辑（简化版）
public AopProxy createAopProxy(AdvisedSupport config) {
    // 条件 1：是否强制使用 CGLIB
    if (config.isOptimize() || config.isProxyTargetClass()) {
        return new ObjenesisCglibAopProxy(config); // 使用 CGLIB
    }
    
    // 条件 2：目标类是否实现了接口
    if (!config.hasInterfaces()) {
        return new ObjenesisCglibAopProxy(config); // 没有接口，用 CGLIB
    }
    
    // 条件 3：默认使用 JDK 动态代理
    return new JdkDynamicAopProxy(config);
}
```

**配置方式**：

**1. 默认行为（Spring Boot 2.x）**
```java
@Service
public class UserServiceImpl implements UserService {
    @Transactional
    public User getUserById(Long id) {
        return userRepository.findById(id).orElse(null);
    }
}

// Spring 会自动选择 JDK 动态代理（因为实现了 UserService 接口）
```

**2. 强制使用 CGLIB**
```java
// 方式 1：配置类
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true) // 强制 CGLIB
public class AopConfig {
}

// 方式 2：application.yml（Spring Boot）
spring:
  aop:
    proxy-target-class: true

// 方式 3：@EnableTransactionManagement
@EnableTransactionManagement(proxyTargetClass = true)
```

**3. Spring Boot 2.x 的变化**
```java
// Spring Boot 1.x：默认使用 JDK 动态代理
// Spring Boot 2.x：默认使用 CGLIB（proxy-target-class 默认为 true）

// 原因：
// 1. 避免类型转换问题
// 2. CGLIB 性能更好（方法调用快）
// 3. 减少 JDK 代理的限制
```

**实际案例分析**：

**案例 1：同类方法调用 AOP 失效**
```java
@Service
public class UserServiceImpl implements UserService {
    
    public User getUserById(Long id) {
        // 问题：这里调用 updateLastLoginTime 不会触发 AOP！
        updateLastLoginTime(id);
        return userRepository.findById(id).orElse(null);
    }
    
    @Transactional
    public void updateLastLoginTime(Long id) {
        // 数据库操作
    }
}

// 原因：getUserById 内部调用的是 this.updateLastLoginTime()
// this 是目标对象，不是代理对象，所以不会走代理逻辑

// 解决方案 1：通过 AopContext 获取代理对象
public User getUserById(Long id) {
    UserServiceImpl proxy = (UserServiceImpl) AopContext.currentProxy();
    proxy.updateLastLoginTime(id); // 走代理
}

// 解决方案 2：注入自身
@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserService self; // 注入的是代理对象
    
    public User getUserById(Long id) {
        self.updateLastLoginTime(id); // 走代理
    }
}
```

**案例 2：final 方法无法代理**
```java
@Service
public class UserServiceImpl implements UserService {
    
    // ❌ final 方法，CGLIB 无法重写，AOP 失效
    public final User getUserById(Long id) {
        return userRepository.findById(id).orElse(null);
    }
}

// 解决方案：去掉 final 关键字
```

---

## 代码实战解析

### ReflectionExample.java 核心要点

**反射的基本使用**：
```java
// 1. 获取 Class 对象（3 种方式）
Class<?> clazz1 = Class.forName("com.example.User"); // 最常用
Class<?> clazz2 = User.class;                        // 编译期已知类型
Class<?> clazz3 = userInstance.getClass();           // 运行期实例

// 2. 创建对象
User user = (User) clazz1.newInstance(); // 已过时
User user = clazz1.getDeclaredConstructor().newInstance(); // 推荐

// 3. 获取方法并调用
Method method = clazz1.getMethod("setName", String.class);
method.invoke(user, "张三");

// 4. 访问私有成员
Field field = clazz1.getDeclaredField("id");
field.setAccessible(true); // 跳过权限检查
field.set(user, 1L);
```

### ProxyTest.java 核心要点

**JDK 动态代理完整示例**：
```java
// 1. 定义接口
public interface UserService {
    User getUserById(Long id);
    void saveUser(User user);
}

// 2. 实现接口
public class UserServiceImpl implements UserService {
    public User getUserById(Long id) {
        return new User(id, "张三");
    }
    public void saveUser(User user) {
        System.out.println("保存用户: " + user);
    }
}

// 3. 创建 InvocationHandler
public class LogInvocationHandler implements InvocationHandler {
    private Object target;
    
    public LogInvocationHandler(Object target) {
        this.target = target;
    }
    
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) 
            throws Throwable {
        // 前置增强
        System.out.println("开始执行: " + method.getName());
        long start = System.currentTimeMillis();
        
        // 调用目标方法
        Object result = method.invoke(target, args);
        
        // 后置增强
        long time = System.currentTimeMillis() - start;
        System.out.println("执行完成: " + method.getName() + 
                          ", 耗时: " + time + "ms");
        
        return result;
    }
}

// 4. 创建代理对象
UserService target = new UserServiceImpl();
UserService proxy = (UserService) Proxy.newProxyInstance(
    target.getClass().getClassLoader(),
    target.getClass().getInterfaces(),
    new LogInvocationHandler(target)
);

// 5. 使用代理对象
User user = proxy.getUserById(1L);
// 输出：
// 开始执行: getUserById
// 执行完成: getUserById, 耗时: 5ms
```

---

## 面试加分技巧

### 回答模板

**面试官**：反射为什么慢？

**回答结构**：
1. **直接回答原因**："反射慢主要是因为绕过了编译期优化，运行时需要额外的权限检查、参数验证、方法查找等操作"
2. **列举具体损耗**："主要包括访问权限检查占 30%、参数包装 20%、方法查找 25%、无法内联优化 25%"
3. **给出优化方案**："可以通过 setAccessible(true)、MethodHandle、缓存 Method 对象、使用字节码生成库等方式优化"
4. **举例说明**："在实际项目中，我使用 MethodHandle 替代 Method.invoke，性能提升了 50%"

### 常见错误回答

❌ **错误 1**："反射慢是因为用了 Native 方法"
✅ **正确**：反射的慢主要是 Java 层的额外操作，不是 Native 方法的问题。

❌ **错误 2**："JDK 代理和 CGLIB 性能差不多"
✅ **正确**：JDK 代理创建快但调用慢，CGLIB 创建慢但调用快（有 FastClass 优化）。

❌ **错误 3**："Spring AOP 只能用 JDK 代理"
✅ **正确**：Spring AOP 会根据情况自动选择 JDK 代理或 CGLIB，也可以强制指定。

---

## 深入学习建议

1. **阅读源码**：
   - `java.lang.reflect.Proxy` - JDK 动态代理核心
   - `sun.misc.ProxyGenerator` - 代理类字节码生成
   - `net.sf.cglib.proxy.Enhancer` - CGLIB 核心类
   - `org.springframework.aop.framework.ProxyFactory` - Spring AOP 代理工厂

2. **实践练习**：
   - 手写一个简单的动态代理框架
   - 对比 JDK 代理、CGLIB、ByteBuddy 的性能
   - 使用 ASM 直接操作字节码

3. **扩展阅读**：
   - 《深入理解 Java 虚拟机》- 字节码执行引擎
   - ASM 官方文档 - 字节码操作框架
   - Spring AOP 源码解析
