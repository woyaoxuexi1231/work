package com.mlm.common.exception;

import com.mlm.common.result.ApiResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.validation.ConstraintViolationException;

/**
 * 全局异常处理器 — 统一捕获各层异常并转换为 {@link ApiResult} 响应。
 * <p>
 * 按异常类型分级处理：
 * <ul>
 *   <li>{@link BizException} — 业务异常，保留其携带的 HTTP 状态码和业务码</li>
 *   <li>{@link PipelineException} — Pipeline 状态异常，返回 400</li>
 *   <li>{@link SecurityException} / {@link IllegalArgumentException} — 安全/参数异常，返回 400</li>
 *   <li>{@link ConstraintViolationException} / {@link MissingServletRequestParameterException} — 校验失败，返回 400</li>
 *   <li>{@link NoHandlerFoundException} — 静态资源 404，DEBUG 级别日志</li>
 *   <li>其它 {@link Exception} — 500 系统异常，ERROR 级别日志</li>
 * </ul>
 * </p>
 *
 * @author mlm
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常，保留异常的 HTTP 状态码和业务码。
     */
    @ExceptionHandler(BizException.class)
    public ApiResult<?> handleBizException(BizException e) {
        if (e.getStatusCode() != null) {
            log.warn("[业务异常] httpCode={}, statusCode={}, message={}",
                    e.getHttpCode(), e.getStatusCode(), e.getMessage());
            return ApiResult.fail(e.getHttpCode(), e.getMessage(), e.getStatusCode());
        }
        log.warn("[业务异常] httpCode={}, message={}", e.getHttpCode(), e.getMessage());
        return ApiResult.fail(e.getHttpCode(), e.getMessage());
    }

    /**
     * 处理 Pipeline 状态流异常。
     */
    @ExceptionHandler(PipelineException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<?> handlePipelineException(PipelineException e) {
        log.warn("[Pipeline 异常] {}", e.getMessage());
        return ApiResult.fail(400, e.getMessage());
    }

    /**
     * 处理安全异常和非法参数异常。
     */
    @ExceptionHandler({SecurityException.class, IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<?> handleSecurityAndParamException(RuntimeException e) {
        log.warn("[安全/参数异常] {}", e.getMessage());
        return ApiResult.fail(400, e.getMessage());
    }

    /**
     * 处理 Bean Validation 校验异常。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<?> handleConstraintViolation(ConstraintViolationException e) {
        log.warn("[参数校验失败] {}", e.getMessage());
        return ApiResult.fail(400, "参数校验失败: " + e.getMessage());
    }

    /**
     * 处理请求参数缺失异常。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<?> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("[缺少请求参数] {}", e.getParameterName());
        return ApiResult.fail(400, "缺少必要参数: " + e.getParameterName());
    }

    /**
     * 处理 404 静态资源。
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResult<?> handleNoResource(NoHandlerFoundException e) {
        log.debug("[静态资源不存在] {}", e.getRequestURL());
        return ApiResult.fail(404, "资源不存在");
    }

    /**
     * 处理所有未预期的系统异常 — 兜底处理。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResult<?> handleUnknownException(Exception e) {
        log.error("[系统异常] {}", e.getMessage(), e);
        return ApiResult.fail(500, "系统繁忙，请稍后重试");
    }
}
