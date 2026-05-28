package com.example.springqa.Q20_AsyncRequest;

import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <h1>Q20：异步请求 — DeferredResult / Callable / SSE</h1>
 */
@RestController
public class AsyncRequestDemo {


    @GetMapping("/q20")
    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 异步请求 ===\n\n");
        sb.append("访问 /q20.html 查看完整架构师级回答。\n");
        sb.append("本接口演示 Spring 容器中正在运行的代码逻辑。\n");
        return sb.toString();
    }
}
