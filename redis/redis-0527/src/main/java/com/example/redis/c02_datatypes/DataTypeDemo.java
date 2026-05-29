package com.example.redis.c02_datatypes;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.HashOperations;
import java.util.Arrays;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

/**
 * 2. 数据类型与底层编码
 * <p>
 * Redis 5 种基本类型及其底层编码：
 * <p>
 * String: int（纯数字）、embstr（≤44字节短字符串，对象头与数据连续分配）、raw（>44字节长字符串）
 * List:  quicklist（3.2+ 统一，ziplist + 双向链表的混合体）
 * Set:   intset（全整数且元素较少时）、hashtable（其他情况）
 * ZSet:  ziplist/listpack（元素少且长度短时）、skiplist + dict（其他情况）
 * Hash:  ziplist/listpack（字段少且值短时）、hashtable（其他情况）
 * <p>
 * 编码转换阈值由以下配置控制：
 * - hash-max-listpack-entries 128
 * - hash-max-listpack-value 64
 * - zset-max-listpack-entries 128
 * - zset-max-listpack-value 64
 * - set-max-intset-entries 512
 * - list-max-listpack-size -2
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataTypeDemo {

    private final StringRedisTemplate redisTemplate;

    /**
     * 演示 String 类型的三种底层编码
     * <p>
     * int:   值为整数时，直接用 long 存储，零额外分配
     * embstr: 字符串长度 ≤ 44 字节时，redisObject 与 SDS 在同一块内存中（一次分配）
     * raw:   字符串长度 > 44 字节时，redisObject 与 SDS 分开分配（两次分配）
     */
    public String stringEncoding() {
        ValueOperations<String, String> ops = redisTemplate.opsForValue();
        RedisConnection conn = redisTemplate.getConnectionFactory().getConnection();
        try {
            ops.set("str:int", "12345");
            ops.set("str:embstr", "hello");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 100; i++) {
                sb.append("a");
            }
            ops.set("str:raw", sb.toString());

            String encInt = objectEncoding(conn, "str:int");
            String encEmbstr = objectEncoding(conn, "str:embstr");
            String encRaw = objectEncoding(conn, "str:raw");

            log.info("[String 编码] int值='12345' → {}", encInt);
            log.info("[String 编码] 短字符串='hello' → {}", encEmbstr);
            log.info("[String 编码] 长字符串(100字节) → {}", encRaw);

            // 清理
            redisTemplate.delete("str:int");
            redisTemplate.delete("str:embstr");
            redisTemplate.delete("str:raw");

            return String.format("int=%s, embstr=%s, raw=%s", encInt, encEmbstr, encRaw);
        } finally {
            conn.close();
        }
    }

    /**
     * 演示 Hash 类型的编码转换
     * <p>
     * 当字段数 ≤ hash-max-listpack-entries 且每个值 ≤ hash-max-listpack-value 字节时，
     * 使用 listpack（旧版为 ziplist）编码，内存紧凑。
     * 超过阈值后自动转换为 hashtable 编码。
     */
    public String hashEncoding() {
        HashOperations<String, Object, Object> ops = redisTemplate.opsForHash();
        RedisConnection conn = redisTemplate.getConnectionFactory().getConnection();
        try {
            // 小 hash → listpack 编码
            ops.put("hash:small", "name", "张三");
            ops.put("hash:small", "age", "25");
            String smallEnc = objectEncoding(conn, "hash:small");

            // 大 hash → hashtable 编码（超过 128 个字段）
            for (int i = 0; i < 200; i++) {
                ops.put("hash:large", "field:" + i, "value:" + i);
            }
            String largeEnc = objectEncoding(conn, "hash:large");

            log.info("[Hash 编码] 小hash(2字段) → {}", smallEnc);
            log.info("[Hash 编码] 大hash(200字段) → {}", largeEnc);

            redisTemplate.delete("hash:small");
            redisTemplate.delete("hash:large");

            return String.format("小hash=%s, 大hash=%s", smallEnc, largeEnc);
        } finally {
            conn.close();
        }
    }

    /**
     * 演示 Set 类型的编码转换
     * <p>
     * intset: 所有元素都是整数且数量 ≤ set-max-intset-entries 时使用
     * hashtable: 包含非整数元素或数量超限时使用
     */
    public String setEncoding() {
        SetOperations<String, String> ops = redisTemplate.opsForSet();
        RedisConnection conn = redisTemplate.getConnectionFactory().getConnection();
        try {
            // 全整数小集合 → intset
            for (int i = 0; i < 10; i++) {
                ops.add("set:intset", String.valueOf(i));
            }
            String intsetEnc = objectEncoding(conn, "set:intset");

            // 包含字符串 → hashtable
            ops.add("set:hash", "hello", "world", "redis");
            String hashEnc = objectEncoding(conn, "set:hash");

            log.info("[Set 编码] 全整数小集合 → {}", intsetEnc);
            log.info("[Set 编码] 含字符串集合 → {}", hashEnc);

            redisTemplate.delete("set:intset");
            redisTemplate.delete("set:hash");

            return String.format("intset=%s, hashtable=%s", intsetEnc, hashEnc);
        } finally {
            conn.close();
        }
    }

    /**
     * 演示 ZSet（有序集合）的编码转换
     * <p>
     * ziplist/listpack: 元素少且成员短时，按 score 有序排列在连续内存中
     * skiplist + dict: 元素多或成员长时，跳表支持范围查询，dict 支持 O(1) 查分
     */
    public String zsetEncoding() {
        ZSetOperations<String, String> ops = redisTemplate.opsForZSet();
        RedisConnection conn = redisTemplate.getConnectionFactory().getConnection();
        try {
            // 小 zset → listpack
            ops.add("zset:small", "a", 1.0);
            ops.add("zset:small", "b", 2.0);
            ops.add("zset:small", "c", 3.0);
            String smallEnc = objectEncoding(conn, "zset:small");

            // 大 zset → skiplist
            for (int i = 0; i < 200; i++) {
                ops.add("zset:large", "member:" + i, i);
            }
            String largeEnc = objectEncoding(conn, "zset:large");

            log.info("[ZSet 编码] 小集合(3元素) → {}", smallEnc);
            log.info("[ZSet 编码] 大集合(200元素) → {}", largeEnc);

            redisTemplate.delete("zset:small");
            redisTemplate.delete("zset:large");

            return String.format("小zset=%s, 大zset=%s", smallEnc, largeEnc);
        } finally {
            conn.close();
        }
    }

    /**
     * 演示 List 类型的底层编码
     * <p>
     * Redis 3.2+ 统一使用 quicklist 编码：
     * - quicklist 是一个双向链表，每个节点是一个 ziplist/listpack
     * - 兼顾了链表的插入灵活性与 ziplist 的内存紧凑性
     * - list-max-listpack-size 控制每个节点的最大大小
     */
    public String listEncoding() {
        ListOperations<String, String> ops = redisTemplate.opsForList();
        RedisConnection conn = redisTemplate.getConnectionFactory().getConnection();
        try {
            ops.rightPushAll("list:demo", Arrays.asList("a", "b", "c", "d", "e"));
            String enc = objectEncoding(conn, "list:demo");

            log.info("[List 编码] 5元素列表 → {}", enc);

            redisTemplate.delete("list:demo");
            return "quicklist=" + enc;
        } finally {
            conn.close();
        }
    }

    /**
     * OBJECT ENCODING 辅助方法
     * <p>
     * 通过 RedisConnection 发送底层命令获取键的编码类型
     */
    private String objectEncoding(RedisConnection conn, String key) {
        // OBJECT ENCODING key 返回该键底层使用的编码名称
        byte[] result = (byte[]) conn.execute("OBJECT", "ENCODING".getBytes(), key.getBytes());
        return result != null ? new String(result) : "nil";
    }
}
