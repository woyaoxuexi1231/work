package com.example.rabbitmq0601.q6;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Q6: 跨机房集群架构与网络分区处理
 *
 * 实战类型：操作文档为主 + 代码演示辅助
 *
 * 演示场景：
 *   - 通过 docker network 模拟网络分区
 *   - 观察 pause_minority 策略下的节点行为
 *   - 验证客户端多地址连接 + 自动故障切换
 *
 * 本 Controller 提供辅助接口：查看集群拓扑、验证连接切换、测试消息收发
 */
@RestController
@RequestMapping("/q6")
public class Q6Controller {

    private static final Logger log = LoggerFactory.getLogger(Q6Controller.class);

    private final RabbitTemplate rabbitTemplate;
    private final RabbitAdmin rabbitAdmin;
    private final ConnectionFactory connectionFactory;

    public Q6Controller(RabbitTemplate rabbitTemplate, RabbitAdmin rabbitAdmin,
                        ConnectionFactory connectionFactory) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitAdmin = rabbitAdmin;
        this.connectionFactory = connectionFactory;
    }

    /**
     * 查看当前连接信息 —— 验证客户端连到了哪个节点
     */
    @GetMapping("/connection-info")
    public Map<String, Object> connectionInfo() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("configured_addresses", "192.168.3.100:5672,192.168.3.100:5673,192.168.3.100:5674");
        resp.put("tip", "Spring Boot 客户端会按顺序尝试连接这些地址，"
                + "第一个成功的用于所有后续通信。如果该节点宕机，自动切换到下一个。");
        resp.put("note", "当前连接的具体节点信息可通过管理界面或 DEBUG 日志查看");

        // 【重点】验证连接是否存活
        try {
            rabbitTemplate.execute(channel -> {
                resp.put("connected", true);
                resp.put("connection_host", channel.getConnection().getAddress().getHostName());
                resp.put("connection_port", channel.getConnection().getPort());
                return null;
            });
        } catch (Exception e) {
            resp.put("connected", false);
            resp.put("error", e.getMessage());
        }

        return resp;
    }

    /**
     * 快速发布-消费测试：验证消息通路正常
     *
     * 用于分区前/分区后验证消息能否正常收发
     */
    @GetMapping("/ping-pong")
    public Map<String, Object> pingPong() {
        Map<String, Object> resp = new LinkedHashMap<>();
        String queueName = "q6.health.check";

        try {
            // 声明一个临时队列
            rabbitAdmin.declareQueue(
                    new org.springframework.amqp.core.Queue(queueName, true, false, false));

            // 【重点】发送测试消息
            String testMsg = "PING-" + System.currentTimeMillis();
            rabbitTemplate.convertAndSend("", queueName, testMsg);

            // 立即消费
            Object received = rabbitTemplate.receiveAndConvert(queueName, 3000);

            resp.put("sent", testMsg);
            resp.put("received", received);
            resp.put("通路状态", testMsg.equals(received) ? "✓ 正常" : "✗ 消息不匹配!");
        } catch (Exception e) {
            resp.put("通路状态", "✗ 故障");
            resp.put("error", e.getMessage());
            resp.put("解读", "网络分区或节点宕机导致消息无法收发。"
                    + "客户端会自动重连到其他存活节点。");
        } finally {
            rabbitAdmin.deleteQueue(queueName);
        }

        return resp;
    }

    /**
     * 模拟客户端重连：连续发 20 条消息，中间如果断连自动恢复
     *
     * 用法：在发消息过程中手动执行 docker stop 杀掉当前连接的节点，
     *       观察后 10 条消息的延迟是否突增（重连开销）
     */
    @GetMapping("/stress-with-failover")
    public Map<String, Object> stressWithFailover(@RequestParam(defaultValue = "20") int count) {
        List<Map<String, Object>> results = new ArrayList<>();
        String queueName = "q6.failover.test";

        rabbitAdmin.declareQueue(
                new org.springframework.amqp.core.Queue(queueName, true, false, false));

        for (int i = 1; i <= count; i++) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("seq", i);

            long start = System.nanoTime();
            try {
                rabbitTemplate.convertAndSend("", queueName,
                        "FAILOVER-TEST-" + i + "-" + UUID.randomUUID().toString().substring(0, 6));
                long elapsed = System.nanoTime() - start;
                record.put("status", "OK");
                record.put("latency_us", elapsed / 1000);
            } catch (Exception e) {
                record.put("status", "ERROR");
                record.put("error", e.getMessage().substring(0, Math.min(80, e.getMessage().length())));
            }
            results.add(record);

            // 每发 5 条停 1 秒，给人操作窗口
            if (i % 5 == 0) {
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("results", results);
        resp.put("instructions", "在发送第 5-10 条之间执行 docker stop rabbitmq-node1（当前连接节点），"
                + "观察后续记录的 status 变化：短暂 ERROR → 自动恢复 OK → latency 恢复正常");
        return resp;
    }
}
