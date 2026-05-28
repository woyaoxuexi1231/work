package com.example.springqa.Q14_TransactionPrinciple;

import org.springframework.stereotype.Component;

@Component
public class TransactionPrincipleDemo {

    private final Q14BusinessService service;

    public TransactionPrincipleDemo(Q14BusinessService service) {
        this.service = service;
    }

    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Q14: 事务原理 ===\n\n");

        service.createUser("Alice");
        sb.append("createUser → 事务正常提交\n\n");

        service.createUserWithCallback("Bob");
        sb.append("createUserWithCallback → afterCommit 回调已触发（见控制台）\n\n");

        sb.append("【@Transactional 的 AOP 织入】\n");
        sb.append("@EnableTransactionManagement → 注册 Advisor\n");
        sb.append("  Pointcut: TransactionAttributeSourcePointcut\n");
        sb.append("  Advice:   TransactionInterceptor\n");
        sb.append("Bean 初始化 → postProcessAfterInitialization → 创建代理\n");
        sb.append("调用时: getTransaction() → 执行业务 → commit/rollback\n\n");
        sb.append("【afterCommit 典型应用】\n");
        sb.append("发送 MQ——事务真正提交后才发送，避免回滚后消息已发出。\n");

        return sb.toString();
    }
}
