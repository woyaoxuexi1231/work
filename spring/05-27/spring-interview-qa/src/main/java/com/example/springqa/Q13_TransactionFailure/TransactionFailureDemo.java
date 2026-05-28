package com.example.springqa.Q13_TransactionFailure;

import org.springframework.stereotype.Component;

@Component
public class TransactionFailureDemo {

    private final Q13UserService service;

    public TransactionFailureDemo(Q13UserService service) {
        this.service = service;
    }

    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Q13: 事务失效场景 ===\n\n");

        service.outerMethod();
        sb.append("失效2: this 调用 → 绕过代理 ❌\n\n");

        service.caughtException();
        sb.append("失效3: 异常被 catch → Spring 看不到 → 不回滚 ❌\n\n");

        sb.append("失效1: 非 public 方法 → 代理无法拦截\n");
        sb.append("失效4: checked 异常默认不回滚 → rollbackFor=Exception.class\n");
        sb.append("失效5: 数据库引擎不支持(MyISAM)\n");
        sb.append("失效6: 多线程调用 → 事务绑定 ThreadLocal\n");
        sb.append("失效7: 类没被 Spring 管理\n\n");

        sb.append("【推荐】@Transactional(rollbackFor = Exception.class)\n");

        return sb.toString();
    }
}
