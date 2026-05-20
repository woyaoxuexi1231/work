package com.mlm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MLM 动漫平台 — Spring Boot 启动类
 * <p>
 * 一个基于 Pipeline 状态机驱动的 AI 动漫生成平台。
 * 核心流程：DRAFT(剧本创作) → REVIEW(审核) → STORYBOARD(拆分镜)
 * → GENERATING(AI成片) → APPROVAL(终审) → COMPLETED(完成)
 * <p>
 * 技术栈：Spring Boot 3.2 + MyBatis-Plus + RabbitMQ + MinIO + Caffeine
 */
@SpringBootApplication
@EnableScheduling
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
