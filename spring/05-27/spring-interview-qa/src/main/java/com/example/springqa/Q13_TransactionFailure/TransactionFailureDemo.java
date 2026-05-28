package com.example.springqa.Q13_TransactionFailure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.IOException;

/**
 * <h1>Q13：@Transactional 失效的 5+ 场景</h1>
 *
 * <h2>失效场景</h2>
 * <ol>
 *   <li>方法非 public — 代理只能拦截 public 方法</li>
 *   <li>自调用 (this.xxx) — 绕过代理</li>
 *   <li>异常被 catch 未抛出 — Spring 感知不到异常</li>
 *   <li>rollbackFor 设置错误 — 默认只回滚 RuntimeException</li>
 *   <li>数据库引擎不支持 — MyISAM 不支持事务</li>
 *   <li>多线程调用 — 事务绑定 ThreadLocal，新线程没有</li>
 *   <li>类没被 Spring 管理 — new 出来的对象没有代理</li>
 * </ol>
 *
 * <h2>为什么默认只回滚 RuntimeException？</h2>
 * <p>EJB 时代的设计遗产——Checked Exception 表示"可恢复的业务异常"。
 * 推荐显式指定 @Transactional(rollbackFor = Exception.class)。</p>
 */
@Component
public class TransactionFailureDemo {

    private final Q13UserService service;

    public TransactionFailureDemo(Q13UserService service) {
        this.service = service;
    }

    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Q13: 事务失效场景 ===\n\n");

        // 失效 2：自调用
        sb.append("【失效 2: 自调用】\n");
        service.outerMethod();
        sb.append("  outerMethod 有事务，但 this.innerTransactional() 绕过了代理 → 失效 ❌\n\n");

        // 失效 3：异常被 catch
        sb.append("【失效 3: 异常被 catch 吞掉】\n");
        service.caughtException();
        sb.append("  RuntimeException 被 catch → Spring 没看到异常 → 事务提交 ❌\n\n");

        // 失效 4：checked 异常
        sb.append("【失效 4: checked 异常默认不回滚】\n");
        sb.append("  IOException 是 checked exception → 默认只回滚 RuntimeException\n\n");

        // 修复
        sb.append("【修复: rollbackFor = Exception.class】\n");
        sb.append("  @Transactional(rollbackFor = Exception.class) → 所有异常都回滚 ✅\n\n");

        sb.append("【为什么非 public 方法不能增强？】\n");
        sb.append("JDK 代理只能代理接口（隐式 public），CGLIB 虽能代理 protected，\n");
        sb.append("但 Spring 统一限制为 public——这是契约清晰性的考虑。\n");

        return sb.toString();
    }

    // ================================================================
    @Configuration
    static class Q13Config {
        @Bean
        public DataSource q13_dataSource() {
            return new EmbeddedDatabaseBuilder()
                    .setType(EmbeddedDatabaseType.H2).setName("q13_failure").build();
        }

        @Bean
        public PlatformTransactionManager q13_transactionManager(DataSource q13_dataSource) {
            return new DataSourceTransactionManager(q13_dataSource);
        }

        @Bean
        public JdbcTemplate q13_jdbcTemplate(DataSource q13_dataSource) {
            JdbcTemplate jt = new JdbcTemplate(q13_dataSource);
            jt.execute("CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY, name VARCHAR(50))");
            return jt;
        }
    }

    @Component
    static class Q13UserService {

        @Transactional(transactionManager = "q13_transactionManager")
        public void outerMethod() {
            System.out.println("  outerMethod 有事务");
            this.innerTransactional(); // ❌ this 绕过代理
        }

        @Transactional(transactionManager = "q13_transactionManager")
        public void innerTransactional() {
            System.out.println("  innerTransactional 本应有独立事务但失效了");
        }

        @Transactional(transactionManager = "q13_transactionManager")
        public void caughtException() {
            try {
                throw new RuntimeException("业务异常");
            } catch (RuntimeException e) {
                System.out.println("  ⚠️ 异常被 catch 了: " + e.getMessage());
                // 事务不会回滚！
            }
        }

        @Transactional(transactionManager = "q13_transactionManager")
        public void checkedExceptionNoRollback() {
            try {
                throw new IOException("checked exception");
            } catch (IOException e) {
                System.out.println("  IOException 默认不回滚（需 rollbackFor = Exception.class）");
            }
        }
    }
}
