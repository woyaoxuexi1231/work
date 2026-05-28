package com.example.springqa.Q15_DistributedTransaction;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <h1>Q15：分布式事务 — 不引入 Seata 的三种方案</h1>
 *
 * <h2>方案一：TCC（Try-Confirm-Cancel）</h2>
 * <p>Try 冻结资源 → Confirm 确认扣减 → Cancel 释放冻结。
 * 不需要全局事务管理器，但要自己实现幂等和补偿。</p>
 *
 * <h2>方案二：本地消息表 + 定时任务 + 幂等</h2>
 * <p>业务 + 消息写入同一本地事务 → 定时扫描发送 → 消费方幂等。
 * 最终一致性方案。</p>
 *
 * <h2>方案三：JTA</h2>
 * <p>配置 Atomikos/Bitronix + XA 数据源，使用现有的 @Transactional。</p>
 *
 * <h2>Spring 为什么没有内置分布式事务？</h2>
 * <p>CAP 理论决定了一致性/可用性/分区容错不可兼得。
 * Spring 提供 PlatformTransactionManager 接口作为 SPI，
 * 让各种实现（JTA、Seata）作为插件接入——"提供框架，不绑定方案"。</p>
 */
@Component
public class DistributedTransactionDemo {

    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Q15: 分布式事务 ===\n\n");

        // === TCC 演示 ===
        sb.append("--- 方案一：TCC 模式 ---\n");
        TccInventoryService inventory = new TccInventoryService();
        TccOrderService orderService = new TccOrderService();
        String orderId = UUID.randomUUID().toString().substring(0, 8);

        try {
            inventory.tryFreeze("PROD-001", 2);
            sb.append("  [Try] 冻结库存成功\n");
            orderService.tryCreate(orderId, "PROD-001", 2);
            sb.append("  [Try] 创建订单 PENDING\n");
            inventory.confirmDeduct("PROD-001", 2);
            sb.append("  [Confirm] 确认扣减\n");
            orderService.confirmOrder(orderId);
            sb.append("  [Confirm] 确认订单 → ").append(orderService.getStatus(orderId)).append("\n");
        } catch (Exception e) {
            inventory.cancelFreeze("PROD-001", 2);
            orderService.cancelOrder(orderId);
            sb.append("  [Cancel] 全部回滚\n");
        }
        sb.append("  ✅ TCC 流程完成\n\n");

        // === 本地消息表演示 ===
        sb.append("--- 方案二：本地消息表 ---\n");
        LocalMessageTable table = new LocalMessageTable();
        table.insert("ORD-001", "ORDER_CREATED", "{\"amount\":100}");
        sb.append("  [本地事务] 订单创建 + 消息写入\n");
        table.scanAndSend();
        sb.append("  [定时任务] 扫描待发送 → 发送成功\n");
        sb.append("  [消费方] 幂等检查 → 执行业务\n");
        sb.append("  ✅ 最终一致性保证\n\n");

        sb.append("【TCC 三大挑战】\n");
        sb.append("1. 空回滚：Cancel 被调用时 Try 可能没执行 → Cancel 要判断记录是否存在\n");
        sb.append("2. 悬挂：Cancel 比 Try 先到达 → Try 要检查是否已有 Cancel\n");
        sb.append("3. 幂等：Confirm/Cancel 可能重复 → 通过事务 ID 去重\n\n");

        sb.append("【Spring 为什么不内置分布式事务？】\n");
        sb.append("分布式事务没有银弹。Spring 选择提供抽象接口（PlatformTransactionManager），\n");
        sb.append("让各种实现作为插件接入——这是\"提供框架，不绑定方案\"的哲学。\n");

        return sb.toString();
    }

    // ================================================================
    static class TccInventoryService {
        private final Map<String, Integer> frozen = new ConcurrentHashMap<>();
        private final Map<String, Integer> stock = new ConcurrentHashMap<>();
        { stock.put("PROD-001", 100); }

        void tryFreeze(String product, int qty) {
            if (stock.getOrDefault(product, 0) < qty) throw new RuntimeException("库存不足");
            stock.merge(product, -qty, Integer::sum);
            frozen.merge(product, qty, Integer::sum);
        }
        void confirmDeduct(String product, int qty) {
            frozen.merge(product, -qty, Integer::sum);
        }
        void cancelFreeze(String product, int qty) {
            frozen.merge(product, -qty, Integer::sum);
            stock.merge(product, qty, Integer::sum);
        }
    }

    static class TccOrderService {
        private final Map<String, String> orders = new ConcurrentHashMap<>();
        void tryCreate(String id, String product, int qty) { orders.put(id, "PENDING"); }
        void confirmOrder(String id) { orders.put(id, "CONFIRMED"); }
        void cancelOrder(String id) { orders.put(id, "CANCELLED"); }
        String getStatus(String id) { return orders.get(id); }
    }

    static class LocalMessageTable {
        static class Msg { String status = "PENDING"; LocalDateTime time = LocalDateTime.now(); }
        private final Map<String, Msg> messages = new ConcurrentHashMap<>();

        void insert(String id, String type, String payload) { messages.put(id, new Msg()); }
        void scanAndSend() {
            messages.values().forEach(m -> m.status = "SENT");
        }
    }
}
