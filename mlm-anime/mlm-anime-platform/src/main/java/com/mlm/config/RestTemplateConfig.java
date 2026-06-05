package com.mlm.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * HTTP 客户端配置 — 统一管理 RestTemplate 实例。
 * <p>
 * 连接超时 5 秒，读取超时 30 秒。所有外部 HTTP 调用（认证网关、AI 厂商 API）
 * 通过依赖注入获取此 Bean。
 * </p>
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }
}
