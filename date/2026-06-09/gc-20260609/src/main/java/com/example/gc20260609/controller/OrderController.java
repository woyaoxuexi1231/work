package com.example.gc20260609.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * <h2>订单接口 —— 被 Full GC STW "波及"的业务接口</h2>
 *
 * <h3>🎯 这些接口本身没有问题</h3>
 * <p>
 * 这些接口的代码逻辑非常简单，正常情况下响应时间在 1~50ms。<br>
 * 但当 Full GC 发生时，JVM 会暂停所有应用线程（STW），<br>
 * 此时正在执行的请求会被"卡住"，响应时间从正常值飙升到 2~5 秒。
 * </p>
 *
 * <h3>📊 如何观察毛刺</h3>
 * <pre>
 * # 用循环持续请求，观察响应时间变化
 * # PowerShell:
 * while ($true) { $r = Invoke-WebRequest -Uri "http://localhost:8200/api/orders" -UseBasicParsing; Write-Host "$($r.Headers['X-Response-Time']) $($r.StatusCode)"; Start-Sleep -Milliseconds 500 }
 *
 * # Linux/Mac:
 * while true; do curl -s -w "\n" -o /dev/null http://localhost:8200/api/orders; sleep 0.5; done
 * </pre>
 * <p>
 * 正常情况下每次响应约 10~50ms，但每隔几分钟会出现一次 2000ms+ 的响应——这就是 Full GC STW 造成的毛刺。
 * </p>
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    /** 请求计数器 */
    private final AtomicLong requestCount = new AtomicLong(0);

    /** 慢请求计数器（响应时间 > 500ms 的请求） */
    private final AtomicLong slowRequestCount = new AtomicLong(0);

    /**
     * 订单列表接口
     * <p>
     * 模拟一个正常的业务接口：查询订单列表。<br>
     * 正常耗时 ~10ms，Full GC 时 ~2000-5000ms。
     * </p>
     */
    @GetMapping
    public Map<String, Object> listOrders() {
        long start = System.currentTimeMillis();

        // 模拟正常的业务逻辑（查询、组装数据）
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> orders = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> order = new LinkedHashMap<>();
            order.put("id", 1000 + i);
            order.put("orderNo", "ORD-" + System.currentTimeMillis() + "-" + i);
            order.put("amount", ThreadLocalRandom.current().nextDouble(10, 999));
            order.put("status", new String[]{"PAID", "SHIPPED", "DELIVERED", "REFUND"}[i % 4]);
            orders.add(order);
        }
        result.put("orders", orders);
        result.put("total", orders.size());

        long cost = System.currentTimeMillis() - start;
        recordRequest(cost);
        result.put("costMs", cost);

        return result;
    }

    /**
     * 订单详情接口
     */
    @GetMapping("/{id}")
    public Map<String, Object> getOrder(@PathVariable Long id) {
        long start = System.currentTimeMillis();

        Map<String, Object> order = new LinkedHashMap<>();
        order.put("id", id);
        order.put("orderNo", "ORD-" + System.currentTimeMillis());
        order.put("userId", ThreadLocalRandom.current().nextLong(10000, 99999));
        order.put("amount", ThreadLocalRandom.current().nextDouble(10, 999));
        order.put("items", 3);
        order.put("status", "PAID");
        order.put("createTime", System.currentTimeMillis());

        long cost = System.currentTimeMillis() - start;
        recordRequest(cost);
        order.put("costMs", cost);

        return order;
    }

    /**
     * 订单搜索接口 —— 模拟稍复杂的业务逻辑
     * <p>
     * 正常耗时 ~30-50ms，Full GC 时同样会被 STW 卡住。
     * </p>
     */
    @GetMapping("/search")
    public Map<String, Object> searchOrders() {
        long start = System.currentTimeMillis();

        // 模拟一个稍复杂的业务操作（循环计算 + 字符串拼接）
        List<Map<String, Object>> results = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", 2000 + i);
            item.put("keyword", "商品-" + i);
            item.put("score", ThreadLocalRandom.current().nextDouble(0, 100));
            results.add(item);
        }

        // 模拟排序
        results.sort((a, b) -> Double.compare((double) b.get("score"), (double) a.get("score")));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("results", results.subList(0, Math.min(10, results.size())));
        response.put("totalHits", results.size());

        long cost = System.currentTimeMillis() - start;
        recordRequest(cost);
        response.put("costMs", cost);

        return response;
    }

    /**
     * 记录请求统计
     */
    private void recordRequest(long costMs) {
        long total = requestCount.incrementAndGet();
        if (costMs > 500) {
            long slow = slowRequestCount.incrementAndGet();
            log.warn("🚫 [OrderController] ⚡ 检测到慢请求! cost={}ms | 累计慢请求={}/{}", costMs, slow, total);
        }
    }
}
