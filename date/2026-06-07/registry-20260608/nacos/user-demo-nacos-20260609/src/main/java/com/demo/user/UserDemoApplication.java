package com.demo.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@EnableDiscoveryClient
@RestController
public class UserDemoApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(UserDemoApplication.class, args);
        System.out.println("\n========================================");
        System.out.println("  User Demo Service Started [Nacos]");
        System.out.println("  Service: user-demo-nacos-20260609");
        System.out.println("  Port: 18081");
        System.out.println("  Nacos: 192.168.3.100:8848");
        System.out.println("========================================\n");
    }
    
    @GetMapping("/")
    public String index() {
        return "User Demo Service - Nacos";
    }
    
    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}