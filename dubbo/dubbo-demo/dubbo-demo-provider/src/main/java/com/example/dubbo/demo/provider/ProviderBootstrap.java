package com.example.dubbo.demo.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * <h3>Dubbo Provider 启动引导类</h3>
 *
 * <p>
 * 本类是 Provider 进程的入口。启动后会发生以下 Dubbo 核心流程：
 * </p>
 *
 * <h4>Provider 启动时序（对应 §1 核心概念中的 Container 启动流程）</h4>
 * <pre>
 * 1. SpringApplication.run() 启动 Spring 容器
 *        │
 * 2. @EnableDubbo 触发 Dubbo 初始化
 *        │
 * 3. 扫描 @DubboService → 创建 ServiceConfig Bean
 *        │
 * 4. 读取 application.yml → 配置 ProtocolConfig, RegistryConfig
 *        │
 * 5. 启动 Netty 服务端（监听 dubbo://0.0.0.0:20880）
 *        │
 * 6. 连接 Nacos → 注册服务到命名空间：
 *    public@dubbo-demo-provider → 服务列表 → UserService / OrderService
 *        │
 * 7. Provider 就绪，等待 Consumer 调用
 * </pre>
 *
 * <p><b>注意：</b>启动前请确保 Nacos 已在 192.168.3.100:8848 运行，
 * 且可使用 nacos/nacos 登录控制台。</p>
 *
 * @author Dubbo Demo Team
 * @version 1.0.0
 */
@SpringBootApplication
public class ProviderBootstrap {

    private static final Logger log = LoggerFactory.getLogger(ProviderBootstrap.class);

    /**
     * Provider 入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        log.info("========================================");
        log.info("  Dubbo Provider 启动中...");
        log.info("  服务: UserService, OrderService");
        log.info("  协议: dubbo://:20880");
        log.info("  注册中心: nacos://192.168.3.100:8848");
        log.info("========================================");

        SpringApplication.run(ProviderBootstrap.class, args);

        log.info("========================================");
        log.info("  Dubbo Provider 启动完成！");
        log.info("  等待 Consumer 调用...");
        log.info("========================================");
    }
}
