# Spring Framework 30天面试突击指南 (💎 纯粹源码与核心原理版)

> **总纲**：本指南剔除了 Spring Boot 和 Spring Cloud，深度聚焦于 **Spring Framework** 本身的核心机制。我们不仅要学习如何使用，更要从源码层面掌握其设计哲学。
> 
> **学习路径**：
> 1. **Week 1 (1-7天)**：IoC 容器底层、Bean 生命周期与核心扩展点。
> 2. **Week 2 (8-14天)**：AOP 代理机制、AspectJ 整合与资源/属性抽象。
> 3. **Week 3 (15-21天)**：JDBC 数据访问、声明式事务管理（底层原理）与验证绑定。
> 4. **Week 4 (22-26天)**：Spring MVC 体系结构、请求生命周期与异步处理机制。
> 5. **Week 5 (27-30天)**：设计模式精讲、测试框架、调度集成与性能调优。
> 
> **预期成果**：彻底掌握 Spring 核心源码逻辑，能够手撕 IoC 和 AOP 原理，在面试中展现出对 Spring 生态最底层的控制力。

---

## 📅 第一阶段：IoC 容器与 Bean 生命周期 (核心基石)

### 第1天：IoC 哲学与容器分层架构
#### ### 面试题
1. **基础**：什么是 IoC（控制反转）？它与 DI（依赖注入）的关系是什么？
2. **中级**：对比 `BeanFactory` 和 `ApplicationContext` 的继承体系与功能差异。
3. **高级**：详细描述 `BeanDefinition` 的核心元数据包含哪些内容？为什么需要它？
4. **源码**：`DefaultListableBeanFactory` 为什么被称为 Spring 的核心工厂实现？它实现了哪些关键接口？
5. **地狱级**：如果让你设计一个支持“懒加载”和“作用域自定义”的容器，你会如何设计 `BeanDefinition` 的存储结构？

#### ### 编程题
手动模拟实现一个极简的 `BeanFactory`：支持通过反射创建对象并实现简单的 `@Autowired` 字段注入。

---

### 第2天：Bean 的实例化与初始化深度解剖
#### ### 面试题
1. **基础**：Spring Bean 的生命周期中，“实例化”和“初始化”有什么区别？
2. **中级**：`Aware` 接口族（如 `BeanNameAware`, `BeanFactoryAware`）的调用时机是什么？
3. **高级**：详细拆解 `AbstractAutowireCapableBeanFactory.doCreateBean` 的执行流程。
4. **源码**：`BeanPostProcessor` 的两个方法 `postProcessBeforeInitialization` 和 `postProcessAfterInitialization` 之间发生了什么？
5. **地狱级**：在 `postProcessBeforeInstantiation`（注意是 Instantiation）返回一个非空对象会发生什么？这种机制通常用于什么场景？

#### ### 编程题
实现一个自定义的 `BeanPostProcessor`，用于统计应用中所有 Bean 的初始化耗时，并打印超过 100ms 的 Bean。

---

### 第3天：三级缓存与循环依赖之谜
#### ### 面试题
1. **基础**：什么是循环依赖？Spring 默认支持哪种类型的循环依赖（构造器还是 Setter）？
2. **中级**：三级缓存分别存的是什么？为什么不能只有一级缓存？
3. **高级**：为什么二级缓存解决不了涉及 AOP 代理的循环依赖？
4. **源码**：详细描述 `getSingleton` 方法从三级缓存中获取 Bean 的“升级”逻辑。
5. **地狱级**：如果在循环依赖中，A 被 B 依赖，而 A 又有 `@Async` 注解生成的代理，Spring 会报错吗？为什么？

#### ### 编程题
编写代码复现“多实例 (Prototype) Bean 循环依赖导致异常”的场景，并解释为什么 Spring 不解决多实例的循环依赖。

---

### 第4天：FactoryBean 与核心扩展点
#### ### 面试题
1. **基础**：`BeanFactory` 和 `FactoryBean` 有什么区别？
2. **中级**：如何通过 `&` 符号获取 `FactoryBean` 本身而不是它生成的对象？
3. **高级**：`BeanFactoryPostProcessor` 和 `BeanPostProcessor` 的区别和执行顺序。
4. **源码**：`ConfigurationClassPostProcessor` 是如何处理 `@Configuration` 和 `@Bean` 注解的？
5. **地狱级**：如何利用 `FactoryBean` 优雅地整合一个非 Spring 管理的第三方连接池（如自研 RPC 客户端）？

