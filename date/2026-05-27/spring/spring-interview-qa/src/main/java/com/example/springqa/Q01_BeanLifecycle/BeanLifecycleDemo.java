package com.example.springqa.Q01_BeanLifecycle;

import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <h1>Q1：Bean 生命周期 — 每个方法在哪个节点执行</h1>
 * <p>完整架构师级回答见 <a href="/q01.html">/q01.html</a></p>
 */
@RestController
public class BeanLifecycleDemo {

    private final ApplicationContext ctx;

    public BeanLifecycleDemo(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    @GetMapping("/q01")
    public String runDemo() {
        Q01MyBean bean = ctx.getBean(Q01MyBean.class);
        StringBuilder sb = new StringBuilder();
        sb.append("=== Q01: Bean 生命周期（实时运行结果） ===\n\n");
        sb.append("当前 Bean name: ").append(bean.getName()).append("\n");
        sb.append("Bean 类: ").append(bean.getClass().getName()).append("\n\n");

        sb.append("【执行顺序 — 查看启动日志验证】\n");
        sb.append("  [0] BeanFactoryPostProcessor  ← 修改 BeanDefinition\n");
        sb.append("  [1] 构造器                     ← 对象诞生\n");
        sb.append("  [2] 属性填充 (@Autowired)       ← 依赖就位\n");
        sb.append("  [3] BeanNameAware             ← 知道自己的名字\n");
        sb.append("  [4] BeanFactoryAware           ← 拿到 BeanFactory\n");
        sb.append("  [5] ApplicationContextAware    ← ★ 通过 BPP 回调\n");
        sb.append("  [6] BPP.before                 ← 初始化前拦截\n");
        sb.append("  [7] @PostConstruct > InitializingBean > init-method\n");
        sb.append("  [8] BPP.after                  ← ★ AOP 代理在此生成\n");
        sb.append("  [9] 就绪 → singletonObjects\n\n");

        sb.append("name 字段被 @PostConstruct 修改为: ").append(bean.getName()).append("\n\n");

        sb.append("━━━ 完整架构分析 → http://localhost:8080/q01.html ━━━\n");
        return sb.toString();
    }
}
