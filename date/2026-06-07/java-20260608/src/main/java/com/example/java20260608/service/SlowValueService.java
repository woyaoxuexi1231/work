package com.example.java20260608.service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class SlowValueService {

    private final AtomicLong counter = new AtomicLong();

    public String compute(String key) {
        long n = counter.incrementAndGet();
        return key + "|v" + n + "|" + Instant.now().toString();
    }
}
