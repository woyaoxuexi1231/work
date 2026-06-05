package com.riskdatahub.controller;

import com.riskdatahub.common.result.ApiResult;
import com.riskdatahub.datasource.DataSourceManager;
import com.riskdatahub.datasource.dto.DataSourceConfigDTO;
import com.riskdatahub.datasource.dto.DataSourceVO;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据源管理控制器 — 运行时查看、注册、删除数据源。
 * <p>
 * <b>RESTful 资源映射：</b>
 * <ul>
 *   <li>{@code GET /api/datasource} — 列出所有数据源</li>
 *   <li>{@code GET /api/datasource/{key}} — 查看单个数据源</li>
 *   <li>{@code POST /api/datasource/register} — 注册新数据源</li>
 *   <li>{@code DELETE /api/datasource/{key}} — 删除数据源</li>
 * </ul>
 * 参数校验统一使用 {@code @Valid} + {@code @NotBlank}，由 {@link com.riskdatahub.common.exception.GlobalExceptionHandler} 统一处理。
 * </p>
 *
 * @author risk-data-hub
 */
@Slf4j
@RestController
@RequestMapping("/api/datasource")
@RequiredArgsConstructor
public class DataSourceController {

    private final DataSourceManager dataSourceManager;

    /**
     * 列出所有已注册的数据源。
     *
     * @return 数据源列表
     */
    @GetMapping
    public ApiResult<List<DataSourceVO>> list() {
        return ApiResult.ok(dataSourceManager.listAll(), "DATASOURCE_LIST_LOADED");
    }

    /**
     * 根据标识查询单个数据源。
     *
     * @param key 数据源标识
     * @return 数据源详情
     */
    @GetMapping("/{key}")
    public ApiResult<DataSourceVO> get(@PathVariable @NotBlank String key) {
        DataSourceVO vo = dataSourceManager.get(key);
        if (vo == null) {
            return ApiResult.fail(404, "数据源不存在: " + key, "DATASOURCE_NOT_FOUND");
        }
        return ApiResult.ok(vo, "DATASOURCE_LOADED");
    }

    /**
     * 注册新数据源。
     * <p>
     * 参数校验 → 检查唯一性 → 创建连接池 → 测试连接 → 加入路由表。
     * </p>
     *
     * @param config 数据源配置
     * @return 操作成功
     */
    @PostMapping("/register")
    public ApiResult<Void> register(@Valid @RequestBody DataSourceConfigDTO config) {
        if (dataSourceManager.exists(config.getKey())) {
            return ApiResult.fail(409, "数据源 '" + config.getKey() + "' 已存在", "DATASOURCE_ALREADY_EXISTS");
        }
        try {
            dataSourceManager.register(config);
            return ApiResult.ok("DATASOURCE_REGISTERED");
        } catch (Exception e) {
            return ApiResult.fail(500, e.getMessage(), "DATASOURCE_REGISTER_FAILED");
        }
    }

    /**
     * 删除数据源（优雅下线）。
     * <p>
     * 检查存在性 → 摘除路由 → 排空连接池 → 关闭连接池。
     * </p>
     *
     * @param key 要删除的数据源标识
     * @return 操作成功
     */
    @DeleteMapping("/{key}")
    public ApiResult<Void> remove(@PathVariable @NotBlank String key) {
        if (!dataSourceManager.exists(key)) {
            return ApiResult.fail(404, "数据源不存在: " + key, "DATASOURCE_NOT_FOUND");
        }
        try {
            dataSourceManager.remove(key);
            return ApiResult.ok("DATASOURCE_REMOVED");
        } catch (Exception e) {
            return ApiResult.fail(500, e.getMessage(), "DATASOURCE_REMOVE_FAILED");
        }
    }
}
