package com.example.springqa.Q23_StartupFlow;

import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <h1>Q23：启动流程 — SpringApplication.run() 内部核心工作</h1>
 */
@RestController
public class StartupFlowDemo {


    @GetMapping("/q23")
    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 启动流程 ===\n\n");
        sb.append("访问 /q23.html 查看完整架构师级回答。\n");
        sb.append("本接口演示 Spring 容器中正在运行的代码逻辑。\n");
        return sb.toString();
    }
}
