package com.example.springqa.Q15_DistributedTransaction;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class Q15TccInventoryService {
    private final Map<String, Integer> frozen = new ConcurrentHashMap<>();
    private final Map<String, Integer> stock = new ConcurrentHashMap<>();
    { stock.put("PROD-001", 100); }

    void tryFreeze(String product, int qty) {
        if (stock.getOrDefault(product, 0) < qty) throw new RuntimeException("库存不足");
        stock.merge(product, -qty, Integer::sum);
        frozen.merge(product, qty, Integer::sum);
    }
    void confirmDeduct(String product, int qty) { frozen.merge(product, -qty, Integer::sum); }
    void cancelFreeze(String product, int qty) {
        frozen.merge(product, -qty, Integer::sum);
        stock.merge(product, qty, Integer::sum);
    }
}

class Q15TccOrderService {
    private final Map<String, String> orders = new ConcurrentHashMap<>();
    void tryCreate(String id, String product, int qty) { orders.put(id, "PENDING"); }
    void confirmOrder(String id) { orders.put(id, "CONFIRMED"); }
    void cancelOrder(String id) { orders.put(id, "CANCELLED"); }
    String getStatus(String id) { return orders.get(id); }
}

class Q15LocalMessageTable {
    static class Msg { String status = "PENDING"; }
    private final Map<String, Msg> messages = new ConcurrentHashMap<>();
    void insert(String id, String type, String payload) { messages.put(id, new Msg()); }
    void scanAndSend() { messages.values().forEach(m -> m.status = "SENT"); }
}
