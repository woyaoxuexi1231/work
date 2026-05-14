# Spring 45天学习总纲（Spring Boot 引导式）

## 45天知识图谱
| 天数 | 阶段 | 知识点主题 | 覆盖原理项 | 练习类型 | 完成情况 |
|------|------|------------|------------|----------|----------|
| 1 | 使用期 | Spring Boot 项目搭建与第一个接口 | 无 | 基础集成 | 100 |
| 2 | 使用期 | 依赖注入：@Component、@Autowired、构造器注入 | 无 | 基础查询 | 100 |
| 3 | 使用期 | 配置管理：@Value、@ConfigurationProperties、YAML | 无 | 基础集成 | 100 |
| 4 | 使用期 | Spring MVC 基础：@RestController、请求映射、参数接收 | 无 | 基础查询 | 100 |
| 5 | 使用期 | 请求参数校验：@Valid、自定义校验注解 | 无 | 基础查询 | 50 |
| 6 | 使用期 | 统一异常处理：@ControllerAdvice 与拦截器 | 无 | 基础集成 | 100 |
| 7 | 使用期 | Spring Data JPA 集成：实体定义与基础 CRUD | 无 | 基础集成 | 100 |
| 8 | 使用期 | Spring 声明式事务：@Transactional 基本用法 | 无 | 基础查询 | 100 |
| 9 | 使用期 | Spring Boot 集成 MyBatis（对比 JPA） | 无 | 基础集成 | 100 |
| 10 | 使用期 | AOP 入门：@Aspect 定义日志切面 | 无 | 基础查询 | 100 |
| 11 | 使用期 | Spring Security 基础：内存用户与角色认证 | 无 | 基础集成 |  |
| 12 | 使用期 | Actuator 监控端点与自定义健康检查 | 无 | 基础集成 |  |
| 13 | 使用期 | 定时任务：@Scheduled 与 @Async 异步方法 | 无 | 基础查询 |  |
| 14 | 使用期 | Spring Boot 测试：@SpringBootTest 与 MockMvc | 无 | 基础集成 |  |
| 15 | 使用期 | 综合练习：构建安全、事务、日志完备的 REST 应用 | 无 | 综合查询 |  |
| 16 | 原理期 | IoC 容器启动流程：BeanDefinition 到 ApplicationContext | P1 | 原理探究 |  |
| 17 | 原理期 | Bean 生命周期与 BeanPostProcessor 机制 | P1,P2 | 原理验证 |  |
| 18 | 原理期 | @Autowired 注入原理：AutowiredAnnotationBeanPostProcessor | P2 | 原理探究 |  |
| 19 | 原理期 | 循环依赖与三级缓存解决原理 | P3 | 原理验证 |  |
| 20 | 原理期 | Spring Boot 自动配置原理：@EnableAutoConfiguration 与 spring.factories | P4 | 原理探究 |  |
| 21 | 原理期 | 条件注解原理：@Conditional 系列及自动装配条件类 | P4 | 原理验证 |  |
| 22 | 原理期 | AOP 代理原理：JDK 动态代理 vs CGLIB，拦截链调用过程 | P5 | 原理验证+压测 |  |
| 23 | 原理期 | 事务原理：PlatformTransactionManager、AOP 拦截与传播行为 | P6 | 原理验证 |  |
| 24 | 原理期 | Spring MVC 请求处理流程：DispatcherServlet 核心组件协作 | P7 | 原理探究 |  |
| 25 | 原理期 | 参数解析与返回值处理：HandlerMethodArgumentResolver、HttpMessageConverter | P7,P8 | 原理验证 |  |
| 26 | 原理期 | SpringApplication 启动流程解析 | P1,P4 | 原理探究 |  |
| 27 | 原理期 | Spring Security 过滤器链与认证授权原理 | P9 | 原理探究 |  |
| 28 | 原理期 | Spring 事件机制：ApplicationEvent 与 ApplicationListener | P10 | 原理验证 |  |
| 29 | 原理期 | 缓存抽象原理：@Cacheable 与 CacheManager 实现 | P11 | 原理验证 |  |
| 30 | 原理期 | 综合原理压测：代理方式、事务传播、缓存命中性能对比 | P5,P6,P11 | 性能对比实验 |  |
| 31 | 大厂期 | 多模块项目划分与依赖管理最佳实践 | P1,P4 | 系统设计 |  |
| 32 | 大厂期 | 统一响应体与异常处理：全局返回码、业务异常设计 | P7,P8 | 系统设计 |  |
| 33 | 大厂期 | 接口幂等设计：Token 机制 + 自定义注解 + 拦截器 | P9 | 系统设计 |  |
| 34 | 大厂期 | 自定义 Spring Boot Starter：自动配置封装与发布 | P4 | 系统设计 |  |
| 35 | 大厂期 | 分布式 Session 管理：Spring Session + Redis | P11 | 系统设计 |  |
| 36 | 大厂期 | 微服务远程调用：Spring Cloud OpenFeign 集成与拦截器 | P7 | 系统设计 |  |
| 37 | 大厂期 | 全链路日志追踪：MDC + 拦截器实现 traceId 透传 | P7,P9 | 系统设计 |  |
| 38 | 大厂期 | 接口限流：基于拦截器 + 令牌桶算法 | P9 | 系统设计 |  |
| 39 | 大厂期 | 多数据源动态路由：AbstractRoutingDataSource 与应用切换 | P2,P6 | 系统设计 |  |
| 40 | 大厂期 | 优雅关闭与健康检查：graceful shutdown、readiness/liveness | P1 | 系统设计 |  |
| 41 | 大厂期 | 面试连环问：Spring IoC 容器启动与 Bean 加载全过程 | P1,P2,P3 | 综合论述 |  |
| 42 | 大厂期 | 面试连环问：Spring AOP 核心机制及失效场景 | P5 | 综合论述 |  |
| 43 | 大厂期 | 面试连环问：Spring 事务失效原因与解决方案 | P6 | 综合论述 |  |
| 44 | 大厂期 | 面试连环问：Spring Boot 自动配置原理、覆盖与排雷 | P4 | 综合论述 |  |
| 45 | 大厂期 | 毕业设计：构建企业级项目骨架（分层、多数据源、安全、监控） | P1-P11 | 系统设计 |  |

