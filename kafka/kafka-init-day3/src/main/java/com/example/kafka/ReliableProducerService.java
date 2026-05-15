package com.example.kafka;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * @author hulei
 * @since 2026/5/15 13:43
 */

@Slf4j
@Service
public class ReliableProducerService {

    @Autowired
    KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 发送消息并处理发送结果，失败时自动投递到死信队列
     * <p>
     * <b>⚠️ 关于 JsonSerializer 的说明：</b><br>
     * 当前配置了 {@code value-serializer: JsonSerializer}，但本方法参数 {@code value} 为 {@code String} 类型。<br>
     * JsonSerializer 底层使用 Jackson 序列化，传入 {@code String} 时会将其作为 JSON 字符串字面量处理，
     * 即外层再加一对双引号 {@code "..."} 写入 Kafka。<br>
     * 例如传入 {@code {"key":"value"}}，实际写入的消息是 {@code "{"key":"value"}"}（双重编码）。<br>
     * 如果要让 Kafka 中存储真正的 JSON <b>对象</b>，应传入 POJO、{@code Map} 或 {@code JSONObject} 类型，
     * 而不是提前序列化好的 JSON 字符串。
     * <p>
     * <b>⚠️ 关于发送到不存在的 topic 不报错的说明：</b><br>
     * Kafka Broker 默认 {@code auto.create.topics.enable=true}，当 Producer 向不存在的 topic 发送消息时，
     * Broker 会自动创建该 topic，然后消息正常发送成功，因此 {@code whenComplete} 中不会出现异常。<br>
     * 如果要测试发送到不存在的 topic 触发异常的情形，需要在 Broker 端设置
     * {@code auto.create.topics.enable=false}（修改 {@code server.properties} 后重启 Broker）。
     * <p>
     * whenComplete 中 {@code ex}（异常）不为 {@code null} 的常见情况：
     * <ul>
     *   <li>序列化失败 &mdash; key/value 无法被配置的 Serializer 序列化（如对象转 JSON 出错）</li>
     *   <li>发送超时 &mdash; broker 在 {@code max.block.ms} 内未响应，或 {@code request.timeout.ms} 内未收到 ack</li>
     *   <li>Broker 不可用 &mdash; 目标 topic 的 leader 副本宕机或整个集群不可达</li>
     *   <li>Topic 不存在 &mdash; topic 未创建且 {@code auto.create.topics.enable=false}</li>
     *   <li>消息体过大 &mdash; 消息大小超过 {@code max.request.size} 或 {@code batch.size} 限制</li>
     *   <li>权限不足 &mdash; 生产者缺少对 topic 的写入权限（授权失败）</li>
     *   <li>缓冲区满 &mdash; 生产者缓冲区已满，且在 {@code max.block.ms} 内无法腾出空间</li>
     *   <li>网络异常 &mdash; 连接重置、DNS 解析失败、SSL 握手失败等底层网络错误</li>
     *   <li>Ack 不足 &mdash; {@code acks=all} 时，ISR 中的副本数未达到 {@code min.insync.replicas} 要求</li>
     * </ul>
     *
     * @param key   消息键
     * @param value 消息值（当前是 String 类型，会被 JsonSerializer 序列化为 JSON 字符串字面量）
     */
    public void sendWithRetryRecord(String key, String value) {
        CompletableFuture<SendResult<String, Object>> send = kafkaTemplate.send("retry-demo-topic1", key, value);
        send.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("✅ 消息发送成功");
            } else {
                log.info("🚫 发送失败, ", ex);
                JSONObject jsonObject = JSON.parseObject(value);
                jsonObject.put("failureReason", ex.getMessage());
                kafkaTemplate.send("retry-demo-dlq", key, jsonObject.toJSONString());
            }
        });
    }
}
