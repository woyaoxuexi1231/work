package com.example.springqa.Q12_TransactionPropagation;

import org.springframework.stereotype.Component;

@Component
public class TransactionPropagationDemo {

    private final Q12OrderService service;

    public TransactionPropagationDemo(Q12OrderService service) {
        this.service = service;
    }

    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Q12: 事务传播 ===\n\n");

        service.requiredOuter();
        sb.append("REQUIRED: 内外层共享同一事务\n\n");

        service.requiresNewOuter();
        sb.append("REQUIRES_NEW: 内层独立事务，外层被挂起\n\n");

        service.nestedOuter();
        sb.append("NESTED: 子事务回滚不影响主事务\n\n");

        sb.append("【NESTED 数据库要求】\n");
        sb.append("依赖 JDBC 3.0 Savepoint（H2/MySQL InnoDB/PG支持）\n");
        sb.append("【挂起实现】TransactionSynchronizationManager.unbindResource()\n");

        return sb.toString();
    }
}
