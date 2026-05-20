package com.mlm.common.exception;

/**
 * 业务异常 — 自定义错误码的业务错误
 * <p>
 * 由 {@link com.mlm.web.config.GlobalExceptionHandler} 捕获后返回对应的 HTTP 状态码。
 * 默认 code=500，可通过构造函数自定义。
 */
public class BizException extends RuntimeException {
    private final int code;

    /** 默认 500 业务异常 */
    public BizException(String message) {
        super(message);
        this.code = 500;
    }

    /** 自定义 code 的业务异常 */
    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    /** 获取业务错误码 */
    public int getCode() { return code; }
}
