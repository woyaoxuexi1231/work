package com.example.java20260608.springcache;

import com.google.common.cache.Cache;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import org.springframework.cache.support.NullValue;
import org.springframework.cache.support.SimpleValueWrapper;

public class GuavaSpringCache implements org.springframework.cache.Cache {

    private final String name;
    private final Cache<Object, Object> cache;

    public GuavaSpringCache(String name, Cache<Object, Object> cache) {
        this.name = name;
        this.cache = cache;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        return cache;
    }

    @Override
    public ValueWrapper get(Object key) {
        Object value = cache.getIfPresent(key);
        if (value == null) {
            return null;
        }
        return new SimpleValueWrapper(fromStoreValue(value));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Class<T> type) {
        Object value = cache.getIfPresent(key);
        if (value == null) {
            return null;
        }
        Object userValue = fromStoreValue(value);
        if (type != null && userValue != null && !type.isInstance(userValue)) {
            throw new IllegalStateException("Cached value is not of required type [" + type.getName() + "]: " + userValue);
        }
        return (T) userValue;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Callable<T> valueLoader) {
        try {
            Object value = cache.get(key, () -> toStoreValue(valueLoader.call()));
            return (T) fromStoreValue(value);
        } catch (ExecutionException e) {
            throw new ValueRetrievalException(key, valueLoader, e.getCause());
        } catch (Exception e) {
            throw new ValueRetrievalException(key, valueLoader, e);
        }
    }

    @Override
    public void put(Object key, Object value) {
        cache.put(key, toStoreValue(value));
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        Object existing = cache.asMap().putIfAbsent(key, toStoreValue(value));
        if (existing == null) {
            return null;
        }
        return new SimpleValueWrapper(fromStoreValue(existing));
    }

    @Override
    public void evict(Object key) {
        cache.invalidate(key);
    }

    @Override
    public void clear() {
        cache.invalidateAll();
    }

    private static Object toStoreValue(Object userValue) {
        return userValue == null ? NullValue.INSTANCE : userValue;
    }

    private static Object fromStoreValue(Object storeValue) {
        return storeValue == NullValue.INSTANCE ? null : storeValue;
    }
}
