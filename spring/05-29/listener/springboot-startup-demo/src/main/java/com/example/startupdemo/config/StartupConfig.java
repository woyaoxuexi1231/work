package com.example.startupdemo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * 方案七（补充）：@Bean 的 initMethod
 *
 * 也可在 @Bean 注解中指定 initMethod，效果与 @PostConstruct 类似。
 *
 * 这里也演示了监听 WebServerInitializedEvent — 一个比 ApplicationReadyEvent
 * 更细粒度的时机：内嵌 Web 服务器启动后立即触发，此时应用尚未完全就绪。
 */
@Configuration
public class StartupConfig {

    private static final Logger log = LoggerFactory.getLogger(StartupConfig.class);

    @Bean(initMethod = "customInit")
    public ConfigBean configBean() {
        return new ConfigBean();
    }

    @EventListener
    public void onWebServerReady(WebServerInitializedEvent event) {
        log.info("========== [WebServerInitializedEvent] Web 服务器已启动，端口: {} ==========",
                event.getWebServer().getPort());
    }

    public static class ConfigBean {

        private static final Logger log = LoggerFactory.getLogger(ConfigBean.class);

        public void customInit() {
            log.info("========== [@Bean(initMethod)] 自定义初始化方法被调用 ==========");
        }
    }
}
