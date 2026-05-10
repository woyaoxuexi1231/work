> 请将以下完整内容保存为 "Spring_45天渐进编码面试指南（附答案）.md"

# Spring 45天渐进编码面试冲刺指南（含标准答案）

## 总纲
### 45天知识图谱
| 天数 | 阶段 | 知识点 | 覆盖原理项 | 有性能题 |
|------|------|--------|-----------|----------|
| 1 | 使用期 | Spring IoC容器与Bean定义 | 无 | 否 |
| 2 | 使用期 | Bean生命周期与初始化销毁 | 无 | 否 |
| 3 | 使用期 | 依赖注入方式（构造器/Setter/字段） | 无 | 否 |
| 4 | 使用期 | @Autowired与@Resource区别 | 无 | 否 |
| 5 | 使用期 | 注解驱动的Bean配置 | 无 | 否 |
| 6 | 使用期 | Spring AOP基础与切面表达式 | 无 | 否 |
| 7 | 使用期 | AOP通知类型与执行顺序 | 无 | 否 |
| 8 | 使用期 | Spring MVC请求处理流程 | 无 | 否 |
| 9 | 使用期 | 请求参数绑定与校验 | 无 | 否 |
| 10 | 使用期 | Spring Boot自动配置原理 | 无 | 否 |
| 11 | 使用期 | Spring Data JPA基础 | 无 | 否 |
| 12 | 使用期 | 事务管理@Transactional | 无 | 否 |
| 13 | 使用期 | 声明式事务传播行为 | 无 | 否 |
| 14 | 使用期 | Spring Security基础认证 | 无 | 否 |
| 15 | 使用期 | 综合实战：完整CRUD应用 | 无 | 否 |
| 16 | 原理期 | IoC容器初始化流程 | P1 | 是 |
| 17 | 原理期 | BeanDefinition解析机制 | P2 | 是 |
| 18 | 原理期 | 依赖注入实现原理 | P3 | 是 |
| 19 | 原理期 | AOP动态代理机制（JDK/CGLIB） | P4 | 是 |
| 20 | 原理期 | 事务实现原理与AOP整合 | P5 | 是 |
| 21 | 原理期 | Spring MVC DispatcherServlet流程 | P6 | 是 |
| 22 | 原理期 | HandlerMapping与HandlerAdapter | P7 | 是 |
| 23 | 原理期 | 视图解析与模板引擎 | P8 | 是 |
| 24 | 原理期 | 数据绑定与类型转换 | P9 | 是 |
| 25 | 原理期 | 异常处理机制 | P10 | 是 |
| 26 | 原理期 | 消息转换器（HttpMessageConverter） | P11 | 是 |
| 27 | 原理期 | 拦截器与过滤器 | P12 | 是 |
| 28 | 原理期 | Spring Boot启动流程 | P13 | 是 |
| 29 | 原理期 | 自动配置条件注解 | P14 | 是 |
| 30 | 原理期 | 综合实战：手写迷你Spring容器 | P1-P14 | 是 |
| 31 | 大厂期 | 高并发场景下的Bean作用域选择 | P15 | 是 |
| 32 | 大厂期 | 分布式事务解决方案 | P16 | 是 |
| 33 | 大厂期 | Spring Cloud微服务架构 | P17 | 是 |
| 34 | 大厂期 | 服务注册与发现 | P18 | 是 |
| 35 | 大厂期 | 负载均衡与熔断降级 | P19 | 是 |
| 36 | 大厂期 | 分布式追踪与链路监控 | P20 | 是 |
| 37 | 大厂期 | Spring与消息队列集成 | P21 | 是 |
| 38 | 大厂期 | 缓存策略与Spring Cache | P22 | 是 |
| 39 | 大厂期 | 异步处理与CompletableFuture | P23 | 是 |
| 40 | 大厂期 | 批量数据处理优化 | P24 | 是 |
| 41 | 大厂期 | 配置中心与外部化配置 | P25 | 是 |
| 42 | 大厂期 | 安全加固与漏洞防护 | P26 | 是 |
| 43 | 大厂期 | 监控告警体系设计 | P27 | 是 |
| 44 | 大厂期 | 灰度发布与蓝绿部署 | P28 | 是 |
| 45 | 大厂期 | 终极系统设计：高可用电商订单系统 | P1-P28 | 是 |

### 面试必考原理总清单
1. P1: IoC容器初始化流程（第16天）
2. P2: BeanDefinition解析机制（第17天）
3. P3: 依赖注入实现原理（第18天）
4. P4: AOP动态代理机制（第19天）
5. P5: 事务实现原理（第20天）
6. P6: DispatcherServlet处理流程（第21天）
7. P7: HandlerMapping与HandlerAdapter（第22天）
8. P8: 视图解析机制（第23天）
9. P9: 数据绑定与类型转换（第24天）
10. P10: 异常处理机制（第25天）
11. P11: HttpMessageConverter（第26天）
12. P12: 拦截器与过滤器（第27天）
13. P13: Spring Boot启动流程（第28天）
14. P14: 自动配置条件注解（第29天）
15. P15: Bean作用域与线程安全（第31天）
16. P16: 分布式事务（第32天）
17. P17: Spring Cloud架构（第33天）
18. P18: 服务注册发现（第34天）
19. P19: 负载均衡与熔断（第35天）
20. P20: 分布式追踪（第36天）
21. P21: 消息队列集成（第37天）
22. P22: 缓存策略（第38天）
23. P23: 异步处理（第39天）
24. P24: 批量处理优化（第40天）
25. P25: 配置中心（第41天）
26. P26: 安全防护（第42天）
27. P27: 监控告警（第43天）
28. P28: 灰度发布（第44天）

---

## 第1天：Spring IoC容器与Bean定义
**本日掌握**：理解IoC核心概念，掌握Bean定义的多种方式；**覆盖原理点**：无；**阶段**：使用期

### 🟢 基础用法题
#### 题目1：创建一个简单的Spring Bean

**问题描述**：定义一个UserService Bean，包含一个sayHello方法返回"Hello, Spring!"，并通过ApplicationContext获取Bean并调用该方法。

**✅ 标准答案**：

```java
// UserService.java
public class UserService {
    public String sayHello() {
        return "Hello, Spring!";
    }
}
```

```xml
<!-- applicationContext.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://www.springframework.org/schema/beans
                           http://www.springframework.org/schema/beans/spring-beans.xsd">
    
    <bean id="userService" class="com.example.UserService"/>
</beans>
```

```java
// 测试类
public class Main {
    public static void main(String[] args) {
        ApplicationContext context = 
            new ClassPathXmlApplicationContext("applicationContext.xml");
        UserService userService = context.getBean("userService", UserService.class);
        System.out.println(userService.sayHello());
    }
}
```

**输出结果**：
```
Hello, Spring!
```

🔍 **深度反思**：IoC的核心是控制反转，传统方式需要手动new对象，而Spring容器负责创建和管理Bean的生命周期。这里getBean方法体现了依赖查找（DL）模式。

💬 **追问预判**：
- Q: getBean("userService")和getBean(UserService.class)有什么区别？  
  A: 前者按名称查找，后者按类型查找。如果同类型有多个Bean，按类型查找会抛出NoUniqueBeanDefinitionException。
