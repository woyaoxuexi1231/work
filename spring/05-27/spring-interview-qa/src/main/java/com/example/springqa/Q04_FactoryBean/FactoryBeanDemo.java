package com.example.springqa.Q04_FactoryBean;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class FactoryBeanDemo {

    private final ApplicationContext ctx;

    public FactoryBeanDemo(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Q04: FactoryBean ===\n\n");

        Q04UserMapper mapper = ctx.getBean("q04_userMapper", Q04UserMapper.class);
        sb.append("getBean(\"q04_userMapper\"): ").append(mapper.getClass().getName()).append("\n");
        sb.append("mapper.findById(1): ").append(mapper.findById(1L)).append("\n\n");

        Object fb = ctx.getBean("&q04_userMapper");
        sb.append("getBean(\"&q04_userMapper\"): ").append(fb.getClass().getName()).append("\n\n");

        sb.append("【FactoryBean vs BeanFactory】\n");
        sb.append("BeanFactory  — Spring IOC 容器顶层接口\n");
        sb.append("FactoryBean  — 一个特殊的 Bean，定制某个 Bean 的创建\n");
        sb.append("记法: FactoryBean 以 Bean 结尾（它是 Bean），\n");
        sb.append("      BeanFactory 以 Factory 结尾（它是工厂）\n");
        sb.append("getObject() 产物能被 AOP 增强 ✅\n");

        return sb.toString();
    }
}
