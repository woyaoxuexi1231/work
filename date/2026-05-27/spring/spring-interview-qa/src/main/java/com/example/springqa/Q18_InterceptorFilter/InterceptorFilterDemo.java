package com.example.springqa.Q18_InterceptorFilter;

import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <h1>Q18：拦截器与过滤器 — HandlerInterceptor vs Filter</h1>
 */
@RestController
public class InterceptorFilterDemo {


    @GetMapping("/q18")
    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 拦截器 vs 过滤器 ===\n\n");
        sb.append("访问 /q18.html 查看完整架构师级回答。\n");
        sb.append("本接口演示 Spring 容器中正在运行的代码逻辑。\n");
        return sb.toString();
    }
}
