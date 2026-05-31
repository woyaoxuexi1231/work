package com.example.springqa.Q13_TransactionFailure;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

@Component
public class Q13UserService {

    @Transactional(transactionManager = "q13_transactionManager")
    public void outerMethod() {
        System.out.println("  outerMethod 有事务");
        this.innerTransactional(); // ❌ this 绕过代理
    }

    @Transactional(transactionManager = "q13_transactionManager")
    public void innerTransactional() {
        System.out.println("  innerTransactional 失效");
    }

    @Transactional(transactionManager = "q13_transactionManager")
    public void caughtException() {
        try {
            throw new RuntimeException("业务异常");
        } catch (RuntimeException e) {
            System.out.println("  ⚠️ 异常被 catch: " + e.getMessage());
        }
    }
}
