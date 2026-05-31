package com.example.springqa.Q03_DependencyInjection;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class Q03Config {

    @Bean @Primary
    public Q03Chef q03_chineseChef() {
        return new Q03Chef("ChineseChef (@Primary)");
    }

    @Bean
    public Q03Chef q03_japaneseChef() {
        return new Q03Chef("JapaneseChef");
    }

    @Bean(name = "waiter")
    public Q03Waiter q03_waiter() {
        return new Q03Waiter("DefaultWaiter");
    }
}
