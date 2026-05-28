package com.example.rsa.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NonceService {
    private static final long REPLAY_WINDOW_MS = 5 * 60 * 1000L;
    private static final long NONCE_TTL_MS = 10 * 60 * 1000L;

    private final ConcurrentHashMap<String, Long> usedNonceExpireAt = new ConcurrentHashMap<>();

    public long getReplayWindowMs() {
        return REPLAY_WINDOW_MS;
    }

    public boolean isTimestampWithinWindow(Long timestamp, long nowMs) {
        if (timestamp == null) {
            return false;
        }
        return Math.abs(nowMs - timestamp) <= REPLAY_WINDOW_MS;
    }

    public boolean consumeNonce(String nonce, long nowMs) {
        if (StringUtils.hasLength(nonce)) {
            return false;
        }
        cleanup(nowMs);
        Long expireAt = nowMs + NONCE_TTL_MS;
        return usedNonceExpireAt.putIfAbsent(nonce, expireAt) == null;
    }

    private void cleanup(long nowMs) {
        Iterator<Map.Entry<String, Long>> iterator = usedNonceExpireAt.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (entry.getValue() <= nowMs) {
                iterator.remove();
            }
        }
    }
}
