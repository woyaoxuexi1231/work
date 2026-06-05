package com.mlm.common.exception;

import com.mlm.common.constant.ErrorCode;

/**
 * 业务异常 — 自定义错误码的业务错误
 * <p>
 * 由 {@link com.mlm.web.config.GlobalExceptionHandler} 捕获后返回对应的
 * HTTP 状态码和错误信息。默认错误码为 500，可通过构造函数自定义。
 * <p>
 * 【使用场景】
 * <ul>
 *   <li>参数校验失败 — {@code new BizException(ErrorCode.INVALID_REQUEST, "xxx不能为空")}</li>
 *   <li>资源不存在 — {@code new BizException(ErrorCode.PROJECT_NOT_FOUND, "项目不存在")}</li>
 *   <li>权限不足 — {@code new BizException(ErrorCode.FORBIDDEN, "无权操作")}</li>
 * </ul>
 *
 * @author mlm
 * @see ErrorCode 错误码常量
 * @see com.mlm.web.config.GlobalExceptionHandler
 */
public class BizException extends RuntimeException {

    /** 业务错误码 */
    private final int code;

    /**
     * 创建默认错误码（500）的业务异常
     *
     * @param message 错误描述
     */
    public BizException(String message) {
        super(message);
        this.code = 500;
    }

    /**
     * 创建指定错误码的业务异常
     *
     * @param code    业务错误码
     * @param message 错误描述
     */
    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 获取业务错误码
     *
     * @return 业务错误码
     */
    public int getCode() {
        return code;
    }
}
