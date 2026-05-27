package com.example.springqa.Q21_AutoConfiguration;

/**
 * <h1>Q21：自动装配原理 — spring.factories + 条件注解</h1>
 *
 * <h2>面试点</h2>
 * <ul>
 *   <li>@SpringBootApplication 包含哪三个注解？</li>
 *   <li>@EnableAutoConfiguration 如何通过 spring.factories 加载自动配置类？</li>
 *   <li>条件注解 @ConditionalOnClass / @ConditionalOnMissingBean 的工作原理？</li>
 * </ul>
 *
 * <h2>@SpringBootApplication 的三个注解</h2>
 * <pre>
 * @SpringBootApplication  =  @SpringBootConfiguration
 *                           + @EnableAutoConfiguration
 *                           + @ComponentScan
 *
 * @SpringBootConfiguration  — 本质是 @Configuration
 * @EnableAutoConfiguration  — 触发自动装配（核心）
 * @ComponentScan            — 扫描当前包及子包的组件
 * </pre>
 *
 * <h2>自动装配流程</h2>
 * <pre>
 * @EnableAutoConfiguration
 *   → @Import(AutoConfigurationImportSelector.class)
 *     → selectImports():
 *       1. 加载 META-INF/spring.factories 中的自动配置类列表
 *       2. 用 @Conditional 注解过滤：
 *          - @ConditionalOnClass → 类路径有该类？
 *          - @ConditionalOnMissingBean → 容器中没有该 Bean？
 *          - @ConditionalOnProperty → 配置项为 true？
 *       3. 过滤后的就是需要生效的自动配置
 * </pre>
 *
 * <h2>Spring 为什么这样设计？</h2>
 * <p>自动装配是 Spring Boot 最核心的创新：
 * 引入 starter → 自动配置加载 → 条件注解保证"需要什么有什么"→
 * 用户可通过 @Bean 覆盖自动配置。</p>
 *
 * @author Spring Interview QA
 */
public class AutoConfigurationDemo {

    public static void main(String[] args) {
        System.out.println("========== Q21: 自动装配原理 Demo ==========\n");

        explainSpringBootApplication();
        System.out.println();
        explainAutoConfigurationFlow();
        System.out.println();
        demoConditionalAnnotations();

        System.out.println("\n========== Demo 结束 ==========");
    }

    static void explainSpringBootApplication() {
        System.out.println("@SpringBootApplication 展开:");
        System.out.println();
        System.out.println("@Target(ElementType.TYPE)");
        System.out.println("@Retention(RetentionPolicy.RUNTIME)");
        System.out.println("@SpringBootConfiguration    // ① 本质是 @Configuration");
        System.out.println("@EnableAutoConfiguration    // ② 触发自动装配（核心！）");
        System.out.println("@ComponentScan(             // ③ 组件扫描");
        System.out.println("    excludeFilters = {");
        System.out.println("        @Filter(type = FilterType.CUSTOM,");
        System.out.println("                classes = TypeExcludeFilter.class)");
        System.out.println("    }");
        System.out.println(")");
        System.out.println("public @interface SpringBootApplication { ... }");
        System.out.println();
        System.out.println("设计意图: 这是\"外观模式\"——把三个常用配置合并为一个入口注解。");
        System.out.println("99% 的应用只需要 @SpringBootApplication 就够了。");
    }

    static void explainAutoConfigurationFlow() {
        System.out.println("自动装配加载流程:");
        System.out.println();
        System.out.println("// Step 1: @EnableAutoConfiguration 触发");
        System.out.println("@EnableAutoConfiguration");
        System.out.println("  → @Import(AutoConfigurationImportSelector.class)");
        System.out.println();
        System.out.println("// Step 2: AutoConfigurationImportSelector.selectImports()");
        System.out.println("protected List<String> getCandidateConfigurations(...) {");
        System.out.println("    // 从所有 jar 的 META-INF/spring.factories 中加载");
        System.out.println("    List<String> configurations = SpringFactoriesLoader");
        System.out.println("        .loadFactoryNames(EnableAutoConfiguration.class, classLoader);");
        System.out.println("    return configurations;");
        System.out.println("}");
        System.out.println();
        System.out.println("// Step 3: 条件过滤");
        System.out.println("// spring-boot-autoconfigure 的 spring.factories:");
        System.out.println("// org.springframework.boot.autoconfigure.EnableAutoConfiguration=\\\\");
        System.out.println("//   org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,\\\\");
        System.out.println("//   org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,\\\\");
        System.out.println("//   ...");
        System.out.println();
        System.out.println("// DataSourceAutoConfiguration 头部:");
        System.out.println("@Configuration");
        System.out.println("@ConditionalOnClass({DataSource.class, EmbeddedDatabaseType.class})");
        System.out.println("@ConditionalOnMissingBean(DataSource.class)");
        System.out.println("@EnableConfigurationProperties(DataSourceProperties.class)");
        System.out.println("public class DataSourceAutoConfiguration { ... }");
        System.out.println();
        System.out.println("条件过滤:");
        System.out.println("1. @ConditionalOnClass → 类路径有驱动？有 → 继续");
        System.out.println("2. @ConditionalOnMissingBean → 用户定义了？没 → 自动创建");
    }

    static void demoConditionalAnnotations() {
        System.out.println("条件注解速查:");
        System.out.println();
        System.out.println("@ConditionalOnClass(name = \"com.mysql.cj.jdbc.Driver\")");
        System.out.println("  → Class.forName() 成功 → 配置生效");
        System.out.println("  → ClassNotFoundException → 配置跳过");
        System.out.println("  用途: \"有 MySQL 驱动才配置 MySQL DataSource\"");
        System.out.println();
        System.out.println("@ConditionalOnMissingBean(DataSource.class)");
        System.out.println("  → beanFactory.getBeanNamesForType(DataSource).length == 0 → 生效");
        System.out.println("  用途: \"用户没定义 DataSource 时，自动配置一个\"");
        System.out.println();
        System.out.println("@ConditionalOnProperty(prefix = \"my-starter\", name = \"enabled\",");
        System.out.println("                        havingValue = \"true\", matchIfMissing = true)");
        System.out.println("  → environment.getProperty(\"my-starter.enabled\") == \"true\" → 生效");
        System.out.println("  用途: \"通过配置项开关控制是否启用\"");
        System.out.println();
        System.out.println("@ConditionalOnWebApplication(type = Type.SERVLET)");
        System.out.println("  → 只在 Servlet Web 应用中生效");
        System.out.println();
        System.out.println("设计意图: 自动装配是\"推断式配置\"——框架根据当前环境");
        System.out.println("（类路径、已有Bean、配置项、应用类型）决定加载什么。");
        System.out.println("没有条件注解，自动装配就是\"全量加载\"，会大量启动失败。");
    }
}
