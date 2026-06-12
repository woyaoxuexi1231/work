package com.demo.sb3.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * SB3 新特性：RestClient 配置
 *
 * RestClient 是 Spring 6.1+ 引入的，替代 RestTemplate 的流式 API
 * 对比 WebClient：RestClient 是同步的（配合虚线程性能极佳），WebClient 是响应式的
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        return builder
                .baseUrl("https://httpbin.org")
                .defaultHeader("User-Agent", "SB3-RestClient-Demo")
                .build();
    }
}
