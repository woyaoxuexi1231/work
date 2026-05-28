package com.example.springqa.Q07_ProxySelection;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class ProxySelectionDemo {

    private final ApplicationContext ctx;

    public ProxySelectionDemo(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Q07: 代理选择 ===\n\n");

        Q07GreetingService proxy = ctx.getBean(Q07GreetingService.class);
        String clz = proxy.getClass().getName();
        sb.append("代理类型: ").append(clz).append("\n");
        sb.append("是 JDK Proxy? ").append(java.lang.reflect.Proxy.isProxyClass(proxy.getClass())).append("\n");
        sb.append("是 CGLIB? ").append(clz.contains("CGLIB") || clz.contains("$$")).append("\n\n");

        sb.append("当前: proxyTargetClass=true → CGLIB\n\n");
        sb.append("【对比】\n");
        sb.append("JDK 动态代理: 反射+Proxy，必须接口，零依赖\n");
        sb.append("CGLIB: ASM 生成子类，不能是 final\n\n");
        sb.append("【Spring Boot 2.x 为什么默认 CGLIB？】\n");
        sb.append("1.x 默认 JDK → Service 没接口时 @Transactional 静默失效\n");
        sb.append("2.x 改 CGLIB → 最小惊讶原则——加了注解就应该生效\n");

        return sb.toString();
    }
}
