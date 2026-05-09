package work.N1javabasic.old.day14;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisException;
import org.apache.commons.io.IOUtils;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

public class DistributedLeakyBucket {

    private final JedisPool jedisPool;
    private final String bucketKey;
    private final long capacity;       // 桶容量（原始，例如 100）
    private final double leakRate;     // 漏出速率（每秒处理请求数）
    private static final long SCALE = 1000L;  // 精度放大系数

    private final String luaScriptSha;
    private final String luaScriptContent;

    // 加载 Lua 脚本
    private static String loadLuaScript() {
        try {
            return IOUtils.toString(
                    DistributedLeakyBucket.class
                            .getResourceAsStream("/lua/leaky_bucket_try_acquire.lua"),
                    StandardCharsets.UTF_8
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Lua script", e);
        }
    }

    public DistributedLeakyBucket(String host, int port, String bucketKey,
                                  long capacity, double leakRate) {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(50);
        config.setMaxIdle(10);
        this.jedisPool = new JedisPool(config, host, port, "default", "123456");
        this.bucketKey = bucketKey;
        this.capacity = capacity;
        this.leakRate = leakRate;

        this.luaScriptContent = loadLuaScript();
        try (Jedis jedis = jedisPool.getResource()) {
            this.luaScriptSha = jedis.scriptLoad(luaScriptContent);
        }
    }

    /**
     * 尝试放入一个请求（加水），返回是否允许。
     */
    public boolean tryAcquire() {
        try (Jedis jedis = jedisPool.getResource()) {
            Object result = jedis.evalsha(
                    luaScriptSha,
                    Collections.singletonList(bucketKey),
                    java.util.Arrays.asList(
                            String.valueOf(capacity * SCALE),          // 放大后的容量
                            String.valueOf((long)(leakRate * SCALE)), // 放大后的每秒漏出量
                            String.valueOf(SCALE)
                    )
            );
            return (Long) result == 1;

        } catch (JedisException e) {
            if (e.getMessage() != null && e.getMessage().contains("NOSCRIPT")) {
                try (Jedis jedis = jedisPool.getResource()) {
                    Object result = jedis.eval(
                            luaScriptContent,
                            Collections.singletonList(bucketKey),
                            java.util.Arrays.asList(
                                    String.valueOf(capacity * SCALE),
                                    String.valueOf((long)(leakRate * SCALE)),
                                    String.valueOf(SCALE)
                            )
                    );
                    return (Long) result == 1;
                }
            }
            System.err.println("Redis error: " + e.getMessage());
            return false;   // 降级拒绝
        }
    }

    public void close() {
        jedisPool.close();
    }
}