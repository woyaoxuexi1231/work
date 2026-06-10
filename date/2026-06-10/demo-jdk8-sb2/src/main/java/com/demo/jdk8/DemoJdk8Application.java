package com.demo.jdk8;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * <h2>JDK 1.8 + Spring Boot 2.7.18 —— 经典写法基准项目</h2>
 *
 * <h3>本项目展示 JDK 8 时代的"经典"编码风格：</h3>
 * <ul>
 *   <li>{@link com.demo.jdk8.dto.OrderDTO} -- 传统 POJO + Lombok（对比 JDK 17 的 Record）</li>
 *   <li>{@link com.demo.jdk8.dto.OrderStatus} -- 传统枚举（对比 JDK 17 的 Sealed Interface）</li>
 *   <li>{@link com.demo.jdk8.controller.OrderController} -- javax 命名空间（对比 jakarta）</li>
 *   <li>{@link com.demo.jdk8.service.OrderService} -- instanceof 强转 + switch 字符串（对比模式匹配）</li>
 *   <li>{@link com.demo.jdk8.client.ExternalApiClient} -- RestTemplate（对比 HttpInterface）</li>
 *   <li>{@link com.demo.jdk8.config.AppConfig} -- 传统 @ConfigurationProperties（对比 Record 绑定）</li>
 * </ul>
 *
 * <h3>版本信息：</h3>
 * <ul>
 *   <li>JDK: 1.8 (2014 年发布)</li>
 *   <li>Spring Boot: 2.7.18 (2.x 终版，2023.11 已停止维护)</li>
 *   <li>命名空间: javax.* (非 jakarta.*)</li>
 *   <li>Servlet API: 4.0 (非 6.0)</li>
 * </ul>
 */
@SpringBootApplication
public class DemoJdk8Application {

    public static void main(String[] args) {
        SpringApplication.run(DemoJdk8Application.class, args);
        System.out.println("================================================");
        System.out.println("  JDK 8 + Spring Boot 2.7 经典项目已启动");
        System.out.println("  接口地址: http://localhost:8081/api/orders");
        System.out.println("  外部API模拟: http://localhost:8081/api/external/slow");
        System.out.println("================================================");
    }
}
