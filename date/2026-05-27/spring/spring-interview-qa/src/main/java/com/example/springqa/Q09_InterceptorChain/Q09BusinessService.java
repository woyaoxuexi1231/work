package com.example.springqa.Q09_InterceptorChain;

import org.springframework.stereotype.Component;

@Component
public class Q09BusinessService {
    public String doBusiness() {
        System.out.println("    🎯 目标方法 doBusiness()");
        return "OK";
    }

    public String doBusinessWithBlock() {
        System.out.println("    🎯 目标方法 doBusinessWithBlock()");
        return "OK";
    }
}
