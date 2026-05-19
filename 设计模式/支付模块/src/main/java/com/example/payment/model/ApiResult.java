package com.example.payment.model;

import java.util.Map;

public class ApiResult {
    private int code;
    private String message;
    private Map<String, Object> data;

    public static ApiResult ok(Map<String, Object> data) {
        ApiResult r = new ApiResult();
        r.code = 200;
        r.message = "success";
        r.data = data;
        return r;
    }

    public static ApiResult ok(String message, Map<String, Object> data) {
        ApiResult r = new ApiResult();
        r.code = 200;
        r.message = message;
        r.data = data;
        return r;
    }

    public static ApiResult fail(int code, String message) {
        ApiResult r = new ApiResult();
        r.code = code;
        r.message = message;
        return r;
    }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }
}
