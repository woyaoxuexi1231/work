package com.riskdatahub.common.result;

import lombok.Data;

/**
 * 统一 API 响应结果封装。
 * <p>
 * 所有 REST 接口必须使用此类作为统一响应体，确保前端可以按固定结构解析：
 * <pre>
 * { "code": 200, "message": "操作成功", "status": "SUCCESS", "data": {...} }
 * </pre>
 * </p>
 *
 * @param <T> data 字段的具体类型
 * @author risk-data-hub
 */
@Data
public class ApiResult<T> {

    /** HTTP 状态码，200 表示成功 */
    private int code;

    /** 面向用户的提示信息 */
    private String message;

    /** 业务状态码，供前端做程序化判断（如 SUCCESS / VALIDATION_FAILED） */
    private String status;

    /** 响应数据体 */
    private T data;

    /**
     * 构造成功响应（带返回数据 + 默认状态描述）。
     *
     * @param data 返回数据
     * @param <T>  数据类型
     * @return 成功响应
     */
    public static <T> ApiResult<T> ok(T data) {
        ApiResult<T> r = new ApiResult<>();
        r.code = 200;
        r.message = "操作成功";
        r.status = "SUCCESS";
        r.data = data;
        return r;
    }

    /**
     * 构造成功响应（带返回数据 + 自定义状态描述）。
     *
     * @param data   返回数据
     * @param status 自定义业务状态码
     * @param <T>    数据类型
     * @return 成功响应
     */
    public static <T> ApiResult<T> ok(T data, String status) {
        ApiResult<T> r = new ApiResult<>();
        r.code = 200;
        r.message = "操作成功";
        r.status = status;
        r.data = data;
        return r;
    }

    /**
     * 构造成功响应（无返回数据）。
     *
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> ApiResult<T> ok() {
        return ok(null, "SUCCESS");
    }

    /**
     * 构造成功响应（无返回数据 + 自定义状态描述）。
     *
     * @param status 自定义业务状态码
     * @param <T>    数据类型
     * @return 成功响应
     */
    public static <T> ApiResult<T> ok(String status) {
        return ok(null, status);
    }

    /**
     * 构造失败响应。
     *
     * @param code    HTTP 语义状态码（400/404/409/500）
     * @param message 错误描述
     * @param <T>     数据类型
     * @return 失败响应
     */
    public static <T> ApiResult<T> fail(int code, String message) {
        ApiResult<T> r = new ApiResult<>();
        r.code = code;
        r.message = message;
        r.status = "FAILED";
        return r;
    }

    /**
     * 构造失败响应（自定义业务状态码）。
     *
     * @param code    HTTP 语义状态码
     * @param message 错误描述
     * @param status  自定义业务状态码
     * @param <T>     数据类型
     * @return 失败响应
     */
    public static <T> ApiResult<T> fail(int code, String message, String status) {
        ApiResult<T> r = new ApiResult<>();
        r.code = code;
        r.message = message;
        r.status = status;
        return r;
    }
}