- Q: Bean的id和name有什么区别？  
  A: id必须唯一，name可以多个（用逗号分隔），且name支持别名。

---

### 🟡 中级用法题
#### 题目2：通过Java配置类定义Bean

**问题描述**：使用@Configuration和@Bean注解定义一个DataSource Bean，模拟数据库连接配置。

**✅ 标准答案**：

```java
@Configuration
public class AppConfig {
    
    @Bean(name = "dataSource")
    public DataSource dataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setUrl("jdbc:mysql://localhost:3306/test");
        ds.setUsername("root");
        ds.setPassword("password");
        return ds;
    }
    
    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
```

```java
// 使用
public class Main {
    public static void main(String[] args) {
        ApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        DataSource ds = context.getBean(DataSource.class);
        JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
        System.out.println("DataSource: " + ds.getClass().getSimpleName());
        System.out.println("JdbcTemplate: " + jdbcTemplate.getClass().getSimpleName());
    }
}
```

**输出结果**：
```
DataSource: DriverManagerDataSource
JdbcTemplate: JdbcTemplate
```

🔍 **深度反思**：@Configuration类本身也是一个Bean，Spring会通过CGLIB创建代理对象，确保@Bean方法之间的调用能获取到Spring管理的Bean而不是新创建的实例。

💬 **追问预判**：
- Q: @Configuration和@Component有什么区别？  
  A: @Configuration是@Component的特殊形式，主要用于定义Bean配置，其内部@Bean方法会被代理；@Component主要用于标记普通组件。
- Q: 如果@Bean方法调用另一个@Bean方法会怎样？  
  A: 在@Configuration类中，会返回Spring容器中的Bean；在普通@Component中，会调用普通方法创建新对象。

---

### 🔴 高级用法题
#### 题目3：条件化Bean注册

**问题描述**：根据不同环境（dev/test/prod）注册不同的DataSource实现。

**✅ 标准答案**：

```java
@Configuration
public class DataSourceConfig {
    
    @Bean
    @Profile("dev")
    public DataSource devDataSource() {
        return new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .addScript("classpath:schema.sql")
            .build();
    }
    
    @Bean
    @Profile("test")
    public DataSource testDataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setUrl("jdbc:mysql://localhost:3306/test_db");
        return ds;
    }
    
    @Bean
    @Profile("prod")
    public DataSource prodDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setJdbcUrl("jdbc:mysql://prod-db:3306/prod_db");
        ds.setUsername("prod_user");
        ds.setPassword(System.getenv("DB_PASSWORD"));
        ds.setMaximumPoolSize(20);
        return ds;
    }
}
```

```java
// 激活profile的方式
public class Main {
    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = 
            new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles("dev");
        context.register(DataSourceConfig.class);
        context.refresh();
        
        DataSource ds = context.getBean(DataSource.class);
        System.out.println("Active Profile DataSource: " + ds.getClass().getSimpleName());
    }
}
```

**输出结果（dev环境）**：
```
Active Profile DataSource: EmbeddedDatabase
```

🔍 **深度反思**：@Profile实现了环境隔离，同一套代码可以在不同环境下使用不同的Bean配置。这在多环境部署中非常有用，避免硬编码环境相关配置。

💬 **追问预判**：
- Q: 如何设置默认Profile？  
  A: 可以使用@Profile("default")或者通过spring.profiles.default配置属性。
- Q: 如果多个Profile同时激活会怎样？  
  A: 多个Profile的Bean都会注册，但如果有相同类型的Bean会冲突，需要使用@Primary解决。

---

### 🏢 大厂面试场景实战（使用层面）
#### 题目4：企业级Bean注册策略

**问题描述**：假设你正在设计一个企业级应用，需要管理数百个Bean。请设计一套合理的Bean注册和组织策略。

**✅ 标准答案**：

```java
// 策略1：按功能模块划分配置类
@Configuration
@ComponentScan(basePackages = "com.example.controller")
public class ControllerConfig {}

@Configuration
@ComponentScan(basePackages = "com.example.service")
public class ServiceConfig {}

@Configuration
@ComponentScan(basePackages = "com.example.repository")
public class RepositoryConfig {}

// 策略2：使用@Import批量导入
@Configuration
@Import({ControllerConfig.class, ServiceConfig.class, RepositoryConfig.class})
public class AppRootConfig {}

// 策略3：分层配置
@Configuration
public class InfrastructureConfig {
    @Bean
    public DataSource dataSource() { ... }
    
    @Bean
    public RedisTemplate<String, Object> redisTemplate() { ... }
}

@Configuration
@Import(InfrastructureConfig.class)
public class BusinessConfig {
    @Bean
    public OrderService orderService(OrderRepository repo) { ... }
}
```

**设计要点**：
1. **模块化**：按MVC分层或业务域划分配置类
2. **关注点分离**：基础设施配置与业务配置分离
3. **可读性**：每个配置类职责单一，不超过50行
4. **可测试性**：通过@Profile支持测试环境替换
5. **扩展性**：使用@Import便于组合和扩展

---

### 🎯 今日高频面试题速览
1. **问题**：什么是IoC？Spring IoC容器的作用是什么？  
   **答案**：IoC（控制反转）是一种设计模式，将对象的创建和依赖管理交给容器。Spring IoC容器负责Bean的生命周期管理、依赖注入和配置管理，降低耦合度。

2. **问题**：BeanFactory和ApplicationContext的区别？  
   **答案**：BeanFactory是基础IoC容器，提供基本的DI功能；ApplicationContext是BeanFactory的扩展，提供事件发布、国际化、AOP集成等更丰富的功能。

3. **问题**：@Bean和@Component的区别？  
   **答案**：@Bean用于方法级别，手动创建Bean；@Component用于类级别，自动扫描注册。@Bean更灵活，适合第三方库集成。

4. **问题**：如何指定Bean的作用域？  
   **答案**：使用@Scope注解，常用作用域：singleton（单例）、prototype（原型）、request、session。

5. **问题**：Spring如何解决循环依赖？  
   **答案**：通过三级缓存机制：singletonObjects（成品Bean）、earlySingletonObjects（早期Bean）、singletonFactories（Bean工厂），支持构造器注入之外的循环依赖。

---

## 第2天：Bean生命周期与初始化销毁
**本日掌握**：理解Bean从创建到销毁的完整生命周期，掌握初始化和销毁方法的多种配置方式；**覆盖原理点**：无；**阶段**：使用期

### 🟢 基础用法题
#### 题目1：使用@PostConstruct和@PreDestroy

**问题描述**：创建一个ConnectionPool Bean，在初始化时建立连接，销毁时释放资源。

**✅ 标准答案**：

```java
@Component
public class ConnectionPool {
    
    private Connection connection;
    
    @PostConstruct
    public void init() {
        try {
            connection = DriverManager.getConnection(
                "jdbc:h2:mem:test", "sa", "");
            System.out.println("ConnectionPool initialized, connection established");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create connection", e);
        }
    }
    
    @PreDestroy
    public void destroy() {
        if (connection != null) {
            try {
                connection.close();
                System.out.println("ConnectionPool destroyed, connection closed");
            } catch (SQLException e) {
                System.err.println("Error closing connection: " + e.getMessage());
            }
        }
    }
    
    public Connection getConnection() {
        return connection;
    }
}
```

