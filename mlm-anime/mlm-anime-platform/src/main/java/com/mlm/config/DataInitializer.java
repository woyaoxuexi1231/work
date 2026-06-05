package com.mlm.config;

import com.mlm.common.enums.ModelType;
import com.mlm.model.config.ModelConfigEntity;
import com.mlm.model.mapper.ModelConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 数据初始化器 — 首次启动时初始化必要的模型配置数据。
 * <p>
 * 检查 model_config 表是否为空，为空则插入各 AI 厂商的演示配置。
 * 通过检查表记录数确保幂等性。
 * </p>
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
        initModelConfigs();
    }

    private void initModelConfigs() {
        if (configMapper.selectCount(null) > 0) {
            log.info("模型配置已存在，跳过初始化");
            return;
        }

        log.info("初始化演示模型配置...");

        ModelConfigEntity openai = new ModelConfigEntity();
        openai.setVendor("openai");
        openai.setModelType(ModelType.TEXT_TO_TEXT);
        openai.setApiEndpoint("https://api.openai.com/v1/chat/completions");
        openai.setApiKey("sk-demo-key");
        openai.setPollInterval(5);
        openai.setMaxPollCount(30);
        openai.setMaxRetries(3);
        configMapper.insert(openai);

        ModelConfigEntity sd = new ModelConfigEntity();
        sd.setVendor("stable_diffusion");
        sd.setModelType(ModelType.TEXT_TO_IMAGE);
        sd.setApiEndpoint("https://api.stability.ai/v1/generation");
        sd.setApiKey("sk-demo-key");
        sd.setPollInterval(10);
        sd.setMaxPollCount(60);
        sd.setMaxRetries(3);
        configMapper.insert(sd);

        ModelConfigEntity kling = new ModelConfigEntity();
        kling.setVendor("kling");
        kling.setModelType(ModelType.IMAGE_TO_VIDEO);
        kling.setApiEndpoint("https://api.klingai.com/v1/videos");
        kling.setApiKey("sk-demo-key");
        kling.setPollInterval(30);
        kling.setMaxPollCount(120);
        kling.setMaxRetries(2);
        configMapper.insert(kling);

        log.info("演示模型配置初始化完成: count={}", configMapper.selectCount(null));
    }
}
