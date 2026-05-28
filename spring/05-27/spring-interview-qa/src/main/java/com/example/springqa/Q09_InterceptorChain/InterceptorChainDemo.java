package com.example.springqa.Q09_InterceptorChain;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * <h1>Q9：拦截链 — 多个切面的执行顺序</h1>
 *
 * <h2>执行顺序（洋葱模型）</h2>
 * <pre>
 * 进入: order 小的先进入（先进后出）
 *   @Order(1) 进入 → @Order(2) 进入 → @Order(3) 进入
 *     → 目标方法
 *   @Order(3) 退出 → @Order(2) 退出 → @Order(1) 退出
 * </pre>
 *
 * <h2>@Order vs @Priority</h2>
 * <p>@Order 是 Spring 原生，@Priority 是 JSR-250 标准。切面排序默认用 @Order。</p>
 *
 * <h2>proceed() 不调用的后果</h2>
 * <p>后续所有切面 + 目标方法都不会执行——这是"权限拦截"的实现基础。</p>
 */
@Component
public class InterceptorChainDemo {

    private final Q09BusinessService service;

    public InterceptorChainDemo(Q09BusinessService service) {
        this.service = service;
    }

    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Q09: 拦截链 ===\n\n");

        sb.append("调用 doBusiness()：\n");
        sb.append("  ").append(service.doBusiness()).append("\n\n");

        sb.append("调用 doBusinessWithBlock()（权限不通过）：\n");
        sb.append("  ").append(service.doBusinessWithBlock()).append("\n\n");

        sb.append("【执行顺序验证】\n");
        sb.append("查看控制台日志，输出顺序应为：\n");
        sb.append("  [Logging  @Order(1)] >>> 进入（外层）\n");
        sb.append("  [Security @Order(2)] >>> 权限检查...\n");
        sb.append("  [Tx       @Order(3)] >>> 开启事务（最内层）\n");
        sb.append("    → 目标方法执行\n");
        sb.append("  [Tx       @Order(3)] <<< 提交事务\n");
        sb.append("  [Security @Order(2)] <<< 权限通过\n");
        sb.append("  [Logging  @Order(1)] <<< 退出\n\n");

        sb.append("【为什么事务在最内层？】\n");
        sb.append("事务应最靠近业务代码——外层切面执行时间长会浪费数据库连接。\n");
        sb.append("Spring @Transactional 默认 order = LOWEST_PRECEDENCE（最低优先级），\n");
        sb.append("保证用户自定义切面在外层、事务在最内层。\n");

        return sb.toString();
    }

    // ================================================================
    @Aspect @Component @Order(1)
    static class LoggingAspect {
        @Around("execution(* com.example.springqa.Q09_InterceptorChain.Q09BusinessService.*(..))")
        public Object around(ProceedingJoinPoint pjp) throws Throwable {
            System.out.println("  [Logging  @Order(1)] >>> 进入");
            Object result = pjp.proceed();
            System.out.println("  [Logging  @Order(1)] <<< 退出");
            return result;
        }
    }

    @Aspect @Component @Order(2)
    static class SecurityAspect {
        @Around("execution(* com.example.springqa.Q09_InterceptorChain.Q09BusinessService.*(..))")
        public Object around(ProceedingJoinPoint pjp) throws Throwable {
            System.out.println("  [Security @Order(2)] >>> 权限检查...");
            if (pjp.getSignature().getName().contains("Block")) {
                System.out.println("  [Security @Order(2)] 🚫 权限不通过，阻断！");
                return "BLOCKED"; // 不调用 proceed() → 后续全部跳过
            }
            Object result = pjp.proceed();
            System.out.println("  [Security @Order(2)] <<< 权限通过");
            return result;
        }
    }

    @Aspect @Component @Order(3)
    static class TransactionAspect {
        @Around("execution(* com.example.springqa.Q09_InterceptorChain.Q09BusinessService.*(..))")
        public Object around(ProceedingJoinPoint pjp) throws Throwable {
            System.out.println("  [Tx       @Order(3)] >>> 开启事务");
            Object result = pjp.proceed();
            System.out.println("  [Tx       @Order(3)] <<< 提交事务");
            return result;
        }
    }

    @Component
    static class Q09BusinessService {
        public String doBusiness() {
            System.out.println("    🎯 目标方法 doBusiness()");
            return "OK";
        }

        public String doBusinessWithBlock() {
            System.out.println("    🎯 目标方法 doBusinessWithBlock()");
            return "OK";
        }
    }
}
