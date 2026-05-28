package com.example.springqa.Q01_BeanLifecycle;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * <h1>Q1：Bean 生命周期 — 每个阶段是哪个特性催生的</h1>
 *
 * <h2>正确的学习方式：不要背阶段，要理解因果链</h2>
 *
 * <p>Spring 的 Bean 生命周期经历了从 2004 到 2014 的十年演化。
 * 每一个扩展点都不是凭空出现的——它背后都有<b>一个具体的特性</b>
 * 在问："如果我要实现这个，需要容器给我什么钩子？"</p>
 *
 * <h2>因果链全景</h2>
 *
 * <pre>
 * ┌──────────┬─────────┬─────────────────────────────────────────┐
 * │  Spring  │  阶段   │  因果链                                  │
 * │  版本    │         │                                         │
 * ├──────────┼─────────┼─────────────────────────────────────────┤
 * │   1.0    │ [1][2]  │ XML &lt;bean&gt; 需要创建对象 + 填充属性        │
 * │  2004    │ [7b][7c]│ 连接池需要"属性注完后再初始化"             │
 * │          │ [10]    │ 连接池需要在"关闭时释放资源"               │
 * ├──────────┼─────────┼─────────────────────────────────────────┤
 * │   2.0    │ [3][4]  │ Scope + AOP 需要 Bean 知道自己的身份       │
 * │  2006    │ [5]     │ 事件/国际化/资源加载能力需要暴露给 Bean     │
 * │          │  (BFPP) │ ${placeholder} 替换（第一个 BFPP 诞生）   │
 * ├──────────┼─────────┼─────────────────────────────────────────┤
 * │   2.5    │ [6][8]  │ @Autowired 需要扫描字段并注入！           │
 * │  2007    │         │ @PostConstruct/@PreDestroy 需要被识别！   │
 * │          │         │ → BPP 爆发：一个个注解对应一个个 BPP      │
 * ├──────────┼─────────┼─────────────────────────────────────────┤
 * │   3.0    │  重点   │ @Configuration + @Bean 要替代 XML！       │
 * │  2009    │   BFPP  │ → ConfigurationClassPostProcessor 解析    │
 * │          │   BPP   │ → AOP 全面注解化（AutoProxyCreator）      │
 * ├──────────┼─────────┼─────────────────────────────────────────┤
 * │   3.2    │   BPP   │ @Async 异步 + @Scheduled 定时任务         │
 * │  2012    │         │ → 又是两个新的 BPP                        │
 * └──────────┴─────────┴─────────────────────────────────────────┘
 * </pre>
 *
 * <h2>三条核心认知</h2>
 *
 * <ol>
 *   <li><b>BFPP 是因 @Configuration 而成熟的。</b>
 *       没有 ConfigurationClassPostProcessor，@Bean 不会被识别。
 *       Spring 3.0 之前的 Java Config 根本不存在。</li>
 *   <li><b>BPP 是因 @Autowired 而爆发的。</b>
 *       Spring 2.5 引入的注解驱动开发需要一个"通用的注解处理器"模式——
 *       BPP 就是那个答案。之后每一个新注解几乎都对应一个新 BPP。</li>
 *   <li><b>AOP 代理在 BPP.after。</b>
 *       不是偶然——before 时 Bean 还是半成品，after 时才是成品。
 *       这就是全部 AOP 事务/缓存/异步/定时任务的基础。</li>
 * </ol>
 */
@Component
public class BeanLifecycleDemo {

    private final ApplicationContext ctx;

    public BeanLifecycleDemo(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Q01: Bean 生命周期——因果链 ===\n\n");

        sb.append("Spring 1.0 (2004): 需要一个 DI 容器\n");
        sb.append("  → 创建了 [1]构造器 + [2]setter（最朴素的反射）\n");
        sb.append("  → 创建了 [7b]InitializingBean（连接池的 init()）\n");
        sb.append("  → 创建了 [7c]init-method（第三方类也能用）\n");
        sb.append("  → 创建了 [10b]DisposableBean + [10c]destroy-method（资源释放）\n\n");

        sb.append("Spring 2.0 (2006): Scope + AOP 来了\n");
        sb.append("  → Bean 需要知道自己的身份 → [3]BeanNameAware\n");
        sb.append("  → Bean 需要手动获取 Bean → [4]BeanFactoryAware\n");
        sb.append("  → Bean 需要发事件/读资源 → [5]ApplicationContextAware\n\n");

        sb.append("Spring 2.5 (2007): 注解革命——@Autowired 来了！\n");
        sb.append("  → 需要一个通用注解处理机制 → [6][8]BeanPostProcessor 爆发\n");
        sb.append("  → @Autowired → AutowiredAnnotationBeanPostProcessor\n");
        sb.append("  → @PostConstruct → CommonAnnotationBeanPostProcessor\n\n");

        sb.append("Spring 3.0 (2009): 消灭 XML——@Configuration 来了！\n");
        sb.append("  → 需要解析 @Bean 方法 → [0]BeanFactoryPostProcessor 成熟\n");
        sb.append("  → ConfigurationClassPostProcessor 是其中的核心\n");
        sb.append("  → AOP 全面注解化 → AnnotationAwareAspectJAutoProxyCreator(BPP)\n");
        sb.append("  → @Transactional/@Cacheable 的代理在 [8]BPP.after 生成\n\n");

        sb.append("Spring 3.2 (2012): @Async + @Scheduled\n");
        sb.append("  → AsyncAnnotationBeanPostProcessor + ScheduledAnnotationBeanPostProcessor\n");
        sb.append("  → 又是两个 BPP。这个模式已经固化了——\n");
        sb.append("  → \"每一个新注解，都对应一个新 BPP\"\n\n");

        sb.append("【你猜对了】\n");
        sb.append("问：没有 ConfigurationClassPostProcessor，Spring 能处理 @Configuration 吗？\n");
        sb.append("答：不能。Spring 3.0 之前的 Java Config 根本不存在。\n");
        sb.append("   @Configuration + @Bean 就是为了消灭 XML 而生的，\n");
        sb.append("   ConfigurationClassPostProcessor 就是实现它的引擎。\n\n");

        sb.append("【你能答出的面试题】\n");
        sb.append("1. BeanFactoryPostProcessor vs BeanPostProcessor？\n");
        sb.append("   → BFPP 操作 BeanDefinition（蓝图），在创建前运行\n");
        sb.append("   → BPP 操作 Bean 实例（成品），在每个 Bean 创建时运行\n\n");
        sb.append("2. 为什么 AOP 代理在 after 不在 before？\n");
        sb.append("   → before 时 @PostConstruct 还没执行，是半成品\n\n");
        sb.append("3. @Autowired 怎么生效的？\n");
        sb.append("   → AutowiredAnnotationBeanPostProcessor 在 BPP.before 中\n");
        sb.append("     field.set(bean, dependency)\n");

        return sb.toString();
    }
}
