package com.demo.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@EnableDiscoveryClient
@RestController
public class ProductDemoApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(ProductDemoApplication.class, args);
        System.out.println("\n========================================");
        System.out.println("  Product Demo Service Started [Nacos]");
        System.out.println("  Service: product-demo-nacos-20260609");
        System.out.println("  Port: 18083");
        System.out.println("  Nacos: 192.168.3.100:8848");
        System.out.println("========================================\n");
    }
    
    @GetMapping("/")
    public String index() {
        return "Product Demo Service - Nacos";
    }
    
    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}