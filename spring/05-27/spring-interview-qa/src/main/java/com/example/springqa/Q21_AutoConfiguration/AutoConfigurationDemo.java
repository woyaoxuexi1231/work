package com.example.springqa.Q21_AutoConfiguration;

import org.springframework.stereotype.Component;

/**
 * <h1>Q21：自动装配原理 — spring.factories + 条件注解</h1>
 */
@Component
public class AutoConfigurationDemo {

    public String runDemo() {
        return "=== Q21: 自动装配原理 ===\n\n" +
            "@SpringBootApplication 的三个注解:\n" +
            "  = @SpringBootConfiguration     (本质 @Configuration)\n" +
            "  + @EnableAutoConfiguration     (触发自动装配 ★核心)\n" +
            "  + @ComponentScan               (扫描组件)\n\n" +
            "自动装配流程:\n" +
            "  @EnableAutoConfiguration\n" +
            "    → @Import(AutoConfigurationImportSelector.class)\n" +
            "    → selectImports():\n" +
            "      1. 加载 META-INF/spring.factories 中的配置类列表 (~180个)\n" +
            "      2. 条件注解过滤:\n" +
            "         @ConditionalOnClass       → 类路径有该类?\n" +
            "         @ConditionalOnMissingBean → 容器中没该 Bean?\n" +
            "         @ConditionalOnProperty    → 配置项为 true?\n" +
            "         @ConditionalOnWebApplication → 是 Web 应用?\n" +
            "      3. 过滤后就是需要生效的配置\n\n" +
            "示例: DataSourceAutoConfiguration\n" +
            "  @ConditionalOnClass(DataSource.class)      → 有驱动?\n" +
            "  @ConditionalOnMissingBean(DataSource.class) → 用户没定义?\n" +
            "  → 满足条件 → 自动创建 DataSource\n\n" +
            "【Spring 为什么这样设计？】\n" +
            "自动装配是 Spring Boot 最核心的创新——\"约定优于配置 + 条件化加载\"。\n" +
            "引入 starter → 自动配置加载 → 条件注解保证\"需要什么有什么\"。\n" +
            "用户可通过 @Bean 覆盖任何自动配置。\n";
    }
}
