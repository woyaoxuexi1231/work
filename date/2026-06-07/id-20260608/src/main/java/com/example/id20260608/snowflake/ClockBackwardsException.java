package com.example.id20260608.snowflake;

/**
 * 时钟回拨异常
 */
public class ClockBackwardsException extends RuntimeException {

    private final long backwardMs;
    private final long lastTimestamp;
    private final long currentTimestamp;

    public ClockBackwardsException(String message, long backwardMs,
                                   long lastTimestamp, long currentTimestamp) {
        super(message);
        this.backwardMs = backwardMs;
        this.lastTimestamp = lastTimestamp;
        this.currentTimestamp = currentTimestamp;
    }

    public long getBackwardMs() {
        return backwardMs;
    }

    public long getLastTimestamp() {
        return lastTimestamp;
    }

    public long getCurrentTimestamp() {
        return currentTimestamp;
    }
}
