package com.mlm.web.config;

import com.mlm.common.dto.ApiResult;
import com.mlm.common.exception.BizException;
import com.mlm.common.exception.PipelineException;
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
 * 全局异常处理器 — 将未捕获的异常转为统一 {@link ApiResult} 格式
 * <p>
 * 按异常类型分级处理：
 * <ul>
 *   <li>{@link BizException} — 业务异常，返回自定义 code + message</li>
 *   <li>{@link PipelineException} — Pipeline 状态异常，返回 400</li>
 *   <li>{@link SecurityException} / {@link IllegalArgumentException} — 安全/参数异常，返回 400</li>
 *   <li>{@link ConstraintViolationException} / {@link MissingServletRequestParameterException} — 校验失败，返回 400</li>
 *   <li>{@link NoHandlerFoundException} — 静态资源 404，DEBUG 级别日志</li>
 *   <li>其它 {@link Exception} — 500 系统异常，ERROR 级别日志</li>
 * </ul>
 * <p>
 * 【日志分级策略】
 * <ul>
 *   <li>DEBUG: 预期内的非关键路径（如 favicon.ico 404）</li>
 *   <li>WARN: 业务异常、权限不足、参数错误等可恢复异常</li>
 *   <li>ERROR: 未预期的系统异常，需要人工关注</li>
 * </ul>
 *
 * @author mlm
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常 {@link BizException}
     * <p>
     * 返回调用方指定的 HTTP 状态码（默认 400），保持前端的错误处理一致性。
     */
    @ExceptionHandler(BizException.class)
    public ApiResult<?> handleBizException(BizException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return ApiResult.fail(e.getCode(), e.getMessage());
    }

    /**
     * 处理 Pipeline 状态流异常 {@link PipelineException}
     * <p>
     * 非法状态跳转、项目不存在等场景。
     */
    @ExceptionHandler(PipelineException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<?> handlePipelineException(PipelineException e) {
        log.warn("Pipeline 异常: {}", e.getMessage());
        return ApiResult.fail(400, e.getMessage());
    }

    /**
     * 处理安全异常 {@link SecurityException} 和非法参数 {@link IllegalArgumentException}
     * <p>
     * 这两种异常通常由权限校验和参数校验主动抛出。
     */
    @ExceptionHandler({SecurityException.class, IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<?> handleSecurityAndParamException(RuntimeException e) {
        log.warn("安全/参数异常: {}", e.getMessage());
        return ApiResult.fail(400, e.getMessage());
    }

    /**
     * 处理 Bean Validation 校验异常 {@link ConstraintViolationException}
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<?> handleConstraintViolation(ConstraintViolationException e) {
        log.warn("参数校验失败: {}", e.getMessage());
        return ApiResult.fail(400, "参数校验失败: " + e.getMessage());
    }

    /**
     * 处理请求参数缺失异常 {@link MissingServletRequestParameterException}
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<?> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数: {}", e.getParameterName());
        return ApiResult.fail(400, "缺少必要参数: " + e.getParameterName());
    }

    /**
     * 处理 404 静态资源（如 favicon.ico、robots.txt）— 低级别日志
     * <p>
     * 这些请求由浏览器自动发起且不影响功能，用 DEBUG 级别避免污染 WARN/ERROR 日志。
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResult<?> handleNoResource(NoHandlerFoundException e) {
        log.debug("静态资源不存在: {}", e.getRequestURL());
        return ApiResult.fail(404, "资源不存在");
    }

    /**
     * 处理所有未预期的系统异常 — 兜底处理
     * <p>
     * 此 handler 确保任何未捕获的异常都以统一格式返回，避免直接暴露
     * 堆栈信息给客户端。同时以 ERROR 级别记录完整堆栈供运维排查。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResult<?> handleUnknownException(Exception e) {
        log.error("未预期系统异常: {}", e.getMessage(), e);
        return ApiResult.fail(500, "系统繁忙，请稍后重试");
    }
}
