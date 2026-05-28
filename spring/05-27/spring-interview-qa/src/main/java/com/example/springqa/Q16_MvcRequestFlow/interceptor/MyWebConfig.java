package com.example.springqa.Q16_MvcRequestFlow.interceptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MyWebConfig implements WebMvcConfigurer {
    
    @Autowired
    private TokenInterceptor myInterceptor;  // 注入 @Component 的 Bean
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(myInterceptor)   // 手动注册
                .addPathPatterns("/q16","/q16-post")
                .excludePathPatterns("/login");
    }
}