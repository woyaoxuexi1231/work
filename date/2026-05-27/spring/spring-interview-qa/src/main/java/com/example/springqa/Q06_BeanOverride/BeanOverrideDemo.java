package com.example.springqa.Q06_BeanOverride;

import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BeanOverrideDemo {

    private final ApplicationContext ctx;

    public BeanOverrideDemo(ApplicationContext ctx) { this.ctx = ctx; }

    @GetMapping("/q06")
    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Q06: Bean 覆盖 ===\n\n");
        sb.append("当前容器 Bean 定义数: ").append(ctx.getBeanDefinitionCount()).append("\n");
        sb.append("allow-bean-definition-overriding: true（本项目已开启）\n\n");
        sb.append("Spring Boot 2.1+ 默认禁止覆盖 → fail-fast 原则\n");
        sb.append("Spring Framework 原生默认允许覆盖 → 后注册覆盖先注册\n\n");
        sb.append("实际冲突排查: --debug 启动 → 看 AUTO-CONFIGURATION REPORT\n\n");
        sb.append("━━━ 完整分析 → /q06.html ━━━\n");
        return sb.toString();
    }
}
