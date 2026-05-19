package com.example.redissonlocklab.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InstanceId {
    private final String value;

    public InstanceId(@Value("${app.instance-id}") String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}

