package com.mlm.common.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * HTTP 客户端配置 — 统一管理 RestTemplate 实例
 * <p>
 * 使用 Spring 推荐的 {@link RestTemplateBuilder} 构造，配置连接超时和
 * 读取超时时间。所有需要调用外部 HTTP 服务的组件（认证网关、AI 厂商 API）
 * 通过依赖注入获取此 Bean，避免直接 {@code new RestTemplate()}。
 * <p>
 * 超时说明：
 * <ul>
 *   <li>connectTimeout: 5 秒 — 建立 TCP 连接的超时</li>
 *   <li>readTimeout: 30 秒 — 等待服务器返回数据的超时</li>
 * </ul>
 *
 * @author mlm
 */
@Configuration
public class RestTemplateConfig {

    /**
     * 创建应用共享的 RestTemplate 实例
     * <p>
     * 配置 5 秒连接超时和 30 秒读取超时，适用于认证网关调用和
     * AI 厂商 API 调用场景。
     *
     * @param builder Spring 提供的构建器
     * @return 配置完成的 RestTemplate
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }
}
