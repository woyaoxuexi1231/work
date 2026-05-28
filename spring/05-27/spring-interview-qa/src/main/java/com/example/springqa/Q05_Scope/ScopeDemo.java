package com.example.springqa.Q05_Scope;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class ScopeDemo {

    private final ApplicationContext ctx;
    private final Q05MixedService mixed;

    public ScopeDemo(ApplicationContext ctx, Q05MixedService mixed) {
        this.ctx = ctx; this.mixed = mixed;
    }

    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Q05: 作用域 ===\n\n");

        Q05SingletonService s1 = ctx.getBean(Q05SingletonService.class);
        Q05SingletonService s2 = ctx.getBean(Q05SingletonService.class);
        sb.append("singleton: s1 == s2 ? ").append(s1 == s2).append("\n");

        Q05PrototypeService p1 = ctx.getBean(Q05PrototypeService.class);
        Q05PrototypeService p2 = ctx.getBean(Q05PrototypeService.class);
        sb.append("prototype: p1 == p2 ? ").append(p1 == p2).append("\n\n");

        Q05PrototypeService lp1 = mixed.getPrototypeByLookup();
        Q05PrototypeService lp2 = mixed.getPrototypeByLookup();
        sb.append("@Lookup:   lp1 == lp2 ? ").append(lp1 == lp2).append("\n");

        Q05PrototypeService op1 = mixed.getPrototypeByProvider();
        Q05PrototypeService op2 = mixed.getPrototypeByProvider();
        sb.append("ObjectProvider: op1 == op2 ? ").append(op1 == op2).append("\n\n");

        sb.append("【singleton 注入 prototype 的陷阱】\n");
        sb.append("singleton 只创建一次 → 依赖注入也只执行一次 →\n");
        sb.append("prototype 在注入时创建一次后被永久持有。\n");
        sb.append("解法: @Lookup / ObjectProvider / ApplicationContext.getBean\n");

        return sb.toString();
    }
}
