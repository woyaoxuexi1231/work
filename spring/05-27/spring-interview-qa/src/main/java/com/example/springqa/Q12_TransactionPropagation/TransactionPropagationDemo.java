package com.example.springqa.Q12_TransactionPropagation;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;

/**
 * <h1>Q12：事务传播行为 — REQUIRED / REQUIRES_NEW / NESTED</h1>
 *
 * <h2>三种传播行为对比</h2>
 * <pre>
 * REQUIRED     — 有事务则加入，无则新建（默认）
 * REQUIRES_NEW — 挂起当前事务，新建独立事务
 * NESTED       — 嵌套子事务，依赖数据库 Savepoint
 * </pre>
 *
 * <h2>REQUIRES_NEW 挂起实现</h2>
 * <p>TransactionSynchronizationManager.unbindResource() →
 * 保存 SuspendedResourcesHolder → 开启新事务 → 恢复。</p>
 */
@Component
public class TransactionPropagationDemo {

    private final Q12OrderService service;

    public TransactionPropagationDemo(Q12OrderService service) {
        this.service = service;
    }

    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Q12: 事务传播 ===\n\n");
        sb.append("查看控制台日志确认事务行为：\n\n");

        sb.append("--- REQUIRED（默认）---\n");
        service.requiredOuter();
        sb.append("  requiredOuter 和 requiredInner 共享同一个事务\n\n");

        sb.append("--- REQUIRES_NEW ---\n");
        service.requiresNewOuter();
        sb.append("  requiresNewInner 有独立事务（外层事务被挂起）\n\n");

        sb.append("--- NESTED ---\n");
        service.nestedOuter();
        sb.append("  nestedInner 子事务回滚不影响主事务\n\n");

        sb.append("【NESTED 数据库要求】\n");
        sb.append("NESTED 依赖 JDBC 3.0 Savepoint。H2/MySQL InnoDB/PostgreSQL 支持。\n");
        sb.append("不支持 Savepoint 的数据库会降级为 REQUIRED。\n");

        return sb.toString();
    }

    // ================================================================
    @Configuration
    static class Q12Config {
        @Bean
        public DataSource q12_dataSource() {
            return new EmbeddedDatabaseBuilder()
                    .setType(EmbeddedDatabaseType.H2).setName("q12_propagation").build();
        }

        @Bean
        public PlatformTransactionManager q12_transactionManager(DataSource q12_dataSource) {
            return new DataSourceTransactionManager(q12_dataSource);
        }

        @Bean
        public JdbcTemplate q12_jdbcTemplate(DataSource q12_dataSource) {
            return new JdbcTemplate(q12_dataSource);
        }
    }

    @Component
    static class Q12OrderService {

        @Transactional(propagation = Propagation.REQUIRED,
                transactionManager = "q12_transactionManager")
        public void requiredOuter() {
            printTxStatus("requiredOuter");
            requiredInner();
        }

        @Transactional(propagation = Propagation.REQUIRED,
                transactionManager = "q12_transactionManager")
        public void requiredInner() {
            printTxStatus("requiredInner（同一事务）");
        }

        @Transactional(propagation = Propagation.REQUIRED,
                transactionManager = "q12_transactionManager")
        public void requiresNewOuter() {
            printTxStatus("requiresNewOuter（事务A）");
            requiresNewInner();
            printTxStatus("requiresNewOuter 恢复（事务A仍在）");
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW,
                transactionManager = "q12_transactionManager")
        public void requiresNewInner() {
            printTxStatus("requiresNewInner（独立事务B——A被挂起）");
        }

        @Transactional(propagation = Propagation.REQUIRED,
                transactionManager = "q12_transactionManager")
        public void nestedOuter() {
            printTxStatus("nestedOuter（主事务）");
            try { nestedInner(); } catch (Exception ignored) {}
            printTxStatus("nestedOuter（主事务仍可提交）");
        }

        @Transactional(propagation = Propagation.NESTED,
                transactionManager = "q12_transactionManager")
        public void nestedInner() {
            printTxStatus("nestedInner（子事务）");
            throw new RuntimeException("模拟子事务回滚到 savepoint");
        }

        private void printTxStatus(String ctx) {
            System.out.println("  [" + ctx + "] 活跃="
                    + TransactionSynchronizationManager.isActualTransactionActive());
        }
    }
}
