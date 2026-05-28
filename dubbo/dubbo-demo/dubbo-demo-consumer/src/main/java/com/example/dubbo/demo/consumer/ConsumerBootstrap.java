package com.example.dubbo.demo.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * <h3>Dubbo Consumer 启动引导类</h3>
 *
 * <p>
 * 本类是 Consumer 进程的入口。启动后会发生以下 Dubbo 核心流程：
 * </p>
 *
 * <h4>Consumer 启动时序（对应 §1 核心概念中的服务引用流程）</h4>
 * <pre>
 * 1. SpringApplication.run() 启动 Spring 容器
 *        │
 * 2. @EnableDubbo 触发 Dubbo 初始化
 *        │
 * 3. 扫描 @DubboReference → 创建 ReferenceConfig Bean
 *        │
 * 4. 连接 Nacos → 订阅 UserService / OrderService 的 Provider 列表
 *    （Nacos 通过 UDP 推送 + 定时轮询实现服务列表更新）
 *        │
 * 5. 创建动态代理对象 → 注入到 @DubboReference 字段
 *        │
 * 6. 与 Provider 建立 TCP 长连接（连接池）
 *        │
 * 7. Consumer 就绪，等待 HTTP 请求触发 RPC 调用
 * </pre>
 *
 * <p><b>注意：</b>
 * 由于设置了 {@code check = false}，即使 Provider 尚未启动，
 * Consumer 也能正常启动（只是调用时会失败）。
 * </p>
 *
 * @author Dubbo Demo Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableDubbo(scanBasePackages = "com.example.dubbo.demo.consumer")
public class ConsumerBootstrap {

    private static final Logger log = LoggerFactory.getLogger(ConsumerBootstrap.class);

    /**
     * Consumer 入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        log.info("========================================");
        log.info("  Dubbo Consumer 启动中...");
        log.info("  引用服务: UserService, OrderService");
        log.info("  注册中心: nacos://192.168.3.100:8848");
        log.info("  HTTP 端口: 8090 (Spring MVC)");
        log.info("========================================");

        SpringApplication.run(ConsumerBootstrap.class, args);

        log.info("========================================");
        log.info("  Dubbo Consumer 启动完成！");
        log.info("  HTTP API 就绪: http://localhost:8090/api/users");
        log.info("  测试命令: curl http://localhost:8090/api/users/1");
        log.info("========================================");
    }
}
