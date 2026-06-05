package com.riskdatahub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置 — CORS 跨域设置。
 * <p>
 * 允许所有来源和方法的跨域请求（开发环境策略），
 * 生产环境应收紧 allowedOriginPatterns。
 * </p>
 *
 * @author risk-data-hub
 */
@Configuration
public class WebConfig {

    /**
     * 注册 CORS 配置：允许来自任何来源的跨域请求。
     *
     * @return WebMvcConfigurer 实例
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOriginPatterns("*")
                        .allowedMethods("*")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }
}
