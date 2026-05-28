package com.example.springqa.Q01_BeanLifecycle;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * <h1>Q1：Bean 生命周期 — 完整时间轴</h1>
 *
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │                    Spring 容器启动 (refresh)                         │
 * ├─────────────────────────────────────────────────────────────────────┤
 * │                                                                     │
 * │  ╔═══════════════════════════════════════════════════════════════╗  │
 * │  ║  阶段 [0]  BeanFactoryPostProcessor.postProcessBeanFactory()  ║  │
 * │  ║  ├─ 时机: 所有 Bean 创建之前                                     ║  │
 * │  ║  ├─ 调用方: AbstractApplicationContext.refresh()                ║  │
 * │  ║  │         → invokeBeanFactoryPostProcessors()                  ║  │
 * │  ║  ├─ 操作对象: BeanDefinition（蓝图）                             ║  │
 * │  ║  ├─ 代表: ConfigurationClassPostProcessor 解析 @Configuration   ║  │
 * │  ║  │       PropertySourcesPlaceholderConfigurer 替换 ${...}      ║  │
 * │  ║  └─ 注意: 只执行一次，对【所有】BeanDefinition 生效              ║  │
 * │  ╚═══════════════════════════════════════════════════════════════╝  │
 * │                              ↓                                      │
 * │  ╔═══════════════════════════════════════════════════════════════╗  │
 * │  ║              这个循环对【每个单例 Bean】执行一次               ║  │
 * │  ╚═══════════════════════════════════════════════════════════════╝  │
 * │                              ↓                                      │
 * │  ┌─ 阶段 [1]  构造器实例化 ────────────────────────────────────┐    │
 * │  │  调用方: AbstractAutowireCapableBeanFactory.createBeanInstance()│
 * │  │  做什么: Constructor.newInstance() → 对象诞生                │    │
 * │  │  依赖: 无（此时 @Autowired 字段全部为 null）                 │    │
 * │  └─────────────────────────────────────────────────────────────┘    │
 * │                              ↓                                      │
 * │  ┌─ 阶段 [2]  属性填充 (Populate) ────────────────────────────┐    │
 * │  │  调用方: AbstractAutowireCapableBeanFactory.populateBean()      │
 * │  │  做什么:                                                       │
 * │  │    a) InstantiationAwareBeanPostProcessor.postProcessProperties()│
 * │  │       → AutowiredAnnotationBeanPostProcessor 处理 @Autowired   │
 * │  │       → field.set(bean, resolvedDependency)  ← 私有字段也能注入│
 * │  │       → CommonAnnotationBeanPostProcessor 处理 @Resource       │
 * │  │    b) XML <property> 或 @Bean 参数注入                         │
 * │  └─────────────────────────────────────────────────────────────┘    │
 * │                              ↓                                      │
 * │  ┌─ 阶段 [3]  BeanNameAware.setBeanName() ─────────────────────┐    │
 * │  │  调用方: AbstractAutowireCapableBeanFactory.invokeAwareMethods()│
 * │  │  做什么: bean.setBeanName(beanDefinition中的name/id)           │
 * │  │  触发条件: Bean 实现了 BeanNameAware 接口                       │
 * │  └─────────────────────────────────────────────────────────────┘    │
 * │                              ↓                                      │
 * │  ┌─ 阶段 [4]  BeanClassLoaderAware.setBeanClassLoader() ───────┐    │
 * │  │  作用: 拿到加载本 Bean 类的 ClassLoader                        │    │
 * │  │  (和 [3] 在同一个方法中回调，按序执行)                           │    │
 * │  └─────────────────────────────────────────────────────────────┘    │
 * │                              ↓                                      │
 * │  ┌─ 阶段 [5]  BeanFactoryAware.setBeanFactory() ───────────────┐    │
 * │  │  调用方: 同上 invokeAwareMethods()                              │
 * │  │  做什么: bean.setBeanFactory(this)                              │
 * │  │  此时 Bean 可以手动 getBean() 了                                 │
 * │  └─────────────────────────────────────────────────────────────┘    │
 * │                              ↓                                      │
 * │  ╔═══════════════════════════════════════════════════════════════╗  │
 * │  ║  注意：[3][4][5] 这三个 Aware 的触发条件不同：                ║  │
 * │  ║  • [3][4][5] 由 AbstractAutowireCapableBeanFactory            ║  │
 * │  ║              .invokeAwareMethods() 直接回调                    ║  │
 * │  ║  • EnvironmentAware / ApplicationContextAware /                ║  │
 * │  ║    MessageSourceAware / ApplicationEventPublisherAware         ║  │
 * │  ║    等 6 个 Context 级别的 Aware → 不是在这里回调！             ║  │
 * │  ║    它们由 ApplicationContextAwareProcessor (一个 BPP)          ║  │
 * │  ║    在阶段 [6] 的 before 中回调（见下面）                       ║  │
 * │  ╚═══════════════════════════════════════════════════════════════╝  │
 * │                              ↓                                      │
 * │  ┌─ 阶段 [6]  BeanPostProcessor.postProcessBeforeInitialization() ┐ │
 * │  │  调用方: AbstractAutowireCapableBeanFactory.initializeBean()    │
 * │  │                                                               │
 * │  │  遍历所有已注册的 BPP，依次调用:                                │
 * │  │                                                               │
 * │  │  ★ ApplicationContextAwareProcessor                           │
 * │  │    → 检测 Bean 是否实现了 EnvironmentAware                      │
 * │  │    → 是 → bean.setEnvironment(...)                             │
 * │  │    → 检测 Bean 是否实现了 ApplicationContextAware               │
 * │  │    → 是 → bean.setApplicationContext(...)    ← [5'] 在这里！  │
 * │  │    → 检测 MessageSourceAware / ApplicationEventPublisherAware  │
 * │  │      / ResourceLoaderAware / EmbeddedValueResolverAware        │
 * │  │                                                               │
 * │  │  ★ AutowiredAnnotationBeanPostProcessor                       │
 * │  │    → 处理 @Autowired 方法注入（构造器已注入，现在是方法）        │
 * │  │                                                               │
 * │  │  ★ BeanValidationPostProcessor                                │
 * │  │    → JSR-303 校验 @NotNull @Size 等                            │
 * │  │                                                               │
 * │  │  ★ InitDestroyAnnotationBeanPostProcessor                     │
 * │  │    → 扫描 @PostConstruct 方法……但还没执行！只是找到它们        │
 * │  └─────────────────────────────────────────────────────────────┘    │
 * │                              ↓                                      │
 * │  ┌─ 阶段 [7a] @PostConstruct ─────────────────────────────────┐    │
 * │  │  调用方: CommonAnnotationBeanPostProcessor                     │
 * │  │         (继承自 InitDestroyAnnotationBeanPostProcessor)        │
 * │  │  做什么: method.invoke(bean) → 反射调用 @PostConstruct 方法    │
 * │  │  时机: 在 BPP.before 返回之后，但仍在 initializeBean() 内部    │
 * │  │  优先级: ★ 最高（最先执行）                                    │
 * │  └─────────────────────────────────────────────────────────────┘    │
 * │                              ↓                                      │
 * │  ┌─ 阶段 [7b] InitializingBean.afterPropertiesSet() ──────────┐    │
 * │  │  调用方: AbstractAutowireCapableBeanFactory.invokeInitMethods() │
 * │  │  做什么: ((InitializingBean) bean).afterPropertiesSet()         │
 * │  │  触发条件: Bean 实现了 InitializingBean 接口                    │
 * │  │  优先级: ★★ 中等                                               │
 * │  └─────────────────────────────────────────────────────────────┘    │
 * │                              ↓                                      │
 * │  ┌─ 阶段 [7c] init-method ────────────────────────────────────┐    │
 * │  │  调用方: 同上 invokeInitMethods()                               │
 * │  │  做什么: method.invoke(bean) → 反射调用 XML/注解指定的方法       │
 * │  │  触发条件: BeanDefinition 中配置了 initMethodName               │
 * │  │  优先级: ★★★ 最低（最后执行）                                  │
 * │  └─────────────────────────────────────────────────────────────┘    │
 * │                              ↓                                      │
 * │  ┌─ 阶段 [8]  BeanPostProcessor.postProcessAfterInitialization() ┐  │
 * │  │  调用方: 同上 initializeBean()                                  │
 * │  │                                                               │
 * │  │  遍历所有已注册的 BPP，依次调用:                                │
 * │  │                                                               │
 * │  │  ★ AbstractAutoProxyCreator (及其子类)                        │
 * │  │    → 检查 Bean 是否匹配任何 Advisor/切面                       │
 * │  │    → 匹配 → wrapIfNecessary() 创建代理对象                    │
 * │  │    → 返回代理（替换原始 Bean）                                 │
 * │  │    子类: AnnotationAwareAspectJAutoProxyCreator               │
 * │  │    它生成的代理让 @Transactional / @Cacheable / @Aspect 生效  │
 * │  │                                                               │
 * │  │  ★ AsyncAnnotationBeanPostProcessor                           │
 * │  │    → 检测 @Async → 创建异步代理                               │
 * │  │                                                               │
 * │  │  ★ ScheduledAnnotationBeanPostProcessor                       │
 * │  │    → 检测 @Scheduled → 注册到 TaskScheduler                   │
 * │  └─────────────────────────────────────────────────────────────┘    │
 * │                              ↓                                      │
 * │  ┌─ 阶段 [9]  Bean 就绪 ──────────────────────────────────────┐    │
 * │  │  做什么: 放入 singletonObjects（一级缓存）                     │
 * │  │  状态: getBean() 从这里开始可以获取到                          │
 * │  │  注意: 如果 [8] 中创建了代理，存的就是代理对象                 │
 * │  └─────────────────────────────────────────────────────────────┘    │
 * │                                                                     │
 * │  ╔═══════════════════════════════════════════════════════════════╗  │
 * │  ║                    容器运行中……                               ║  │
 * │  ╚═══════════════════════════════════════════════════════════════╝  │
 * │                                                                     │
 * ├─────────────────────────────────────────────────────────────────────┤
 * │                    Spring 容器关闭 (close)                           │
 * ├─────────────────────────────────────────────────────────────────────┤
 * │                                                                     │
 * │  ┌─ 阶段 [10a] @PreDestroy ───────────────────────────────────┐    │
 * │  │  调用方: CommonAnnotationBeanPostProcessor                     │
 * │  │         (实现 DestructionAwareBeanPostProcessor 接口)          │
 * │  │  做什么: method.invoke(bean) → 反射调用 @PreDestroy 方法       │
 * │  │  触发条件: Bean 有 @PreDestroy 注解 + 是 singleton            │
 * │  │  注意: prototype Bean 的 @PreDestroy 不会被 Spring 调用       │
 * │  └─────────────────────────────────────────────────────────────┘    │
 * │                              ↓                                      │
 * │  ┌─ 阶段 [10b] DisposableBean.destroy() ──────────────────────┐    │
 * │  │  调用方: DisposableBeanAdapter.destroy()                       │
 * │  │  做什么: ((DisposableBean) bean).destroy()                     │
 * │  │  触发条件: Bean 实现了 DisposableBean 接口                     │
 * │  └─────────────────────────────────────────────────────────────┘    │
 * │                              ↓                                      │
 * │  ┌─ 阶段 [10c] destroy-method ────────────────────────────────┐    │
 * │  │  调用方: DisposableBeanAdapter.destroy()                       │
 * │  │  做什么: method.invoke(bean) → 反射调用配置的销毁方法           │
 * │  │  触发条件: BeanDefinition 中配置了 destroyMethodName            │
 * │  └─────────────────────────────────────────────────────────────┘    │
 * │                                                                     │
 * └─────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>两个关键认知</h2>
 *
 * <h3>1. Aware 分两批执行</h3>
 * <p>很多人以为所有 *Aware 接口在同一个阶段回调——不是的：</p>
 * <ul>
 *   <li>BeanNameAware、BeanClassLoaderAware、BeanFactoryAware →
 *       在 [3][4][5] 阶段，由 BeanFactory 直接调用</li>
 *   <li>ApplicationContextAware、EnvironmentAware、MessageSourceAware 等 →
 *       在 [6] 阶段，由 ApplicationContextAwareProcessor（一个 BPP）调用</li>
 * </ul>
 * <p>分批的原因是：Context 级别的 Aware 依赖 ApplicationContext，
 * 而 ApplicationContext 在 BeanFactory 创建之后才准备好。
 * 所以需要的回调时机更晚。</p>
 *
 * <h3>2. postProcessBefore 和 @PostConstruct 之间有 "缝隙"</h3>
 * <p>BPP.before 返回后，@PostConstruct 才开始执行——
 * 这意味着 BPP.before 中修改的属性，@PostConstruct 能看到；
 * 但 @PostConstruct 是独立于 BPP 调用的，不在 BPP 链中。</p>
 */
@Component
public class BeanLifecycleDemo {

    private final ApplicationContext ctx;

    public BeanLifecycleDemo(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Q01: Bean 生命周期 — 完整时间轴 ===\n\n");

        sb.append("容器启动 → refresh()\n");
        sb.append("  │\n");
        sb.append("  ├─ [0]  BeanFactoryPostProcessor         ← 操作 BeanDefinition\n");
        sb.append("  │        ConfigurationClassPostProcessor  ← 解析 @Configuration\n");
        sb.append("  │        PropertySourcesPlaceholderCfg    ← 替换 ${...}\n");
        sb.append("  │\n");
        sb.append("  │  ═══ 以下对每个单例 Bean 执行 ═══\n");
        sb.append("  │\n");
        sb.append("  ├─ [1]  构造器                               ← Constructor.newInstance()\n");
        sb.append("  ├─ [2]  属性填充                             ← field.set(bean, dep)\n");
        sb.append("  │        AutowiredAnnotationBeanPostProcessor ← @Autowired 在此注入\n");
        sb.append("  │\n");
        sb.append("  ├─ [3]  BeanNameAware                       ← BeanFactory 直接回调\n");
        sb.append("  ├─ [4]  BeanClassLoaderAware\n");
        sb.append("  ├─ [5]  BeanFactoryAware                    ← 此时可手动 getBean()\n");
        sb.append("  │\n");
        sb.append("  ├─ [6]  BPP.before                          ← initializeBean() 入口\n");
        sb.append("  │        ApplicationContextAwareProcessor    ← ApplicationContextAware 等在此回调！\n");
        sb.append("  │        BeanValidationPostProcessor         ← JSR-303 校验\n");
        sb.append("  │\n");
        sb.append("  ├─ [7a] @PostConstruct                      ← JSR-250 标准（最高优先级）\n");
        sb.append("  ├─ [7b] InitializingBean.afterPropertiesSet  ← Spring 接口（中等优先级）\n");
        sb.append("  ├─ [7c] init-method                          ← XML/@Bean 指定（最低优先级）\n");
        sb.append("  │\n");
        sb.append("  ├─ [8]  BPP.after                           ← ★ AOP 代理在此生成！\n");
        sb.append("  │        AnnotationAwareAspectJAutoProxyCreator → @Transactional/@Cacheable\n");
        sb.append("  │        AsyncAnnotationBeanPostProcessor      → @Async\n");
        sb.append("  │        ScheduledAnnotationBeanPostProcessor  → @Scheduled\n");
        sb.append("  │\n");
        sb.append("  ├─ [9]  就绪 → singletonObjects              ← getBean() 可获取\n");
        sb.append("  │\n");
        sb.append("  │  ═══════════ 容器运行中 ═══════════\n");
        sb.append("  │\n");
        sb.append("容器关闭 → close()\n");
        sb.append("  │\n");
        sb.append("  ├─ [10a] @PreDestroy                         ← 销毁前\n");
        sb.append("  ├─ [10b] DisposableBean.destroy()\n");
        sb.append("  └─ [10c] destroy-method                      ← 销毁后\n\n");

        sb.append("【核心调用链追溯】\n");
        sb.append("  [0] → AbstractApplicationContext.refresh()\n");
        sb.append("        → invokeBeanFactoryPostProcessors()\n");
        sb.append("  [1]~[9] → AbstractAutowireCapableBeanFactory\n");
        sb.append("        → doCreateBean()\n");
        sb.append("        → createBeanInstance()     [1]\n");
        sb.append("        → populateBean()           [2]\n");
        sb.append("        → initializeBean()         [3]~[9]\n");
        sb.append("  [10] → DefaultSingletonBeanRegistry.destroySingleton()\n");

        return sb.toString();
    }
}
