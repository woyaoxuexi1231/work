package com.example.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

@Service
public class RebalanceConsumerService {

    private static final Logger log = LoggerFactory.getLogger(RebalanceConsumerService.class);

    // 这里无法直接注入监听器，因为监听器是每个消费者独立的。
    // 简便方法：通过 ThreadLocal 传递，或者将偏移量记录到外部存储，
    // 然后监听器从外部存储读取。为演示，我们简单打印日志。
    // 实际项目中可以将监听器绑定到 consumer 的上下文。

    @KafkaListener(topics = "orders-string", groupId = "rebalance-demo-group",
            containerFactory = "rebalanceAwareContainerFactory")
    public void listen(ConsumerRecord<String, Object> record,
                       Acknowledgment ack,
                       @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                       @Header(KafkaHeaders.OFFSET) long offset) {
        log.info("消费消息: partition={}, offset={}, value={}", partition, offset, record.value());

        // 模拟业务处理
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
        }

        // 手动提交偏移量（注意：配合再均衡监听器，最好在监听器中统一提交）
        // 但为了演示，我们也手动提交。
        ack.acknowledge();

        // 实际中如果需要记录偏移量到监听器，可以通过存储服务或回调方式，
        // 这里省略，重点展示再均衡触发时的日志。
    }
}