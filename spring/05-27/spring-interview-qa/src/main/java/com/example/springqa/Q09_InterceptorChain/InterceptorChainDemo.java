package com.example.springqa.Q09_InterceptorChain;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * <h1>Q9：拦截链 — 多个切面的执行顺序</h1>
 *
 * <h2>面试点</h2>
 * <ul>
 *   <li>多个切面作用于同一方法时，执行顺序如何控制？</li>
 *   <li>@Order 与 @Priority 的区别？</li>
 *   <li>环绕增强的 proceed() 如果不调用会怎样？</li>
 * </ul>
 *
 * <h2>执行顺序</h2>
 *
 * <p>多个切面形成一条"责任链"：</p>
 * <pre>
 * 外部调用
 *   → 切面1 @Before (order 小)
 *     → 切面2 @Before (order 大)
 *       → 切面3 @Before (order 更大)
 *         → 目标方法执行
 *       → 切面3 @After / @AfterReturning
 *     → 切面2 @After / @AfterReturning
 *   → 切面1 @After / @AfterReturning
 *
 * // 记忆口诀：进入时 order 小先执行（类似栈——先进后出）
 * // @Around 的 proceed() 是分水岭：之前是进，之后是出
 * </pre>
 *
 * <h2>@Order vs @Priority</h2>
 * <pre>
 * | 特性      | @Order                          | @Priority (JSR-250)           |
 * |----------|---------------------------------|-------------------------------|
 * | 标准     | Spring 原生                     | JDK 标准                      |
 * | 范围     | 切面 / Bean / Filter / Interceptor | 更通用                        |
 * | 优先级值 | 越小越高                        | 越小越高                      |
 * | Spring   | 切面排序默认用 @Order           | 需要额外配置                   |
 * </pre>
 *
 * <h2>proceed() 不调用的后果</h2>
 * <p>proceed() 是分水岭——调用它才会继续执行链中的下一个切面。
 * 如果不调用：后续所有切面 + 目标方法都不会执行。
 * <b>这是实现"权限拦截"的基础</b>——如果权限不通过，直接返回，不调用 proceed()。</p>
 *
 * <h2>Spring 为什么这样设计？</h2>
 * <p>责任链模式是拦截器/过滤器的经典实现。Spring 选择用 @Order 控制排序，
 * 是因为这个注解已经被广泛使用（@Order 在 Spring 2.0 就有了），
 * 复用现有机制比引入新概念更好。</p>
 *
 * @author Spring Interview QA
 */
public class InterceptorChainDemo {

    public static void main(String[] args) {
        System.out.println("========== Q9: 拦截链 Demo ==========\n");

        AnnotationConfigApplicationContext ctx =
                new AnnotationConfigApplicationContext(DemoConfig.class);

        BusinessService service = ctx.getBean(BusinessService.class);
        System.out.println(">>> 调用 doBusiness():\n");
        service.doBusiness();

        System.out.println("\n>>> 调用 doBusinessWithBlock():\n");
        service.doBusinessWithBlock();

        ctx.close();
        System.out.println("\n========== Demo 结束 ==========");
    }

    // ================================================================
    // 三个切面，模拟：日志 → 权限 → 性能监控
    // ================================================================

    /**
     * 日志切面：order=1（最外层，最先进入，最后退出）
     *
     * 【设计意图】
     * 为什么日志切面放在最外层？
     * 因为它要记录"整个请求"的开始和结束时间。
     * 如果放在内层，那就记录不到外层切面的耗时。
     */
    @Aspect
    @Component
    @Order(1)
    static class LoggingAspect {

        @Around("execution(* com.example.springqa.Q09_InterceptorChain.BusinessService.*(..))")
        public Object around(ProceedingJoinPoint pjp) throws Throwable {
            System.out.println("  [Logging  @Order(1)] >>> 进入（外层）");
            Object result = pjp.proceed();  // ← 调用下一个切面
            System.out.println("  [Logging  @Order(1)] <<< 退出（外层）");
            return result;
        }
    }

    /**
     * 权限切面：order=2（中间层）
     *
     * 注意 doBusinessWithBlock 方法——如果权限不通过，
     * proceed() 不被调用，后续所有切面和目标方法都不会执行。
     */
    @Aspect
    @Component
    @Order(2)
    static class SecurityAspect {

        @Around("execution(* com.example.springqa.Q09_InterceptorChain.BusinessService.*(..))")
        public Object around(ProceedingJoinPoint pjp) throws Throwable {
            String methodName = pjp.getSignature().getName();
            System.out.println("  [Security @Order(2)] >>> 权限检查...");

            if (methodName.contains("Block")) {
                // 模拟权限不通过——不调用 proceed()
                System.out.println("  [Security @Order(2)] 🚫 权限不通过，阻断请求！");
                /*
                 * 【核心知识点】
                 * 不调用 proceed() 意味着：
                 * 1. 当前切面的 after 部分不会执行
                 * 2. 后续切面（@Order(3)）完全不会执行
                 * 3. 目标方法不会执行
                 *
                 * 这就是 Spring Security 的 @PreAuthorize 实现基础！
                 * 如果表达式求值为 false，切面直接抛 AccessDeniedException，
                 * 不调用 proceed()。
                 */
                return "BLOCKED";
            }

            Object result = pjp.proceed();
            System.out.println("  [Security @Order(2)] <<< 权限通过，清理安全上下文");
            return result;
        }
    }

    /**
     * 事务切面：order=3（最内层，最后进入，最先退出）
     *
     * 【设计意图】
     * 为什么事务切面放在最内层？
     * 因为事务应该在最靠近业务代码的地方开启和提交——
     * 如果外层切面（如日志）执行了很长时间，事务持有数据库连接会浪费资源。
     *
     * Spring 的 @Transactional 默认 order = Ordered.LOWEST_PRECEDENCE
     * （即 Integer.MAX_VALUE），这样用户自定义切面默认在外层，
     * 事务在最内层。这是合理的默认值。
     */
    @Aspect
    @Component
    @Order(3)
    static class TransactionAspect {

        @Around("execution(* com.example.springqa.Q09_InterceptorChain.BusinessService.*(..))")
        public Object around(ProceedingJoinPoint pjp) throws Throwable {
            System.out.println("  [Tx       @Order(3)] >>> 开启事务（最内层）");
            Object result = pjp.proceed();
            System.out.println("  [Tx       @Order(3)] <<< 提交事务（最内层）");
            return result;
        }
    }

    // ================================================================
    @Component
    static class BusinessService {

        public String doBusiness() {
            System.out.println("    🎯 目标方法 doBusiness() 执行中...");
            return "OK";
        }

        public String doBusinessWithBlock() {
            System.out.println("    🎯 目标方法 doBusinessWithBlock() 执行中...");
            return "OK";
        }
    }

    // ================================================================
    @Configuration
    @ComponentScan(basePackageClasses = InterceptorChainDemo.class)
    @EnableAspectJAutoProxy
    static class DemoConfig {
    }
}
