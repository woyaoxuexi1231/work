package com.example.springqa.Q05_Scope;

import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ScopeDemo {

    private final ApplicationContext ctx;
    private final Q05MixedService mixed;

    public ScopeDemo(ApplicationContext ctx, Q05MixedService mixed) {
        this.ctx = ctx; this.mixed = mixed;
    }

    @GetMapping("/q05")
    public String runDemo() {
        Q05SingletonService s1 = ctx.getBean(Q05SingletonService.class);
        Q05SingletonService s2 = ctx.getBean(Q05SingletonService.class);
        Q05PrototypeService p1 = ctx.getBean(Q05PrototypeService.class);
        Q05PrototypeService p2 = ctx.getBean(Q05PrototypeService.class);

        StringBuilder sb = new StringBuilder();
        sb.append("=== Q05: 作用域 ===\n\n");
        sb.append("singleton getBean() 两次: s1==s2 ? ").append(s1 == s2).append(" ✅ 同一实例\n");
        sb.append("prototype getBean() 两次: p1==p2 ? ").append(p1 == p2).append(" ❌ 不同实例\n\n");

        Q05PrototypeService lp1 = mixed.getPrototypeByLookup();
        Q05PrototypeService lp2 = mixed.getPrototypeByLookup();
        sb.append("@Lookup 两次调用: lp1==lp2 ? ").append(lp1 == lp2).append(" ❌ 每次新实例 ✅\n");

        Q05PrototypeService op1 = mixed.getPrototypeByProvider();
        Q05PrototypeService op2 = mixed.getPrototypeByProvider();
        sb.append("ObjectProvider 两次: op1==op2 ? ").append(op1 == op2).append(" ❌ 每次新实例 ✅\n\n");

        sb.append("singleton 注入 prototype → 只注入一次 → 后续都是同一个引用!\n");
        sb.append("解法: @Lookup / ObjectProvider / ApplicationContext.getBean()\n\n");
        sb.append("━━━ 完整分析 → /q05.html ━━━\n");
        return sb.toString();
    }
}
