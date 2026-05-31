package com.example.springqa.Q07_ProxySelection;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProxySelectionDemo {


    @GetMapping("/q07")
    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 代理选择 ===\n\n");
        sb.append("访问 /q07.html 查看完整架构师级回答。\n");
        sb.append("本接口演示 Spring 容器中正在运行的代码逻辑。\n");
        return sb.toString();
    }
}