#### ### 编程题
实现一个自定义 `FactoryBean`，它可以根据配置文件动态决定生成 `AImpl` 还是 `BImpl` 实例。

---

### 第5天：Spring 作用域与自定义 Scope
#### ### 面试题
1. **基础**：Spring 内置了哪些 Bean 作用域 (Scope)？
2. **中级**：`request` 和 `session` 作用域在非 Web 环境下能使用吗？为什么？
3. **高级**：当单例 Bean 注入一个原型 (Prototype) Bean 时，原型 Bean 的“原型性”会失效吗？如何解决？
4. **源码**：`ScopedProxyFactoryBean` 的实现原理是什么？
5. **地狱级**：手动实现一个 `ThreadScope`，确保在同一个线程内获取的是同一个 Bean 实例，跨线程则是不同实例。

#### ### 编程题
使用 `@Lookup` 注解或 `ObjectFactory` 解决“单例注入原型 Bean 失效”的问题。

---

### 第6天：容器刷新流程 (refresh) 深度拆解
#### ### 面试题
1. **基础**：`ApplicationContext.refresh()` 方法的主要作用是什么？
2. **中级**：简述 `prepareRefresh` 和 `obtainFreshBeanFactory` 阶段做了什么。
3. **高级**：`invokeBeanFactoryPostProcessors` 阶段是如何保证顺序性的（PriorityOrdered/Ordered）？
4. **源码**：详细拆解 `finishBeanFactoryInitialization` 阶段，单例 Bean 是如何被预初始化的？
5. **地狱级**：如果在 `onRefresh` 阶段抛出异常，Spring 的 `destroyBeans` 回滚机制是如何执行的？

#### ### 编程题
利用 `ApplicationListener<ContextRefreshedEvent>` 实现一个容器启动后的热点数据预加载逻辑。

---

### 第7天：第一周复盘：Spring IoC 设计模式与架构权衡
#### ### 面试题
1. **综合**：Spring 容器如何体现“开闭原则”？
2. **架构**：对比 Spring 的构造器注入与 Setter 注入，为什么 Spring 团队现在更推荐构造器注入？
3. **深度**：如何理解 Spring 中的“元编程”思想（通过注解和反射动态改变程序行为）？
4. **地狱级**：假设要完全脱离反射，利用 Java 17 的 `Sealed Classes` 和代码生成重写 Spring IoC，你会如何设计 Bean 的注入链路？

#### ### 编程题
手撕一个简单的责任链模式，模拟 Spring 容器中多个 `BeanFactoryPostProcessor` 的有序调用逻辑。

---

## 📅 第二阶段：AOP 代理机制与资源抽象 (能力跃迁)

### 第8天：AOP 核心概念与代理选择逻辑
#### ### 面试题
1. **基础**：什么是连接点 (Joinpoint)、切点 (Pointcut) 和增强 (Advice)？
2. **中级**：JDK 动态代理要求目标类必须实现接口，CGLIB 则不需要，底层原因是什么？
3. **高级**：`ProxyFactory` 是如何根据 `isProxyTargetClass` 等参数决定使用哪种代理方式的？
4. **源码**：`AopProxy` 接口有哪些实现？它们是如何包装目标对象的？
5. **地狱级**：在多级代理（如同时有事务代理和日志代理）的情况下，Spring 是如何保证切面执行顺序的？

#### ### 编程题
手写一个基于 JDK `Proxy` 的动态代理，实现对所有方法执行时间的拦截打印。

---

### 第9天：CGLIB 深度原理与 FastClass 机制
#### ### 面试题
1. **基础**：为什么 CGLIB 无法代理 `final` 类或 `final` 方法？
2. **中级**：CGLIB 相比 JDK 动态代理，在性能上有哪些优势和劣势？
3. **高级**：什么是 `MethodProxy`？它与 Java 原生 `Method` 有什么区别？
4. **源码**：详细描述 FastClass 机制是如何通过索引定位方法并避免反射调用的。
5. **地狱级**：如何解决 CGLIB 代理类导致的 `Method.invoke` 死亡递归问题？

