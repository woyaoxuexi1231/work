package com.demo.rocketmq.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.messaging.Message;

/**
 * RocketMQ 事务消息监听器
 * 实现本地事务执行和事务回查逻辑
 *
 * 流程：
 * 1. 生产者发送半消息（Half Message）到 Broker
 * 2. Broker 返回成功后，回调 executeLocalTransaction() 执行本地事务
 * 3. 根据本地事务结果返回 COMMIT / ROLLBACK / UNKNOWN
 * 4. 如果返回 UNKNOWN，Broker 会定时回调 checkLocalTransaction() 进行回查
 */
@Slf4j
@RocketMQTransactionListener
public class RocketmqTransactionListener implements RocketMQLocalTransactionListener {

    /**
     * 执行本地事务
     * 在半消息发送成功后被调用
     */
    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        String payload = (String) msg.getPayload();
        log.info("✅ [TX-Listener] 执行本地事务: payload={}, arg={}", payload, arg);

        try {
            // 模拟本地事务处理（如：扣库存、创建订单等）
            // 这里简单模拟成功
            boolean localTxSuccess = true;

            if (localTxSuccess) {
                log.info("✅ [TX-Listener] 本地事务执行成功，提交消息");
                return RocketMQLocalTransactionState.COMMIT;
            } else {
                log.warn("⚠️ [TX-Listener] 本地事务执行失败，回滚消息");
                return RocketMQLocalTransactionState.ROLLBACK;
            }
        } catch (Exception e) {
            log.error("🚫 [TX-Listener] 本地事务异常，返回UNKNOWN等待回查: {}", e.getMessage());
            return RocketMQLocalTransactionState.UNKNOWN;
        }
    }

    /**
     * 事务回查
     * 当 executeLocalTransaction 返回 UNKNOWN 或 Broker 未收到确认时，
     * Broker 会定时调用此方法回查本地事务状态（最多回查 15 次）
     */
    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        String payload = (String) msg.getPayload();
        log.info("⚠️ [TX-Listener] 事务回查: payload={}", payload);

        // 实际项目中应该查询本地事务表（如 t_order_tx_log）
        // 这里模拟返回 COMMIT
        return RocketMQLocalTransactionState.COMMIT;
    }
}
