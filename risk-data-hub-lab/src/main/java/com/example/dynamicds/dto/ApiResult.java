package com.example.dynamicds.dto;

import lombok.Data;

/**
 * 统一 API 响应封装。
 * <p>
 * <b>为什么要有 ApiResult？</b><br>
 * 统一响应格式，前端可以按统一结构解析：
 * <pre>{ "code": 200, "message": "success", "data": {...} }</pre>
 * 200 表示成功，非 200 表示业务异常。
 * 前端判断 {@code if (res.code === 200)} 比 try-catch 解析异常要可靠。
 * <p>
 * <b>静态工厂方法 vs new ApiResult()</b><br>
 * {@code ApiResult.ok(data)} 和 {@code ApiResult.fail(code, msg)} 是静态工厂方法，
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
    private T data;

    /** 成功（有返回数据） */
    public static <T> ApiResult<T> ok(T data) {
        ApiResult<T> r = new ApiResult<>();
        r.code = 200;
        r.message = "success";
        r.data = data;
        return r;
    }

    /** 成功（无返回数据，如 POST 注册/删除后） */
    public static <T> ApiResult<T> ok() {
        return ok(null);
    }

    /** 失败 — code 使用 HTTP 语义（400/404/409/500） */
    public static <T> ApiResult<T> fail(int code, String message) {
        ApiResult<T> r = new ApiResult<>();
        r.code = code;
        r.message = message;
        return r;
    }
}
