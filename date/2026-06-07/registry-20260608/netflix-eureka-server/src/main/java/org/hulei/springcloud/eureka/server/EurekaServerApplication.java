package org.hulei.springcloud.eureka.server;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Eureka服务注册中心应用
 * <p>
 * 启用Eureka服务器功能，用于服务注册和发现
 * </p>
 * <p>
 * 主要配置说明：
 * <ul>
 *   <li>eureka.instance.prefer-ip-address=true: 优先使用IP地址注册</li>
 *   <li>eureka.client.register-with-eureka=false: 不向Eureka注册自身（适用于Server）</li>
 *   <li>eureka.client.fetch-registry=false: 不从Eureka获取注册表（适用于Server）</li>
 * </ul>
 * </p>
 * <p>
 * 常见问题：
 * <ul>
 *   <li>Renewals are lesser than threshold: 心跳数量低于阈值，可能是服务实例下线或网络问题</li>
 *   <li>Instances are not being expired: 安全机制，防止误删仍在运行的实例</li>
 * </ul>
 * </p>
 *
 * @author h1123
 * @since 2023/5/5
 */
@Slf4j
@EnableEurekaServer
@SpringBootApplication
public class EurekaServerApplication {

    /**
     * 应用启动入口
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        log.info("Eureka服务注册中心开始启动");
        try {
            SpringApplication.run(EurekaServerApplication.class, args);
            log.info("Eureka服务注册中心启动完成");
        } catch (Exception e) {
            log.error("Eureka服务注册中心启动失败", e);
            System.exit(1);
        }
    }
}