**输出结果**：
```
ConnectionPool initialized, connection established
... (应用运行中) ...
ConnectionPool destroyed, connection closed
```

🔍 **深度反思**：@PostConstruct在依赖注入完成后执行，@PreDestroy在容器关闭前执行。这两个注解是JSR-250标准，比Spring特有的InitializingBean和DisposableBean接口更推荐使用，因为不耦合Spring框架。

💬 **追问预判**：
- Q: @PostConstruct和构造器有什么区别？  
  A: 构造器执行时依赖还未注入，@PostConstruct执行时所有依赖已注入完成。
- Q: 如果Bean是prototype作用域，@PreDestroy会执行吗？  
  A: 不会，Spring容器不管理prototype Bean的销毁。

---

### 🟡 中级用法题
#### 题目2：实现InitializingBean和DisposableBean接口

**问题描述**：通过实现Spring生命周期接口来管理Bean的初始化和销毁。

**✅ 标准答案**：

```java
@Component
public class FileProcessor implements InitializingBean, DisposableBean {
    
    private FileWriter writer;
    
    @Override
    public void afterPropertiesSet() throws Exception {
        writer = new FileWriter("output.log");
        System.out.println("FileProcessor initialized, writer created");
    }
    
    @Override
    public void destroy() throws Exception {
        if (writer != null) {
            writer.close();
            System.out.println("FileProcessor destroyed, writer closed");
        }
    }
    
    public void write(String content) throws IOException {
        writer.write(content + "\n");
        writer.flush();
    }
}
```

**输出结果**：
```
FileProcessor initialized, writer created
... (应用运行中) ...
FileProcessor destroyed, writer closed
```

🔍 **深度反思**：实现接口方式的优点是生命周期回调时机明确，但缺点是强耦合Spring框架。实际项目中更推荐使用注解方式。

💬 **追问预判**：
- Q: 初始化方法的执行顺序是什么？  
  A: 构造器 → @Autowired → @PostConstruct/afterPropertiesSet()。
- Q: 如果同时使用@PostConstruct和afterPropertiesSet会怎样？  
  A: @PostConstruct先执行，因为它的优先级更高。

---

### 🔴 高级用法题
#### 题目3：自定义生命周期处理器

**问题描述**：创建一个自定义的BeanPostProcessor，在Bean初始化前后执行自定义逻辑。

**✅ 标准答案**：

```java
@Component
public class CustomBeanPostProcessor implements BeanPostProcessor {
    
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof Service) {
            System.out.println("Before initialization: " + beanName);
        }
        return bean;
    }
    
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof Service) {
            System.out.println("After initialization: " + beanName);
            // 可以返回代理对象增强Bean
        }
        return bean;
    }
}

@Service
public class UserService {
    @PostConstruct
    public void init() {
        System.out.println("UserService init method");
    }
}
```

**输出结果**：
```
Before initialization: userService
UserService init method
After initialization: userService
```

🔍 **深度反思**：BeanPostProcessor是Spring框架的扩展点，可以在Bean初始化前后进行增强处理，AOP就是基于这个机制实现的。

💬 **追问预判**：
- Q: BeanPostProcessor的执行顺序如何控制？  
  A: 使用@Order注解或实现Ordered接口指定顺序，数值越小优先级越高。
- Q: BeanPostProcessor会处理自己吗？  
  A: 不会，BeanPostProcessor在所有Bean初始化之前被注册，不会处理自身。

---

### 🏢 大厂面试场景实战（使用层面）
#### 题目4：资源池的优雅关闭

**问题描述**：设计一个连接池管理组件，要求支持优雅关闭，确保在应用关闭时所有连接都被正确释放。

**✅ 标准答案**：

```java
@Component
public class GracefulConnectionPool implements SmartLifecycle {
    
    private List<Connection> connections = new ArrayList<>();
    private boolean running = false;
    
    @Override
    public void start() {
        // 初始化连接池
        for (int i = 0; i < 10; i++) {
            try {
                Connection conn = createConnection();
                connections.add(conn);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        running = true;
        System.out.println("Connection pool started with " + connections.size() + " connections");
    }
    
    @Override
    public void stop() {
        for (Connection conn : connections) {
            try {
                conn.close();
            } catch (SQLException e) {
                System.err.println("Error closing connection: " + e.getMessage());
            }
        }
        connections.clear();
        running = false;
        System.out.println("Connection pool stopped, all connections closed");
    }
    
    @Override
    public boolean isRunning() {
        return running;
    }
    
    // 其他方法...
}
```

**设计要点**：
1. 使用SmartLifecycle接口实现优雅启停
2. 重写isRunning()方法让容器知道组件状态
3. 在stop()方法中确保资源释放的异常处理
4. 支持start()方法进行预热初始化

---

### 🎯 今日高频面试题速览
1. **问题**：Bean的生命周期有哪些阶段？  
   **答案**：实例化 → 属性注入 → 初始化前（BeanPostProcessor）→ 初始化（@PostConstruct/afterPropertiesSet）→ 初始化后（BeanPostProcessor）→ 使用 → 销毁前 → 销毁（@PreDestroy/destroy）。

2. **问题**：@PostConstruct、InitializingBean、init-method的执行顺序？  
   **答案**：@PostConstruct最先执行，然后是InitializingBean的afterPropertiesSet()，最后是init-method指定的方法。

3. **问题**：BeanPostProcessor和BeanFactoryPostProcessor的区别？  
   **答案**：BeanPostProcessor处理Bean实例，在Bean初始化前后执行；BeanFactoryPostProcessor处理BeanDefinition，在Bean实例化之前修改配置元数据。

4. **问题**：prototype Bean的生命周期有什么特点？  
   **答案**：Spring容器只负责创建和初始化，不负责销毁，需要用户手动管理。每次getBean都会创建新实例。

5. **问题**：SmartLifecycle和Lifecycle的区别？  
   **答案**：SmartLifecycle提供isRunning()方法和更细粒度的生命周期控制，支持自动启动。

---

## 第3天：依赖注入方式（构造器/Setter/字段）
**本日掌握**：掌握三种依赖注入方式的使用场景和最佳实践；**覆盖原理点**：无；**阶段**：使用期

### 🟢 基础用法题
#### 题目1：构造器注入

**问题描述**：创建一个OrderService，通过构造器注入OrderRepository依赖。

**✅ 标准答案**：

```java
@Repository
public class OrderRepository {
    public void save(Order order) {
        System.out.println("Saving order: " + order.getId());
    }
}

@Service
public class OrderService {
    
    private final OrderRepository orderRepository;
    
    // 构造器注入（Spring 4.3+ 可省略@Autowired）
    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
        System.out.println("OrderService constructed with OrderRepository");
    }
    
    public void createOrder(Order order) {
        orderRepository.save(order);
    }
}
```

**输出结果**：
```
OrderService constructed with OrderRepository
```

🔍 **深度反思**：构造器注入是Spring官方推荐的方式，优点是：1) 依赖不可变（final字段）；2) 依赖必须在创建时提供；3) 便于单元测试（可以手动传入mock对象）；4) 避免循环依赖问题。

💬 **追问预判**：
- Q: 什么时候需要显式添加@Autowired？  
  A: 当有多个构造器时，需要用@Autowired指定使用哪个。
