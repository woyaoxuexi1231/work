package com.example.springqa.Q21_AutoConfiguration;

import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <h1>Q21：自动装配原理 — spring.factories + 条件注解</h1>
 */
@RestController
public class AutoConfigurationDemo {


    @GetMapping("/q21")
    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 自动装配 ===\n\n");
        sb.append("访问 /q21.html 查看完整架构师级回答。\n");
        sb.append("本接口演示 Spring 容器中正在运行的代码逻辑。\n");
        return sb.toString();
    }
}
