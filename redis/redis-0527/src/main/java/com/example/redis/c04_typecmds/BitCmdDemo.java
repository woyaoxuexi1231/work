package com.example.redis.c04_typecmds;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 4. 各类型核心命令 —— Bitmap
 * <p>
 * Bitmap 不是独立的数据类型，而是 String 类型的位操作。
 * 一个 String 最大 512MB = 2^32 个 bit。
 * <p>
 * 核心命令：
 * - SETBIT key offset value: 设置指定位的值（0 或 1）
 * - GETBIT key offset: 获取指定位的值
 * - BITCOUNT key [start end]: 统计值为 1 的位数
 * - BITPOS key bit [start [end]]: 查找第一个值为 bit 的位
 * - BITOP operation destkey key [key ...]: 位运算（AND/OR/XOR/NOT）
 * - BITFIELD: 批量位操作（子命令 GET/SET/INCRBY）
 * <p>
 * 应用场景：
 * - 用户签到：SETBIT sign:user:1001 0 1 表示第 1 天签到
 * - 在线状态：SETBIT online 1001 1 表示用户 1001 在线
 * - 布隆过滤器：多个 hash 函数 + 大 bitmap
 * - 统计活跃用户：BITOP OR 合并多天签到
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BitCmdDemo {

    private final StringRedisTemplate redisTemplate;

    /**
     * Bitmap 基础操作
     * <p>
     * SETBIT: 设置指定位（offset 从 0 开始）
     * GETBIT: 读取指定位
     * BITCOUNT: 统计 1 的数量
     */
    public String basicOps() {
        var conn = redisTemplate.getConnectionFactory().getConnection();
        byte[] key = "bitmap:demo".getBytes();

        // 清空
        conn.keyCommands().del(key);

        // SETBIT: 设置位
        // 设置 offset 0, 3, 5, 7 为 1
        conn.stringCommands().setBit(key, 0, true);
        conn.stringCommands().setBit(key, 3, true);
        conn.stringCommands().setBit(key, 5, true);
        conn.stringCommands().setBit(key, 7, true);

        // GETBIT: 读取位
        Boolean bit0 = conn.stringCommands().getBit(key, 0);
        Boolean bit1 = conn.stringCommands().getBit(key, 1);
        Boolean bit3 = conn.stringCommands().getBit(key, 3);
        log.info("[GETBIT] bit0={}, bit1={}, bit3={}", bit0, bit1, bit3);

        // BITCOUNT: 统计 1 的个数
        Long count = conn.bitCount(key);
        log.info("[BITCOUNT] 1的个数: {}", count);

        // BITPOS: 查找第一个 1
        Long pos = conn.bitPos(key, true);
        log.info("[BITPOS] 第一个1的位置: {}", pos);

        conn.keyCommands().del(key);
        return "bit0=" + bit0 + ", count=" + count + ", firstPos=" + pos;
    }

    /**
     * Bitmap 应用：用户签到系统
     * <p>
     * 思路：
     * - key: sign:{user_id}:{year}
     * - offset: 一年中的第几天（0~364）
     * - value: 1 表示签到，0 表示未签到
     * <p>
     * 优势：
     * - 一年的签到数据只需 366 bit ≈ 46 字节
     * - 统计活跃天数只需 BITCOUNT
     * - 判断某天是否签到只需 GETBIT
     */
    public String userSignIn() {
        var conn = redisTemplate.getConnectionFactory().getConnection();
        byte[] key = "bitmap:sign:user:1001:2024".getBytes();

        conn.keyCommands().del(key);

        // 模拟签到：第 1、2、5、10、30 天签到
        int[] signDays = {1, 2, 5, 10, 30};
        for (int day : signDays) {
            conn.stringCommands().setBit(key, day, true);
        }

        // 查询第 5 天是否签到
        boolean day5Signed = conn.stringCommands().getBit(key, 5);
        boolean day3Signed = conn.stringCommands().getBit(key, 3);
        log.info("[签到查询] 第5天={}, 第3天={}", day5Signed, day3Signed);

        // 统计本月签到天数
        Long totalDays = conn.bitCount(key);
        log.info("[签到统计] 总签到天数: {}", totalDays);

        conn.keyCommands().del(key);
        return "第5天=" + day5Signed + ", 总签到=" + totalDays;
    }

    /**
     * Bitmap 应用：统计日活跃用户（DAU）
     * <p>
     * 思路：
     * - key: active:{日期}
     * - offset: 用户 ID
     * - 用户访问时 SETBIT active:20240101 user_id 1
     * <p>
     * BITOP OR 多天数据 → 统计周活跃、月活跃
     * 内存效率极高：1 亿用户只需约 12MB
     */
    public String activeUserStats() {
        var conn = redisTemplate.getConnectionFactory().getConnection();

        // 清空测试数据
        conn.keyCommands().del("bitmap:active:day1".getBytes());
        conn.keyCommands().del("bitmap:active:day2".getBytes());
        conn.keyCommands().del("bitmap:active:merged".getBytes());

        // Day1: 用户 1,2,3,5,8 活跃
        long[] day1Users = {1, 2, 3, 5, 8};
        for (long uid : day1Users) {
            conn.stringCommands().setBit("bitmap:active:day1".getBytes(), uid, true);
        }

        // Day2: 用户 2,3,6,7,8 活跃
        long[] day2Users = {2, 3, 6, 7, 8};
        for (long uid : day2Users) {
            conn.stringCommands().setBit("bitmap:active:day2".getBytes(), uid, true);
        }

        // BITCOUNT: 每天活跃用户数
        Long dau1 = conn.bitCount("bitmap:active:day1".getBytes());
        Long dau2 = conn.bitCount("bitmap:active:day2".getBytes());
        log.info("[DAU] Day1 活跃用户: {}, Day2 活跃用户: {}", dau1, dau2);

        // BITOP OR: 合并两天 → 两天内活跃的用户（去重）
        conn.bitOp(
                org.springframework.data.redis.connection.BitOperation.OR,
                "bitmap:active:merged".getBytes(),
                "bitmap:active:day1".getBytes(),
                "bitmap:active:day2".getBytes()
        );
        Long wau = conn.bitCount("bitmap:active:merged".getBytes());
        log.info("[WAU] 两天内活跃用户（去重）: {}", wau);

        // BITOP AND: 两天都活跃的用户
        conn.bitOp(
                org.springframework.data.redis.connection.BitOperation.AND,
                "bitmap:active:merged".getBytes(),
                "bitmap:active:day1".getBytes(),
                "bitmap:active:day2".getBytes()
        );
        Long bothDays = conn.bitCount("bitmap:active:merged".getBytes());
        log.info("[连续活跃] 两天都活跃: {}", bothDays);

        conn.keyCommands().del("bitmap:active:day1".getBytes());
        conn.keyCommands().del("bitmap:active:day2".getBytes());
        conn.keyCommands().del("bitmap:active:merged".getBytes());

        return String.format("DAU1=%d, DAU2=%d, WAU=%d, 连续=%d", dau1, dau2, wau, bothDays);
    }
}
