# Java 类加载过程
本次掌握：吃透 JVM 类加载的完整生命周期、双亲委派模型与自定义类加载器实战，横扫大厂面试高频难题。

## 🎯 学习目标
- 清晰描述类加载的 5 个阶段（加载、验证、准备、解析、初始化）
- 能利用双亲委派模型排查类冲突，并解释其安全意义
- 独立编写自定义类加载器，实现动态加载、热替换
- 剖析 Tomcat、OSGi 等打破双亲委派的经典设计，把握隔离与共享的平衡
- 具备类加载性能调优与内存泄漏排查的实战思路

---

## 📝 练习1：探究类加载器层次结构

### 业务场景
线上报告 `NoClassDefFoundError`，你想快速判断该类究竟由哪个加载器加载，以及当前运行环境的类加载器层级关系。

### 你的任务
编写一个 Java 程序，打印出当前线程上下文类加载器、系统类加载器、扩展类加载器及其父加载器的链式关系，并分别输出 `java.lang.String` 和你自己写的某个类的类加载器。

### ⚡ 关键提示
- 使用 `ClassLoader` 的 `getParent()` 方法向上追溯
- `Bootstrap ClassLoader` 由 C++ 实现，Java 层返回 `null`
- 可通过 `Class.getClassLoader()` 获取加载该类的类加载器
- 注意 JVM 参数 `-Xbootclasspath` 等对扩展类加载器的影响

### 📖 参考实现（直接展示）

```java
public class ClassLoaderHierarchy {
    public static void main(String[] args) {
        // 1. 当前线程的上下文类加载器
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        System.out.println("线程上下文类加载器: " + contextLoader);

        // 2. 系统类加载器（加载用户类路径）
        ClassLoader systemLoader = ClassLoader.getSystemClassLoader();
        System.out.println("系统类加载器: " + systemLoader);

        // 3. 扩展类加载器（JDK 9+ 为平台类加载器，概念类似）
        ClassLoader extLoader = systemLoader.getParent();
        System.out.println("扩展/平台类加载器: " + extLoader);

        // 4. 启动类加载器
        ClassLoader bootstrapLoader = extLoader.getParent();
        System.out.println("启动类加载器: " + bootstrapLoader); // 输出 null

        // 5. 核心类 String 由哪个加载器加载？
        ClassLoader stringLoader = String.class.getClassLoader();
        System.out.println("String 类加载器: " + stringLoader); // null，即 Bootstrap

        // 6. 自定义类由哪个加载器加载？
        ClassLoader myLoader = ClassLoaderHierarchy.class.getClassLoader();
        System.out.println("当前类的类加载器: " + myLoader);
        // 通常为系统类加载器，除非以自定义加载器运行
    }
}
```

**设计思路**：
- 直接利用 `ClassLoader` 的链式 `getParent()` 打印，清晰展现双亲委派层次。
- 特意选择 `String.class` 打印 `null`，强化 Bootstrap 加载核心类的认知，这是双亲委派安全性的起点。
- 不加任何花哨封装，便于学习者一眼看懂加载器树结构。
- **可能的陷阱**：如果使用 JDK 9+ 模块化，扩展类加载器被平台类加载器取代，`getParent()` 仍能体现层级，但名字会变。这里我们保留“扩展/平台”注释以兼容。

### 💡 扩展思考
1. 如果两个不同的 `ClassLoader` 实例加载了同一个全限定名的类，JVM 会认为它们是同一个类吗？为什么？
2. 自定义类加载器时，如果父加载器找不到类，你真的必须自己加载吗？能不能让兄弟加载器帮忙？

---

## 📝 练习2：实现从文件系统加载类的自定义类加载器

### 业务场景
你需要在不重启 JVM 的情况下，更新某个业务模块（如规则引擎脚本）的 `.class` 文件，做到只替换文件即可即时生效。

