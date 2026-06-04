package com.example.kafka;

import com.example.kafka.dto.Order;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.AcknowledgingMessageListener;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.TopicPartitionOffset;
import org.springframework.stereotype.Component;

/**
 * <b>assign 模式消费者</b> — 手动指定分区，不通过消费者组协调。
 * <p>
 * ─────────────────────────────────────────────────────────────────
 * <b>assign 模式 vs {@code @KafkaListener}（subscribe 模式）的区别</b>
 * ─────────────────────────────────────────────────────────────────
 * <table border="1">
 *   <tr>
 *     <th>对比维度</th>
 *     <th>assign 模式（本类）</th>
 *     <th>{@code @KafkaListener}（subscribe 模式）</th>
 *   </tr>
 *   <tr>
 *     <td><b>分区分配</b></td>
 *     <td>手动精确指定分区（如只消费 orders 的 0、1 分区）</td>
 *     <td>由消费者组协调器自动分配，组内消费者负载均衡</td>
 *   </tr>
 *   <tr>
 *     <td><b>消费者组</b></td>
 *     <td>不参与消费者组，无组协调，无 rebalance</td>
 *     <td>必须属于一个消费者组，自动处理 rebalance</td>
 *   </tr>
 *   <tr>
 *     <td><b>偏移量管理</b></td>
 *     <td>完全手动管理偏移量，不会自动提交，需调用 {@code ack.acknowledge()}</td>
 *     <td>可自动提交（{@code enable-auto-commit=true}）或手动提交</td>
 *   </tr>
 *   <tr>
 *     <td><b>弹性扩缩</b></td>
 *     <td>消费者增减不会触发 rebalance，需手动调整代码</td>
 *     <td>自动 rebalance，消费者增减自动调整分区分配</td>
 *   </tr>
 *   <tr>
 *     <td><b>适用场景</b></td>
 *     <td>测试/调试、精确控制消费哪些分区、有状态消费（如从指定 offset 开始重放）</td>
 *     <td>生产环境主流用法，高可用、容错、弹性扩缩容</td>
 *   </tr>
 * </table>
 * <p>
 * ⚠️ <b>注意：</b>assign 模式下，即使配置了 {@code group-id} 也不会参与组协调，
 * 偏移量也不会自动提交到 {@code __consumer_offsets} 主题，需要完全手动管理。
 */
@Component
public class AssignPartitionConsumer {

    private static final Logger log = LoggerFactory.getLogger(AssignPartitionConsumer.class);

    @Autowired
    private ConsumerFactory<String, Order> consumerFactory;

    // @PostConstruct
    public void startConsumer() {
        // ── 1. 用 TopicPartitionOffset 精确指定要消费的分区（不通过消费者组） ──
        ContainerProperties props = new ContainerProperties(
                new TopicPartitionOffset("orders", 0),  // 分区 0
                new TopicPartitionOffset("orders", 1)   // 分区 1
        );

        // ── 2. 手动确认模式 ──
        // 处理成功后必须调用 ack.acknowledge() 才会提交偏移量
        props.setAckMode(ContainerProperties.AckMode.MANUAL);

        // ── 3. 设置消息监听器（使用 AcknowledgingMessageListener 获取 Acknowledgment） ──
        props.setMessageListener((AcknowledgingMessageListener<String, Order>) (record, ack) -> {
            try {
                log.info("Assign消费: partition={}, offset={}, value={}",
                        record.partition(), record.offset(), record.value());

                // TODO: 业务处理...

                // ✅ 手动提交偏移量 —— 只有调用 acknowledge() 才算消费成功
                // 调用后，下次重启会从该 offset+1 开始消费
                ack.acknowledge();
                log.info("✅ 偏移量 {} 已手动提交", record.offset());

            } catch (Exception e) {
                // 🚫 处理失败时，根据业务决定提交策略：
                //   - 不调用 acknowledge() → 下次重启会重新消费此消息（at-least-once 语义）
                //   - 调用 acknowledge()   → 跳过此消息（at-most-once 语义），
                //     通常配合死信队列（DLQ）将失败消息投递到专门的主题
                log.error("🚫 消费失败: partition={}, offset={}, value={}",
                        record.partition(), record.offset(), record.value(), e);
            }
        });

        // ── 4. 创建并启动容器 ──
        // assign 模式下 concurrency 不能超过指定的分区数
        ConcurrentMessageListenerContainer<String, Order> container =
                new ConcurrentMessageListenerContainer<>(consumerFactory, props);
        container.setConcurrency(1);  // 1 个线程处理 2 个分区
        container.start();

        log.info("✅ Assign 消费者已启动，手动指定消费 orders 分区 0 和 1");
    }
}