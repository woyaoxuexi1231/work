package com.example.springqa.Q09_InterceptorChain;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect @Component @Order(3)
public class Q09TransactionAspect {
    @Around("execution(* com.example.springqa.Q09_InterceptorChain.Q09BusinessService.*(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("  [Tx       @Order(3)] >>> 开启事务（最内层）");
        Object result = pjp.proceed();
        System.out.println("  [Tx       @Order(3)] <<< 提交事务");
        return result;
    }
}
