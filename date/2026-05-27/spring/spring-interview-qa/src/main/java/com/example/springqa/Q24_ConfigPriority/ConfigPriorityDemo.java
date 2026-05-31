package com.example.springqa.Q24_ConfigPriority;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <h1>Q24：配置优先级 — 外部化配置的加载顺序</h1>
 */
@RestController
public class ConfigPriorityDemo {

    private final ConfigurableEnvironment env;

    public ConfigPriorityDemo(ConfigurableEnvironment env) {
        this.env = env;
    }


    @GetMapping("/q24")
    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 配置优先级 ===\n\n");
        sb.append("访问 /q24.html 查看完整架构师级回答。\n");
        sb.append("本接口演示 Spring 容器中正在运行的代码逻辑。\n");
        return sb.toString();
    }
}
