package com.example.concurrencylab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ConcurrencyLabApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConcurrencyLabApplication.class, args);
    }
}
