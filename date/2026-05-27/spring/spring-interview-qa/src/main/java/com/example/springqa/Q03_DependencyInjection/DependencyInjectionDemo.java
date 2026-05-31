package com.example.springqa.Q03_DependencyInjection;

import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DependencyInjectionDemo {

    private final ApplicationContext ctx;

    public DependencyInjectionDemo(ApplicationContext ctx) { this.ctx = ctx; }

    @GetMapping("/q03")
    public String runDemo() {
        Q03Restaurant r = ctx.getBean(Q03Restaurant.class);
        StringBuilder sb = new StringBuilder();
        sb.append("=== Q03: 依赖注入 ===\n\n");
        sb.append("构造器注入 + @Qualifier: ").append(r.getChef()).append("\n");
        sb.append("@Resource byName:        ").append(r.getWaiter()).append("\n\n");
        sb.append("@Autowired 默认 byType; @Resource 默认 byName\n");
        sb.append("@Qualifier > @Primary > 字段名匹配 > 类型匹配\n");
        sb.append("构造器注入: 依赖可 final、防 NPE、一目了然\n\n");
        sb.append("━━━ 完整分析 → /q03.html ━━━\n");
        return sb.toString();
    }
}
