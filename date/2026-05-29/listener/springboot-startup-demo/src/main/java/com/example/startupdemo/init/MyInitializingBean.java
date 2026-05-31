package com.example.startupdemo.init;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * 方案五：InitializingBean
 *
 * Spring 容器初始化 Bean 时，在属性设置完毕后调用 afterPropertiesSet()。
 *
 * 优势：
 *   - Spring 原生接口，不依赖 Spring Boot / JSR-250
 *   - 语义明确："所有属性已注入，请初始化"
 *   - 执行时机与 @PostConstruct 几乎相同，但 @PostConstruct 先执行
 *
 * 劣势：
 *   - 同 @PostConstruct — 执行太早，Web 容器/应用上下文可能尚未就绪
 *   - 耦合了 Spring API（实现 InitializingBean），不方便单元测试
 *   - 通常建议用 @PostConstruct 替代（更轻量、无框架耦合）
 */
@Component
public class MyInitializingBean implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(MyInitializingBean.class);

    @Override
    public void afterPropertiesSet() {
        log.info("========== [InitializingBean] afterPropertiesSet 被调用 ==========");
    }
}
