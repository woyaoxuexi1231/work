package com.example.springqa.Q14_TransactionPrinciple;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;

/**
 * <h1>Q14：事务实现原理 — AOP 织入 + TransactionSynchronization</h1>
 *
 * <h2>@Transactional 的 AOP 织入</h2>
 * <pre>
 * @EnableTransactionManagement
 *   → 注册 InfrastructureAdvisorAutoProxyCreator (BeanPostProcessor)
 *   → 注册 BeanFactoryTransactionAttributeSourceAdvisor:
 *      Pointcut: 匹配 @Transactional 方法
 *      Advice:   TransactionInterceptor
 *   → Bean 初始化 → 匹配切点 → 创建代理
 *   → 调用时 TransactionInterceptor.invoke():
 *      getTransaction() → 执行业务 → commit/rollback
 * </pre>
 *
 * <h2>TransactionSynchronizationManager</h2>
 * <p>使用 ThreadLocal 管理：事务连接、事务名称、只读标志、隔离级别、
 * TransactionSynchronization 回调链。</p>
 */
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
        sb.append("createUser(\"Alice\") 执行完毕\n\n");

        service.createUserWithCallback("Bob");
        sb.append("createUserWithCallback(\"Bob\"): 查看控制台 afterCommit 回调\n\n");

        sb.append("【@Transactional 的 AOP 织入流程】\n");
        sb.append("1. @EnableTransactionManagement → 注册 AutoProxyCreator + Advisor\n");
        sb.append("2. Advisor: TransactionAttributeSourcePointcut + TransactionInterceptor\n");
        sb.append("3. Bean 初始化 → postProcessAfterInitialization → 匹配 → 创建代理\n");
        sb.append("4. 方法调用 → TransactionInterceptor.invoke():\n");
        sb.append("   a. getTransaction() 开启/加入事务\n");
        sb.append("   b. invocation.proceed() 执行目标方法\n");
        sb.append("   c. commit/rollback\n\n");

        sb.append("【afterCommit 典型应用】\n");
        sb.append("发送 MQ 消息——只在事务真正提交后才发送，\n");
        sb.append("避免事务回滚后消息已发出导致数据不一致。\n");

        return sb.toString();
    }

    // ================================================================
    @Configuration
    static class Q14Config {
        @Bean
        public DataSource q14_dataSource() {
            return new EmbeddedDatabaseBuilder()
                    .setType(EmbeddedDatabaseType.H2).setName("q14_principle").build();
        }

        @Bean
        public PlatformTransactionManager q14_transactionManager(DataSource q14_dataSource) {
            return new DataSourceTransactionManager(q14_dataSource);
        }

        @Bean
        public JdbcTemplate q14_jdbcTemplate(DataSource q14_dataSource) {
            JdbcTemplate jt = new JdbcTemplate(q14_dataSource);
            jt.execute("CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY, name VARCHAR(50))");
            return jt;
        }
    }

    @Component
    static class Q14BusinessService {

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
}
