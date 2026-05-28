package com.example.springqa.Q08_InternalCallFailure;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * <h1>Q8：内部调用失效 — this 调用绕过代理</h1>
 *
 * <h2>根因</h2>
 * <p>Spring AOP 基于代理。外部调用 proxy.methodA() 时，代理拦截后
 * 反射调用 target.methodA()。但 methodA() 内部 this.methodB() 时，
 * this 指向原始对象（target），不是代理——methodB 上的切面不触发。</p>
 *
 * <h2>三种解决方案</h2>
 * <ol>
 *   <li>AopContext.currentProxy() — 从 ThreadLocal 获取当前代理</li>
 *   <li>注入自身 (@Autowired self) — Spring 注入的是代理对象</li>
 *   <li>重构到另一个类 — 治本之策</li>
 * </ol>
 */
@Component
public class InternalCallFailureDemo {

    private final Q08GreetingService service;

    public InternalCallFailureDemo(Q08GreetingService service) {
        this.service = service;
    }

    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Q08: 内部调用失效 ===\n\n");

        sb.append("场景一：外部直接调用 → 切面生效 ✅\n");
        sb.append("  ").append(service.greet("World")).append("\n\n");

        sb.append("场景二：内部调用 this.method() → 切面失效 ❌\n");
        sb.append("  ").append(service.wrapperMethod("World")).append("\n\n");

        sb.append("方案一：AopContext.currentProxy() → 切面生效 ✅\n");
        sb.append("  ").append(service.wrapperWithCurrentProxy("World")).append("\n\n");

        sb.append("方案二：@Autowired 注入自身 → 切面生效 ✅\n");
        sb.append("  ").append(service.wrapperWithSelfInjection("World")).append("\n\n");

        sb.append("【为什么不是 Spring 的 bug？】\n");
        sb.append("这是 Java 语言限制 + 代理模式的天生缺陷。\n");
        sb.append("AspectJ 编译期织入没有这个问题——因为它直接修改字节码，不存在代理层。\n");
        sb.append("Spring 选择运行时代理是为了\"无侵入性\"，付出的代价就是内部调用失效。\n");

        return sb.toString();
    }

    // ================================================================
    @Aspect
    @Component
    static class LoggingAspect {
        @Around("execution(* com.example.springqa.Q08_InternalCallFailure.Q08GreetingService.inner*(..))")
        public Object log(ProceedingJoinPoint pjp) throws Throwable {
            System.out.println("  [AOP] 切面拦截: " + pjp.getSignature().getName());
            return pjp.proceed();
        }
    }

    @Component("q08GreetingService")
    static class Q08GreetingService {

        @Autowired
        private Q08GreetingService self;

        public String greet(String name) {
            return "Hello, " + name;
        }

        /** ❌ 内部调用：this 是原始对象，切面不触发 */
        public String wrapperMethod(String name) {
            return this.innerGreet(name);
        }

        /** ✅ 方案一：通过 AopContext 获取代理 */
        public String wrapperWithCurrentProxy(String name) {
            Q08GreetingService proxy = (Q08GreetingService) AopContext.currentProxy();
            return proxy.innerGreet(name);
        }

        /** ✅ 方案二：注入自身（Spring 注入的是代理） */
        public String wrapperWithSelfInjection(String name) {
            return self.innerGreet(name);
        }

        /** 被内部调用的方法（希望被切面拦截） */
        public String innerGreet(String name) {
            return "Inner: Hello, " + name;
        }
    }
}
