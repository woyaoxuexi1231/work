package com.example.springqa.Q11_CustomAop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * <h1>Q11：自研简化版 AOP 引擎</h1>
 *
 * <h2>面试点</h2>
 * <ul>
 *   <li>如果让你自己实现一个支持前置、后置、环绕通知的 AOP 引擎，核心接口怎么设计？</li>
 *   <li>如何在 Bean 初始化时织入代理？</li>
 * </ul>
 *
 * <h2>设计思路</h2>
 *
 * <p>一个最小化的 AOP 引擎需要以下几个部分：</p>
 * <ol>
 *   <li><b>Advice 接口族</b>：BeforeAdvice / AfterAdvice / AroundAdvice</li>
 *   <li><b>Pointcut</b>：判断哪些方法需要增强</li>
 *   <li><b>Aspect 注册</b>：将 Advice + Pointcut 绑定</li>
 *   <li><b>代理工厂</b>：在 Bean 初始化时，如果匹配切点，创建代理</li>
 *   <li><b>调用链</b>：多个 Advice 串联执行</li>
 * </ol>
 *
 * <p>以下是完整实现。大约 200 行代码，覆盖了 Spring AOP 最核心的设计思想。</p>
 *
 * <h2>Spring 为什么这样设计？</h2>
 * <ul>
 *   <li><b>Advice 接口族而非一个接口</b>：不同类型的通知有不同的方法签名——
 *       前置通知不需要返回值，环绕通知需要。分开设计比"一个接口 + if判断"更内聚。</li>
 *   <li><b>Pointcut 用接口抽象</b>：切点可能是注解、表达式、正则……接口让扩展变得容易。</li>
 *   <li><b>代理在初始化时织入</b>：而不是编译期。这样类可以独立于 AOP 运行，
 *       AOP 只是"外挂"——这是 Spring AOP 和 AspectJ 最大的区别。</li>
 * </ul>
 *
 * @author Spring Interview QA
 */
public class CustomAopDemo {

    // ================================================================
    // 1. Advice 接口族 — 前置 / 后置 / 环绕
    // ================================================================

    /** 通知标记接口 */
    interface Advice {}

    /** 前置通知：目标方法执行前调用 */
    @FunctionalInterface
    interface BeforeAdvice extends Advice {
        void before(Method method, Object[] args, Object target);
    }

    /** 后置通知：目标方法执行后调用（无论是否异常） */
    @FunctionalInterface
    interface AfterAdvice extends Advice {
        void after(Method method, Object[] args, Object target, Object result);
    }

    /** 异常通知：目标方法抛异常时调用 */
    @FunctionalInterface
    interface ThrowsAdvice extends Advice {
        void afterThrowing(Method method, Object[] args, Object target, Exception e);
    }

    /** 环绕通知：完全控制方法执行 */
    @FunctionalInterface
    interface AroundAdvice extends Advice {
        Object around(Method method, Object[] args, Object target,
                      MethodInvoker invoker) throws Throwable;
    }

    /** 环绕通知中的"执行目标方法"的回调 */
    @FunctionalInterface
    interface MethodInvoker {
        Object invoke() throws Throwable;
    }

    // ================================================================
    // 2. Pointcut — 判断方法是否匹配
    // ================================================================

    interface Pointcut {
        boolean matches(Method method, Class<?> targetClass);
    }

    /** 基于方法注解的切点 */
    static class AnnotationPointcut implements Pointcut {
        private final Class<? extends java.lang.annotation.Annotation> annotationType;

        AnnotationPointcut(Class<? extends java.lang.annotation.Annotation> annotationType) {
            this.annotationType = annotationType;
        }

        @Override
        public boolean matches(Method method, Class<?> targetClass) {
            return method.isAnnotationPresent(annotationType);
        }
    }

    // ================================================================
    // 3. Aspect — 将 Advice 和 Pointcut 绑定
    // ================================================================

    static class Aspect {
        final String name;
        final Pointcut pointcut;
        final List<Advice> advices = new ArrayList<>();
        int order = 0;

        Aspect(String name, Pointcut pointcut) {
            this.name = name;
            this.pointcut = pointcut;
        }

