package com.example.springqa.Q18_InterceptorFilter.test;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册 Filter 和 Interceptor——都只对 /q18-test/** 生效。
 */
@Configuration
public class TestWebConfig implements WebMvcConfigurer {

    private final TestInterceptor testInterceptor;

    public TestWebConfig(TestInterceptor testInterceptor) {
        this.testInterceptor = testInterceptor;
    }

    // ── Filter：用 FilterRegistrationBean 限制 URL 模式 ──
    @Bean
    public FilterRegistrationBean<TestFilter> testFilter() {
        FilterRegistrationBean<TestFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new TestFilter());
        bean.addUrlPatterns("/q18-test/*");  // ← 只拦截 /q18-test 路径
        bean.setOrder(1);
        return bean;
    }

    // ── Interceptor：用 addPathPatterns 限制路径 ──
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(testInterceptor)
                .addPathPatterns("/q18-test/**")   // ← 只拦截 /q18-test 路径
                .order(0);
    }
}