- Q: 构造器注入能解决循环依赖吗？  
  A: 不能，构造器注入无法解决循环依赖，需要使用Setter注入或@Lazy。

---

### 🟡 中级用法题
#### 题目2：Setter注入与可选依赖

**问题描述**：创建一个UserService，其中UserRepository是必需依赖（构造器注入），EmailService是可选依赖（Setter注入）。

**✅ 标准答案**：

```java
@Repository
public class UserRepository {
    public User findById(Long id) {
        return new User(id, "TestUser");
    }
}

@Service
public class EmailService {
    public void sendEmail(String to, String message) {
        System.out.println("Sending email to " + to + ": " + message);
    }
}

@Service
public class UserService {
    
    private final UserRepository userRepository;
    private EmailService emailService;
    
    // 必需依赖：构造器注入
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    // 可选依赖：Setter注入
    @Autowired(required = false)
    public void setEmailService(EmailService emailService) {
        this.emailService = emailService;
        System.out.println("EmailService injected via Setter");
    }
    
    public User getUserWithNotification(Long id) {
        User user = userRepository.findById(id);
        if (emailService != null) {
            emailService.sendEmail(user.getEmail(), "User accessed: " + user.getName());
        }
        return user;
    }
}
```

**输出结果**：
```
EmailService injected via Setter
```

🔍 **深度反思**：Setter注入适合可选依赖或需要重新配置的场景。使用@Autowired(required = false)可以让依赖变为可选，如果容器中没有该Bean也不会报错。

💬 **追问预判**：
- Q: 为什么构造器注入更适合必需依赖？  
  A: 构造器注入确保对象创建时依赖已经就绪，避免NullPointerException。
- Q: Setter注入和字段注入的区别？  
  A: Setter注入支持可选依赖和重新配置，字段注入更简洁但不利于测试。

---

### 🔴 高级用法题
#### 题目3：混合注入与@Qualifier

**问题描述**：当存在多个同类型Bean时，如何精确注入指定的Bean？

**✅ 标准答案**：

```java
public interface PaymentService {
    void pay(double amount);
}

@Service("alipay")
public class AlipayService implements PaymentService {
    @Override
    public void pay(double amount) {
        System.out.println("Alipay payment: " + amount);
    }
}

@Service("wechat")
public class WechatService implements PaymentService {
    @Override
    public void pay(double amount) {
        System.out.println("WeChat payment: " + amount);
    }
}

@Service
public class OrderService {
    
    private final PaymentService primaryPaymentService;
    private PaymentService secondaryPaymentService;
    
    // 构造器注入，使用@Qualifier指定Bean名称
    public OrderService(@Qualifier("alipay") PaymentService paymentService) {
        this.primaryPaymentService = paymentService;
    }
    
    // Setter注入，使用@Qualifier指定另一个Bean
    @Autowired
    @Qualifier("wechat")
    public void setSecondaryPaymentService(PaymentService paymentService) {
        this.secondaryPaymentService = paymentService;
    }
    
    public void processPayment(double amount, boolean useSecondary) {
        if (useSecondary && secondaryPaymentService != null) {
            secondaryPaymentService.pay(amount);
        } else {
            primaryPaymentService.pay(amount);
        }
    }
}
```

**输出结果**：
```
Alipay payment: 100.0  (使用主支付)
WeChat payment: 100.0  (使用备用支付)
```

🔍 **深度反思**：@Qualifier通过Bean名称进行精确匹配，解决同类型多个Bean的注入歧义问题。还可以配合@Primary指定默认Bean。

💬 **追问预判**：
- Q: @Qualifier和@Primary的区别？  
  A: @Qualifier用于精确指定Bean，@Primary用于指定首选Bean。
- Q: 如果没有指定@Qualifier且有多个同类型Bean会怎样？  
  A: 抛出NoUniqueBeanDefinitionException。

---

### 🏢 大厂面试场景实战（使用层面）
#### 题目4：复杂依赖注入场景

**问题描述**：设计一个具有多层依赖的服务，包含必需依赖、可选依赖、集合依赖，并说明注入策略。

**✅ 标准答案**：

```java
@Service
public class OrderProcessingService {
    
    // 必需依赖：构造器注入
    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    
    // 可选依赖：Setter注入
    private NotificationService notificationService;
    
    // 集合依赖：自动注入所有实现
    private List<OrderValidator> validators;
    private Map<String, DiscountCalculator> discountCalculators;
    
    // 构造器注入必需依赖
    public OrderProcessingService(
        OrderRepository orderRepository,
        @Qualifier("default") PaymentService paymentService) {
        this.orderRepository = orderRepository;
        this.paymentService = paymentService;
    }
    
    // Setter注入可选依赖
    @Autowired(required = false)
    public void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }
    
    // 集合注入：自动收集所有实现
    @Autowired
    public void setValidators(List<OrderValidator> validators) {
        this.validators = validators;
    }
    
    @Autowired
    public void setDiscountCalculators(Map<String, DiscountCalculator> calculators) {
        this.discountCalculators = calculators;
    }
    
    public void processOrder(Order order) {
        // 使用集合依赖进行校验
        for (OrderValidator validator : validators) {
            validator.validate(order);
        }
        
        // 使用Map依赖获取对应计算器
        DiscountCalculator calculator = discountCalculators.get(order.getDiscountType());
        double discount = calculator.calculate(order);
        
        paymentService.pay(order.getAmount() - discount);
        orderRepository.save(order);
        
        // 可选依赖的条件使用
        if (notificationService != null) {
            notificationService.notify(order.getUserId());
        }
    }
}
```

**设计要点**：
1. 必需依赖使用构造器注入，保证对象完整性
2. 可选依赖使用Setter注入，增加灵活性
3. 集合注入自动收集所有同类型Bean，支持扩展
4. Map注入通过Bean名称作为key，便于按名称查找

---

### 🎯 今日高频面试题速览
1. **问题**：三种依赖注入方式各有什么优缺点？  
   **答案**：构造器注入：优点（不可变、保证依赖完整、利于测试），缺点（构造器冗长）；Setter注入：优点（灵活、支持可选依赖），缺点（依赖可变、可能为空）；字段注入：优点（简洁），缺点（难以测试、依赖关系不明显）。

2. **问题**：为什么Spring推荐构造器注入？  
   **答案**：保证依赖不可变（final）、保证对象创建时依赖就绪、便于单元测试、避免循环依赖问题。

3. **问题**：如何注入一个List或Map类型的依赖？  
   **答案**：Spring会自动收集所有同类型Bean注入到List，或按Bean名称作为key注入到Map。

4. **问题**：@Autowired的required属性有什么作用？  
   **答案**：required=true（默认）时，找不到Bean会抛出异常；required=false时，找不到Bean则注入null。

5. **问题**：Spring如何解决循环依赖？  
   **答案**：通过三级缓存机制，支持Setter注入的循环依赖，但不支持构造器注入的循环依赖。

---

## 第4天：@Autowired与@Resource区别
**本日掌握**：深入理解@Autowired和@Resource的区别，掌握各自的使用场景；**覆盖原理点**：无；**阶段**：使用期

### 🟢 基础用法题
#### 题目1：@Autowired的基本使用

**问题描述**：创建一个Service，使用@Autowired注入依赖。

**✅ 标准答案**：

