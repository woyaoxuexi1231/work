package com.example.springqa.Q02_CircularDependency;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class CircularDependencyDemo {

    private final ApplicationContext ctx;

    public CircularDependencyDemo(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Q02: 循环依赖 ===\n\n");

        Q02ServiceA a = ctx.getBean(Q02ServiceA.class);
        Q02ServiceB b = ctx.getBean(Q02ServiceB.class);

        sb.append("ServiceA 中的 ServiceB: ").append(a.getServiceB() != null ? "✅" : "❌").append("\n");
        sb.append("ServiceB 中的 ServiceA: ").append(b.getServiceA() != null ? "✅" : "❌").append("\n");
        sb.append("a == b.getServiceA(): ").append(a == b.getServiceA()).append("\n");
        sb.append("b == a.getServiceB(): ").append(b == a.getServiceB()).append("\n\n");

        sb.append("【三级缓存流程】\n");
        sb.append("1. 创建 A → 实例化 → 放入三级缓存(ObjectFactory)\n");
        sb.append("2. 填充 A → 需要 B → 去创建 B\n");
        sb.append("3. 创建 B → 实例化 → 填充 B → 需要 A\n");
        sb.append("4. B 从三级缓存取 ObjectFactory → getObject() → 拿到 A 早期引用\n");
        sb.append("5. B 完成初始化 → 一级缓存\n");
        sb.append("6. A 用 B 完成填充 → postProcessAfterInitialization → AOP代理\n\n");

        sb.append("【为什么必须三级缓存？】\n");
        sb.append("AOP 代理。三级缓存的 ObjectFactory 保证无论谁先拿到引用，\n");
        sb.append("拿到的都是同一个代理对象。二级缓存无法解决。\n");

        return sb.toString();
    }
}
