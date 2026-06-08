package com.example.id20260608.snowflake;

/**
 * 3. 快速失败策略 Snowflake —— 检测到回拨立即抛出异常
 *
 * 适用场景: 金融/交易系统，对ID唯一性要求极高，宁可拒绝服务也不冒重复风险。
 */
public class FastFailSnowflake {

    private static final long TWEPOCH = 1735689600000L;
    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    private final long workerId;
    private final long datacenterId;

    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public FastFailSnowflake(long workerId, long datacenterId) {
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException("workerId 必须在 0 ~ " + MAX_WORKER_ID + " 之间");
        }
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException("datacenterId 必须在 0 ~ " + MAX_DATACENTER_ID + " 之间");
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    public synchronized long nextId() {
        long currentTimestamp = System.currentTimeMillis();

        // 严格检测：任何时候检测到回拨都快速失败
        if (currentTimestamp < lastTimestamp) {
            long backwardMs = lastTimestamp - currentTimestamp;
            throw new ClockBackwardsException(
                String.format("[快速失败] 检测到时钟回拨 %d ms，立即拒绝生成ID！"
                              + " 上一个时间戳=%d, 当前时间戳=%d",
                              backwardMs, lastTimestamp, currentTimestamp),
                backwardMs, lastTimestamp, currentTimestamp);
        }

        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                currentTimestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = currentTimestamp;

        return ((currentTimestamp - TWEPOCH) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}
