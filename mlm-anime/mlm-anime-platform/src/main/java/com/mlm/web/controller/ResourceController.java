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
 * 资源管理控制器 — 资源列表查询
 * <p>
 * 资源（图片/视频/音频/文本）通过前端直连 MinIO 上传，
 * 此控制器仅提供资源元信息的查询接口。
 * 文件上传逻辑在 {@link com.mlm.resource.service.ResourceService} 中。
 *
 * @author mlm
 * @see Resource 资源实体
 * @see ResourceService 资源服务
 * @see com.mlm.resource.service.OssService MinIO 对象存储服务
 */
@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    private static final Logger log = LoggerFactory.getLogger(ResourceController.class);

    private final ResourceService resourceService;

    /**
     * 构造资源控制器
     *
     * @param resourceService 资源服务
     */
    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    /**
     * 查询所有资源列表
     *
     * @return 资源列表
     */
    @PostMapping("/list")
    public ApiResult<List<Resource>> list() {
        List<Resource> resources = resourceService.listAll();
        log.debug("资源列表查询: count={}", resources.size());
        return ApiResult.ok(resources);
    }
}
