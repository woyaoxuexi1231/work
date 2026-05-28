package com.example.springqa.Q01_BeanLifecycle;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <h1>Q01 配置类 — 两个"后置处理器"的诞生故事</h1>
 *
 * <p>如果说 Q01MyBean 是主角，那这个类里的两个处理器就是导演——
 * 一个在开拍前改剧本（BeanFactoryPostProcessor），
 * 一个在每个演员上台前后做妆造（BeanPostProcessor）。</p>
 */
@Configuration
public class Q01Config {

    @Bean(initMethod = "customInit", destroyMethod = "customDestroy")
    public Q01MyBean q01_myBean() {
        return new Q01MyBean("初始名称");
    }

    // ============================================================
    // [0] BeanFactoryPostProcessor — Spring 3.0 的核武器
    // ============================================================
    /**
     * <b>[0] BeanFactoryPostProcessor — Spring 3.0 (2009)</b>
     *
     * <h3>你的猜测完全正确</h3>
     *
     * <p><b>问：如果没有 ConfigurationClassPostProcessor，
     * Spring 能处理 @Configuration 注解吗？</b></p>
     *
     * <p><b>答：不能。</b>在 Spring 2.5 时代，虽然有了 @Component、@Autowired
     * 这些注解，但它们是通过 XML 中的 {@code <context:component-scan/>}
     * 来激活的。当时根本没有 @Configuration 和 @Bean——这些是 Spring 3.0
     * 才引入的。</p>
     *
     * <p>Spring 3.0 想实现一个革命性的目标：<b>用 Java 类完全替代 XML 配置。</b>
     * 但这需要一个底层能力——能解析 Java 类中的 @Configuration 注解，
     * 把 @Bean 方法转成 BeanDefinition，注册到容器中。</p>
     *
     * <p>这个能力就是 {@code ConfigurationClassPostProcessor} 提供的。
     * <b>它是 BeanFactoryPostProcessor 的一个实现，而且是 Spring 中
     * 最重要的一个。</b>没有它，@Configuration 和 @Bean 都不会被识别。</p>
     *
     * <h3>完整的故事链</h3>
     *
     * <pre>
     * Spring 3.0 想要：用 Java 类替代 XML 配置
     *   → 需要：解析 @Configuration 类中的 @Bean 方法
     *   → 需要：在 Bean 创建之前注册这些 BeanDefinition
     *   → 需要：一个在所有 Bean 创建之前运行的钩子
     *   → 创建：BeanFactoryPostProcessor 接口 +
     *          ConfigurationClassPostProcessor 实现
     *
     * 后来又发现：
     *   → @Bean 中的 DataSource 配置需要读取 ${db.url} 占位符
     *   → 占位符必须在 Bean 实例化之前被替换
     *   → 创建：PropertySourcesPlaceholderConfigurer（另一个 BFPP）
     * </pre>
     *
     * <h3>真实世界的 BFPP 案例</h3>
     *
     * <table border="1">
     *   <tr><th>BFPP 实现</th><th>Spring 版本</th><th>为解决什么问题而创建</th></tr>
     *   <tr>
     *     <td>{@code PropertyPlaceholderConfigurer}</td>
     *     <td>Spring 2.0</td>
     *     <td>最早的 BFPP。XML 中写 {@code ${db.url}}，
     *         从 .properties 文件读取值替换。
     *         <b>这是第一个 BeanFactoryPostProcessor 实现。</b></td>
     *   </tr>
     *   <tr>
     *     <td>{@code ConfigurationClassPostProcessor}</td>
     *     <td>Spring 3.0</td>
     *     <td>解析 @Configuration 类，处理 @Bean、@ComponentScan、
     *         @Import、@PropertySource 等注解，
     *         把它们变成 BeanDefinition 注册到容器。
     *         <b>没有它就没有 Java Config。</b></td>
     *   </tr>
     *   <tr>
     *     <td>{@code PropertySourcesPlaceholderConfigurer}</td>
     *     <td>Spring 3.1</td>
     *     <td>PropertyPlaceholderConfigurer 的升级版。
     *         不仅读 .properties 文件，还能从 Environment、
     *         环境变量、命令行参数中获取值。
     *         所以你在 application.yml 中写的值能注入到 @Value。</td>
     *   </tr>
     * </table>
     *
     * <h3>关键区别：BFPP vs BPP</h3>
     * <pre>
     * BFPP 操作的是 BeanDefinition（蓝图）→ 在 Bean 创建<b>之前</b>
     * BPP  操作的是 Bean 实例（成品） → 在 Bean 创建<b>过程中</b>
     *
     * BFPP 的调用方是 AbstractApplicationContext.refresh() 中的
     *   invokeBeanFactoryPostProcessors()
     *
     * BPP 的调用方是 AbstractAutowireCapableBeanFactory 中的
     *   initializeBean() 方法，在创建每个 Bean 时调用
     * </pre>
     */
    @Bean
    public static BeanFactoryPostProcessor q01_propertyModifier() {
        return (ConfigurableListableBeanFactory factory) -> {
            System.out.println("  [0] BeanFactoryPostProcessor");
            System.out.println("      操作: BeanDefinition（蓝图），不是 Bean 实例（成品）");
            System.out.println("      调用方: AbstractApplicationContext.invokeBeanFactoryPostProcessors()");
            System.out.println("      催生者: Spring 3.0 @Configuration + 占位符替换需求");
            System.out.println("      代表: ConfigurationClassPostProcessor — 没有它就没有 @Bean");
            System.out.println("            PropertySourcesPlaceholderConfigurer — 没有它 @Value 不认 ${...}");
        };
    }

