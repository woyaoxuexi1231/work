package com.example.springqa.Q01_BeanLifecycle;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * <h1>Q1：Bean 生命周期 — 从实例化到销毁的完整流程</h1>
 *
 * <h2>面试点</h2>
 * <ul>
 *   <li>BeanFactory vs ApplicationContext 加载 Bean 的区别</li>
 *   <li>Bean 生命周期各阶段 + 扩展点</li>
 *   <li>BeanPostProcessor 的 before/after 触发时机</li>
 *   <li>如何在属性设置之前修改属性值</li>
 * </ul>
 *
 * <h2>生命周期全景图</h2>
 * <pre>
 * 1. 实例化 (Instantiation)
 *    ↓
 * 2. 属性填充 (Populate Properties)
 *    ↓
 * 3. BeanNameAware.setBeanName()
 *    ↓
 * 4. BeanFactoryAware.setBeanFactory()
 *    ↓
 * 5. ApplicationContextAware.setApplicationContext()
 *    ↓
 * 6. BeanPostProcessor.postProcessBeforeInitialization()  ← 可以在初始化前修改
 *    ↓
 * 7. @PostConstruct / InitializingBean.afterPropertiesSet() / init-method
 *    ↓
 * 8. BeanPostProcessor.postProcessAfterInitialization()   ← AOP 代理在此生成！
 *    ↓
 * 9. Bean 就绪，放入单例池（singletonObjects 一级缓存）
 *    ↓  (容器关闭时)
 * 10. @PreDestroy / DisposableBean.destroy() / destroy-method
 * </pre>
 *
 * <h2>Spring 为什么这样设计？</h2>
 *
 * <h3>为什么分层这么多？</h3>
 * <p>这是"开闭原则"的极致体现。每层是一个扩展点，框架使用者可以在不修改
 * Spring 源码的前提下，在 Bean 生命周期的任意阶段插入自定义逻辑。
 * 如果把这些合并成一个方法，扩展性会极差。</p>
 *
 * <h3>为什么 BeanPostProcessor 分 before 和 after？</h3>
 * <p>before 在 init-method 之前触发——适合做"前置加工"（如属性校验）；
 * after 在 init-method 之后触发——适合做"后置代理"（如 AOP 生成代理对象）。
 * 这个时间差至关重要：before 时 Bean 还没完成初始化，after 时已经是一个"完整"的 Bean。</p>
 *
 * <h3>BeanFactory vs ApplicationContext 的核心区别</h3>
 * <ul>
 *   <li>BeanFactory：懒加载，getBean() 时才创建 Bean。不自动注册 BeanPostProcessor。</li>
 *   <li>ApplicationContext：预加载单例，容器启动时就创建所有单例 Bean。
 *       自动注册 BeanFactoryPostProcessor 和 BeanPostProcessor。</li>
 * </ul>
 * <p>Spring 这样设计的理由：ApplicationContext 是"生产级"容器，
 * 启动时多花点时间把所有 Bean 初始化好，运行时就没有延迟。</p>
 *
 * <h3>如果想在属性设置之前修改属性值，用哪个扩展点？</h3>
 * <p>答案：<b>BeanFactoryPostProcessor</b>（不是 BeanPostProcessor！）。
 * BeanFactoryPostProcessor 在 Bean 实例化之前执行，可以修改 BeanDefinition
 * 中的属性值。BeanPostProcessor 触发时 Bean 已经实例化并填充完属性了，
 * 只能修改对象上的属性值，不能修改 BeanDefinition。</p>
 *
 * @author Spring Interview QA
 */
public class BeanLifecycleDemo {

