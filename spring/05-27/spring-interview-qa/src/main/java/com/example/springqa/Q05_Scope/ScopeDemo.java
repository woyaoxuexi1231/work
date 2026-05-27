package com.example.springqa.Q05_Scope;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * <h1>Q5：作用域 — singleton/prototype/request/session</h1>
 *
 * <h2>面试点</h2>
 * <ul>
 *   <li>singleton / prototype / request / session 的原理？</li>
 *   <li>prototype Bean 的生命周期由谁管理？</li>
 *   <li>singleton Bean 里注入 prototype Bean，如何保证每次拿到新实例？</li>
 * </ul>
 *
 * <h2>核心原理</h2>
 *
 * <h3>singleton（默认）</h3>
 * <p>容器中只有一个实例。Spring 启动时创建，存放在 singletonObjects（一级缓存）。
 * 所有请求返回同一个对象。</p>
 *
 * <h3>prototype</h3>
 * <p>每次 getBean() 都创建新实例。Spring 只负责创建，不负责销毁——
 * <b>prototype Bean 的生命周期由调用方管理</b>。
 * @PreDestroy 不会被 Spring 调用。</p>
 *
 * <h3>request / session（Web 环境）</h3>
 * <p>request：每个 HTTP 请求一个实例，请求结束销毁。
 * session：每个 HTTP Session 一个实例，session 过期销毁。</p>
 *
 * <h2>singleton 里注入 prototype 的"失效"问题</h2>
 *
 * <p>这是经典面试题：</p>
 * <pre>
 * &#64;Component
 * class SingletonBean {
 *     &#64;Autowired
 *     private PrototypeBean proto;  // 只注入一次！后续都返回同一个
 * }
 * </pre>
 * <p>因为 singleton Bean 只创建一次，依赖注入也只执行一次——
 * prototype Bean 在注入时创建一次后，singleton 一直持有同一个引用。</p>
 *
 * <h3>三种解决方案</h3>
 * <ol>
 *   <li><b>@Lookup</b>：Spring 通过 CGLIB 生成子类，重写 @Lookup 方法，
 *       每次调用都从容器获取新实例。</li>
 *   <li><b>ObjectFactory / ObjectProvider</b>：注入一个工厂，需要时调用 getObject()。</li>
 *   <li><b>ApplicationContext.getBean()</b>：直接获取，但耦合了 Spring 容器。</li>
 * </ol>
 *
 * <h2>Spring 为什么这样设计？</h2>
 * <p>prototype 不管理完整生命周期是"职责分离"的设计：
 * Spring 是一个"容器"，不是"垃圾回收器"。如果 Spring 管理 prototype 的销毁，
 * 它需要持有每个 prototype 实例的引用，这会导致内存泄漏。
 * 销毁的职责交给创建方，谁创建谁负责销毁——这是合理的分工。</p>
 *
 * @author Spring Interview QA
 */
public class ScopeDemo {

    public static void main(String[] args) {
        System.out.println("========== Q5: 作用域 Demo ==========\n");

        AnnotationConfigApplicationContext ctx =
                new AnnotationConfigApplicationContext(DemoConfig.class);

        // ===== 1. singleton 每次获取同一个实例 =====
        SingletonService s1 = ctx.getBean(SingletonService.class);
        SingletonService s2 = ctx.getBean(SingletonService.class);
        System.out.println("singleton: s1 == s2 ? " + (s1 == s2));  // true

        // ===== 2. prototype 每次获取新实例 =====
        PrototypeService p1 = ctx.getBean(PrototypeService.class);
        PrototypeService p2 = ctx.getBean(PrototypeService.class);
        System.out.println("prototype: p1 == p2 ? " + (p1 == p2));  // false

        // ===== 3. singleton 里拿到新 prototype 的三种方式 =====
        MixedService mixed = ctx.getBean(MixedService.class);

        System.out.println("\n--- @Lookup 方式 ---");
        PrototypeService lp1 = mixed.getPrototypeByLookup();
        PrototypeService lp2 = mixed.getPrototypeByLookup();
        System.out.println("lp1 == lp2 ? " + (lp1 == lp2));  // false，每次新实例

        System.out.println("\n--- ObjectFactory 方式 ---");
        PrototypeService op1 = mixed.getPrototypeByFactory();
        PrototypeService op2 = mixed.getPrototypeByFactory();
        System.out.println("op1 == op2 ? " + (op1 == op2));  // false

        System.out.println("\n--- ObjectProvider 方式 ---");
        PrototypeService ovp1 = mixed.getPrototypeByProvider();
        PrototypeService ovp2 = mixed.getPrototypeByProvider();
        System.out.println("ovp1 == ovp2 ? " + (ovp1 == ovp2));  // false

        ctx.close();
        System.out.println("\n========== Demo 结束 ==========");
    }

