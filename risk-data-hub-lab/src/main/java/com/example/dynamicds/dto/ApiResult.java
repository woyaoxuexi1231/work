package com.example.dynamicds.dto;

import lombok.Data;

/**
 * 统一 API 响应封装。
 * <p>
 * <b>为什么要有 ApiResult？</b><br>
 * 统一响应格式，前端可以按统一结构解析：
 * <pre>{ "code": 200, "message": "success", "status": "OK", "data": {...} }</pre>
 * 200 表示成功，非 200 表示业务异常。
 * status 字段提供详细的状态描述。
 * <p>
 * <b>静态工厂方法 vs new ApiResult()</b><br>
 * {@code ApiResult.ok(data)} 和 {@code ApiResult.getFail(code, msg)} 是静态工厂方法，
 * 比构造器更语义化：看到 {@code ok()} 就知道是成功，看到 {@code fail()} 就知道是失败。
 * 这也是 Effective Java 推荐的"用静态工厂方法替代构造器"原则。
 * <p>
 * <b>为什么用泛型 {@code <T>}？</b><br>
 * 编译期类型检查：{@code ApiResult&lt;List&lt;DataSourceVO&gt;&gt;} 保证 data 字段的类型安全，
 * Controller 返回时无需手动 cast。
 */
@Data
public class ApiResult<T> {
    private int code;
    private String message;
    private String status;
    private T data;

    /** 成功（有返回数据）- 默认状态描述 */
    public static <T> ApiResult<T> ok(T data) {
        ApiResult<T> r = new ApiResult<>();
        r.code = 200;
        r.message = "操作成功";
        r.status = "SUCCESS";
        r.data = data;
        return r;
    }

    /** 成功（有返回数据）- 自定义状态描述 */
    public static <T> ApiResult<T> ok(T data, String status) {
        ApiResult<T> r = new ApiResult<>();
        r.code = 200;
        r.message = "操作成功";
        r.status = status;
        r.data = data;
        return r;
    }

    /** 成功（无返回数据，如 POST 注册/删除后） */
    public static <T> ApiResult<T> ok() {
        return ok(null, "SUCCESS");
    }

    /** 成功（无返回数据）- 自定义状态描述 */
    public static <T> ApiResult<T> ok(String status) {
        return ok(null, status);
    }

    /** 失败 — code 使用 HTTP 语义（400/404/409/500） */
    public static <T> ApiResult<T> fail(int code, String message) {
        ApiResult<T> r = new ApiResult<>();
        r.code = code;
        r.message = message;
        r.status = "FAILED";
        return r;
    }

    /** 失败 - 自定义状态描述 */
    public static <T> ApiResult<T> fail(int code, String message, String status) {
        ApiResult<T> r = new ApiResult<>();
        r.code = code;
        r.message = message;
        r.status = status;
        return r;
    }
}
