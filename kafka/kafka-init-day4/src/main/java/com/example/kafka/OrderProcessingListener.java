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
 * 订单处理监听器 — 包含重试与死信队列示例。
 * <p>
 * <b>1. Kafka 没有 ack 时的处理逻辑（对比 RabbitMQ）</b>
 * <p>
 * RabbitMQ 中，如果消息未 ack，该消息会保持在「Unacked」状态，
 * 消费者不会收到下一条消息（默认 basicQoS=1），直到 ack 或 connection 关闭后重新入队。
 * <p>
 * <b>Kafka 完全不同：</b>
 * <ul>
 *   <li>Kafka 不跟踪「单条消息的确认状态」，而是跟踪「分区偏移量（offset）」</li>
 *   <li>消费者的 {@code poll()} 每次返回一批消息，消费位置（position）在客户端内存中推进，
 *       与是否调用 {@code ack.acknowledge()} 无关</li>
 *   <li><b>不调用 acknowledge() 的效果：</b>
 *     <br>当前运行期间 → 消费者依旧正常拉取后续消息，不受任何影响
 *     <br>重启/rebalance 后 → 消费者从<em>最后已提交的 offset</em> 重新消费，
 *       导致未 ack 的消息会被重新消费一次（at-least-once 语义）</li>
 * </ul>
 *
 * <b>2. 没有 ack 后续消息还能接收吗？</b>
 * <p>
 * <b>能。</b>消费者内部维护一个本地 position 指针，每次 {@code poll()} 后自动推进。
 * offset 提交与消息消费是两个独立的过程：
 * <pre>
 *   poll() 拉取一批消息 ──→ position 前进 ──→ 处理消息
 *                                                    ↓
 *   下次 poll() 从 position 位置继续拉      ack.acknowledge() 提交 offset
 *                                         （仅影响重启后的恢复点）
 * </pre>
 * 所以即使整个生命周期都不调用 acknowledge()，当前运行时每条消息也只会被处理一次。
 * 代价是重启后会重复消费。<b>因此生产环境必须确保正确处理并提交 offset。</b>
 * <p>
 * <b>3. {@code max-poll-records} 的作用</b>
 * <p>
 * 即 {@link org.apache.kafka.clients.consumer.ConsumerConfig#MAX_POLL_RECORDS_CONFIG}，
 * 控制单次 {@code poll()} 最多返回的记录数。当前配置为 50。
 * <ul>
 *   <li>防止一次拉取过多消息导致内存溢出或处理超时</li>
 *   <li>配合 {@code max-poll-interval-ms}（当前 5min），确保在这段时间内能处理完
 *       本次 poll 的所有消息，否则消费者会被视为死亡触发 rebalance</li>
 *   <li>可根据每条消息的处理耗时适当调整：处理慢则设小，处理快则设大</li>
 * </ul>
 *
 * <b>4. Kafka 拉取消息的逻辑</b>
 * <p>
 * Kafka 采用 <b>Pull（拉）模型</b>，不是服务器主动 Push。
 * <p>
 * <b>执行流程：</b>
 * <ol>
 *   <li>消费者通过 {@code poll()} 发起 Fetch 请求到 broker</li>
 *   <li>Broker 检查该分区是否有新数据：
 *     <br>- 有数据 → 立即返回
 *     <br>- 无数据 → 保持连接等待（长轮询，最久 {@code fetch-max-wait-ms}=500ms）
 *     <br>- 等待期间新消息到达 → 立即返回</li>
 *   <li>消费者处理这批消息后，再次调用 {@code poll()} 拉取下一批</li>
 * </ol>
 * <b>关键点：</b>
 * <ul>
 *   <li>没有后台线程，没有定时任务 — {@code poll()} 是同步阻塞调用</li>
 *   <li>消费者必须<em>持续循环</em>调用 {@code poll()}，
 *       超过 {@code max-poll-interval-ms} 不调用 poll 会被判定死亡</li>
 *   <li>心跳由 {@code poll()} 内部自动发送，不需要额外线程</li>
 *   <li>这种设计让消费者自己控制处理节奏（Back Pressure），处理慢就少 poll，
 *       Broker 不会压垮消费者</li>
 * </ul>
 *
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
                acknowledgment.acknowledge();
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
        acknowledgment.acknowledge();
    }
}