```java
@Repository
public class UserDao {
    public User findById(Long id) {
        return new User(id, "Test");
    }
}

@Service
public class UserService {
    
    @Autowired
    private UserDao userDao;
    
    public User getUser(Long id) {
        return userDao.findById(id);
    }
}
```

**输出结果**：
```
User{id=1, name='Test'}
```

🔍 **深度反思**：@Autowired是Spring框架特有的注解，默认按类型注入。如果有多个同类型Bean，需要配合@Qualifier指定Bean名称。

💬 **追问预判**：
- Q: @Autowired默认是按类型还是按名称？  
  A: 默认按类型注入，如果类型匹配多个Bean，则按名称匹配。
- Q: @Autowired可以用于哪些位置？  
  A: 可以用于字段、构造器、Setter方法、普通方法和参数。

---

### 🟡 中级用法题
#### 题目2：@Resource的基本使用

**问题描述**：创建一个Service，使用@Resource注入依赖。

**✅ 标准答案**：

```java
@Repository("userRepository")
public class UserRepository {
    public User findById(Long id) {
        return new User(id, "Test");
    }
}

@Service
public class UserService {
    
    @Resource(name = "userRepository")
    private UserRepository userRepo;
    
    @Resource
    private OrderRepository orderRepository;  // 按名称注入，默认字段名
    
    public User getUser(Long id) {
        return userRepo.findById(id);
    }
}
```

**输出结果**：
```
User{id=1, name='Test'}
```

🔍 **深度反思**：@Resource是JSR-250标准注解，默认按名称注入。如果指定了name属性，则严格按名称查找；如果没有指定name，则先按字段名查找，找不到再按类型查找。

💬 **追问预判**：
- Q: @Resource的name属性和Bean名称有什么关系？  
  A: name属性指定要注入的Bean名称，对应@Service/@Repository等注解中的value属性或默认类名首字母小写。
- Q: @Resource可以用于哪些位置？  
  A: 可以用于字段和Setter方法，不能用于构造器。

---

### 🔴 高级用法题
#### 题目3：@Autowired与@Resource对比实践

**问题描述**：当存在多个同类型Bean时，对比@Autowired和@Resource的注入行为。

**✅ 标准答案**：

```java
public interface MessageService {
    String getMessage();
}

@Service("smsService")
public class SmsService implements MessageService {
    @Override
    public String getMessage() {
        return "SMS message";
    }
}

@Service("emailService")
public class EmailService implements MessageService {
    @Override
    public String getMessage() {
        return "Email message";
    }
}

@Service
public class NotificationService {
    
    // @Autowired + @Qualifier：按类型查找后按名称筛选
    @Autowired
    @Qualifier("smsService")
    private MessageService primaryService;
    
    // @Resource：直接按名称查找
    @Resource(name = "emailService")
    private MessageService secondaryService;
    
    // @Autowired：不指定Qualifier会报错（多个同类型Bean）
    // @Autowired
    // private MessageService anotherService;  // NoUniqueBeanDefinitionException
    
    public void sendNotifications() {
        System.out.println("Primary: " + primaryService.getMessage());
        System.out.println("Secondary: " + secondaryService.getMessage());
    }
}
```

**输出结果**：
```
Primary: SMS message
Secondary: Email message
```

🔍 **深度反思**：@Autowired是Spring特有的，支持更丰富的注入场景（构造器、参数等），需要配合@Qualifier处理多Bean场景；@Resource是J2EE标准，按名称注入更直接，但功能相对简单。

💬 **追问预判**：
- Q: 如果@Resource没有找到匹配名称的Bean会怎样？  
  A: 会回退到按类型查找，如果类型也不匹配则抛出异常。
- Q: @Autowired和@Resource的性能有差异吗？  
  A: @Autowired需要先按类型查找再按名称筛选，@Resource直接按名称查找，理论上@Resource性能稍好。

---

### 🏢 大厂面试场景实战（使用层面）
#### 题目4：企业级依赖注入策略

**问题描述**：设计一套企业级应用的依赖注入策略，说明何时使用@Autowired，何时使用@Resource。

**✅ 标准答案**：

```java
// 策略1：核心服务使用构造器注入 + @Autowired（推荐）
@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    
    public OrderService(OrderRepository orderRepository, PaymentService paymentService) {
        this.orderRepository = orderRepository;
        this.paymentService = paymentService;
    }
}

// 策略2：需要精确控制Bean名称时使用@Resource
@Service
public class NotificationService {
    @Resource(name = "asyncEmailService")
    private EmailService emailService;
    
    @Resource(name = "smsService")
    private SmsService smsService;
}

// 策略3：集合注入使用@Autowired
@Service
public class ValidatorService {
    @Autowired
    private List<OrderValidator> validators;
    
    @Autowired
    private Map<String, DiscountCalculator> discountCalculators;
}

// 策略4：可选依赖使用@Autowired(required = false)
@Service
public class ReportService {
    @Autowired(required = false)
    private ExcelExportService excelExportService;  // 可选依赖
}
```

**策略总结**：
| 场景 | 推荐注解 | 原因 |
|------|----------|------|
| 构造器注入 | @Autowired（可省略） | Spring 4.3+自动支持，官方推荐 |
| 字段/Setter注入（单个Bean） | @Resource | 按名称注入，语义明确 |
| 集合注入 | @Autowired | 自动收集所有同类型Bean |
| 可选依赖 | @Autowired(required = false) | 支持required属性 |
| 需要Qualifier | @Autowired + @Qualifier | @Resource不支持Qualifier |

---

### 🎯 今日高频面试题速览
1. **问题**：@Autowired和@Resource的区别？  
   **答案**：来源不同（@Autowired是Spring，@Resource是JSR-250）；注入方式不同（@Autowired默认按类型，@Resource默认按名称）；支持位置不同（@Autowired支持构造器，@Resource不支持）。

2. **问题**：@Autowired默认按什么注入？  
   **答案**：默认按类型注入（byType），如果类型匹配多个Bean，则按名称（byName）匹配。

3. **问题**：@Resource的name属性有什么作用？  
   **答案**：指定要注入的Bean名称，精确匹配Bean定义中的id或name。

4. **问题**：什么时候需要使用@Qualifier？  
   **答案**：当@Autowired按类型匹配到多个Bean时，需要用@Qualifier指定Bean名称。

5. **问题**：@Autowired可以省略吗？  
   **答案**：Spring 4.3+中，如果类只有一个构造器，可以省略@Autowired注解。

---

## 第5天：注解驱动的Bean配置
**本日掌握**：掌握@ComponentScan、@Import、@Conditional等注解的使用；**覆盖原理点**：无；**阶段**：使用期

### 🟢 基础用法题
#### 题目1：@ComponentScan自动扫描

**问题描述**：使用@ComponentScan配置Spring自动扫描组件。

**✅ 标准答案**：

```java
// 启动类
@Configuration
@ComponentScan(basePackages = {"com.example.controller", "com.example.service", "com.example.repository"})
public class AppConfig {
    // 额外的Bean定义...
}

// Controller
@Controller
public class UserController {
    @Autowired
    private UserService userService;
}

// Service
@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;
}

// Repository
@Repository
public class UserRepository {
    public User findById(Long id) {
        return new User(id, "Test");
    }
}
```

