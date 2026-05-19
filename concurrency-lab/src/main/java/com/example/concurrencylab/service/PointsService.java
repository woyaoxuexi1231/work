package com.example.concurrencylab.service;

import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Component;

@Component
public class PointsService {
    private Integer unsafePoints = 0;
    private final LongAdder safePoints = new LongAdder();

    public void reset() {
        unsafePoints = 0;
        safePoints.reset();
    }

    public void addUnsafe(int delta) {
        unsafePoints = unsafePoints + delta;
    }

    public void addSafe(int delta) {
        safePoints.add(delta);
    }

    public int getUnsafePoints() {
        return unsafePoints;
    }

    public long getSafePoints() {
        return safePoints.sum();
    }
}
