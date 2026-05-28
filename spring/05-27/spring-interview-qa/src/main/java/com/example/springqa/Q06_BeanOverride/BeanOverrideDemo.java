package com.example.springqa.Q06_BeanOverride;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * <h1>Q6：Bean 覆盖与冲突 — 同名 Bean 的战争</h1>
 *
 * <h2>核心要点</h2>
 * <ul>
 *   <li>Spring Boot 2.1+ 默认<b>不允许</b> Bean 覆盖</li>
 *   <li>spring.main.allow-bean-definition-overriding=true 可启用覆盖</li>
 *   <li>Spring Framework 原生默认允许覆盖</li>
 * </ul>
 *
 * <h2>Spring 为什么改变默认值？</h2>
 * <p>Spring Boot 1.x 默认允许覆盖 → 大量"静默覆盖"的 bug。
 * 改为默认禁止是 fail-fast 原则——启动时报错比生产环境默默出错好。</p>
 *
 * <h2>多模块冲突排查</h2>
 * <ol>
 *   <li>看错误日志：哪个 Bean 名称冲突</li>
 *   <li>@Qualifier 或 @Primary 显式指定</li>
 *   <li>@ConditionalOnMissingBean 宽松注册</li>
 *   <li>--debug 启动查看 AUTO-CONFIGURATION REPORT</li>
 * </ol>
 */
@Component
public class BeanOverrideDemo {

    private final ApplicationContext ctx;

    public BeanOverrideDemo(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Q06: Bean 覆盖与冲突 ===\n\n");

        sb.append("本项目 24 个 Demo 包各自定义了 dataSource / transactionManager 等 Bean，\n");
        sb.append("通过以下两层机制解决冲突：\n\n");

        sb.append("1. application.yml 中启用覆盖（兜底）：\n");
        sb.append("   spring.main.allow-bean-definition-overriding: true\n\n");

        sb.append("2. 每个包用独立 Bean 名称（治本）：\n");
        sb.append("   @Bean(name = \"q12_dataSource\")\n");
        sb.append("   @Bean(name = \"q13_dataSource\")\n\n");

        sb.append("【为什么 Spring Boot 默认禁止覆盖？】\n");
        sb.append("Spring Boot 1.x 默认允许 → 引入依赖不小心覆盖了关键 Bean →\n");
        sb.append("很难排查。2.1+ 改为默认禁止是 fail-fast 原则。\n\n");

        sb.append("【Spring Framework vs Spring Boot 的区别】\n");
        sb.append("- Spring Framework: 默认允许覆盖（后注册覆盖先注册）\n");
        sb.append("- Spring Boot: 默认禁止覆盖（加了一层安全锁）\n\n");

        sb.append("【当前容器中的 Bean 数量】\n");
        sb.append("Bean 定义总数: ").append(ctx.getBeanDefinitionCount()).append("\n");

        return sb.toString();
    }
}
