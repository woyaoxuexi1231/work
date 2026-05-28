package com.example.springqa.Q04_FactoryBean;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Q04Config {

    @Bean
    public Q04MapperFactoryBean<Q04UserMapper> q04_userMapper() {
        return new Q04MapperFactoryBean<>(Q04UserMapper.class);
    }
}
