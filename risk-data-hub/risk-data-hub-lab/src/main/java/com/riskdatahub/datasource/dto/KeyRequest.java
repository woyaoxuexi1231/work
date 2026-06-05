package com.riskdatahub.datasource.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 通用 Key 请求体 — 封装单个数据源标识的请求。
 * <p>
 * 用于 {@code /api-datasource-get} 和 {@code /api-datasource-remove} 等
 * 只需要传递一个 key 参数的 POST 接口。
 * </p>
 *
 * @author risk-data-hub
 */
@Data
public class KeyRequest {

    /** 数据源唯一标识 */
    @NotBlank(message = "数据源 key 不能为空")
    private String key;
}
