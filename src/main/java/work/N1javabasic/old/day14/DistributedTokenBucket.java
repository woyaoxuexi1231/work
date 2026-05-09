package work.N1javabasic.old.day14;

import org.apache.commons.io.IOUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisException;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

// ================== 分布式令牌桶 ==================
public class DistributedTokenBucket {

    private final JedisPool jedisPool;
    private final String bucketKey;
    private final long maxTokens;        // 最大令牌数
    private final long refillRate;       // 每秒生成令牌数
    private static final long SCALE = 1000L; // 毫令牌放大系数

    // Lua 脚本 SHA1 值（用于脚本缓存）
    private final String luaScriptSha;
    private final String luaScriptContent;

    // 从 resource 加载 Lua 脚本
    private static String loadLuaScript() {
        try {
            // 从 classpath 读取 lua 脚本文件
            return IOUtils.toString(
                    DistributedTokenBucket.class
                            .getResourceAsStream("/lua/token_bucket_try_acquire.lua"),
                    StandardCharsets.UTF_8
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Lua script from resource", e);
        }
    }

    public DistributedTokenBucket(String host, int port, String bucketKey,
                                  long maxTokens, long refillRate) {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(50);
        config.setMaxIdle(10);
        this.jedisPool = new JedisPool(config, host, port, "default", "123456");
        this.bucketKey = bucketKey;
        this.maxTokens = maxTokens;
        this.refillRate = refillRate;

        // 加载 Lua 脚本
        this.luaScriptContent = loadLuaScript();

        // 预加载脚本到 Redis，获取 SHA1
        try (Jedis jedis = jedisPool.getResource()) {
            this.luaScriptSha = jedis.scriptLoad(luaScriptContent);
        }
    }

    /**
     * 尝试获取 1 个令牌
     *
     * @return true 获取成功，false 限流
     */
    public boolean tryAcquire() {
        try (Jedis jedis = jedisPool.getResource()) {
            // 使用 EVALSHA 执行缓存的脚本（性能更好）
            Object result = jedis.evalsha(
                    luaScriptSha,
                    Collections.singletonList(bucketKey),
                    java.util.Arrays.asList(
                            String.valueOf(maxTokens * SCALE),
                            String.valueOf(refillRate),
                            String.valueOf(SCALE)
                    )
            );
            long remaining = (Long) result;
            return remaining >= 0;

        } catch (JedisException e) {
            // 如果脚本不存在（Redis 重启导致缓存丢失），回退到 EVAL
            if (e.getMessage() != null && e.getMessage().contains("NOSCRIPT")) {
                try (Jedis jedis = jedisPool.getResource()) {
                    Object result = jedis.eval(
                            luaScriptContent,
                            Collections.singletonList(bucketKey),
                            java.util.Arrays.asList(
                                    String.valueOf(maxTokens * SCALE),
                                    String.valueOf(refillRate),
                                    String.valueOf(SCALE)
                            )
                    );
                    long remaining = (Long) result;
                    return remaining >= 0;
                }
            }
            // Redis 不可用时的降级策略
            System.err.println("Redis error: " + e.getMessage());
            return false;
        }
    }

    /**
     * 重新加载 Lua 脚本到 Redis（用于脚本更新后）
     */
    public void reloadScript() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.scriptFlush();
            // 重新获取新的 SHA1
            String newSha = jedis.scriptLoad(luaScriptContent);
            // 注意：这里无法修改 final 字段，实际使用中需要重新创建实例
            System.out.println("Script reloaded, new SHA: " + newSha);
        }
    }

    public void close() {
        jedisPool.close();
    }
}