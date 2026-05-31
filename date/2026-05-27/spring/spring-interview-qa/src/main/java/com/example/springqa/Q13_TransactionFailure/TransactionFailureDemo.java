package com.example.springqa.Q13_TransactionFailure;

import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransactionFailureDemo {

    private final Q13UserService service;

    public TransactionFailureDemo(Q13UserService service) {
        this.service = service;
    }


    @GetMapping("/q13")
    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 事务失效 ===\n\n");
        sb.append("访问 /q13.html 查看完整架构师级回答。\n");
        sb.append("本接口演示 Spring 容器中正在运行的代码逻辑。\n");
        return sb.toString();
    }
}