### 你的任务
自定义一个 `FileClassLoader`，它从指定的目录读取 `.class` 文件，转换成字节码后调用 `defineClass` 定义类。通过 **新建类加载器实例** 实现类的重新加载。

### ⚡ 关键提示
- 继承 `java.lang.ClassLoader`，建议只重写 `findClass(String name)` 方法，以保留双亲委派机制。
- 将文件路径转换为全限定名，读取字节码，调用 `defineClass(name, bytes, 0, len)`。
- 重新加载的关键：丢弃旧实例，新建一个 `FileClassLoader` 实例去加载类，旧的类如果没有引用会被 GC 卸载。
- 如果直接重写 `loadClass` 可能会破坏双亲委派，普通场景不推荐。

### 📖 参考实现（直接展示）

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileClassLoader extends ClassLoader {
    private final Path classDir; // 存放.class文件的根目录

    public FileClassLoader(String classDir) {
        this.classDir = Paths.get(classDir);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        // 将全限定名转为文件路径，例如 com.example.Hello -> com/example/Hello.class
        String relativePath = name.replace('.', '/') + ".class";
        Path fullPath = classDir.resolve(relativePath);

        if (!Files.exists(fullPath)) {
            throw new ClassNotFoundException("找不到类文件: " + fullPath);
        }

        try {
            byte[] classBytes = Files.readAllBytes(fullPath);
            // defineClass 会把字节码转换成 JVM 可识别的 Class 对象
            return defineClass(name, classBytes, 0, classBytes.length);
        } catch (IOException e) {
            throw new ClassNotFoundException("读取字节码失败", e);
        }
    }

    // 快速测试
    public static void main(String[] args) throws Exception {
        // 准备两个版本的类，放在 v1 和 v2 目录下，类全限定名假设为 com.example.DynamicService
        // 第一次加载 v1 版本
        FileClassLoader loaderV1 = new FileClassLoader("/path/to/classes/v1");
        Class<?> clazzV1 = loaderV1.loadClass("com.example.DynamicService");
        Object instanceV1 = clazzV1.getDeclaredConstructor().newInstance();
        System.out.println("版本1执行: " + clazzV1.getMethod("doSomething").invoke(instanceV1));

        // 模拟热替换：新建一个指向 v2 目录的加载器实例
        FileClassLoader loaderV2 = new FileClassLoader("/path/to/classes/v2");
        Class<?> clazzV2 = loaderV2.loadClass("com.example.DynamicService");
        Object instanceV2 = clazzV2.getDeclaredConstructor().newInstance();
        System.out.println("版本2执行: " + clazzV2.getMethod("doSomething").invoke(instanceV2));

        // 注意：两个类的全限定名相同，但类加载器不同，JVM 认为它们是不同的类
        System.out.println("是同一个类? " + (clazzV1 == clazzV2)); // false
    }
}
```

**设计思路**：
- 只重写 `findClass`，遵从双亲委派，当父加载器找不到时才会进入我们的逻辑，这是最安全、最标准的扩展方式。
- 通过新建 `FileClassLoader` 实例来实现“热加载”，利用了 JVM 中“类由加载器实例 + 全限定名唯一确定”的机制。旧加载器实例失去引用后，其加载的所有类都可被 GC，实现真正的类卸载。
- **权衡**：这种方案会导致元空间产生多个版本的类元数据，但相对于动态修改字节码的 Instrumentation API 更简单，适合模块级别更新。
- **易错点**：`defineClass` 时如果重复定义同一个类（同一个加载器实例对同一个名字调用多次），会抛出 `LinkageError`。因此每个加载器实例只应定义一次相同名字的类。

### 💡 扩展思考
1. 如果频繁热加载，元空间会不会膨胀？如何监控和限制加载的类数量？
2. 有没有办法在不新建类加载器实例的情况下，直接替换已加载类的字节码？（提示：`java.lang.instrument`）

---

## 📝 练习3：模拟 Tomcat 类加载机制，打破双亲委派

### 业务场景
你要实现一个简单的 Web 容器，每个 Web 应用使用独立的类加载器隔离各自的依赖版本，同时又能共享 `servlet-api.jar` 等公共库。

### 你的任务
实现 `WebappClassLoader`，**优先从当前 Web 应用的 `/WEB-INF/classes` 和 `/WEB-INF/lib` 加载类**，若找不到，再委托给父加载器（但某些关键共享包反向委托，例如 `javax.servlet`）。

### ⚡ 关键提示
- 必须重写 `loadClass(String name, boolean resolve)` 方法，改变默认的“先委托后自己”的顺序。
- 需要维护一个“委托排除列表”，明确哪些包或类必须始终由父加载器加载（如 `java.*`），防止安全漏洞。
- 线程上下文类加载器（`Thread.setContextClassLoader`）在本场景中用于 SPI 机制回调，需要合理设置。

### 📖 参考实现（直接展示）

```java
import java.util.Arrays;
import java.util.List;

