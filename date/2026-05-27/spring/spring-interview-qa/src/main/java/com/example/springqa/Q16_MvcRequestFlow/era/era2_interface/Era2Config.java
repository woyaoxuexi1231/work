package com.example.springqa.Q16_MvcRequestFlow.era.era2_interface;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;

import java.util.Properties;

@Configuration
public class Era2Config {

    @Bean
    public Era2ControllerInterface era2Controller() {
        return new Era2ControllerInterface();
    }

    @Bean
    public SimpleUrlHandlerMapping era2HandlerMapping(Era2ControllerInterface controller) {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        Properties urls = new Properties();
        urls.setProperty("/era2/user", "era2Controller");
        mapping.setMappings(urls);
        mapping.setOrder(0);
        return mapping;
    }
}
