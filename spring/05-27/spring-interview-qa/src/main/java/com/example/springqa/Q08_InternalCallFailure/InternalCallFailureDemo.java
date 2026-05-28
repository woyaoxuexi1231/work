package com.example.springqa.Q08_InternalCallFailure;

import org.springframework.stereotype.Component;

@Component
public class InternalCallFailureDemo {

    private final Q08GreetingService service;

    public InternalCallFailureDemo(Q08GreetingService service) {
        this.service = service;
    }

    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Q08: 内部调用失效 ===\n\n");

        sb.append("外部调用 → 切面生效: ").append(service.greet("World")).append(" ✅\n");
        sb.append("this 调用 → 切面失效: ").append(service.wrapperMethod("World")).append(" ❌\n");
        sb.append("AopContext: ").append(service.wrapperWithCurrentProxy("World")).append(" ✅\n");
        sb.append("@Autowired self: ").append(service.wrapperWithSelfInjection("World")).append(" ✅\n\n");

        sb.append("【根因】\n");
        sb.append("Spring AOP 基于代理。外部调用 proxy.method() 时被拦截，\n");
        sb.append("但内部 this.method() 的 this 指向原始对象 → 绕过代理。\n");
        sb.append("这不是 bug——是 Java 语言限制 + 代理模式的天生缺陷。\n");

        return sb.toString();
    }
}
