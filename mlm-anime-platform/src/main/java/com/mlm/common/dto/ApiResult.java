package com.mlm.common.dto;

import lombok.Getter;

/**
 * 统一 HTTP 响应体
 * <p>
 * 所有 REST 接口统一使用此结构返回，code=200 表示成功，非 200 表示业务异常。
 * 前端根据 code 判断是否弹窗或跳转错误页。
 * 新增 status 字段提供详细的状态描述。
 *
 * @param <T> data 数据类型
 */
@Getter
public class ApiResult<T> {
    /** 状态码：200=成功，非200=业务异常 */
    private final int code;
    /** 提示信息 */
    private final String message;
    /** 详细状态描述（如"剧本已提交，等待审核"） */
    private final String status;
    /** 响应数据 */
    private final T data;

    private ApiResult(int code, String message, String status, T data) {
        this.code = code;
        this.message = message;
        this.status = status;
        this.data = data;
    }

    /** 成功（有返回数据）- 默认状态描述 */
    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(200, "操作成功", "SUCCESS", data);
    }

    /** 成功（有返回数据）- 自定义状态描述 */
    public static <T> ApiResult<T> ok(T data, String status) {
        return new ApiResult<>(200, "操作成功", status, data);
    }

    /** 成功（无返回数据）- 默认状态描述 */
    public static <T> ApiResult<T> ok() {
        return new ApiResult<>(200, "操作成功", "SUCCESS", null);
    }

    /** 成功（无返回数据）- 自定义状态描述 */
    public static <T> ApiResult<T> ok(String status) {
        return new ApiResult<>(200, "操作成功", status, null);
    }

    /** 业务失败 */
    public static <T> ApiResult<T> fail(int code, String message) {
        return new ApiResult<>(code, message, "FAILED", null);
    }

    /** 业务失败 - 自定义状态描述 */
    public static <T> ApiResult<T> fail(int code, String message, String status) {
        return new ApiResult<>(code, message, status, null);
    }
}
