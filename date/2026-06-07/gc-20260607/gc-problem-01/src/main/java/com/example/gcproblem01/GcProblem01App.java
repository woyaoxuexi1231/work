package com.example.gcproblem01;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GcProblem01App {
    public static void main(String[] args) {
        SpringApplication.run(GcProblem01App.class, args);
    }
}
