package com.example.redis.c10_lua;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 10. Lua 脚本
 * <p>
 * Redis 通过内嵌 Lua 解释器（LuaJIT）支持脚本化执行。
 * <p>
 * 核心命令：
 * - EVAL script numkeys key [key ...] arg [arg ...]: 执行 Lua 脚本
 * - EVALSHA sha1 numkeys key [key ...] arg [arg ...]: 通过 SHA1 执行缓存的脚本
 * - SCRIPT LOAD script: 将脚本加载到服务器缓存，返回 SHA1
 * - SCRIPT EXISTS sha1 [sha1 ...]: 检查脚本是否已缓存
 * - SCRIPT FLUSH: 清空脚本缓存
 * - SCRIPT KILL: 终止正在运行的脚本（仅限无写操作的脚本）
 * <p>
 * Lua 脚本的优势：
 * 1. 原子性：脚本中的所有命令作为一个整体执行，不会被其他命令打断
 * 2. 灵活性：支持条件判断、循环等复杂逻辑
 * 3. 减少网络往返：多个命令合并为一次 EVAL
 * 4. 可复用：脚本缓存后通过 SHA1 调用，减少带宽
 * <p>
 * 与事务对比：
 * - 事务：MULTI/EXEC 保证原子执行，但不支持条件逻辑
 * - Lua：支持任意复杂逻辑，原子性更强
 * - Lua 脚本在执行期间会阻塞整个 Redis（单线程），应避免长时间运行
 * <p>
 * 限制：
 * - lua-time-limit 默认 5 秒超时
 * - 超时后 Redis 会接受 SCRIPT KILL 命令终止脚本
 * - 若脚本已执行写操作，只能用 SHUTDOWN NOSAVE 终止
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LuaDemo {

    private final StringRedisTemplate redisTemplate;

    /**
     * Lua 脚本基础：原子操作
     * <p>
     * 使用场景：分布式锁的释放
     * 仅当锁的 value 匹配时才删除（防止误删其他客户端的锁）
     * <p>
     * 这是分布式锁的标准做法：
     * 加锁: SET key uuid NX EX 30
     * 解锁: Lua 脚本判断 uuid 匹配后才 DEL
     */
    public String distributedLockLua() {
        var ops = redisTemplate.opsForValue();

        String lockKey = "lua:lock:order:1001";
        String lockValue = java.util.UUID.randomUUID().toString();

        // 加锁
        ops.setIfAbsent(lockKey, lockValue, 30, java.util.concurrent.TimeUnit.SECONDS);
        log.info("[Lua锁] 加锁: key={}, value={}", lockKey, lockValue);

        // 释放锁的 Lua 脚本
        // KEYS[1] = lock key
        // ARGV[1] = expected value
        // 返回 1 表示释放成功，0 表示 value 不匹配
        String unlockScript = """
                if redis.call('get', KEYS[1]) == ARGV[1] then
                    return redis.call('del', KEYS[1])
                else
                    return 0
                end
                """;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(unlockScript, Long.class);

        // 正确的 value → 释放成功
        Long result = redisTemplate.execute(script, Collections.singletonList(lockKey), lockValue);
        log.info("[Lua锁] 释放(正确value): result={}", result);

        // 再次加锁
        ops.setIfAbsent(lockKey, lockValue, 30, java.util.concurrent.TimeUnit.SECONDS);

        // 错误的 value → 释放失败
        Long result2 = redisTemplate.execute(script, Collections.singletonList(lockKey), "wrong-value");
        log.info("[Lua锁] 释放(错误value): result={}", result2);

        redisTemplate.delete(lockKey);
        return "释放结果=" + result + ", 错误释放=" + result2;
    }

    /**
     * Lua 脚本：原子库存扣减
     * <p>
     * 问题：非原子的 "检查+扣减" 存在竞态条件
     * 解决：Lua 脚本将检查与扣减合并为原子操作
     * <p>
     * 流程：
     * 1. GET 库存
     * 2. 判断是否 >= 扣减数量
     * 3. 是则 DECRBY，否则返回 -1
     */
    public String atomicStockDeduct() {
        var ops = redisTemplate.opsForValue();
        ops.set("lua:stock:item:1001", "10");

        // Lua 脚本：原子库存扣减
        String stockScript = """
                local stock = tonumber(redis.call('get', KEYS[1]))
                local quantity = tonumber(ARGV[1])
                if stock == nil then
                    return -1
                end
                if stock >= quantity then
                    redis.call('decrby', KEYS[1], quantity)
                    return stock - quantity
                else
                    return -1
                end
                """;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(stockScript, Long.class);

        // 扣减 3 个
        Long remaining = redisTemplate.execute(script,
                Collections.singletonList("lua:stock:item:1001"), "3");
        log.info("[Lua库存] 扣减3个, 剩余={}", remaining);

        // 扣减 8 个（库存不足）
        remaining = redisTemplate.execute(script,
                Collections.singletonList("lua:stock:item:1001"), "8");
        log.info("[Lua库存] 扣减8个, 结果={} (-1表示库存不足)", remaining);

        // 扣减 7 个（刚好用完）
        remaining = redisTemplate.execute(script,
                Collections.singletonList("lua:stock:item:1001"), "7");
        log.info("[Lua库存] 扣减7个, 剩余={}", remaining);

        redisTemplate.delete("lua:stock:item:1001");
        return "扣减演示完成";
    }

    /**
     * Lua 脚本：限流器（滑动窗口）
     * <p>
     * 使用 ZSET 实现滑动窗口限流：
     * 1. 删除窗口外的旧记录
     * 2. 统计窗口内请求数
     * 3. 若未超限，添加当前请求
     * 4. 返回是否允许
     * <p>
     * 整个过程在 Lua 中原子执行，无竞态条件。
     */
    public String rateLimiterLua() {
        // Lua 限流脚本
        String rateLimitScript = """
                local key = KEYS[1]
                local window = tonumber(ARGV[1])
                local max_requests = tonumber(ARGV[2])
                local now = tonumber(ARGV[3])

                -- 删除窗口外的记录
                redis.call('zremrangebyscore', key, 0, now - window)

                -- 统计当前窗口请求数
                local count = redis.call('zcard', key)

                if count < max_requests then
                    -- 未超限，添加请求
                    redis.call('zadd', key, now, now .. ':' .. math.random(1000000))
                    redis.call('expire', key, math.ceil(window / 1000))
                    return 1
                else
                    return 0
                end
                """;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(rateLimitScript, Long.class);

        String rateLimitKey = "lua:rate:user:1001";
        long windowMs = 10000;  // 10 秒窗口
        int maxRequests = 5;    // 最多 5 次

        // 模拟 7 次请求
        long now = System.currentTimeMillis();
        int allowed = 0;
        int rejected = 0;

        for (int i = 0; i < 7; i++) {
            Long result = redisTemplate.execute(script,
                    Collections.singletonList(rateLimitKey),
                    String.valueOf(windowMs),
                    String.valueOf(maxRequests),
                    String.valueOf(now + i * 100));

            if (result != null && result == 1) {
                allowed++;
                log.info("[Lua限流] 请求{}: 通过", i + 1);
            } else {
                rejected++;
                log.info("[Lua限流] 请求{}: 拒绝", i + 1);
            }
        }

        redisTemplate.delete(rateLimitKey);
        return "通过=" + allowed + ", 拒绝=" + rejected;
    }

    /**
     * Lua 脚本：原子 Hash 字段自增
     * <p>
     * 场景：多个字段需要原子更新
     * 例：同时增加阅读数和点赞数
     */
    public String atomicHashIncrement() {
        var ops = redisTemplate.opsForHash();
        ops.put("lua:article:1001", "views", "0");
        ops.put("lua:article:1001", "likes", "0");

        String script = """
                local key = KEYS[1]
                local views_inc = tonumber(ARGV[1])
                local likes_inc = tonumber(ARGV[2])

                local new_views = redis.call('hincrby', key, 'views', views_inc)
                local new_likes = redis.call('hincrby', key, 'likes', likes_inc)

                return {new_views, new_likes}
                """;

        DefaultRedisScript<List> scriptObj = new DefaultRedisScript<>(script, List.class);

        // 原子增加阅读数+10，点赞数+3
        List<?> result = redisTemplate.execute(scriptObj,
                Collections.singletonList("lua:article:1001"), "10", "3");
        log.info("[Lua Hash] views={}, likes={}", result.get(0), result.get(1));

        redisTemplate.delete("lua:article:1001");
        return "result=" + result;
    }
}
