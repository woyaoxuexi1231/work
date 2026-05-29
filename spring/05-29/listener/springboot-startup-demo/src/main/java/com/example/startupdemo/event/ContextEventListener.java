package com.example.startupdemo.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 方案三：@EventListener 监听 Spring 事件
 *
 * Spring 在启动过程中会发布一系列事件，监听其中"最后的"事件即可实现启动后执行。
 * 两个关键事件：
 *   1. ContextRefreshedEvent   — ApplicationContext 被初始化或刷新时触发
 *   2. ApplicationReadyEvent   — Spring Boot 应用真正"就绪"时触发（内置 web server 已启动）
 *
 * 优势：
 *   - 不依赖 Spring Boot，普通 Spring 项目也可用（监听 ContextRefreshedEvent）
 *   - 可同时监听多种事件，粒度细：知道"容器刷新了" vs "应用就绪了"
 *   - 多个监听器之间可以用 @Order 排序
 *   - 与 Runner 方案互不冲突，可以组合使用
 *
 * 劣势：
 *   - ApplicationReadyEvent 可能触发多次（如果 context 被刷新），需要自行幂等
 *   - 事件机制是异步广播，如果监听器抛出异常且不 catch，会影响整个容器启动
 *   - 相比 Runner，语义上稍微间接 — "监听事件" vs "启动后运行"
 */
@Component
public class ContextEventListener {

    private static final Logger log = LoggerFactory.getLogger(ContextEventListener.class);

    @EventListener
    public void onContextRefreshed(ContextRefreshedEvent event) {
        log.info("========== [@EventListener / ContextRefreshedEvent] 容器已刷新 ==========");
    }

    @EventListener
    public void onApplicationReady(ApplicationReadyEvent event) {
        log.info("========== [@EventListener / ApplicationReadyEvent] 应用已就绪（内嵌 Web 服务器已启动） ==========");
    }
}
