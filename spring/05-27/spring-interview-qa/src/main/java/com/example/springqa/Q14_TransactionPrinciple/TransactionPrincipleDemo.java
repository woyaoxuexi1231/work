package com.example.springqa.Q14_TransactionPrinciple;

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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

/**
 * <h1>Q14：事务实现原理 — AOP 织入 + TransactionSynchronization</h1>
 *
 * <h2>面试点</h2>
 * <ul>
 *   <li>@Transactional 是如何通过 AOP 织入事务的？</li>
 *   <li>PlatformTransactionManager 的核心方法？</li>
 *   <li>TransactionSynchronizationManager 的作用？</li>
 *   <li>如何在一个事务提交后执行自定义逻辑？</li>
 * </ul>
 *
 * <h2>@Transactional 的 AOP 织入流程</h2>
 * <pre>
 * 1. @EnableTransactionManagement 导入 TransactionManagementConfigurationSelector
 * 2. 注册 InfrastructureAdvisorAutoProxyCreator（一个 BeanPostProcessor）
 * 3. 注册 BeanFactoryTransactionAttributeSourceAdvisor（一个 Advisor）：
 *    - Pointcut: TransactionAttributeSourcePointcut（匹配有 @Transactional 的方法）
 *    - Advice:   TransactionInterceptor（MethodInterceptor 实现）
 * 4. 当 Bean 初始化时，AutoProxyCreator 检测到匹配 → 创建 AOP 代理
 * 5. 调用代理方法时，TransactionInterceptor.invoke() 被触发：
 *    a. 获取 TransactionAttribute（传播行为、隔离级别、超时...）
 *    b. 获取 PlatformTransactionManager
 *    c. tm.getTransaction() → 开启/加入事务
 *    d. invocation.proceed() → 执行目标方法
 *    e. 成功 → tm.commit() / 失败 → tm.rollback()
 * </pre>
 *
 * <h2>PlatformTransactionManager 核心方法</h2>
 * <pre>
 * TransactionStatus getTransaction(TransactionDefinition)
 *     — 根据传播行为，开启新事务或加入已有事务
 * void commit(TransactionStatus)
 *     — 提交事务
 * void rollback(TransactionStatus)
 *     — 回滚事务
 * </pre>
 *
 * <h2>TransactionSynchronizationManager</h2>
 * <p>它使用 ThreadLocal 管理事务资源：</p>
 * <ul>
 *   <li>当前事务的连接（DataSource）</li>
 *   <li>当前事务名称</li>
 *   <li>只读标志</li>
 *   <li>隔离级别</li>
 *   <li>TransactionSynchronization 回调链</li>
 * </ul>
 *
 * <h2>Spring 为什么设计 TransactionSynchronization？</h2>
 * <p>这是一个经典的"回调模式"。很多操作需要在事务的特定时点执行——
 * 提交后发 MQ、回滚后清理缓存、提交前校验数据……</p>
 * <p>Spring 提供了一组回调接口而不是硬编码这些行为，
 * 使得框架使用者可以在事务的各个阶段插入自定义逻辑。</p>
 *
 * @author Spring Interview QA
 */
public class TransactionPrincipleDemo {

