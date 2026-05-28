package com.example.springqa.Q02_CircularDependency;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * <h1>Q2：循环依赖 — 三级缓存的精妙设计</h1>
 *
 * <h2>三级缓存</h2>
 * <pre>
 * 一级 singletonObjects     — 完全初始化好的成品 Bean
 * 二级 earlySingletonObjects — 已实例化但未完成初始化的"早期引用"
 * 三级 singletonFactories    — ObjectFactory，需要时才生成早期引用
 * </pre>
 *
 * <h2>为什么必须三级（不能只用二级）？</h2>
 * <p>核心原因：<b>AOP 代理</b>。三级缓存储的是 ObjectFactory，
 * 只在发生循环依赖时才调用 getObject()。此时可统一判断是否需要代理，
 * 保证 B 拿到的 A 和最终放入一级缓存的 A 是同一个对象。
 * 如果只有二级缓存，A 的原始对象先暴露给 B，之后 A 生成了代理——
 * 同一个 Bean 出现了两个版本，违反单例语义。</p>
 *
 * <h2>构造器注入为什么不行？</h2>
 * <p>构造器调用是实例化第一步，A 需要 B、B 需要 A——
 * 双方都无法完成实例化，没机会放入缓存。</p>
 */
@Component
public class CircularDependencyDemo {

    private final ApplicationContext ctx;

    public CircularDependencyDemo(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Q02: 循环依赖 ===\n\n");

        ServiceA a = ctx.getBean(ServiceA.class);
        ServiceB b = ctx.getBean(ServiceB.class);

        sb.append("ServiceA 中的 ServiceB: ").append(a.getServiceB() != null ? "✅ 已注入" : "❌ null").append("\n");
        sb.append("ServiceB 中的 ServiceA: ").append(b.getServiceA() != null ? "✅ 已注入" : "❌ null").append("\n");
        sb.append("a == b.getServiceA(): ").append(a == b.getServiceA()).append("\n");
        sb.append("b == a.getServiceB(): ").append(b == a.getServiceB()).append("\n\n");

        sb.append("【流程推演】\n");
        sb.append("1. 创建 A → 实例化 → 放入三级缓存(ObjectFactory)\n");
        sb.append("2. 填充 A 的属性 → 发现需要 B → 去创建 B\n");
        sb.append("3. 创建 B → 实例化 → 填充 B 的属性 → 发现需要 A\n");
        sb.append("4. B 从三级缓存获取 ObjectFactory → getObject() → 拿到 A 的早期引用\n");
        sb.append("5. B 完成初始化 → 放入一级缓存\n");
        sb.append("6. 回到 A → 用 B 完成填充 → A 执行 postProcessAfterInitialization\n");
        sb.append("   → 如有 AOP 代理在此生成 → 放入一级缓存\n\n");

        sb.append("【面试关键】\n");
        sb.append("Q: 为什么必须三级缓存？\n");
        sb.append("A: 因为 AOP 代理。如果只有二级缓存，步骤2就必须暴露原始 A，\n");
        sb.append("   步骤6生成的代理 A 和 B 持有的原始 A 是两个不同对象，违反单例。\n");
        sb.append("   三级缓存的 ObjectFactory 保证：无论谁先拿到引用，拿到的都是同一个代理。\n");

        return sb.toString();
    }

    // ================================================================
    @Component
    static class ServiceA {
        @Autowired
        private ServiceB serviceB;

        public ServiceA() {
            System.out.println("  ServiceA 构造器");
        }

        public ServiceB getServiceB() { return serviceB; }
    }

    @Component
    static class ServiceB {
        @Autowired
        private ServiceA serviceA;

        public ServiceB() {
            System.out.println("  ServiceB 构造器");
        }

        public ServiceA getServiceA() { return serviceA; }
    }
}
