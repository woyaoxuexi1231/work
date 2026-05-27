package com.example.springqa.Q10_AopPrinciple;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.aspectj.weaver.tools.PointcutExpression;
import org.aspectj.weaver.tools.PointcutParser;
import org.aspectj.weaver.tools.ShadowMatch;
import org.springframework.aop.Advisor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * <h1>Q10：AOP 实现原理 — Advisor / Advice / Pointcut</h1>
 *
 * <h2>面试点</h2>
 * <ul>
 *   <li>Advisor、Advice、Pointcut 的关系？</li>
 *   <li>AspectJExpressionPointcut 如何判断一个类是否匹配切点？</li>
 *   <li>ReflectiveMethodInvocation 的递归调用链是如何工作的？</li>
 * </ul>
 *
 * <h2>核心概念关系</h2>
 * <pre>
 * Advisor = Advice + Pointcut
 *    ↑
 *    |-- Advice:  做什么？（增强逻辑——前置/后置/环绕/异常/返回）
 *    |-- Pointcut: 在哪里做？（切点表达式——匹配哪些类/方法）
 *
 * // 形象理解：
 * // Pointcut 是"筛子"，选出需要增强的方法
 * // Advice  是"动作"，对选出的方法做什么
 * // Advisor 把两者打包在一起
 * </pre>
 *
 * <h2>AspectJExpressionPointcut 匹配原理</h2>
 * <pre>
 * 切点表达式: execution(* com.example.UserService.*(..))
 *
 * Spring 的 AspectJExpressionPointcut 内部使用 AspectJ 的 PointcutParser
 * 来解析表达式。它基于方法的签名（返回类型、类名、方法名、参数类型）进行匹配。
 *
 * 匹配过程:
 * 1. 解析表达式 → PointcutExpression 对象
 * 2. 对于每个候选方法:
 *    a. 获取方法的 java.lang.reflect.Method
 *    b. 调用 PointcutExpression.matchesMethodExecution(method)
 *    c. 返回 true/false
 * 3. 匹配的方法被 Advisor 拦截
 * </pre>
 *
 * <h2>ReflectiveMethodInvocation 递归链</h2>
 * <p>这是 Spring AOP 最精妙的设计之一：</p>
 * <pre>
 * 假设有 3 个拦截器 [I1, I2, I3] 对应方法 doSomething()
 *
 * ReflectiveMethodInvocation.proceed() 伪代码:
 *
 * // currentInterceptorIndex 从 -1 开始
 * Object proceed() {
 *     if (++currentInterceptorIndex == interceptors.size()) {
 *         return invokeJoinpoint();  // 所有拦截器走完，执行目标方法
 *     }
 *     return interceptors[currentInterceptorIndex].invoke(this);
 *     //                                                       ↑
 *     //                           把 this (MethodInvocation) 传给拦截器
 *     //                           拦截器内部再调用 mi.proceed() → 递归！
 * }
 *
 * // 执行链:
 * I1.invoke(mi) → mi.proceed() → I2.invoke(mi) → mi.proceed()
 *   → I3.invoke(mi) → mi.proceed() → target.doSomething()
 *   → I3 返回 → I2 返回 → I1 返回
 * </pre>
 *
 * <p>Spring 把拦截器链做成了<b>递归调用</b>而非简单的 for 循环。
 * 为什么？因为每个拦截器的 invoke() 可以在 proceed() 前后做事情——
 * 递归的结构天然支持"进入时做 A，退出时做 B"的环绕语义。</p>
 *
 * @author Spring Interview QA
 */
public class AopPrincipleDemo {

    public static void main(String[] args) {
        System.out.println("========== Q10: AOP 原理 Demo ==========\n");

        // ===== 1. Pointcut 匹配演示 =====
        System.out.println("--- Pointcut 匹配演示 ---");
        demoPointcutMatching();

        // ===== 2. Advisor + Advice 手动织入演示 =====
        System.out.println("\n--- Advisor + Advice 手动织入演示 ---");
        demoAdvisorAdvice();

        // ===== 3. 递归拦截链演示 =====
        System.out.println("\n--- 递归拦截链演示 ---");
        demoRecursiveChain();

        System.out.println("\n========== Demo 结束 ==========");
    }

    // ---------------------------------------------------------------
    // 1. Pointcut 匹配
    // ---------------------------------------------------------------
    static void demoPointcutMatching() {
        /*
         * AspectJExpressionPointcut 内部使用 PointcutParser 解析表达式。
         * 匹配过程完全基于反射——不需要实际运行类的方法。
         *
         * Spring 为什么用 AspectJ 的表达式语法？
         * 因为 AspectJ 的切点表达式已经是事实标准，表达力强、社区熟悉。
         * Spring 只用了 AspectJ 的表达式解析器，没有用 AspectJ 的织入器。
         */
        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        pointcut.setExpression("execution(* com.example.springqa.Q10_AopPrinciple.BusinessService.*(..))");

        // 匹配 BusinessService 的方法
        for (Method method : BusinessService.class.getDeclaredMethods()) {
            boolean matches = pointcut.matches(method, BusinessService.class);
            System.out.println("  " + method.getName() + "() → " + (matches ? "✅ 匹配" : "❌ 不匹配"));
        }

        // 不匹配其他类
        boolean otherClass = pointcut.matches(
                String.class.getDeclaredMethods()[0], String.class);
        System.out.println("  String.length() → " + (otherClass ? "✅" : "❌") + " (不匹配)");
    }

