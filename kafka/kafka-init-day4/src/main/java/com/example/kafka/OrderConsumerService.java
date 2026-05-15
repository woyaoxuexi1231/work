package com.example.kafka;

import com.example.kafka.dto.Order;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Service
public class OrderConsumerService {

    private static final Logger log = LoggerFactory.getLogger(OrderConsumerService.class);
    private final AtomicInteger counter = new AtomicInteger(0);

    /**
     * 消费 orders 主题
     * - groupId 与 yml 中的 group-id 一致（也可在此覆盖）
     * - containerFactory 指定使用手动提交的容器工厂
     */
    @KafkaListener(topics = "orders", groupId = "order-consumer-group",
                   containerFactory = "manualAckContainerFactory")
    public void consumeOrder(ConsumerRecord<String, Order> record, Acknowledgment ack) {
        log.info("接收到消息: topic={}, partition={}, offset={}, key={}, value={}",
                record.topic(), record.partition(), record.offset(),
                record.key(), record.value());

        // 模拟业务处理（可能失败）
        processOrder(record.value());

        // 手动提交当前偏移量（表示该消息已成功处理）
        ack.acknowledge();
        log.debug("偏移量 {} 已手动提交", record.offset());
    }

    /**
     * 消费 orders 主题
     * - groupId 与 yml 中的 group-id 一致（也可在此覆盖）
     * - containerFactory 指定使用手动提交的容器工厂
     */
    @KafkaListener(topics = "orders", groupId = "order-consumer-group",
            containerFactory = "manualAckContainerFactory")
    public void consumeOrder2(ConsumerRecord<String, Order> record, Acknowledgment ack) {
        log.info("接收到消息: topic={}, partition={}, offset={}, key={}, value={}",
                record.topic(), record.partition(), record.offset(),
                record.key(), record.value());

        // 模拟业务处理（可能失败）
        processOrder(record.value());

        // 手动提交当前偏移量（表示该消息已成功处理）
        ack.acknowledge();
        log.debug("偏移量 {} 已手动提交", record.offset());
    }

    private void processOrder(Order order) {
        // 模拟耗时处理
        try { Thread.sleep(10); } catch (InterruptedException e) {}
        counter.incrementAndGet();
        log.info("处理订单 {} 完成，总处理数: {}", order.getOrderId(), counter.get());
    }
}