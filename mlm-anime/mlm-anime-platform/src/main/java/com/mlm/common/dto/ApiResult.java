package com.mlm.common.dto;

import lombok.Getter;

/**
 * 统一 HTTP 响应体 — 所有 REST 接口的标准返回格式
 * <p>
 * 【code 约定】
 * <ul>
 *   <li>200 — 业务成功</li>
 *   <li>400 — 请求参数错误</li>
 *   <li>401 — 未登录/Token 无效</li>
 *   <li>403 — 无操作权限</li>
 *   <li>404 — 资源不存在</li>
 *   <li>500 — 系统异常</li>
 *   <li>非 200 的其它值 — 特定业务异常（见 {@link com.mlm.common.constant.ErrorCode}）</li>
 * </ul>
 * <p>
 * 前端根据 code 判断是否弹窗或跳转错误页。
 * status 字段提供业务层面的状态描述（如 "SUCCESS"、"SCRIPT_SUBMITTED"），
 * 前端可根据 status 进行分支处理。
 *
 * @param <T> data 字段的数据类型
 * @author mlm
 */
@Getter
public class ApiResult<T> {

    /** 状态码：200=成功，非200=业务异常 */
    private final int code;

    /** 提示信息（给人看的） */
    private final String message;

    /** 业务状态描述（给前端判断用的，如 "SCRIPT_SUBMITTED"） */
    private final String status;

    /** 响应数据 */
    private final T data;

    /**
     * 构造 API 响应
     *
     * @param code    状态码
     * @param message 提示信息
     * @param status  业务状态描述
     * @param data    响应数据
     */
    private ApiResult(int code, String message, String status, T data) {
        this.code = code;
        this.message = message;
        this.status = status;
        this.data = data;
    }

    /**
     * 成功响应（有返回数据）
     *
     * @param data 响应数据
     * @param <T>  数据类型
     * @return API 响应
     */
    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(200, "操作成功", "SUCCESS", data);
    }

    /**
     * 成功响应（有返回数据 + 自定义业务状态描述）
     *
     * @param data   响应数据
     * @param status 业务状态描述
     * @param <T>    数据类型
     * @return API 响应
     */
    public static <T> ApiResult<T> ok(T data, String status) {
        return new ApiResult<>(200, "操作成功", status, data);
    }

    /**
     * 成功响应（无返回数据）
     *
     * @param <T> 数据类型
     * @return API 响应
     */
    public static <T> ApiResult<T> ok() {
        return new ApiResult<>(200, "操作成功", "SUCCESS", null);
    }

    /**
     * 成功响应（无返回数据 + 自定义业务状态描述）
     *
     * @param status 业务状态描述
     * @param <T>    数据类型
     * @return API 响应
     */
    public static <T> ApiResult<T> ok(String status) {
        return new ApiResult<>(200, "操作成功", status, null);
    }

    /**
     * 业务失败响应
     *
     * @param code    错误码
     * @param message 错误描述
     * @param <T>     数据类型
     * @return API 响应
     */
    public static <T> ApiResult<T> fail(int code, String message) {
        return new ApiResult<>(code, message, "FAILED", null);
    }

    /**
     * 业务失败响应（自定义业务状态描述）
     *
     * @param code    错误码
     * @param message 错误描述
     * @param status  业务状态描述
     * @param <T>     数据类型
     * @return API 响应
     */
    public static <T> ApiResult<T> fail(int code, String message, String status) {
        return new ApiResult<>(code, message, status, null);
    }
}
