package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一 API 响应体
 *
 * <p>配合 {@code ResponseBodyAdvice} 使用，把所有 Controller 的返回值
 * 自动包装成 { code, message, data } 格式。
 *
 * <p>示例输出：
 * <pre>
 * {
 *   "code": 200,
 *   "message": "success",
 *   "data": { "id": 1, "name": "张三", "email": "zhangsan@example.com" }
 * }
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)  // null 字段不序列化
public class ApiResult<T> {

    private int code;
    private String message;
    private T data;

    // ---------- 工厂方法 ----------

    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(200, "success", data);
    }

    public static <T> ApiResult<T> error(int code, String message) {
        return new ApiResult<>(code, message, null);
    }
}
