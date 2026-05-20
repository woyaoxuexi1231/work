package com.mlm.web.controller;

import com.mlm.common.dto.ApiResult;
import com.mlm.common.enums.ResourceType;
import com.mlm.resource.entity.Resource;
import com.mlm.resource.service.ResourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 资源管理 REST 接口
 * <p>
 * 提供资源的列表查询、详情查询和文件上传。
 * 上传的文件存储在 MinIO，数据库仅保存元信息。
 */
@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    private static final Logger log = LoggerFactory.getLogger(ResourceController.class);

    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    /** 查询所有资源 */
    @GetMapping
    public ApiResult<List<Resource>> list() {
        return ApiResult.ok(resourceService.listAll());
    }

    /** 根据 ID 查询资源 */
    @GetMapping("/{id}")
    public ApiResult<Resource> get(@PathVariable Long id) {
        Resource r = resourceService.getById(id);
        return r != null ? ApiResult.ok(r) : ApiResult.fail(404, "资源不存在");
    }

    /** 上传文件 */
    @PostMapping("/upload")
    public ApiResult<Resource> upload(@RequestParam MultipartFile file,
                                      @RequestParam String name,
                                      @RequestParam ResourceType type) {
        Resource resource = resourceService.upload(file, name, type);
        log.info("文件上传: name={}, type={}, size={}", name, type, file.getSize());
        return ApiResult.ok(resource);
    }
}
