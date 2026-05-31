package com.example.springqa.Q11_CustomAop;

class Q11OrderServiceImpl implements Q11OrderService {
    @Q11Loggable
    @Override public void createOrder(String item) {
        System.out.println("    📦 创建订单: " + item);
    }

    @Override public void cancelOrder(String orderId) {
        System.out.println("    🗑 取消订单: " + orderId);
    }

    @Q11Loggable
    @Override public String queryOrder(String orderId) {
        System.out.println("    🔍 查询订单: " + orderId);
        return "Order{id=" + orderId + "}";
    }
}
