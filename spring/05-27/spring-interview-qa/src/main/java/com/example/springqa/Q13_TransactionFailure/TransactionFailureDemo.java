package com.example.springqa.Q13_TransactionFailure;

import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.IOException;

/**
 * <h1>Q13：@Transactional 失效的 5+ 场景</h1>
 *
 * <h2>面试点</h2>
 * <p>列举至少 5 种 @Transactional 失效的情况，并解释原因。</p>
 *
 * <h2>失效场景汇总</h2>
 * <pre>
 * 1. ❌ 方法非 public        → CGLIB/JDK 代理只能拦截 public 方法
 * 2. ❌ 自调用（this.xxx()） → 绕过代理，详见 Q08
 * 3. ❌ 异常被 catch 未抛出  → Spring 只在 RuntimeException/Error 时回滚
 * 4. ❌ rollbackFor 设置错误 → 默认不回滚 checked 异常
 * 5. ❌ 数据库引擎不支持事务  → MyISAM 不支持事务
 * 6. ❌ 多线程调用          → 事务绑定在 ThreadLocal，新线程拿不到
 * 7. ❌ 类没有被 Spring 管理  → new 出来的对象不会被代理
 * 8. ❌ propagation 设置错误 → 如 NEVER/SUPPORTS 等
 * </pre>
 *
 * <h2>Spring 为什么这样设计？</h2>
 *
 * <h3>为什么默认只回滚 RuntimeException？</h3>
 * <p>这是 EJB 时代留下的设计遗产。Checked Exception 在 EJB 中表示
 * "可恢复的业务异常"，不应该回滚事务。Spring 沿用了这个惯例。</p>
 * <p>但实际开发中这经常导致问题——开发者抛出了 SQLException，
 * 以为事务会回滚，结果没有。所以最佳实践是显式指定
 * {@code @Transactional(rollbackFor = Exception.class)}。</p>
 *
 * <h3>为什么非 public 方法不能增强？</h3>
 * <p>JDK 动态代理只能代理接口方法（隐式 public），
 * CGLIB 虽然能代理 protected/package-private 方法，
 * 但 Spring 选择统一限制为 public——这是契约清晰性的考虑。</p>
 *
 * @author Spring Interview QA
 */
public class TransactionFailureDemo {

    public static void main(String[] args) {
        System.out.println("========== Q13: 事务失效场景 Demo ==========\n");

        AnnotationConfigApplicationContext ctx =
                new AnnotationConfigApplicationContext(DemoConfig.class);

        UserService service = ctx.getBean(UserService.class);

        // ===== 失效 1：非 public 方法 =====
        System.out.println("--- 失效 1：非 public 方法 ---");
        try {
            service.callNonPublic();
        } catch (Exception e) {
            System.out.println("  (非 public 方法上的 @Transactional 不会生效)");
        }

        // ===== 失效 2：自调用 =====
        System.out.println("\n--- 失效 2：自调用 (this.xxx) ---");
        service.outerMethod();  // 内部调用 this.innerTransactional() → 事务不生效

        // ===== 失效 3：异常被 catch =====
        System.out.println("\n--- 失效 3：异常被 catch 吞掉 ---");
        service.caughtException();  // 异常被 catch 了，事务不会回滚！

        // ===== 失效 4：rollbackFor 未包含 checked 异常 =====
        System.out.println("\n--- 失效 4：checked 异常未回滚 ---");
        service.checkedExceptionNoRollback();  // IOException 是 checked，默认不回滚

        // ===== 修复：显式 rollbackFor =====
        System.out.println("\n--- 修复：rollbackFor = Exception.class ---");
        service.checkedExceptionWithRollback();

        // ===== 解决方案：通过代理调用 =====
        System.out.println("\n--- 修复自调用：通过 AopContext.currentProxy() ---");
        service.outerMethodFixed();

        ctx.close();
        System.out.println("\n========== Demo 结束 ==========");
    }

    // ================================================================
    @Component
    static class UserService {

        @Autowired
        private JdbcTemplate jdbcTemplate;

