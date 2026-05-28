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
 * <p>每个方法标注了三件事：</p>
 * <ul>
 *   <li>它在生命周期的哪个阶段（[0]~[10]）</li>
 *   <li>它被 Spring 的哪个类在哪个方法中调用</li>
 *   <li>什么特性催生了它</li>
 * </ul>
 */
public class Q01MyBean implements BeanNameAware, BeanFactoryAware,
        ApplicationContextAware, InitializingBean, DisposableBean {

    private String name;

    // ═══════════════════════════════════════════════════════
    // [1] 构造器
    // 调用方: AbstractAutowireCapableBeanFactory.createBeanInstance()
    // 催生者: Spring 1.0 — 最基本的反射创建对象
    // ═══════════════════════════════════════════════════════
    public Q01MyBean(String name) {
        this.name = name;
        System.out.println("  [1] 构造器　← AbstractAutowireCapableBeanFactory.createBeanInstance()");
    }

    // ═══════════════════════════════════════════════════════
    // [2] setter
    // 调用方: AbstractAutowireCapableBeanFactory.populateBean()
    //        → AutowiredAnnotationBeanPostProcessor.postProcessProperties()
    //        → field.set(bean, dependency)
    // 催生者: Spring 1.0 XML <property>，Spring 2.5 @Autowired
    // ═══════════════════════════════════════════════════════
    public void setName(String name) {
        this.name = name;
        System.out.println("  [2] setter　← AbstractAutowireCapableBeanFactory.populateBean()");
    }

    // ═══════════════════════════════════════════════════════
    // [3] BeanNameAware
    // 调用方: AbstractAutowireCapableBeanFactory.invokeAwareMethods()
    // 催生者: Spring 2.0 Scope + AOP
    // ═══════════════════════════════════════════════════════
    @Override
    public void setBeanName(String name) {
        System.out.println("  [3] BeanNameAware　← invokeAwareMethods() → bean.setBeanName(name)");
    }

    // ═══════════════════════════════════════════════════════
    // [4] BeanFactoryAware
    // 调用方: 同上 invokeAwareMethods()
    // 催生者: Spring 2.0 FactoryBean + 动态代理
    // ═══════════════════════════════════════════════════════
    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        System.out.println("  [4] BeanFactoryAware　← invokeAwareMethods()");
    }

    // ═══════════════════════════════════════════════════════
    // [5] ApplicationContextAware (注意: Context 级别的 Aware)
    // 调用方: ApplicationContextAwareProcessor.postProcessBeforeInitialization()
    //        → invokeAwareInterfaces()
    //        → bean.setApplicationContext(ctx)
    // ★ 不是 invokeAwareMethods()！它在 BPP.before 阶段回调！
    // 催生者: Spring 2.0 事件/国际化/资源加载
    // ═══════════════════════════════════════════════════════
    @Override
    public void setApplicationContext(ApplicationContext ctx) {
        System.out.println("  [5] ApplicationContextAware　← ApplicationContextAwareProcessor (BPP)");
    }

    // ═══════════════════════════════════════════════════════
    // [7a] @PostConstruct
    // 调用方: CommonAnnotationBeanPostProcessor
    //        (继承 InitDestroyAnnotationBeanPostProcessor)
    //        在 BPP.before 返回之后、afterPropertiesSet() 之前调用
    // 催生者: Spring 2.5 + JSR-250 标准化
    // ═══════════════════════════════════════════════════════
    @PostConstruct
    public void postConstruct() {
        this.name = "PostConstruct 修改后";
        System.out.println("  [7a] @PostConstruct　← CommonAnnotationBeanPostProcessor → method.invoke(bean)");
    }

    // ═══════════════════════════════════════════════════════
    // [7b] InitializingBean
    // 调用方: AbstractAutowireCapableBeanFactory.invokeInitMethods()
    //        → ((InitializingBean) bean).afterPropertiesSet()
    // 催生者: Spring 1.0 连接池初始化
    // ═══════════════════════════════════════════════════════
    @Override
    public void afterPropertiesSet() {
        System.out.println("  [7b] InitializingBean　← invokeInitMethods() → bean.afterPropertiesSet()");
    }

    // ═══════════════════════════════════════════════════════
    // [7c] init-method
    // 调用方: 同上 invokeInitMethods() → invokeCustomInitMethod()
    // 催生者: Spring 1.0 第三方连接池
    // ═══════════════════════════════════════════════════════
    public void customInit() {
        System.out.println("  [7c] init-method　← invokeInitMethods() → invokeCustomInitMethod()");
    }

    // ═══════════════════════════════════════════════════════
    // [10a] @PreDestroy
    // 调用方: CommonAnnotationBeanPostProcessor
    //        (实现 DestructionAwareBeanPostProcessor)
    //        → postProcessBeforeDestruction()
    // 注意: 仅对 singleton Bean 生效
    // ═══════════════════════════════════════════════════════
    @PreDestroy
    public void preDestroy() {
        System.out.println("  [10a] @PreDestroy　← DestructionAwareBeanPostProcessor");
    }

    // ═══════════════════════════════════════════════════════
    // [10b] DisposableBean
    // 调用方: DisposableBeanAdapter.destroy()
    // 触发: DefaultSingletonBeanRegistry.destroySingleton()
    // ═══════════════════════════════════════════════════════
    @Override
    public void destroy() {
        System.out.println("  [10b] DisposableBean　← DisposableBeanAdapter → bean.destroy()");
    }

    // ═══════════════════════════════════════════════════════
    // [10c] destroy-method
    // 调用方: DisposableBeanAdapter.destroy() → invokeCustomDestroyMethod()
    // ═══════════════════════════════════════════════════════
    public void customDestroy() {
        System.out.println("  [10c] destroy-method　← DisposableBeanAdapter → invokeCustomDestroyMethod()");
    }

    public String getName() { return name; }
}
