package com.example.springqa.Q04_FactoryBean;

import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FactoryBeanDemo {

    private final ApplicationContext ctx;

    public FactoryBeanDemo(ApplicationContext ctx) { this.ctx = ctx; }

    @GetMapping("/q04")
    public String runDemo() {
        Q04UserMapper mapper = ctx.getBean("q04_userMapper", Q04UserMapper.class);
        Object fb = ctx.getBean("&q04_userMapper");

        StringBuilder sb = new StringBuilder();
        sb.append("=== Q04: FactoryBean ===\n\n");
        sb.append("getBean(\"q04_userMapper\"): ").append(mapper.getClass().getName()).append("\n");
        sb.append("mapper.findById(1L):        ").append(mapper.findById(1L)).append("\n");
        sb.append("getBean(\"&q04_userMapper\"): ").append(fb.getClass().getName()).append("\n\n");
        sb.append("FactoryBean 以 Bean 结尾（它是一个 Bean），\n");
        sb.append("BeanFactory 以 Factory 结尾（它是工厂）。\n");
        sb.append("getObject() 产物是 JDK 动态代理 — MyBatis Mapper 同理。\n\n");
        sb.append("━━━ 完整分析 → /q04.html ━━━\n");
        return sb.toString();
    }
}
