package com.example.id20260608.config;

import com.example.id20260608.snowflake.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 雪花算法实例配置
 */
@Configuration
public class SnowflakeConfig {

    // ---- Classic Snowflake ----
    @Bean
    public ClassicSnowflake classicSnowflake(
            @Value("${snowflake.classic.worker-id}") long workerId,
            @Value("${snowflake.classic.datacenter-id}") long datacenterId) {
        return new ClassicSnowflake(workerId, datacenterId);
    }

    // ---- Wait Strategy Snowflake ----
    @Bean
    public WaitStrategySnowflake waitStrategySnowflake(
            @Value("${snowflake.wait-strategy.worker-id}") long workerId,
            @Value("${snowflake.wait-strategy.datacenter-id}") long datacenterId,
            @Value("${snowflake.wait-strategy.max-backward-ms}") long maxBackwardMs) {
        return new WaitStrategySnowflake(workerId, datacenterId, maxBackwardMs);
    }

    // ---- Fast Fail Snowflake ----
    @Bean
    public FastFailSnowflake fastFailSnowflake(
            @Value("${snowflake.fast-fail.worker-id}") long workerId,
            @Value("${snowflake.fast-fail.datacenter-id}") long datacenterId) {
        return new FastFailSnowflake(workerId, datacenterId);
    }

    // ---- RingBuffer Snowflake ----
    @Bean
    public RingBufferSnowflake ringBufferSnowflake(
            @Value("${snowflake.ring-buffer.worker-id}") long workerId,
            @Value("${snowflake.ring-buffer.datacenter-id}") long datacenterId,
            @Value("${snowflake.ring-buffer.buffer-size}") int bufferSize) {
        return new RingBufferSnowflake(workerId, datacenterId, bufferSize);
    }

    // ---- Custom Bits Snowflake ----
    @Bean
    public CustomBitsSnowflake customBitsSnowflake(
            @Value("${snowflake.custom-bits.worker-id}") long workerId,
            @Value("${snowflake.custom-bits.timestamp-bits}") long timestampBits,
            @Value("${snowflake.custom-bits.worker-bits}") long workerBits,
            @Value("${snowflake.custom-bits.sequence-bits}") long sequenceBits) {
        return new CustomBitsSnowflake(workerId, 1735689600000L,
                timestampBits, workerBits, sequenceBits);
    }
}
