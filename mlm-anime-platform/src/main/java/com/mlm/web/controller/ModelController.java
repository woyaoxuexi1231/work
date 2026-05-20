package com.mlm.web.controller;

import com.mlm.common.dto.ApiResult;
import com.mlm.model.config.ModelConfigEntity;
import com.mlm.model.mapper.ModelConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模型配置管理 REST 接口
 * <p>
 * 提供 AI 厂商配置的查询和新增功能。
 * 配置项包括 API 地址、密钥、轮询间隔等。
 */
@RestController
@RequestMapping("/api/models")
public class ModelController {

    private static final Logger log = LoggerFactory.getLogger(ModelController.class);

    private final ModelConfigMapper configMapper;

    public ModelController(ModelConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    /** 查询所有模型配置 */
    @GetMapping("/configs")
    public ApiResult<List<ModelConfigEntity>> listConfigs() {
        return ApiResult.ok(configMapper.selectList(null));
    }

    /** 新增模型配置 */
    @PostMapping("/configs")
    public ApiResult<ModelConfigEntity> addConfig(@RequestBody ModelConfigEntity config) {
        configMapper.insert(config);
        log.info("新增模型配置: vendor={}, type={}", config.getVendor(), config.getModelType());
        return ApiResult.ok(config);
    }
}
