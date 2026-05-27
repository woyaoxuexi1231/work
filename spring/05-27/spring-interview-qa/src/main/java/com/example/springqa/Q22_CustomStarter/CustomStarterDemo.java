package com.example.springqa.Q22_CustomStarter;

/**
 * <h1>Q22：自定义 Starter — autoconfigure + starter 双模块</h1>
 *
 * <h2>面试点</h2>
 * <ul>
 *   <li>如果让你写一个 my-starter，需要哪些模块？</li>
 *   <li>@ConfigurationProperties 如何实现 IDE 配置提示？</li>
 *   <li>spring-configuration-metadata.json 是手动写的吗？</li>
 * </ul>
 *
 * <h2>自定义 Starter 的标准结构（双模块）</h2>
 * <pre>
 * my-spring-boot-starter/           ← 空壳 POM，只引入 autoconfigure
 *   └── pom.xml
 *
 * my-spring-boot-autoconfigure/     ← 核心：自动配置 + 配置属性
 *   ├── pom.xml
 *   └── src/main/
 *       ├── java/com/example/my/
 *       │   ├── MyProperties.java           ← @ConfigurationProperties
 *       │   ├── MyAutoConfiguration.java    ← @AutoConfiguration
 *       │   └── MyService.java              ← 对外暴露的业务类
 *       └── resources/META-INF/spring.factories
 * </pre>
 *
 * <h2>为什么需要双模块？</h2>
 * <p>starter 模块只负责依赖管理，autoconfigure 模块包含自动配置和业务逻辑。
 * 用户可以独立引用 autoconfigure + 手动排除不需要的配置。</p>
 *
 * <h2>@ConfigurationProperties IDE 提示</h2>
 * <p>spring-boot-configuration-processor 编译时扫描 @ConfigurationProperties，
 * 生成 META-INF/spring-configuration-metadata.json，IDE 读取后提供自动补全。</p>
 *
 * <h2>Spring 为什么这样设计？</h2>
 * <p>Starter 机制是 Spring Boot 生态的基石——引入一个依赖就能自动配置。
 * 这种"约定优于配置 + 自动装配"让任何人都可以扩展 Spring Boot。</p>
 *
 * @author Spring Interview QA
 */
public class CustomStarterDemo {

    public static void main(String[] args) {
        System.out.println("========== Q22: 自定义 Starter Demo ==========\n");

        showStarterStructure();
        System.out.println();
        showConfigurationProperties();
        System.out.println();
        showAutoConfiguration();
        System.out.println();
        showSpringFactories();

        System.out.println("\n========== Demo 结束 ==========");
    }

    static void showStarterStructure() {
        System.out.println("标准 Starter 双模块结构:");
        System.out.println();
        System.out.println("my-spring-boot-starter/");
        System.out.println("├── pom.xml   ← 空壳 POM，只做依赖聚合");
        System.out.println("│   <dependencies>");
        System.out.println("│       <dependency>");
        System.out.println("│           <groupId>com.example</groupId>");
        System.out.println("│           <artifactId>my-spring-boot-autoconfigure</artifactId>");
        System.out.println("│       </dependency>");
        System.out.println("│   </dependencies>");
        System.out.println();
        System.out.println("my-spring-boot-autoconfigure/");
        System.out.println("├── pom.xml");
        System.out.println("│   <dependencies>");
        System.out.println("│       <dependency>");
        System.out.println("│           <groupId>org.springframework.boot</groupId>");
        System.out.println("│           <artifactId>spring-boot-autoconfigure</artifactId>");
        System.out.println("│       </dependency>");
        System.out.println("│       <dependency>");
        System.out.println("│           <groupId>org.springframework.boot</groupId>");
        System.out.println("│           <artifactId>spring-boot-configuration-processor</artifactId>");
        System.out.println("│           <optional>true</optional>  ← 只在编译时需要");
        System.out.println("│       </dependency>");
        System.out.println("│   </dependencies>");
        System.out.println("└── src/main/");
        System.out.println("    ├── java/com/example/my/");
        System.out.println("    │   ├── MyProperties.java         ← 配置属性");
        System.out.println("    │   ├── MyAutoConfiguration.java  ← 自动配置");
        System.out.println("    │   └── MyService.java            ← 业务功能");
        System.out.println("    └── resources/META-INF/");
        System.out.println("        └── spring.factories");
        System.out.println("            (内容: com.example.my.MyAutoConfiguration)");
        System.out.println();
        System.out.println("双模块设计的巧妙之处:");
        System.out.println("1. starter 只负责依赖管理（不包含任何代码）");
        System.out.println("2. autoconfigure 只负责配置和实现");
        System.out.println("3. 用户可只引入 autoconfigure 而不引入 starter");
    }

