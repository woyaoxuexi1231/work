package com.example.springqa.Q11_CustomAop;

interface Q11OrderService {
    void createOrder(String item);
    void cancelOrder(String orderId);
    String queryOrder(String orderId);
}
