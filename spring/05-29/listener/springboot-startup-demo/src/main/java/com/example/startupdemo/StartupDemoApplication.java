package com.example.startupdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class StartupDemoApplication {

    public static void main(String[] args) {
        args = new String[]{"--spring.profiles.active=dev"};
        SpringApplication.run(StartupDemoApplication.class, args);
    }
}
