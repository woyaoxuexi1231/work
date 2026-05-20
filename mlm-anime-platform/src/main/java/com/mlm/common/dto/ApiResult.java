package com.mlm.common.dto;

import lombok.Getter;

/**
 * 统一 HTTP 响应体
 * <p>
 * 所有 REST 接口统一使用此结构返回，code=200 表示成功，非 200 表示业务异常。
 * 前端根据 code 判断是否弹窗或跳转错误页。
 *
 * @param <T> data 数据类型
 */
@Getter
public class ApiResult<T> {
    /** 状态码：200=成功，非200=业务异常 */
    private final int code;
    /** 提示信息 */
    private final String message;
    /** 响应数据 */
    private final T data;

    private ApiResult(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 成功（有返回数据） */
    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(200, "success", data);
    }

    /** 成功（无返回数据） */
    public static <T> ApiResult<T> ok() {
        return new ApiResult<>(200, "success", null);
    }

    /** 业务失败 */
    public static <T> ApiResult<T> fail(int code, String message) {
        return new ApiResult<>(code, message, null);
    }
}
