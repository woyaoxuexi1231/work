package com.example.springqa.Q02_CircularDependency;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Q02ServiceB {

    @Autowired
    private Q02ServiceA serviceA;

    public Q02ServiceB() {
        System.out.println("  Q02ServiceB 构造器");
    }

    public Q02ServiceA getServiceA() { return serviceA; }
}
