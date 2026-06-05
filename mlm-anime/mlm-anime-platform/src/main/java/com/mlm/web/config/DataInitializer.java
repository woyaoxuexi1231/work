package com.mlm.web.config;

import com.mlm.common.enums.ModelType;
import com.mlm.model.config.ModelConfigEntity;
import com.mlm.model.mapper.ModelConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 数据初始化器 — 在应用首次启动时初始化必要的配置数据
 * <p>
 * 【职责】
 * 检查 model_config 表是否为空，为空则插入各 AI 厂商的演示配置。
 * 配置包含 OpenAI、Stable Diffusion、可灵 Kling 的 API 端点
 * 和轮询参数，供开发和演示使用。
 * <p>
 * 【安全说明】
 * 初始化的 API Key 为演示用途的占位值（sk-demo-key），
 * 生产环境需通过管理后台替换为真实密钥。
 * <p>
 * 【幂等性】
 * 通过检查 model_config 表记录数确保只初始化一次：
 * 表中有记录则跳过，防止重复插入。
 *
 * @author mlm
 * @see ModelConfigEntity 模型配置实体
 * @see com.mlm.model.config.ModelConfigLoader 模型配置加载器
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final ModelConfigMapper configMapper;

    /**
     * 构造数据初始化器
     *
     * @param configMapper 模型配置 Mapper
     */
    public DataInitializer(ModelConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    /**
     * Spring Boot 启动后自动执行
     * <p>
     * 初始化模型配置数据。
     *
     * @param args 命令行参数（未使用）
     */
    @Override
    public void run(String... args) {
        initModelConfigs();
    }

    /**
     * 初始化各 AI 厂商的演示模型配置
     * <p>
     * 仅在 model_config 表为空时执行（幂等性保证）。
     * 配置内容包括：厂商、模型类型、API 端点、轮询参数。
     */
    private void initModelConfigs() {
        if (configMapper.selectCount(null) > 0) {
            log.info("模型配置已存在，跳过初始化");
            return;
        }

        log.info("初始化演示模型配置...");

        // OpenAI — 文生文（剧本润色、分镜拆分）
        ModelConfigEntity openai = new ModelConfigEntity();
        openai.setVendor("openai");
        openai.setModelType(ModelType.TEXT_TO_TEXT);
        openai.setApiEndpoint("https://api.openai.com/v1/chat/completions");
        openai.setApiKey("sk-demo-key");
        openai.setPollInterval(5);
        openai.setMaxPollCount(30);
        openai.setMaxRetries(3);
        configMapper.insert(openai);

        // Stable Diffusion — 文生图（场景生成）
        ModelConfigEntity sd = new ModelConfigEntity();
        sd.setVendor("stable_diffusion");
        sd.setModelType(ModelType.TEXT_TO_IMAGE);
        sd.setApiEndpoint("https://api.stability.ai/v1/generation");
        sd.setApiKey("sk-demo-key");
        sd.setPollInterval(10);
        sd.setMaxPollCount(60);
        sd.setMaxRetries(3);
        configMapper.insert(sd);

        // 可灵 Kling — 图生视频（成片合成）
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
