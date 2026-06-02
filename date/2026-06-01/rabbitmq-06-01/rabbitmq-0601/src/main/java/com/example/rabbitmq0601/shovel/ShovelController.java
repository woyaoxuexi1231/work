package com.example.rabbitmq0601.shovel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Shovel 搬运验证接口
 *
 * 核心链路: upstream/orders.source ──(Shovel, on-confirm)──→ downstream/ex.order → warehouse.inbound
 *
 * 测试方法:
 *   1. 往源队列发消息 → Shovel 自动搬运到目标队列
 *   2. 删除目标队列 → 消息堆积源队列 → 重建后自动恢复
 *   3. 对比 ack-mode: on-confirm vs on-publish 的行为差异
 */
@RestController
@RequestMapping("/shovel")
public class ShovelController {

    private static final Logger log = LoggerFactory.getLogger(ShovelController.class);

    /**
     * 完整测试流程
     */
    @GetMapping("/test")
    public Map<String, Object> test() {
        Map<String, Object> resp = new LinkedHashMap<>();
        List<Map<String, String>> steps = new ArrayList<>();

        steps.add(step("1", "往源队列发消息",
                "docker exec rabbitmq-node1 rabbitmqadmin -u admin -p admin123 "
                + "-V upstream publish routing_key=order.new "
                + "exchange=ex.order payload='{\"orderId\":1,\"amount\":5000}'",
                "Shovel 检测到 orders.source 有新消息 → 消费 → 发布到 downstream/ex.order"));
        steps.add(step("2", "验证目标端收到",
                "docker exec rabbitmq-node1 rabbitmqadmin -u admin -p admin123 "
                + "-V downstream get queue=warehouse.inbound ackmode=ack_requeue_false",
                "如果取到消息 → Shovel 搬运成功"));
        steps.add(step("3", "模拟目标不可达",
                "docker exec rabbitmq-node1 rabbitmqadmin -u admin -p admin123 "
                + "-V downstream delete queue name=warehouse.inbound",
                "删除目标队列后，Shovel 无法发布"));
        steps.add(step("4", "发消息观察堆积",
                "连续发 10 条消息到 orders.source，然后查看源队列消息数:",
                "docker exec rabbitmq-node1 rabbitmqctl list_queues -p upstream name messages 2>&1"));
        steps.add(step("5", "重建目标，观察恢复",
                "重建 warehouse.inbound 并绑定 → Shovel 自动重连 → 堆积的消息全部 drain",
                "docker exec rabbitmq-node1 rabbitmqadmin -u admin -p admin123 "
                + "-V downstream declare queue name=warehouse.inbound durable=true"));
        steps.add(step("★", "原理",
                "ack-mode: on-confirm → Shovel 只有等目标 confirm 后才 ack 源。",
                "目标不可达 → 源队列消息保持 unacked → 不丢。这是端到端可靠传递。"));

        resp.put("测试流程", steps);
        return resp;
    }

    /**
     * 对比 ack-mode
     */
    @GetMapping("/ack-mode-compare")
    public Map<String, Object> ackModeCompare() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("on-confirm（当前配置）", "目标 Broker confirm 后，才 ack 源。端到端可靠，延迟高（至少一个 RTT）。");
        resp.put("on-publish", "消息发到目标 socket 缓冲区就 ack 源。快，但目标宕机时可能丢消息。");
        resp.put("no-ack", "消费源时不 ack。纯 fire-and-forget，最快但最不可靠。");
        resp.put("当前配置", "on-confirm → 适合金融/订单等不允许丢消息的场景");
        return resp;
    }

    /**
     * 查看 Shovel 状态
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("管理界面", "http://192.168.3.100:15672/#/shovels");
        resp.put("命令行", "docker exec rabbitmq-node1 rabbitmqctl shovel_status 2>&1");
        resp.put("源队列消息数", "docker exec rabbitmq-node1 rabbitmqctl list_queues -p upstream name messages 2>&1 | grep orders");
        resp.put("目标队列消息数", "docker exec rabbitmq-node1 rabbitmqctl list_queues -p downstream name messages 2>&1 | grep warehouse");
        return resp;
    }

    private Map<String, String> step(String num, String title, String cmd, String expect) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("步骤", num);
        m.put("操作", title);
        m.put("命令", cmd);
        m.put("预期", expect);
        return m;
    }
}