    @Configuration
    @ComponentScan(basePackageClasses = ScopeDemo.class)
    static class DemoConfig {
    }

    // ================================================================

    @Component
    static class SingletonService {
        public SingletonService() {
            System.out.println("  SingletonService 创建");
        }
    }

    @Component
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    static class PrototypeService {
        public PrototypeService() {
            System.out.println("  PrototypeService 创建");
        }
    }

    /**
     * MixedService 是 singleton，但需要每次拿到新的 PrototypeService。
     *
     * 【Spring 设计意图】
     * @Lookup 方法需要被 CGLIB 重写——如果类被声明为 final，Lookup 会失效。
     * 这是 Spring 选择 CGLIB 作为默认代理方式的另一个原因——
     * 如果只有 JDK 动态代理（接口代理），@Lookup 根本无法工作。
     */
    @Component
    static class MixedService {

        // ===== 方案一：@Lookup（最优雅） =====

        /**
         * Spring 会通过 CGLIB 生成 MixedService 的子类，
         * 重写此方法，每次调用都执行 ctx.getBean(PrototypeService.class)。
         *
         * 【设计意图】
         * 为什么 @Lookup 需要 CGLIB？
         * 因为 JDK 动态代理只能代理接口方法，而 @Lookup 标记的是类的方法。
         * CGLIB 通过继承生成子类，可以重写任意非 final 方法。
         */
        @Lookup
        public PrototypeService getPrototypeByLookup() {
            // 这个方法体会被 CGLIB 子类覆盖，永远不会执行
            // Spring 文档建议返回 null 作为"占位"
            return null;
        }

        // ===== 方案二：ObjectFactory（函数式） =====

        /*
         * 注入 ObjectFactory，每次调用 getObject() 都会从容器获取新实例。
         *
         * 【设计意图】
         * ObjectFactory 是一个函数式接口（只有一个 getObject() 方法），
         * 实现非常轻量。Spring 内部大量使用它来实现"懒加载"——
         * 比如三级缓存中的 singletonFactories 就是
         * Map<String, ObjectFactory<?>>。
         */

        public PrototypeService getPrototypeByFactory() {
            // 直接使用 provider（它实现了 ObjectFactory 接口）
            return prototypeProvider.getObject();
        }

        // ===== 方案三：ObjectProvider（增强版 ObjectFactory） =====

        private final ObjectProvider<PrototypeService> prototypeProvider;

        /**
         * ObjectProvider 继承 ObjectFactory，增加了：
         * - getIfAvailable() — 可选获取
         * - getIfUnique()     — 确保只有一个候选
         * - stream() / iterator() — 流式遍历所有匹配 Bean
         *
         * Spring 4.3 引入 ObjectProvider 是为了解决 @Autowired(required=false)
         * 不够灵活的问题——有时候需要"如果有多个则选一个，没有则用默认值"的语义。
         */
        public MixedService(ObjectProvider<PrototypeService> prototypeProvider) {
            this.prototypeProvider = prototypeProvider;
        }

        public PrototypeService getPrototypeByProvider() {
            return prototypeProvider.getObject(); // 等效于 getBean()
        }
    }
}
