package com.mlm.web.config;

import com.mlm.common.enums.ModelType;
import com.mlm.model.config.ModelConfigEntity;
import com.mlm.model.mapper.ModelConfigMapper;
import com.mlm.user.entity.User;
import com.mlm.user.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 数据初始化器
 * <p>
 * 首次启动时插入演示用户和模型配置，已有数据时跳过。
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private final ModelConfigMapper configMapper;
    private final UserMapper userMapper;

    public DataInitializer(ModelConfigMapper configMapper, UserMapper userMapper) {
        this.configMapper = configMapper;
        this.userMapper = userMapper;
    }

    @Override
    public void run(String... args) {
        initUsers();
        initModelConfigs();
    }

    private void initUsers() {
        if (userMapper.selectCount(null) > 0) {
            log.info("用户已存在，跳过初始化");
            return;
        }
        log.info("初始化演示用户...");

        User admin = new User();
        admin.setUsername("admin");
        admin.setPassword("123456");
        admin.setRole("ADMIN");
        userMapper.insert(admin);

        User demo = new User();
        demo.setUsername("demo");
        demo.setPassword("123456");
        demo.setRole("USER");
        userMapper.insert(demo);

        log.info("已初始化 2 个演示用户");
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

        log.info("已初始化 {} 条模型配置", configMapper.selectCount(null));
    }
}
