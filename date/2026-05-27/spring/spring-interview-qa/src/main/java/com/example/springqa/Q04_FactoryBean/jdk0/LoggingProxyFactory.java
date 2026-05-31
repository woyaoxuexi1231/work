package com.example.springqa.Q04_FactoryBean.jdk0;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * @author hulei
 * @since 2026/5/28 18:45
 */

public class LoggingProxyFactory {

    public Object getProxy() {
        Object o = Proxy.newProxyInstance(
                Calculator.class.getClassLoader(),
                new Class[]{Calculator.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        System.out.println("开始执行方法：" + method.getName());
                        // Object result = method.invoke(proxy, args);
                        System.out.println("骗你的，其实根本没有实现类。");
                        System.out.println("方法执行完毕：" + method.getName());
                        return 0;
                    }
                }
        );
        return o;
    }
}