**输出结果**：所有标注@Component及其派生注解（@Controller、@Service、@Repository）的类都会被自动注册为Bean。

🔍 **深度反思**：@ComponentScan默认扫描配置类所在的包及其子包。可以通过basePackages或basePackageClasses指定扫描范围。

💬 **追问预判**：
- Q: @ComponentScan的默认扫描范围是什么？  
  A: 默认扫描配置类所在的包及其所有子包。
- Q: 如何排除某些类不被扫描？  
  A: 使用excludeFilters属性，如excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {ExcludedClass.class})。

---

### 🟡 中级用法题
#### 题目2：@Import导入配置

**问题描述**：使用@Import组合多个配置类。

**✅ 标准答案**：

```java
// 数据源配置
@Configuration
public class DataSourceConfig {
    @Bean
    public DataSource dataSource() {
        return new DriverManagerDataSource("jdbc:h2:mem:test");
    }
}

// JPA配置
@Configuration
public class JpaConfig {
    @Bean
    public EntityManagerFactory entityManagerFactory(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        return factory.getObject();
    }
}

// 事务配置
@Configuration
@EnableTransactionManagement
public class TransactionConfig {
    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}

// 主配置类：组合所有配置
@Configuration
@Import({DataSourceConfig.class, JpaConfig.class, TransactionConfig.class})
@ComponentScan(basePackages = "com.example")
public class AppConfig {
    // 主配置类
}
```

**输出结果**：所有导入的配置类中的Bean都会被注册到容器中。

🔍 **深度反思**：@Import是Spring配置模块化的重要手段，可以将配置按职责划分到不同的配置类中，再通过@Import组合，提高代码的可维护性。

💬 **追问预判**：
- Q: @Import可以导入哪些类型？  
  A: 可以导入@Configuration类、ImportSelector实现类、ImportBeanDefinitionRegistrar实现类。
- Q: @Import和@ComponentScan有什么区别？  
  A: @Import用于显式导入指定的配置类，@ComponentScan用于自动扫描包下的组件。

---

### 🔴 高级用法题
#### 题目3：@Conditional条件化配置

**问题描述**：根据不同条件注册不同的Bean。

**✅ 标准答案**：

```java
// 自定义条件：检查是否存在特定类
public class RedisCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        try {
            context.getClassLoader().loadClass("redis.clients.jedis.Jedis");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}

// 自定义条件：检查配置属性
public class ProductionCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String profile = context.getEnvironment().getProperty("spring.profiles.active");
        return "prod".equals(profile);
    }
}

@Configuration
public class CacheConfig {
    
    @Bean
    @Conditional(RedisCondition.class)
    public CacheManager redisCacheManager() {
        return new RedisCacheManager();
    }
    
    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    public CacheManager simpleCacheManager() {
        return new ConcurrentMapCacheManager();
    }
    
    @Bean
    @Conditional(ProductionCondition.class)
    public CacheManager clusterCacheManager() {
        return new ClusterCacheManager();
    }
}
```

**输出结果**：根据条件动态决定注册哪个CacheManager Bean。

🔍 **深度反思**：@Conditional是Spring 4.0引入的条件化配置机制，Spring Boot大量使用这个特性实现自动配置。可以通过实现Condition接口或使用Spring Boot提供的@ConditionalOnXxx注解。

💬 **追问预判**：
- Q: Spring Boot提供了哪些常用的@ConditionalOnXxx注解？  
  A: @ConditionalOnClass、@ConditionalOnMissingClass、@ConditionalOnBean、@ConditionalOnMissingBean、@ConditionalOnProperty等。
- Q: @Conditional和@Profile有什么区别？  
  A: @Profile基于环境配置，@Conditional基于任意条件判断，更灵活。

---

### 🏢 大厂面试场景实战（使用层面）
#### 题目4：企业级配置组织策略

**问题描述**：设计一个大型企业应用的配置组织策略，包含多环境支持、模块化配置、条件化Bean注册。

**✅ 标准答案**：

```java
// 1. 基础设施配置（数据库、缓存等）
@Configuration
public class InfrastructureConfig {
    @Bean
    @Profile("dev")
    public DataSource devDataSource() { return new EmbeddedDatabaseBuilder().build(); }
    
    @Bean
    @Profile("prod")
    public DataSource prodDataSource() { return new HikariDataSource(); }
    
    @Bean
    @ConditionalOnProperty(name = "cache.enabled", havingValue = "true")
    public CacheManager cacheManager() { return new RedisCacheManager(); }
}

// 2. 安全配置
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests().anyRequest().authenticated().and().build();
    }
}

// 3. Web配置
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**").allowedOrigins("*");
    }
}

// 4. 业务配置
@Configuration
public class BusinessConfig {
    @Bean
    public OrderService orderService(OrderRepository repo, PaymentService payment) {
        return new OrderService(repo, payment);
    }
}

// 5. 主配置类
@Configuration
@Import({
    InfrastructureConfig.class,
    SecurityConfig.class,
    WebConfig.class,
    BusinessConfig.class
})
@ComponentScan(basePackages = "com.example")
public class AppConfig {
    // 主入口
}
```

**配置策略总结**：
1. **模块化**：按功能划分配置类（基础设施、安全、Web、业务）
2. **多环境支持**：使用@Profile切换不同环境的Bean
3. **条件化注册**：使用@Conditional系列注解按需注册Bean
4. **组合配置**：使用@Import组合各模块配置
5. **自动扫描**：使用@ComponentScan扫描业务组件

---

### 🎯 今日高频面试题速览
1. **问题**：@ComponentScan的作用是什么？  
   **答案**：自动扫描指定包下的@Component及其派生注解（@Controller、@Service、@Repository），将其注册为Spring Bean。

2. **问题**：@Import可以导入哪些类型？  
   **答案**：可以导入@Configuration类、ImportSelector实现类、ImportBeanDefinitionRegistrar实现类，以及普通组件类。

3. **问题**：@Conditional的作用是什么？  
   **答案**：根据条件判断是否注册Bean，实现条件化配置。

4. **问题**：@Profile和@Conditional的区别？  
   **答案**：@Profile基于环境配置（spring.profiles.active），@Conditional基于任意条件判断，更灵活。

5. **问题**：Spring Boot的自动配置是如何实现的？  
   **答案**：通过@EnableAutoConfiguration注解，结合@Conditional系列注解和META-INF/spring.factories文件实现自动配置。

---

## 第6天：Spring AOP基础与切面表达式
**本日掌握**：理解AOP核心概念，掌握切面表达式的编写；**覆盖原理点**：无；**阶段**：使用期

### 🟢 基础用法题
#### 题目1：创建一个简单的日志切面

**问题描述**：创建一个切面，在所有Service方法执行前后打印日志。

**✅ 标准答案**：

```java
// 定义切面
@Aspect
@Component
public class LoggingAspect {
    
    private static final Logger logger = LoggerFactory.getLogger(LoggingAspect.class);
    
    // 切点：所有Service类的所有方法
    @Pointcut("execution(* com.example.service.*.*(..))")
    public void serviceMethods() {}
    
    // 前置通知
    @Before("serviceMethods()")
    public void logBefore(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();
        logger.info("Before method: {} with args: {}", methodName, Arrays.toString(args));
    }
    
    // 后置通知
    @After("serviceMethods()")
    public void logAfter(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().getName();
        logger.info("After method: {}", methodName);
    }
}

// 使用切面的Service
@Service
public class UserService {
    public User getUser(Long id) {
        return new User(id, "Test");
    }
    
    public void saveUser(User user) {
        System.out.println("User saved: " + user.getName());
    }
}
```

