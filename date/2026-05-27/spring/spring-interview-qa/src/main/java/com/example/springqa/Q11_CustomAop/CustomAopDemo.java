package com.example.springqa.Q11_CustomAop;

import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CustomAopDemo {


    @GetMapping("/q11")
    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 自研 AOP ===\n\n");
        sb.append("访问 /q11.html 查看完整架构师级回答。\n");
        sb.append("本接口演示 Spring 容器中正在运行的代码逻辑。\n");
        return sb.toString();
    }
}
