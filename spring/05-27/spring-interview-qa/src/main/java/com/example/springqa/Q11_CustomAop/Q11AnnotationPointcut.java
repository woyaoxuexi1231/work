package com.example.springqa.Q11_CustomAop;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

class Q11AnnotationPointcut implements Q11Pointcut {

    private final Class<? extends Annotation> type;

    Q11AnnotationPointcut(Class<? extends Annotation> type) {
        this.type = type;
    }

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        return method.isAnnotationPresent(type);
    }
}
