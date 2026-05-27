package com.example.redis.c04_typecmds;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 4. 各类型核心命令 —— String
 * <p>
 * String 是 Redis 最基础的数据类型，二进制安全，最大存储 512MB。
 * <p>
 * 核心命令：
 * - 基础读写：SET、GET、SETNX、SETEX、MSET、MGET
 * - 原子计数：INCR、INCRBY、DECR、DECRBY、INCRBYFLOAT
 * - 字符串操作：APPEND、GETRANGE、SETRANGE、STRLEN
 * <p>
 * 典型应用场景：
 * - 缓存：序列化对象后存储
 * - 计数器：页面 PV、点赞数（INCR 原子自增）
 * - 分布式锁：SETNX + EXPIRE（推荐用 SET key value NX EX）
 * - 限流：INCR + EXPIRE 实现滑动窗口
 * - 位图：SETBIT/GETBIT 实现签到、在线状态
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StringCmdDemo {

    private final StringRedisTemplate redisTemplate;

    /**
     * 基础读写命令
     * <p>
     * SET key value [EX seconds] [PX milliseconds] [NX|XX]
     *   - EX: 设置过期时间（秒）
     *   - PX: 设置过期时间（毫秒）
     *   - NX: 仅当键不存在时才设置（分布式锁核心）
     *   - XX: 仅当键已存在时才设置（更新场景）
     * <p>
     * MSET / MGET: 批量操作，减少网络往返
     * 注意：MSET 是原子的，要么全成功要么全失败
     */
    public String basicOps() {
        var ops = redisTemplate.opsForValue();

        // SET 基础用法
        ops.set("str:name", "Redis");

        // SET + EX（带过期时间）= SETEX
        ops.set("str:token", "abc123", 30, TimeUnit.SECONDS);

        // SETNX（仅当不存在时设置）—— 底层 SET key value NX
        Boolean setIfAbsent = ops.setIfAbsent("str:name", "NewValue");
        log.info("[SETNX] key已存在, 设置结果={}", setIfAbsent); // false

        Boolean setIfAbsent2 = ops.setIfAbsent("str:new_key", "only_if_absent");
        log.info("[SETNX] key不存在, 设置结果={}", setIfAbsent2); // true

        // XX（仅当已存在时设置）—— 更新场景
        Boolean setIfPresent = ops.setIfPresent("str:name", "Updated");
        log.info("[XX] key已存在, 更新结果={}", setIfPresent); // true

        // GET
        String value = ops.get("str:name");
        log.info("[GET] str:name = {}", value);

        // MSET / MGET: 批量操作
        ops.multiSet(Map.of(
                "str:k1", "v1",
                "str:k2", "v2",
                "str:k3", "v3"
        ));
        List<String> values = ops.multiGet(List.of("str:k1", "str:k2", "str:k3"));
        log.info("[MGET] k1={}, k2={}, k3={}", values.get(0), values.get(1), values.get(2));

        // 清理
        redisTemplate.delete(List.of("str:name", "str:token", "str:new_key", "str:k1", "str:k2", "str:k3"));

        return "SET/GET/MSET/MGET 演示完成";
    }

    /**
     * 原子计数操作
     * <p>
     * INCR: 值 +1（值必须是整数，否则报错）
     * INCRBY: 值 +N
     * INCRBYFLOAT: 浮点数自增
     * DECR / DECRBY: 自减
     * <p>
     * 关键特性：
     * - 原子性：即使多个客户端同时 INCR，最终结果也正确
     * - 若键不存在，先初始化为 0 再执行
     * - 单线程保证：无需加锁，天然线程安全
     * <p>
     * 应用：
     * - 页面浏览量（PV）
     * - 点赞数/转发数
     * - 库存扣减（配合 Lua 保证原子判断+扣减）
     * - 限流计数器
     */
    public String counterOps() {
        var ops = redisTemplate.opsForValue();

        // 初始化计数器
        ops.set("counter:page_view", "0");

        // INCR: 原子 +1
        for (int i = 0; i < 5; i++) {
            Long pv = ops.increment("counter:page_view");
            log.info("[INCR] 第{}次访问, PV={}", i + 1, pv);
        }

        // INCRBY: 指定增量
        Long newPv = ops.increment("counter:page_view", 100);
        log.info("[INCRBY +100] PV={}", newPv);

        // DECR: 原子 -1
        Long decrResult = ops.decrement("counter:page_view");
        log.info("[DECR] PV={}", decrResult);

        // INCRBYFLOAT: 浮点数自增
        ops.set("counter:price", "99.9");
        Double newPrice = ops.increment("counter:price", 0.1);
        log.info("[INCRBYFLOAT +0.1] price={}", newPrice);

        // 清理
        redisTemplate.delete("counter:page_view");
        redisTemplate.delete("counter:price");

        return "PV=" + newPv + ", price=" + newPrice;
    }

    /**
     * 字符串操作命令
     * <p>
     * APPEND: 追加字符串到末尾（键不存在则创建）
     * STRLEN: 获取字符串长度
     * GETRANGE: 获取子串（闭区间，支持负索引，-1 表示最后一个字符）
     * SETRANGE: 从指定偏移量开始覆写字符串
     * <p>
     * 注意：中文字符在 UTF-8 编码下每个字符占 3 字节
     * STRLEN 返回的是字节长度，不是字符长度
     */
    public String stringManipulation() {
        var ops = redisTemplate.opsForValue();

        // APPEND: 追加
        ops.set("str:msg", "Hello");
        ops.append("str:msg", " Redis");
        ops.append("str:msg", " World");
        String msg = ops.get("str:msg");
        log.info("[APPEND] {}", msg); // Hello Redis World

        // STRLEN: 字符串长度（字节数）
        Long len = ops.size("str:msg");
        log.info("[STRLEN] 长度={}", len);

        // GETRANGE: 获取子串
        // GETRANGE key start end（闭区间，支持负索引）
        String sub = ops.get("str:msg", 0, 4);
        log.info("[GETRANGE 0 4] {}", sub); // Hello

        // SETRANGE: 从偏移量开始覆写
        ops.set("str:demo", "Hello World");
        ops.set("str:demo", "Redis", 6); // 从偏移量6开始写
        String afterSetRange = ops.get("str:demo");
        log.info("[SETRANGE] 原='Hello World', 覆写后='{}'", afterSetRange); // Hello Redis

        // 清理
        redisTemplate.delete("str:msg");
        redisTemplate.delete("str:demo");

        return "msg=" + msg + ", sub=" + sub;
    }

    /**
     * 分布式锁简单实现（推荐方案）
     * <p>
     * SET key value NX EX seconds
     * - NX: 仅当键不存在时设置（互斥性）
     * - EX: 设置过期时间（防死锁）
     * - value: 使用 UUID 作为锁持有者标识（保证释放时是自己持有的锁）
     * <p>
     * 生产环境建议使用 Redisson 或 RedLock 实现，本例仅演示原理。
     */
    public String distributedLock() {
        var ops = redisTemplate.opsForValue();

        String lockKey = "lock:order:1001";
        String lockValue = java.util.UUID.randomUUID().toString();
        long lockTimeout = 10;

        // 加锁：SET NX EX
        Boolean locked = ops.setIfAbsent(lockKey, lockValue, lockTimeout, TimeUnit.SECONDS);
        log.info("[LOCK] 尝试加锁: {}", locked);

        if (Boolean.TRUE.equals(locked)) {
            // 模拟业务处理
            log.info("[LOCK] 获得锁, 执行业务逻辑...");
            // 释放锁: 仅当 value 匹配时才删除（Lua 脚本保证原子性）
            // 简单示例中直接删除，生产环境应使用 Lua:
            // if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end
            redisTemplate.delete(lockKey);
            log.info("[LOCK] 释放锁");
        }

        return "locked=" + locked;
    }
}
