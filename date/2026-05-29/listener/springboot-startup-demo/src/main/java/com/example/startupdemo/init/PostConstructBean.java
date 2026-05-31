package com.example.startupdemo.init;

import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 方案四：@PostConstruct
 *
 * JDK 注解，Bean 初始化阶段的回调 — 依赖注入完成后立即执行。
 *
 * 优势：
 *   - 不需要 Spring Boot，任何 Java 环境 + JSR-250 都支持
 *   - 零配置，一个注解即可，写起来最轻量
 *   - 执行早：在依赖注入完成后、Bean 被使用前执行
 *
 * 劣势：
 *   - ❗ 执行时机远早于"应用启动完成"：
 *      @PostConstruct 在 Bean 初始化阶段触发，此时 ApplicationContext 可能还没有 refresh 完，
 *      内嵌 Tomcat/Undertow 也还没启动。如果代码依赖完整的应用运行时环境（如 HTTP 端口已监听
 *      才能调用的第三方服务），此时执行可能会失败。
 *   - 不建议在这里做需要完整应用上下文的初始化工作
 *   - Spring 在某个 Bean 上调用 @PostConstruct 时，其他 Bean 可能还没初始化完毕
 */
@Component
public class PostConstructBean {

    private static final Logger log = LoggerFactory.getLogger(PostConstructBean.class);

    @PostConstruct
    public void init() {
        log.info("========== [@PostConstruct] Bean 初始化完成 ==========");
    }
}
