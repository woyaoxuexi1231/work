package work.N5redis.day1;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;

/**
 * @author hulei
 * @since 2026/5/2 12:03
 */

@Component
public class RedisLockUtil {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public Boolean tryLock() {
        Boolean lockKey = stringRedisTemplate.opsForValue().setIfAbsent("LOCK_KEY", Thread.currentThread().getName(), Duration.ofSeconds(10));
        return Boolean.TRUE.equals(lockKey);
    }

    public Boolean unlock() {
        /*
                        "if ((redis.call('exists', KEYS[1]) == 0) " +
                            "or (redis.call('hexists', KEYS[1], ARGV[2]) == 1)) then " +
                        "redis.call('hincrby', KEYS[1], ARGV[2], 1); " +
                        "redis.call('pexpire', KEYS[1], ARGV[1]); " +
                        "return nil; " +
                    "end; " +
                    "return redis.call('pttl', KEYS[1]);",
         */
        String unlockLua =
                "if redis.call('get', KEYS[1]) == ARGV[1]" +
                        "then redis.call('del', KEYS[1]);" +
                        "return true;" +
                        "end;" +
                        "return false;";


        // 1. 创建 RedisScript 对象，指定返回类型（Boolean）
        RedisScript<Boolean> script = new DefaultRedisScript<>(unlockLua, Boolean.class);


        // stringRedisTemplate 执行这个lua
        Boolean lockKey = stringRedisTemplate.execute(script, Collections.singletonList("LOCK_KEY"), Thread.currentThread().getName());

        return Boolean.TRUE.equals(lockKey);
    }
}
