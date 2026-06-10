package com.demo.mybatis.entity;

import java.util.List;

/**
 * 用户 — 一对多中的"一"方
 * 一个用户可以有多个订单
 */
public class User {
    private Long id;
    private String name;

    // ▼ collection：一对多 — 用户的订单列表
    private List<Order> orders;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<Order> getOrders() { return orders; }
    public void setOrders(List<Order> orders) { this.orders = orders; }

    @Override
    public String toString() {
        return "User{id=" + id + ", name='" + name + "', orders=" + orders + "}";
    }
}
