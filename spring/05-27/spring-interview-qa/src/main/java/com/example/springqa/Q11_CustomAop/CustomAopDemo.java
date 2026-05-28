package com.example.springqa.Q11_CustomAop;

import org.springframework.stereotype.Component;

@Component
public class CustomAopDemo {

    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Q11: 自研 AOP 引擎 ===\n\n");

        Q11AopEngine engine = new Q11AopEngine();

        engine.registerAspect(new Q11Aspect("logging", new Q11AnnotationPointcut(Q11Loggable.class))
                .order(1)
                .addAdvice((Q11BeforeAdvice) (method, args, target) ->
                        sb.append("  [Before] ").append(method.getName()).append("\n"))
                .addAdvice((Q11AfterAdvice) (method, args, target, result) ->
                        sb.append("  [After]  ").append(method.getName()).append(" → ").append(result).append("\n"))
        );

        engine.registerAspect(new Q11Aspect("perf", (method, targetClass) -> true)
                .order(2)
                .addAdvice((Q11AroundAdvice) (method, args, target, invoker) -> {
                    long s = System.nanoTime();
                    Object r = invoker.invoke();
                    sb.append("  [Perf] ").append(method.getName()).append(" ").append((System.nanoTime() - s) / 1000).append("μs\n");
                    return r;
                })
        );

        engine.registerBean("orderService", new Q11OrderServiceImpl());
        Q11OrderService svc = engine.getBean("orderService");

        svc.createOrder("iPhone 15");
        svc.cancelOrder("ORD-123");
        sb.append("queryOrder: ").append(svc.queryOrder("ORD-456")).append("\n\n");

        sb.append("【核心设计】\n");
        sb.append("1. Advice 接口族 → 各类型通知各司其职\n");
        sb.append("2. Pointcut 接口 → 注解/表达式/正则都可扩展\n");
        sb.append("3. 递归 MethodInvoker 链 → 洋葱模型\n");
        sb.append("4. 代理在 registerBean 时织入 → 对应 postProcessAfterInitialization\n");

        return sb.toString();
    }
}
