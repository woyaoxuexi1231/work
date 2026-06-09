package com.example.gcproblem02;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GcProblem02App {
    public static void main(String[] args) {
        SpringApplication.run(GcProblem02App.class, args);
    }
}
