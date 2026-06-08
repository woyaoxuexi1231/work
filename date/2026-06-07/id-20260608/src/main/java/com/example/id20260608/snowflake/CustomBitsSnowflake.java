package com.example.id20260608.snowflake;

/**
 * 5. 自定义位分配 Snowflake —— 满足不同业务场景的位分配需求
 *
 * 允许自定义 时间戳位数 / 机器位数 / 序列号位数，
 * 适应"机器多但并发低" 或 "机器少但并发极高" 等不同场景。
 */
public class CustomBitsSnowflake {

    private final long twepoch;

    /** 各位段位数 */
    private final long timestampBits;
    private final long workerIdBits;
    private final long sequenceBits;

    /** 各位段最大值 */
    private final long maxWorkerId;
    private final long maxSequence;

    /** 各位段左移偏移量 */
    private final long workerIdShift;
    private final long timestampShift;

    /** workerId + datacenterId 合并为统一机器位 */
    private final long workerId;

    private long sequence = 0L;
    private long lastTimestamp = -1L;

    /**
     * @param workerId      机器ID (workerId + datacenterId 合并)
     * @param twepoch       自定义起始时间戳
     * @param timestampBits 时间戳占用位数
     * @param workerIdBits  机器ID占用位数
     * @param sequenceBits  序列号占用位数
     *                       总位数 = 1(符号位) + timestampBits + workerIdBits + sequenceBits 必须 ≤ 64
     */
    public CustomBitsSnowflake(long workerId, long twepoch,
                               long timestampBits, long workerIdBits, long sequenceBits) {
        long totalBits = 1 + timestampBits + workerIdBits + sequenceBits;
        if (totalBits > 64) {
            throw new IllegalArgumentException(
                "总位数超过64位: " + totalBits + " (符号1 + 时间戳" + timestampBits
                + " + 机器" + workerIdBits + " + 序列" + sequenceBits + ")");
        }

        this.twepoch = twepoch;
        this.timestampBits = timestampBits;
        this.workerIdBits = workerIdBits;
        this.sequenceBits = sequenceBits;

        this.maxWorkerId = ~(-1L << workerIdBits);
        this.maxSequence = ~(-1L << sequenceBits);

        this.workerIdShift = sequenceBits;
        this.timestampShift = sequenceBits + workerIdBits;

        if (workerId > maxWorkerId || workerId < 0) {
            throw new IllegalArgumentException(
                "workerId 必须在 0 ~ " + maxWorkerId + " 之间，当前: " + workerId);
        }
        this.workerId = workerId;
    }

    public synchronized long nextId() {
        long currentTimestamp = System.currentTimeMillis();

        if (currentTimestamp < lastTimestamp) {
            throw new RuntimeException(
                String.format("时钟回拨 %d ms，拒绝生成ID", lastTimestamp - currentTimestamp));
        }

        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & maxSequence;
            if (sequence == 0) {
                currentTimestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = currentTimestamp;

        long timestampDiff = currentTimestamp - twepoch;
        // 检查时间戳是否溢出
        long maxTimestamp = ~(-1L << timestampBits);
        if (timestampDiff > maxTimestamp) {
            throw new RuntimeException(
                "时间戳溢出！当前差值=" + timestampDiff + "，最大可表示=" + maxTimestamp);
        }

        return (timestampDiff << timestampShift)
                | (workerId << workerIdShift)
                | sequence;
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }

    // ---- 信息查询 ----
    public String getBitLayout() {
        return String.format(
            "位分配: [符号1位] [时间戳%d位] [机器%d位] [序列号%d位] = 共%d位",
            timestampBits, workerIdBits, sequenceBits, 1 + timestampBits + workerIdBits + sequenceBits);
    }

    public long getMaxTimestamp() {
        return ~(-1L << timestampBits);
    }

    public long getMaxWorkerId() {
        return maxWorkerId;
    }

    public long getMaxSequence() {
        return maxSequence;
    }
}
