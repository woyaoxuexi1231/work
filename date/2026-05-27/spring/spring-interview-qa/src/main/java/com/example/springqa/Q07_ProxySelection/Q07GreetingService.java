package com.example.springqa.Q07_ProxySelection;

import org.springframework.stereotype.Component;

@Component
public class Q07GreetingService {
    public String greet(String name) {
        return "Hello, " + name;
    }
}