    // ================================================================
    // 运行本 Demo: 直接执行 main 方法，观察控制台输出的生命周期顺序
    // ================================================================
    public static void main(String[] args) {
        System.out.println("========== Q1: Bean 生命周期 Demo ==========\n");

        /*
         * ApplicationContext：启动时就创建所有单例 Bean。
         * 你会看到生命周期回调在启动阶段就全部打印出来了——
         * 这就是 ApplicationContext 的"预加载"特性。
         */
        AnnotationConfigApplicationContext ctx =
                new AnnotationConfigApplicationContext(DemoConfig.class);

        System.out.println("\n>>> 容器启动完成，获取 MyBean...");
        MyBean bean = ctx.getBean(MyBean.class);
        System.out.println(">>> 获取到的 Bean: " + bean.getName());

        System.out.println("\n>>> 关闭容器...");
        ctx.close();
        System.out.println("\n========== Demo 结束 ==========");
    }

    // ================================================================
    // 配置类
    // ================================================================
    @Configuration
    static class DemoConfig {

        @Bean(initMethod = "customInit", destroyMethod = "customDestroy")
        public MyBean myBean() {
            /*
             * 【设计意图】
             * 为什么 @Bean 可以有 initMethod / destroyMethod？
             * 因为不是所有 Bean 都能加 @PostConstruct（比如第三方库的类）。
             * Spring 提供了三种初始化回调方式，优先级从高到低：
             * 1. @PostConstruct / @PreDestroy    （JSR-250 标准注解）
             * 2. InitializingBean / DisposableBean（Spring 接口）
             * 3. @Bean(initMethod / destroyMethod) （配置层指定）
             *
             * 三种方式可以同时存在，执行顺序就是上面的 1→2→3。
             * Spring 这样设计是为了兼容不同场景：
             * - 自己写的类用 @PostConstruct 最简洁
             * - 需要感知 Spring 内部状态的用 *Aware 接口
             * - 第三方类无法修改源码，用 @Bean 属性指定
             */
            return new MyBean("初始名称");
        }

