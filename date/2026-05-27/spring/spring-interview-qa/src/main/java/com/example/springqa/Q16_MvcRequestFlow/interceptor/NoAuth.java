package com.example.springqa.Q16_MvcRequestFlow.interceptor;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface NoAuth {
    // 可加属性，例如 requiredRole 等，但本示例只需要标记
}