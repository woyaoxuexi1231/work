package com.example.springqa.Q07_ProxySelection;

import org.springframework.aop.framework.AopContext;
import org.springframework.aop.framework.AopProxy;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.stereotype.Component;

/**
 * <h1>Q7：代理选择 — JDK 动态代理 vs CGLIB</h1>
 *
 * <h2>面试点</h2>
 * <ul>
 *   <li>JDK 动态代理和 CGLIB 的原理、优缺点、适用场景？</li>
 *   <li>Spring Boot 2.x 为什么默认改为 CGLIB？</li>
 *   <li>proxyTargetClass=true 会强制用 CGLIB 吗，接口存在时呢？</li>
 * </ul>
 *
 * <h2>对比</h2>
 * <pre>
 * | 特性         | JDK 动态代理              | CGLIB                     |
 * |-------------|--------------------------|---------------------------|
 * | 原理        | 反射 + Proxy.newProxyInstance | ASM 字节码操作，生成子类    |
 * | 要求        | 必须有接口                 | 类不能是 final             |
 * | 方法拦截    | 通过接口方法              | 通过方法重写               |
 * | 性能        | 反射调用，早期较慢        | 直接调用，早期较快          |
 * | JDK 版本    | JDK 17+ 反射性能已大幅优化 | 需额外依赖                 |
 * | 内部调用    | 同样失效（接口方法调用代理） | 同样失效（this 调用不走代理）|
 * </pre>
 *
 * <h2>Spring Boot 2.x 为什么默认改为 CGLIB？</h2>
 *
 * <p>Spring Boot 1.x 默认采用 JDK 动态代理（proxyTargetClass=false）。
 * 这意味着：只有实现了接口的 Bean 才会被代理，没有接口的 Bean 不会被代理。</p>
 *
 * <p>问题：开发者在 Service 上加 @Transactional 或 @Cacheable，
 * 但这个 Service 没有实现接口——注解<b>静默失效</b>，没有任何报错。
 * 这是一个非常隐蔽的 bug。</p>
 *
 * <p>Spring Boot 2.x 改为默认 CGLIB（proxyTargetClass=true）：
 * 所有类都会被代理，不管有没有接口。这遵循了"最小惊讶原则"——
 * 开发者加了注解，就应该生效。</p>
 *
 * <h2>proxyTargetClass=true 时接口存在呢？</h2>
 * <p>如果 Bean 实现了接口，且 proxyTargetClass=true：
 * 仍然用 CGLIB 生成代理。<b>但如果通过接口类型注入，可能会出现类型转换异常</b>。
 * 因为 CGLIB 生成的代理类实现了该接口，但它是目标类的子类，不是直接实现了接口的 Bean。</p>
 *
 * @author Spring Interview QA
 */
public class ProxySelectionDemo {

    public static void main(String[] args) {
        System.out.println("========== Q7: 代理选择 Demo ==========\n");

        // ===== JDK 动态代理 (proxyTargetClass = false) =====
        System.out.println("--- JDK 动态代理 (proxyTargetClass = false) ---");
        AnnotationConfigApplicationContext jdkCtx =
                new AnnotationConfigApplicationContext(JdkProxyConfig.class);

        GreetingService jdkProxy = jdkCtx.getBean(GreetingService.class);
        System.out.println("JDK 代理类: " + jdkProxy.getClass().getName());
        // 输出: jdk.proxy2.$ProxyXX (JDK 生成的代理类，实现了 GreetingService 接口)
        System.out.println("is JDK Proxy? " + java.lang.reflect.Proxy.isProxyClass(jdkProxy.getClass()));
        jdkCtx.close();

        // ===== CGLIB 代理 (proxyTargetClass = true) =====
        System.out.println("\n--- CGLIB 代理 (proxyTargetClass = true) ---");
        AnnotationConfigApplicationContext cglibCtx =
                new AnnotationConfigApplicationContext(CglibProxyConfig.class);

        GreetingService cglibProxy = cglibCtx.getBean(GreetingService.class);
        System.out.println("CGLIB 代理类: " + cglibProxy.getClass().getName());
        // 输出: com.example...GreetingServiceImpl$$SpringCGLIB$$0 (CGLIB 生成的子类)
        System.out.println("is CGLIB Proxy? " + cglibProxy.getClass().getName().contains("CGLIB"));
        cglibCtx.close();

        /*
         * 【Spring 设计意图】
         * 为什么 Spring 要提供两种代理方式？
         * JDK 动态代理是 JDK 内置的，零依赖，但只能代理接口。
         * CGLIB 是第三方库（现已内嵌在 spring-core 中），能代理任意类。
         *
         * Spring 的 AOP 接口设计也反映了这一点：
         * - AopProxy 接口有两个实现：
         *   - JdkDynamicAopProxy  (JDK 方式)
         *   - CglibAopProxy       (CGLIB 方式)
         * Spring 通过"策略模式"根据配置选择合适的代理方式。
         *
         * Spring Boot 2.x 默认 CGLIB 的决定反映了一个重要哲学：
         * "默认行为应该符合大多数人的直觉"。
         * 但这也有代价——CGLIB 代理的类不能是 final，
         * 所以 Spring 生态中提倡"不要随意把类声明为 final"。
         */

        System.out.println("\n========== Demo 结束 ==========");
    }

    // ================================================================
    // 接口 + 实现
    // ================================================================
    interface GreetingService {
        String greet(String name);
    }

    @Component
    static class GreetingServiceImpl implements GreetingService {
        @Override
        public String greet(String name) {
            return "Hello, " + name;
        }
    }

    // ================================================================
    // 两套配置：JDK vs CGLIB
    // ================================================================
    @Configuration
    @ComponentScan(basePackageClasses = ProxySelectionDemo.class)
    @EnableAspectJAutoProxy(proxyTargetClass = false)  // JDK 动态代理
    static class JdkProxyConfig {
    }

    @Configuration
    @ComponentScan(basePackageClasses = ProxySelectionDemo.class)
    @EnableAspectJAutoProxy(proxyTargetClass = true)   // CGLIB 代理
    static class CglibProxyConfig {
    }
}
