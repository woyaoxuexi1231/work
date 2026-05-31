package com.redis.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Redis Sentinel & Cluster 深度演示项目 —— 启动入口.
 * <p>
 * 所有演示通过 HTTP 接口触发，浏览器直接访问 http://localhost:8080/ 即可观摩。
 * <p>
 * 切换 Redis 模式：
 * <pre>
 *   java -jar redis-code-0531.jar --spring.profiles.active=sentinel
 *   java -jar redis-code-0531.jar --spring.profiles.active=cluster
 * </pre>
 * 默认使用 standalone 单机模式。
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