    static void showConfigurationProperties() {
        System.out.println("@ConfigurationProperties 配置绑定:");
        System.out.println();
        System.out.println("@ConfigurationProperties(prefix = \"my-starter\")");
        System.out.println("public class MyProperties {");
        System.out.println("    /** 是否启用 My Starter */");
        System.out.println("    private boolean enabled = true;");
        System.out.println("    /** 前缀 */");
        System.out.println("    private String prefix = \"QA-\";");
        System.out.println("    /** 超时时间（毫秒） */");
        System.out.println("    private int timeout = 3000;");
        System.out.println("    // getters and setters...");
        System.out.println("}");
        System.out.println();
        System.out.println("编译时，spring-boot-configuration-processor:");
        System.out.println("1. 扫描所有 @ConfigurationProperties 类");
        System.out.println("2. 提取 prefix、字段名、Javadoc、默认值");
        System.out.println("3. 生成 META-INF/spring-configuration-metadata.json");
        System.out.println("4. IDE 读取 → 自动补全 + 文档提示");
        System.out.println();
        System.out.println("用户 application.yml 中:");
        System.out.println("my-starter:");
        System.out.println("  enabled: true     ← IDE 会提示这个配置项！");
        System.out.println("  prefix: \"PROD-\"");
        System.out.println("  timeout: 5000");
    }

    static void showAutoConfiguration() {
        System.out.println("自动配置类:");
        System.out.println();
        System.out.println("@Configuration");
        System.out.println("@EnableConfigurationProperties(MyProperties.class)");
        System.out.println("@ConditionalOnProperty(prefix = \"my-starter\", name = \"enabled\",");
        System.out.println("                        havingValue = \"true\", matchIfMissing = true)");
        System.out.println("public class MyAutoConfiguration {");
        System.out.println("    @Bean");
        System.out.println("    @ConditionalOnMissingBean");
        System.out.println("    public MyService myService(MyProperties properties) {");
        System.out.println("        return new MyService(properties.getPrefix(), properties.getTimeout());");
        System.out.println("    }");
        System.out.println("}");
        System.out.println();
        System.out.println("注解说明:");
        System.out.println("  @Configuration                  → 标记为配置类");
        System.out.println("  @EnableConfigurationProperties  → 启用属性绑定");
        System.out.println("  @ConditionalOnProperty          → 可通过配置项开关");
        System.out.println("  @ConditionalOnMissingBean       → 用户自定义了就跳过");
        System.out.println();
        System.out.println("设计意图: 自动配置类就是普通的 @Configuration 类！");
        System.out.println("通过 spring.factories 被自动发现，用户可通过 @Bean 覆盖。");
    }

    static void showSpringFactories() {
        System.out.println("自动配置注册:");
        System.out.println();
        System.out.println("文件: META-INF/spring.factories");
        System.out.println("内容:");
        System.out.println("org.springframework.boot.autoconfigure.EnableAutoConfiguration=\\\\");
        System.out.println("  com.example.my.MyAutoConfiguration");
        System.out.println();
        System.out.println("加载机制:");
        System.out.println("AutoConfigurationImportSelector");
        System.out.println("  → SpringFactoriesLoader.loadFactoryNames(...)");
        System.out.println("  → 扫描所有 jar 中的 META-INF/spring.factories");
        System.out.println("  → 收集 EnableAutoConfiguration 的配置类");
        System.out.println("  → 按 @Order / @AutoConfigureBefore/After 排序");
        System.out.println("  → 用 @Conditional 过滤");
        System.out.println("  → 剩下的就是需要生效的配置");
    }
}
