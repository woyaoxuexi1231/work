package com.example.springqa.Q17_ParameterBinding;

import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <h1>Q17：参数绑定与转换 — HttpMessageConverter</h1>
 */
@RestController
public class ParameterBindingDemo {


    @GetMapping("/q17")
    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 参数绑定 ===\n\n");
        sb.append("访问 /q17.html 查看完整架构师级回答。\n");
        sb.append("本接口演示 Spring 容器中正在运行的代码逻辑。\n");
        return sb.toString();
    }
}
