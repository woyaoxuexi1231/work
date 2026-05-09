# Spring Boot 30天面试突击指南 (🚀 自动化与微服务基石版)

> **总纲**：本指南深度聚焦于 **Spring Boot** 的自动化装配、内嵌容器、监控管理以及生产级调优。我们将从最基础的 Starter 使用，一路攻克到最硬核的自动配置源码和启动生命周期。
> 
> **学习路径**：
> 1. **Week 1 (1-7天)**：核心配置、外部化管理与 Profile 环境隔离。
> 2. **Week 2 (8-14天)**：自动装配原理、`spring.factories` 机制与自定义 Starter。
> 3. **Week 3 (15-21天)**：内嵌容器 (Tomcat/Netty)、Web 增强、日志与 Actuator 监控。
> 4. **Week 4 (22-26天)**：启动流程全解析、生命周期扩展点与性能调优。
> 5. **Week 5 (27-30天)**：生产级排障、Spring Native 静态编译与面试综合实战。
> 
> **预期成果**：彻底掌握 Spring Boot “开箱即用”背后的魔法，能够熟练扩展自动配置，并具备解决生产环境下 Spring Boot 应用各类疑难杂症的能力。

---

## 📅 第一阶段：核心配置与外部化管理 (打牢基础)

### 第1天：Spring Boot 设计哲学与核心优势
#### ### 面试题
1. **基础**：Spring Boot 的核心设计思想是什么？它与传统 Spring 相比解决了哪些痛点？
2. **中级**：什么是“约定优于配置” (Convention over Configuration)？请举例说明。
3. **高级**：Spring Boot 为什么能做到“开箱即用”？它的背后有哪些核心组件支持？
4. **源码**：`SpringApplication.run()` 方法在启动时，是如何识别并加载默认配置的？
5. **地狱级**：如果要在不改变 Spring Boot 源码的情况下，修改其默认的“约定”（例如默认扫描包路径），你会采取什么方案？

#### ### 编程题
创建一个最简单的 Spring Boot 应用，并在启动时通过代码逻辑打印出当前应用中所有已加载的 `Bean` 名称。

---

### 第2天：依赖管理与 Starter 机制
#### ### 面试题
1. **基础**：`spring-boot-starter-parent` 的作用是什么？它解决了什么问题？
2. **中级**：如果不使用 `parent` 继承，如何通过 `dependencyManagement` 引入 Spring Boot 依赖？
3. **高级**：常用的 Starter 有哪些？它们的内部结构通常包含哪些部分？
4. **源码**：为什么引入一个 `spring-boot-starter-web` 就能自动引入 Tomcat 和 Spring MVC？
5. **地狱级**：在复杂的企业级项目中，如果两个 Starter 引入了冲突的依赖版本，Spring Boot 是如何处理的？你该如何手动干预？

#### ### 编程题
通过 Maven/Gradle 依赖分析工具，找出 `spring-boot-starter-web` 中所有包含的第三方类库，并编写一个测试类验证其中某个库（如 Jackson）是否已自动生效。

---

### 第3天：配置文件加载全链路 (yml vs properties)
#### ### 面试题
1. **基础**：`application.properties` 和 `application.yml` 哪个优先级更高？
2. **中级**：Spring Boot 支持哪些格式的配置文件？它们在加载时是如何排序的？
3. **高级**：如何实现在同一个 yml 文件中定义多个环境的配置（使用 `---` 分隔符）？
4. **源码**：`ConfigFileApplicationListener` 是如何寻找并解析这些配置文件的？
5. **地狱级**：如果要在应用启动前动态修改 `application.properties` 中的某个值（不修改文件本身），有哪些扩展点可以做到？

#### ### 编程题
编写代码证明：命令行参数 (`--server.port=9000`) 的优先级高于配置文件中的设置。

---

