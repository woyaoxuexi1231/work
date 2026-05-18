package com.example.kafka;

import com.example.kafka.dto.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionalProducerService {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 使用 @Transactional 注解（需指定 transactionManager 为 kafkaTransactionManager）
     * 该方法内所有 Kafka 发送操作将被包装在一个事务中，要么全部成功，要么全部失败。
     */
    @Transactional(transactionManager = "kafkaTransactionManager", rollbackFor = Exception.class)
    public void sendTransactionalMessages(String orderId, double amount) {
        // 发送到订单主题
        kafkaTemplate.send("orders", orderId, 
                new Order(orderId, amount, System.currentTimeMillis()));
        
        // 发送到审计主题（同一个事务）
        kafkaTemplate.send("audit", orderId, 
                "Order " + orderId + " created with amount " + amount);
        
        // 模拟异常：如果金额大于 10000，回滚整个事务
        if (amount > 10000) {
            throw new RuntimeException("Amount too large, rollback transaction");
        }

        // 事务提交后应该是会修改消息的状态，确保已提交，没有提交的消息还是会在日志里的，只是消费者不会消费
        // 事务提交后，两条消息同时可见（对 read_committed 消费者）
    }
}