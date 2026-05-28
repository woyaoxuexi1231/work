package com.example.redis.c03_keyops;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 3. 通用键命令
 * <p>
 * 键操作是 Redis 最基础的能力，涵盖：
 * - 键的查询与删除：KEYS、SCAN、EXISTS、TYPE、DEL、UNLINK
 * - 过期管理：EXPIRE、TTL、PERSIST
 * - 序列化：DUMP、RESTORE
 * - 对象信息：OBJECT ENCODING / IDLETIME / REFCOUNT
 * - 数据库管理：SELECT、SWAPDB、FLUSHDB
 * <p>
 * 生产注意事项：
 * - 禁止使用 KEYS *（O(N) 全库扫描会阻塞），改用 SCAN
 * - DEL 对大键会阻塞，UNLINK 为异步删除（Redis 4.0+）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeyOpsDemo {

    private final StringRedisTemplate redisTemplate;

    /**
     * 演示键的基本操作
     * EXISTS: 判断键是否存在（支持批量，返回存在的数量）
     * TYPE:   返回键的数据类型（string/list/set/zset/hash/stream）
     * DEL:    同步删除键（阻塞直到释放内存）
     * UNLINK: 异步删除键（后台线程释放内存，不阻塞主线程）
     */
    public String basicKeyOps() {
        ValueOperations<String, String> ops = redisTemplate.opsForValue();
        RedisConnection conn = redisTemplate.getConnectionFactory().getConnection();

        // 准备测试数据
        ops.set("key:str", "hello");
        redisTemplate.opsForHash().put("key:hash", "f1", "v1");

        // EXISTS: 检查键是否存在
        Boolean exists1 = redisTemplate.hasKey("key:str");
        Boolean exists2 = redisTemplate.hasKey("key:not_exist");
        log.info("[EXISTS] key:str={}, key:not_exist={}", exists1, exists2);

        // EXISTS 批量检查：返回存在的数量
        Long existCount = redisTemplate.countExistingKeys(Arrays.asList("key:str", "key:hash", "key:none"));
        log.info("[EXISTS] 批量检查3个键，存在数量={}", existCount);

        // TYPE: 查看键的类型
        DataType type = redisTemplate.type("key:str");
        log.info("[TYPE] key:str → {}", type);

        // DEL vs UNLINK
        // DEL 是同步删除，大键（如百万元素的 set）会阻塞
        // UNLINK 是异步删除，立即返回，后台线程负责释放内存
        redisTemplate.delete("key:str");

        // UNLINK 需要通过底层连接调用
        conn.unlink("key:hash".getBytes());
        log.info("[DEL/UNLINK] 已删除 key:str 和 key:hash");

        return "EXISTS: " + exists1 + ", TYPE: " + type;
    }

    /**
     * 演示 SCAN 命令 —— 生产环境的安全遍历方案
     * <p>
     * SCAN 相比 KEYS 的优势：
     * 1. 增量遍历，每次只返回少量键，不会阻塞
     * 2. 基于游标，可中断后继续
     * 3. 支持 COUNT 提示每次返回的键数量（非精确值）
     * <p>
     * SCAN 的保证：
     * - 不会返回已删除的键
     * - 可能返回新增的键（增量遍历的代价）
     * - 不保证返回在遍历期间被多次修改的键的最新值
     * - 同一个键最多返回一次
     */
    public String scanDemo() {
        ValueOperations<String, String> ops = redisTemplate.opsForValue();

        // 准备测试数据
        for (int i = 0; i < 50; i++) {
            ops.set("scan:test:" + i, "v" + i);
        }

        // 使用 SCAN 遍历匹配的键
        ScanOptions options = ScanOptions.scanOptions()
                .match("scan:test:*")
                .count(10)  // 每次扫描建议返回 10 个（实际数量不确定）
                .build();

        int count = 0;
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                cursor.next();
                count++;
            }
        }
        log.info("[SCAN] 匹配 'scan:test:*' 共找到 {} 个键", count);

        // 清理
        for (int i = 0; i < 50; i++) {
            redisTemplate.delete("scan:test:" + i);
        }

        return "SCAN 匹配到 " + count + " 个键";
    }

    /**
     * 演示过期时间管理
     * <p>
     * EXPIRE: 设置键的 TTL（秒）
     * PEXPIRE: 设置键的 TTL（毫秒）
     * TTL: 查看剩余生存时间（秒），-1 表示永不过期，-2 表示键不存在
     * PERSIST: 移除过期时间，使键永久存在
     * <p>
     * 过期删除策略（惰性删除 + 定期删除）：
     * - 惰性删除：访问键时检查是否过期，过期则删除
     * - 定期删除：每 100ms 随机抽取一批键检查，删除已过期的键
     * - 两种策略配合，既保证及时清理，又避免全量扫描的 CPU 开销
     */
    public String expireDemo() {
        ValueOperations<String, String> ops = redisTemplate.opsForValue();

        ops.set("expire:test", "will-expire", 60, TimeUnit.SECONDS);

        // TTL: 查看剩余生存时间
        Long ttl = redisTemplate.getExpire("expire:test", TimeUnit.SECONDS);
        log.info("[TTL] expire:test 剩余 {} 秒", ttl);

        // EXPIRE: 修改过期时间
        redisTemplate.expire("expire:test", 30, TimeUnit.SECONDS);
        ttl = redisTemplate.getExpire("expire:test", TimeUnit.SECONDS);
        log.info("[EXPIRE] 修改后剩余 {} 秒", ttl);

        // PERSIST: 移除过期时间
        Boolean persisted = redisTemplate.persist("expire:test");
        ttl = redisTemplate.getExpire("expire:test", TimeUnit.SECONDS);
        log.info("[PERSIST] 移除过期时间: success={}, TTL={}", persisted, ttl);
        // TTL 为 -1 表示永不过期

        // TTL 特殊值说明
        // -2: 键不存在
        // -1: 键存在但没有设置过期时间
        Long ttlNotExist = redisTemplate.getExpire("key:not_exist", TimeUnit.SECONDS);
        log.info("[TTL] 不存在的键 → {} (-2 表示不存在)", ttlNotExist);

        redisTemplate.delete("expire:test");
        return "TTL=" + ttl + ", PERSIST=" + persisted;
    }

    /**
     * 演示 RENAME 和 COPY
     * <p>
     * RENAME: 原子地重命名键（若目标键已存在则覆盖）
     * COPY: Redis 6.2+ 新增，复制键到新键（不影响原键）
     */
    public String renameCopyDemo() {
        ValueOperations<String, String> ops = redisTemplate.opsForValue();

        ops.set("rename:src", "original-value");

        // RENAME: 重命名
        redisTemplate.rename("rename:src", "rename:dst");
        String value = ops.get("rename:dst");
        Boolean srcExists = redisTemplate.hasKey("rename:src");
        log.info("[RENAME] rename:src → rename:dst, 值={}, 原键存在={}", value, srcExists);

        // COPY: 复制（Redis 6.2+）
        // copy(key, newKey, replace) — replace=false 时若目标已存在则失败
        try {
            redisTemplate.getConnectionFactory().getConnection()
                    .copy("rename:dst".getBytes(), "rename:copy".getBytes(), false);
            String copyValue = ops.get("rename:copy");
            log.info("[COPY] rename:dst → rename:copy, 值={}", copyValue);
        } catch (Exception e) {
            log.warn("[COPY] 当前 Redis 版本可能不支持 COPY 命令: {}", e.getMessage());
        }

        redisTemplate.delete("rename:dst");
        redisTemplate.delete("rename:copy");

        return "RENAME → " + value;
    }

    /**
     * 演示 OBJECT 系列命令
     * <p>
     * OBJECT ENCODING: 键的底层编码类型
     * OBJECT IDLETIME: 键的空闲时间（秒）—— LRU 淘汰依据
     * OBJECT REFCOUNT: 键的引用计数—— 共享对象池机制
     * OBJECT FREQ:     键的访问频率—— LFU 淘汰依据
     */
    public String objectInfoDemo() {
        ValueOperations<String, String> ops = redisTemplate.opsForValue();
        RedisConnection conn = redisTemplate.getConnectionFactory().getConnection();

        ops.set("obj:test", "hello-world");

        // 使用 execute 调用底层 OBJECT 命令
        byte[] encBytes = (byte[]) conn.execute("OBJECT", "ENCODING".getBytes(), "obj:test".getBytes());
        String encoding = encBytes != null ? new String(encBytes) : "nil";

        byte[] idleBytes = (byte[]) conn.execute("OBJECT", "IDLETIME".getBytes(), "obj:test".getBytes());
        String idletime = idleBytes != null ? new String(idleBytes) : "0";

        byte[] refBytes = (byte[]) conn.execute("OBJECT", "REFCOUNT".getBytes(), "obj:test".getBytes());
        String refcount = refBytes != null ? new String(refBytes) : "0";

        log.info("[OBJECT] encoding={}, idletime={}s, refcount={}", encoding, idletime, refcount);

        redisTemplate.delete("obj:test");
        return String.format("encoding=%s, idletime=%ss, refcount=%s", encoding, idletime, refcount);
    }

    /**
     * 演示 FLUSHDB —— 清空当前数据库
     * <p>
     * FLUSHDB: 清空当前数据库的所有键
     * FLUSHALL: 清空所有数据库的键
     * <p>
     * 生产环境务必通过 rename-command 禁用或重命名这两个命令！
     * Redis 6.2+ 支持 FLUSHDB ASYNC 异步清空，避免阻塞。
     */
    public String flushDbDemo() {
        RedisConnection conn = redisTemplate.getConnectionFactory().getConnection();

        // 查看当前库的键数量
        conn.select(15); // 切换到 db15 做测试，避免影响主库
        conn.stringCommands().set("flush:test1".getBytes(), "v1".getBytes());
        conn.stringCommands().set("flush:test2".getBytes(), "v2".getBytes());

        // FLUSHDB（不真正执行，仅演示命令用法）
        log.info("[FLUSHDB] 演示命令: FLUSHDB / FLUSHDB ASYNC");
        log.info("[FLUSHDB] 生产环境应通过 rename-command 禁用此命令");

        // 手动清理测试数据
        conn.keyCommands().del("flush:test1".getBytes());
        conn.keyCommands().del("flush:test2".getBytes());
        conn.select(0);

        return "FLUSHDB 演示完成（未实际执行清空）";
    }
}
