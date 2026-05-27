package com.example.dubbo.demo.consumer.config;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.context.annotation.Configuration;

/**
 * <h3>Dubbo Consumer 配置类</h3>
 *
 * <p>
 * 与 Provider 端相同，{@code @EnableDubbo} 启动 Dubbo 能力。
 * 在 Consumer 端，它会：
 * </p>
 *
 * <ol>
 *   <li><b>扫描 @DubboReference</b> — 为标注了 @DubboReference 的字段创建 RPC 代理对象</li>
 *   <li><b>连接注册中心</b> — 订阅所需服务的 Provider 地址列表</li>
 *   <li><b>建立连接池</b> — 与 Provider 建立 TCP 长连接（复用）</li>
 * </ol>
 *
 * <h4>Consumer 端 RPC 代理原理</h4>
 * <p>
 * 当你在 Consumer 中写 {@code @DubboReference private UserService userService;}
 * 时，Dubbo 在 Spring 容器初始化阶段通过 <b>动态代理（Javassist / JDK Proxy）</b>
 * 生成一个 UserService 接口的代理实现。后续对 {@code userService.getUserById(1L)}
 * 的调用实际上会：
 * </p>
 *
 * <pre>
 * userService.getUserById(1L)
 *      │
 *      ▼
 * ┌─────────────────────────────────┐
 * │  Proxy (UserService 的动态代理)   │
 * │  ┌───────────────────────────┐  │
 * │  │ 1. 负载均衡 — 选择目标 Provider │  │
 * │  │ 2. 构建 RpcInvocation 对象    │  │
 * │  │ 3. Filter 链（Consumer 端）    │  │
 * │  │ 4. 序列化请求（Hessian2）      │  │
 * │  │ 5. Netty 发送 TCP 请求         │  │
 * │  │ 6. 等待响应 → 反序列化 → 返回   │  │
 * │  └───────────────────────────┘  │
 * └─────────────────────────────────┘
 * </pre>
 *
 * @author Dubbo Demo Team
 * @version 1.0.0
 * @see EnableDubbo
 */
@Configuration
@EnableDubbo
public class DubboConsumerConfig {

    /**
     * 本示例中，Consumer 的 Dubbo 配置通过 {@code application.yml}
     * 和 {@code @DubboReference} 注解共同完成。
     *
     * 如需 Java API 方式配置，可在此注入：
     * {@link org.apache.dubbo.config.ApplicationConfig}、
     * {@link org.apache.dubbo.config.RegistryConfig}、
     * {@link org.apache.dubbo.config.ConsumerConfig} 等。
     */
}
