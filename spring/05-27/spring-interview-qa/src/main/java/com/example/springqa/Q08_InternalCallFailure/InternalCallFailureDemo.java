package com.example.springqa.Q08_InternalCallFailure;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.stereotype.Component;

/**
 * <h1>Q8：内部调用失效 — this 调用绕过代理</h1>
 *
 * <h2>面试点</h2>
 * <ul>
 *   <li>同一个类中，方法 A 调用方法 B，B 上有 @Transactional/@Cacheable，为什么失效？</li>
 *   <li>三种解决方案：AopContext.currentProxy()、注入自身、重构类结构</li>
 * </ul>
 *
 * <h2>根因分析</h2>
 *
 * <p>Spring AOP 是基于<b>代理</b>的。当外部调用 proxy.methodA() 时，
 * 代理拦截调用，执行切面逻辑，然后反射调用 target.methodA()。
 * 但是在 methodA() 内部调用 this.methodB() 时，this 指向的是
 * <b>原始对象（target）</b>，不是代理对象（proxy）。
 * 因此 methodB 上的切面完全不会触发。</p>
 *
 * <pre>
 * 外部 → proxy.greet()  ← 代理拦截 ✅
 *          ↓
 *       target.greet()
 *          ↓
 *       this.innerGreet()  ← 直接调用目标对象 ❌
 *
 * // 为什么？因为 Java 的方法调用是在编译期绑定到"当前对象"的。
 * // this 永远指向当前实例（target），不管外面有多少层代理。
 * </pre>
 *
 * <h2>Spring 为什么这样设计？</h2>
 * <p>这不是 Spring 的 bug，而是 <b>Java 语言的限制 + 代理模式的天生缺陷</b>。
 * AspectJ 编译期织入没有这个问题——因为它直接修改字节码，不存在代理层。
 * Spring 选择运行时代理而非编译期织入，是为了"无侵入性"付出的代价。</p>
 *
 * <h3>三种解决方案对比</h3>
 * <pre>
 * | 方案                      | 侵入性 | 复杂度 | 推荐度 |
 * |---------------------------|--------|--------|--------|
 * | AopContext.currentProxy() | 低     | 低     | ⭐⭐⭐   |
 * | 注入自身 (@Autowired self) | 低     | 低     | ⭐⭐    |
 * | 重构到另一个类             | 中     | 中     | ⭐⭐⭐⭐ |
 * | 改用 AspectJ LTW          | 高     | 高     | ⭐     |
 * </pre>
 *
 * @author Spring Interview QA
 */
public class InternalCallFailureDemo {

    public static void main(String[] args) {
        System.out.println("========== Q8: 内部调用失效 Demo ==========\n");

        /*
         * 注意：@EnableAspectJAutoProxy(exposeProxy = true) 是必须的，
         * 否则 AopContext.currentProxy() 会抛 IllegalStateException。
         *
         * Spring 默认不暴露代理，是因为这是一个有代价的操作——
         * 每个代理对象都需要额外维护一个 ThreadLocal 引用。
         */
        AnnotationConfigApplicationContext ctx =
                new AnnotationConfigApplicationContext(DemoConfig.class);

        GreetingService service = ctx.getBean(GreetingService.class);

        // ===== 场景一：外部调用 → 切面生效 =====
        System.out.println("--- 场景一：外部直接调用 ---");
        service.greet("World");
        // 切面日志会打印

        // ===== 场景二：内部调用 → 切面失效 =====
        System.out.println("\n--- 场景二：内部调用 (this.innerGreet) ---");
        service.wrapperMethod("World");
        // 切面不会打印！因为内部调用绕过了代理

        // ===== 方案一：AopContext.currentProxy() =====
        System.out.println("\n--- 方案一：AopContext.currentProxy() ---");
        service.wrapperWithCurrentProxy("World");
        // 切面日志会打印 ✅

        // ===== 方案二：注入自身 =====
        System.out.println("\n--- 方案二：注入自身 (@Autowired self) ---");
        service.wrapperWithSelfInjection("World");
        // 切面日志会打印 ✅

        ctx.close();
        System.out.println("\n========== Demo 结束 ==========");
    }

    // ================================================================
    // 切面
    // ================================================================
    @Aspect
    @Component
    static class LoggingAspect {

        @Around("execution(* com.example.springqa.Q08_InternalCallFailure.*.*(..))")
        public Object log(ProceedingJoinPoint pjp) throws Throwable {
            System.out.println("  🔵 切面拦截: " + pjp.getSignature().getName());
            Object result = pjp.proceed();
            System.out.println("  🟢 切面完成: " + pjp.getSignature().getName());
            return result;
        }
    }

    // ================================================================
    // 业务 Service
    // ================================================================
    @Component
    static class GreetingService {

        // 方案二：注入自身
        @Autowired
        private GreetingService self;

        public String greet(String name) {
            System.out.println("    → greet(\"" + name + "\") 执行");
            return "Hello, " + name;
        }

        /** ❌ 内部调用：切面不会拦截 innerGreet */
        public String wrapperMethod(String name) {
            System.out.println("    → wrapperMethod 调用 this.innerGreet()");
            // this 指向原始对象，不是代理！
            return this.innerGreet(name);
        }

        // ---------- 方案一：AopContext.currentProxy() ----------
        public String wrapperWithCurrentProxy(String name) {
            System.out.println("    → wrapperWithCurrentProxy 调用 AopContext.currentProxy()");
            /*
             * AopContext.currentProxy() 从 ThreadLocal 获取当前线程的代理对象。
             *
             * 【Spring 设计意图】
             * 为什么用 ThreadLocal？因为代理对象调用链可能跨多个方法，
             * 用一个 ThreadLocal 保持当前代理引用是最简单的实现。
             *
             * 缺点：
             * - 与 Spring API 耦合
             * - 需要 exposeProxy = true
             * - 单元测试困难（需要 mock AopContext）
             */
            GreetingService proxy = (GreetingService) AopContext.currentProxy();
            return proxy.innerGreet(name);
        }

        // ---------- 方案二：注入自身 ----------
        public String wrapperWithSelfInjection(String name) {
            System.out.println("    → wrapperWithSelfInjection 调用 self.innerGreet()");
            /*
             * 通过 @Autowired 注入自身——Spring 会注入代理对象，不是原始对象。
             *
             * 【设计意图】
             * 为什么这样能工作？
             * 因为 @Autowired 是从容器中获取 Bean，容器返回的是代理对象。
             * self 指向的就是代理对象，调用 self.innerGreet() 会走代理。
             *
             * 注意：构造器注入时不能用这个方式——构造器执行时 Bean 还没创建完，
             * 注入自身会导致循环依赖。所以只能用 @Autowired 字段注入或 setter 注入。
             * 这是构造器注入"全好"的一个反例。
             */
            return self.innerGreet(name);
        }

        // ---------- 被内部调用的方法（希望被切面拦截） ----------
        public String innerGreet(String name) {
            System.out.println("    → innerGreet(\"" + name + "\") 执行");
            return "Inner: Hello, " + name;
        }
    }

    // ================================================================
    @Configuration
    @ComponentScan(basePackageClasses = InternalCallFailureDemo.class)
    @EnableAspectJAutoProxy(exposeProxy = true)  // 必须开启才能用 AopContext
    // ↑ Spring 默认 exposeProxy = false，是为了性能——
    //   每个代理调用都需要设置 ThreadLocal，有不小的开销。
    static class DemoConfig {
    }
}