## 面试必考原理总清单
1. **P1: IoC 容器与 Bean 生命周期** – 第16、17、26、31、40、41天
2. **P2: 依赖注入与后置处理器** – 第17、18、39、41天
3. **P3: 循环依赖与三级缓存** – 第19、41天
4. **P4: Spring Boot 自动配置** – 第20、21、26、31、34、44天
5. **P5: AOP 动态代理与拦截链** – 第22、30、42天
6. **P6: Spring 事务原理与传播机制** – 第23、30、39、43天
7. **P7: Spring MVC 核心流程** – 第24、25、32、36、37天
8. **P8: 参数解析与消息转换器** – 第25、32天
9. **P9: Security 过滤器链** – 第27、33、37、38天
10. **P10: 事件机制** – 第28天
11. **P11: 缓存抽象** – 第29、30、35天

## 每日一句话概览
- 第1天：使用 Spring Initializr 创建工程，写出第一个 RESTful 接口，理解项目结构。
- 第2天：通过 @Component、@Service、@Repository 声明 Bean，使用 @Autowired 和构造器注入。
- 第3天：在 application.yml 中定义配置，并用 @Value 和 @ConfigurationProperties 进行绑定。
- 第4天：定义多个 REST 接口，学习 @GetMapping、@PostMapping、@RequestParam、@PathVariable。
- 第5天：集成 Hibernate Validator，对请求体使用 @Valid 校验，定义自定义校验注解。
- 第6天：用 @ControllerAdvice 处理全局异常，编写登录拦截器检查请求头 Token。
- 第7天：引入 Spring Data JPA，定义 @Entity，继承 JpaRepository 完成增删改查。
- 第8天：为 Service 层方法添加 @Transactional，体验事务回滚，理解默认传播行为。
- 第9天：集成 MyBatis-Plus，与 JPA 对比开发效率，理解 Spring 整合不同持久层框架的方式。
- 第10天：编写 @Aspect 切面类，对 Controller 层统一记录请求日志和执行时间。
- 第11天：引入 Spring Security，配置基于内存的用户、角色，实现登录与权限控制。
- 第12天：访问 /actuator 端点，自定义健康指示器，暴露系统运行时信息。
- 第13天：使用 @Scheduled 执行定时任务，@Async 实现方法异步执行，配置线程池。
- 第14天：编写 @SpringBootTest 集成测试，使用 MockMvc 模拟 HTTP 请求并断言响应。
- 第15天：从 Controller 到 DAO 搭建完整的 REST 应用，包含校验、异常处理、事务、AOP、Security。
- 第16天：调试 BeanDefinition 加载过程，追踪 ApplicationContext refresh() 中的关键步骤。
- 第17天：定义 BeanPostProcessor，观察初始化前后处理，理解 Aware 接口回调顺序。
- 第18天：跟踪 AutowiredAnnotationBeanPostProcessor，查看 @Autowired 字段何时被注入。
- 第19天：编写 A 依赖 B、B 依赖 A 的 Bean，断点三级缓存（singletonFactories、earlySingletonObjects）如何解决循环依赖。
- 第20天：阅读 @SpringBootApplication 和 spring.factories 文件，理解自动配置的加载入口。
- 第21天：自定义 @ConditionalOnMissingBean 条件类，控制自己 Starter 的装配时机。
- 第22天：用断点观察 JDK 动态代理和 CGLIB 代理对象的调用链，压测两种代理性能差异。
- 第23天：开启事务日志，分析不同 Propagation 设置下事务挂起、新建、嵌套的调用栈。
- 第24天：调试 DispatcherServlet，梳理 HandlerMapping、HandlerAdapter、ViewResolver 的执行顺序。
- 第25天：编写自定义 ArgumentResolver 解析加密请求参数，注册自定义 HttpMessageConverter。
- 第26天：阅读 SpringApplication.run() 源码，理解环境准备、上下文创建、刷新、监听器触发。
- 第27天：打印 Security 过滤器链，追踪 UsernamePasswordAuthenticationFilter 到最终授权的过程。
- 第28天：发布自定义 ApplicationEvent，使用 @EventListener 异步处理，理解事件广播原理。
- 第29天：开启 @EnableCaching，对比 Caffeine 和 Redis 缓存管理器实现，分析 CacheInterceptor 拦截逻辑。
- 第30天：对同一业务分别用 JDK 代理和 CGLIB 代理实现事务与缓存，压测 QPS 和内存占用。
- 第31天：将项目拆分为 common、service、web 多模块，管理统一依赖版本与可复用组件。
- 第32天：设计统一响应体 Result<T>，全局错误码枚举，让 @ControllerAdvice 统一包装返回。
- 第33天：用自定义注解 + 拦截器 + Redis 实现接口幂等，防止重复提交。
- 第34天：将日志切面封装成 autoconfigure 模块，发布为自定义 Starter，通过 @EnableXxx 引入。
- 第35天：集成 Spring Session + Redis，实现多服务实例间共享登录态。
- 第36天：用 OpenFeign 调用远程服务，编写请求拦截器传递认证 Token，实现降级 fallback。
- 第37天：利用 MDC 在拦截器中生成 traceId，并透传到 Feign 调用，实现全链路日志追踪。
- 第38天：基于拦截器实现接口级限流，用 Guava RateLimiter 或自定义令牌桶算法。
- 第39天：继承 AbstractRoutingDataSource，根据注解切换主从数据源，并保证事务内切换安全。
- 第40天：配置 graceful shutdown，自定义 HealthIndicator，结合 K8s 探针实现零宕机上线。
- 第41天：准备面试回答：描述 IoC 容器启动全流程，从 BeanDefinition 到单例池的完整链路。
- 第42天：准备面试回答：解释 AOP 实现原理，并说明 self-invocation、final 方法等失效场景。
- 第43天：准备面试回答：列举事务失效的常见原因（非 public、self 调用、异常类型不匹配等）。
- 第44天：准备面试回答：剖析自动配置如何判断生效条件，以及如何排除或覆盖默认配置。
- 第45天：毕业设计：设计一个可扩展的企业后端脚手架，包含分层架构、多数据源、安全、监控与链路追踪。