#### ### 编程题
利用 CGLIB 的 `Enhancer` 手动创建一个代理对象，并演示如何拦截 `toString()` 方法。

---

### 第10天：AspectJ 整合与切点表达式
#### ### 面试题
1. **基础**：Spring AOP 与 AspectJ 的关系是什么？Spring 是如何使用 AspectJ 的切点表达式的？
2. **中级**：`execution`, `within`, `this`, `target`, `@annotation` 等切点指示符的区别。
3. **高级**：什么是“引介增强” (Introduction Advice)？如何通过 AOP 给一个现有类动态添加新接口？
4. **源码**：`AspectJExpressionPointcut` 是如何解析并匹配字符串形式的表达式的？
5. **地狱级**：如何实现一个自定义的切点指示符（如 `@MyLog`），使其支持更复杂的业务逻辑匹配？

#### ### 编程题
编写一个 AOP 切面，使用 `@Around` 通知拦截带有自定义注解 `@Retry` 的方法，并实现失败重试逻辑。

---

### 第11天：Spring AOP 代理失效与自我调用
#### ### 面试题
1. **基础**：为什么在类内部调用 `this.method2()` 会导致 `method2` 的 AOP 拦截失效？
2. **中级**：解决 AOP 自我调用失效的几种方案（`AopContext.currentProxy()`, 注入自己等）。
3. **高级**：`ExposeProxy` 属性的作用及其对性能的影响。
4. **源码**：`JdkDynamicAopProxy` 的 `invoke` 方法中是如何获取当前代理对象的？
5. **地狱级**：在非 Spring 管理的线程（如手动 `new Thread`）中调用代理对象的方法，`AopContext` 还能获取到代理吗？为什么？

#### ### 编程题
演示三种解决 AOP 自我调用失效的方法，并对比其优缺点。

---

### 第12天：Spring 资源加载 (Resource) 与属性源
#### ### 面试题
1. **基础**：`Resource` 接口抽象了哪些物理资源类型？
2. **中级**：`ResourcePatternResolver` 是如何处理 `classpath*:*` 这种通配符加载的？
3. **高级**：`Environment` 接口如何整合 `SystemProperties` 和 `SystemEnvironment`？
4. **源码**：`PropertySource` 的优先级顺序是如何在 `MutablePropertySources` 中维护的？
5. **地狱级**：如何实现一个从远程 Http 接口动态读取配置并注入到 `@Value` 的自定义 `PropertySource`？

#### ### 编程题
实现一个自定义 `Resource`，用于读取存储在数据库中的配置文件。

---

### 第13天：Spring 验证、类型转换与格式化
#### ### 面试题
1. **基础**：`Converter` 和 `PropertyEditor` 有什么区别？为什么 Spring 后来更推荐 `Converter`？
2. **中级**：`GenericConversionService` 是如何进行复杂泛型转换的？
3. **高级**：`DataBinder` 在数据绑定过程中的核心作用。
4. **源码**：`DefaultFormattingConversionService` 是如何整合格式化逻辑（如日期格式化）的？
5. **地狱级**：在高并发场景下，类型转换器如果是单例的，如何保证线程安全？

#### ### 编程题
实现一个自定义 `Converter`，将字符串 `ID:Name:Age` 格式自动转换为 `User` 实体类。

---

### 第14天：第二周复盘：Spring AOP 设计模式与源码深度总结
#### ### 面试题
1. **综合**：Spring AOP 中使用了哪些设计模式？（代理模式、观察者模式、适配器模式等）。
2. **对比**：Spring AOP 的动态代理与 AspectJ 的静态织入 (Weaving) 在原理和性能上的巨大差异。
3. **地狱级**：如果让你重写 Spring AOP，你会如何利用 Java 9+ 的 `VarHandle` 或 `MethodHandle` 提升拦截性能？

#### ### 编程题
手写一个简化版的 `AspectJAutoProxyCreator`，模拟其扫描所有切面并为 Bean 生成代理的逻辑。

---