        /**
         * 【关键】这个 BeanPostProcessor 会在每个 Bean 初始化前后被调用。
         * Spring 内部大量使用了这个机制，比如：
         * - AutowiredAnnotationBeanPostProcessor 处理 @Autowired
         * - AnnotationAwareAspectJAutoProxyCreator 生成 AOP 代理
         *
         * 为什么设计成对所有 Bean 生效？因为这样框架才能"无侵入"地
         * 对所有 Bean 做统一处理，而不需要每个 Bean 自己声明。
         */
        @Bean
        public static BeanPostProcessor loggingBeanPostProcessor() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessBeforeInitialization(Object bean, String beanName) {
                    if (bean instanceof MyBean) {
                        System.out.println("  [6] BeanPostProcessor.before: beanName=" + beanName
                                + ", name=" + ((MyBean) bean).getName());
                        /*
                         * 【设计意图】
                         * before 阶段 Bean 属性已经填充完了，但 init 方法还没调用。
                         * 适合做的事情：属性校验、修改属性值、包装对象（不是代理）。
                         * 为什么不在 after 阶段做？因为 after 阶段通常保留给 AOP 代理生成。
                         */
                    }
                    return bean;
                }

                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (bean instanceof MyBean) {
                        System.out.println("  [8] BeanPostProcessor.after: beanName=" + beanName
                                + ", name=" + ((MyBean) bean).getName());
                        /*
                         * 【设计意图 — AOP 代理】
                         * Spring AOP 的代理就是在 after 阶段生成的！
                         * 此时 Bean 已经完全初始化（@PostConstruct、afterPropertiesSet、
                         * init-method 都已执行），是一个"功能完整"的 Bean。
                         * 在这之后生成代理，代理对象就能完全替代原始 Bean。
                         *
                         * 这就是为什么循环依赖需要三级缓存——
                         * 如果 A 依赖 B，B 依赖 A，且两者都需要 AOP 代理，
                         * 那么 A 的"早期引用"必须在 before 和 after 之间暴露出去，
                         * 否则 B 拿到的是未代理的 A。详见 Q02。
                         */
                    }
                    return bean;
                }
            };
        }

        /**
         * BeanFactoryPostProcessor：在 Bean 实例化之前执行。
         * 可以修改 BeanDefinition 的元数据（scope、lazy、property values 等）。
         *
         * 【面试关键】
         * "如果我想在 Bean 属性设置之前修改某个属性值，应该用哪个扩展点？"
         * → 答案就是这个 BeanFactoryPostProcessor。
         * 它操作的是 BeanDefinition（元数据），不是 Bean 实例。
         */
        @Bean
        public static BeanFactoryPostProcessor propertyModifier() {
            return (ConfigurableListableBeanFactory factory) -> {
                System.out.println("  [0] BeanFactoryPostProcessor: 在 Bean 实例化之前执行");
                /*
                 * 【设计意图】
                 * 为什么需要这个扩展点？
                 * 有些配置信息可能在运行时才能确定（如从配置中心动态拉取），
                 * 需要在 Bean 创建之前修改 BeanDefinition 的属性。
                 *
                 * Spring 内部的 PropertySourcesPlaceholderConfigurer 就是
                 * 一个 BeanFactoryPostProcessor——它负责把 ${...} 占位符
                 * 替换成真实的配置值。这个处理必须在 Bean 实例化之前完成。
                 */
            };
        }
    }

    // ================================================================
    // 演示 Bean — 实现了所有感知接口
    // ================================================================
    static class MyBean implements BeanNameAware, BeanFactoryAware,
            ApplicationContextAware, InitializingBean, DisposableBean {

        private String name;
        private String beanName;
        private BeanFactory beanFactory;
        private ApplicationContext applicationContext;

        public MyBean(String name) {
            this.name = name;
            System.out.println("  [1] 构造器调用: name=" + name);
            /*
             * 【设计意图】
             * Spring 通过反射调用构造器（或工厂方法）来实例化 Bean。
             * 此时 Bean 的属性还是"原始值"——@Autowired、@Value 都还没注入。
             * 为什么不让构造器就完成所有注入？因为构造器注入需要参数匹配，
             * 而字段注入/setter 注入可以灵活地按名称/类型匹配。
             */
        }

        // ---------- Aware 接口链 ----------
        // 设计意图：Aware 接口是 Spring 的"回射"机制——
        // 让 Bean 能够感知到容器的存在，获得容器的基础设施引用。
        // 为什么不用自动注入？因为 Aware 是标准接口，任何实现了该接口的
        // Bean 都会自动收到回调，不需要加 @Autowired —— 更简洁、更"无侵入"。

        @Override
        public void setBeanName(String name) {
            this.beanName = name;
            System.out.println("  [3] BeanNameAware.setBeanName: " + name);
        }

        @Override
        public void setBeanFactory(BeanFactory beanFactory) {
            this.beanFactory = beanFactory;
            System.out.println("  [4] BeanFactoryAware.setBeanFactory");
        }

        @Override
        public void setApplicationContext(ApplicationContext ctx) {
            this.applicationContext = ctx;
            System.out.println("  [5] ApplicationContextAware.setApplicationContext");
        }

        // ---------- 初始化回调（三种方式） ----------

        @PostConstruct
        public void postConstruct() {
            System.out.println("  [7a] @PostConstruct: name=" + name);
            this.name = "PostConstruct 修改后的名称";
        }

        @Override
        public void afterPropertiesSet() {
            System.out.println("  [7b] InitializingBean.afterPropertiesSet: name=" + name);
        }

        /** @Bean(initMethod="customInit") 指定的方法 */
        public void customInit() {
            System.out.println("  [7c] init-method (customInit): name=" + name);
        }

        // ---------- 销毁回调（三种方式） ----------

        @PreDestroy
        public void preDestroy() {
            System.out.println("  [10a] @PreDestroy");
        }

        @Override
        public void destroy() {
            System.out.println("  [10b] DisposableBean.destroy");
        }

        /** @Bean(destroyMethod="customDestroy") 指定的方法 */
        public void customDestroy() {
            System.out.println("  [10c] destroy-method (customDestroy)");
        }

        // ---------- 业务方法 ----------

        public String getName() {
            return name;
        }

        public void setName(String name) {
            System.out.println("  [2] setter 注入: name=" + name);
            this.name = name;
        }
    }
}
