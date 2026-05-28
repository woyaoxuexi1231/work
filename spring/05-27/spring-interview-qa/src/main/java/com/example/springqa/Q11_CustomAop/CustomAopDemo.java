package com.example.springqa.Q11_CustomAop;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * <h1>Q11：自研简化版 AOP 引擎 (~200 行)</h1>
 *
 * <h2>核心组件</h2>
 * <ol>
 *   <li>Advice 接口族 — BeforeAdvice / AfterAdvice / AroundAdvice / ThrowsAdvice</li>
 *   <li>Pointcut — 判断哪些方法需要增强</li>
 *   <li>Aspect — Advice + Pointcut 绑定</li>
 *   <li>AopEngine — 注册切面 + 创建代理（对应 Spring 的 AutoProxyCreator）</li>
 *   <li>InvocationHandler — 拦截方法调用，执行通知链（对应 ReflectiveMethodInvocation）</li>
 * </ol>
 *
 * <h2>Spring 为什么这样设计？</h2>
 * <ul>
 *   <li>Advice 接口族而非一个接口 — 不同类型通知有不同方法签名，分开更内聚</li>
 *   <li>Pointcut 用接口抽象 — 切点可能是注解/表达式/正则，接口让扩展容易</li>
 *   <li>代理在初始化时织入 — 不是编译期。类可独立于 AOP 运行，AOP 只是"外挂"</li>
 * </ul>
 */
@Component
public class CustomAopDemo {

    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Q11: 自研 AOP 引擎 ===\n\n");

        // 1. 创建引擎
        AopEngine engine = new AopEngine();

        // 2. 注册切面
        engine.registerAspect(new Aspect("logging", new AnnotationPointcut(Loggable.class))
                .order(1)
                .addAdvice((BeforeAdvice) (method, args, target) ->
                        sb.append("  [Before] ").append(method.getName())
                                .append(Arrays.toString(args)).append("\n"))
                .addAdvice((AfterAdvice) (method, args, target, result) ->
                        sb.append("  [After]  ").append(method.getName())
                                .append(" → ").append(result).append("\n"))
        );

        engine.registerAspect(new Aspect("perf", (method, targetClass) -> true) // 匹配所有方法
                .order(2)
                .addAdvice((AroundAdvice) (method, args, target, invoker) -> {
                    long start = System.nanoTime();
                    Object result = invoker.invoke();
                    long us = (System.nanoTime() - start) / 1000;
                    sb.append("  [Perf] ").append(method.getName())
                            .append(" 耗时: ").append(us).append("μs\n");
                    return result;
                })
        );

        // 3. 注册 Bean + 自动织入
        engine.registerBean("orderService", new OrderServiceImpl());

        // 4. 调用
        OrderService svc = engine.getBean("orderService");
        sb.append("--- createOrder (有 @Loggable + 性能监控) ---\n");
        svc.createOrder("iPhone 15");

        sb.append("\n--- cancelOrder (仅性能监控，无 @Loggable) ---\n");
        svc.cancelOrder("ORD-123");

        sb.append("\n--- queryOrder (有 @Loggable + 性能监控) ---\n");
        sb.append("结果: ").append(svc.queryOrder("ORD-456")).append("\n\n");

        sb.append("【核心设计】\n");
        sb.append("1. Advice 接口族 → 不同通知类型不同签名，各司其职\n");
        sb.append("2. Pointcut 接口 → 注解/表达式/正则都可实现，扩展性强\n");
        sb.append("3. MethodInvoker 链 → 递归结构实现洋葱模型（对应 ReflectiveMethodInvocation）\n");
        sb.append("4. 代理在 registerBean 时织入 → 对应 Spring 的 postProcessAfterInitialization\n");

