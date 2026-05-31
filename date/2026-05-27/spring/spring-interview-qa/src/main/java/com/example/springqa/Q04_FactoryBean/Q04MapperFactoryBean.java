package com.example.springqa.Q04_FactoryBean;

import org.springframework.beans.factory.FactoryBean;

import java.lang.reflect.Proxy;

public class Q04MapperFactoryBean<T> implements FactoryBean<T> {

    private final Class<T> mapperInterface;

    public Q04MapperFactoryBean(Class<T> mapperInterface) {
        this.mapperInterface = mapperInterface;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T getObject() {
        // org.mybatis.spring.mapper.MapperFactoryBean
        return (T) Proxy.newProxyInstance(
                mapperInterface.getClassLoader(),
                new Class<?>[]{mapperInterface},
                new Q04MapperProxy());
    }

    @Override public Class<?> getObjectType() { return mapperInterface; }

    @Override public boolean isSingleton() { return true; }
}
