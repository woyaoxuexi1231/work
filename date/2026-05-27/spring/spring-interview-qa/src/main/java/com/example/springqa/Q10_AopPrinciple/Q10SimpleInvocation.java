package com.example.springqa.Q10_AopPrinciple;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.List;

public class Q10SimpleInvocation implements MethodInvocation {

    private final List<MethodInterceptor> interceptors;
    private int idx = -1;

    public Q10SimpleInvocation(List<MethodInterceptor> interceptors) {
        this.interceptors = interceptors;
    }

    @Override
    public Object proceed() throws Throwable {
        if (++idx >= interceptors.size()) return "target-result";
        return interceptors.get(idx).invoke(this);
    }

    @Override public Method getMethod() { return null; }
    @Override public Object[] getArguments() { return new Object[0]; }
    @Override public Object getThis() { return this; }
    @Override public AccessibleObject getStaticPart() { return null; }
}
