package com.example.id20260608.controller;

import com.example.id20260608.snowflake.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * 雪花算法演示 Controller
 *
 * 提供多种雪花算法实现的 API 演示接口
 */
@RestController
@RequestMapping("/api/snowflake")
public class SnowflakeController {

    @Autowired
    private ClassicSnowflake classicSnowflake;

    @Autowired
    private WaitStrategySnowflake waitStrategySnowflake;

    @Autowired
    private FastFailSnowflake fastFailSnowflake;

    @Autowired
    private RingBufferSnowflake ringBufferSnowflake;

    @Autowired
    private CustomBitsSnowflake customBitsSnowflake;

    // ==================== 单次生成 ====================

    /** 经典Snowflake生成单个ID */
    @GetMapping("/classic")
    public Map<String, Object> classic() {
        long start = System.nanoTime();
        long id = classicSnowflake.nextId();
        long cost = System.nanoTime() - start;
        return result("经典Twitter Snowflake", id, cost);
    }

    /** 等待策略Snowflake生成单个ID */
    @GetMapping("/wait-strategy")
    public Map<String, Object> waitStrategy() {
        long start = System.nanoTime();
        long id = waitStrategySnowflake.nextId();
        long cost = System.nanoTime() - start;
        return result("等待策略 Snowflake", id, cost);
    }

    /** 快速失败Snowflake生成单个ID */
    @GetMapping("/fast-fail")
    public Map<String, Object> fastFail() {
        long start = System.nanoTime();
        long id = fastFailSnowflake.nextId();
        long cost = System.nanoTime() - start;
        return result("快速失败 Snowflake", id, cost);
    }

    /** RingBuffer Snowflake生成单个ID */
    @GetMapping("/ring-buffer")
    public Map<String, Object> ringBuffer() {
        long start = System.nanoTime();
        long id = ringBufferSnowflake.nextId();
        long cost = System.nanoTime() - start;
        Map<String, Object> r = result("RingBuffer Snowflake", id, cost);
        r.put("bufferAvailable", ringBufferSnowflake.getAvailable());
        return r;
    }

    /** 自定义位分配生成单个ID */
    @GetMapping("/custom-bits")
    public Map<String, Object> customBits() {
        long start = System.nanoTime();
        long id = customBitsSnowflake.nextId();
        long cost = System.nanoTime() - start;
        Map<String, Object> r = result("自定义位分配 Snowflake", id, cost);
        r.put("bitLayout", customBitsSnowflake.getBitLayout());
        return r;
    }

    // ==================== 批量压测 ====================

    /** 批量生成ID进行性能对比 */
    @GetMapping("/benchmark")
    public Map<String, Object> benchmark(@RequestParam(defaultValue = "100000") int count) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", count);

        // 经典
        long t1 = System.currentTimeMillis();
        for (int i = 0; i < count; i++) classicSnowflake.nextId();
        result.put("classicMs", System.currentTimeMillis() - t1);
        result.put("classicQPS", count * 1000L / Math.max(1, (long) result.get("classicMs")));

        // 等待策略
        long t2 = System.currentTimeMillis();
        for (int i = 0; i < count; i++) waitStrategySnowflake.nextId();
        result.put("waitStrategyMs", System.currentTimeMillis() - t2);
        result.put("waitStrategyQPS", count * 1000L / Math.max(1, (long) result.get("waitStrategyMs")));

        // 快速失败
        long t3 = System.currentTimeMillis();
        for (int i = 0; i < count; i++) fastFailSnowflake.nextId();
        result.put("fastFailMs", System.currentTimeMillis() - t3);
        result.put("fastFailQPS", count * 1000L / Math.max(1, (long) result.get("fastFailMs")));

        // RingBuffer
        long t4 = System.currentTimeMillis();
        for (int i = 0; i < count; i++) ringBufferSnowflake.nextId();
        result.put("ringBufferMs", System.currentTimeMillis() - t4);
        result.put("ringBufferQPS", count * 1000L / Math.max(1, (long) result.get("ringBufferMs")));

        // 自定义位分配
        long t5 = System.currentTimeMillis();
        for (int i = 0; i < count; i++) customBitsSnowflake.nextId();
        result.put("customBitsMs", System.currentTimeMillis() - t5);
        result.put("customBitsQPS", count * 1000L / Math.max(1, (long) result.get("customBitsMs")));

        return result;
    }

    /** 对比所有实现生成ID的差异 */
    @GetMapping("/compare")
    public Map<String, Object> compare() {
        Map<String, Object> result = new LinkedHashMap<>();

        long id1 = classicSnowflake.nextId();
        long id2 = waitStrategySnowflake.nextId();
        long id3 = fastFailSnowflake.nextId();
        long id4 = ringBufferSnowflake.nextId();
        long id5 = customBitsSnowflake.nextId();

        result.put("classic", analyzeId(id1, "经典Snowflake"));
        result.put("waitStrategy", analyzeId(id2, "等待策略"));
        result.put("fastFail", analyzeId(id3, "快速失败"));
        result.put("ringBuffer", analyzeId(id4, "RingBuffer"));
        result.put("customBits", analyzeId(id5, "自定义位分配"));

        return result;
    }

    /** 列出所有支持的策略 */
    @GetMapping("/summary")
    public List<Map<String, String>> summary() {
        return Arrays.asList(
            mapOf("name", "经典 Twitter Snowflake",
                  "key", "classic",
                  "desc", "41位时间戳+5位数据中心+5位机器+12位序列，回拨直接抛异常",
                  "适用场景", "通用分布式ID生成"),
            mapOf("name", "等待策略 Snowflake",
                  "key", "wait-strategy",
                  "desc", "短回拨(≤10ms)自旋等待恢复，长回拨快速失败",
                  "适用场景", "偶尔NTP抖动的普通业务"),
            mapOf("name", "快速失败 Snowflake",
                  "key", "fast-fail",
                  "desc", "检测到任何回拨立即抛ClockBackwardsException",
                  "适用场景", "金融/交易/订单等强一致性系统"),
            mapOf("name", "RingBuffer Snowflake",
                  "key", "ring-buffer",
                  "desc", "预生成ID存入环形缓冲区，回拨时从缓存取用",
                  "适用场景", "高并发、追求极致性能"),
            mapOf("name", "自定义位分配 Snowflake",
                  "key", "custom-bits",
                  "desc", "自由分配时间戳/机器/序列号位数，适应不同规模",
                  "适用场景", "特殊拓扑或非标需求")
        );
    }

    // ==================== 工具方法 ====================

    private Map<String, Object> result(String name, long id, long costNs) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("strategy", name);
        r.put("id", id);
        r.put("idHex", "0x" + Long.toHexString(id));
        r.put("idBinary", String.format("%64s", Long.toBinaryString(id)).replace(' ', '0'));
        r.put("costNs", costNs);
        long timestamp = id >> 22; // 约等于时间戳部分
        r.put("approxTimestamp", timestamp);
        r.put("timestamp", System.currentTimeMillis());
        return r;
    }

    private Map<String, Object> analyzeId(long id, String name) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("name", name);
        r.put("id", id);
        r.put("binary", String.format("%64s", Long.toBinaryString(id)).replace(' ', '0'));
        return r;
    }

    private Map<String, String> mapOf(String... pairs) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put(pairs[i], pairs[i + 1]);
        }
        return m;
    }
}
