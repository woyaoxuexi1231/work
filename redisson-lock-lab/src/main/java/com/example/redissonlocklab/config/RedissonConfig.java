package com.example.redissonlocklab.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    @Lazy
    public RedissonClient redissonClient(
            @Value("${spring.data.redis.host}") String host,
            @Value("${spring.data.redis.port}") int port,
            @Value("${app.redisson.lock-watchdog-timeout-ms:30000}") long lockWatchdogTimeoutMs
    ) {
        Config config = new Config();
        config.setLockWatchdogTimeout(lockWatchdogTimeoutMs);

        SingleServerConfig single = config.useSingleServer();
        single.setAddress("redis://" + host + ":" + port);
        return Redisson.create(config);
    }
}
