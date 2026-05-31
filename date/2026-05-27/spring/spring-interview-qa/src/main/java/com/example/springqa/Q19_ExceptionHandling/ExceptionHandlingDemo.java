package com.example.springqa.Q19_ExceptionHandling;

import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <h1>Q19：统一异常处理 — @ControllerAdvice + @ExceptionHandler</h1>
 */
@RestController
public class ExceptionHandlingDemo {


    @GetMapping("/q19")
    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 异常处理 ===\n\n");
        sb.append("访问 /q19.html 查看完整架构师级回答。\n");
        sb.append("本接口演示 Spring 容器中正在运行的代码逻辑。\n");
        return sb.toString();
    }
}
