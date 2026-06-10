package com.demo.jdk21.service;

import com.demo.jdk21.dto.OrderDTO;
import com.demo.jdk21.dto.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * <h3>JDK 21 终极写法 —— 虚线程改变一切</h3>
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    // ========================================================================
    // 🔥 核心改进：虚线程（对比 JDK 8/17 的 CompletableFuture）
    // ========================================================================

    /**
     * <pre>
     * // JDK 8：CompletableFuture + 平台线程池（每个线程 ~1MB）
     * ExecutorService executor = Executors.newFixedThreadPool(3);
     * var f1 = CompletableFuture.supplyAsync(() -> callApi(), executor);
     * CompletableFuture.allOf(f1, f2, f3).join();
     * executor.shutdown();
     *
     * // JDK 21：虚线程，写同步代码获得异步性能！每个 ~1KB
     * try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
     *     var f1 = executor.submit(() -> callApi());
     *     return Map.of("a", f1.get());
     * }  // 自动关闭，不用 shutdown()
     * </pre>
     */
    public Map<String, Object> batchQueryExternal(Long orderId) {
        log.info("📡 开始批量查询（当前线程: {}, 虚线程: {}）",
                Thread.currentThread().getName(), Thread.currentThread().isVirtual());

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            Future<String> orderFuture = executor.submit(() -> {
                log.info("📡 订单详情 API（线程: {}, 虚线程: {}）",
                        Thread.currentThread().getName(), Thread.currentThread().isVirtual());
                simulateSlowApi();
                return "order-detail-" + orderId;
            });

            Future<String> payFuture = executor.submit(() -> {
                log.info("📡 支付信息 API（线程: {}, 虚线程: {}）",
                        Thread.currentThread().getName(), Thread.currentThread().isVirtual());
                simulateSlowApi();
                return "payment-info-" + orderId;
            });

            Future<String> logisticsFuture = executor.submit(() -> {
                log.info("📡 物流信息 API（线程: {}, 虚线程: {}）",
                        Thread.currentThread().getName(), Thread.currentThread().isVirtual());
                simulateSlowApi();
                return "logistics-info-" + orderId;
            });

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("order", orderFuture.get());
            result.put("payment", payFuture.get());
            result.put("logistics", logisticsFuture.get());
            result.put("threadType", "virtual-thread（每个 ~1KB）");
            result.put("codeStyle", "同步写法，无 CompletableFuture 回调");
            return result;

        } catch (Exception e) {
            log.error("🚫 批量查询失败: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    // ========================================================================
    // 🔥 高并发压测：虚线程 vs 平台线程内存对比
    // ========================================================================

    public Map<String, Object> highConcurrencyDemo(int concurrencyCount) {
        log.info("🚀 高并发: {} 个并发任务", concurrencyCount);
        long start = System.currentTimeMillis();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < concurrencyCount; i++) {
                final int taskId = i;
                futures.add(executor.submit(() -> {
                    simulateSlowApi();
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
                    "memoryUsage", concurrencyCount + " 虚线程 ≈ " + concurrencyCount + "KB（平台线程需 ~" + concurrencyCount + "MB）"
            );
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    // ========================================================================
    // JDK 21 特性：Record Patterns（对比 JDK 17 instanceof 只绑定整个对象）
    // ========================================================================

    /**
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

    // ========================================================================
    // JDK 21 特性：Sequenced Collections
    // ========================================================================

    /**
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
                "first", orderNos.getFirst(),
                "last", orderNos.getLast(),
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
