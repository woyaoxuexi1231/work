package com.example.springqa.Q12_TransactionPropagation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransactionPropagationDemo {

    private final Q12OrderService service;

    public TransactionPropagationDemo(Q12OrderService service) { this.service = service; }

    @GetMapping("/q12")
    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Q12: 事务传播 ===\n\n");
        sb.append("--- REQUIRED（默认）---\n");  service.requiredOuter();
        sb.append("→ 内外层共享同一事务\n\n");
        sb.append("--- REQUIRES_NEW ---\n");  service.requiresNewOuter();
        sb.append("→ 内层独立事务，外层被挂起\n\n");
        sb.append("--- NESTED ---\n");  service.nestedOuter();
        sb.append("→ 子事务回滚不影响主事务\n\n");
        sb.append("REQUIRES_NEW 挂起: TransactionSynchronizationManager.unbindResource()\n");
        sb.append("NESTED 依赖 JDBC 3.0 Savepoint (H2/MySQL/PG 支持)\n\n");
        sb.append("━━━ 完整分析 → /q12.html ━━━\n");
        return sb.toString();
    }
}
