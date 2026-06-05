package com.riskdatahub.datasource;

import com.riskdatahub.common.result.ApiResult;
import com.riskdatahub.datasource.dto.DataSourceConfigDTO;
import com.riskdatahub.datasource.dto.DataSourceVO;
import com.riskdatahub.datasource.dto.KeyRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 数据源管理控制器 — 运行时查看、注册、删除数据源。
 * <p>
 * 所有接口均使用 POST + RequestBody 风格。
 * </p>
 *
 * @author risk-data-hub
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class DataSourceController {

    private final DataSourceManager dataSourceManager;

    /**
     * 列出所有已注册的数据源。
     *
     * @return 数据源列表
     */
    @PostMapping("/api-datasource-list")
    public ApiResult<List<DataSourceVO>> list() {
        return ApiResult.ok(dataSourceManager.listAll(), "DATASOURCE_LIST_LOADED");
    }

    /**
     * 根据标识查询单个数据源。
     *
     * @param request 包含数据源 key 的请求
     * @return 数据源详情
     */
    @PostMapping("/api-datasource-get")
    public ApiResult<DataSourceVO> get(@Valid @RequestBody KeyRequest request) {
        DataSourceVO vo = dataSourceManager.get(request.getKey());
        if (vo == null) {
            return ApiResult.fail(404, "数据源不存在: " + request.getKey(), "DATASOURCE_NOT_FOUND");
        }
        return ApiResult.ok(vo, "DATASOURCE_LOADED");
    }

    /**
     * 注册新数据源。
     *
     * @param config 数据源配置
     * @return 操作成功
     */
    @PostMapping("/api-datasource-register")
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
     * 删除数据源。
     *
     * @param request 包含数据源 key 的请求
     * @return 操作成功
     */
    @PostMapping("/api-datasource-remove")
    public ApiResult<Void> remove(@Valid @RequestBody KeyRequest request) {
        if (!dataSourceManager.exists(request.getKey())) {
            return ApiResult.fail(404, "数据源不存在: " + request.getKey(), "DATASOURCE_NOT_FOUND");
        }
        try {
            dataSourceManager.remove(request.getKey());
            return ApiResult.ok("DATASOURCE_REMOVED");
        } catch (Exception e) {
            return ApiResult.fail(500, e.getMessage(), "DATASOURCE_REMOVE_FAILED");
        }
    }
}
