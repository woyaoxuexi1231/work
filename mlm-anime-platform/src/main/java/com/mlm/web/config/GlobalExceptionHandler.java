package com.mlm.web.config;

import com.mlm.common.dto.ApiResult;
import com.mlm.common.exception.BizException;
import com.mlm.common.exception.PipelineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * 全局异常处理器 — 将未捕获的异常转为统一 {@link ApiResult} 格式
 * <p>
 * 按异常类型分级处理：
 * <ul>
 *   <li>{@link BizException} — 业务异常，400 + 自定义 code</li>
 *   <li>{@link PipelineException} — Pipeline 状态异常，400</li>
 *   <li>{@link NoHandlerFoundException} — 静态资源 404（如 favicon.ico），静默处理</li>
 *   <li>其它 Exception — 500 系统异常</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<?> handleBiz(BizException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return ApiResult.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(PipelineException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<?> handlePipeline(PipelineException e) {
        log.warn("Pipeline异常: {}", e.getMessage());
        return ApiResult.fail(400, e.getMessage());
    }

    /**
     * 静态资源 404（如 favicon.ico / robots.txt）— 低级别日志，不污染 ERROR 日志
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResult<?> handleNoResource(NoHandlerFoundException e) {
        log.debug("静态资源不存在: {}", e.getRequestURL());
        return ApiResult.fail(404, "资源不存在");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResult<?> handleOther(Exception e) {
        log.error("系统异常: {}", e.getMessage(), e);
        return ApiResult.fail(500, "系统异常: " + e.getMessage());
    }
}
