package com.example.springqa.Q07_ProxySelection;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * <h1>Q7：代理选择 — JDK 动态代理 vs CGLIB</h1>
 *
 * <h2>对比</h2>
 * <pre>
 * JDK 动态代理 — 反射 + Proxy.newProxyInstance，必须有接口
 * CGLIB        — ASM 生成子类，类不能是 final
 * </pre>
 *
 * <h2>Spring Boot 2.x 为什么默认改为 CGLIB？</h2>
 * <p>1.x 默认 JDK 代理 → Service 没实现接口时 @Transactional 静默失效。
 * 2.x 改为 CGLIB → 遵循"最小惊讶原则"——加了注解就应该生效。</p>
 *
 * <h2>proxyTargetClass=true 时接口存在？</h2>
 * <p>仍然用 CGLIB 生成子类代理。</p>
 */
@Component
public class ProxySelectionDemo {

    private final ApplicationContext ctx;

    public ProxySelectionDemo(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Q07: 代理选择 ===\n\n");

        GreetingService proxy = ctx.getBean(GreetingService.class);
        String proxyClass = proxy.getClass().getName();

        sb.append("代理类型: ").append(proxyClass).append("\n");
        sb.append("是 JDK Proxy? ").append(java.lang.reflect.Proxy.isProxyClass(proxy.getClass())).append("\n");
        sb.append("是 CGLIB? ").append(proxyClass.contains("CGLIB") || proxyClass.contains("$$")).append("\n\n");

        sb.append("当前配置: proxyTargetClass=true → CGLIB 代理\n\n");

        sb.append("【Spring 为什么提供两种代理？】\n");
        sb.append("JDK 是 JDK 内置（零依赖），但只能代理接口。\n");
        sb.append("CGLIB 能代理任意类（不能是 final）。\n");
        sb.append("Spring 通过 AopProxy 接口 + 策略模式选择合适的实现。\n\n");

        sb.append("【Spring Boot 2.x 默认 CGLIB 的设计哲学】\n");
        sb.append("\"默认行为应符合大多数人的直觉\"——加了 @Transactional 就该生效。\n");
        sb.append("代价：类不能是 final，所以 Spring 生态提倡不要随意 final 类。\n");

        return sb.toString();
    }

    // ================================================================
    @Component
    static class GreetingService {
        public String greet(String name) {
            return "Hello, " + name;
        }
    }
}
