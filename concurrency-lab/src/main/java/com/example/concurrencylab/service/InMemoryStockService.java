package com.example.concurrencylab.service;

import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Component;

@Component
public class InMemoryStockService {
    private volatile int unsafeStock = 0;
    private volatile int lockedStock = 0;
    private final ReentrantLock lock = new ReentrantLock();

    public void reset(int stock) {
        this.unsafeStock = stock;
        this.lockedStock = stock;
    }

    public boolean buyUnsafe(int qty) {
        int cur = unsafeStock;
        if (cur < qty) {
            return false;
        }
        unsafeStock = cur - qty;
        return true;
    }

    public boolean buyLocked(int qty) {
        lock.lock();
        try {
            if (lockedStock < qty) {
                return false;
            }
            lockedStock -= qty;
            return true;
        } finally {
            lock.unlock();
        }
    }

    public int getUnsafeStock() {
        return unsafeStock;
    }

    public int getLockedStock() {
        return lockedStock;
    }
}
