package com.example.springqa.Q08_InternalCallFailure;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class Q08LoggingAspect {

    @Around("execution(* com.example.springqa.Q08_InternalCallFailure.Q08GreetingService.inner*(..))")
    public Object log(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("  [AOP] 切面拦截: " + pjp.getSignature().getName());
        return pjp.proceed();
    }
}
