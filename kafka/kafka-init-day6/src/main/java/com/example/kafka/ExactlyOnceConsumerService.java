package com.example.kafka;

import com.example.kafka.dto.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExactlyOnceConsumerService {

    @Autowired
    private JdbcTemplate jdbcTemplate;  // 假设有数据库用于记录处理状态

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 消费者：从 orders 主题读取订单，处理后写入数据库，
     * 并同时将偏移量提交作为事务的一部分。
     * 
     * 需要配置 ChainedKafkaTransactionManager 或使用 KafkaTransactionManager +
     * DataSourceTransactionManager 的链式管理器。
     * 
     * 为简化，这里只演示思路：在 @Transactional 中同时操作 DB 和 Kafka
     */
    @KafkaListener(topics = "orders", groupId = "eos-consumer-group")
    @Transactional(value = "kafkaTransactionManager", rollbackFor = Exception.class)
    public void onMessage(Order order) {
        // 1. 插入数据库（唯一约束防重，实现幂等）
        jdbcTemplate.update("INSERT INTO `order` (order_id, amount, timestamp) VALUES (?, ?, CURRENT_TIME)",
                order.getOrderId(), order.getAmount());
        
        // 2. 发送下游结果消息（与数据库更新同一个事务）
        kafkaTemplate.send("order-results", order.getOrderId(), "PROCESSED");

        // 默认 org.springframework.kafka.listener.DefaultErrorHandler ，会重试 10 次，没有提交偏移量，每次重启都会重新消费
        throw new RuntimeException("模拟异常");
        // 如果任一操作失败，事务回滚，Kafka 偏移量也不会提交（需要手动 ack 与事务绑定）
        // 这里简单起见，假设使用 AUTO 模式并绑定事务管理器。
    }
}