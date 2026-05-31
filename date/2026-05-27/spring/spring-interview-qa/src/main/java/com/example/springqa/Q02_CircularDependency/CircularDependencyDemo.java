package com.example.springqa.Q02_CircularDependency;

import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CircularDependencyDemo {

    private final ApplicationContext ctx;

    public CircularDependencyDemo(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    @GetMapping("/q02")
    public String runDemo() {
        Q02ServiceA a = ctx.getBean(Q02ServiceA.class);
        Q02ServiceB b = ctx.getBean(Q02ServiceB.class);

        StringBuilder sb = new StringBuilder();
        sb.append("=== Q02: 循环依赖（实时验证） ===\n\n");
        sb.append("Q02ServiceA 中的 ServiceB: ").append(a.getServiceB() != null ? "✅ 已注入" : "❌ null").append("\n");
        sb.append("Q02ServiceB 中的 ServiceA: ").append(b.getServiceA() != null ? "✅ 已注入" : "❌ null").append("\n");
        sb.append("a == b.getServiceA(): ").append(a == b.getServiceA()).append(" ← 同一个对象\n");
        sb.append("b == a.getServiceB(): ").append(b == a.getServiceB()).append(" ← 同一个对象\n\n");

        sb.append("【三级缓存流程推演】\n");
        sb.append("1. 创建 A → 实例化 → 三级缓存(ObjectFactory)\n");
        sb.append("2. 填充 A → 需要 B → 去创建 B\n");
        sb.append("3. 创建 B → 实例化 → 填充 B → 需要 A\n");
        sb.append("4. B 从三级缓存取 ObjectFactory → getObject() → A 早期引用\n");
        sb.append("5. B 完成 → 一级缓存；A 用 B 完成填充 → AOP代理 → 一级缓存\n\n");
        sb.append("━━━ 完整分析 → /q02.html ━━━\n");
        return sb.toString();
    }
}