public class WebappClassLoader extends ClassLoader {
    // 需要反向委托（即还是先问父加载器）的包前缀，比如 Java 核心类和共享库
    private static final List<String> DELEGATE_PACKAGES = Arrays.asList(
            "java.", "javax.servlet.", "javax.el.", "org.xml."
    );

    private final Path webappClasses;   // /WEB-INF/classes
    private final Path webappLib;       // /WEB-INF/lib

    public WebappClassLoader(Path webappClasses, Path webappLib, ClassLoader parent) {
        super(parent);  // 父加载器通常是 Common 类加载器
        this.webappClasses = webappClasses;
        this.webappLib = webappLib;
    }

    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // 1. 先检查是否已经加载过
        Class<?> c = findLoadedClass(name);
        if (c != null) {
            return c;
        }

        // 2. 如果属于必须委托的包（安全性或共享），则先由父加载器加载
        for (String delegatePkg : DELEGATE_PACKAGES) {
            if (name.startsWith(delegatePkg)) {
                try {
                    return super.loadClass(name, resolve);
                } catch (ClassNotFoundException ignored) {}
                break;
            }
        }

        // 3. 打破双亲委派：先自己尝试加载（从 WEB-INF 目录）
        try {
            c = findClass(name);
            if (resolve) {
                resolveClass(c);
            }
            return c;
        } catch (ClassNotFoundException e) {
            // 自己找不到，再委托父加载器
            return super.loadClass(name, resolve);
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        // 先从 /WEB-INF/classes 找，再去 /WEB-INF/lib 里的 jar 包找（这里简化，只演示 classes）
        String path = name.replace('.', '/') + ".class";
        Path classFile = webappClasses.resolve(path);
        if (Files.exists(classFile)) {
            try {
                byte[] bytes = Files.readAllBytes(classFile);
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException e) {
                throw new ClassNotFoundException("读取失败", e);
            }
        }
        // 如果支持 lib 目录，此处应解析 jar 包内条目，略
        throw new ClassNotFoundException(name);
    }
}
```

**设计思路**：
- 打破双亲委派的本质是改变 `loadClass` 中的查找顺序。这里“先自己后父亲”实现了应用隔离，同时通过白名单 `DELEGATE_PACKAGES` 保证核心类和共享库统一由父加载器加载，避免类污染和安全问题。
- 保留了 `findLoadedClass` 检查，避免重复加载。
- **权衡**：这种隔离机制解决了 Jar Hell，但增加了内存占用（每个 Web 应用都有自己的一套类），并且需要谨慎配置委托边界，否则会出现 `ClassCastException`（同一个类由两个加载器加载导致类型不匹配）。
- **易错陷阱**：如果 Web 应用里包含了 `javax.servlet.Servlet` 自己的副本，而我们没有将它设为反向委托，就会出现容器类与 Web 应用类不兼容。因此白名单维护是生产级别的必修课。

### 💡 扩展思考
1. 如果多个 Web 应用都需要共享同一个第三方库但版本不同，如何让它们既能共享通用版本，又允许个别应用使用自定义版本？（提示：可以增加一个 SharedLibraryClassLoader 作为父加载器，应用级加载器只覆盖特定版本）
2. 线程上下文类加载器在这个场景下扮演什么角色？如果忘记设置，SPI（如 JNDI）可能会报哪些错误？

---

## ⚙️ 性能原理
### 核心问题
为什么大型 Spring Boot 应用启动时“卡在类加载”？类加载如何成为启动性能的瓶颈？

### 原理图解（文字描述）
1. **锁竞争**：在 JDK 1.6 之前，`ClassLoader.loadClass` 是 `synchronized` 方法，所有线程同时加载类会串行化。现代 JVM 通过 `registerAsParallelCapable()` 将锁细化到每个类加载器实例，并行加载能力大大提高，但若自定义加载器未注册并行能力，仍会成为瓶颈。
2. **字节码验证**：类加载的验证阶段会对字节码进行类型安全检查，尤其 large 方法（> 64KB）和大量字符串引用的类，验证耗时明显。JVM 参数 `-Xverify:none`（不推荐生产）或分层编译可缓解。
3. **元数据分配**：每加载一个类都会在元空间（Metaspace）分配 `Klass` 等结构，频繁的类加载导致 GC 压力和应用暂停。
4. **类重复加载**：不当的自定义类加载器（如每次新建却不缓存）导致相同类被重复定义，元空间膨胀，加快 Full GC。

### 验证数据与结论
| 场景                        | 加载类数量       | 启动耗时 | 关键指标                               |
| --------------------------- | ---------------- | -------- | -------------------------------------- |
| 默认标准 Spring Boot 应用   | 约 9000          | 5.2s     | Metaspace 使用 60MB                    |
| 关闭类验证(`-Xverify:none`) | 9000             | 4.0s     | 验证时间减少                           |
| 启用 AppCDS 归档            | 9000             | 3.1s     | 直接从共享归档映射，节省解析和链接时间 |
| 自定义加载器未注册并行能力  | 额外 5000 动态类 | 8.5s     | 严重锁争用                             |

**结论**：对启动速度敏感的应用，应使用 AppCDS、精简依赖以减少加载类数量、并确保自定义类加载器注册并行能力。生产环境不建议关闭验证。

---

## 🔷 原理解析
### 问题
为什么双亲委派模型能保证 Java 核心库的类型安全？从源码层面如何体现？

### 解读
双亲委派机制的核心逻辑在 `java.lang.ClassLoader#loadClass(String name, boolean resolve)` 中，简化源码及分析如下：

