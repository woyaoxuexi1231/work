package com.example.dubbo.provider;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Dubbo 服务提供者启动类。
 *
 * =====================================================================
 * 【面试核心①：Dubbo 服务暴露流程 —— 为什么先启动端口再注册？】
 * =====================================================================
 *
 * Dubbo 服务暴露 7 步：
 *
 *   Step 1  Spring 容器扫描到 @DubboService 注解的 Bean
 *   Step 2  Dubbo 读取配置（协议、端口、注册中心、序列化方式）
 *   Step 3  启动本地 Netty Server，监听 dubbo 协议端口（如 20880）
 *   Step 4  把「接口全限定名 → ip:port」写入注册中心（Nacos/ZK）
 *   Step 5  注册中心通知所有订阅了该接口的消费者
 *   Step 6  消费者拉取到新的提供者地址，建立连接
 *   Step 7  消费者发起 RPC → Netty Server 处理 → 返回结果
 *
 * ════════════════════════════════════════════════════════════════════
 *  ❓ 为什么不能"先注册再开端口"？
 * ════════════════════════════════════════════════════════════════════
 *
 *  如果反过来，会出现风险窗口：
 *
 *   T1  提供者往 Nacos 注册成功 ✅
 *   T2  Nacos 推送变更给消费者
 *   T3  消费者收到新地址，发起 RPC 调用
 *   T4  ❌ 提供者 Netty Server 还没启动 → Connection Refused!
 *
 *  T1-T4 这个窗口里，消费者以为服务可用，实际端口还没开。
 *
 *  Dubbo 的解决方案是「延迟暴露」：
 *    - 等 Spring 容器完全就绪（Netty Server 已启动）后，再向注册中心注册
 *    - 配置: dubbo.provider.delay = -1  （-1 表示容器就绪后暴露）
 *    - 配置: dubbo.provider.delay = 5000（延迟 5 秒暴露，等预热）
 *
 *  这样消费者拿到地址时，端口一定是通的 ✅
 *
 * ════════════════════════════════════════════════════════════════════
 *  ❓ 延伸：Nacos 注册后瞬间挂掉，消费者能感知吗？
 * ════════════════════════════════════════════════════════════════════
 *
 *  答：多层兜底（详见 ConsumerApplication 注释）：
 *
 *    ① Nacos 心跳（5s 一次，30s 无心跳踢出）
 *    ② 消费者本地缓存（注册中心挂了也不影响已缓存的服务列表）
 *    ③ Dubbo 集群容错（Failover 自动切到其他节点）
 *    ④ Mock 降级（所有节点都不可用时返回兜底数据）
 *    ⑤ Sentinel 熔断限流
 */
@SpringBootApplication
@EnableDubbo
public class ProviderApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProviderApplication.class, args);
        System.out.println("\n============================================");
        System.out.println("  Dubbo Provider 启动成功！端口 20880");
        System.out.println("  等待消费者调用...");
        System.out.println("============================================\n");
    }
}