        Aspect addAdvice(Advice advice) {
            this.advices.add(advice);
            return this;
        }

        Aspect order(int order) {
            this.order = order;
            return this;
        }
    }

    // ================================================================
    // 4. AOP 引擎 — 注册切面 + 创建代理
    // ================================================================

    static class AopEngine {
        private final List<Aspect> aspects = new CopyOnWriteArrayList<>();
        // 缓存：哪些类已经被代理过
        private final ConcurrentHashMap<Class<?>, Class<?>> proxyCache = new ConcurrentHashMap<>();

        /** 注册切面 */
        public void registerAspect(Aspect aspect) {
            aspects.add(aspect);
            aspects.sort(Comparator.comparingInt(a -> a.order));
        }

        /**
         * 如果目标对象匹配任何切点，返回代理对象；否则返回原对象。
         *
         * 【Spring 设计意图】
         * 这个方法对应 Spring 中 AnnotationAwareAspectJAutoProxyCreator
         * 的 postProcessAfterInitialization() 方法。
         * 它在 Bean 初始化完成后被调用，如果有匹配的切点就包装代理。
         */
        @SuppressWarnings("unchecked")
        public <T> T wrapIfNecessary(T target) {
            Class<?> targetClass = target.getClass();
            List<Aspect> matched = new ArrayList<>();

            for (Aspect aspect : aspects) {
                for (Method method : targetClass.getDeclaredMethods()) {
                    if (aspect.pointcut.matches(method, targetClass)) {
                        matched.add(aspect);
                        break; // 一个切面只加一次
                    }
                }
            }

            if (matched.isEmpty()) {
                return target; // 没有匹配的切面，返回原对象
            }

            // 创建 JDK 动态代理
            return (T) Proxy.newProxyInstance(
                    targetClass.getClassLoader(),
                    targetClass.getInterfaces(),
                    new AopInvocationHandler(target, matched)
            );
        }
    }

    // ================================================================
    // 5. InvocationHandler — 拦截方法调用，执行通知链
    // ================================================================

    static class AopInvocationHandler implements InvocationHandler {
        private final Object target;
        private final List<Aspect> aspects;

        AopInvocationHandler(Object target, List<Aspect> aspects) {
            this.target = target;
            this.aspects = aspects;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // 找到匹配此方法的切面
            List<Aspect> matchedAspects = new ArrayList<>();
            for (Aspect aspect : aspects) {
                if (aspect.pointcut.matches(method, target.getClass())) {
                    matchedAspects.add(aspect);
                }
            }

            if (matchedAspects.isEmpty()) {
                // 不匹配任何切面，直接调用目标方法
                return method.invoke(target, args);
            }

            // 构建 MethodInvoker 链（从最内层到最外层）
            MethodInvoker chain = () -> method.invoke(target, args);

            // 倒序遍历（外层先注册，执行时应该外层先进）
            for (int i = matchedAspects.size() - 1; i >= 0; i--) {
                Aspect aspect = matchedAspects.get(i);
                MethodInvoker next = chain;

                chain = () -> {
                    // 执行前置通知
                    for (Advice advice : aspect.advices) {
                        if (advice instanceof BeforeAdvice) {
                            ((BeforeAdvice) advice).before(method, args, target);
                        }
                    }

                    Object result;
                    try {
                        // 查找环绕通知
                        AroundAdvice around = null;
                        for (Advice advice : aspect.advices) {
                            if (advice instanceof AroundAdvice) {
                                around = (AroundAdvice) advice;
                                break;
                            }
                        }

                        if (around != null) {
                            result = around.around(method, args, target, next);
                        } else {
                            result = next.invoke();
                        }
                    } catch (Exception e) {
                        // 异常通知
                        for (Advice advice : aspect.advices) {
                            if (advice instanceof ThrowsAdvice) {
                                ((ThrowsAdvice) advice)
                                        .afterThrowing(method, args, target, e);
                            }
                        }
                        throw e;
                    }

                    // 后置通知
                    for (Advice advice : aspect.advices) {
                        if (advice instanceof AfterAdvice) {
                            ((AfterAdvice) advice).after(method, args, target, result);
                        }
                    }

                    return result;
                };
            }

            return chain.invoke();
        }
    }

