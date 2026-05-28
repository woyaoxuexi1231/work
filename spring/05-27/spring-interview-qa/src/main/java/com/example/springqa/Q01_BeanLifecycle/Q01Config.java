package com.example.springqa.Q01_BeanLifecycle;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <h1>Q01 配置类 — BFPP 和 BPP 在源码中的调用位置</h1>
 *
 * <pre>
 * AbstractApplicationContext.refresh()
 *   │
 *   ├── invokeBeanFactoryPostProcessors(beanFactory)
 *   │     └── ★ 在这里调用所有 BFPP
 *   │         postProcessor.postProcessBeanFactory(beanFactory)
 *   │         → 此时还没有任何 Bean 被创建
 *   │
 *   ├── registerBeanPostProcessors(beanFactory)
 *   │     └── 注册所有 BPP（只是注册，还没调用）
 *   │
 *   ├── finishBeanFactoryInitialization(beanFactory)
 *   │     └── beanFactory.preInstantiateSingletons()
 *   │           └── 对每个单例 Bean:
 *   │               AbstractAutowireCapableBeanFactory.doCreateBean()
 *   │                 ├── createBeanInstance()          ← [1] 构造器
 *   │                 ├── populateBean()                ← [2] 属性填充
 *   │                 └── initializeBean()
 *   │                       ├── invokeAwareMethods()    ← [3][4][5] Aware
 *   │                       ├── applyBeanPostProcessorsBeforeInitialization()
 *   │                       │     └── ★ 遍历所有 BPP，调用 postProcessBeforeInitialization()
 *   │                       ├── invokeInitMethods()     ← [7a][7b][7c] init
 *   │                       └── applyBeanPostProcessorsAfterInitialization()
 *   │                             └── ★ 遍历所有 BPP，调用 postProcessAfterInitialization()
 *   │                                   └── @Transactional / @Cacheable 代理在此生成
 *   │
 *   └── finishRefresh()
 *
 * 容器关闭时:
 *   DefaultSingletonBeanRegistry.destroySingletons()
 *     └── DisposableBeanAdapter.destroy()
 *           ├── DestructionAwareBeanPostProcessor.postProcessBeforeDestruction()
 *           │     └── ★ @PreDestroy 在此执行
 *           ├── ((DisposableBean) bean).destroy()       ← [10b]
 *           └── invokeCustomDestroyMethod()             ← [10c]
 * </pre>
 */
@Configuration
public class Q01Config {

    @Bean(initMethod = "customInit", destroyMethod = "customDestroy")
    public Q01MyBean q01_myBean() {
        return new Q01MyBean("初始名称");
    }

    // ═══════════════════════════════════════════════════════
    // [0] BeanFactoryPostProcessor
    // 调用方: AbstractApplicationContext.refresh()
    //        → PostProcessorRegistrationDelegate.invokeBeanFactoryPostProcessors()
    //        → postProcessor.postProcessBeanFactory(beanFactory)
    // 执行时机: 在【所有 Bean 创建之前】运行一次
    // 操作对象: BeanDefinition
    // ═══════════════════════════════════════════════════════
    @Bean
    public static BeanFactoryPostProcessor q01_propertyModifier() {
        return (ConfigurableListableBeanFactory factory) -> {
            System.out.println("  [0] BeanFactoryPostProcessor");
            System.out.println("      调用: AbstractApplicationContext.refresh()");
            System.out.println("           → invokeBeanFactoryPostProcessors()");
            System.out.println("           → postProcessor.postProcessBeanFactory(beanFactory)");
            System.out.println("      操作: BeanDefinition（此时所有 Bean 尚未创建）");
        };
    }

    // ═══════════════════════════════════════════════════════
    // [6][8] BeanPostProcessor
    // 调用方: AbstractAutowireCapableBeanFactory.initializeBean()
    //        → applyBeanPostProcessorsBeforeInitialization()  [6]
    //        → applyBeanPostProcessorsAfterInitialization()   [8]
    // 执行时机: 每个 Bean 在 initializeBean() 中调用两次
    // 操作对象: Bean 实例
    // ═══════════════════════════════════════════════════════
    @Bean
    public static BeanPostProcessor q01_loggingBpp() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName)
                    throws BeansException {
                if (bean instanceof Q01MyBean) {
                    System.out.println("  [6] BPP.before");
                    System.out.println("      调用: AbstractAutowireCapableBeanFactory.initializeBean()");
                    System.out.println("           → applyBeanPostProcessorsBeforeInitialization()");
                    System.out.println("      此时: 属性已填充，init 未执行（半成品）");
                }
                return bean;
            }

            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName)
                    throws BeansException {
                if (bean instanceof Q01MyBean) {
                    System.out.println("  [8] BPP.after");
                    System.out.println("      调用: 同上 → applyBeanPostProcessorsAfterInitialization()");
                    System.out.println("      此时: init 已执行完毕（成品）→ ★ AOP 代理在此生成");
                }
                return bean;
            }
        };
    }
}
