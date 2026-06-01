package com.example.rabbitmq0601.q1;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Q1: 集群基础与节点元数据同步 —— 辅助验证接口
 *
 * 实战类型：操作文档（本 Controller 仅提供 HTTP 入口辅助验证）
 */
@RestController
@RequestMapping("/q1")
public class Q1Controller {

    private static final Logger log = LoggerFactory.getLogger(Q1Controller.class);
    private final RabbitTemplate rabbitTemplate;
    private final RabbitAdmin rabbitAdmin;

    public Q1Controller(RabbitTemplate rabbitTemplate, RabbitAdmin rabbitAdmin) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitAdmin = rabbitAdmin;
    }

    /**
     * 声明一个普通队列 —— 非镜像、非仲裁
     * 【重点】普通队列的消息内容只存在于 master 节点本地
     */
    @GetMapping("/declare")
    public Map<String, Object> declare() {
        Queue queue = new Queue("q1.normal.test", true, false, false);
        String result = rabbitAdmin.declareQueue(queue);

        Map<String, Object> resp = new HashMap<>();
        resp.put("queue", "q1.normal.test");
        resp.put("declared", result);
        resp.put("tip", "元数据已同步到 3 个节点，但消息内容只存 master 本地");
        resp.put("next", "请到管理界面查看该队列的 Node 字段，记录 master 是哪个节点");
        return resp;
    }

    /**
     * 发布 10 条消息
     */
    @GetMapping("/publish")
    public Map<String, Object> publish() {
        for (int i = 1; i <= 10; i++) {
            rabbitTemplate.convertAndSend("", "q1.normal.test", "Q1-MSG-" + i);
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("published", 10);
        resp.put("queue", "q1.normal.test");
        return resp;
    }

    /**
     * 测试队列可用性
     * 【重点】如果 master 宕机，receiveAndConvert 会抛异常 —— 队列不可用
     */
    @GetMapping("/test-availability")
    public Map<String, Object> testAvailability() {
        Map<String, Object> resp = new HashMap<>();
        try {
            Object msg = rabbitTemplate.receiveAndConvert("q1.normal.test", 2000);
            resp.put("available", true);
            resp.put("message", msg != null ? msg.toString() : "(队列为空)");
            resp.put("verdict", "队列 master 存活，可以正常消费");
        } catch (Exception e) {
            resp.put("available", false);
            resp.put("error", e.getMessage());
            resp.put("verdict", "✗ 队列不可用! master 宕机 → 消息锁在宕机节点磁盘上 → 其他节点救不了你 → 这就是为什么需要镜像队列/仲裁队列");
        }
        return resp;
    }
}
