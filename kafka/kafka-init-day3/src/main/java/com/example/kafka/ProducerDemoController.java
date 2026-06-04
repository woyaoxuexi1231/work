package com.example.kafka;

import com.alibaba.fastjson2.JSON;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@RestController
@RequestMapping("/producer-demo")
public class ProducerDemoController {

    @Autowired
    private AdvancedProducerService producerService;

    // 1. 发后即忘
    @PostMapping("/fire")
    public String fire(@RequestBody Map<String, Object> msg) {
        producerService.sendFireAndForget("orders", (String) msg.get("key"), msg.get("data"));
        return "Fire-and-forget sent";
    }

    // 2. 同步发送
    @PostMapping("/sync")
    public String sync(@RequestBody Map<String, Object> msg) throws ExecutionException, InterruptedException, TimeoutException {
        producerService.sendSync("orders", (String) msg.get("key"), msg.get("data"));
        return "Sync sent";
    }

    // 3. 异步发送
    @PostMapping("/async")
    public String async(@RequestBody Map<String, Object> msg) {
        producerService.sendAsync("orders", (String) msg.get("key"), msg.get("data"));
        return "Async sent (callback will log)";
    }


    @Autowired
    ReliableProducerService reliableProducerService;

    @GetMapping("/reliable/send")
    public void send() {
        Map<String, Object> msg = java.util.Collections.singletonMap("key", "value");
        reliableProducerService.sendWithRetryRecord("key", "asjfgdaskjfhh");
    }

}