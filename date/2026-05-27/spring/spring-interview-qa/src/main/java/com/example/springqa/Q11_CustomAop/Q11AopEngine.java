package com.example.springqa.Q11_CustomAop;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

class Q11AopEngine {

    private final List<Q11Aspect> aspects = new CopyOnWriteArrayList<>();
    private final Map<String, Object> beans = new ConcurrentHashMap<>();

    void registerAspect(Q11Aspect aspect) {
        aspects.add(aspect);
        aspects.sort(Comparator.comparingInt(a -> a.order));
    }

    @SuppressWarnings("unchecked")
    <T> void registerBean(String name, T bean) {
        List<Q11Aspect> matched = new ArrayList<>();
        for (Q11Aspect a : aspects) {
            for (Method m : bean.getClass().getDeclaredMethods()) {
                if (a.pointcut.matches(m, bean.getClass())) { matched.add(a); break; }
            }
        }
        if (matched.isEmpty()) { beans.put(name, bean); return; }
        beans.put(name, Proxy.newProxyInstance(
                bean.getClass().getClassLoader(),
                bean.getClass().getInterfaces(),
                new Q11AopHandler(bean, matched)));
    }

    @SuppressWarnings("unchecked")
    <T> T getBean(String name) { return (T) beans.get(name); }
}

class Q11AopHandler implements InvocationHandler {
    private final Object target;
    private final List<Q11Aspect> aspects;

    Q11AopHandler(Object target, List<Q11Aspect> aspects) {
        this.target = target; this.aspects = aspects;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        List<Q11Aspect> matched = new ArrayList<>();
        for (Q11Aspect a : aspects) {
            if (a.pointcut.matches(method, target.getClass())) matched.add(a);
        }
        if (matched.isEmpty()) return method.invoke(target, args);

        Q11MethodInvoker chain = () -> method.invoke(target, args);
        for (int i = matched.size() - 1; i >= 0; i--) {
            Q11Aspect aspect = matched.get(i);
            Q11MethodInvoker next = chain;
            chain = () -> {
                for (Q11Advice a : aspect.advices) {
                    if (a instanceof Q11BeforeAdvice)
                        ((Q11BeforeAdvice) a).before(method, args, target);
                }
                Q11AroundAdvice around = null;
                for (Q11Advice a : aspect.advices) {
                    if (a instanceof Q11AroundAdvice) { around = (Q11AroundAdvice) a; break; }
                }
                Object result = around != null ? around.around(method, args, target, next) : next.invoke();
                for (Q11Advice a : aspect.advices) {
                    if (a instanceof Q11AfterAdvice)
                        ((Q11AfterAdvice) a).after(method, args, target, result);
                }
                return result;
            };
        }
        return chain.invoke();
    }
}
