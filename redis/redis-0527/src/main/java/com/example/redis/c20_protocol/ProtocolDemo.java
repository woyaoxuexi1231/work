package com.example.redis.c20_protocol;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 20. 客户端与协议
 * <p>
 * RESP（REdis Serialization Protocol）是 Redis 客户端与服务器通信的协议。
 * <p>
 * RESP2（Redis 旧版协议）：
 * - 简单字符串: +OK\r\n
 * - 错误: -ERR message\r\n
 * - 整数: :1000\r\n
 * - 批量字符串: $6\r\nhello\r\n
 * - 数组: *2\r\n$3\r\nfoo\r\n$3\r\nbar\r\n
 * <p>
 * RESP3（Redis 6.0+）：
 * - 新增更多数据类型：
 *   - 空值: _\r\n
 *   - 布尔值: #t\r\n / #f\r\n
 *   - 双精度浮点数: ,1.23\r\n
 *   - 大数: (3492890328409238509324850943850943825024385\r\n
 *   - 映射: %2\r\n
 *   - 集合: ~2\r\n
 *   - 推送: >2\r\n
 * <p>
 * 客户端缓存（Client-side caching，Redis 6.0+）：
 * - 服务端跟踪键的变更，通知客户端缓存失效
 * - 减少网络往返，提升读取性能
 * - 两种模式：普通模式、广播模式
 * <p>
 * 连接管理：
 * - AUTH: 认证
 * - SELECT: 切换数据库
 * - PING: 心跳检测
 * - QUIT: 关闭连接
 * - CLIENT SETNAME: 设置连接名
 * - CLIENT SETINFO: 设置客户端信息
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProtocolDemo {

    private final StringRedisTemplate redisTemplate;

    /**
     * RESP 协议说明
     * <p>
     * RESP 是一种文本协议，可读性强，客户端实现简单。
     * 每个响应以类型前缀开头，后跟数据和 \r\n。
     */
    public String respProtocol() {
        String protocol = """
                RESP 协议格式：

                RESP2 基本类型：
                ─────────────────────────────────
                类型        前缀    示例
                ─────────────────────────────────
                简单字符串  +       +OK\\r\\n
                错误        -       -ERR unknown command\\r\\n
                整数        :       :1000\\r\\n
                批量字符串  $       $5\\r\\nhello\\r\\n
                数组        *       *2\\r\\n$3\\r\\nfoo\\r\\n$3\\r\\nbar\\r\\n
                ─────────────────────────────────

                RESP3 新增类型（Redis 6.0+）：
                ─────────────────────────────────
                类型        前缀    示例
                ─────────────────────────────────
                空值        _       _\\r\\n
                布尔        #       #t\\r\\n
                双精度      ,       ,1.23\\r\\n
                大数        (       (34928903284092385093248509\\r\\n
                映射        %       %2\\r\\n
                集合        ~       ~3\\r\\n
                推送        >       >2\\r\\n
                ─────────────────────────────────

                示例：SET hello world 的 RESP 编码：
                *3\\r\\n$3\\r\\nSET\\r\\n$5\\r\\nhello\\r\\n$5\\r\\nworld\\r\\n

                解析：
                *3     → 数组，3 个元素
                $3     → 批量字符串，3 字节
                SET    → 命令名
                $5     → 批量字符串，5 字节
                hello  → 键名
                $5     → 批量字符串，5 字节
                world  → 键值
                """;

        log.info("[RESP 协议]\n{}", protocol);
        return "协议说明已输出";
    }

    /**
     * 客户端缓存（Client-side caching）
     * <p>
     * Redis 6.0+ 支持服务端辅助的客户端缓存。
     * 服务端跟踪客户端访问的键，当键被修改时通知客户端。
     * <p>
     * 两种模式：
     * 1. 普通模式（Tracking）：
     *    - 客户端发送 CLIENT TRACKING ON
     *    - 读取键后，服务端记录该键与客户端的关联
     *    - 键被修改时，服务端发送失效通知
     *    - 客户端删除本地缓存
     * <p>
     * 2. 广播模式（Tracking BCAST）：
     *    - 客户端订阅键前缀
     *    - 匹配前缀的键被修改时，所有订阅者收到通知
     *    - 适合热点键监控
     */
    public String clientCaching() {
        String caching = """
                客户端缓存（Client-side caching）：

                # 普通模式
                CLIENT TRACKING ON
                GET user:1001      # 服务端记录此键
                # ... 其他客户端修改 user:1001 ...
                # 服务端推送失效通知 → 客户端删除本地缓存

                # 广播模式
                CLIENT TRACKING ON BCAST PREFIX user:
                # 所有 user: 开头的键被修改时都会收到通知

                # Opt-in 模式（只跟踪明确请求的键）
                CLIENT TRACKING ON OPTIN
                CLIENT CACHING YES
                GET user:1001      # 只跟踪这个键

                # Opt-out 模式（默认跟踪，明确排除）
                CLIENT TRACKING ON OPTOUT
                CLIENT CACHING NO
                GET temp:123       # 不跟踪这个键

                # 重定向（将失效通知发送到另一个连接）
                CLIENT TRACKING ON REDIRECT 123

                # 取消跟踪
                CLIENT TRACKING OFF

                Spring Boot 配置：
                # 启用客户端缓存
                spring.data.redis.client-type=lettuce
                # Lettuce 支持客户端缓存
                """;

        log.info("[客户端缓存]\n{}", caching);
        return "客户端缓存说明已输出";
    }

    /**
     * 连接管理命令
     * <p>
     * Redis 连接管理的最佳实践。
     */
    public String connectionManagement() {
        String management = """
                连接管理最佳实践：

                1. 连接池配置
                   Lettuce（推荐）：
                   - 基于 Netty，支持异步和响应式
                   - 单连接多路复用，资源占用少
                   - 自动重连和重试

                   Jedis：
                   - 同步阻塞模型
                   - 基于 Apache Commons Pool2
                   - 每个操作占用一个连接

                2. 连接池参数
                   max-active: 最大连接数（建议 16~32）
                   max-idle: 最大空闲连接（建议 8~16）
                   min-idle: 最小空闲连接（建议 4~8）
                   max-wait: 获取连接最大等待时间

                3. 超时配置
                   connect-timeout: 连接超时（建议 3~5 秒）
                   command-timeout: 命令超时（建议 3~5 秒）
                   idle-timeout: 空闲连接超时

                4. 健康检查
                   test-while-idle: 空闲时检测连接有效性
                   time-between-eviction-runs: 检测间隔

                5. 重试策略
                   - 网络抖动时自动重试
                   - 指数退避
                   - 最大重试次数限制

                Spring Boot 配置示例：
                spring.data.redis.host=192.168.3.100
                spring.data.redis.port=6379
                spring.data.redis.password=123456
                spring.data.redis.timeout=5000ms
                spring.data.redis.lettuce.pool.max-active=16
                spring.data.redis.lettuce.pool.max-idle=8
                spring.data.redis.lettuce.pool.min-idle=4
                spring.data.redis.lettuce.pool.max-wait=3000ms
                """;

        log.info("[连接管理]\n{}", management);
        return "连接管理说明已输出";
    }
}
