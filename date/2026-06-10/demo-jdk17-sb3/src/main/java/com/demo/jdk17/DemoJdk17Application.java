package com.demo.jdk17;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * <h2>JDK 17 + Spring Boot 3.4 —— 现代 Java 特性项目</h2>
 *
 * <h3>相比 JDK 8 项目的核心变化（每个文件都有详细对比注释）：</h3>
 * <ul>
 *   <li>{@link com.demo.jdk17.dto.OrderDTO} -- Record 替代 POJO + Lombok（一行搞定 DTO）</li>
 *   <li>{@link com.demo.jdk17.dto.OrderStatus} -- Sealed Interface 替代枚举（每种状态携带不同数据）</li>
 *   <li>{@link com.demo.jdk17.controller.OrderController} -- jakarta 命名空间（javax → jakarta）</li>
 *   <li>{@link com.demo.jdk17.service.OrderService} -- 模式匹配 + Switch 表达式（告别 instanceof 强转）</li>
 *   <li>{@link com.demo.jdk17.client.ExternalApiClient} -- HttpInterface 声明式客户端（替代 RestTemplate）</li>
 *   <li>{@link com.demo.jdk17.config.AppConfig} -- Record 做配置绑定</li>
 * </ul>
 *
 * <h3>版本信息：</h3>
 * <ul>
 *   <li>JDK: 17 (LTS, 2021.9 发布) —— 现代 Java 集大成者</li>
 *   <li>Spring Boot: 3.4.1</li>
 *   <li>命名空间: jakarta.* (从 javax.* 迁移到 Jakarta EE 9+)</li>
 *   <li>Servlet API: 6.0</li>
 * </ul>
 *
 * <h3>JDK 17 关键特性清单：</h3>
 * <ul>
 *   <li>Records (JDK 16 正式) —— 不可变数据载体</li>
 *   <li>Sealed Classes (JDK 17 正式) —— 密封类/接口，控制继承</li>
 *   <li>Pattern Matching for instanceof (JDK 16 正式) —— 类型判断+绑定一步完成</li>
 *   <li>Switch Expressions (JDK 14 正式) —— 箭头语法，可返回值</li>
 *   <li>Text Blocks (JDK 15 正式) —— 多行字符串</li>
 *   <li>Useful NPE messages (JDK 14) —— NPE 精准指出哪个变量为 null</li>
 * </ul>
 */
@SpringBootApplication
public class DemoJdk17Application {

    public static void main(String[] args) {
        SpringApplication.run(DemoJdk17Application.class, args);
        System.out.println("================================================");
        System.out.println("  JDK 17 + Spring Boot 3.4 现代项目已启动");
        System.out.println("  接口地址: http://localhost:8082/api/orders");
        System.out.println("  外部API模拟: http://localhost:8082/api/external/slow");
        System.out.println("================================================");
    }
}
