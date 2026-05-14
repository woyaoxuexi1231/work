# Java 日期/异常/反射/泛型 核心面试原理清单

## 日期与时间（Java 8+）

1. **P1: LocalDate/ LocalTime/ LocalDateTime 不可变设计** – 所有调整操作返回新对象，原对象不变；线程安全，区别于可变且线程不安全的 Date/Calendar。

2. **P2: Instant 与时间戳** – 基于 Unix 纪元（1970-01-01 00:00:00 UTC）的纳秒级时间点，用于机器时间记录，与人类时间表示（LocalDateTime）分离。

3. **P3: ZoneId 与 ZoneOffset 时区处理** – ZoneOffset 为固定偏移（如 +08:00），ZoneId 包含夏令时规则（如 Asia/Shanghai）；ZonedDateTime 与 OffsetDateTime 分别对应。

4. **P4: 日期解析与格式化线程安全** – DateTimeFormatter 不可变且线程安全，而 SimpleDateFormat 非线程安全（内部 Calendar 共享状态）；格式化器可预定义为静态常量。

5. **P5: 时间跨度表示 Period/ Duration 与时间调整器** – Period 基于日期（年/月/日），Duration 基于时间（秒/纳秒）；TemporalAdjuster 实现复杂日期运算（如下一个工作日）。

## 异常处理

6. **P6: 受检异常与非受检异常设计哲学** – 受检异常（Exception 除 RuntimeException）强制处理或声明，用于可恢复场景（如 IOException）；非受检异常（RuntimeException）用于编程错误（如 NullPointerException）。

7. **P7: try-with-resources 与 AutoCloseable** – 自动关闭资源，编译期生成 suppress 异常代码（addSuppressed）；关闭异常不覆盖主异常，且遵循后声明先关闭原则。

8. **P8: 异常链与 cause 传播** – Throwable 构造器传入 cause，保留原始异常栈；initCause 方法可后置设置，避免异常信息丢失。

9. **P9: 异常性能开销根源** – fillInStackTrace 填充栈轨迹（遍历调用栈，消耗内存与 CPU）；频繁创建异常应使用预先创建的静态异常（需覆盖 fillInStackTrace）或复用异常对象。

10. **P10: finally 块与 return 冲突** – finally 中 return 会覆盖 try/catch 中的 return 或异常；finally 块不应抛出异常（会屏蔽原始异常），应记录日志。

## 反射机制

11. **P11: Class 对象获取方式与类加载** – 类字面量（String.class）、实例 getClass()、Class.forName()（触发静态初始化）；forName 可指定类加载器控制加载过程。

12. **P12: Method.invoke 反射调用开销与 NativeAccessor** – 首次调用通过 NativeMethodAccessorImpl（JNI），调用次数超过阈值（15 次）后生成 GeneratedMethodAccessor 字节码版本（动态生成），消除 JNI 边界开销。

13. **P13: setAccessible 打破访问控制的原理** – 修改 Field/Method/Constructor 的 accessible 标志位，抑制 Java 语言访问检查（public/protected/private），但受 module 系统限制（未 open 包不可访问）。

14. **P14: 反射与泛型擦除的获取** – 通过 Method.getGenericParameterTypes 可获取泛型签名（如 List<String>），而 getParameterTypes 返回原始类型（List）；利用此特性实现泛型类型传递（如 TypeReference）。

15. **P15: 动态代理与代理类的字节码生成** – JDK 动态代理要求目标类实现接口，运行时生成 $Proxy0 类（使用 ProxyGenerator 生成字节码）；CGLIB 基于子类继承（ASM 字节码），无法代理 final 类或方法。

## 泛型

16. **P16: 类型擦除与原始类型** – 编译后泛型类型参数被移除，替换为上限（Object 或指定边界）；泛型类型信息仅保留在 Signature 属性（供反射读取）。

17. **P17: 桥接方法保持多态** – 子类重写父类泛型方法（如 Comparable.compareTo(T)）后，编译器生成桥接方法（参数为 Object）并调用实际方法，维持二进制兼容性。

18. **P18: 通配符上界（extends）与下界（super）的 PECS 原则** – Producer Extends（读取），Consumer Super（写入）；? extends T 无法添加（除 null），? super T 可添加 T 及其子类型但读取仅能返回 Object。

19. **P19: 泛型方法与类型推导** – 类型参数在方法返回值或参数上声明，调用时编译器根据传入实参推导类型；若推导失败可显式指定（Collections.<String>emptyList()）。

20. **P20: 不可具体化类型与堆污染** – 运行时无法获知泛型具体类型（如 T、List<String>）为不可具体化；堆污染指带泛型的变量指向不兼容原始类型（如 List 赋给 List<String>），可能引发 ClassCastException。