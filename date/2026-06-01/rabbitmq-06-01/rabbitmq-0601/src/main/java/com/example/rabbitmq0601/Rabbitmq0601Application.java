package com.example.rabbitmq0601;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * RabbitMQ 集群/镜像队列/仲裁队列 实战演练项目
 *
 * 7 个面试题分别对应 q1~q7 包，每个包有独立的 Controller 和 .md 演练文档。
 * 3 个 Docker 脚本在 scripts/ 目录下，用于部署集群环境。
 */
@SpringBootApplication
public class Rabbitmq0601Application {

    public static void main(String[] args) {
        SpringApplication.run(Rabbitmq0601Application.class, args);
    }
}
