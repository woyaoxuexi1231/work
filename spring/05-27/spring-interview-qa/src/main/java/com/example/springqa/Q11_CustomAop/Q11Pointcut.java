package com.example.springqa.Q11_CustomAop;

import java.lang.reflect.Method;

interface Q11Pointcut {
    boolean matches(Method method, Class<?> targetClass);
}
