package com.mlm.common.exception;

import com.mlm.common.constant.ErrorCode;

/**
 * 业务异常 — 携带 HTTP 语义状态码和可选的业务状态码。
 * <p>
 * 在 Service 层直接抛出此异常，由 {@link GlobalExceptionHandler} 统一捕获并转换为 {@link com.mlm.common.result.ApiResult}。
 * 使用业务异常而非直接返回 ApiResult.fail()，可以保持 Service 层的方法签名干净，
 * 同时将异常处理逻辑收敛到全局异常处理器中。
 * </p>
 *
 * <p>
 * 【使用场景】
 * <ul>
 *   <li>参数校验失败 — {@code throw new BizException(400, "xxx不能为空", "PARAM_MISSING")}</li>
 *   <li>资源不存在 — {@code throw new BizException(404, "项目不存在", "PROJECT_NOT_FOUND")}</li>
 *   <li>权限不足 — {@code throw new BizException(403, "无权操作")}</li>
 * </ul>
 * </p>
 *
 * @author mlm
 * @see ErrorCode 错误码常量
 * @see GlobalExceptionHandler
 */
public class BizException extends RuntimeException {

    /** HTTP 语义状态码（400/404/403/500） */
    private final int httpCode;

    /** 业务状态码（如 PROJECT_NOT_FOUND, PERMISSION_DENIED），可为 null */
    private final String statusCode;

    /**
     * 创建默认错误码（500）的业务异常。
     *
     * @param message 错误描述
     */
    public BizException(String message) {
        super(message);
        this.httpCode = 500;
        this.statusCode = null;
    }

    /**
     * 创建指定 HTTP 语义错误码的业务异常。
     *
     * @param httpCode HTTP 语义状态码（400/404/403/500）
     * @param message  错误描述
     */
    public BizException(int httpCode, String message) {
        super(message);
        this.httpCode = httpCode;
        this.statusCode = null;
    }

    /**
     * 创建指定 HTTP 语义错误码和业务状态码的业务异常。
     *
     * @param httpCode   HTTP 语义状态码（400/404/403/500）
     * @param message    错误描述
     * @param statusCode 业务状态码（如 PROJECT_NOT_FOUND），可为 null
     */
    public BizException(int httpCode, String message, String statusCode) {
        super(message);
        this.httpCode = httpCode;
        this.statusCode = statusCode;
    }

    /**
     * 获取 HTTP 语义状态码。
     *
     * @return HTTP 状态码
     */
    public int getHttpCode() {
        return httpCode;
    }

    /**
     * 获取业务状态码。
     *
     * @return 业务状态码，可能为 null
     */
    public String getStatusCode() {
        return statusCode;
    }

    /**
     * 获取 HTTP 语义状态码（兼容旧版调用）。
     *
     * @return HTTP 状态码
     * @deprecated 使用 {@link #getHttpCode()} 替代
     */
    @Deprecated
    public int getCode() {
        return httpCode;
    }
}
