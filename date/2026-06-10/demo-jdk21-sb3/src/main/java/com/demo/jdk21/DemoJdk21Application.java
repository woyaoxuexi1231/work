package com.demo.jdk21;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * <h3>JDK 21 + Spring Boot 3.4 对比演示项目</h3>
 *
 * <p>核心特性：虚线程（Virtual Threads）—— 彻底改变 Java 并发编程方式</p>
 *
 * <pre>
 * 三版本对比路线：
 *   demo-jdk8-sb2   → CompletableFuture + 平台线程池（传统写法）
 *   demo-jdk17-sb3  → CompletableFuture + var（略有改善）
 *   demo-jdk21-sb3  → 虚线程 + StructuredTaskScope（革命性变化）← 你在这里
 * </pre>
 */
@SpringBootApplication
public class DemoJdk21Application {

    public static void main(String[] args) {
        SpringApplication.run(DemoJdk21Application.class, args);
    }
}
