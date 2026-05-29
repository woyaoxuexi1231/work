package com.example.springqa.Q16_MvcRequestFlow.era.era1_servlet;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Era1Config {
    @Bean
    public ServletRegistrationBean<Era1PureServlet> era1Servlet() {
        ServletRegistrationBean<Era1PureServlet> bean =
                new ServletRegistrationBean<>(new Era1PureServlet(), "/era1/user");
        bean.setLoadOnStartup(1);
        return bean;
    }
}
