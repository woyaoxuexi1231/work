package com.demo.rabbitmq.controller;

import com.demo.rabbitmq.producer.RabbitProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ REST API 入口
 * 提供统一风格的接口用于触发各种测试场景
 */
@Slf4j
@RestController
@RequestMapping("/api/mq")
@RequiredArgsConstructor
public class RabbitController {

    private final RabbitProducer rabbitProducer;

    // ==================== 基础收发 ====================

    /**
     * Direct 模式发送
     * POST /api/mq/send?mode=direct&count=100
     */
    @PostMapping("/send")
    public Map<String, Object> send(@RequestParam(defaultValue = "direct") String mode,
                                    @RequestParam(defaultValue = "10") int count) {
        return switch (mode) {
            case "topic" -> rabbitProducer.sendTopic(count);
            case "fanout" -> rabbitProducer.sendFanout(count);
            default -> rabbitProducer.sendDirect(count);
        };
    }

    // ==================== 性能压测 ====================

    /**
     * 性能压测
     * POST /api/mq/bench?count=10000&size=1024
     */
    @PostMapping("/bench")
    public Map<String, Object> bench(@RequestParam(defaultValue = "10000") int count,
                                     @RequestParam(defaultValue = "256") int size) {
        return rabbitProducer.benchmark(count, size);
    }

    // ==================== 可靠性保障 ====================

    /**
     * 可靠性演示 - Publisher Confirm + Return
     * POST /api/mq/reliable
     */
    @PostMapping("/reliable")
    public Map<String, Object> reliable() {
        return rabbitProducer.sendReliable();
    }

    // ==================== 高级特性 ====================

    /**
     * 高级特性入口
     * POST /api/mq/advanced/delay?delayMs=3000
     * POST /api/mq/advanced/deadletter
     * POST /api/mq/advanced/priority
     */
    @PostMapping("/advanced/{feature}")
    public Map<String, Object> advanced(@PathVariable String feature,
                                        @RequestParam(defaultValue = "3000") int delayMs) {
        return switch (feature) {
            case "delay" -> rabbitProducer.sendDelay(delayMs);
            case "deadletter" -> rabbitProducer.sendToDeadLetter();
            case "priority" -> rabbitProducer.sendPriority();
            default -> {
                Map<String, Object> result = new HashMap<>();
                result.put("❌ 错误", "未知特性: " + feature);
                result.put("支持的特性", "delay(延迟消息), deadletter(死信队列), priority(优先级队列)");
                yield result;
            }
        };
    }
}
