package com.example.dynamicds.controller;

import com.example.dynamicds.datasource.DynamicDataSourceManager;
import com.example.dynamicds.dto.ApiResult;
import com.example.dynamicds.dto.DataSourceConfigDTO;
import com.example.dynamicds.dto.DataSourceVO;
import com.example.dynamicds.dto.KeyRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据源管理控制器 — 运行时查看、注册、删除数据源。
 * <p>
 * <b>接口风格：全部 POST + 请求体对象</b>，无路径参数、无 Query 参数。
 */
@RestController
@RequestMapping("/api/datasource")
@RequiredArgsConstructor
public class DataSourceController {

    private final DynamicDataSourceManager manager;

    @PostMapping("/list")
    public ApiResult<List<DataSourceVO>> list() {
        return ApiResult.ok(manager.listAll(), "DATASOURCE_LIST_LOADED");
    }

    @PostMapping("/get")
    public ApiResult<DataSourceVO> get(@RequestBody KeyRequest request) {
        DataSourceVO vo = manager.get(request.getKey());
        if (vo == null) {
            return ApiResult.fail(404, "数据源不存在: " + request.getKey(), "DATASOURCE_NOT_FOUND");
        }
        return ApiResult.ok(vo, "DATASOURCE_LOADED");
    }

    /**
     * 注册数据源：参数校验 → 检查唯一性 → 创建连接池 → 测试连接 → 加入路由表
     */
    @PostMapping("/register")
    public ApiResult<Void> register(@RequestBody DataSourceConfigDTO config) {
        if (config.getKey() == null || config.getKey().isBlank()) {
            return ApiResult.fail(400, "key 不能为空", "VALIDATION_KEY_EMPTY");
        }
        if (config.getName() == null || config.getName().isBlank()) {
            return ApiResult.fail(400, "name 不能为空", "VALIDATION_NAME_EMPTY");
        }
        if (config.getDatasourceType() == null || config.getDatasourceType().isBlank()) {
            return ApiResult.fail(400, "datasourceType 不能为空", "VALIDATION_TYPE_EMPTY");
        }
        if (config.getUrl() == null || config.getUrl().isBlank()) {
            return ApiResult.fail(400, "url 不能为空", "VALIDATION_URL_EMPTY");
        }
        if (manager.exists(config.getKey())) {
            return ApiResult.fail(409, "数据源 '" + config.getKey() + "' 已存在", "DATASOURCE_ALREADY_EXISTS");
        }
        try {
            manager.register(config);
            return ApiResult.ok("DATASOURCE_REGISTERED");
        } catch (Exception e) {
            return ApiResult.fail(500, e.getMessage(), "DATASOURCE_REGISTER_FAILED");
        }
    }

    /**
     * 删除数据源：检查存在性 → 优雅下线（摘除路由 + 排空连接池 + 关闭）
     */
    @PostMapping("/remove")
    public ApiResult<Void> remove(@RequestBody KeyRequest request) {
        if (!manager.exists(request.getKey())) {
            return ApiResult.fail(404, "数据源不存在: " + request.getKey(), "DATASOURCE_NOT_FOUND");
        }
        try {
            manager.remove(request.getKey());
            return ApiResult.ok("DATASOURCE_REMOVED");
        } catch (Exception e) {
            return ApiResult.fail(500, e.getMessage(), "DATASOURCE_REMOVE_FAILED");
        }
    }
}