### 第4天：外部化配置与属性绑定高级技巧
#### ### 面试题
1. **基础**：`@Value` 注解和 `@ConfigurationProperties` 的主要区别是什么？
2. **中级**：什么是“松散绑定” (Relaxed Binding)？`first-name` 能绑定到 `firstName` 吗？
3. **高级**：如何对 `@ConfigurationProperties` 标注的属性进行校验 (JSR-303)？
4. **源码**：`Binder` 类在属性绑定过程中是如何处理复杂类型（如 List, Map）的？
5. **地狱级**：如何实现配置文件的加解密？即 yml 中存的是加密串，代码中注入的是解密后的明文。

#### ### 编程题
实现一个自定义的 `PropertySource`，从环境变量中读取以 `MYAPP_` 开头的属性，并将其注入到一个配置类中。

---

### 第5天：Profile 环境隔离与多环境切换
#### ### 面试题
1. **基础**：如何通过命令行指定运行环境？（`--spring.profiles.active`）。
2. **中级**：`@Profile` 注解可以标注在哪些地方？它的工作原理是什么？
3. **高级**：如何实现多环境配置文件的嵌套加载？（如 `application-dev.yml` 包含 `application-common.yml`）。
4. **源码**：`StandardEnvironment` 是如何处理活动 Profile 的合并逻辑的？
5. **地狱级**：在生产环境下，如果需要动态切换 Profile 而不重启应用，你有哪些可行性方案？

#### ### 编程题
配置三个环境：`dev`, `test`, `prod`，并在启动时根据不同的 Profile 注入不同的 `DataSource` 模拟实现类。

---

### 第6天：YAML 解析底层与多文档支持
#### ### 面试题
1. **基础**：YAML 相比 Properties 有哪些语法上的优势？
2. **中级**：Spring Boot 是使用哪个底层库来解析 YAML 的？（SnakeYAML）。
3. **高级**：如何自定义 YAML 的解析逻辑，使其支持自定义的数据类型转换？
4. **源码**：`YamlPropertySourceLoader` 的具体实现逻辑。
5. **地狱级**：如果 YAML 文件中存在循环引用（`*` 和 `&` 锚点），Spring Boot 的解析器会如何处理？会有安全隐患吗？

#### ### 编程题
实现一个自定义的 `YamlProcessor`，将 YAML 中的嵌套结构扁平化为 Map 形式。

---

### 第7天：第一周复盘：Spring Boot 启动初探与配置架构总结
#### ### 面试题
1. **综合**：总结 Spring Boot 配置加载的 17 个优先级顺序。
2. **架构**：为什么 Spring Boot 坚持使用“外部化配置”而不是硬编码在代码中？
3. **对比**：对比 Spring Boot 配置与 Spring Framework 配置在设计上的差异。
4. **地狱级**：如果让你重写 Spring Boot 的配置模块，你会如何支持“配置实时刷新”且不影响性能？

#### ### 编程题
手写一个简单的“配置覆盖”逻辑，模拟 Spring Boot 中命令行参数 > 系统变量 > 文件的优先级规则。

---

## 📅 第二阶段：自动装配原理与扩展 (核心灵魂)

### 第8天：@SpringBootApplication 注解深挖
#### ### 面试题
1. **基础**：`@SpringBootApplication` 是一个复合注解，它包含哪三个核心注解？
2. **中级**：`@EnableAutoConfiguration` 的作用是什么？
3. **高级**：`@ComponentScan` 在 Spring Boot 中默认的扫描范围是什么？如何修改？
4. **源码**：`AutoConfigurationImportSelector` 是如何被触发并执行的？
5. **地狱级**：如果我想完全禁用自动装配，只使用手动配置，应该如何操作？会对应用产生什么影响？

#### ### 编程题
自定义一个注解 `@EnableMyFeature`，模仿 `@EnableAutoConfiguration` 的模式，实现自定义功能的开启。

---

