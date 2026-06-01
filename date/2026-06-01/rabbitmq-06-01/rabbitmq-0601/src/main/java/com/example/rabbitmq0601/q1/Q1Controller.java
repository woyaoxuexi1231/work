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
 * Q1: 集群基础与节点元数据同步
 *
 * 演示场景：连接到集群任意节点声明队列，观察元数据同步；
 *          然后通过管理界面或 rabbitmqctl 关掉 master 节点，
 *          验证普通队列不可用。
 *
 * 实战类型：操作文档为主，本 Controller 提供辅助验证接口
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
     * 声明一个普通队列（非镜像、非仲裁）
     * 观察点：队列元数据会同步到所有节点，但消息只存 master
     */
    @GetMapping("/declare")
    public Map<String, Object> declareQueue() {
        // 【重点】普通队列 —— 消息只存在于 master 节点本地磁盘
        Queue queue = new Queue("q1.normal.test", true, false, false);
        String result = rabbitAdmin.declareQueue(queue);

        Map<String, Object> resp = new HashMap<>();
        resp.put("queue", "q1.normal.test");
        resp.put("declared", result);
        resp.put("tip", "元数据已同步到 3 个节点，但消息内容只存 master 本地");
        resp.put("next", "去管理界面 http://192.168.3.100:15672 查看队列详情 → 找到 master 节点 → 用 docker stop 关掉它 → 再调用 GET /q1/test-availability");
        return resp;
    }

    /**
     * 发布 10 条消息到 q1.normal.test
     */
    @GetMapping("/publish")
    public Map<String, Object> publishMessages() {
        int count = 0;
        for (int i = 1; i <= 10; i++) {
            rabbitTemplate.convertAndSend("", "q1.normal.test",
                    "Q1-TEST-MSG-" + i);
            count++;
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("published", count);
        resp.put("queue", "q1.normal.test");
        resp.put("tip", "消息已存入 master 节点本地，现在去管理界面看哪个节点是 master");
        return resp;
    }

    /**
     * 测试队列可用性 —— 在关掉 master 节点后调用
     * 如果 master 宕机，这里会报错
     */
    @GetMapping("/test-availability")
    public Map<String, Object> testAvailability() {
        Map<String, Object> resp = new HashMap<>();

        try {
            //【重点】尝试从宕机 master 的队列消费 —— 会抛异常
            Object msg = rabbitTemplate.receiveAndConvert("q1.normal.test", 2000);
            resp.put("available", true);
            resp.put("message", msg != null ? msg.toString() : "(队列为空)");
            resp.put("verdict", "队列 master 存活，可以正常消费");
        } catch (Exception e) {
            resp.put("available", false);
            resp.put("error", e.getMessage());
            // 【重点】这就是面试题 Q1 的核心现象：
            // 普通队列的 master 宕机 → 队列完全不可用，即使元数据在其他节点上
            resp.put("verdict", "✗ 队列不可用! master 宕机 → 消息锁在宕机节点磁盘上 → 其他节点救不了你 → 这就是为什么需要镜像队列/仲裁队列");
        }
        return resp;
    }
}
