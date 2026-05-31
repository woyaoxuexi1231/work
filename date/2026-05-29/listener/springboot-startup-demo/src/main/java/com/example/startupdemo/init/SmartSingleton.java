package com.example.startupdemo.init;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * 方案六：SmartInitializingSingleton
 *
 * Spring 4.1+ 提供。在所有非懒加载单例 Bean 都初始化完毕后触发。
 * 时序上：@PostConstruct → InitializingBean → SmartInitializingSingleton
 *
 * 优势：
 *   - 保证所有单例 Bean 都已创建完毕，比 @PostConstruct / InitializingBean 更晚
 *   - Spring 原生（非 Spring Boot），普通 Spring 项目也可用
 *   - 适合"所有 Bean 都就绪后才能做的事情"
 *
 * 劣势：
 *   - 仍然比 ApplicationReadyEvent / Runner 早 — Web 服务器此时尚未启动
 *   - 只能用于单例 Bean
 *   - 接口命名不太直观，团队新人可能不理解其语义
 */
@Component
public class SmartSingleton implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(SmartSingleton.class);

    @Override
    public void afterSingletonsInstantiated() {
        log.info("========== [SmartInitializingSingleton] 所有单例 Bean 已实例化 ==========");
    }
}
