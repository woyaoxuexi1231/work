package com.example.springqa.Q09_InterceptorChain;

import org.springframework.stereotype.Component;

@Component
public class InterceptorChainDemo {

    private final Q09BusinessService service;

    public InterceptorChainDemo(Q09BusinessService service) {
        this.service = service;
    }

    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Q09: 拦截链 ===\n\n");

        sb.append("doBusiness() → ").append(service.doBusiness()).append(" ✅\n");
        sb.append("doBusinessWithBlock() → ").append(service.doBusinessWithBlock()).append(" (权限阻断)\n\n");

        sb.append("【执行顺序: 洋葱模型】\n");
        sb.append("@Order(1) 进入 → @Order(2) 进入 → @Order(3) 进入\n");
        sb.append("  → 目标方法\n");
        sb.append("@Order(3) 退出 → @Order(2) 退出 → @Order(1) 退出\n\n");
        sb.append("【proceed() 不调用 = 权限拦截】\n");
        sb.append("【事务默认 order=Integer.MAX_VALUE → 最内层 → 最小事务持有时间】\n");

        return sb.toString();
    }
}
