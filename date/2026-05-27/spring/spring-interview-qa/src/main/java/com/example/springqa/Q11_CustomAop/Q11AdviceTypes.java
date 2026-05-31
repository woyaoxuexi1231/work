package com.example.springqa.Q11_CustomAop;

import java.lang.reflect.Method;

interface Q11BeforeAdvice extends Q11Advice {
    void before(Method method, Object[] args, Object target);
}

interface Q11AfterAdvice extends Q11Advice {
    void after(Method method, Object[] args, Object target, Object result);
}

interface Q11AroundAdvice extends Q11Advice {
    Object around(Method method, Object[] args, Object target, Q11MethodInvoker invoker) throws Throwable;
}

interface Q11MethodInvoker {
    Object invoke() throws Throwable;
}
