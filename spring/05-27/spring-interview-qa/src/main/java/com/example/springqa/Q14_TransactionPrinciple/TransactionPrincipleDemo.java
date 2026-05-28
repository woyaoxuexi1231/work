package com.example.springqa.Q14_TransactionPrinciple;

import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransactionPrincipleDemo {

    private final Q14BusinessService service;

    public TransactionPrincipleDemo(Q14BusinessService service) {
        this.service = service;
    }


    @GetMapping("/q14")
    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 事务原理 ===\n\n");
        sb.append("访问 /q14.html 查看完整架构师级回答。\n");
        sb.append("本接口演示 Spring 容器中正在运行的代码逻辑。\n");
        return sb.toString();
    }
}
