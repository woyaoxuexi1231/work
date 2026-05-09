package work.N1javabasic.old.day14;

import java.util.concurrent.atomic.AtomicLong;

class RateLimiter {

    // ============================================== 令牌桶的一些参数配置 ============================================
    // 容量（令牌数），这个是令牌的使用单位、扣除按此单位进行扣除
    private final long maxTokens;
    // 每秒生成令牌数，这个是用于控制每秒生成的令牌总数 refillRate
    private final long refillRate;
    // 精度缩放：1令牌 = 1000毫令牌，这里用了一点巧妙的方法来使得令牌补充和扣除精度更高，更方便
    private static final long SCALE = 1000;

    // ============================================== 令牌桶的状态 ============================================
    // 当前令牌数，单位：毫令牌（即实际令牌数 × 1000）
    private final AtomicLong currentTokens;
    // 上次补充时间，单位：毫秒，这个用于知道下一次获取令牌时，从哪开始补充令牌，补充多少令牌
    private final AtomicLong lastRefillTime;

    public RateLimiter(long maxTokens, long refillRate) {
        this.maxTokens = maxTokens;
        this.refillRate = refillRate;
        // 初始时桶满
        this.currentTokens = new AtomicLong(maxTokens * SCALE);
        this.lastRefillTime = new AtomicLong(System.currentTimeMillis());
    }

    public boolean tryAcquire() {

        /*
        令牌桶算法的整体流程：
        1.  计算经过的时间（处理时钟回拨）
        2.  生成毫令牌数 = 经过毫秒 × 每秒令牌数（refillRate）
        3.  补充后令牌数，不能超过容量
        4.  令牌不足（需要1个令牌 = 1000毫令牌）
        5.  CAS 扣减 1 令牌
        6.  返回获取结果

        令牌的补充属于一个主动触发的逻辑，而并不是说后台就把令牌补充好了，而是由用户主动获取令牌时，才补充令牌。
         */


        long now = System.currentTimeMillis();
        while (true) {
            long lastTime = lastRefillTime.get();
            long tokens = currentTokens.get();

            // 1. 计算经过的时间（处理时钟回拨）
            long elapsed = Math.max(0, now - lastTime);
            // 2. 生成毫令牌数 = 经过毫秒 × 每秒令牌数（refillRate）
            long generated = elapsed * refillRate;
            // 3. 补充后令牌数，不能超过容量
            long newTokens = Math.min(tokens + generated, maxTokens * SCALE);

            // 4. 令牌不足（需要1个令牌 = 1000毫令牌）
            if (newTokens < SCALE) {
                // 令牌不足，但需要保存生成的零头，同时更新时间
                if (currentTokens.compareAndSet(tokens, newTokens)) {
                    // 成功保存零头，更新最后补充时间
                    updateLastRefillTime(now, lastTime);
                }
                // CAS 失败则说明被其他线程修改，重试
                return false;
            }

            // 5. CAS 扣减 1 令牌
            if (currentTokens.compareAndSet(tokens, newTokens - SCALE)) {
                // 成功获取，尝试更新最后补充时间
                updateLastRefillTime(now, lastTime);
                return true;
            }
            // CAS 失败，说明有并发修改，重试
        }
    }

    /**
     * 仅当 lastRefillTime 小于当前时间时，将它更新为当前时间。
     * 保证时间单调递增，防止被旧线程写回过去的时间。
     */
    private void updateLastRefillTime(long now, long expected) {
        // 对比并交换，预期值为 expected，新值为 now（仅当 expected < now 时才有意义）
        // 如果当前值已经比 now 大（被其他线程更新过），则放弃本次更新
        while (expected < now && !lastRefillTime.compareAndSet(expected, now)) {
            expected = lastRefillTime.get();
            // 若发现值已 ≥ now，直接退出
            if (expected >= now) {
                break;
            }
        }
    }
}