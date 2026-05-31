package com.example.springqa.Q22_CustomStarter;

import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <h1>Q22：自定义 Starter — autoconfigure + starter 双模块</h1>
 */
@RestController
public class CustomStarterDemo {


    @GetMapping("/q22")
    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 自定义 Starter ===\n\n");
        sb.append("访问 /q22.html 查看完整架构师级回答。\n");
        sb.append("本接口演示 Spring 容器中正在运行的代码逻辑。\n");
        return sb.toString();
    }
}
