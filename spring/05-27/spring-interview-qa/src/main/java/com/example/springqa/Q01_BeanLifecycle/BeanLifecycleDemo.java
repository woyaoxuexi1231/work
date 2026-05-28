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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * <h1>Q1：Bean 生命周期 — 从实例化到销毁的完整流程</h1>
 *
 * <h2>生命周期全景（10 个阶段）</h2>
 * <pre>
 * 1. 实例化 (Instantiation)
 * 2. 属性填充 (Populate Properties)
 * 3. BeanNameAware.setBeanName()
 * 4. BeanFactoryAware.setBeanFactory()
 * 5. ApplicationContextAware.setApplicationContext()
 * 6. BeanPostProcessor.postProcessBeforeInitialization()
 * 7. @PostConstruct / InitializingBean.afterPropertiesSet() / init-method
 * 8. BeanPostProcessor.postProcessAfterInitialization() ← AOP 代理在此生成!
 * 9. Bean 就绪，放入单例池
 * 10. 容器关闭 → @PreDestroy / DisposableBean.destroy() / destroy-method
 * </pre>
 *
 * <h2>Spring 为什么分层这么多？</h2>
 * <p>开闭原则的极致体现——每层是一个扩展点，使用者可在任意阶段插入逻辑。
 * BeanPostProcessor 分 before/after：before 做属性校验，after 留给 AOP 代理生成。
 * BeanFactoryPostProcessor 在实例化前修改 BeanDefinition，是"属性设置前修改"的唯一入口。</p>
 */
@Component
public class BeanLifecycleDemo {

    private final ApplicationContext ctx;

    public BeanLifecycleDemo(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    /**
     * 返回演示文本。启动时 Bean 已由 Spring 容器自动创建，
     * 生命周期回调已全部执行完毕（在启动日志中可见）。
     */
    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Q01: Bean 生命周期 ===\n\n");

        sb.append("MyBean 已由 Spring 容器在启动时创建。\n");
        sb.append("查看启动日志可看到完整的生命周期回调顺序：\n\n");

        sb.append("  [0] BeanFactoryPostProcessor      ← 在 Bean 实例化之前执行\n");
        sb.append("  [1] 构造器调用                      ← 实例化\n");
        sb.append("  [2] setter 注入                     ← 属性填充\n");
        sb.append("  [3] BeanNameAware.setBeanName()     ← 感知自己的名字\n");
        sb.append("  [4] BeanFactoryAware.setBeanFactory()← 感知 BeanFactory\n");
        sb.append("  [5] ApplicationContextAware         ← 感知 ApplicationContext\n");
        sb.append("  [6] BeanPostProcessor.before        ← 初始化前处理\n");
        sb.append("  [7] @PostConstruct / afterPropertiesSet / init-method\n");
        sb.append("  [8] BeanPostProcessor.after         ← AOP 代理在此生成！\n");
        sb.append("  [9] Bean 就绪 → 放入 singletonObjects\n\n");

        MyBean bean = ctx.getBean(MyBean.class);
        sb.append("当前 Bean 的 name 属性: ").append(bean.getName()).append("\n\n");

        sb.append("【面试关键】\n");
        sb.append("Q: 想在属性设置之前修改属性值，用哪个扩展点？\n");
        sb.append("A: BeanFactoryPostProcessor（不是 BeanPostProcessor！）\n");
        sb.append("   因为 BeanFactoryPostProcessor 操作的是 BeanDefinition（元数据），\n");
        sb.append("   在 Bean 实例化之前执行。BeanPostProcessor 触发时属性已填充完毕。\n");

        return sb.toString();
    }

    // ================================================================
    @Configuration
    static class DemoConfig {

        @Bean(initMethod = "customInit", destroyMethod = "customDestroy")
        public MyBean myBean() {
            return new MyBean("初始名称");
        }

        @Bean
        public static BeanPostProcessor loggingBeanPostProcessor() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessBeforeInitialization(Object bean, String beanName) {
                    if (bean instanceof MyBean) {
                        System.out.println("  [6] BeanPostProcessor.before: " + beanName);
                    }
                    return bean;
                }

                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (bean instanceof MyBean) {
                        System.out.println("  [8] BeanPostProcessor.after: " + beanName
                                + "（AOP 代理在此生成！）");
                    }
                    return bean;
                }
            };
        }

        @Bean
        public static BeanFactoryPostProcessor propertyModifier() {
            return (ConfigurableListableBeanFactory factory) -> {
                System.out.println("  [0] BeanFactoryPostProcessor: 在 Bean 实例化之前执行");
            };
        }
    }

    // ================================================================
    static class MyBean implements BeanNameAware, BeanFactoryAware,
            ApplicationContextAware, InitializingBean, DisposableBean {

        private String name;

        public MyBean(String name) {
            this.name = name;
            System.out.println("  [1] 构造器调用: name=" + name);
        }

        public void setName(String name) {
            System.out.println("  [2] setter 注入: name=" + name);
            this.name = name;
        }

        @Override public void setBeanName(String name) {
            System.out.println("  [3] BeanNameAware: " + name);
        }

        @Override public void setBeanFactory(BeanFactory beanFactory) {
            System.out.println("  [4] BeanFactoryAware");
        }

        @Override public void setApplicationContext(ApplicationContext ctx) {
            System.out.println("  [5] ApplicationContextAware");
        }

        @PostConstruct
        public void postConstruct() {
            System.out.println("  [7a] @PostConstruct: name=" + name);
            this.name = "PostConstruct 修改后";
        }

        @Override public void afterPropertiesSet() {
            System.out.println("  [7b] InitializingBean.afterPropertiesSet");
        }

        public void customInit() {
            System.out.println("  [7c] init-method (customInit)");
        }

        @PreDestroy public void preDestroy() {
            System.out.println("  [10a] @PreDestroy");
        }

        @Override public void destroy() {
            System.out.println("  [10b] DisposableBean.destroy");
        }

        public void customDestroy() {
            System.out.println("  [10c] destroy-method (customDestroy)");
        }

        public String getName() { return name; }
    }
}
