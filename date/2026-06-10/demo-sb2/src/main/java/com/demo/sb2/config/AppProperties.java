package com.demo.sb2.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 演示 Spring Boot 2 的 @ConfigurationProperties 配置绑定
 *
 * SB2 增强：独立于 @Value 的 API，支持复杂嵌套结构，提供属性来源追踪
 * 对比 SB3：SB3 使用 @ConstructorBinding + 不可变对象（JDK 17 record）
 */
@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String name;
    private String version;
    private List<String> features;
    private Database database = new Database();

    @Data
    public static class Database {
        private String host;
        private int port;
        private String name;
    }
}
public class AppProperties {
    
}
