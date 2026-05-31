package com.example.springqa.Q16_MvcRequestFlow.controlleradvice;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;
    // 构造方法、getter/setter 省略...
    
    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> resp = new ApiResponse<>();
        resp.setCode(200);
        resp.setMessage("success");
        resp.setData(data);
        return resp;
    }
    
    public static ApiResponse<?> error(int code, String msg) {
        ApiResponse<?> resp = new ApiResponse<>();
        resp.setCode(code);
        resp.setMessage(msg);
        return resp;
    }
}