        return sb.toString();
    }

    // ================================================================
    // 1. Advice 接口族
    // ================================================================
    interface Advice {}
    interface BeforeAdvice extends Advice {
        void before(Method method, Object[] args, Object target);
    }
    interface AfterAdvice extends Advice {
        void after(Method method, Object[] args, Object target, Object result);
    }
    interface AroundAdvice extends Advice {
        Object around(Method method, Object[] args, Object target, MethodInvoker invoker) throws Throwable;
    }
    interface MethodInvoker {
        Object invoke() throws Throwable;
    }

    // ================================================================
    // 2. Pointcut
    // ================================================================
    interface Pointcut {
        boolean matches(Method method, Class<?> targetClass);
    }

    static class AnnotationPointcut implements Pointcut {
        private final Class<? extends java.lang.annotation.Annotation> type;
        AnnotationPointcut(Class<? extends java.lang.annotation.Annotation> type) { this.type = type; }
        @Override public boolean matches(Method method, Class<?> targetClass) {
            return method.isAnnotationPresent(type);
        }
    }

    // ================================================================
    // 3. Aspect
    // ================================================================
    static class Aspect {
        final String name;
        final Pointcut pointcut;
        final List<Advice> advices = new ArrayList<>();
        int order;

        Aspect(String name, Pointcut pointcut) { this.name = name; this.pointcut = pointcut; }
        Aspect addAdvice(Advice a) { advices.add(a); return this; }
        Aspect order(int o) { this.order = o; return this; }
    }

    // ================================================================
    // 4. AopEngine + InvocationHandler
    // ================================================================
    static class AopEngine {
        private final List<Aspect> aspects = new CopyOnWriteArrayList<>();
        private final java.util.Map<String, Object> beans = new java.util.concurrent.ConcurrentHashMap<>();

        void registerAspect(Aspect aspect) {
            aspects.add(aspect);
            aspects.sort(Comparator.comparingInt(a -> a.order));
        }

        @SuppressWarnings("unchecked")
        <T> void registerBean(String name, T bean) {
            List<Aspect> matched = new ArrayList<>();
            for (Aspect a : aspects) {
                for (Method m : bean.getClass().getDeclaredMethods()) {
                    if (a.pointcut.matches(m, bean.getClass())) { matched.add(a); break; }
                }
            }
            if (matched.isEmpty()) { beans.put(name, bean); return; }
            beans.put(name, Proxy.newProxyInstance(
                    bean.getClass().getClassLoader(),
                    bean.getClass().getInterfaces(),
                    new AopHandler(bean, matched)));
        }

        @SuppressWarnings("unchecked")
        <T> T getBean(String name) { return (T) beans.get(name); }
    }

    static class AopHandler implements InvocationHandler {
        private final Object target;
        private final List<Aspect> aspects;

        AopHandler(Object target, List<Aspect> aspects) {
            this.target = target; this.aspects = aspects;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            List<Aspect> matched = new ArrayList<>();
            for (Aspect a : aspects) {
                if (a.pointcut.matches(method, target.getClass())) matched.add(a);
            }
            if (matched.isEmpty()) return method.invoke(target, args);

            MethodInvoker chain = () -> method.invoke(target, args);
            for (int i = matched.size() - 1; i >= 0; i--) {
                Aspect aspect = matched.get(i);
                MethodInvoker next = chain;
                chain = () -> {
                    for (Advice a : aspect.advices) {
                        if (a instanceof BeforeAdvice)
                            ((BeforeAdvice) a).before(method, args, target);
                    }
                    Object result;
                    AroundAdvice around = null;
                    for (Advice a : aspect.advices) {
                        if (a instanceof AroundAdvice) { around = (AroundAdvice) a; break; }
                    }
                    result = around != null ? around.around(method, args, target, next) : next.invoke();
                    for (Advice a : aspect.advices) {
                        if (a instanceof AfterAdvice)
                            ((AfterAdvice) a).after(method, args, target, result);
                    }
                    return result;
                };
            }
            return chain.invoke();
        }
    }

    // ================================================================
    // 演示业务
    // ================================================================
    @Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME)
    @interface Loggable {}

    interface OrderService {
        void createOrder(String item);
        void cancelOrder(String orderId);
        String queryOrder(String orderId);
    }

    static class OrderServiceImpl implements OrderService {
        @Loggable
        @Override public void createOrder(String item) {
            System.out.println("    📦 创建订单: " + item);
        }
        @Override public void cancelOrder(String orderId) {
            System.out.println("    🗑 取消订单: " + orderId);
        }
        @Loggable
        @Override public String queryOrder(String orderId) {
            System.out.println("    🔍 查询订单: " + orderId);
            return "Order{id=" + orderId + "}";
        }
    }
}
