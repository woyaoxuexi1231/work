package com.example.order;

import org.springframework.web.bind.annotation.*;

@RestController
public class OrderController {

    private final UserClient userClient;

    public OrderController(UserClient userClient) {
        this.userClient = userClient;
    }

    @GetMapping("/order/{id}")
    public String getOrder(@PathVariable Long id) {
        String user = userClient.getUser(id);
        return "order:" + id + ", " + user;
    }
}
