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
 * 【职责】
 * 根据厂商标识和模型类型查询启用的配置，供 {@link com.mlm.model.core.ModelGateway}
 * 在轮询前获取轮询间隔、重试次数等参数。
 * <p>
 * 【缓存策略】
 * 当前直接查询数据库，未启用缓存。
 * 若配置变更频繁度低，可考虑引入 Caffeine 本地缓存或 Redis 缓存
 * 减少数据库查询压力。配置变更时通过 ModelConfigMapper 更新 DB
 * 并主动失效缓存。
 *
 * @author mlm
 * @see ModelConfigEntity
 * @see ModelConfigMapper
 * @see com.mlm.model.core.ModelGateway#pollAndUpdate
 */
@Component
public class ModelConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(ModelConfigLoader.class);

    private final ModelConfigMapper mapper;

    /**
     * 构造配置加载器
     *
     * @param mapper 模型配置 Mapper
     */
    public ModelConfigLoader(ModelConfigMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 加载指定厂商+类型的启用配置
     * <p>
     * 查询条件：vendor + modelType + isEnabled=true。
     * 未找到时返回 {@link Optional#empty()}，调用方需自行处理缺失配置的情况。
     *
     * @param vendor 厂商标识（如 openai、stable_diffusion、kling）
     * @param type   模型类型（文生文/文生图/图生视频）
     * @return 配置实体 Optional
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
        } else {
            log.debug("模型配置加载成功: vendor={}, type={}, endpoint={}",
                    vendor, type, entity.getApiEndpoint());
        }

        return Optional.ofNullable(entity);
    }
}
