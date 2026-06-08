package com.example.java20260608;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication
public class CachePlayApplication {

    public static void main(String[] args) {
        SpringApplication.run(CachePlayApplication.class, args);
    }
}
