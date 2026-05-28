package com.example.springqa.Q10_AopPrinciple;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.stereotype.Component;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@Component
public class AopPrincipleDemo {

    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Q10: AOP 原理 ===\n\n");

        // 1. Pointcut 匹配
        AspectJExpressionPointcut pc = new AspectJExpressionPointcut();
        pc.setExpression("execution(* com.example.springqa.Q10_AopPrinciple.Q10BusinessService.*(..))");
        for (Method m : Q10BusinessService.class.getDeclaredMethods()) {
            sb.append("  ").append(m.getName()).append(" → ").append(pc.matches(m, Q10BusinessService.class) ? "✅" : "❌").append("\n");
        }

        // 2. 手动织入
        sb.append("\n--- 手动织入代理 ---\n");
        ProxyFactory factory = new ProxyFactory();
        factory.setTarget(new Q10BusinessService());
        factory.addAdvisor(new DefaultPointcutAdvisor(pc,
                (MethodInterceptor) invocation -> {
                    sb.append("  [Advice] >>> 前置\n");
                    Object r = invocation.proceed();
                    sb.append("  [Advice] <<< 后置\n");
                    return r;
                }));
        Q10BusinessService proxy = (Q10BusinessService) factory.getProxy();
        sb.append("代理: ").append(proxy.getClass().getName()).append("\n");

        // 3. 递归拦截链
        sb.append("\n--- 递归拦截链 ---\n");
        List<MethodInterceptor> chain = new ArrayList<>();
        chain.add(mi -> { sb.append("  [I1] 进入\n"); Object r = mi.proceed(); sb.append("  [I1] 退出\n"); return r; });
        chain.add(mi -> { sb.append("  [I2] 进入 → 🎯\n"); sb.append("  [I2] 退出\n"); return "r"; });
        try { new Q10SimpleInvocation(chain).proceed(); } catch (Throwable ignored) {}

        sb.append("\n【为什么用递归而非 for？】\n");
        sb.append("递归天然支持\"进入A退出B\"的环绕语义——洋葱模型。\n");
        return sb.toString();
    }
}