**输出结果**：
```
INFO  - Before method: getUser with args: [1]
User{id=1, name='Test'}
INFO  - After method: getUser
INFO  - Before method: saveUser with args: [User{id=2, name='Alice'}]
User saved: Alice
INFO  - After method: saveUser
```

🔍 **深度反思**：AOP（面向切面编程）通过切点表达式定位目标方法，通过通知（Advice）在目标方法执行前后插入额外逻辑。@Pointcut定义切点，@Before和@After是通知类型。

💬 **追问预判**：
- Q: 切点表达式的语法是什么？  
  A: 格式为execution(modifiers-pattern? ret-type-pattern declaring-type-pattern? name-pattern(param-pattern) throws-pattern?)。
- Q: JoinPoint对象包含哪些信息？  
  A: 包含目标对象、方法签名、方法参数、代理对象等信息。

---

### 🟡 中级用法题
#### 题目2：使用@Around环绕通知

**问题描述**：创建一个性能监控切面，使用@Around通知记录方法执行时间。

**✅ 标准答案**：

```java
@Aspect
@Component
public class PerformanceAspect {
    
    private static final Logger logger = LoggerFactory.getLogger(PerformanceAspect.class);
    
    @Around("execution(* com.example.service..*.*(..))")
    public Object monitorPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        
        // 执行目标方法
        Object result = joinPoint.proceed();
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        String methodName = joinPoint.getSignature().getName();
        logger.info("Method {} executed in {} ms", methodName, duration);
        
        // 慢方法告警
        if (duration > 1000) {
            logger.warn("Slow method detected: {} took {} ms", methodName, duration);
        }
        
        return result;
    }
}
```

**输出结果**：
```
INFO  - Method getUser executed in 15 ms
INFO  - Method processOrder executed in 1200 ms
WARN  - Slow method detected: processOrder took 1200 ms
```

🔍 **深度反思**：@Around通知是最强大的通知类型，可以完全控制目标方法的执行，包括是否执行、何时执行、修改参数和返回值。需要调用proceed()方法执行目标方法。

💬 **追问预判**：
- Q: @Around和其他通知类型的区别？  
  A: @Around可以控制目标方法的执行流程，其他通知只能在特定时机执行额外逻辑。
- Q: ProceedingJoinPoint和JoinPoint有什么区别？  
  A: ProceedingJoinPoint继承自JoinPoint，增加了proceed()方法用于执行目标方法。

---

### 🔴 高级用法题
#### 题目3：自定义注解实现切面

**问题描述**：创建一个自定义注解@Loggable，用于标记需要记录日志的方法。

**✅ 标准答案**：

```java
// 自定义注解
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Loggable {
    String level() default "INFO";
    boolean logArgs() default true;
    boolean logResult() default true;
}

// 切面实现
@Aspect
@Component
public class LoggableAspect {
    
    private static final Logger logger = LoggerFactory.getLogger(LoggableAspect.class);
    
    @Around("@annotation(loggable)")
    public Object logMethod(ProceedingJoinPoint joinPoint, Loggable loggable) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        Level level = Level.valueOf(loggable.level());
        
        // 记录入参
        if (loggable.logArgs()) {
            logger.log(level, "Method {} called with args: {}", methodName, 
                Arrays.toString(joinPoint.getArgs()));
        }
        
        long startTime = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        long duration = System.currentTimeMillis() - startTime;
        
        // 记录结果
        if (loggable.logResult()) {
            logger.log(level, "Method {} returned: {} (took {} ms)", 
                methodName, result, duration);
        } else {
            logger.log(level, "Method {} executed in {} ms", methodName, duration);
        }
        
        return result;
    }
}

// 使用自定义注解
@Service
public class OrderService {
    
    @Loggable(level = "DEBUG")
    public Order createOrder(User user, List<Product> products) {
        return new Order(1L, user.getId(), products);
    }
    
    @Loggable(logArgs = false)
    public void updateOrderStatus(Long orderId, String status) {
        System.out.println("Order " + orderId + " status updated to " + status);
    }
}
```

**输出结果**：
```
DEBUG - Method createOrder called with args: [User{id=1}, [Product{id=1}, Product{id=2}]]
DEBUG - Method createOrder returned: Order{id=1, userId=1, products=[...]} (took 5 ms)
INFO  - Method updateOrderStatus executed in 2 ms
```

🔍 **深度反思**：通过自定义注解+切面的方式，可以灵活地为特定方法添加横切关注点，无需修改目标代码，符合开闭原则。

💬 **追问预判**：
- Q: 自定义注解需要什么元注解？  
  A: @Target指定作用位置（方法、类等），@Retention指定保留策略（RUNTIME必须），@Documented可选。
- Q: 如何在切面中获取注解的属性值？  
  A: 在通知方法参数中添加注解类型参数，Spring会自动注入。

---

### 🏢 大厂面试场景实战（使用层面）
#### 题目4：企业级AOP应用场景

**问题描述**：设计一套企业级AOP方案，包含日志记录、性能监控、异常处理、事务管理等切面。

**✅ 标准答案**：

```java
// 1. 日志切面
@Aspect
@Component
public class LoggingAspect {
    @Pointcut("@annotation(com.example.annotation.Loggable)")
    public void loggableMethods() {}
    
    @Around("loggableMethods()")
    public Object log(ProceedingJoinPoint joinPoint) throws Throwable { /* ... */ }
}

// 2. 性能监控切面
@Aspect
@Component
public class PerformanceAspect {
    @Pointcut("execution(* com.example.service..*.*(..))")
    public void serviceMethods() {}
    
    @Around("serviceMethods()")
    public Object monitor(ProceedingJoinPoint joinPoint) throws Throwable { /* ... */ }
}

// 3. 异常处理切面
@Aspect
@Component
public class ExceptionHandlingAspect {
    @Pointcut("execution(* com.example.controller..*.*(..))")
    public void controllerMethods() {}
    
    @AfterThrowing(pointcut = "controllerMethods()", throwing = "ex")
    public void handleException(JoinPoint joinPoint, Exception ex) {
        logger.error("Exception in {}: {}", joinPoint.getSignature().getName(), ex.getMessage());
        // 可以发送告警通知
    }
}

// 4. 事务切面（Spring自带，这里展示自定义事务管理）
@Aspect
@Component
public class TransactionAspect {
    @Pointcut("@annotation(org.springframework.transaction.annotation.Transactional)")
    public void transactionalMethods() {}
    
    @Around("transactionalMethods()")
    public Object manageTransaction(ProceedingJoinPoint joinPoint) throws Throwable { /* ... */ }
}

// 5. 安全切面
@Aspect
@Component
public class SecurityAspect {
    @Pointcut("@annotation(com.example.annotation.RequiresPermission)")
    public void securedMethods() {}
    
    @Before("securedMethods()")
    public void checkPermission(JoinPoint joinPoint) {
        // 权限校验逻辑
    }
}
```