    public static void main(String[] args) {
        System.out.println("========== Q14: 事务原理 Demo ==========\n");

        AnnotationConfigApplicationContext ctx =
                new AnnotationConfigApplicationContext(DemoConfig.class);

        // ===== 方式一：声明式事务（@Transactional，最常用） =====
        System.out.println("--- 声明式事务 (@Transactional) ---");
        BusinessService service = ctx.getBean(BusinessService.class);
        service.createUser("Alice");

        // ===== 方式二：编程式事务（TransactionTemplate） =====
        System.out.println("\n--- 编程式事务 (TransactionTemplate) ---");
        TransactionTemplate txTemplate = ctx.getBean(TransactionTemplate.class);
        txTemplate.execute(status -> {
            /*
             * 编程式事务的优势：
             * - 可以精确控制事务边界（不像 @Transactional 以方法为单位）
             * - 可以在事务中动态决定是否回滚
             * - 没有 AOP 代理的性能开销
             *
             * 缺点：
             * - 代码侵入性强
             * - 容易忘记提交/回滚
             */
            JdbcTemplate jt = ctx.getBean(JdbcTemplate.class);
            jt.update("INSERT INTO users VALUES (2, '编程式事务')");
            System.out.println("  事务中插入了一条数据");
            return null;
        });

        // ===== TransactionSynchronization — afterCommit =====
        System.out.println("\n--- TransactionSynchronization.afterCommit() ---");
        service.createUserWithCallback("Bob");

        // ===== 查看事务状态 =====
        System.out.println("\n--- 当前事务状态 ---");
        System.out.println("  活跃事务: " + TransactionSynchronizationManager.isActualTransactionActive());
        System.out.println("  只读: " + TransactionSynchronizationManager.isCurrentTransactionReadOnly());
        /*
         * 此时在主线程中，事务已经提交，TransactionSynchronizationManager
         * 中的 ThreadLocal 已经被清理。
         *
         * 【为什么用 ThreadLocal？】
         * ThreadLocal 天然地将事务绑定到线程——每个线程有独立的事务上下文。
         * 这也是为什么 @Transactional 不能跨线程使用：
         * 新线程有自己的 ThreadLocal，看不到原线程的事务。
         *
         * 这是 Spring 事务管理的核心约束，也是它的精妙之处——
         * 用最简单的机制（ThreadLocal）解决了最复杂的问题（事务上下文传播）。
         */

        ctx.close();
        System.out.println("\n========== Demo 结束 ==========");
    }

    // ================================================================
    @Component
    static class BusinessService {

        private final JdbcTemplate jdbcTemplate;

        public BusinessService(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @Transactional
        public void createUser(String name) {
            jdbcTemplate.update("INSERT INTO users VALUES (?, ?)", name.hashCode(), name);
            System.out.println("  创建用户: " + name + "（事务自动提交）");
        }

        @Transactional
        public void createUserWithCallback(String name) {
            // 注册事务同步回调——在事务提交后执行
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            /*
                             * 【典型应用场景】
                             * 1. 发送 MQ 消息——只在事务真正提交后才发送，
                             *    避免事务回滚后消息已经发出
                             * 2. 清除缓存——事务提交后缓存失效
                             * 3. 发送通知——如"订单创建成功"的邮件
                             *
                             * 【为什么不直接在方法末尾发？】
                             * 方法末尾 ≠ 事务已提交！
                             * @Transactional 的方法结束后，Spring 还要执行
                             * commit 操作。如果 commit 失败（如数据库连接断开），
                             * 消息已经发出了——数据不一致。
                             *
                             * afterCommit 保证只在事务真正提交后才执行。
                             */
                            System.out.println("  📬 afterCommit: 用户 " + name
                                    + " 创建成功，发送通知...");
                        }

                        @Override
                        public void afterCompletion(int status) {
                            System.out.println("  📋 afterCompletion: 事务完成，status="
                                    + (status == STATUS_COMMITTED ? "COMMITTED" : "ROLLED_BACK"));
                        }
                    }
            );

            jdbcTemplate.update("INSERT INTO users VALUES (?, ?)", name.hashCode(), name);
            System.out.println("  创建用户: " + name + "（事务将在方法返回后提交）");

            /*
             * TransactionSynchronization 的生命周期：
             *
             * 1. beforeCommit(readOnly)    — 提交前（可做最后的校验）
             * 2. beforeCompletion()        — 完成前（提交或回滚前）
             * 3. afterCommit()             — 提交成功后
             * 4. afterCompletion(status)   — 无论成功/失败，最终回调
             *
             * Spring 事务管理器在 commit() 时会遍历所有注册的
             * TransactionSynchronization 实例，按上述顺序调用。
             */
        }
    }

    // ================================================================
    @Configuration
    @ComponentScan(basePackageClasses = TransactionPrincipleDemo.class)
    @EnableTransactionManagement
    static class DemoConfig {

        @Bean
        public DataSource dataSource() {
            return new EmbeddedDatabaseBuilder()
                    .setType(EmbeddedDatabaseType.H2)
                    .setName("testdb_principle")
                    .build();
        }

        @Bean
        public JdbcTemplate jdbcTemplate(DataSource dataSource) {
            JdbcTemplate jt = new JdbcTemplate(dataSource);
            jt.execute("CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY, name VARCHAR(50))");
            return jt;
        }

        @Bean
        public PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        public TransactionTemplate transactionTemplate(PlatformTransactionManager tm) {
            return new TransactionTemplate(tm);
        }
    }
}
