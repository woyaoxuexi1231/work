package com.example.springqa.Q01_BeanLifecycle;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * <h1>Bean 生命周期 — 每阶段因何而生</h1>
 *
 * <p>下面每个方法都标注了：</p>
 * <ul>
 *   <li>Spring 哪个版本首次出现</li>
 *   <li>当时引入了什么新特性，需要什么底层能力来支撑</li>
 *   <li>所以设计了这个扩展点</li>
 *   <li>今天哪个具体组件在用它做什么</li>
 * </ul>
 *
 * <pre>
 * 时间线速览：
 *   Spring 1.0  → 构造器 + setter + InitializingBean + DisposableBean
 *   Spring 2.0  → BeanNameAware / BeanFactoryAware / ApplicationContextAware
 *   Spring 2.5  → @PostConstruct / @PreDestroy (JSR-250) + BeanPostProcessor 爆发
 *   Spring 3.0  → @Configuration + BeanFactoryPostProcessor 成熟
 *   Spring 3.2  → @Async / @Scheduled 基于 BPP
 *   Spring Boot → 自动装配（AutoConfigurationImportSelector 本身也是 BPP 的变体）
 * </pre>
 */
public class Q01MyBean implements BeanNameAware, BeanFactoryAware,
        ApplicationContextAware, InitializingBean, DisposableBean {

    private String name;

    // ============================================================
    // [1] 构造器 — Spring 1.0，无替代方案
    // ============================================================
    /**
     * <b>[1] Spring 1.0 — Java 反射就是你的一切</b>
     *
     * <p>2004 年，Rod Johnson 在《Expert One-on-One J2EE Development without EJB》
     * 中提出了一个激进的想法：用普通的 Java 对象（POJO）替代 EJB。
     * 这本书的代码后来成了 Spring Framework。</p>
     *
     * <p>当时的基础设施极其简陋——没有 @Autowired，没有 @Component，
     * 所有的 Bean 都在 XML 里用 &lt;bean class="..."&gt; 声明。
     * Spring 唯一能做的就是通过反射调用构造器来创建对象。</p>
     *
     * <p>没有注解，没有自动扫描——XML 是唯一配置源。</p>
     */
    public Q01MyBean(String name) {
        this.name = name;
        System.out.println("  [1] 构造器    ← Spring 1.0，java.lang.reflect.Constructor 就是全部");
    }

    // ============================================================
    // [2] setter — Spring 1.0，XML <property> 的引擎
    // ============================================================
    /**
     * <b>[2] Spring 1.0 — XML 时代的属性注入</b>
     *
     * <p>2004 年的配置长这样：</p>
     * <pre>
     * &lt;bean id="myBean" class="..."&gt;
     *     &lt;property name="name" value="hello"/&gt;
     * &lt;/bean&gt;
     * </pre>
     *
     * <p>Spring 拿到 &lt;property&gt; 标签后，需要一种机制把值塞进对象。
     * 当时只有 JavaBean 规范的 setter 方法可用——
     * {@code Introspector.getBeanInfo()} → 找到 setXxx() → {@code Method.invoke()}。
     * 这就是属性填充的起源。</p>
     *
     * <p><b>后来（Spring 2.5）：</b>@Autowired 引入后，这个阶段被大幅增强。
     * {@code AutowiredAnnotationBeanPostProcessor} 不再满足于 XML 中的
     * property 值——它直接扫描字段上的 @Autowired 注解，
     * 从容器中查找匹配的 Bean，通过 {@code field.set(bean, dependency)} 注入。
     * XML 的 setter 注入和注解的字段注入在同一个阶段完成。</p>
     */
    public void setName(String name) {
        this.name = name;
        System.out.println("  [2] setter     ← Spring 1.0 XML <property>，后来 2.5 加入 @Autowired");
    }

    // ============================================================
    // [3] BeanNameAware — Spring 2.0，Bean 开始"有身份"
    // ============================================================
    /**
     * <b>[3] Spring 2.0 (2006) — Aware 接口族的开端</b>
     *
     * <p><b>催生它的特性：</b>Spring 2.0 引入了 Scope（singleton/prototype/
     * request/session），Bean 不再只是"全局唯一的单例"。
     * 同时引入了 AOP（基于 AspectJ 切入点表达式）。</p>
     *
     * <p>这两个特性都需要 Bean 知道自己的身份——Scope 需要知道 Bean 的名字
     * 来在不同作用域之间协调；AOP 需要知道 Bean 的名字来精确匹配切入点。</p>
     *
     * <p>于是有了 BeanNameAware——Aware 家族中最简单、最早的一个。
     * 它是 Spring 从"纯被动注入"转向"让 Bean 感知容器"的起点。</p>
     */
    @Override
    public void setBeanName(String name) {
        System.out.println("  [3] BeanNameAware    ← 催生者: Spring 2.0 Scope + AOP");
    }

    // ============================================================
    // [4] BeanFactoryAware — Spring 2.0，需要"手动获取 Bean"
    // ============================================================
    /**
     * <b>[4] Spring 2.0 (2006) — 让 Bean 能主动获取依赖</b>
     *
     * <p><b>催生它的特性：</b>Spring 2.0 的 AOP 引入了"代理"概念。
     * 但代理带来了新问题——如果一个 Bean 需要在运行时动态决定使用哪个
     * 策略对象，静态的 XML 注入做不到。唯一的办法是让 Bean 拿到
     * BeanFactory 的引用，手动调用 {@code getBean()}。</p>
     *
     * <p>这就是 BeanFactoryAware 的诞生背景——不是所有依赖关系
     * 都能在配置文件中写死。</p>
     */
    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        System.out.println("  [4] BeanFactoryAware ← 催生者: Spring 2.0 动态代理 + FactoryBean");
    }

    // ============================================================
    // [5] ApplicationContextAware — Spring 2.0，超越纯 DI
    // ============================================================
    /**
     * <b>[5] Spring 2.0 (2006) — ApplicationContext 的功能远大于 BeanFactory</b>
     *
     * <p><b>催生它的特性：</b>Spring 2.0 的 ApplicationContext 新增了：</p>
     * <ul>
     *   <li>事件发布机制（ApplicationEvent + ApplicationListener）</li>
     *   <li>国际化消息（MessageSource）</li>
     *   <li>资源加载（ResourceLoader + ResourcePatternResolver）</li>
     * </ul>
     *
     * <p>这些能力都在 ApplicationContext 上，不在 BeanFactory 上。
     * 如果一个 Bean 需要发布事件（比如"订单已创建"），它必须拿到
     * ApplicationContext 引用——这就是 ApplicationContextAware 的作用。</p>
     *
     * <p><b>注意：</b>这个接口是大而全的——拿一次就能调用上面所有能力。
     * 后来为了遵循"最小权限原则"，又衍生出了更细粒度的：
     * EnvironmentAware、ApplicationEventPublisherAware、MessageSourceAware 等，
     * 让 Bean 只暴露自己真正需要的部分。</p>
     */
    @Override
    public void setApplicationContext(ApplicationContext ctx) {
        System.out.println("  [5] ApplicationContextAware ← 催生者: Spring 2.0 事件/国际化/资源加载");
    }

    // ============================================================
    // [6] BeanPostProcessor.before — 见 Q01Config
    // ============================================================

    // ============================================================
    // [7a] @PostConstruct — Spring 2.5，JSR-250 标准的胜利
    // ============================================================
    /**
     * <b>[7a] Spring 2.5 (2007) — Java 标准注解取代 Spring 专有接口</b>
     *
     * <p><b>催生它的特性：</b>2006 年 Java EE 5 发布了 JSR-250 (Common Annotations)，
     * 定义了 @PostConstruct、@PreDestroy、@Resource 等标准注解。
     * 这意味着一个用 @PostConstruct 标注初始化方法的 Bean，
     * 可以在 Spring、EJB 3、Guice 等任何兼容容器中运行。</p>
     *
     * <p>Spring 2.5 立刻跟进了这个标准——因为它符合 Spring 一贯的哲学：
     * <b>你的代码不应该和 Spring 耦合。</b>用 @PostConstruct 替代
     * InitializingBean 意味着你的类不需要 import 任何 Spring 的包。</p>
     *
     * <p><b>底层实现：</b>{@code CommonAnnotationBeanPostProcessor}
     * （继承自 InitDestroyAnnotationBeanPostProcessor）。
     * 它在 BPP 的 before 阶段扫描 Bean 的所有方法，
     * 找到 @PostConstruct 标注的 → 反射调用。</p>
     */
    @PostConstruct
    public void postConstruct() {
        System.out.println("  [7a] @PostConstruct  ← 催生者: Spring 2.5 + JSR-250 标准化");
        this.name = "PostConstruct 修改后";
    }

    // ============================================================
    // [7b] InitializingBean — Spring 1.0，最早的回调机制
    // ============================================================
    /**
     * <b>[7b] Spring 1.0 (2004) — "所有属性注入完毕后，我要做初始化"</b>
     *
     * <p><b>催生它的场景：</b>JDBC 连接池。你必须在 XML 中指定
     * url、username、password、maxPoolSize……但这些属性单独拿出来
     * 都没有意义。连接池需要等<b>所有属性</b>都注入完毕后，
     * 才能调用 {@code init()} 创建真正的 Connection 对象。</p>
     *
     * <p>于是 Spring 设计了 InitializingBean 接口和 init-method 属性——
     * 它们保证在所有 setter 调用完毕后才执行。</p>
     *
     * <p><b>缺陷：</b>与 Spring 强耦合，你的类必须实现 Spring 的接口。</p>
     */
    @Override
    public void afterPropertiesSet() {
        System.out.println("  [7b] InitializingBean  ← 催生者: Spring 1.0 连接池初始化场景");
    }

    // ============================================================
    // [7c] init-method — Spring 1.0，"第三方类我也要管"
    // ============================================================
    /**
     * <b>[7c] Spring 1.0 (2004) — 无侵入初始化</b>
     *
     * <p><b>催生它的场景：</b>DruidDataSource 是一个第三方 JAR 里的类，
     * 你不能改它的源码让它实现 InitializingBean。
     * 但它有一个 {@code init()} 方法需要在属性注入后调用。</p>
     *
     * <p>Spring 的解决方案：在 XML 中写
     * {@code <bean class="DruidDataSource" init-method="init"/>}，
     * Spring 在属性注入完毕后通过反射调用 init()。
     * 目标类完全不知道自己被 Spring 管理——这就是"无侵入"。</p>
     *
     * <p>今天用 @Bean(initMethod="xxx") 达到同样效果。</p>
     */
    public void customInit() {
        System.out.println("  [7c] init-method    ← 催生者: Spring 1.0 第三方连接池 DruidDataSource");
    }

    // ============================================================
    // [8] BeanPostProcessor.after — 见 Q01Config
    // ============================================================

    // ============================================================
    // [10a] @PreDestroy — Spring 2.5，JSR-250
    // ============================================================
    @PreDestroy
    public void preDestroy() {
        System.out.println("  [10a] @PreDestroy   ← JSR-250 标准");
    }

    // ============================================================
    // [10b] DisposableBean — Spring 1.0
    // ============================================================
    /**
     * <b>[10b] Spring 1.0 — 初始化对称面：优雅关闭</b>
     *
     * <p><b>催生它的场景：</b>连接池不光要初始化，还要关闭——
     * 否则每次重启应用都会泄露数据库连接。
     * 与 InitializingBean 对称，但触发时机完全不同——
     * 不是 Bean 创建时，而是容器调用 {@code close()} 时。</p>
     */
    @Override
    public void destroy() {
        System.out.println("  [10b] DisposableBean  ← 催生者: Spring 1.0 连接池关闭场景");
    }

    // ============================================================
    // [10c] destroy-method — Spring 1.0
    // ============================================================
    /**
     * <b>[10c] Spring 1.0 — 无侵入销毁（与 init-method 对称）</b>
     *
     * <p>DruidDataSource 的 {@code close()} 不需要实现 DisposableBean，
     * 在 XML 中写 {@code destroy-method="close"} 即可。</p>
     */
    public void customDestroy() {
        System.out.println("  [10c] destroy-method ← 第三方连接池的 close()");
    }

    public String getName() { return name; }
}
