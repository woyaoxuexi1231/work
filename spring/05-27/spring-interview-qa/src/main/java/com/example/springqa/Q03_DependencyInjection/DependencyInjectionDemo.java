package com.example.springqa.Q03_DependencyInjection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * <h1>Q3：依赖注入 — @Autowired vs @Resource vs 构造器注入</h1>
 *
 * <h2>核心对比</h2>
 * <pre>
 * @Autowired (Spring)  — 默认 byType，配合 @Qualifier 精确匹配
 * @Resource  (JSR-250) — 默认 byName → byType
 * </pre>
 *
 * <h2>字段注入的致命缺陷</h2>
 * <ol>
 *   <li>不可变性破坏 — 字段不能是 final</li>
 *   <li>隐藏依赖 — new 时不报错，运行时 NPE</li>
 *   <li>测试困难 — 必须启动容器或用反射</li>
 *   <li>循环依赖容忍 — 隐藏了设计问题</li>
 * </ol>
 *
 * <h2>@Qualifier vs @Primary 优先级</h2>
 * <p>@Qualifier > @Primary > 字段名匹配 > 类型匹配</p>
 */
@Component
public class DependencyInjectionDemo {

    private final ApplicationContext ctx;

    public DependencyInjectionDemo(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Q03: 依赖注入 ===\n\n");

        Restaurant restaurant = ctx.getBean(Restaurant.class);

        sb.append("Restaurant 的 Chef (构造器注入 + @Qualifier): ")
                .append(restaurant.getChef()).append("\n");
        sb.append("Restaurant 的 Waiter (@Resource byName): ")
                .append(restaurant.getWaiter()).append("\n\n");

        sb.append("【对比】\n");
        sb.append("@Autowired 默认 byType → 有多个同类型时用 @Primary/@Qualifier\n");
        sb.append("@Resource  默认 byName → 先找 name=\"waiter\" 的 Bean\n\n");

        sb.append("【为什么推荐构造器注入？】\n");
        sb.append("1. 依赖可以是 final → 不可变\n");
        sb.append("2. 依赖显式声明在构造器参数中 → 一目了然\n");
        sb.append("3. new 时必须传依赖 → 天然防 NPE\n");
        sb.append("4. Spring 4.3+ 单构造器无需 @Autowired\n\n");

        sb.append("【@Qualifier vs @Primary】\n");
        sb.append("@Qualifier 优先级 > @Primary，因为 @Qualifier 是调用方\"明确指定\"的意图，\n");
        sb.append("应当优先于提供方的 @Primary 声明。\n");

        return sb.toString();
    }

    // ================================================================
    @Configuration
    static class DemoConfig {

        @Bean @Primary
        public Chef q03_chineseChef() {
            return new Chef("ChineseChef (@Primary)");
        }

        @Bean
        public Chef q03_japaneseChef() {
            return new Chef("JapaneseChef");
        }

        @Bean(name = "waiter")
        public Waiter q03_waiter() {
            return new Waiter("DefaultWaiter");
        }
    }

    @Component
    static class Restaurant {

        private final Chef chef;

        @Resource(name = "waiter")
        private Waiter waiter;

        public Restaurant(@Qualifier("q03_chineseChef") Chef chef) {
            this.chef = chef;
            System.out.println("  Restaurant 构造器注入: " + chef);
        }

        public Chef getChef() { return chef; }
        public Waiter getWaiter() { return waiter; }
    }

    static class Chef {
        private final String name;
        public Chef(String name) { this.name = name; }
        @Override public String toString() { return "Chef{" + name + "}"; }
    }

    static class Waiter {
        private final String name;
        public Waiter(String name) { this.name = name; }
        @Override public String toString() { return "Waiter{" + name + "}"; }
    }
}
