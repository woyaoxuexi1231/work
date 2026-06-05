package com.mlm.web.controller;

import com.mlm.common.dto.ApiResult;
import com.mlm.model.config.ModelConfigEntity;
import com.mlm.model.mapper.ModelConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模型配置管理控制器 — AI 厂商配置的查询和创建
 * <p>
 * 【职责】
 * <ul>
 *   <li>模型配置列表（list）— 查询所有已配置的厂商模型</li>
 *   <li>模型配置创建（create）— 新增厂商或模型类型的配置</li>
 * </ul>
 * <p>
 * 配置通过 {@link com.mlm.web.config.DataInitializer} 在首次启动时自动初始化，
 * 此控制器提供运行时的管理能力。
 *
 * @author mlm
 * @see ModelConfigEntity 模型配置实体
 * @see com.mlm.model.config.ModelConfigLoader 模型配置加载器
 */
@RestController
@RequestMapping("/api/models")
public class ModelController {

    private static final Logger log = LoggerFactory.getLogger(ModelController.class);

    private final ModelConfigMapper configMapper;

    /**
     * 构造模型配置控制器
     *
     * @param configMapper 模型配置 Mapper
     */
    public ModelController(ModelConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    /**
     * 查询所有模型配置
     *
     * @return 模型配置列表
     */
    @PostMapping("/list")
    public ApiResult<List<ModelConfigEntity>> list() {
        List<ModelConfigEntity> configs = configMapper.selectList(null);
        log.debug("模型配置查询: count={}", configs.size());
        return ApiResult.ok(configs);
    }

    /**
     * 创建新的模型配置
     *
     * @param config 模型配置实体
     * @return 创建后的配置实体（含自增 ID）
     */
    @PostMapping("/create")
    public ApiResult<ModelConfigEntity> create(@RequestBody ModelConfigEntity config) {
        configMapper.insert(config);
        log.info("模型配置已创建: vendor={}, type={}", config.getVendor(), config.getModelType());
        return ApiResult.ok(config);
    }
}
