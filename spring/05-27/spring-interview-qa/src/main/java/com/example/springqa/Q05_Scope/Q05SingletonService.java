package com.example.springqa.Q05_Scope;

import org.springframework.stereotype.Component;

@Component
public class Q05SingletonService {
    public Q05SingletonService() {
        System.out.println("  Q05SingletonService 创建");
    }
}