### 第9天：自动配置原理：从 spring.factories 说起
#### ### 面试题
1. **基础**：`META-INF/spring.factories` 文件在 Spring Boot 中扮演什么角色？
2. **中级**：Spring Boot 3.x 之后引入了 `spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 文件，它与 `spring.factories` 有什么区别？
3. **高级**：如何排除掉某个不需要的自动配置类？（通过注解参数或配置文件）。
4. **源码**：`SpringFactoriesLoader` 是如何加载这些工厂类的？
5. **地狱级**：如果两个不同的 jar 包中都定义了同一个 Key 的 `spring.factories`，Spring Boot 是如何合并这些配置的？顺序如何保证？

#### ### 编程题
在本地项目中创建一个 `META-INF/spring.factories`，并注册一个自定义的初始化器，观察其是否在启动时执行。

---

### 第10天：条件注解家族 (@Conditional 系列)
#### ### 面试题
1. **基础**：常用的条件注解有哪些？（`@ConditionalOnClass`, `@ConditionalOnBean`, `@ConditionalOnProperty` 等）。
2. **中级**：`@ConditionalOnMissingBean` 为什么在编写自动配置时非常重要？
3. **高级**：`AnyNestedCondition` 和 `AllNestedConditions` 有什么用？
4. **源码**：`OnBeanCondition` 底层是如何判断容器中是否存在某个 Bean 的？（涉及扫描顺序问题）。
5. **地狱级**：如何自定义一个复杂的条件注解？例如：只有当当前操作系统是 Linux 且存在某个环境变量时才生效。

#### ### 编程题
实现一个自定义 `Condition`，只有在类路径下存在 `com.mysql.cj.jdbc.Driver` 时，才加载自定义的数据库工具类。

---

### 第11天：自定义 Starter 开发实战
#### ### 面试题
1. **基础**：开发一个自定义 Starter 需要哪几个核心步骤？
2. **中级**：命名规范：为什么自定义 Starter 建议命名为 `xxx-spring-boot-starter` 而不是 `spring-boot-starter-xxx`？
3. **高级**：如何为 Starter 提供完善的配置元数据（IDE 自动补全）？
4. **源码**：`ConfigurationPropertiesBindingPostProcessor` 是如何处理 Starter 中的配置属性的？
5. **地狱级**：如果你的 Starter 依赖了另一个 Starter，如何保证它们的加载顺序和条件判断不冲突？

#### ### 编程题
手写一个 `log-spring-boot-starter`：要求能自动拦截所有请求并打印耗时，且支持通过配置文件开关该功能。

---

### 第12天：ImportSelector 与 ImportBeanDefinitionRegistrar
#### ### 面试题
1. **基础**：`@Import` 注解有哪几种使用方式？
2. **中级**：`ImportSelector` 和 `DeferredImportSelector` 的区别是什么？
3. **高级**：为什么 Spring Boot 的自动配置主要使用 `DeferredImportSelector`？
4. **源码**：`ConfigurationClassParser` 是如何处理 `@Import` 导入的配置类的？
5. **地狱级**：如何利用 `ImportBeanDefinitionRegistrar` 动态注册成千上万个 Mapper 接口到容器中？

#### ### 编程题
实现一个自定义 `ImportSelector`，根据配置文件中的值动态选择导入 `MySQLConfig` 还是 `OracleConfig`。

---

### 第13天：SPI 机制在 Spring Boot 中的应用
#### ### 面试题
1. **基础**：什么是 SPI (Service Provider Interface)？
2. **中级**：Java 原生 SPI 与 Spring 的 `spring.factories` 有何异同？
3. **高级**：Spring Boot 为什么不直接使用 Java 原生 SPI？
4. **源码**：`SpringFactoriesLoader.loadFactories` 的底层实现细节。
5. **地狱级**：如果在启动阶段需要拦截并修改所有的自动配置类列表，你会使用哪个 SPI 扩展点？

#### ### 编程题
演示如何利用 `spring.factories` 扩展 `ApplicationContextInitializer` 来在容器刷新前修改环境变量。

---

### 第14天：第二周复盘：自动装配全流程与源码级大盘点
#### ### 面试题
1. **综合**：请画出（或描述）从 `@SpringBootApplication` 到加载某个自动配置类的完整时序图。
2. **对比**：对比 Spring Boot 2.x 和 3.x 在自动装配实现上的差异。
3. **架构**：自动装配机制虽然方便，但会带来启动变慢的问题，你该如何平衡？
4. **地狱级**：如果某个三方库的自动配置类报错，但你又无法修改其源码，你有哪些手段可以“动态拦截”并修复它的配置？

#### ### 编程题
编写一个工具类，利用 `ConditionEvaluationReport` 打印出当前应用中所有生效和不生效的自动配置类及其原因。

---

## 📅 第三阶段：容器、Web 增强与监控 (生产进阶)

### 第15天：内嵌容器深度解剖 (Tomcat/Jetty/Undertow)
#### ### 面试题
1. **基础**：Spring Boot 默认支持哪些内嵌容器？如何切换？
2. **中级**：内嵌容器与传统外置容器 (WAR 部署) 在性能和管理上有何不同？
3. **高级**：如何通过 `WebServerFactoryCustomizer` 动态修改 Tomcat 的线程池和连接数？
4. **源码**：`ServletWebServerApplicationContext` 是如何创建并启动内嵌 Web 容器的？
5. **地狱级**：在高并发场景下，如何配置内嵌 Tomcat 的 NIO 运行模式以提升吞吐量？

#### ### 编程题
实现一个自定义的 `TomcatConnectorCustomizer`，为内嵌 Tomcat 添加一个特殊的 HTTP 连接器。

---

### 第16天：Spring Boot 与日志架构 (SLF4J/Logback)
#### ### 面试题
1. **基础**：Spring Boot 默认使用哪种日志框架？（Logback）。
2. **中级**：如何实现日志的分环境配置（使用 `logback-spring.xml` 和 `<springProfile>`）？
3. **高级**：如何动态调整运行中的 Spring Boot 应用的日志级别？
4. **源码**：`LoggingApplicationListener` 是如何监听事件并初始化日志系统的？
5. **地狱级**：在高并发大流量下，同步日志会产生严重的 IO 阻塞，如何配置异步日志且保证日志不丢失？

#### ### 编程题
配置 Logback 异步日志，并编写一个多线程压测程序证明异步日志对响应时间的提升。

---

### 第17天：错误处理与响应封装机制
#### ### 面试题
1. **基础**：Spring Boot 默认的错误页 (Whitelabel Error Page) 是如何实现的？
2. **中级**：如何自定义全局异常处理？（`@ControllerAdvice` + `@ExceptionHandler`）。
3. **高级**：如何自定义 `ErrorAttributes` 来统一返回给前端的错误报文格式？
4. **源码**：`BasicErrorController` 的工作原理及其在浏览器和 API 请求间的自动切换逻辑。
5. **地狱级**：对于 404 错误，如果拦截器没有匹配到对应的 Handler，Spring Boot 是如何跳转到错误处理逻辑的？

#### ### 编程题
实现一个自定义的 `ErrorController`，要求根据异常类型自动匹配不同的 HTTP 状态码和友好的错误提示。

---

### 第18天：Actuator 监控与指标度量 (Micrometer)
#### ### 面试题
1. **基础**：Actuator 提供了哪些核心端点？（`health`, `info`, `metrics` 等）。
2. **中级**：如何开启和保护敏感端点？（安全性配置）。
3. **高级**：Micrometer 是如何统一不同监控系统（Prometheus, Grafana）的指标定义的？
4. **源码**：`EndpointDiscoverer` 是如何扫描并暴露这些端点的？
5. **地狱级**：如何自定义一个业务指标（如：实时订单量），并通过 Actuator 暴露给 Prometheus 拉取？

#### ### 编程题
自定义一个健康检查指标 `DatabaseHealthIndicator`，模拟检查数据库连接是否正常。

---

### 第19天：Spring Boot DevTools 与热部署
#### ### 面试题
1. **基础**：DevTools 是如何实现热部署的？（双类加载器机制）。
2. **中级**：为什么热部署不能完全替代重启？哪些更改必须重启？
3. **高级**：如何配置 DevTools 排除掉某些不需要监控的文件路径？
4. **源码**：`RestartClassLoader` 的工作原理。
5. **地狱级**：在生产环境下使用 DevTools 会有哪些风险？如何彻底禁用它？

#### ### 编程题
配置并演示 DevTools 的热部署功能，并观察控制台输出，区分哪些日志是由于热部署触发的。

---

### 第20天：Web 增强：Jackson、验证器与过滤器
#### ### 面试题
1. **基础**：如何自定义 Jackson 的序列化行为？（日期格式化等）。
2. **中级**：在 Spring Boot 中如何注册自定义的 `Filter` 和 `Servlet`？（`FilterRegistrationBean`）。
3. **高级**：`ValidationAutoConfiguration` 是如何集成 Hibernate Validator 的？
4. **源码**：`JacksonAutoConfiguration` 是如何通过 `Jackson2ObjectMapperBuilder` 构建 ObjectMapper 的？
5. **地狱级**：在高并发 API 场景下，如何通过配置参数优化 Jackson 的序列化性能？

#### ### 编程题
注册一个自定义 Filter，实现对所有请求进行签名校验，并注入到 Spring Boot 容器中生效。

---

### 第21天：第三周复盘：Spring Boot 运维架构与 Web 底层总结
#### ### 面试题
1. **综合**：Spring Boot 如何实现“无缝停机” (Graceful Shutdown)？底层原理是什么？
2. **架构**：对比 Spring Boot 内嵌容器与传统 Web 容器部署的优劣。
3. **地狱级**：如果线上应用出现假死（进程在但端点无法响应），你该如何利用 Actuator 或底层工具快速定位是线程池打满还是死锁？

#### ### 编程题
实现一个“优雅停机”的拦截逻辑：在应用关闭前，先停止接收新请求，并等待已有的任务处理完成（最长等待 30 秒）。

---

## 📅 第四阶段：启动流程、生命周期与调优 (架构之巅)

### 第22天：Spring Boot 启动流程全解析 (上)
#### ### 面试题
1. **基础**：`SpringApplication` 构造函数里做了哪些准备工作？（识别 Web 类型、加载 Initializer 和 Listener）。
2. **中级**：`SpringApplicationRunListeners` 的作用是什么？
3. **高级**：如何自定义一个 `ApplicationContextInitializer`？它的执行时机是什么？
4. **源码**：详细描述 `prepareEnvironment` 阶段发生了什么。
5. **地狱级**：如果在启动初期（加载 Environment 之前）就需要读取某些配置，该如何实现？

#### ### 编程题
实现一个自定义的 `SpringApplicationRunListener`，记录启动过程中各个阶段的耗时。

---

### 第23天：Spring Boot 启动流程全解析 (下)
#### ### 面试题
1. **基础**：`createApplicationContext` 是根据什么来决定创建哪种类型的 Context 的？
2. **中级**：`prepareContext` 阶段是如何将启动类注册到容器中的？
3. **高级**：`refreshContext` 与传统 Spring 的 `refresh` 有什么区别和联系？
4. **源码**：`afterRefresh` 方法在 Spring Boot 中默认做了什么？
5. **地狱级**：如果在 `run` 方法抛出异常，Spring Boot 的错误处理报告机制 (`SpringBootExceptionReporter`) 是如何工作的？

#### ### 编程题
演示如何利用 `ApplicationRunner` 和 `CommandLineRunner` 在应用启动后执行一些初始化脚本，并对比它们的优先级。

---

### 第24天：生命周期扩展点深度应用
#### ### 面试题
1. **基础**：常用的生命周期回调接口有哪些？
2. **中级**：`SmartLifecycle` 接口在 Spring Boot 中有什么特殊地位？（可以控制启动和关闭顺序）。
3. **高级**：如何监听 Spring Boot 启动成功的事件？（`ApplicationReadyEvent`）。
4. **源码**：`DefaultLifecycleProcessor` 是如何管理这些生命周期 Bean 的？
5. **地狱级**：如何实现在应用关闭时自动清理本地临时文件，且确保清理逻辑在数据库连接关闭之前执行？

#### ### 编程题
利用 `SmartLifecycle` 实现一个后台轮询任务，要求在容器就绪后开始运行，容器关闭前停止运行。

---

### 第25天：Spring Boot 启动性能优化实战
#### ### 面试题
1. **基础**：为什么 Spring Boot 启动会越来越慢？（类扫描、自动装配、初始化任务过多）。
2. **中级**：如何通过 `spring-boot-startup-report` 工具分析启动瓶颈？
3. **高级**：什么是懒加载 (Lazy Initialization)？全局开启懒加载会有什么副作用？
4. **源码**：`spring.components` 索引文件是如何加速 Bean 扫描的？
5. **地狱级**：针对 Serverless 场景，如何通过裁剪不必要的自动配置和减少类加载来将启动时间降至 1 秒以内？

#### ### 编程题
编写一个 `BeanFactoryPostProcessor`，统计并打印出所有实例化耗时超过 50ms 的单例 Bean。

---

### 第26天：第四周复盘：生命周期与启动流程源码总结
#### ### 面试题
1. **综合**：请从源码层面总结 `SpringApplication.run()` 方法的 10 个核心步骤。
2. **架构**：Spring Boot 的这种生命周期设计，如何体现了其作为“微服务基石”的灵活性？
3. **地狱级**：如果让你重写 Spring Boot 的启动器，你会如何设计以支持并行启动多个独立的 `ApplicationContext`？

#### ### 编程题
实现一个简单的“启动预热”机制：在 `ApplicationReadyEvent` 之后，预先加载 1000 条热点数据到本地缓存中。

---

## 📅 第五阶段：疑难杂症、Native 与实战 (终极进化)

### 第27天：生产环境下的排障与调试
#### ### 面试题
1. **高级**：如何通过远程调试 (Remote Debug) 调试线上运行的 Spring Boot 应用？
2. **地狱级**：线上应用出现 Metaspace OOM，且怀疑是由于某个自动配置类频繁生成代理类导致的，你该如何排查和定位？
#### ### 编程题
编写一段代码故意制造一个 Spring Boot 启动失败（如循环依赖且涉及代理），并展示如何通过日志快速定位根因。

---

### 第28天：Spring Native 与 GraalVM 静态编译
#### ### 面试题
1. **高级**：什么是 Spring Native？它与传统 JVM 模式的区别是什么？
2. **地狱级**：GraalVM 静态编译中最大的挑战是什么？（反射、动态代理、序列化）。Spring Boot 3 引入的 AOT (Ahead-of-Time) 编译是如何解决这些问题的？
#### ### 编程题
尝试将一个简单的 Spring Boot 应用使用 Maven 插件进行 AOT 处理，并观察生成的 `reflect-config.json` 等配置文件。

---

### 第29天：Spring Boot 测试框架深度应用
#### ### 面试题
1. **高级**：`@SpringBootTest` 和 `@WebMvcTest` 的区别是什么？
2. **地狱级**：如何实现一个不依赖外部数据库的 Spring Boot 集成测试？（使用 `Testcontainers` ）。
#### ### 编程题
编写一个集成测试用例，使用 `MockBean` 模拟三方支付接口，验证订单支付成功的逻辑。

---

### 第30天：终极实战：从 0 到 1 构建高可用运维体系
#### ### 任务描述
1. **综合设计**：设计一套 Spring Boot 应用的标准化生产发布规范（包含日志规约、健康检查、优雅停机、Prometheus 监控）。
2. **总教官面试**：模拟回答面试官最后的问题：“在你的项目中，你对 Spring Boot 做了哪些深度的定制或调优？解决了什么实际问题？”

---

**总教官寄语**：恭喜你，战士！30 天的时间，你已经从“只会用注解”的初学者，蜕变成了“深谙自动化装配精髓”的 Spring Boot 专家。记住，技术只是工具，解决问题的思维才是你的核心竞争力。面试场上，带上这份自信，去征服那些大厂面试官吧！
