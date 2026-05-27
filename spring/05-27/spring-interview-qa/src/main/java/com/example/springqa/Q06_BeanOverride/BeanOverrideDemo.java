package com.example.springqa.Q06_BeanOverride;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <h1>Q6：Bean 覆盖与冲突 — 同名 Bean 的战争</h1>
 *
 * <h2>面试点</h2>
 * <ul>
 *   <li>Spring Boot 中同名 Bean 默认允许覆盖吗？</li>
 *   <li>spring.main.allow-bean-definition-overriding 参数的作用？</li>
 *   <li>多模块同名 Bean 冲突怎么排错？</li>
 * </ul>
 *
 * <h2>答案</h2>
 *
 * <h3>Spring Boot 默认行为</h3>
 * <p>Spring Boot 2.1+ 默认 <b>不允许</b> Bean 覆盖。
 * 如果两个 Bean 有相同的名称或相同的类型且没有 @Primary，启动时会抛出
 * {@code ConflictingBeanDefinitionException}。</p>
 *
 * <p>可以通过以下方式改变：</p>
 * <pre>
 * # application.yml
 * spring.main.allow-bean-definition-overriding: true
 *
 * # 或启动参数
 * --spring.main.allow-bean-definition-overriding=true
 * </pre>
 *
 * <h3>Spring 为什么改变默认值？</h3>
 * <p>Spring Boot 1.x 默认允许覆盖，但这导致了大量"静默覆盖"的 bug——
 * 开发者引入了一个依赖，不小心覆盖了关键的 Bean，很难排查。
 * 改为默认禁止覆盖是"fail-fast"原则的体现：
 * 与其在生产环境默默出错，不如启动时就明确报错。</p>
 *
 * <h3>Spring 框架 vs Spring Boot 的区别</h3>
 * <p>原始的 Spring Framework（AnnotationConfigApplicationContext）
 * 默认<b>允许</b>覆盖——后注册的同名 Bean 会覆盖先注册的。
 * Spring Boot 在 Spring Framework 之上加了一层"安全锁"，
 * 默认不允许覆盖。</p>
 *
 * <h3>多模块同名 Bean 冲突排查步骤</h3>
 * <ol>
 *   <li>看错误日志：哪个 Bean 名称冲突了</li>
 *   <li>用 {@code @Qualifier} 或 {@code @Primary} 显式指定优先级</li>
 *   <li>用 {@code @ConditionalOnMissingBean} 让其中一个变成"宽松注册"</li>
 *   <li>用 {@code spring.main.allow-bean-definition-overriding=true}（不推荐）</li>
 *   <li>用 {@code --debug} 启动，查看 AUTO-CONFIGURATION REPORT</li>
 * </ol>
 *
 * @author Spring Interview QA
 */
public class BeanOverrideDemo {

    /**
     * 运行本 Demo:
     * - 默认情况（allowOverriding=true）：同一个 Service 接口有两个实现，
     *   Spring Framework 会静默用后面的覆盖前面的。
     * - 如果改为 @SpringBootApplication 启动，会看到报错。
     */
    public static void main(String[] args) {
        System.out.println("========== Q6: Bean 覆盖 Demo ==========\n");

        /*
         * 注意：这里用的是 AnnotationConfigApplicationContext（Spring Framework 原生），
         * 它默认允许 BeanDefinition 覆盖。
         *
         * 如果用 Spring Boot 的 SpringApplication.run()，默认不允许覆盖，
         * 会抛出 ConflictingBeanDefinitionException。
         */

        // ===== 场景一：同名 @Bean =====
        System.out.println("--- 场景一：同名 @Bean ---");
        AnnotationConfigApplicationContext ctx1 =
                new AnnotationConfigApplicationContext(ConflictConfig1.class);
        /*
         * ConflictConfig1 中定义了两个同名的 userService Bean。
         * Spring Framework 默认允许覆盖：后一个覆盖前一个。
         * 控制台会输出一条 INFO 日志：
         * "Overriding bean definition for bean 'userService'..."
         */
        String svc = ctx1.getBean("userService", String.class);
        System.out.println("userService = " + svc);  // 输出 "V2"
        ctx1.close();

        // ===== 场景二：同类型不同名 =====
        System.out.println("\n--- 场景二：同类型多个 Bean ---");
        AnnotationConfigApplicationContext ctx2 =
                new AnnotationConfigApplicationContext(ConflictConfig2.class);
        /*
         * 此时有两个 String 类型的 Bean：name1 和 name2。
         * 用 ctx2.getBean(String.class) 会报 NoUniqueBeanDefinitionException。
         */
        try {
            ctx2.getBean(String.class);
        } catch (Exception e) {
            System.out.println("  ❌ 按类型获取失败: " + e.getClass().getSimpleName());
            System.out.println("  解决方式: @Primary 或 @Qualifier(\"name2\")");
        }
        ctx2.close();

        System.out.println("\n========== Demo 结束 ==========");
    }

    @Configuration
    static class ConflictConfig1 {
        @Bean
        public String userService() {
            return "V1";
        }

        @Bean
        public String userService2() {  // 不同方法名...
            // 但 name="userService"，故意制造冲突
            return "V2";
        }
    }

    @Configuration
    static class ConflictConfig2 {
        @Bean
        public String name1() { return "Bean-1"; }

        @Bean
        public String name2() { return "Bean-2"; }
    }
}
