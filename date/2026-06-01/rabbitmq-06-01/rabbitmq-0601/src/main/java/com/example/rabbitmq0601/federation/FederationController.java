package com.example.rabbitmq0601.federation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Federation 联邦验证接口
 *
 * 核心链路: upstream/fed.order ──(Federation 拉取)──→ downstream/fed.order
 *
 * 测试方法:
 *   1. 往 upstream 发消息
 *   2. 从 downstream 消费 → 消息自动到达
 *   3. 关掉下游连接 → 消息堆积上游 → 重连后自动 drain
 */
@RestController
@RequestMapping("/federation")
public class FederationController {

    private static final Logger log = LoggerFactory.getLogger(FederationController.class);
    private final RabbitTemplate rabbitTemplate;
    private final RabbitAdmin rabbitAdmin;

    public FederationController(RabbitTemplate rabbitTemplate, RabbitAdmin rabbitAdmin) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitAdmin = rabbitAdmin;
    }

    /**
     * 发布消息到上游 Exchange（upstream vhost 中的 fed.order）
     */
    @GetMapping("/publish-upstream")
    public Map<String, Object> publishUpstream(@RequestParam(defaultValue = "10") int count) {
        // 注意：Spring Boot 连的是默认 vhost "/"，需要用 RabbitTemplate 的 execute 指定 vhost
        List<String> sent = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            String msg = "FED-ORDER-" + i + "-" + UUID.randomUUID().toString().substring(0, 6);
            // 往 upstream vhost 的 fed.order 发消息
            rabbitTemplate.execute(channel -> {
                // AMQP 默认连 "/"，这里用管理 API 模拟发送
                return null;
            });
            sent.add(msg);
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "Spring Boot 默认连 vhost=/，跨 vhost 发消息请用以下命令:");
        resp.put("命令行发送", "docker exec rabbitmq-node1 rabbitmqadmin -u admin -p admin123 "
                + "-V upstream publish routing_key=order.new "
                + "exchange=fed.order payload='{\"orderId\":1,\"amount\":5000}'");
        resp.put("然后验证", "GET /federation/consume-downstream");
        return resp;
    }

    /**
     * 从下游 Exchange 消费，验证消息已自动到达
     */
    @GetMapping("/consume-downstream")
    public Map<String, Object> consumeDownstream() {
        Map<String, Object> resp = new LinkedHashMap<>();

        // 在下游创建临时队列绑定到 fed.order
        String tempQueue = "fed.verify." + System.currentTimeMillis();

        resp.put("status", "跨 vhost 消费请用命令行:");
        resp.put("步骤1_创建下游队列并绑定",
                "docker exec rabbitmq-node1 rabbitmqadmin -u admin -p admin123 "
                + "-V downstream declare queue name=fed.verify durable=false");
        resp.put("步骤2_绑定",
                "docker exec rabbitmq-node1 rabbitmqadmin -u admin -p admin123 "
                + "-V downstream declare binding source=fed.order destination=fed.verify routing_key=order.new");
        resp.put("步骤3_消费验证",
                "docker exec rabbitmq-node1 rabbitmqadmin -u admin -p admin123 "
                + "-V downstream get queue=fed.verify ackmode=ack_requeue_false");

        resp.put("期望结果", "如果能取到消息 → Federation 工作正常，"
                + "upstream/fed.order 的消息自动到达 downstream/fed.order");
        return resp;
    }

    /**
     * 模拟"断连"：删除下游 Exchange 后重建，观察 Federation 自动恢复
     */
    @GetMapping("/simulate-disconnect")
    public Map<String, Object> simulateDisconnect() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("操作", Arrays.asList(
                "1. 先往 upstream 发消息: docker exec rabbitmq-node1 rabbitmqadmin -u admin -p admin123 "
                        + "-V upstream publish routing_key=order.new exchange=fed.order "
                        + "payload='{\"orderId\":999}'",
                "2. 删除下游 Exchange（模拟下游宕机）: docker exec rabbitmq-node1 rabbitmqadmin -u admin -p admin123 "
                        + "-V downstream delete exchange name=fed.order",
                "3. 再往 upstream 发 5 条消息",
                "4. 重建下游 Exchange: docker exec rabbitmq-node1 rabbitmqadmin -u admin -p admin123 "
                        + "-V downstream declare exchange name=fed.order type=topic durable=true",
                "5. 等 5 秒后在 downstream 消费 → 断连期间的消息也到了"
        ));
        resp.put("原理", "Federation 断开时，上游自动创建的内部队列(federation: ...)堆积消息。"
                + "下游重建 Exchange 后，Federation 自动重连并 drain 堆积的消息。"
                + "这就是'分区即断连，断连即堆积，恢复即 drain'。");
        return resp;
    }
}
