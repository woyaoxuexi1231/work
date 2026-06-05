package com.riskdatahub.common.exception;

import lombok.Getter;

/**
 * 业务异常 — 携带业务状态码和 HTTP 语义状态码。
 * <p>
 * 在 Service 层抛出此异常，由 {@link GlobalExceptionHandler} 统一捕获并转换为 {@link com.riskdatahub.common.result.ApiResult}。
 * 使用业务异常而非直接返回 ApiResult.fail()，可以保持 Service 层的方法签名干净（返回业务对象而非 ApiResult），
 * 同时将异常处理逻辑收敛到全局处理器中。
 * </p>
 *
 * @author risk-data-hub
 */
@Getter
public class BusinessException extends RuntimeException {

    /** HTTP 语义状态码（400/404/409/500） */
    private final int httpCode;

    /** 业务状态码（如 DATASOURCE_NOT_FOUND, SYNC_TASK_ALREADY_RUNNING） */
    private final String statusCode;

    /**
     * 构造业务异常。
     *
     * @param httpCode   HTTP 语义状态码
     * @param message    错误描述
     * @param statusCode 业务状态码
     */
    public BusinessException(int httpCode, String message, String statusCode) {
        super(message);
        this.httpCode = httpCode;
        this.statusCode = statusCode;
    }

    /**
     * 构造业务异常（含根因）。
     *
     * @param httpCode   HTTP 语义状态码
     * @param message    错误描述
     * @param statusCode 业务状态码
     * @param cause      根因异常
     */
    public BusinessException(int httpCode, String message, String statusCode, Throwable cause) {
        super(message, cause);
        this.httpCode = httpCode;
        this.statusCode = statusCode;
    }
}
