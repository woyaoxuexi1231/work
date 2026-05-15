package com.example.kafka;

import com.example.kafka.dto.Order;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * @author hulei
 * @since 2026/5/15 16:45
 */

@Slf4j
@Component
public class OrderProcessingListener {

    @Autowired
    KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = "order-processing")
    public void receive(ConsumerRecord<String, Order> record, Acknowledgment acknowledgment) {
        log.info("key: {}, offset: {}, partition: {}",
                record.key(),
                record.offset(),
                record.partition()
        );
        Order value = record.value();
        try {
            if (value.getAmount() > 1000) {
            /*
            当发生异常时，不要提交该消息的偏移量，并且：
            将消息的 retryCount 字段加 1。
            如果 retryCount < 3，将修改后的消息重新发送到同一个主题（延迟 1 秒再发，提示：可以使用 Thread.sleep 或定时任务简单处理）。
            如果 retryCount >= 3，将消息发送到一个死信主题 order-processing-dlq（原样发送，不再重试）。
             */
                log.error("订单金额大于 1000，处理失败，订单信息: {}", value);
                throw new RuntimeException("订单金额大于 1000，处理失败");
            } else {
                log.info("订单金额小于 1000，处理成功，订单信息: {}", value);
            }
        } catch (Exception e) {
            if (value.getRetryCount() <= 3) {
                value.setRetryCount(value.getRetryCount() + 1);
                value.setAmount(value.getAmount() - 100);
                // 重发
                log.info("增加重试次数，继续发送消息到 order-processing");
                kafkaTemplate.send("order-processing", value.getOrderId(), value);
            } else {
                // 发送到死信主题
                log.info("无法再重试次数，发送消息到 order-processing-dlq");
                kafkaTemplate.send("order-processing-dlq", value.getOrderId(), value);
            }
        }
    }

    @KafkaListener(topics = "order-processing-dlq")
    public void receive2(ConsumerRecord<String, Order> record, Acknowledgment acknowledgment) {
        log.info("key: {}, offset: {}, partition: {}",
                record.key(),
                record.offset(),
                record.partition()
        );
        Order value = record.value();
        log.info("死信队列接收到消息: {}", value);
    }
}
