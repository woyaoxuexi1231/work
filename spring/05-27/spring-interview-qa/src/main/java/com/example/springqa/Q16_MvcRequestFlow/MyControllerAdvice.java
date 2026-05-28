package com.example.springqa.Q16_MvcRequestFlow;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.HashMap;
import java.util.Map;

/**
 * @author hulei
 * @since 2026/5/28 23:14
 */

@ControllerAdvice("com.example.springqa.Q16_MvcRequestFlow")
public class MyControllerAdvice {

    // 兜底：处理所有未捕获的异常
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public Map<String, String> handleGeneric(Exception ex) {
        Map<String, String> map = new HashMap<>();
        map.put("error", "服务器内部错误: " + ex.getMessage());
        return map;
    }
}
