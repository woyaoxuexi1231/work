package com.mlm.resource.controller;

import com.mlm.common.result.ApiResult;
import com.mlm.resource.entity.Resource;
import com.mlm.resource.service.ResourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 资源管理控制器 — 资源列表查询。
 */
@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    private static final Logger log = LoggerFactory.getLogger(ResourceController.class);

    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    /**
     * 查询所有资源列表。
     */
    @PostMapping("/list")
    public ApiResult<List<Resource>> list() {
        List<Resource> resources = resourceService.listAll();
        return ApiResult.ok(resources);
    }
}
