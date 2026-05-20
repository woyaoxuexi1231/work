package com.mlm.common.exception;

/**
 * Pipeline 状态流异常 — 非法状态跳转、项目不存在等
 * <p>
 * 由 {@link com.mlm.web.config.GlobalExceptionHandler} 捕获后返回 400。
 */
public class PipelineException extends RuntimeException {

    public PipelineException(String message) {
        super(message);
    }

    public PipelineException(String message, Throwable cause) {
        super(message, cause);
    }
}
