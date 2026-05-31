package com.example.springqa.Q08_InternalCallFailure;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalCallFailureDemo {

    private final Q08GreetingService service;

    public InternalCallFailureDemo(Q08GreetingService service) { this.service = service; }

    @GetMapping("/q08")
    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Q08: 内部调用失效 ===\n\n");
        sb.append("外部调用 → 切面生效:   ").append(service.greet("World")).append(" ✅\n");
        sb.append("this 调用 → 切面失效: ").append(service.wrapperMethod("World")).append(" ❌\n");
        sb.append("AopContext:           ").append(service.wrapperWithCurrentProxy("World")).append(" ✅\n");
        sb.append("@Autowired self:      ").append(service.wrapperWithSelfInjection("World")).append(" ✅\n\n");
        sb.append("根因: this 指向原始对象，不是代理。AOP 基于代理 → 内部调用绕过。\n");
        sb.append("三种解法: AopContext / 注入自身 / 重构到另一个类\n\n");
        sb.append("━━━ 完整分析 → /q08.html ━━━\n");
        return sb.toString();
    }
}
