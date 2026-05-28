package com.example.springqa.Q03_DependencyInjection;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class DependencyInjectionDemo {

    private final ApplicationContext ctx;

    public DependencyInjectionDemo(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Q03: 依赖注入 ===\n\n");

        Q03Restaurant r = ctx.getBean(Q03Restaurant.class);
        sb.append("Chef (构造器注入+@Qualifier): ").append(r.getChef()).append("\n");
        sb.append("Waiter (@Resource byName): ").append(r.getWaiter()).append("\n\n");

        sb.append("【对比】\n");
        sb.append("@Autowired 默认 byType → 多同类型用 @Primary/@Qualifier\n");
        sb.append("@Resource  默认 byName → 先找 name 再找 type\n\n");
        sb.append("【为什么推荐构造器注入？】\n");
        sb.append("1. 依赖可 final → 不可变\n");
        sb.append("2. 依赖显式声明 → 一目了然\n");
        sb.append("3. new 必须传依赖 → 防 NPE\n");
        sb.append("4. @Qualifier > @Primary（调用方明确意图优先于提供方声明）\n");

        return sb.toString();
    }
}
