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
 * <h1>Bean 生命周期 Demo Bean</h1>
 *
 * <p>每个方法只回答一个问题：<b>Spring 设计者设计这个方法的目的是什么？</b></p>
 */
public class Q01MyBean implements BeanNameAware, BeanFactoryAware,
        ApplicationContextAware, InitializingBean, DisposableBean {

    private String name;

    // [1] 构造器 ————————————————————————————————————————
    // 设计目的：提供对象创建的入口。
    // Java 中只有构造器能创建对象实例，Spring 无法绕开。
    // Spring 通过反射选择构造器（单构造器直接用，多构造器选 @Autowired 标记的）。
    // 注意：此时依赖尚未注入，@Autowired 字段全部为 null——
    // 所以构造器中不能使用任何注入的依赖，只能做最简单的赋值。
    public Q01MyBean(String name) {
        this.name = name;
        System.out.println("  [1] 构造器 — 设计目的: 对象实例化的唯一入口");
    }

    // [2] setter / 属性填充 ——————————————————————————————
    // 设计目的：让配置值和依赖能在对象创建之后、初始化之前被注入。
    // 为什么不在构造器一步完成？因为 Spring 需要支持按类型/按名称的
    // 灵活匹配——构造器参数必须在调用时就全部确定，而 setter 注入
    // 可以先把对象创建出来，再逐个解析依赖。
    public void setName(String name) {
        this.name = name;
        System.out.println("  [2] 属性填充 — 设计目的: 对象创建后再注入依赖，支持灵活匹配");
    }

    // [3] BeanNameAware ——————————————————————————————————
    // 设计目的：让 Bean 知道自己在容器中的名字（id）。
    // 你可能会想"为什么要知道自己的名字？"——当 Bean 需要把自己
    // 注册到外部系统时（如 JMX 的 ObjectName、日志的 MDC 前缀、
    // 消息队列的 consumer group），名字是天然的唯一标识。
    @Override
    public void setBeanName(String name) {
        System.out.println("  [3] BeanNameAware — 设计目的: 让 Bean 知道自己在容器中的标识");
    }

    // [4] BeanFactoryAware ———————————————————————————————
    // 设计目的：让 Bean 获得"主动获取依赖"的能力。
    // 正常情况下 Bean 被动接受注入——容器给你什么你就拿什么。
    // 但策略模式中一个路由器 Bean 需要根据运行时参数调用
    // getBean("strategyA") 或 getBean("strategyB")——
    // 这种动态选择必须手动从容器获取。
    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        System.out.println("  [4] BeanFactoryAware — 设计目的: 让 Bean 能主动从容器获取依赖");
    }

    // [5] ApplicationContextAware ————————————————————————
    // 设计目的：让 Bean 获得比纯 DI 更高级的容器能力——
    // 发布事件、读取国际化消息、加载文件资源。
    // 这些能力不属于"依赖注入"的范畴——你不能 @Autowired 一个事件。
    // 必须拿到容器引用才能调用 ctx.publishEvent()。
    // ★ 注意：这个回调不在 [4] 的同级，而是通过 BPP 实现的——
    // 因为 ApplicationContext 在 BeanFactory 准备好之后才存在。
    @Override
    public void setApplicationContext(ApplicationContext ctx) {
        System.out.println("  [5] ApplicationContextAware — 设计目的: 让 Bean 获得事件/国际化/资源能力");
    }

    // [7a] @PostConstruct ————————————————————————————————
    // 设计目的：提供一种"与框架解耦"的初始化方式。
    // 你的类只需要 import javax.annotation.PostConstruct，
    // 不需要 import 任何 Spring 的包。这意味着这个类可以在
    // Spring、EJB 3、Guice 等任何兼容 JSR-250 的容器中工作。
    @PostConstruct
    public void postConstruct() {
        this.name = "PostConstruct 修改后";
        System.out.println("  [7a] @PostConstruct — 设计目的: 与框架解耦的初始化（JSR-250 标准）");
    }

    // [7b] InitializingBean ——————————————————————————————
    // 设计目的：提供"所有属性都设置完毕"的时刻，让 Bean 执行
    // 依赖这些属性的初始化逻辑。这是 Spring 最早的初始化回调。
    // 典型场景：连接池在 url/username/password 都注入后，才能创建 Connection。
    // 缺点：与 Spring 强耦合——你的代码必须 import Spring 的接口。
    @Override
    public void afterPropertiesSet() {
        System.out.println("  [7b] InitializingBean — 设计目的: 属性全部注入后的初始化时机");
    }

    // [7c] init-method ———————————————————————————————————
    // 设计目的：让第三方类也能被 Spring 初始化。
    // 如果 Bean 来自第三方 JAR（如 DruidDataSource），你无法修改
    // 它的源码来实现 InitializingBean。但你可以通过 XML 或 @Bean
    // 指定它的任意 public 方法作为初始化回调——完全无侵入。
    public void customInit() {
        System.out.println("  [7c] init-method — 设计目的: 第三方类也能用的无侵入初始化");
    }

    // [10a] @PreDestroy ——————————————————————————————————
    // 设计目的：让 Bean 在销毁前释放资源。与 @PostConstruct 对称，
    // 同样是 JSR-250 标准，与框架解耦。
    @PreDestroy
    public void preDestroy() {
        System.out.println("  [10a] @PreDestroy — 设计目的: 与框架解耦的销毁回调（JSR-250 标准）");
    }

    // [10b] DisposableBean ———————————————————————————————
    // 设计目的：提供容器关闭时的资源释放时机。
    // 与 InitializingBean 对称——最早的销毁回调。
    // 典型场景：连接池调用 close() 释放数据库连接。
    @Override
    public void destroy() {
        System.out.println("  [10b] DisposableBean — 设计目的: 容器关闭时释放外部资源");
    }

    // [10c] destroy-method ———————————————————————————————
    // 设计目的：第三方类的无侵入销毁。与 init-method 对称——
    // 不需要修改源码，通过配置指定清理方法。
    public void customDestroy() {
        System.out.println("  [10c] destroy-method — 设计目的: 第三方类也能用的无侵入销毁");
    }

    public String getName() { return name; }
}
