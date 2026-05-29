package com.example.startupdemo.runner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 方案一：ApplicationRunner
 *
 * Spring Boot 提供的最正统的"启动后执行"方案。
 * run() 会在 SpringApplication.run() 中所有 Bean 初始化完毕、
 * ApplicationContext 刷新完成之后被调用。
 *
 * 优势：
 *   - 官方推荐，专为此场景设计
 *   - 可通过 ApplicationArguments 获得完整的启动参数（含选项参数 --key=val）
 *   - 可以定义多个 @Order 或 Ordered，控制执行顺序
 *   - 与 CommandLineRunner 共用同一执行时机
 *
 * 劣势：
 *   - 仅适用于 Spring Boot 应用，不能用在普通 Spring 项目中
 *   - 如果同时存在 CommandLineRunner，两者执行次序需要通过 Ordered 协调
 */
@Component
public class AppStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AppStartupRunner.class);

    @Override
    public void run(ApplicationArguments args) {
        log.info("========== [ApplicationRunner] 应用已启动 ==========");
        log.info("非选项参数: {}", args.getNonOptionArgs());
        log.info("选项参数:   {}", args.getOptionNames());
    }
}
