package com.example.springqa.Q24_ConfigPriority;

import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

/**
 * <h1>Q24：配置优先级 — 外部化配置的加载顺序</h1>
 *
 * <h2>面试点</h2>
 * <ul>
 *   <li>外部化配置的加载顺序？</li>
 *   <li>命令行参数、环境变量、application.yml、bootstrap.yml 谁先谁后？</li>
 *   <li>多环境配置如何组织？</li>
 * </ul>
 *
 * <h2>配置加载顺序（从高到低）</h2>
 * <pre>
 * 1. 命令行参数               --server.port=9090
 * 2. SPRING_APPLICATION_JSON  SPRING_APPLICATION_JSON='{"server":{"port":9090}}'
 * 3. Servlet 参数             web.xml init-param
 * 4. JNDI                    java:comp/env/
 * 5. 环境变量                  export SERVER_PORT=9090
 * 6. 系统属性                  -Dserver.port=9090
 * 7. application-{profile}.yml
 * 8. application.yml
 * 9. @PropertySource
 * 10. 默认值                   SpringApplication.setDefaultProperties()
 * </pre>
 *
 * <h2>Spring 为什么这样设计？</h2>
 * <p>核心原则：<b>"越靠近部署环境的配置，优先级越高"</b>。
 * 代码中的默认值最通用，部署时可通过环境变量、命令行参数覆盖。
 * 同一个构建产物可在不同环境运行而不需重新打包。</p>
 *
 * @author Spring Interview QA
 */
public class ConfigPriorityDemo {

    public static void main(String[] args) {
        System.out.println("========== Q24: 配置优先级 Demo ==========\n");

        showPropertySourceOrder();
        System.out.println();
        demoConfigOverride();
        System.out.println();
        showMultiEnvConfig();

        System.out.println("\n========== Demo 结束 ==========");
    }

    static void showPropertySourceOrder() {
        StandardEnvironment env = new StandardEnvironment();
        MutablePropertySources sources = env.getPropertySources();

        System.out.println("PropertySource 列表（越靠上优先级越高）：");
        int i = 1;
        for (org.springframework.core.env.PropertySource<?> ps : sources) {
            System.out.printf("  %d. %-35s (%s)%n",
                    i++, ps.getName(), ps.getClass().getSimpleName());
        }
        System.out.println();
        System.out.println("Spring Boot 在基础上添加:");
        System.out.println("  - commandLineArgs                      ← 最高优先级");
        System.out.println("  - servletConfigInitParams");
        System.out.println("  - applicationConfig: [classpath:/application.yml]");
        System.out.println("  - applicationConfig: [classpath:/application-dev.yml]");
        System.out.println("  - ...");
        System.out.println();
        System.out.println("环境变量 key 转换: SERVER_PORT → server.port");
        System.out.println("(SystemEnvironmentPropertySource 自动处理 - 和 _ 的转换)");
    }

    static void demoConfigOverride() {
        MutablePropertySources sources = new MutablePropertySources();

        // 模拟 application.yml（默认值，优先级最低）
        Map<String, Object> appYml = new java.util.HashMap<>();
        appYml.put("server.port", "8080");
        appYml.put("app.name", "spring-qa");
        sources.addLast(new MapPropertySource("application.yml (classpath)", appYml));

        // 模拟外部 application.yml
        Map<String, Object> extYml = new java.util.HashMap<>();
        extYml.put("server.port", "8081");
        sources.addFirst(new MapPropertySource("application.yml (external)", extYml));

        // 模拟环境变量（优先级更高）
        Map<String, Object> sysEnv = new java.util.HashMap<>();
        sysEnv.put("SERVER_PORT", "9090");
        sources.addFirst(new MapPropertySource("systemEnvironment", sysEnv));

        // 模拟命令行参数（最高优先级）
        Map<String, Object> cmdArgs = new java.util.HashMap<>();
        cmdArgs.put("server.port", "9999");
        sources.addFirst(new MapPropertySource("commandLineArgs", cmdArgs));

        System.out.println("查找 server.port 的值（按优先级顺序）：");
        for (org.springframework.core.env.PropertySource<?> ps : sources) {
            Object value = ps.getProperty("server.port");
            if (value != null) {
                System.out.printf("  %-40s → server.port=%s ← 当前有效值%n",
                        ps.getName(), value);
            }
        }
        System.out.println("最终: 9999（命令行参数优先级最高）");
        System.out.println();
        System.out.println("查找逻辑: env.getProperty(\"server.port\")");
        System.out.println("→ 遍历 PropertySource 列表 → 第一个非 null 就是最终值");
        System.out.println("→ 这就是\"后者覆盖前者\"的实现");
    }

    static void showMultiEnvConfig() {
        System.out.println("多环境配置最佳实践:");
        System.out.println();
        System.out.println("1. 公共配置放 application.yml:");
        System.out.println("   spring.application.name: spring-interview-qa");
        System.out.println("   server.port: 8080");
        System.out.println();
        System.out.println("2. 环境专属放 application-{profile}.yml:");
        System.out.println("   application-dev.yml:");
        System.out.println("     server.port: 8081");
        System.out.println("     logging.level.root: DEBUG");
        System.out.println("   application-prod.yml:");
        System.out.println("     server.port: 80");
        System.out.println("     logging.level.root: WARN");
        System.out.println();
        System.out.println("3. 敏感配置用环境变量:");
        System.out.println("   export DB_PASSWORD=xxx");
        System.out.println("   export SPRING_DATASOURCE_URL=xxx");
        System.out.println();
        System.out.println("4. 本地覆盖（不提交 Git）:");
        System.out.println("   application-local.yml  ← 加入 .gitignore");
        System.out.println();
        System.out.println("5. 激活方式:");
        System.out.println("   方式一: spring.profiles.active=dev");
        System.out.println("   方式二: --spring.profiles.active=prod");
        System.out.println("   方式三: export SPRING_PROFILES_ACTIVE=prod");
        System.out.println();
        System.out.println("6. Profile 分组 (Spring Boot 2.4+):");
        System.out.println("   spring.profiles.group.dev=dev-db,dev-redis,dev-mq");
        System.out.println("   spring.profiles.group.prod=prod-db,prod-redis,prod-mq");
        System.out.println();
        System.out.println("核心原则: 越靠近部署环境，优先级越高。");
        System.out.println("同一个 jar 可在不同环境运行，不需要重新打包。");
    }
}