## 📅 第三阶段：数据访问与事务管理 (实战深度)

### 第15天：Spring JDBC 与资源抽象
#### ### 面试题
1. **基础**：`JdbcTemplate` 相比原生 JDBC 解决了哪些痛点？
2. **中级**：Spring 是如何管理 `DataSource` 连接的？`DataSourceUtils` 的作用。
3. **高级**：什么是 `SQLExceptionTranslator`？它是如何将供应商特定的错误码转换为 Spring 通用异常的？
4. **源码**：`JdbcTemplate` 是如何保证在同一个事务中获取同一个 Connection 的？
5. **地狱级**：在高并发下，如何监控 `JdbcTemplate` 的执行慢 SQL 并自动记录堆栈信息？

#### ### 编程题
利用 `JdbcTemplate` 实现一个支持“自动重试”的数据库查询工具。

---

### 第16天：Spring 声明式事务底层架构
#### ### 面试题
1. **基础**：`@Transactional` 的实现原理是什么？
2. **中级**：Spring 事务抽象层中的三个核心接口：`PlatformTransactionManager`, `TransactionDefinition`, `TransactionStatus`。
3. **高级**：为什么声明式事务必须基于 AOP？如果类没有被 AOP 代理，事务还会生效吗？
4. **源码**：`TransactionInterceptor` 的核心逻辑拆解。
5. **地狱级**：事务拦截器在执行链中的顺序应该是最高的还是最低的？为什么？

#### ### 编程题
模拟实现一个简易的 `@MyTransactional` 注解，利用 AOP 手动控制 `connection.commit()` 和 `rollback()`。

---

### 第17天：事务传播行为 (Propagation) 深度解析
#### ### 面试题
1. **基础**：`REQUIRED` 和 `REQUIRES_NEW` 的本质区别。
2. **中级**：什么是 `NESTED` 嵌套事务？它与 `REQUIRES_NEW` 的区别在哪？
3. **高级**：`SUPPORTS`, `NOT_SUPPORTED`, `NEVER` 的应用场景。
4. **源码**：`AbstractPlatformTransactionManager.getTransaction` 是如何根据传播行为处理已存在事务的？
5. **地狱级**：在 `REQUIRED` 模式下，子方法抛异常被捕获了，主方法继续执行，为什么最后会报 `Transaction rolled back because it has been marked as rollback-only`？

#### ### 编程题
编写代码复现 `NESTED` 事务的特性：子事务回滚不影响主事务，主事务回滚强制回滚子事务。

---

### 第18天：事务隔离级别与数据库并发
#### ### 面试题
1. **基础**：Spring 支持哪五种隔离级别？`DEFAULT` 代表什么？
2. **中级**：数据库的隔离级别与 Spring 的隔离级别是如何对应的？
3. **高级**：脏读、不可重复读、幻读在 Spring 事务中是如何通过配置规避的？
4. **源码**：Spring 是如何将隔离级别参数传递给底层的 JDBC `Connection` 的？
5. **地狱级**：如果数据库不支持某个隔离级别，Spring 会报错还是降级？为什么？

#### ### 编程题
模拟在高并发下使用 `SERIALIZABLE` 隔离级别，观察并分析其对系统吞吐量的影响。

---

### 第19天：编程式事务与多事务管理器
#### ### 面试题
1. **基础**：什么时候该用 `TransactionTemplate` 而不是 `@Transactional`？
2. **中级**：如何在应用中配置并使用多个 `PlatformTransactionManager`？
3. **高级**：`@Transactional("tm1")` 指定事务管理器的底层查找逻辑。
4. **源码**：`TransactionTemplate` 是如何利用回调机制简化事务代码的？
5. **地狱级**：在分布式场景下，虽然 Spring 不直接提供分布式事务，但如何利用 `ChainedTransactionManager`（已废弃但有参考价值）处理伪分布式事务？

#### ### 编程题
使用 `TransactionTemplate` 实现一个复杂的业务逻辑：在同一个方法内，先执行一段不带事务的逻辑，再执行一段带事务的逻辑。

---

