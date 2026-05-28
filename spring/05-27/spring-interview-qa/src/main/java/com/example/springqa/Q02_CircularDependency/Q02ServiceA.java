package com.example.springqa.Q02_CircularDependency;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Q02ServiceA {

    @Autowired
    private Q02ServiceB serviceB;

    public Q02ServiceA() {
        System.out.println("  Q02ServiceA 构造器");
    }

    public Q02ServiceB getServiceB() { return serviceB; }
}