    // ---------------------------------------------------------------
    // 2. Advisor + Advice 手动织入
    // ---------------------------------------------------------------
    static void demoAdvisorAdvice() {
        // Pointcut: 匹配 doBusiness 方法
        Pointcut pointcut = new AspectJExpressionPointcut();
        ((AspectJExpressionPointcut) pointcut)
                .setExpression("execution(* com.example.springqa.Q10_AopPrinciple.BusinessService.doBusiness(..))");

        // Advice: 环绕增强（MethodInterceptor 是 AOP Alliance 标准接口）
        /*
         * 【设计意图】
         * 为什么 Advice 使用的是 AOP Alliance 接口（org.aopalliance）？
         * AOP Alliance 是一个独立的 AOP 标准组织，定义了 MethodInterceptor、
         * MethodInvocation 等核心接口。Spring 遵守这个标准，使得理论上
         * 任何 AOP Alliance 兼容的拦截器都可以在 Spring 中使用。
         *
         * 这种"依赖标准接口而非具体实现"的设计，是 Spring 的核心哲学。
         */
        Advice advice = (MethodInterceptor) invocation -> {
            System.out.println("  [Advice] >>> 前置增强");
            Object result = invocation.proceed();  // 调用目标方法
            System.out.println("  [Advice] <<< 后置增强，返回值=" + result);
            return result;
        };

        // Advisor = Pointcut + Advice
        Advisor advisor = new DefaultPointcutAdvisor(pointcut, advice);

        // ProxyFactory 手动创建代理
        ProxyFactory factory = new ProxyFactory();
        factory.setTarget(new BusinessService());
        factory.addAdvisor(advisor);

        // 获取代理对象
        BusinessService proxy = (BusinessService) factory.getProxy();
        System.out.println("  代理类型: " + proxy.getClass().getName());
        proxy.doBusiness();        // 这个方法被增强了
        proxy.anotherMethod();     // 这个方法不被匹配，不增强
    }

    // ---------------------------------------------------------------
    // 3. 递归拦截链 — 手动模拟 ReflectiveMethodInvocation
    // ---------------------------------------------------------------
    static void demoRecursiveChain() {
        /*
         * 以下是 ReflectiveMethodInvocation 的简化实现。
         * 核心是 proceed() 的递归结构：每次 proceed() 调用下一个拦截器，
         * 最后一个拦截器调用目标方法。
         */
        List<MethodInterceptor> interceptors = new ArrayList<>();
        interceptors.add(mi -> {
            System.out.println("    [I1] 进入");
            Object result = mi.proceed();
            System.out.println("    [I1] 退出");
            return result;
        });
        interceptors.add(mi -> {
            System.out.println("    [I2] 进入");
            Object result = mi.proceed();
            System.out.println("    [I2] 退出");
            return result;
        });
        interceptors.add(mi -> {
            System.out.println("    [I3] 进入（最后一级）");
            // 这一级不调用 proceed()，而是执行"目标方法"
            System.out.println("    🎯 执行目标方法");
            System.out.println("    [I3] 退出");
            return "result";
        });

        // 模拟 ReflectiveMethodInvocation
        SimpleMethodInvocation invocation = new SimpleMethodInvocation(interceptors);
        invocation.proceed();
        /*
         * 输出顺序:
         *   [I1] 进入
         *   [I2] 进入
         *   [I3] 进入（最后一级）
         *   🎯 执行目标方法
         *   [I3] 退出
         *   [I2] 退出
         *   [I1] 退出
         *
         * 这就是环绕增强的"洋葱模型"！
         */
    }

    /**
     * 简化版 MethodInvocation — 模拟 ReflectiveMethodInvocation 的递归调用链
     */
    static class SimpleMethodInvocation implements MethodInvocation {

        private final List<MethodInterceptor> interceptors;
        private int currentIndex = -1;

        SimpleMethodInvocation(List<MethodInterceptor> interceptors) {
            this.interceptors = interceptors;
        }

        @Override
        public Object proceed() throws Throwable {
            /*
             * 【Spring 设计精妙之处】
             * 这个 currentIndex 是实例变量，在递归调用中不断递增。
             * 每次 proceed() 取出下一个拦截器，把 this 传给拦截器的 invoke()。
             * 拦截器内部再次调用 mi.proceed() 时——
             * 又回到这个方法，currentIndex 继续递增。
             *
             * 为什么不用 for 循环？
             * for 循环无法自然地支持"执行一半暂停，回来再继续"的语义。
             * 递归天然支持这种"进出"结构。
             *
             * 为什么用 currentIndex 递增而不是用递归参数？
             * 因为 MethodInvocation 接口的 proceed() 是无参的。
             * 状态必须保存在对象内部。这也是为什么每个方法调用都需要
             * 一个独立的 MethodInvocation 对象——它不是线程安全的。
             */
            if (++currentIndex >= interceptors.size()) {
                // 所有拦截器走完，执行目标方法
                return "target-result";
            }
            return interceptors.get(currentIndex).invoke(this);
        }

        // 以下方法是 MethodInvocation 接口的其他方法（简化实现）
        @Override public Method getMethod() { return null; }
        @Override public Object[] getArguments() { return new Object[0]; }
        @Override public Object getThis() { return this; }
        @Override public AccessibleObject getStaticPart() { return null; }
    }

    // ================================================================
    static class BusinessService {
        public String doBusiness() {
            System.out.println("    🎯 BusinessService.doBusiness() 执行");
            return "OK";
        }

        public String anotherMethod() {
            System.out.println("    BusinessService.anotherMethod() 执行（不应被拦截）");
            return "OK";
        }
    }
}
