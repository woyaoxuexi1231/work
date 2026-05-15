package com.example.kafka.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderController {

    @Autowired
    private OrderProducerService producerService;

    @PostMapping("/send")
    public String send(@RequestParam String orderId, @RequestParam double amount) {
        producerService.sendOrderWithCustomPartition(orderId, amount);
        return "已发送，分区依据金额确定";
    }

    @PostMapping("/send-with-key")
    public String sendWithKey(@RequestParam String orderId, @RequestParam double amount) {
        producerService.sendOrderWithKey(orderId, amount);
        return "已发送，分区依据 key 的 hash 决定";
    }
}