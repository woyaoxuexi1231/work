package com.example.startupdemo.runner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 方案二：CommandLineRunner
 *
 * 与 ApplicationRunner 共享同一执行时机（context 刷新后、run() 返回前）。
 * 区别仅在于参数模型更简单 — 直接接收原始的 String[] args。
 *
 * 优势：
 *   - 同样官方推荐，签名更简单，适合"只需原始参数"的场景
 *   - 可通过 @Order 控制多个 Runner 之间的顺序
 *   - 执行时机与 ApplicationRunner 完全相同
 *
 * 劣势：
 *   - 参数是平铺的 String[]，没有 --key=value 的语义解析（需要自己 parse）
 *   - 同 ApplicationRunner，仅限 Spring Boot
 */
@Component
public class CmdLineRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CmdLineRunner.class);

    @Override
    public void run(String... args) {
        log.info("========== [CommandLineRunner] 应用已启动，参数: {} ==========", (Object) args);
    }
}
