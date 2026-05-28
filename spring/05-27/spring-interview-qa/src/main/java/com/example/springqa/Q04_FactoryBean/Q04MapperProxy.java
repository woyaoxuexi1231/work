package com.example.springqa.Q04_FactoryBean;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class Q04MapperProxy implements InvocationHandler {

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        if ("findById".equals(method.getName())) {
            return "User{id=" + args[0] + ", name='模拟用户'}";
        }
        if ("insert".equals(method.getName())) {
            return 1;
        }
        try {
            return method.invoke(this, args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
