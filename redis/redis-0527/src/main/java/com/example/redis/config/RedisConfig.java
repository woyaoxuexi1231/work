package com.example.redis.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis 核心配置
 * <p>
 * 本类负责配置 RedisTemplate 与缓存管理器，是整个 Redis 集成的基石。
 * <p>
 * 关键设计决策：
 * 1. Key 采用 String 序列化 → 可读性好，便于排查问题
 * 2. Value 采用 JSON 序列化 → 跨语言兼容，结构自描述
 * 3. 开启 @EnableCaching → 支持 Spring Cache 注解驱动缓存
 */
@Configuration
@EnableCaching
public class RedisConfig {

    /**
     * 配置 JSON 序列化的 ObjectMapper
     * <p>
     * 启用类型信息写入 → 反序列化时能还原具体子类型
     * 这对多态场景（如缓存不同子类对象）至关重要
     */
    private ObjectMapper redisObjectMapper() {
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        om.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        return om;
    }

    /**
     * 通用 RedisTemplate<Object, Object>
     * <p>
     * Key: String 序列化
     * Value: JSON 序列化（含类型信息）
     * Hash Key: String 序列化
     * Hash Value: JSON 序列化
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(redisObjectMapper());
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();

        return template;
    }

    /**
     * 纯字符串 RedisTemplate
     * <p>
     * 适用于：简单 KV 操作、计数器、分布式锁等场景
     * 相比通用模板，避免了 JSON 序列化开销，性能更优
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    /**
     * Spring Cache 缓存管理器
     * <p>
     * 配合 @Cacheable / @CacheEvict / @CachePut 注解使用
     * 默认 TTL 10 分钟，key 前缀 "demo::"
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer(redisObjectMapper())))
                .prefixCacheNameWith("demo::")
                .disableCachingNullValues();

        return RedisCacheManager.builder(factory)
                .cacheDefaults(config)
                .build();
    }
}
