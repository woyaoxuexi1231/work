package com.mlm.model.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mlm.common.enums.ModelType;
import com.mlm.model.mapper.ModelConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 模型配置加载器 — 从数据库读取厂商+模型类型的配置
 * <p>
 * {@link com.mlm.model.core.ModelGateway#pollAndUpdate} 在轮询前调用此加载器
 * 获取轮询间隔、重试次数等配置参数。
 * 配置通过 {@link com.mlm.web.config.DataInitializer} 在首次启动时初始化。
 */
@Component
public class ModelConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(ModelConfigLoader.class);

    private final ModelConfigMapper mapper;

    public ModelConfigLoader(ModelConfigMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 加载指定厂商+类型的启用配置
     *
     * @param vendor 厂商标识
     * @param type   模型类型
     * @return 配置实体（可能为空）
     */
    public Optional<ModelConfigEntity> load(String vendor, ModelType type) {
        ModelConfigEntity entity = mapper.selectOne(
            new LambdaQueryWrapper<ModelConfigEntity>()
                .eq(ModelConfigEntity::getVendor, vendor)
                .eq(ModelConfigEntity::getModelType, type)
                .eq(ModelConfigEntity::getIsEnabled, true)
        );
        if (entity == null) {
            log.warn("未找到启用的模型配置: vendor={}, type={}", vendor, type);
        }
        return Optional.ofNullable(entity);
    }
}