### 第20天：Spring 事务失效全场景复盘
#### ### 面试题
1. **综合**：总结至少 8 种会导致 `@Transactional` 失效的情况。
2. **中级**：为什么 `private` 方法加 `@Transactional` 会失效？
3. **高级**：`rollbackFor` 默认只回滚哪些异常？为什么这么设计？
4. **源码**：`TransactionAttributeSource` 是如何判断一个方法是否应该开启事务的？
5. **地狱级**：多线程环境下，父线程的事务能传播到子线程吗？如果不能，如何实现跨线程的事务一致性？

#### ### 编程题
演示并修复三种由于 AOP 代理机制导致的事务失效场景。

---

### 第21天：第三周复盘：Spring 数据层架构与异常体系
#### ### 面试题
1. **综合**：Spring 的 `DataAccessException` 体系为什么采用非检查异常 (Unchecked Exception)？
2. **架构**：谈谈对 Spring “资源同步器” (`TransactionSynchronizationManager`) 的理解。
3. **地狱级**：在高并发写场景下，如何利用 Spring 事务的 `ReadOnly` 优化数据库性能？底层做了什么？

#### ### 编程题
实现一个自定义的 `TransactionSynchronization`，在事务提交成功后异步发送一条 MQ 消息。

---

## 📅 第四阶段：Spring MVC 体系结构 (Web 进阶)

### 第22天：DispatcherServlet 启动与请求分发
#### ### 面试题
1. **基础**：`DispatcherServlet` 在 Web 容器中是如何初始化的？
2. **中级**：描述一个 HTTP 请求进入 Spring MVC 后的 9 大核心组件调用链路。
3. **高级**：`HandlerMapping` 是如何根据 URL 找到对应的 `Handler` 的？
4. **源码**：`initStrategies` 方法是如何加载默认组件配置的？
5. **地狱级**：如果在 Spring MVC 中配置了多个 `DispatcherServlet`，它们之间的容器父子关系是怎样的？

#### ### 编程题
手写一个极简版的 `DispatcherServlet`，实现基础的 `@RequestMapping` 路由分发。

---

### 第23天：参数绑定与消息转换器 (HttpMessageConverter)
#### ### 面试题
1. **基础**：`@RequestBody` 和 `@RequestParam` 的底层处理逻辑有什么不同？
2. **中级**：什么是 `HttpMessageConverter`？常用的有哪些？
3. **高级**：Spring MVC 是如何根据 `Accept` 头决定返回 JSON 还是 XML 的？（内容协商机制）。
4. **源码**：`HandlerMethodArgumentResolver` 是如何扩展自定义参数解析逻辑的？
5. **地狱级**：如何自定义一个消息转换器，实现对所有 JSON 输出的敏感字段脱敏？

#### ### 编程题
实现一个自定义 `HandlerMethodArgumentResolver`，将请求头中的 `X-User-ID` 自动注入到 Controller 方法参数 `UserContext` 中。

---

### 第24天：视图解析与拦截器链 (Interceptor)
#### ### 面试题
1. **基础**：`HandlerInterceptor` 的三个方法 `preHandle`, `postHandle`, `afterCompletion` 的执行时机。
2. **中级**：拦截器 (Interceptor) 和过滤器 (Filter) 的本质区别是什么？
3. **高级**：`ViewResolver` 是如何根据逻辑视图名找到物理视图文件的？
4. **源码**：多个拦截器的执行顺序是如何保证的？如果其中一个返回 `false`，后续流程如何执行？
5. **地狱级**：在 `postHandle` 中修改 `ModelAndView` 对结果有什么影响？如果在 `afterCompletion` 中抛异常会怎样？

#### ### 编程题
实现一个防重提交拦截器，利用 Redis（或本地 Map 模拟）防止同一个请求在 1 秒内重复提交。

---

### 第25天：异常处理与控制器增强 (@ControllerAdvice)
#### ### 面试题
1. **基础**：`@ExceptionHandler` 如何实现统一异常处理？
2. **中级**：`HandlerExceptionResolver` 的执行顺序及默认实现。
3. **高级**：`@ControllerAdvice` 的工作原理及其对全局数据的绑定支持。
4. **源码**：`ExceptionHandlerExceptionResolver` 是如何匹配最合适的异常处理方法的？
5. **地狱级**：如果 `@ExceptionHandler` 方法本身又抛出了异常，Spring MVC 会如何处理？