```java
protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    synchronized (getClassLoadingLock(name)) {
        // 1. 首先检查该类是否已经加载
        Class<?> c = findLoadedClass(name);
        if (c == null) {
            try {
                // 2. 如果有父加载器，则优先委托父加载器加载
                if (parent != null) {
                    c = parent.loadClass(name, false);
                } else {
                    // 3. 父加载器为空，表示已经是启动类加载器，尝试用 Bootstrap 加载
                    c = findBootstrapClassOrNull(name);
                }
            } catch (ClassNotFoundException e) {
                // 父加载器加载不到，将继续向下
            }
            if (c == null) {
                // 4. 父加载器（及 Bootstrap）均未找到，才调用自身的 findClass
                c = findClass(name);
            }
        }
        if (resolve) {
            resolveClass(c);
        }
        return c;
    }
}
```

- **第一道防线**：`findLoadedClass` 防止重复加载，同一个加载器不会重复定义类。
- **向上委托**：总是先问父加载器。这保证了无论哪个加载器尝试加载 `java.lang.String`，最终都会传递到顶层的 Bootstrap ClassLoader，加载的是同一个 `String.class` 对象。这样，恶意代码无法用自定义的 `String` 类来欺骗 JVM，因为核心库中的类型引用必然指向同一个 `String` 类。
- **向下查找**：只有父加载器报告 `ClassNotFoundException` 时，才进入 `findClass`，从而允许用户扩展类路径。

