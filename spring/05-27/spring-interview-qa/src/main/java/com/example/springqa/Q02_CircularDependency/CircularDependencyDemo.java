package com.example.springqa.Q02_CircularDependency;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * <h1>Q2：循环依赖 — 三级缓存的精妙设计</h1>
 *
 * <h2>面试点</h2>
 * <ul>
 *   <li>Spring 如何解决单例 Bean 的循环依赖？</li>
 *   <li>三级缓存分别存什么？为什么必须三级，二级行不行？</li>
 *   <li>AOP 代理在循环依赖中何时生成？</li>
 *   <li>构造器注入为什么无法解决循环依赖？</li>
 * </ul>
 *
 * <h2>三级缓存结构</h2>
 * <pre>
 * 一级缓存 singletonObjects    : Map&lt;String, Object&gt;
 *    存放完全初始化好的单例 Bean（成品）
 *
 * 二级缓存 earlySingletonObjects: Map&lt;String, Object&gt;
 *    存放"早期引用"——已实例化但未完成初始化的 Bean（半成品）
 *
 * 三级缓存 singletonFactories   : Map&lt;String, ObjectFactory&lt;?&gt;&gt;
 *    存放能生成"早期引用"的工厂。当有循环依赖时，通过工厂提前暴露 Bean
 * </pre>
 *
 * <h2>为什么必须三级缓存（不能只用二级）？</h2>
 *
 * <p>核心原因：<b>AOP 代理</b>。</p>
 *
 * <p>假设 A ↔ B 循环依赖，且 A 需要 AOP 代理：</p>
 * <pre>
 * 1. 创建 A → 实例化 → 放入三级缓存（一个 ObjectFactory，它能生成 A 的代理）
 * 2. 填充 A 的属性 → 发现需要 B → 去创建 B
 * 3. 创建 B → 实例化 → 填充 B 的属性 → 发现需要 A
 * 4. B 从三级缓存取出 ObjectFactory → 调用 getObject()
 *    → 此时 Spring 判断 A 是否需要代理：
 *       - 如果需要 → 生成 A 的代理对象 → 放入二级缓存 → 返回代理
 *       - 如果不需要 → 返回原始 A → 放入二级缓存
 * 5. B 拿到 A 的引用（可能是代理），完成初始化 → 放入一级缓存
 * 6. 回到 A：用 B 完成属性填充 → A 执行 postProcessAfterInitialization
 *    → 生成 A 的 AOP 代理 → 放入一级缓存
 * </pre>
 *
 * <p><b>为什么不直接用二级缓存？</b></p>
 * <p>如果只有二级缓存，步骤 2 就必须把 A 的"原始对象"直接暴露到二级缓存。
 * 到步骤 6 时，B 持有的 A 是原始对象，而一级缓存里的 A 是代理对象——
 * <b>同一个 Bean 出现了两个版本</b>，违反单例语义。</p>
 *
 * <p>三级缓存的巧妙之处在于：它存的是一个 <b>ObjectFactory（工厂）</b>，
 * 而不是直接存对象。只有当真正需要"早期引用"时（即发生循环依赖时），
 * 才调用工厂的 getObject()。这时可以统一判断是否需要代理，
 * <b>保证无论谁先拿到引用，拿到的都是同一个代理对象</b>。</p>
 *
 * <h2>构造器注入为什么无法解决循环依赖？</h2>
 * <p>因为构造器调用是实例化的第一步。A 的构造器需要 B，B 的构造器需要 A——
 * 双方都无法完成实例化，根本没有机会放入三级缓存。
 * setter/字段注入则不同：先实例化（可以放入缓存），再注入依赖（可以从缓存获取）。</p>
 *
 * @author Spring Interview QA
 */
public class CircularDependencyDemo {

    /**
     * 运行本 Demo: 直接执行 main 方法。
     * ServiceA 和 ServiceB 互相通过字段 @Autowired 注入，构成循环依赖。
     * Spring 通过三级缓存成功解决，不会报错。
     *
     * 如果你想看构造器注入循环依赖的失败案例：
     * 把 @Autowired 从字段移到构造器上，重新运行就会看到 BeanCurrentlyInCreationException。
     */
    public static void main(String[] args) {
        System.out.println("========== Q2: 循环依赖 Demo ==========\n");

        AnnotationConfigApplicationContext ctx =
                new AnnotationConfigApplicationContext(DemoConfig.class);

        ServiceA a = ctx.getBean(ServiceA.class);
        ServiceB b = ctx.getBean(ServiceB.class);

        // 验证循环依赖成功解决
        System.out.println("\n>>> ServiceA 中的 ServiceB 引用: " + a.getServiceB());
        System.out.println(">>> ServiceB 中的 ServiceA 引用: " + b.getServiceA());
        System.out.println(">>> 两者都不为 null，循环依赖解决成功！");

        // 对比引用一致性
        System.out.println(">>> a == b.getServiceA(): " + (a == b.getServiceA()));
        System.out.println(">>> b == a.getServiceB(): " + (b == a.getServiceB()));

        ctx.close();
        System.out.println("\n========== Demo 结束 ==========");
    }

    @Configuration
    @ComponentScan(basePackageClasses = CircularDependencyDemo.class)
    static class DemoConfig {
    }

    // ================================================================
    // 循环依赖的两个 Service
    // ================================================================

    @Component
    static class ServiceA {

        /*
         * 字段注入：此时 ServiceA 已经实例化完毕，放入三级缓存。
         * 当 Spring 发现 ServiceB 的字段需要 ServiceA 时，
         * 就从三级缓存获取 ServiceA 的早期引用。
         *
         * 【对比构造器注入】
         * 如果改为构造器注入：
         *   public ServiceA(ServiceB b) { ... }
         * 那么在实例化 ServiceA 时就需要 ServiceB，而 ServiceB 又需要 ServiceA——
         * 两者都无法完成实例化，更没有机会放入缓存，必然失败。
         */
        @Autowired
        private ServiceB serviceB;

        public ServiceA() {
            System.out.println("  ServiceA 构造器: 实例化完成（注意：此时 serviceB 还是 null）");
        }

        public ServiceB getServiceB() {
            return serviceB;
        }
    }

    @Component
    static class ServiceB {

        @Autowired
        private ServiceA serviceA;

        public ServiceB() {
            System.out.println("  ServiceB 构造器: 实例化完成（注意：此时 serviceA 还是 null）");
        }

        public ServiceA getServiceA() {
            return serviceA;
        }
    }
}
