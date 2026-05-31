package com.example.springqa.Q09_InterceptorChain;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect @Component @Order(1)
public class Q09LoggingAspect {
    @Around("execution(* com.example.springqa.Q09_InterceptorChain.Q09BusinessService.*(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("  [Logging  @Order(1)] >>> 进入");
        Object result = pjp.proceed();
        System.out.println("  [Logging  @Order(1)] <<< 退出");
        return result;
    }
}
