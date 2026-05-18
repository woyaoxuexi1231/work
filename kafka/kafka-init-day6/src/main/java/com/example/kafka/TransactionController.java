package com.example.kafka;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tx")
public class TransactionController {

    @Autowired
    private TransactionalProducerService txService;

    @PostMapping("/order")
    public String createOrder(@RequestParam String orderId, @RequestParam double amount) {
        try {
            txService.sendTransactionalMessages(orderId, amount);
            return "事务提交成功，两条消息已发送";
        } catch (Exception e) {
            return "事务回滚，未发送任何消息: " + e.getMessage();
        }
    }
}