#### ### 编程题
实现一个全局异常处理器，要求根据异常类型返回不同的 HTTP 状态码，并记录详细的错误日志。

---

### 第26天：Spring MVC 异步请求处理
#### ### 面试题
1. **基础**：为什么要用异步 Servlet (Servlet 3.0+)？Spring MVC 如何支持异步调用？
2. **中级**：`Callable` 和 `DeferredResult` 的区别及应用场景。
3. **高级**：异步请求处理时，拦截器的执行逻辑会有什么变化？
4. **源码**：`WebAsyncManager` 是如何管理异步任务状态和超时处理的？
5. **地狱级**：在高并发异步请求下，如何防止线程池被打满导致系统崩溃？

#### ### 编程题
使用 `DeferredResult` 实现一个简易的“长轮询”聊天室 demo。

---

## 📅 第五阶段：集成扩展与性能调优 (架构之巅)

### 第27天：Spring 缓存抽象与集成
#### ### 面试题
1. **基础**：`@Cacheable`, `@CacheEvict`, `@CachePut` 的用法。
2. **中级**：Spring 缓存抽象是如何解决缓存穿透、缓存雪崩的？（它其实不解决，需要手动配置）。
3. **高级**：`CacheManager` 和 `Cache` 接口的设计初衷。
4. **源码**：`CacheInterceptor` 的核心拦截逻辑。
5. **地狱级**：如何在分布式环境下保证 Spring 本地缓存与 Redis 远程缓存的一致性？

#### ### 编程题
实现一个自定义 `KeyGenerator`，根据方法参数的特定字段生成缓存 Key。

---

### 第28天：Spring 任务调度与异步执行
#### ### 面试题
1. **基础**：`@Scheduled` 和 `@Async` 的基本用法。
2. **中级**：Spring 默认使用的任务执行器和调度器是什么？为什么推荐自定义线程池？
3. **高级**：`CronExpression` 的解析逻辑及其与 Quartz 的区别。
4. **源码**：`TaskExecutor` 体系结构。
5. **地狱级**：如果在 `@Scheduled` 方法中抛出异常，后续调度还会继续吗？如何实现可靠的定时任务？

#### ### 编程题
配置一个自定义线程池，并演示如何通过 `@Async` 获取异步执行的结果（使用 `CompletableFuture`）。

---

### 第29天：Spring 测试框架与设计模式总结
#### ### 面试题
1. **基础**：`@ContextConfiguration` 和 `@MockBean` 的作用。
2. **中级**：Spring TestContext Framework 是如何实现容器缓存的？（加速测试运行）。
3. **高级**：总结 Spring 框架中至少 10 种设计模式的应用位置。
4. **源码**：`SpringRunner` (JUnit 4) 或 `SpringExtension` (JUnit 5) 的底层原理。
5. **地狱级**：如何实现一个不依赖 Web 容器的 Spring MVC 单元测试（使用 `MockMvc`）？

#### ### 编程题
编写一个集成测试用例，使用 `EmbeddedDatabaseBuilder` 模拟数据库环境，并验证事务回滚逻辑。

---

### 第30天：Spring 性能调优与架构演进
#### ### 面试题
1. **综合**：Spring 应用启动缓慢的常见原因及优化方案。
2. **中级**：谈谈 Spring 5 引入的 `Index` 文件（`spring.components`）如何提升扫描速度。
3. **高级**：Spring 框架在云原生背景下的演进：Spring Native 与 GraalVM 静态编译。
4. **地狱级**：如果你要设计下一代轻量级 IoC 容器，你会舍弃 Spring 的哪些繁重特性？为什么？

#### ### 终极面试
1. 准备一段 5 分钟的 Spring 核心原理演讲稿，涵盖 IoC、AOP 和声明式事务。
2. 模拟回答：当面试官问“你对 Spring 源码最深刻的理解是什么”时，你的切入点在哪里？

---

**总教官寄语**：恭喜你完成了这场 30 天的 Spring 苦修。记住，源码不是用来背的，是用来感受其设计之美的。愿你在面试场上游刃有余，在架构路上砥砺前行！