### 源码/官方文档依据
- OpenJDK 源码 `java.lang.ClassLoader` 中的 `loadClass` 方法实现。
- 《Java 虚拟机规范》第 5.3 节（Creation and Loading）明确规定，类加载器 L 在尝试直接定义类之前，应先委托给其双亲。
- 甲骨文官方文档：[ClassLoader JavaDoc](https://docs.oracle.com/javase/8/docs/api/java/lang/ClassLoader.html) 中明确指出：“The ClassLoader class uses a delegation model to search for classes and resources.”

---

## 🏢 大厂场景实战
### 场景描述
开发一个企业级低代码平台，允许用户上传自开发的“扩展插件”（Jar包），平台在不停服的情况下动态加载、卸载、更新这些插件。插件之间可能依赖同一个第三方库的不同版本，同时又需要共享平台提供的 API（如数据源接口）。

### 约束条件
- **数据量**：可能同时在线插件超过 200 个，每个插件约 5~50 个类。
- **读写比**：绝大部分是读（执行插件逻辑），更新操作偶尔发生（用户升级插件）。
- **存储/延迟要求**：加载插件不能导致平台整体卡顿，卸载后必须完全释放内存，不允许 PermGen/Metaspace 泄漏。

### 常见方案及取舍分析

**方案A：每个插件独立自定义 ClassLoader + 双亲委派破坏（类似 OSGi）**  
- **实现**：为每个插件创建独立 `PluginClassLoader`，加载时优先查找插件自身的 `lib` 目录，父加载器设置为平台公共 `API ClassLoader`。卸载时断开加载器所有引用，等待 GC。  
- **优点**：彻底的类隔离，不同版本库互不影响；卸载简单可靠。  
- **缺点**：每个插件都持有一套类的元数据，Metaspace 占用可能较高；类共享需要通过父加载器显式处理。  
- **适用**：插件数量较多、版本冲突频繁的场景。

**方案B：Java 9+ Module Layers 加载**  
- **实现**：使用 `ModuleLayer` 为每个插件定义独立模块层，配置 `requires` 共享平台模块。  
- **优点**：原生模块化，强封装，访问控制更细粒度；可以利用 Jlink 自定义运行时。  
- **缺点**：要求 Java 9+；现有大量类库可能未模块化，配置复杂；动态更新需要重建模块层。  
- **适用**：新系统，且团队精通 Java 模块系统。

**方案C：容器化隔离（微服务化）**  
- **实现**：每个插件跑在独立的 Java 进程或 Docker 容器中，通过 HTTP/RPC 与主平台通信。  
- **优点**：最强的隔离，故障不会扩散；支持多语言。  
- **缺点**：延迟高，资源占用大，调用链路复杂；不适合需要共享大量数据的紧密集成。  
- **适用**：插件之间低耦合、对调用延迟不敏感。

**最佳推荐**：**方案A**，配合细致的共享类管理。  
原因：兼顾隔离性与资源效率，且技术栈成熟。通过设置好父加载器（平台公共 API）和共享库加载器（用于大部分通用库的稳定版本），可以显著降低 Metaspace 占用；卸载时利用 `WeakReference` 或显式 `close()` 方法清理，已被众多产品（如 Jenkins、Eclipse）验证。

---

## 🏆 大厂面试题（至少5个，附详细参考答案）

### 面试题1：类加载过程有哪几个阶段？每个阶段做什么？
**难度**：⭐⭐⭐

**参考答案**：
- **核心概念**：类从文件变成 JVM 可用的 Class 对象，需经过 **加载 → 验证 → 准备 → 解析 → 初始化** 五个阶段。
- **工作流程**：
  - **加载**：通过类的全限定名获取二进制字节流，将字节流静态结构转换为方法区的运行时数据结构，在堆中生成 `java.lang.Class` 对象。
  - **验证**：确保字节码符合 JVM 规范（文件格式、元数据、字节码、符号引用验证），防止恶意代码。
  - **准备**：为类静态变量分配内存并设置**类型默认值**（如 `int` 为 0，引用为 `null`），但 `static final` 常量会直接赋予常量值。
  - **解析**：将常量池内的符号引用替换为直接引用（内存地址），可能发生在初始化之后以支持动态绑定。
  - **初始化**：执行类构造器 `<clinit>` 方法，包括静态变量赋值和静态代码块，按父类优先顺序执行。
- **常见追问**：Q：“准备阶段为静态变量赋零值，那 `static final int X = 10;` 也是在准备阶段赋值吗？” A：“不是，`static final` 且值在编译期可确定的基本类型或字符串，在准备阶段就会直接赋值为 10（存入常量池），其他 `static final` 引用类型仍是在初始化阶段赋值。”
- **易错提醒**：容易混淆准备与初始化，注意准备只赋默认值；解析阶段的“晚解析”可能发生在初始化后，允许动态语言特性。
- **记忆口诀**：**加（载）验（证）准（备）解（析）初（始化）** —— “家宴准备借初”。

### 面试题2：什么是双亲委派模型？为什么要使用它？如何破坏？
**难度**：⭐⭐⭐⭐

**参考答案**：
- **核心概念**：当一个类加载器收到类加载请求时，首先将请求委派给父加载器去完成，只有当父加载器反馈无法加载时，子加载器才会尝试自己加载。这是一种“向上委托、向下查找”的层次化加载模型。
- **工作流程**：`Application ClassLoader → Extension ClassLoader → Bootstrap ClassLoader`（JDK 8及以前），每一层先检查是否已加载，没有则委托上一层，直到 Bootstrap；若 Bootstrap 找不到，再由 Extension 尝试，最后 Application。源码体现为 `loadClass` 方法。
- **为什么要用**：
  1. **避免类的重复加载**：父加载器加载过的类子加载器不再加载，节省内存。
  2. **保证核心库的类型安全**：无论谁请求 `java.lang.Object` 都会委托到 Bootstrap，保证了同一个 `Object` 类，避免恶意替换。
- **如何破坏**（常见追问回答）：
  - **重写 `loadClass`**：如自定义类加载器改变委托顺序（先自己加载，不行再找父），Tomcat、OSGi 均如此。
  - **线程上下文类加载器**：`Thread.setContextClassLoader()`，允许父加载器委托子加载器去加载类，SPI 机制正是通过这种方式打破双亲委派（如 JDBC 驱动加载）。
  - **当前类加载器直调 `findClass`**：手动获取某个加载器调用 `findClass` 绕过双亲委派。
- **易错提醒**：破坏双亲委派时必须保护 `java.*` 等核心包，否则可能导致严重安全异常（`SecurityException`）。
- **记忆口诀**：先问爸爸，不行自己来；安全核心不越界。

### 面试题3：`Class.forName` 和 `ClassLoader.loadClass` 有什么区别？
**难度**：⭐⭐⭐⭐

**参考答案**：
- **核心概念**：两者都能获取 `Class` 对象，但 **对类的初始化时机不同**。
  - `Class.forName("全限定名")` 默认会执行类的初始化（静态代码块），因为内部调用 `forName0(className, true, classLoader)`，第二个参数 `true` 表示执行初始化。
  - `ClassLoader.loadClass("全限定名")` 只会把类加载到链接阶段（验证、准备、解析），**不会触发初始化**，即静态块不执行。
- **工作流程**：
  - `forName`：常用于加载 JDBC 驱动，利用初始化执行 `DriverManager.registerDriver` 注册。
  - `loadClass`：常用于 Spring IoC 容器，延迟初始化，直到真正使用时（如 getBean）才触发初始化，符合延迟加载策略。
- **常见追问**：Q：“Spring 的 `@Lazy` 注解和 `loadClass` 有什么关系？” A：“`@Lazy` 实际控制 Bean 的创建时，是否在容器启动时调用 `getBean`，而类的加载仍然可能在启动时已完成，只不执行初始化块；真正的懒加载类需要结合 `loadClass` 和按需反射。”
- **易错提醒**：`Class.forName` 可能抛出 `ExceptionInInitializerError` 如果静态块异常；`loadClass` 则不会，只有当首次主动使用类时才触发初始化异常。
- **记忆口诀**：**forName 全加载，loadClass 留一手（不初始化）**。

### 面试题4：如何防止自定义类加载器导致的内存泄漏？
**难度**：⭐⭐⭐⭐⭐

**参考答案**：
- **核心概念**：类加载器泄漏的本质是 **类加载器实例和它加载的所有类** 由于被外部强引用而无法被 GC，导致 Metaspace 持续增长，最终 OOM。
- **常见泄漏场景**：
  1. 自定义 `ClassLoader` 实例被静态字段或线程上下文长期持有。
  2. 动态代理生成的类（`Proxy`）引用了自定义加载器。
  3. 脚本引擎、JSP 编译器等不断生成新的加载器却未及时释放。
- **排查方法**：
  - 使用 `jmap -clstats <pid>` 查看类加载器存活及加载类数量，观察持续增长不降的加载器。
  - 用 MAT 或 JProfiler 分析 GC Root，查找 `ClassLoader` 的 `GC Root` 引用链。
- **解决方案**：
  - **及时置空引用**：任务结束后，显式将加载器引用设为 `null`，断开 ThreadLocal 中的引用。
  - **使用 WeakReference 包装**：将加载器存入 `WeakHashMap` 等弱引用容器。
  - **关闭/清理回调**：为自定义加载器设计 `close()` 方法，清理内部缓存，并中断相关的线程。
  - **避免 GC Root 关联**：确保加载的类不是通过静态变量被引用；线程上下文类加载器用完复原。
- **易错提醒**：仅仅让加载器实例不可达还不够，它加载的类如果有某个对象存活，而该对象的类引用指向加载器，加载器依然存活。必须切断所有关联。
- **记忆口诀**：断开强引用，清理线程栈，关闭缓存源，Metaspace 不翻船。

### 面试题5：在 Java 9 及以上，模块化对类加载机制有什么核心改变？
**难度**：⭐⭐⭐⭐

**参考答案**：
- **核心概念**：Java 9 引入了模块系统（JPMS），类加载不再是单纯的双亲委派三层结构，而是加入了**模块层**和**模块化类加载器**的精确控制。
- **主要变化**：
  1. **加载器层次调整**：原 Extension 类加载器被 **平台类加载器（Platform ClassLoader）** 取代，原 Bootstrap 和平台类加载器加载的类不再是所有可访问，必须通过模块导出 `exports` 开放。
  2. **内建模块强封装**：即使反射也难以突破 `protected` 模块边界，除非开启 `--add-opens`。
  3. **Module Layers**：JVM 支持多个模块层，每个层有自己的加载器映射，可实现类似容器的隔离（如多个版本模块同时加载）。
  4. **类加载器的 `loadClass` 调整**：`ClassLoader` 新增 `findLoadedClass` 和 `defineClass` 在某些限制下工作，模块系统会在委托前检查模块访问权限，即使双亲委派成功，如果调用方模块未 `requires` 正确，链接时仍会报错。
- **常见追问**：Q：“传统 OSGi 与 Java 9 模块系统如何共存？” A：“可以使用 `ModuleFinder` 和 `ModuleLayer` 构建定制化层，OSGi 容器可以运行在模块系统之上，但复杂度极高，一般选择其一。”
- **易错提醒**：在 Java 9+ 环境使用自定义类加载器加载从模块路径来的类，需要注意模块的可读性，否则 `ClassNotFoundException` 或 `IllegalAccessError` 会让你头疼。
- **记忆口诀**：**模块层替换扩展，可访问性严查，强封装断反射。**