    // ================================================================
    // 6. 模拟 Spring 的 Bean 容器（初始化时织入代理）
    // ================================================================

    static class SimpleContainer {
        private final AopEngine aopEngine = new AopEngine();
        private final ConcurrentHashMap<String, Object> beans = new ConcurrentHashMap<>();

        void registerAspect(Aspect aspect) {
            aopEngine.registerAspect(aspect);
        }

        <T> void registerBean(String name, T bean) {
            // 【关键】在注册 Bean 时织入 AOP 代理
            // 对应 Spring 的 postProcessAfterInitialization()
            T wrapped = aopEngine.wrapIfNecessary(bean);
            beans.put(name, wrapped);
            System.out.println("  注册 Bean: " + name
                    + " → " + wrapped.getClass().getSimpleName());
        }

        @SuppressWarnings("unchecked")
        <T> T getBean(String name) {
            return (T) beans.get(name);
        }
    }

    // ================================================================
    // 演示用的自定义注解
    // ================================================================
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Loggable {
    }

    // ================================================================
    // 演示用的业务接口和实现
    // ================================================================
    interface OrderService {
        void createOrder(String item);
        void cancelOrder(String orderId);
        String queryOrder(String orderId);
    }

    static class OrderServiceImpl implements OrderService {
        @Loggable
        @Override
        public void createOrder(String item) {
            System.out.println("    📦 创建订单: " + item);
        }

        @Override
        public void cancelOrder(String orderId) {
            System.out.println("    🗑 取消订单: " + orderId);
        }

        @Loggable
        @Override
        public String queryOrder(String orderId) {
            System.out.println("    🔍 查询订单: " + orderId);
            return "Order{id=" + orderId + "}";
        }
    }

    // ================================================================
    // Main
    // ================================================================
    public static void main(String[] args) {
        System.out.println("========== Q11: 自研 AOP 引擎 Demo ==========\n");

        // 1. 创建容器
        SimpleContainer container = new SimpleContainer();
        System.out.println("--- 注册切面 ---");

        // 2. 注册切面：对 @Loggable 方法做日志增强
        container.registerAspect(new Aspect("logging", new AnnotationPointcut(Loggable.class))
                .order(1)
                .addAdvice((BeforeAdvice) (method, args2, target) ->
                        System.out.println("  🔵 [日志] 前置: " + method.getName()
                                + Arrays.toString(args2)))
                .addAdvice((AfterAdvice) (method, args2, target, result) ->
                        System.out.println("  🟢 [日志] 后置: " + method.getName()
                                + " → " + result))
        );

        // 3. 注册切面：对所有方法做性能监控
        container.registerAspect(new Aspect("perf", new Pointcut() {
            @Override
            public boolean matches(Method method, Class<?> targetClass) {
                return true; // 匹配所有方法
            }
        })
                .order(2)
                .addAdvice((AroundAdvice) (method, args2, target, invoker) -> {
                    long start = System.nanoTime();
                    Object result = invoker.invoke();
                    long duration = System.nanoTime() - start;
                    System.out.println("  ⏱ [性能] " + method.getName()
                            + " 耗时: " + duration / 1000 + "μs");
                    return result;
                })
        );

        // 4. 注册 Bean（此时自动织入代理）
        System.out.println("\n--- 注册 Bean（织入 AOP 代理）---");
        container.registerBean("orderService", new OrderServiceImpl());

        // 5. 使用
        System.out.println("\n--- 调用 createOrder() —— 有 @Loggable ---");
        OrderService svc = container.getBean("orderService");
        svc.createOrder("iPhone 15");

        System.out.println("\n--- 调用 cancelOrder() —— 无 @Loggable，但性能监控全局 ---");
        svc.cancelOrder("ORD-123");

        System.out.println("\n--- 调用 queryOrder() —— 有 @Loggable + 性能监控 ---");
        String result = svc.queryOrder("ORD-456");
        System.out.println("  查询结果: " + result);

        System.out.println("\n========== Demo 结束 ==========");
    }
}
