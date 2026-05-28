package com.example.springqa.Q01_BeanLifecycle;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <h1>Q01 配置类 — BFPP 和 BPP 的设计目的</h1>
 *
 * <p>这两个"后置处理器"不是给普通 Bean 用的——它们是给<b>框架开发者</b>用的。
 * Spring 自身和第三方框架（MyBatis、Hibernate Validator）通过它们把自己的能力
 * "织入"到每一个 Bean 中，而你作为应用开发者完全不需要感知。</p>
 */
@Configuration
public class Q01Config {

    @Bean(initMethod = "customInit", destroyMethod = "customDestroy")
    public Q01MyBean q01_myBean() {
        return new Q01MyBean("初始名称");
    }

    // [0] BeanFactoryPostProcessor ———————————————————————
    /**
     * <b>设计目的：让框架可以在 Bean 创建之前修改"配置元数据"。</b>
     *
     * <p>通俗解释：BFPP 操作的是<b>蓝图</b>，不是成品。它在你（应用开发者）
     * 写的每一个 Bean 被创建之前运行，可以修改它们的定义——改 scope、
     * 改属性值、甚至添加/删除 Bean。</p>
     *
     * <p><b>设计者最初面临的场景：</b>application.yml 中写的是
     * {@code ${DB_HOST:localhost}}。这个占位符必须在 Bean 实例化
     * <b>之前</b>被替换成真正的 IP 地址。如果等 Bean 创建完了再替换——
     * 太晚了，Bean 已经用错误的值创建了。</p>
     *
     * <p>所以 BFPP 被设计为在<b>所有 Bean 创建之前</b>运行一次。
     * 它拿到了整个 BeanFactory 的引用，可以遍历所有 BeanDefinition，
     * 在"开工"之前把图纸改对。</p>
     *
     * <p><b>两个最核心的 BFPP：</b></p>
     * <ul>
     *   <li>{@code ConfigurationClassPostProcessor}：解析 @Configuration 类。
     *       没有它，你写的 @Bean 方法不会被识别——因为没有人读取它们并注册到容器。</li>
     *   <li>{@code PropertySourcesPlaceholderConfigurer}：替换 ${...} 占位符。
     *       没有它，你 application.yml 中的值不会注入到 @Value 字段。</li>
     * </ul>
     */
    @Bean
    public static BeanFactoryPostProcessor q01_propertyModifier() {
        return (ConfigurableListableBeanFactory factory) -> {
            System.out.println("  [0] BFPP — 设计目的: 在 Bean 创建之前修改蓝图（BeanDefinition）");
        };
    }

    // [6][8] BeanPostProcessor ———————————————————————————
    /**
     * <b>设计目的：让框架可以对<b>每一个</b> Bean 的创建过程做统一处理，
     * 且不要求 Bean 实现任何接口。</b>
     *
     * <p>通俗解释：BPP 是 Spring 的"流水线质检员"——
     * 每一个 Bean 在生产线上经过时，都要被 before 和 after 检查两次。
     * 这个设计解决了 Spring 1.x 时代最大的痛点：扩展机制必须侵入 Bean 源码。</p>
     *
     * <p><b>设计者最初面临的场景：</b>Spring 2.5 引入了 @Autowired 注解。
     * 怎么让容器自动识别这个注解并注入依赖？
     * 方案 A：让每个 Bean 都实现一个接口——侵入式，不接受。
     * 方案 B：写一个"观察者"，拦截每个 Bean 的创建过程，扫描它的字段——
     * 找到了 @Autowired 就从容器获取依赖并注入。这就是 BPP 的起源。</p>
     *
     * <h3>before 和 after 的分工</h3>
     *
     * <p><b>before — 设计目的：提供一个"初始化前"的拦截点。</b>
     * 此时属性已填充完毕，但 @PostConstruct 还没执行。
     * 适合做"注入后的检查和处理"——校验注入的值对不对、
     * 给 Bean 回调容器引用（Aware）、准备代理所需的信息。</p>
     *
     * <p><b>after — 设计目的：提供一个"初始化后、成品阶段"的拦截点。</b>
     * 此时 Bean 已经完全初始化，所有回调都执行完毕。
     * 这是生成代理的<b>唯一正确时机</b>——代理对象能完整替代原始 Bean，
     * 包括 @PostConstruct 中修改过的所有状态。</p>
     *
     * <p><b>为什么 before 不能生成代理？</b>
     * 假设 AOP 代理在 before 就生成，它包装的 Bean 还没执行 @PostConstruct。
     * 之后 @PostConstruct 修改了 Bean 的内部状态——但代理对象持有的引用
     * 是"修改前"的快照。Bean 出现了两个版本：代理持有的旧版和容器中的新版。
     * 这是不可接受的 bug。</p>
     *
     * <p><b>Spring 自身用 BPP 做了什么：</b></p>
     * <ul>
     *   <li>before: @Autowired 注入、@Resource 注入、*Aware 回调、JSR-303 校验</li>
     *   <li>after:  AOP 代理生成（@Transactional、@Cacheable、@Async）</li>
     * </ul>
     */
    @Bean
    public static BeanPostProcessor q01_loggingBpp() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName)
                    throws BeansException {
                if (bean instanceof Q01MyBean) {
                    System.out.println("  [6] BPP.before — 设计目的: 初始化前拦截（校验、Aware回调、准备代理）");
                }
                return bean;
            }

            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName)
                    throws BeansException {
                if (bean instanceof Q01MyBean) {
                    System.out.println("  [8] BPP.after  — 设计目的: 成品阶段拦截（★ 生成 AOP 代理的唯一正确时机）");
                }
                return bean;
            }
        };
    }
}
