package com.mlm.web.config;

import com.mlm.common.enums.ModelType;
import com.mlm.model.config.ModelConfigEntity;
import com.mlm.model.mapper.ModelConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 数据初始化器 — 首次启动时插入演示用的模型配置
 * <p>
 * 如果 model_config 表为空，则插入 OpenAI、Stable Diffusion、Kling 三套演示配置。
 * 已有数据时跳过，确保重启不重复插入。
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private final ModelConfigMapper configMapper;

    public DataInitializer(ModelConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    @Override
    public void run(String... args) {
        Long count = configMapper.selectCount(null);
        if (count > 0) {
            log.info("模型配置已存在（{} 条），跳过初始化", count);
            return;
        }

        log.info("初始化演示模型配置...");

        // OpenAI — 文生文（剧本/分镜）
        ModelConfigEntity openai = new ModelConfigEntity();
        openai.setVendor("openai");
        openai.setModelType(ModelType.TEXT_TO_TEXT);
        openai.setApiEndpoint("https://api.openai.com/v1/chat/completions");
        openai.setApiKey("sk-demo-key");
        openai.setPollInterval(5);
        openai.setMaxPollCount(30);
        openai.setMaxRetries(3);
        configMapper.insert(openai);

        // Stable Diffusion — 文生图
        ModelConfigEntity sd = new ModelConfigEntity();
        sd.setVendor("stable_diffusion");
        sd.setModelType(ModelType.TEXT_TO_IMAGE);
        sd.setApiEndpoint("https://api.stability.ai/v1/generation");
        sd.setApiKey("sk-demo-key");
        sd.setPollInterval(10);
        sd.setMaxPollCount(60);
        sd.setMaxRetries(3);
        configMapper.insert(sd);

        // 可灵 Kling — 图生视频
        ModelConfigEntity kling = new ModelConfigEntity();
        kling.setVendor("kling");
        kling.setModelType(ModelType.IMAGE_TO_VIDEO);
        kling.setApiEndpoint("https://api.klingai.com/v1/videos");
        kling.setApiKey("sk-demo-key");
        kling.setPollInterval(30);
        kling.setMaxPollCount(120);
        kling.setMaxRetries(2);
        configMapper.insert(kling);

        log.info("已初始化 {} 条模型配置", configMapper.selectCount(null));
    }
}
