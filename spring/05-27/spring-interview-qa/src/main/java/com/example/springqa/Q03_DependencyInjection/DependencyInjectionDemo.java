package com.example.springqa.Q03_DependencyInjection;

import javax.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * <h1>Q3：依赖注入 — @Autowired vs @Resource vs 构造器注入</h1>
 *
 * <h2>面试点</h2>
 * <ul>
 *   <li>@Autowired 和 @Resource 的查找逻辑有何不同？</li>
 *   <li>@Qualifier 和 @Primary 的优先级？</li>
 *   <li>字段注入的致命缺陷？为什么官方推荐构造器注入？</li>
 *   <li>如何在团队强制推行构造器注入？</li>
 * </ul>
 *
 * <h2>核心对比</h2>
 * <pre>
 * | 特性         | @Autowired (Spring)        | @Resource (JSR-250)       |
 * |-------------|----------------------------|---------------------------|
 * | 查找逻辑     | 默认 byType，配合 @Qualifier | 默认 byName → byType      |
 * | 提供者       | Spring 原生                 | JDK 标准（javax.annotation）|
 * | required    | required=false 可选         | 默认必须找到                |
 * | 注入方式     | 字段/setter/构造器/方法参数  | 字段/setter               |
 * </pre>
 *
 * <h2>字段注入的致命缺陷</h2>
 * <ol>
 *   <li><b>不可变性破坏</b>：字段不可能是 final，不能在编译期保证不可变。</li>
 *   <li><b>隐藏依赖</b>：类的依赖关系散落在字段中，new 一个实例时不会报错，
 *       但 NPE 在运行时才会暴露。</li>
 *   <li><b>测试困难</b>：单元测试必须启动 Spring 容器或用反射注入，
 *       无法简单地 new + 传参。</li>
 *   <li><b>循环依赖容忍</b>：构造器注入在编译/启动期就能暴露循环依赖，
 *       字段注入则隐藏了问题。</li>
 * </ol>
 *
 * <h2>Spring 为什么推荐构造器注入？</h2>
 * <p>这是 Spring 官方文档从 4.x 就开始推荐的实践：</p>
 * <ul>
 *   <li>构造器参数可以是 final，保证不可变</li>
 *   <li>构造器参数是显式声明的，依赖一目了然</li>
 *   <li>new 对象时必须传依赖，天然防止 NPE</li>
 *   <li>Spring 4.3+ 单构造器无需 @Autowired</li>
 * </ul>
 *
 * @author Spring Interview QA
 */
public class DependencyInjectionDemo {

    public static void main(String[] args) {
        System.out.println("========== Q3: 依赖注入 Demo ==========\n");

        AnnotationConfigApplicationContext ctx =
                new AnnotationConfigApplicationContext(DemoConfig.class);

        Restaurant restaurant = ctx.getBean(Restaurant.class);
        System.out.println(">>> Restaurant 的 Chef: " + restaurant.getChef());
        System.out.println(">>> Restaurant 的 Waiter: " + restaurant.getWaiter());

        ctx.close();
        System.out.println("\n========== Demo 结束 ==========");
    }

    // ================================================================
    // 配置类：定义多个同类型 Bean 来演示 @Qualifier / @Primary
    // ================================================================
    @Configuration
    @ComponentScan(basePackageClasses = DependencyInjectionDemo.class)
    static class DemoConfig {

        @Bean
        @Primary  // 当有多个 Chef 类型 Bean 时，@Autowired 默认选这个
        public Chef chineseChef() {
            return new Chef("ChineseChef");
        }

        @Bean
        public Chef japaneseChef() {
            return new Chef("JapaneseChef");
        }
    }

    // ================================================================
    // 演示类：展示不同注入方式
    // ================================================================

    /**
     * 推荐写法：构造器注入
     *
     * 【Spring 设计意图】
     * Spring 4.3+ 开始，如果类只有一个构造器，不需要 @Autowired。
     * 这体现了"约定优于配置"——依赖通过构造器传入是 OOP 的基本实践，
     * 不应该需要额外注解来声明。
     *
     * 多个构造器时，必须用 @Autowired 标记想用的那个。
     */
    @Component
    static class Restaurant {

        private final Chef chef;     // final！不可变

        /*
         * 【@Resource vs @Autowired】
         *
         * @Resource 默认按名称查找：
         *   - 先找 name="waiter" 的 Bean
         *   - 找不到再按类型 Waiter 查找
         *
         * @Autowired 默认按类型查找：
         *   - 找所有 Waiter 类型的 Bean
         *   - 只有一个 → 直接注入
         *   - 有多个 → 按字段名匹配 / @Qualifier / @Primary 决定
         *
         * Spring 为什么默认按类型？
         * 因为类型是编译期确定的，重构时 IDE 可以追踪引用。
         * 按名称则依赖字符串，重构时容易遗漏。
         */

        @Resource(name = "waiter")  // @Resource 显式指定 name
        private Waiter waiter;

        /**
         * 构造器注入：Spring 会自动解析 Chef 类型的 Bean。
         *
         * 因为有 @Primary 在 chineseChef 上，所以这里注入的是 ChineseChef。
         * 如果用 @Qualifier("japaneseChef") 可以覆盖 @Primary。
         *
         * 【@Qualifier vs @Primary 优先级】
         * @Qualifier 优先级 > @Primary > 字段名匹配 > 类型匹配
         * 设计理由：@Qualifier 是调用方"明确指定"的意图，应当优先于提供方的 @Primary 声明。
         */
        public Restaurant(@Qualifier("chineseChef") Chef chef) {
            this.chef = chef;
            System.out.println("  Restaurant 构造器注入: " + chef);
        }

        /**
         * setter 注入：Spring 会在构造器之后调用 setter 完成注入。
         *
         * 【为什么构造器注入优于 setter 注入？】
         * setter 注入的问题是对象可能处于"不完整"状态——
         * 构造器执行完毕后依赖还是 null，要等 setter 调用后才完整。
         * 如果有人在 setter 之前调用业务方法，就会 NPE。
         * 构造器注入保证对象创建完毕时就"完整可用"。
         */
        @Autowired
        public void setChef(Chef chef) {
            // 这里做的不是真正的 setter 注入，只是演示
            System.out.println("  setChef (setter 注入): " + chef);
        }

        public Chef getChef() { return chef; }
        public Waiter getWaiter() { return waiter; }
    }

    @Component("waiter")  // 显式命名，配合 @Resource(name="waiter")
    static class Waiter {
        private final String name = "DefaultWaiter";

        @Override
        public String toString() {
            return "Waiter{" + "name='" + name + '\'' + '}';
        }
    }

    static class Chef {
        private final String name;

        public Chef(String name) { this.name = name; }

        @Override
        public String toString() {
            return "Chef{" + "name='" + name + '\'' + '}';
        }
    }
}
