package com.example.springqa.Q12_TransactionPropagation;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;

/**
 * <h1>Q12：事务传播行为 — REQUIRED / REQUIRES_NEW / NESTED</h1>
 *
 * <h2>面试点</h2>
 * <ul>
 *   <li>REQUIRED、REQUIRES_NEW、NESTED 的区别和适用场景？</li>
 *   <li>REQUIRES_NEW 挂起当前事务是怎么实现的？</li>
 *   <li>NESTED 在什么数据库下才真正生效？</li>
 * </ul>
 *
 * <h2>三种传播行为对比</h2>
 * <pre>
 * | 传播行为       | 当前有事务 | 当前无事务 | 回滚影响           |
 * |---------------|-----------|-----------|-------------------|
 * | REQUIRED      | 加入当前   | 新建      | 一起回滚           |
 * | REQUIRES_NEW  | 挂起当前   | 新建      | 独立回滚（不影响外层）|
 * | NESTED        | 嵌套子事务 | 新建      | 子事务可独立回滚    |
 * </pre>
 *
 * <h2>REQUIRES_NEW 挂起当前事务的实现</h2>
 * <p>Spring 通过 TransactionSynchronizationManager 实现挂起/恢复：</p>
 * <pre>
 * // 挂起当前事务
 * Object suspendedResources = TransactionSynchronizationManager.unbindResource(dataSource);
 * TransactionSynchronizationManager.clearSynchronization();
 *
 * // ... 执行新事务 ...
 *
 * // 恢复原事务
 * TransactionSynchronizationManager.bindResource(dataSource, suspendedResources);
 * </pre>
 *
 * <h2>NESTED 的数据库要求</h2>
 * <p>NESTED 依赖 JDBC 3.0 的 <b>Savepoint</b> 功能。
 * 只有支持 Savepoint 的数据库（MySQL InnoDB、PostgreSQL、Oracle）才能用。
 * 不支持 Savepoint 的数据库（如某些 NoSQL），NESTED 会降级为 REQUIRED。</p>
 *
 * <h2>Spring 为什么这样设计？</h2>
 * <p>事务传播是 Spring 对"声明式事务管理"的核心贡献。
 * 传统 JDBC 编程中，事务管理是嵌套的 try-catch-finally，
 * 传播行为把这个复杂性封装成了配置级别——配置传播行为，
 * 比手动编写事务嵌套代码可靠得多。</p>
 *
 * @author Spring Interview QA
 */
public class TransactionPropagationDemo {

    /**
     * 运行本 Demo：
     * 需要 spring-boot-starter-jdbc + H2 数据库依赖。
     * 观察控制台输出的"当前事务名称"和"是否有活跃事务"。
     */
    public static void main(String[] args) {
        System.out.println("========== Q12: 事务传播 Demo ==========\n");

        AnnotationConfigApplicationContext ctx =
                new AnnotationConfigApplicationContext(DemoConfig.class);

        OrderService service = ctx.getBean(OrderService.class);

        // ===== 场景一：REQUIRED（默认） =====
        System.out.println("--- REQUIRED: 外层有事务，内层加入 ---");
        service.requiredOuter();

        // ===== 场景二：REQUIRES_NEW =====
        System.out.println("\n--- REQUIRES_NEW: 内层独立事务 ---");
        service.requiresNewOuter();

        // ===== 场景三：NESTED =====
        System.out.println("\n--- NESTED: 嵌套子事务 ---");
        service.nestedOuter();

        ctx.close();
        System.out.println("\n========== Demo 结束 ==========");
    }

    // ================================================================
    @Component
    static class OrderService {

        // ==================== REQUIRED ====================
        @Transactional(propagation = Propagation.REQUIRED)
        public void requiredOuter() {
            printTxStatus("requiredOuter 开始");
            /*
             * REQUIRED 是默认传播行为：
             * - 如果当前有事务 → 加入当前事务
             * - 如果当前没有事务 → 新建一个
             *
             * 【设计意图】
             * 为什么 REQUIRED 是默认值？
             * 因为大多数业务场景下，一组操作应该共享同一个事务——
             * 下单、减库存、记账要么一起成功要么一起失败。
             * 这是"ACID"中原子性的体现。
             */
            requiredInner();  // 内层会加入外层的同一个事务
            printTxStatus("requiredOuter 结束");
        }

