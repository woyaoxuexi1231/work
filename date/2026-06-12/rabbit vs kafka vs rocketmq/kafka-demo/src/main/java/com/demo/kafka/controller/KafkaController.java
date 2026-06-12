package com.demo.kafka.controller;

import com.demo.kafka.producer.KafkaProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka REST API 入口
 * 提供统一风格的接口用于触发各种测试场景
 */
@Slf4j
@RestController
@RequestMapping("/api/mq")
@RequiredArgsConstructor
public class KafkaController {

    private final KafkaProducer kafkaProducer;

    // ==================== 基础收发 ====================

    /**
     * 基础发送
     * POST /api/mq/send?mode=basic&count=100
     * POST /api/mq/send?mode=key&count=100
     */
    @PostMapping("/send")
    public Map<String, Object> send(@RequestParam(defaultValue = "basic") String mode,
                                    @RequestParam(defaultValue = "10") int count) {
        if ("key".equals(mode)) {
            return kafkaProducer.sendWithKey(count);
        }
        return kafkaProducer.sendBasic(count);
    }

    // ==================== 性能压测 ====================

    /**
     * 性能压测
     * POST /api/mq/bench?count=10000&size=1024
     */
    @PostMapping("/bench")
    public Map<String, Object> bench(@RequestParam(defaultValue = "10000") int count,
                                     @RequestParam(defaultValue = "256") int size) {
        return kafkaProducer.benchmark(count, size);
    }

    // ==================== 可靠性保障 ====================

    /**
     * 可靠性演示 - acks=all + 幂等生产者 + 重试
     * POST /api/mq/reliable
     */
    @PostMapping("/reliable")
    public Map<String, Object> reliable() {
        return kafkaProducer.sendReliable();
    }

    // ==================== 高级特性 ====================

    /**
     * 高级特性入口
     * POST /api/mq/advanced/order     顺序消息
     * POST /api/mq/advanced/transaction 事务消息（Exactly-Once）
     * POST /api/mq/advanced/compress?count=1000  压缩对比
     */
    @PostMapping("/advanced/{feature}")
    public Map<String, Object> advanced(@PathVariable String feature,
                                        @RequestParam(defaultValue = "1000") int count) {
        if ("order".equals(feature)) {
            return kafkaProducer.sendOrder();
        } else if ("transaction".equals(feature)) {
            return kafkaProducer.sendTransaction();
        } else if ("compress".equals(feature)) {
            return kafkaProducer.sendCompressed(count);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("❌ 错误", "未知特性: " + feature);
        result.put("支持的特性", "order(顺序消息), transaction(事务消息/Exactly-Once), compress(压缩对比)");
        return result;
    }
}
