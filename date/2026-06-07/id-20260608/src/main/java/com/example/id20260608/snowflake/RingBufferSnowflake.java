package com.example.id20260608.snowflake;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * 4. RingBuffer 缓存策略 Snowflake —— 借鉴百度 UidGenerator 思路
 *
 * 核心思想: 预生成一批ID放入环形缓冲区，时钟回拨时直接从缓冲区取已生成的ID继续提供服务。
 */
public class RingBufferSnowflake {

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

    // 环形缓冲区
    private final long[] ringBuffer;
    private final int bufferMask;
    private volatile int readIndex = 0;
    private volatile int writeIndex = 0;
    private volatile int available = 0;

    // 异步填充线程
    private final Thread fillerThread;
    private volatile boolean running = true;

    public RingBufferSnowflake(long workerId, long datacenterId, int bufferSize) {
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException("workerId 必须在 0 ~ " + MAX_WORKER_ID + " 之间");
        }
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException("datacenterId 必须在 0 ~ " + MAX_DATACENTER_ID + " 之间");
        }
        // bufferSize 必须是 2的幂
        int size = 1;
        while (size < bufferSize) {
            size <<= 1;
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
        this.ringBuffer = new long[size];
        this.bufferMask = size - 1;

        // 启动后台填充线程
        this.fillerThread = new Thread(this::fillBuffer, "ringbuffer-filler");
        this.fillerThread.setDaemon(true);
        this.fillerThread.start();
    }

    /**
     * 从环形缓冲区获取ID（无锁CAS消费）
     */
    public long nextId() {
        // 自旋等待可用ID
        while (available <= 0) {
            if (!running && available <= 0) {
                // buffer关闭且无可用ID，降级为直接生成
                return generateIdDirectly();
            }
            Thread.yield();
        }

        // CAS消费
        int currentRead;
        long id = 0;
        do {
            currentRead = readIndex;
            if (available <= 0) {
                continue;
            }
            id = ringBuffer[currentRead];
        } while (!compareAndSwapRead(currentRead, (currentRead + 1) & bufferMask, 1));

        return id;
    }

    /**
     * 缓冲填充逻辑
     */
    private void fillBuffer() {
        while (running) {
            if (available >= ringBuffer.length / 2) {
                // 缓冲充足时休眠，避免空转
                LockSupport.parkNanos(1_000_000L); // 1ms
                continue;
            }
            // 批量填充
            for (int i = 0; i < 100 && available < ringBuffer.length && running; i++) {
                long id = generateIdDirectly();
                int currentWrite = writeIndex;
                ringBuffer[currentWrite] = id;
                // CAS更新写指针
                if (compareAndSwapWrite(currentWrite, (currentWrite + 1) & bufferMask, id)) {
                    // 成功写入，增加可用计数
                    synchronized (this) {
                        available++;
                    }
                }
            }
        }
    }

    private synchronized long generateIdDirectly() {
        long currentTimestamp = System.currentTimeMillis();

        if (currentTimestamp < lastTimestamp) {
            long offset = lastTimestamp - currentTimestamp;
            if (offset <= 5) {
                try {
                    Thread.sleep(offset);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                currentTimestamp = System.currentTimeMillis();
            } else {
                throw new RuntimeException(
                    String.format("时钟回拨 %d ms，RingBuffer也拒绝生成ID", offset));
            }
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

    // ---------- CAS操作 ----------
    private static final Unsafe U;
    private static final long READ_INDEX_OFFSET;
    private static final long WRITE_INDEX_OFFSET;

    static {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            U = (Unsafe) field.get(null);
            READ_INDEX_OFFSET = U.objectFieldOffset(
                RingBufferSnowflake.class.getDeclaredField("readIndex"));
            WRITE_INDEX_OFFSET = U.objectFieldOffset(
                RingBufferSnowflake.class.getDeclaredField("writeIndex"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean compareAndSwapRead(int expect, int update, int consumeCount) {
        if (U.compareAndSwapInt(this, READ_INDEX_OFFSET, expect, update)) {
            synchronized (this) {
                available -= consumeCount;
            }
            return true;
        }
        return false;
    }

    private boolean compareAndSwapWrite(int expect, int update, long id) {
        return U.compareAndSwapInt(this, WRITE_INDEX_OFFSET, expect, update);
    }

    public void shutdown() {
        running = false;
    }

    public int getAvailable() {
        return available;
    }
}
