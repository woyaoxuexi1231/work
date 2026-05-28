package com.example.springqa.Q09_InterceptorChain;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect @Component @Order(2)
public class Q09SecurityAspect {
    @Around("execution(* com.example.springqa.Q09_InterceptorChain.Q09BusinessService.*(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("  [Security @Order(2)] >>> 权限检查...");
        if (pjp.getSignature().getName().contains("Block")) {
            System.out.println("  [Security @Order(2)] 🚫 阻断！proceed() 不被调用");
            return "BLOCKED";
        }
        Object result = pjp.proceed();
        System.out.println("  [Security @Order(2)] <<< 通过");
        return result;
    }
}
