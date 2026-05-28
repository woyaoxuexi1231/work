package com.example.dubbo.demo.provider.config;

import org.springframework.context.annotation.Configuration;

/**
 * <h3>Dubbo Provider 配置类</h3>
 *
 * <p>
 * {@code @EnableDubbo} 是 Dubbo Spring Boot Starter 的核心开关。
 * 加上此注解后，Spring Boot 启动时会自动：
 * </p>
 *
 * <ol>
 *   <li><b>扫描 @DubboService</b> — 将所有标注 @DubboService 的 Bean 注册为 Dubbo 服务</li>
 *   <li><b>初始化配置</b> — 读取 {@code application.yml} 中的 {@code dubbo.*} 配置项</li>
 *   <li><b>加载 SPI 扩展</b> — 扫描 {@code META-INF/dubbo/} 下的扩展点文件</li>
 *   <li><b>启动协议端口</b> — 根据配置启动 Netty 服务端（如 20880 端口）</li>
 *   <li><b>注册到注册中心</b> — 将服务地址注册到 Nacos</li>
 * </ol>
 *
 * <h4>配置方式对比（Dubbo 支持的四种方式）</h4>
 * <table border="1">
 *   <tr><th>方式</th><th>示例</th><th>优先级</th></tr>
 *   <tr>
 *     <td>JVM 参数</td>
 *     <td>{@code -Ddubbo.protocol.port=20880}</td>
 *     <td>最高</td>
 *   </tr>
 *   <tr>
 *     <td>YAML 文件</td>
 *     <td>{@code dubbo.protocol.port: 20880}</td>
 *     <td>↓</td>
 *   </tr>
 *   <tr>
 *     <td>注解/API 默认值</td>
 *     <td>{@code @DubboService(port=20880)}</td>
 *     <td>最低</td>
 *   </tr>
 * </table>
 *
 * @author Dubbo Demo Team
 * @version 1.0.0
 * @see EnableDubbo
 */
@Configuration
// @EnableDubbo 已移至 ProviderBootstrap 启动类上，以正确控制 Dubbo 扫描基准包
public class DubboProviderConfig {

    /**
     * 如需 Java API 方式配置，可在此处通过 {@code @Bean} 创建：
     * <ul>
     *   <li>{@link org.apache.dubbo.config.ApplicationConfig} — 应用配置</li>
     *   <li>{@link org.apache.dubbo.config.RegistryConfig} — 注册中心配置</li>
     *   <li>{@link org.apache.dubbo.config.ProtocolConfig} — 协议配置</li>
     *   <li>{@link org.apache.dubbo.config.ProviderConfig} — 提供者默认配置</li>
     * </ul>
     *
     * 在本示例中，这些配置通过 {@code application.yml} 文件完成，
     * 因为 Dubbo Spring Boot Starter 的自动配置已足够满足需求。
     */
}
