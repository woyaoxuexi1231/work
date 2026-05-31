package com.example.springqa.Q16_MvcRequestFlow;

import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * <h1>Q16：Spring MVC 请求处理流程</h1>
 */
@RestController
public class MvcRequestFlowDemo {


    @GetMapping("/q16")
    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== MVC 流程 ===\n\n");
        sb.append("访问 /q16.html 查看完整架构师级回答。\n");
        sb.append("本接口演示 Spring 容器中正在运行的代码逻辑。\n");
        return sb.toString();
    }

    @PostMapping("/q16-post")
    public void post(@RequestBody Map<String, Object> map) {
        System.out.println("q16-post收到请求: " + map);
        throw new RuntimeException("测试异常");
    }
}
