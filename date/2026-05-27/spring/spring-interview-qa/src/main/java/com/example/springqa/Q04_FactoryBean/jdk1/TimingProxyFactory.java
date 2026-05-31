package com.example.springqa.Q04_FactoryBean.jdk1;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * @author hulei
 * @since 2026/5/28 18:50
 */

public class TimingProxyFactory {

    public DataService getProxy(){
        ClassLoader classLoader = DataService.class.getClassLoader();
        Class[] interfaces = new Class[]{DataService.class};
        InvocationHandler invocationHandler = new InvocationHandler() {

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                long start = System.currentTimeMillis();
                System.out.println("开始执行方法：" + method.getName());
                Object result = method.invoke(proxy, args);
                System.out.println("方法执行完毕：" + method.getName());
                System.out.println("方法执行结果：" + result);
                System.out.println("方法执行耗时：" + (System.currentTimeMillis() - start) + "ms");
                return null;
            }
        };

        Object o = Proxy.newProxyInstance(
                classLoader,
                interfaces,
                invocationHandler
        );

        return (DataService) o;
    }
}
