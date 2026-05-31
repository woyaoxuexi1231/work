package com.example.springqa.Q09_InterceptorChain;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InterceptorChainDemo {

    private final Q09BusinessService service;

    public InterceptorChainDemo(Q09BusinessService service) { this.service = service; }

    @GetMapping("/q09")
    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Q09: 拦截链 ===\n\n");
        sb.append("doBusiness()           → ").append(service.doBusiness()).append(" ✅\n");
        sb.append("doBusinessWithBlock()  → ").append(service.doBusinessWithBlock()).append(" (权限阻断)\n\n");
        sb.append("洋葱模型: @Order(1)进入→@Order(2)进入→目标→@Order(2)退出→@Order(1)退出\n");
        sb.append("proceed() 不调用 = 权限拦截 — 后续切面+目标方法全部跳过\n");
        sb.append("@Transactional order=Integer.MAX_VALUE → 总是在最内层\n\n");
        sb.append("━━━ 完整分析 → /q09.html ━━━\n");
        return sb.toString();
    }
}