    // ============================================================
    // [6][8] BeanPostProcessor — Spring 2.5 的"万能拦截器"
    // ============================================================
    /**
     * <b>[6][8] BeanPostProcessor — Spring 2.5 (2007)</b>
     *
     * <h3>问题：为什么 BeanPostProcessor 在第六代？</h3>
     *
     * <p>你可能会问：BFPP 是第五代、BPP 是第六代……但 BFPP 是 Spring 3.0 才成熟的，
     * BPP 在 2.5 就大规模使用了？</p>
     *
     * <p>这里的"代"指的是<b>设计层次</b>，不是严格的时间顺序——
     * BPP 的设计确实更靠后，因为它比 BFPP 抽象层次更高（泛化程度更强）。
     * 但它在 2007 年就随着 @Autowired 的引入而成熟了。</p>
     *
     * <h3>BPP 的爆发——Spring 2.5 的注解革命</h3>
     *
     * <pre>
     * Spring 2.5 (2007) 引入了：@Autowired, @Component, @Repository, @Service
     *   → 每个都需要机制来"扫描并处理"
     *   → BeanPostProcessor 完美匹配这个需求
     *   → 于是诞生了 AutowiredAnnotationBeanPostProcessor
     *
     * 同一个版本还引入了：JSR-250 标准 → @PostConstruct, @PreDestroy, @Resource
     *   → 需要：扫描这些注解并执行对应逻辑
     *   → 创建：CommonAnnotationBeanPostProcessor
     *
     * Spring 3.0 (2009) AOP 全面注解化
     *   → 需要：检查 Bean 是否匹配 @Aspect 切面，创建代理
     *   → 创建：AnnotationAwareAspectJAutoProxyCreator
     *            （继承自 AbstractAutoProxyCreator，一个 BPP）
     *
     * Spring 3.2 (2012) 引入 @Async 和 @Scheduled
     *   → 需要：拦截异步方法调用 / 注册定时任务
     *   → 创建：AsyncAnnotationBeanPostProcessor
     *            ScheduledAnnotationBeanPostProcessor
     * </pre>
     *
     * <h3>完整故事链：你有多少个 @ 注解，背后就有多少个 BPP</h3>
     *
     * <table border="1">
     *   <tr><th>注解</th><th>对应的 BPP</th><th>阶段</th><th>做了什么</th></tr>
     *   <tr>
     *     <td>{@code @Autowired}</td>
     *     <td>AutowiredAnnotationBeanPostProcessor</td>
     *     <td>before</td>
     *     <td>扫描字段 → 从容器找候选 → field.set(bean, dep)</td>
     *   </tr>
     *   <tr>
     *     <td>{@code @Value}</td>
     *     <td>同上的 BPP</td>
     *     <td>before</td>
     *     <td>解析 ${...} 表达式 → 从 Environment 取值 → 注入</td>
     *   </tr>
     *   <tr>
     *     <td>{@code @Resource}</td>
     *     <td>CommonAnnotationBeanPostProcessor</td>
     *     <td>before</td>
     *     <td>先按 name 再按 type 查找 → 注入</td>
     *   </tr>
     *   <tr>
     *     <td>{@code @PostConstruct}</td>
     *     <td>同上的 BPP</td>
     *     <td>before</td>
     *     <td>扫描所有方法 → 找到 @PostConstruct → 反射调用</td>
     *   </tr>
     *   <tr>
     *     <td>{@code *Aware}</td>
     *     <td>ApplicationContextAwareProcessor</td>
     *     <td>before</td>
     *     <td>检测 Aware 接口 → 按序回调 setter</td>
     *   </tr>
     *   <tr>
     *     <td>{@code @Transactional}</td>
     *     <td><b>AnnotationAwareAspectJAutoProxyCreator</b></td>
     *     <td><b>after</b></td>
     *     <td>创建代理 → 拦截方法 → 开启/提交/回滚事务</td>
     *   </tr>
     *   <tr>
     *     <td>{@code @Cacheable}</td>
     *     <td>同上的 BPP</td>
     *     <td>after</td>
     *     <td>创建代理 → 拦截方法 → 查缓存/写缓存</td>
     *   </tr>
     *   <tr>
     *     <td>{@code @Async}</td>
     *     <td>AsyncAnnotationBeanPostProcessor</td>
     *     <td>after</td>
     *     <td>创建代理 → 拦截 → 提交给 TaskExecutor</td>
     *   </tr>
     *   <tr>
     *     <td>{@code @Scheduled}</td>
     *     <td>ScheduledAnnotationBeanPostProcessor</td>
     *     <td>after</td>
     *     <td>解析 cron → 注册到 TaskScheduler</td>
     *   </tr>
     * </table>
     *
     * <h3>为什么 "before 做注入，after 做代理"？</h3>
     *
     * <p>这是一个非常精确的分工：</p>
     * <ul>
     *   <li>before 时，Bean 的属性刚填充完，@PostConstruct 还没执行。
     *       此时适合做"验证 + Aware 回调"——确认注入正确，给 Bean 容器引用。</li>
     *   <li>after 时，Bean 已经完全初始化（所有 init 方法已执行），
     *       是成品。此时生成的代理对象能完整替代原始 Bean——
     *       包括 @PostConstruct 中修改的状态。</li>
     * </ul>
     *
     * <p>如果 AOP 代理在 before 就生成，那它包装的是一个半成品——
     * @PostConstruct 中的初始化逻辑都不会被代理执行。这就是为什么
     * 循环依赖需要三级缓存：before 和 after 之间需要提前暴露引用时，
     * 必须用 ObjectFactory 延迟生成代理。</p>
     */
    @Bean
    public static BeanPostProcessor q01_loggingBpp() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName)
                    throws BeansException {
                if (bean instanceof Q01MyBean) {
                    System.out.println("  [6] BPP.before ← 属性已填充，init 未执行");
                    System.out.println("      催生者: Spring 2.5 @Autowired → AutowiredAnnotationBeanPostProcessor");
                    System.out.println("             JSR-250 @PostConstruct → CommonAnnotationBeanPostProcessor");
                }
                return bean;
            }

            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName)
                    throws BeansException {
                if (bean instanceof Q01MyBean) {
                    System.out.println("  [8] BPP.after  ← Bean 已完全初始化（成品）");
                    System.out.println("      催生者: Spring 3.0 AOP → AnnotationAwareAspectJAutoProxyCreator");
                    System.out.println("             Spring 3.2 @Async/@Scheduled → Async/Scheduled BPP");
                    System.out.println("      ★ @Transactional、@Cacheable 的代理都在这里生成 ★");
                }
                return bean;
            }
        };
    }
}