        // ---------- 失效 1：非 public 方法 ----------
        /*
         * 【设计意图】
         * 为什么 Spring 不代理非 public 方法？
         *
         * JDK 动态代理：只能代理接口方法，接口方法隐式 public
         * CGLIB 代理：可以代理 protected/包级私有的方法
         *
         * 但 Spring 官方统一要求 public。理由：
         * 1. 事务是"公开"的行为契约，用 public 表达意图更清晰
         * 2. 避免混淆——如果 protected 方法也能有事务，继承时会很复杂
         * 3. 性能——减少需要检查的方法数量
         */
        @Transactional
        void nonPublicMethod() {  // 非 public！
            System.out.println("  这个方法上的 @Transactional 不会生效");
            jdbcTemplate.execute("INSERT INTO users VALUES (1, 'test')");
        }

        public void callNonPublic() {
            nonPublicMethod(); // 没有事务！
            /*
             * Spring 的 AbstractFallbackTransactionAttributeSource
             * 在解析 @Transactional 时会检查 Modifier.isPublic(method.getModifiers())。
             * 非 public 方法直接返回 null（无事务属性）。
             */
        }

        // ---------- 失效 2：自调用 ----------
        @Transactional
        public void outerMethod() {
            System.out.println("  outerMethod 有事务，但...");
            this.innerTransactional();  // this 调用绕过了代理！
            // 解决方案见 outerMethodFixed()
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void innerTransactional() {
            System.out.println("  innerTransactional 本应有独立事务，但自调用导致失效");
        }

        // 修复方案
        public void outerMethodFixed() {
            UserService proxy = (UserService) AopContext.currentProxy();
            proxy.innerTransactional();  // 通过代理调用，事务生效 ✅
        }

        // ---------- 失效 3：异常被 catch 吞掉 ----------
        @Transactional
        public void caughtException() {
            try {
                jdbcTemplate.execute("INSERT INTO users VALUES (1, 'test')");
                throw new RuntimeException("业务异常");
            } catch (RuntimeException e) {
                // ❌ 异常被吞掉了！Spring 感知不到，事务正常提交
                System.out.println("  ⚠️ 异常被 catch 了: " + e.getMessage());
                System.out.println("  ⚠️ 但事务不会回滚——因为 Spring 没看到异常！");
                /*
                 * 正确做法：catch 之后重新抛出，或手动回滚：
                 * TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                 */
            }
        }

        // ---------- 失效 4：默认不回滚 checked 异常 ----------
        @Transactional
        public void checkedExceptionNoRollback() {
            try {
                throw new IOException("文件不存在");  // checked exception
            } catch (IOException e) {
                System.out.println("  IOException 是 checked 异常");
                System.out.println("  默认情况下 Spring 只回滚 RuntimeException 和 Error");
                /*
                 * checked 异常代表"调用方可以处理的异常"，
                 * Spring 的设计哲学认为这类异常不应该导致事务回滚。
                 *
                 * 但这是一个有争议的设计！大多数实际场景中，
                 * 任何异常都应该回滚事务。所以推荐：
                 * @Transactional(rollbackFor = Exception.class)
                 */
                throw new RuntimeException(e); // 包装成 RuntimeException
            }
        }

        // ---------- 修复：显式指定 rollbackFor ----------
        @Transactional(rollbackFor = Exception.class)  // ✅ 包含所有异常
        public void checkedExceptionWithRollback() {
            System.out.println("  使用 @Transactional(rollbackFor = Exception.class)");
            System.out.println("  即使是 checked 异常也会回滚 ✅");
            throw new RuntimeException("模拟异常——事务回滚");
        }
    }

    // ================================================================
    @Configuration
    @ComponentScan(basePackageClasses = TransactionFailureDemo.class)
    @EnableTransactionManagement
    static class DemoConfig {

        @Bean
        public DataSource dataSource() {
            return new EmbeddedDatabaseBuilder()
                    .setType(EmbeddedDatabaseType.H2)
                    .setName("testdb_failure")
                    .build();
        }

        @Bean
        public JdbcTemplate jdbcTemplate(DataSource dataSource) {
            // 初始化表
            JdbcTemplate jt = new JdbcTemplate(dataSource);
            jt.execute("CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY, name VARCHAR(50))");
            return jt;
        }

        @Bean
        public PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }
}
