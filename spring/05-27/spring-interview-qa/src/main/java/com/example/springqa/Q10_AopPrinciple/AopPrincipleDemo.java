package com.example.springqa.Q10_AopPrinciple;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.Advisor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.stereotype.Component;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * <h1>Q10：AOP 实现原理 — Advisor / Advice / Pointcut</h1>
 *
 * <h2>核心关系</h2>
 * <pre>
 * Advisor = Advice + Pointcut
 *   Advice:   做什么？（增强逻辑）
 *   Pointcut: 在哪里做？（匹配哪些方法）
 * </pre>
 *
 * <h2>ReflectiveMethodInvocation 递归链</h2>
 * <p>Spring AOP 最精妙的设计：用递归而非 for 循环实现拦截链。
 * 每个拦截器内部调用 mi.proceed() → 形成递归调用栈 → 天然支持"进入做 A，退出做 B"。</p>
 */
@Component
public class AopPrincipleDemo {

    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Q10: AOP 原理 ===\n\n");

        // 1. Pointcut 匹配演示
        sb.append("--- Pointcut 匹配 ---\n");
        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        pointcut.setExpression("execution(* com.example.springqa.Q10_AopPrinciple.BusinessService.*(..))");
        for (Method method : BusinessService.class.getDeclaredMethods()) {
            sb.append("  ").append(method.getName()).append("() → ")
                    .append(pointcut.matches(method, BusinessService.class) ? "✅" : "❌").append("\n");
        }

        // 2. Advisor + Advice 手动织入
        sb.append("\n--- 手动织入代理 ---\n");
        ProxyFactory factory = new ProxyFactory();
        factory.setTarget(new BusinessService());
        factory.addAdvisor(new DefaultPointcutAdvisor(pointcut,
                (MethodInterceptor) invocation -> {
                    sb.append("  [Advice] >>> 前置\n");
                    Object result = invocation.proceed();
                    sb.append("  [Advice] <<< 后置\n");
                    return result;
                }));
        BusinessService proxy = (BusinessService) factory.getProxy();
        sb.append("代理类型: ").append(proxy.getClass().getName()).append("\n");
        proxy.doBusiness(); // 被增强
        proxy.anotherMethod(); // 不匹配，不增强

        // 3. 递归拦截链演示
        sb.append("\n--- 递归拦截链 (简化 ReflectiveMethodInvocation) ---\n");
        List<MethodInterceptor> interceptors = new ArrayList<>();
        interceptors.add(mi -> {
            sb.append("  [I1] 进入\n");
            Object r = mi.proceed();
            sb.append("  [I1] 退出\n");
            return r;
        });
        interceptors.add(mi -> {
            sb.append("  [I2] 进入 → 🎯 目标方法\n");
            sb.append("  [I2] 退出\n");
            return "result";
        });
        try {
            new SimpleInvocation(interceptors).proceed();
        } catch (Throwable ignored) {}

        sb.append("\n【为什么用递归而非 for 循环？】\n");
        sb.append("递归天然支持\"进入做 A，退出做 B\"的环绕语义——\n");
        sb.append("每个拦截器的 invoke() 在 proceed() 前后放置逻辑，\n");
        sb.append("形成洋葱模型。for 循环无法自然地支持这种\"进出\"结构。\n");

        return sb.toString();
    }

    // ================================================================
    static class BusinessService {
        public String doBusiness() {
            System.out.println("    🎯 BusinessService.doBusiness()");
            return "OK";
        }
        public String anotherMethod() {
            System.out.println("    BusinessService.anotherMethod()（不匹配切点）");
            return "OK";
        }
    }

    static class SimpleInvocation implements MethodInvocation {
        private final List<MethodInterceptor> interceptors;
        private int idx = -1;

        SimpleInvocation(List<MethodInterceptor> interceptors) { this.interceptors = interceptors; }

        @Override public Object proceed() throws Throwable {
            if (++idx >= interceptors.size()) return "target-result";
            return interceptors.get(idx).invoke(this);
        }

        @Override public Method getMethod() { return null; }
        @Override public Object[] getArguments() { return new Object[0]; }
        @Override public Object getThis() { return this; }
        @Override public AccessibleObject getStaticPart() { return null; }
    }
}
