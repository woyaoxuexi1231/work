package com.riskdatahub.common.exception;

import com.riskdatahub.common.result.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolationException;
import java.util.stream.Collectors;

/**
 * 全局异常处理器 — 统一捕获各层异常并转换为 {@link ApiResult} 响应。
 * <p>
 * 使用 {@link RestControllerAdvice} 全局生效，覆盖三大类异常：
 * <ul>
 *   <li>{@link BusinessException} — 业务异常，保留其携带的 HTTP 状态码和业务码</li>
 *   <li>{@link MethodArgumentNotValidException} / {@link ConstraintViolationException} — 参数校验失败</li>
 *   <li>{@link Exception} — 未预料的系统异常，返回 500 并记录 error 日志</li>
 * </ul>
 * </p>
 *
 * @author risk-data-hub
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常，保留异常的 HTTP 状态码和业务码。
     *
     * @param e 业务异常
     * @return 包含业务码的错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public ApiResult<Void> handleBusinessException(BusinessException e) {
        log.warn("[业务异常] httpCode={}, statusCode={}, message={}", e.getHttpCode(), e.getStatusCode(), e.getMessage());
        return ApiResult.fail(e.getHttpCode(), e.getMessage(), e.getStatusCode());
    }

    /**
     * 处理 {@code @Valid} 参数校验失败异常，提取首个校验失败信息。
     *
     * @param e 参数校验异常
     * @return 400 错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("[参数校验失败] {}", message);
        return ApiResult.fail(400, message, "VALIDATION_FAILED");
    }

    /**
     * 处理 {@code @NotBlank} / {@code @NotNull} 等声明式校验失败。
     *
     * @param e 约束违反异常
     * @return 400 错误响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<Void> handleConstraintViolation(ConstraintViolationException e) {
        log.warn("[约束校验失败] {}", e.getMessage());
        return ApiResult.fail(400, e.getMessage(), "VALIDATION_FAILED");
    }

    /**
     * 处理未预料的系统异常，返回 500 并记录完整堆栈。
     *
     * @param e 系统异常
     * @return 500 错误响应
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResult<Void> handleException(Exception e) {
        log.error("[系统异常] {}", e.getMessage(), e);
        return ApiResult.fail(500, "系统内部错误: " + e.getMessage(), "INTERNAL_ERROR");
    }
}
