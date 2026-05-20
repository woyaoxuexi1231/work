package com.mlm.web.controller;

import com.mlm.common.dto.ApiResult;
import com.mlm.resource.entity.Resource;
import com.mlm.resource.service.ResourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 资源接口 — 全部 POST
 */
@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    private static final Logger log = LoggerFactory.getLogger(ResourceController.class);
    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) { this.resourceService = resourceService; }

    @PostMapping("/list")
    public ApiResult<List<Resource>> list() {
        return ApiResult.ok(resourceService.listAll(), "RESOURCE_LIST_LOADED");
    }
}
