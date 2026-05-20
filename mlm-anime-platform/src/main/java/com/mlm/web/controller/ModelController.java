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
 * 模型配置接口 — 全部 POST
 */
@RestController
@RequestMapping("/api/models")
public class ModelController {

    private static final Logger log = LoggerFactory.getLogger(ModelController.class);
    private final ModelConfigMapper configMapper;

    public ModelController(ModelConfigMapper configMapper) { this.configMapper = configMapper; }

    @PostMapping("/list")
    public ApiResult<List<ModelConfigEntity>> list() {
        return ApiResult.ok(configMapper.selectList(null), "MODEL_LIST_LOADED");
    }

    @PostMapping("/create")
    public ApiResult<ModelConfigEntity> create(@RequestBody ModelConfigEntity config) {
        configMapper.insert(config);
        return ApiResult.ok(config, "MODEL_CREATED");
    }
}
