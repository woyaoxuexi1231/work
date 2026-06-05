package com.mlm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MLM 动漫平台 — Spring Boot 应用程序入口
 * <p>
 * 【平台概述】
     * 一个基于 Pipeline 状态机驱动的 AI 动漫生成平台。
     * 核心管线流程：剧本创作(2) → 剧本审核(3) → 拆分镜(4) → AI成片(5) → 终审(6) → 完成(7)。
 * <p>
 * 【技术栈】
 * <ul>
 *   <li>框架：Spring Boot 2.7.18</li>
 *   <li>ORM：MyBatis-Plus 3.5.9</li>
 *   <li>消息队列：RabbitMQ（已配置，待使用）</li>
 *   <li>对象存储：MinIO</li>
 *   <li>本地缓存：Caffeine</li>
 *   <li>数据库：MySQL</li>
 * </ul>
 * <p>
 * 【模块说明】
 * <ul>
 *   <li>pipeline — Pipeline 状态机核心（Engine、Handler、StateMachine）</li>
 *   <li>model — AI 模型适配层（OpenAI、Stable Diffusion、可灵 Kling）</li>
 *   <li>project — 项目管理</li>
 *   <li>episode — 剧集管理</li>
 *   <li>resource — 资源管理（MinIO 文件存储）</li>
 *   <li>notification — 审核消息通知</li>
 * </ul>
 *
 * @author mlm
 */
@SpringBootApplication
@EnableScheduling
public class Application {

    /**
     * 启动 MLM 动漫平台
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
