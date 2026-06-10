package com.demo.jdk21.service;

import com.demo.jdk21.dto.OrderDTO;
import com.demo.jdk21.dto.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * <h3>JDK 21 终极写法 —— 虚线程改变一切</h3>
 *
 * <p>JDK 21 核心特性：Virtual Threads（虚线程）—— 彻底告别 CompletableFuture 回调地狱</p>
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final List<OrderDTO> orders = Collections.synchronizedList(new ArrayList<>());
    private long idCounter = 0;

    public OrderDTO createOrder(String orderNo, BigDecimal amount) {
        OrderDTO order = new OrderDTO(++idCounter, orderNo, amount,
                new OrderStatus.Pending(), LocalDateTime.now());
        orders.add(order);
        log.info("✅ 创建订单: {}", order);
        return order;
    }

    public Optional<OrderDTO> getOrder(Long id) {
        return orders.stream().filter(o -> o.id().equals(id)).findFirst();
    }

    public List<OrderDTO> getAllOrders() {
        return new ArrayList<>(orders);
    }

    // ========================================================================
    // 🔥 核心改进：虚线程（对比 JDK 8/17 的 CompletableFuture）
    // ========================================================================

    /**
     * <h4>虚线程批量并发调用 —— 同一个业务逻辑，三个版本的写法对比</h4>
     *
     * <pre>
     * // JDK 8：CompletableFuture + 平台线程池（每个线程 ~1MB）
     * ExecutorService executor = Executors.newFixedThreadPool(3);
     * var f1 = CompletableFuture.supplyAsync(() -> callApi(), executor);
     * var f2 = CompletableFuture.supplyAsync(() -> callApi(), executor);
     * var f3 = CompletableFuture.supplyAsync(() -> callApi(), executor);
     * CompletableFuture.allOf(f1, f2, f3).join();
     * executor.shutdown();
     *
     * // JDK 21：虚线程，写同步代码获得异步性能！每个虚线程 ~1KB
     * try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
     *     var f1 = executor.submit(() -> callApi());
     *     var f2 = executor.submit(() -> callApi());
     *     var f3 = executor.submit(() -> callApi());
     *     return Map.of("a", f1.get(), "b", f2.get(), "c", f3.get());
     * }
     * // 不需要 shutdown()！try-with-resources 自动关闭
     * </pre>
     */
    public Map<String, Object> batchQueryExternal(Long orderId) {
        log.info("📡 开始批量查询（当前线程: {}, 虚线程: {}）",
                Thread.currentThread().getName(), Thread.currentThread().isVirtual());

        // JDK 21：每个任务一个虚线程，I/O 阻塞时自动让出载体线程
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            Future<String> orderFuture = executor.submit(() -> {
                log.info("📡 调用订单详情 API（线程: {}, 虚线程: {}）",
                        Thread.currentThread().getName(), Thread.currentThread().isVirtual());
                simulateSlowApi();
                return "order-detail-" + orderId;
            });

            Future<String> payFuture = executor.submit(() -> {
                log.info("📡 调用支付信息 API（线程: {}, 虚线程: {}）",
                        Thread.currentThread().getName(), Thread.currentThread().isVirtual());
                simulateSlowApi();
                return "payment-info-" + orderId;
            });

            Future<String> logisticsFuture = executor.submit(() -> {
                log.info("📡 调用物流信息 API（线程: {}, 虚线程: {}）",
                        Thread.currentThread().getName(), Thread.currentThread().isVirtual());
                simulateSlowApi();
                return "logistics-info-" + orderId;
            });

            // 直接 get()，像同步代码一样简单
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("order", orderFuture.get());
            result.put("payment", payFuture.get());
            result.put("logistics", logisticsFuture.get());
            result.put("threadType", "virtual-thread（每个 ~1KB，不是平台线程的 ~1MB）");
            result.put("codeStyle", "同步写法，无 CompletableFuture 回调");
            return result;

        } catch (Exception e) {
            log.error("🚫 批量查询失败: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    // ========================================================================
    // 🔥 高并发压测：100 个并发，虚线程 vs 平台线程
    // ========================================================================

    /**
     * <h4>虚线程高并发演示</h4>
     * <p>JDK 8：100 个平台线程 ≈ 100MB 栈内存</p>
     * <p>JDK 21：100 个虚线程 ≈ 100KB 内存</p>
     */
    public Map<String, Object> highConcurrencyDemo(int concurrencyCount) {
        log.info("🚀 高并发演示: {} 个并发任务", concurrencyCount);
        long start = System.currentTimeMillis();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < concurrencyCount; i++) {
                final int taskId = i;
                futures.add(executor.submit(() -> {
                    simulateSlowApi(); // 2秒 I/O
                    return "task-" + taskId;
                }));
            }

            long successCount = futures.stream().filter(f -> {
                try { f.get(); return true; } catch (Exception e) { return false; }
            }).count();

            long costMs = System.currentTimeMillis() - start;
            return Map.of(
                    "concurrencyCount", concurrencyCount,
                    "successCount", successCount,
                    "costMs", costMs,
                    "threadType", "virtual-thread",
                    "memoryUsage", concurrencyCount + " 个虚线程 ≈ " + concurrencyCount + "KB（平台线程需要 ~" + concurrencyCount + "MB）"
            );
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    // ========================================================================
    // JDK 21 其他特性：Record Patterns、Sequenced Collections
    // ========================================================================

    /**
     * Record Patterns（JDK 21）：直接在 instanceof 中解构 Record
     *
     * <pre>
     * // JDK 17：instanceof 模式匹配（绑定整个对象）
     * if (obj instanceof OrderDTO order) { order.orderNo(); }
     *
     * // JDK 21：Record Pattern（直接解构每个字段）
     * if (obj instanceof OrderDTO(var id, var no, var amount, var status, var time)) { no; }
     * </pre>
     */
    public String describeOrder(Object obj) {
        if (obj instanceof OrderDTO(var id, var orderNo, var amount, var status, var createTime)) {
            return "订单[%d]: %s, 金额=%s, 状态=%s".formatted(id, orderNo, amount, status.display());
        }
        if (obj instanceof OrderStatus status) {
            return switch (status) {
                case OrderStatus.Pending p   -> "待支付";
                case OrderStatus.Paid p      -> "已支付 %s 元".formatted(p.paidAmount());
                case OrderStatus.Shipped s   -> "已发货: " + s.trackingNo();
                case OrderStatus.Cancelled c -> "已取消: " + c.reason();
            };
        }
        return "未知: " + obj;
    }

    /**
     * Sequenced Collections（JDK 21）：统一了有序集合的 API
     *
     * <pre>
     * // JDK 8：list.get(0) / list.get(list.size()-1)
     * // JDK 21：list.getFirst() / list.getLast() / list.reversed()
     * </pre>
     */
    public Map<String, Object> sequencedCollectionDemo() {
        SequencedCollection<String> orderNos = new ArrayList<>(List.of("ORD-001", "ORD-002", "ORD-003"));
        SequencedMap<String, String> orderMap = new LinkedHashMap<>();
        orderMap.put("order", "ORD-001");
        orderMap.put("payment", "PAID");
        orderMap.put("logistics", "SHIPPED");

        return Map.of(
                "first", orderNos.getFirst(),         // JDK 21：不用 list.get(0)
                "last", orderNos.getLast(),            // JDK 21：不用 list.get(list.size()-1)
                "reversed", orderNos.reversed().toString(),
                "mapFirst", orderMap.firstEntry().toString(),
                "mapLast", orderMap.lastEntry().toString()
        );
    }

    private void simulateSlowApi() {
        try { Thread.sleep(2000); } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new RuntimeException(e);
        }
    }
}
