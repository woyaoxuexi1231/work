package com.mlm.model.controller;

import com.mlm.common.result.ApiResult;
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
 * 模型配置管理控制器 — AI 厂商配置的查询和创建。
 */
@RestController
@RequestMapping("/api/models")
public class ModelController {

    private static final Logger log = LoggerFactory.getLogger(ModelController.class);

    private final ModelConfigMapper configMapper;

    public ModelController(ModelConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    /**
     * 查询所有模型配置。
     */
    @PostMapping("/list")
    public ApiResult<List<ModelConfigEntity>> list() {
        List<ModelConfigEntity> configs = configMapper.selectList(null);
        return ApiResult.ok(configs);
    }

    /**
     * 创建新的模型配置。
     */
    @PostMapping("/create")
    public ApiResult<ModelConfigEntity> create(@RequestBody ModelConfigEntity config) {
        configMapper.insert(config);
        return ApiResult.ok(config);
    }
}