        @Transactional(propagation = Propagation.REQUIRED)
        public void requiredInner() {
            printTxStatus("requiredInner 执行（应该和外层用同一个事务）");
        }

        // ==================== REQUIRES_NEW ====================
        @Transactional(propagation = Propagation.REQUIRED)
        public void requiresNewOuter() {
            printTxStatus("requiresNewOuter 开始（事务A）");
            try {
                requiresNewInner();  // 会挂起事务A，开启事务B
            } catch (Exception e) {
                /*
                 * REQUIRES_NEW 的关键特性：
                 * 即使内层事务回滚，外层事务可以继续提交。
                 * 但如果异常传播到外层（内层抛出未捕获的异常），
                 * 外层也会回滚。
                 *
                 * 【适用场景】
                 * 日志记录：业务失败时，日志必须写入（不能回滚）。
                 * auditLog() 用 REQUIRES_NEW，保证日志独立提交。
                 */
            }
            printTxStatus("requiresNewOuter 结束（事务A仍在）");
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void requiresNewInner() {
            printTxStatus("requiresNewInner 执行（独立事务B——事务A被挂起）");
            /*
             * TransactionSynchronizationManager 内部：
             * 1. 获取当前事务状态（连接、隔离级别、只读标志...）
             * 2. 从 ThreadLocal 中解绑当前事务资源
             * 3. 将状态保存到 SuspendedResourcesHolder
             * 4. 开启新事务（新连接/新 savepoint）
             * 5. 内层结束后恢复 SuspendedResourcesHolder
             *
             * Spring 用 ThreadLocal 来绑定事务到线程——
             * 这也是为什么 @Transactional 在多线程环境下失效：
             * 新线程有新的 ThreadLocal，拿不到原线程的事务。
             */
        }

        // ==================== NESTED ====================
        @Transactional(propagation = Propagation.REQUIRED)
        public void nestedOuter() {
            printTxStatus("nestedOuter 开始（主事务）");
            try {
                nestedInnerCanRollback();  // 子事务独立回滚
            } catch (Exception ignored) {
                System.out.println("  ⚠️ 子事务回滚不影响主事务");
            }
            printTxStatus("nestedOuter 结束（主事务仍可提交）");
            /*
             * NESTED vs REQUIRES_NEW 的区别：
             * - NESTED：子事务是主事务的一部分，子事务回滚不影响主事务，
             *   但主事务回滚会导致子事务也回滚。
             * - REQUIRES_NEW：两个完全独立的事务，互不影响。
             *
             * NESTED 实现：conn.setSavepoint() → conn.rollback(savepoint)
             * 这要求数据库支持 Savepoint。
             */
        }

        @Transactional(propagation = Propagation.NESTED)
        public void nestedInnerCanRollback() {
            printTxStatus("nestedInnerCanRollback 执行（子事务）");
            throw new RuntimeException("模拟子事务异常回滚到 savepoint");
        }
    }

    // ================================================================
    // 工具方法
    // ================================================================

    static void printTxStatus(String context) {
        String txName = TransactionSynchronizationManager.getCurrentTransactionName();
        boolean active = TransactionSynchronizationManager.isActualTransactionActive();
        System.out.println("  [" + context + "] 事务名=" + txName
                + ", 活跃=" + active
                + ", 只读=" + TransactionSynchronizationManager.isCurrentTransactionReadOnly());
    }

    // ================================================================
    @Configuration
    @ComponentScan(basePackageClasses = TransactionPropagationDemo.class)
    @EnableTransactionManagement  // 开启声明式事务（等价于 <tx:annotation-driven/>）
    static class DemoConfig {

        @Bean
        public DataSource dataSource() {
            return new EmbeddedDatabaseBuilder()
                    .setType(EmbeddedDatabaseType.H2)
                    .setName("testdb_propagation")
                    .build();
        }

        @Bean
        public PlatformTransactionManager transactionManager(DataSource dataSource) {
            /*
             * PlatformTransactionManager 是一个 SPI（策略接口）。
             * Spring 这样设计是为了屏蔽不同的事务管理方式：
             * - DataSourceTransactionManager   → JDBC
             * - JpaTransactionManager          → JPA
             * - HibernateTransactionManager    → Hibernate
             * - JtaTransactionManager          → JTA（分布式事务）
             *
             * 上层代码只依赖 PlatformTransactionManager 接口，
             * 切换持久层技术时不需要改业务代码。
             */
            return new DataSourceTransactionManager(dataSource);
        }
    }
}
