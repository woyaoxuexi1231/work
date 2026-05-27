package com.example.redis.c04_typecmds;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 4. 各类型核心命令 —— Hash
 * <p>
 * Hash 是一个键值对集合，适合存储对象。
 * 相比将整个对象序列化为 String 存储，Hash 的优势：
 * 1. 可以只读取/修改单个字段，避免序列化整个对象
 * 2. 小 hash 使用 listpack 编码，内存效率极高
 * 3. 字段级别的过期（Redis 7.4+ 支持 hash 字段级 TTL）
 * <p>
 * 核心命令：
 * - 写入：HSET、HMSET（已废弃，用 HSET 替代）
 * - 读取：HGET、HMGET、HGETALL、HKEYS、HVALS
 * - 删除：HDEL
 * - 判断：HEXISTS
 * - 计数：HINCRBY、HINCRBYFLOAT
 * - 遍历：HSCAN
 * - 长度：HLEN
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HashCmdDemo {

    private final StringRedisTemplate redisTemplate;

    /**
     * Hash 基础操作
     * <p>
     * HSET: 设置一个或多个字段值（Redis 4.0+ 支持多字段）
     * HGET: 获取单个字段值
     * HMGET: 批量获取多个字段值
     * HGETALL: 获取所有字段和值（慎用：大 hash 会阻塞）
     * HDEL: 删除一个或多个字段
     * HEXISTS: 判断字段是否存在
     * HLEN: 返回字段数量
     */
    public String basicOps() {
        var ops = redisTemplate.opsForHash();

        // HSET: 设置单个字段
        ops.put("hash:user:1001", "name", "张三");
        ops.put("hash:user:1001", "age", "28");
        ops.put("hash:user:1001", "email", "zhangsan@example.com");

        // HSET 批量设置（推荐，一次网络往返）
        ops.putAll("hash:user:1002", Map.of(
                "name", "李四",
                "age", "32",
                "email", "lisi@example.com"
        ));

        // HGET: 获取单个字段
        String name = (String) ops.get("hash:user:1001", "name");
        log.info("[HGET] user:1001 name={}", name);

        // HMGET: 批量获取
        List<Object> fields = ops.multiGet("hash:user:1001", List.of("name", "age", "email"));
        log.info("[HMGET] user:1001 → name={}, age={}, email={}", fields.get(0), fields.get(1), fields.get(2));

        // HGETALL: 获取所有字段（生产慎用，大 hash 会阻塞）
        Map<Object, Object> all = ops.entries("hash:user:1001");
        log.info("[HGETALL] user:1001 → {}", all);

        // HEXISTS: 判断字段是否存在
        Boolean hasName = ops.hasKey("hash:user:1001", "name");
        Boolean hasPhone = ops.hasKey("hash:user:1001", "phone");
        log.info("[HEXISTS] name={}, phone={}", hasName, hasPhone);

        // HLEN: 字段数量
        Long size = ops.size("hash:user:1001");
        log.info("[HLEN] user:1001 字段数={}", size);

        // HDEL: 删除字段
        ops.delete("hash:user:1001", "email");
        log.info("[HDEL] 删除 email 后剩余字段={}", ops.size("hash:user:1001"));

        // 清理
        redisTemplate.delete("hash:user:1001");
        redisTemplate.delete("hash:user:1002");

        return "Hash 基础操作演示完成, name=" + name;
    }

    /**
     * Hash 原子计数与遍历
     * <p>
     * HINCRBY: 字段值原子增加整数（字段不存在则初始化为 0）
     * HINCRBYFLOAT: 字段值原子增加浮点数
     * HKEYS: 获取所有字段名（慎用）
     * HVALS: 获取所有值（慎用）
     * HSCAN: 安全遍历（增量式，不阻塞）
     */
    public String counterAndScan() {
        var ops = redisTemplate.opsForHash();

        // HINCRBY: 原子计数
        ops.put("hash:counter:page", "view", "0");
        for (int i = 0; i < 5; i++) {
            Long newView = ops.increment("hash:counter:page", "view", 1);
            log.info("[HINCRBY] view={}", newView);
        }

        // HINCRBYFLOAT: 浮点数自增
        ops.put("hash:price:item", "price", "99.9");
        ops.increment("hash:price:item", "price", 0.1);
        Object price = ops.get("hash:price:item", "price");
        log.info("[HINCRBYFLOAT] price={}", price);

        // HKEYS / HVALS: 获取所有字段名/值
        var keys = ops.keys("hash:user:1001".isEmpty() ? "hash:counter:page" : "hash:counter:page");
        log.info("[HKEYS] {}", keys);

        var values = ops.values("hash:counter:page");
        log.info("[HVALS] {}", values);

        // HSCAN: 安全遍历（生产推荐）
        // 对于大 hash，HGETALL 会阻塞，HSCAN 是增量式遍历
        ops.putAll("hash:large", Map.of());
        for (int i = 0; i < 100; i++) {
            ops.put("hash:large", "field:" + i, "value:" + i);
        }

        var scanOps = redisTemplate.opsForHash();
        var cursor = scanOps.scan("hash:large",
                org.springframework.data.redis.core.ScanOptions.scanOptions().count(20).build());
        int scanCount = 0;
        while (cursor.hasNext()) {
            cursor.next();
            scanCount++;
        }
        cursor.close();
        log.info("[HSCAN] 大hash 扫描到 {} 个字段", scanCount);

        // 清理
        redisTemplate.delete("hash:counter:page");
        redisTemplate.delete("hash:price:item");
        redisTemplate.delete("hash:large");

        return "view=5, price=" + price + ", scanCount=" + scanCount;
    }

    /**
     * Hash 应用场景：对象存储
     * <p>
     * 对比 String 存储对象 vs Hash 存储对象：
     * <p>
     * String + JSON:
     *   优点：读写简单，一次操作获取全部
     *   缺点：修改单个字段需要读取→反序列化→修改→序列化→写入
     * <p>
     * Hash:
     *   优点：可单独读写字段，节省带宽
     *   缺点：HGETALL 在字段多时性能差
     * <p>
     * 选择建议：
     * - 字段少、经常整体读取 → String JSON
     * - 字段多、经常部分更新 → Hash
     * - 需要字段级过期 → Hash（Redis 7.4+）
     */
    public String objectStorage() {
        var ops = redisTemplate.opsForHash();

        // 使用 Hash 存储用户对象
        String userKey = "user:profile:1001";
        ops.put(userKey, "id", "1001");
        ops.put(userKey, "name", "张三");
        ops.put(userKey, "age", "28");
        ops.put(userKey, "city", "北京");
        ops.put(userKey, "score", "4.5");

        // 只读取需要的字段（节省带宽）
        String userName = (String) ops.get(userKey, "name");
        String userCity = (String) ops.get(userKey, "city");
        log.info("[对象存储] 姓名={}, 城市={}", userName, userCity);

        // 只更新单个字段（不影响其他字段）
        ops.increment(userKey, "age", 1);
        String newAge = (String) ops.get(userKey, "age");
        log.info("[对象更新] 年龄更新为 {}", newAge);

        redisTemplate.delete(userKey);

        return "user: " + userName + ", " + userCity + ", age=" + newAge;
    }
}
