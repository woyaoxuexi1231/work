package com.example.dynamicds.controller;

import com.example.dynamicds.datasource.DynamicDataSourceManager;
import com.example.dynamicds.dto.ApiResult;
import com.example.dynamicds.dto.DataSourceConfigDTO;
import com.example.dynamicds.dto.DataSourceVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/datasource")
public class DataSourceController {

    private final DynamicDataSourceManager manager;

    public DataSourceController(DynamicDataSourceManager manager) {
        this.manager = manager;
    }

    /** 查询所有已注册数据源及其连接池状态 */
    @GetMapping
    public ApiResult<List<DataSourceVO>> list() {
        return ApiResult.ok(manager.listAll());
    }

    /** 查询单个数据源 */
    @GetMapping("/{key}")
    public ApiResult<DataSourceVO> get(@PathVariable String key) {
        DataSourceVO vo = manager.get(key);
        if (vo == null) {
            return ApiResult.getFail(404, "数据源不存在: " + key);
        }
        return ApiResult.ok(vo);
    }

    /** 动态注册数据源 */
    @PostMapping
    public ApiResult<Void> register(@RequestBody DataSourceConfigDTO config) {
        if (config.getKey() == null || config.getKey().isBlank()) {
            return ApiResult.getFail(400, "key 不能为空");
        }
        if (config.getUrl() == null || config.getUrl().isBlank()) {
            return ApiResult.getFail(400, "url 不能为空");
        }
        if (manager.exists(config.getKey())) {
            return ApiResult.getFail(409, "数据源 '" + config.getKey() + "' 已存在");
        }
        try {
            manager.register(config);
            return ApiResult.ok();
        } catch (Exception e) {
            return ApiResult.getFail(500, e.getMessage());
        }
    }

    /** 优雅下线数据源 */
    @DeleteMapping("/{key}")
    public ApiResult<Void> remove(@PathVariable String key) {
        if (!manager.exists(key)) {
            return ApiResult.getFail(404, "数据源不存在: " + key);
        }
        try {
            manager.remove(key);
            return ApiResult.ok();
        } catch (Exception e) {
            return ApiResult.getFail(500, e.getMessage());
        }
    }
}
