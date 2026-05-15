package com.example.kafka;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/rebalance-test")
public class RebalanceTestController {

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    // 查看当前活跃的监听器容器
    @GetMapping("/containers")
    public String containers() {
        StringBuilder sb = new StringBuilder();
        for (MessageListenerContainer container : registry.getListenerContainers()) {
            sb.append("Container: ").append(container).append(", running: ").append(container.isRunning()).append("\n");
        }
        return sb.toString();
    }

    // 模拟停止一个消费者（会触发再均衡）
    @PostMapping("/stop/{containerId}")
    public String stopConsumer(@PathVariable String containerId) {
        // 注意：实际的 containerId 需要从 registry 获取，这里简化为停止所有
        registry.getListenerContainers().forEach(container -> {
            if (container.isRunning()) {
                container.stop();
            }
        });
        return "消费者已停止，将会触发再均衡";
    }

    // 重启消费者
    @PostMapping("/start")
    public String startConsumer() {
        registry.getListenerContainers().forEach(container -> {
            if (!container.isRunning()) {
                container.start();
            }
        });
        return "消费者已启动，将会触发再均衡";
    }
}