**AOP策略总结**：
1. **单一职责**：每个切面只负责一个关注点
2. **优先级控制**：使用@Order注解控制切面执行顺序
3. **注解驱动**：通过自定义注解灵活标记目标方法
4. **性能考虑**：避免在切面中执行耗时操作
5. **异常处理**：确保切面自身不会抛出异常影响业务

---

### 🎯 今日高频面试题速览
1. **问题**：AOP的核心概念有哪些？  
   **答案**：切面（Aspect）、切点（Pointcut）、通知（Advice）、连接点（JoinPoint）、织入（Weaving）。

2. **问题**：通知类型有哪些？  
   **答案**：@Before（前置）、@After（后置）、@AfterReturning（返回后）、@AfterThrowing（异常后）、@Around（环绕）。

3. **问题**：切点表达式中".."和"*"分别代表什么？  
   **答案**："*"匹配任意字符，".."匹配任意数量的参数或子包。

4. **问题**：Spring AOP和AspectJ的区别？  
   **答案**：Spring AOP基于代理，只支持方法级别的切面；AspectJ功能更强大，支持字段、构造器等更多切入点。

5. **问题**：@Around通知中必须做什么？  
   **答案**：必须调用proceed()方法执行目标方法，否则目标方法不会被执行。

---

## 第7天：AOP通知类型与执行顺序
**本日掌握**：理解各种通知类型的执行时机和顺序；**覆盖原理点**：无；**阶段**：使用期

### 🟢 基础用法题
#### 题目1：五种通知类型的使用

**问题描述**：创建一个切面，展示五种通知类型的执行顺序。

**✅ 标准答案**：

```java
@Aspect
@Component
public class AllAdviceTypesAspect {
    
    private static final Logger logger = LoggerFactory.getLogger(AllAdviceTypesAspect.class);
    
    @Pointcut("execution(* com.example.service.DemoService.execute(..))")
    public void executeMethod() {}
    
    @Before("executeMethod()")
    public void beforeAdvice(JoinPoint joinPoint) {
        logger.info("1. @Before - Before method execution");
    }
    
    @After("executeMethod()")
    public void afterAdvice(JoinPoint joinPoint) {
        logger.info("5. @After - After method execution (regardless of outcome)");
    }
    
    @AfterReturning(pointcut = "executeMethod()", returning = "result")
    public void afterReturningAdvice(JoinPoint joinPoint, Object result) {
        logger.info("4. @AfterReturning - After method returns with result: {}", result);
    }
    
    @AfterThrowing(pointcut = "executeMethod()", throwing = "ex")
    public void afterThrowingAdvice(JoinPoint joinPoint, Exception ex) {
        logger.info("4. @AfterThrowing - After method throws exception: {}", ex.getMessage());
    }
    
    @Around("executeMethod()")
    public Object aroundAdvice(ProceedingJoinPoint joinPoint) throws Throwable {
        logger.info("2. @Around - Before proceeding to method");
        Object result = null;
        try {
            result = joinPoint.proceed();
            logger.info("3. @Around - After method proceeds successfully");
        } catch (Exception e) {
            logger.info("3. @Around - Exception caught: {}", e.getMessage());
            throw e;
        }
        return result;
    }
}

@Service
public class DemoService {
    public String execute(boolean throwException) {
        if (throwException) {
            throw new RuntimeException("Intentional exception");
        }
        return "Success";
    }
}
```

**正常执行输出**：
```
1. @Before - Before method execution
2. @Around - Before proceeding to method
3. @Around - After method proceeds successfully
4. @AfterReturning - After method returns with result: Success
5. @After - After method execution (regardless of outcome)
```

**异常执行输出**：
```
1. @Before - Before method execution
2. @Around - Before proceeding to method
3. @Around - Exception caught: Intentional exception
4. @AfterThrowing - After method throws exception: Intentional exception
5. @After - After method execution (regardless of outcome)
```

🔍 **深度反思**：通知执行顺序为：@Before → @Around(proceed前) → 目标方法 → @Around(proceed后) → @AfterReturning/@AfterThrowing → @After。@Around最灵活，可以控制目标方法的执行流程。

💬 **追问预判**：
- Q: @After和@AfterReturning的区别？  
  A: @After无论方法是否抛出异常都会执行，@AfterReturning只有方法正常返回时才执行。
- Q: @AfterThrowing可以捕获特定类型的异常吗？  
  A: 可以，通过throwing属性指定异常类型，只捕获该类型及其子类的异常。

---

### 🟡 中级用法题
#### 题目2：多个切面的执行顺序

**问题描述**：创建多个切面，展示切面之间的执行顺序。

**✅ 标准答案**：

```java
// 切面1：日志切面（优先级1）
@Aspect
@Component
@Order(1)
public class LoggingAspect {
    
    @Before("execution(* com.example.service.*.*(..))")
    public void logBefore(JoinPoint joinPoint) {
        System.out.println("[Order 1] Logging - Before: " + joinPoint.getSignature().getName());
    }
    
    @After("execution(* com.example.service.*.*(..))")
    public void logAfter(JoinPoint joinPoint) {
        System.out.println("[Order 1] Logging - After: " + joinPoint.getSignature().getName());
    }
}

// 切面2：性能监控切面（优先级2）
@Aspect
@Component
@Order(2)
public class PerformanceAspect {
    
    @Before("execution(* com.example.service.*.*(..))")
    public void monitorBefore(JoinPoint joinPoint) {
        System.out.println("[Order 2] Performance - Before: " + joinPoint.getSignature().getName());
    }
    
    @After("execution(* com.example.service.*.*(..))")
    public void monitorAfter(JoinPoint joinPoint) {
        System.out.println("[Order 2] Performance - After: " + joinPoint.getSignature().getName());
    }
}

// 切面3：安全切面（优先级3）
@Aspect
@Component
@Order(3)
public class SecurityAspect {
    
    @Before("execution(* com.example.service.*.*(..))")
    public void securityBefore(JoinPoint joinPoint) {
        System.out.println("[Order 3] Security - Before: " + joinPoint.getSignature().getName());
    }
    
    @After("execution(* com.example.service.*.*(..))")
    public void securityAfter(JoinPoint joinPoint) {
        System.out.println("[Order 3] Security - After: " + joinPoint.getSignature().getName());
    }
}
```

**输出结果**：
```
[Order 1] Logging - Before: execute
[Order 2] Performance - Before: execute
[Order 3] Security - Before: execute
Target method executed
[Order 3] Security - After: execute
[Order 2] Performance - After: execute
[Order 1] Logging - After: execute
```

🔍 **深度反思**：多个切面的执行顺序遵循"先进后出"原则：@Order数值越小优先级越高，前置通知按Order从小到大执行，后置通知按Order从大到小执行（类似栈结构）。

💬 **追问预判**：
- Q: @Order注解的数值范围是多少？  
  A: 整数范围，数值越小优先级越高，默认优先级最低（Integer.MAX_VALUE）。
- Q: 如果两个切面的@Order值相同会怎样？  
  A: 执行顺序不确定，取决于Bean的注册顺序。

---

### 🔴 高级用法题
#### 题目3：嵌套切面与异常传播

**问题描述**：分析嵌套调用时AOP通知的执行顺序和异常传播行为。

**✅ 标准答案**：

```java
@Aspect
@Component