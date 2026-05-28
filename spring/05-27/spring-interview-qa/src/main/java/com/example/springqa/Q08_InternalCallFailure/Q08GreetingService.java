package com.example.springqa.Q08_InternalCallFailure;

import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Q08GreetingService {

    @Autowired
    private Q08GreetingService self;

    public String greet(String name) {
        return "Hello, " + name;
    }

    /** ❌ this 是原始对象，切面不触发 */
    public String wrapperMethod(String name) {
        return this.innerGreet(name);
    }

    /** ✅ AopContext.currentProxy() */
    public String wrapperWithCurrentProxy(String name) {
        Q08GreetingService proxy = (Q08GreetingService) AopContext.currentProxy();
        return proxy.innerGreet(name);
    }

    /** ✅ 注入自身（Spring 注入的是代理） */
    public String wrapperWithSelfInjection(String name) {
        return self.innerGreet(name);
    }

    public String innerGreet(String name) {
        return "Inner: Hello, " + name;
    }
}
