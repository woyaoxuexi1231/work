package com.example.springqa.Q24_ConfigPriority;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.stereotype.Component;

/**
 * <h1>Q24：配置优先级 — 外部化配置的加载顺序</h1>
 */
@Component
public class ConfigPriorityDemo {

    private final ConfigurableEnvironment env;

    public ConfigPriorityDemo(ConfigurableEnvironment env) {
        this.env = env;
    }

    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Q24: 配置优先级 ===\n\n");

        sb.append("配置加载顺序（从高到低）:\n");
        sb.append("  1. 命令行参数           --server.port=9090\n");
        sb.append("  2. SPRING_APPLICATION_JSON\n");
        sb.append("  3. Servlet 参数\n");
        sb.append("  4. JNDI\n");
        sb.append("  5. 环境变量              export SERVER_PORT=9090\n");
        sb.append("  6. 系统属性              -Dserver.port=9090\n");
        sb.append("  7. application-{profile}.yml\n");
        sb.append("  8. application.yml\n");
        sb.append("  9. @PropertySource\n");
        sb.append("  10. 默认值                SpringApplication.setDefaultProperties()\n\n");

        sb.append("当前激活的 profile: ").append(
                String.join(", ", env.getActiveProfiles().length > 0
                        ? java.util.Arrays.asList(env.getActiveProfiles())
                        : java.util.Collections.singletonList("default"))).append("\n\n");

        sb.append("多环境配置最佳实践:\n");
        sb.append("  application.yml             ← 公共配置\n");
        sb.append("  application-dev.yml         ← 开发\n");
        sb.append("  application-prod.yml        ← 生产\n");
        sb.append("  激活: spring.profiles.active=prod\n\n");
        sb.append("bootstrap.yml vs application.yml:\n");
        sb.append("  bootstrap.yml 是 Spring Cloud Config 概念，\n");
        sb.append("  Spring Boot 3.x / Cloud 2022+ 已移除，改用 spring.config.import。\n\n");

        sb.append("【核心原则】\n");
        sb.append("\"越靠近部署环境的配置，优先级越高\"。\n");
        sb.append("同一个构建产物在不同环境运行——不需重新打包。\n");

        return sb.toString();
    }
}
