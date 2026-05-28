package com.example.springqa.Q22_CustomStarter;

import org.springframework.stereotype.Component;

/**
 * <h1>Q22：自定义 Starter — autoconfigure + starter 双模块</h1>
 */
@Component
public class CustomStarterDemo {

    public String runDemo() {
        return "=== Q22: 自定义 Starter ===\n\n" +
            "标准双模块结构:\n" +
            "  my-spring-boot-starter/        ← 空壳 POM，只做依赖聚合\n" +
            "    └── pom.xml (引入 autoconfigure)\n\n" +
            "  my-spring-boot-autoconfigure/  ← 核心：自动配置 + 属性\n" +
            "    ├── MyProperties.java          ← @ConfigurationProperties\n" +
            "    ├── MyAutoConfiguration.java   ← @Configuration + @Conditional\n" +
            "    ├── MyService.java             ← 业务功能\n" +
            "    └── META-INF/spring.factories  ← 注册自动配置类\n\n" +
            "为什么双模块?\n" +
            "  关注点分离：starter 只管依赖，autoconfigure 只管配置。\n" +
            "  用户可只引入 autoconfigure 并手动排除不需要的配置。\n\n" +
            "@ConfigurationProperties IDE 提示:\n" +
            "  spring-boot-configuration-processor 编译时扫描 →\n" +
            "  生成 META-INF/spring-configuration-metadata.json →\n" +
            "  IDE 读取 → 自动补全 + 文档提示。不是手写的！\n\n" +
            "【Starter 机制的意义】\n" +
            "把\"引入一个功能\"简化为\"引入一个依赖\"——\n" +
            "不需要改 Java 代码，不需要加 XML 配置。\n" +
            "这是 Spring Boot 生态的基石。\n";
    }
}
