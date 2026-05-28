package com.example.springqa.Q14_TransactionPrinciple;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class Q14BusinessService {

    private final JdbcTemplate jdbc;

    public Q14BusinessService(JdbcTemplate q14_jdbcTemplate) {
        this.jdbc = q14_jdbcTemplate;
    }

    @Transactional(transactionManager = "q14_transactionManager")
    public void createUser(String name) {
        jdbc.update("INSERT INTO users VALUES (?, ?)", name.hashCode(), name);
        System.out.println("  创建用户: " + name);
    }

    @Transactional(transactionManager = "q14_transactionManager")
    public void createUserWithCallback(String name) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        System.out.println("  📬 afterCommit: 用户 " + name + " 创建成功，发送通知...");
                    }
                });
        jdbc.update("INSERT INTO users VALUES (?, ?)", name.hashCode(), name);
        System.out.println("  创建用户: " + name + "（回调已注册）");
    }
}
