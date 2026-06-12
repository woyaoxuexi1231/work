package com.demo.rocketmq.controller;

import com.demo.rocketmq.producer.RocketmqProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * RocketMQ REST API 入口
 * 提供统一风格的接口用于触发各种测试场景
 */
@Slf4j
@RestController
@RequestMapping("/api/mq")
@RequiredArgsConstructor
public class RocketmqController {

    private final RocketmqProducer rocketmqProducer;

    // ==================== 基础收发 ====================

    /**
     * 基础发送
     * POST /api/mq/send?mode=sync&count=100
     * POST /api/mq/send?mode=async&count=100
     * POST /api/mq/send?mode=oneway&count=100
     */
    @PostMapping("/send")
    public Map<String, Object> send(@RequestParam(defaultValue = "sync") String mode,
                                    @RequestParam(defaultValue = "10") int count) {
        if ("async".equals(mode)) {
            return rocketmqProducer.sendAsync(count);
        } else if ("oneway".equals(mode)) {
            return rocketmqProducer.sendOneWay(count);
        }
        return rocketmqProducer.sendSync(count);
    }

    // ==================== 性能压测 ====================

    /**
     * 性能压测
     * POST /api/mq/bench?count=10000&size=1024
     */
    @PostMapping("/bench")
    public Map<String, Object> bench(@RequestParam(defaultValue = "10000") int count,
                                     @RequestParam(defaultValue = "256") int size) {
        return rocketmqProducer.benchmark(count, size);
    }

    // ==================== 可靠性保障 ====================

    /**
     * 可靠性演示 - 同步发送 + 重试 + Broker 确认
     * POST /api/mq/reliable
     */
    @PostMapping("/reliable")
    public Map<String, Object> reliable() {
        return rocketmqProducer.sendReliable();
    }

    // ==================== 高级特性 ====================

    /**
     * 高级特性入口
     * POST /api/mq/advanced/delay?delayLevel=3     延迟消息（level 1-18）
     * POST /api/mq/advanced/order                   顺序消息
     * POST /api/mq/advanced/transaction             事务消息
     * POST /api/mq/advanced/filter                  消息过滤（Tag）
     */
    @PostMapping("/advanced/{feature}")
    public Map<String, Object> advanced(@PathVariable String feature,
                                        @RequestParam(defaultValue = "3") int delayLevel) {
        if ("delay".equals(feature)) {
            return rocketmqProducer.sendDelay(delayLevel);
        } else if ("order".equals(feature)) {
            return rocketmqProducer.sendOrder();
        } else if ("transaction".equals(feature)) {
            return rocketmqProducer.sendTransaction();
        } else if ("filter".equals(feature)) {
            return rocketmqProducer.sendFilter();
        }
        Map<String, Object> result = new HashMap<>();
        result.put("❌ 错误", "未知特性: " + feature);
        result.put("支持的特性", "delay(延迟消息), order(顺序消息), transaction(事务消息), filter(Tag过滤)");
        return result;
    }
}
