package com.example.springqa.Q15_DistributedTransaction;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <h1>Q15：分布式事务 — 不引入 Seata 的解决方案</h1>
 *
 * <h2>面试点</h2>
 * <p>只用 Spring 框架，不引入 Seata，怎么实现简单的分布式事务？</p>
 *
 * <h2>三种可行方案</h2>
 *
 * <h3>方案一：TCC（Try-Confirm-Cancel）手动实现</h3>
 * <pre>
 * Try     — 预留资源（冻结库存、预扣余额）
 * Confirm — 确认操作（真正扣减）
 * Cancel  — 取消操作（释放预留资源）
 *
 * // 不需要全局事务管理器，但要自己实现幂等和补偿
 * </pre>
 *
 * <h3>方案二：本地消息表 + 定时任务 + 幂等</h3>
 * <pre>
 * 1. 业务操作 + 消息写入 在同一个本地事务中
 * 2. 定时任务扫描未发送的消息，发送到 MQ
 * 3. 消费方保证幂等（通过唯一键去重）
 *
 * // 这是"最终一致性"方案，不适合强一致性场景
 * </pre>
 *
 * <h3>方案三：JTA（Java Transaction API）</h3>
 * <p>Spring 内置支持 JTA。配置 Atomikos 或 Bitronix 作为 JTA 实现，
 * 然后使用 {@code @Transactional} 即可。（需要支持 XA 的数据库驱动）</p>
 *
 * <h2>Spring 为什么没有内置分布式事务？</h2>
 * <p>分布式事务是一个"没有银弹"的领域。CAP 理论决定了
 * 一致性、可用性、分区容错性三者不可兼得。Spring 选择提供
 * 抽象的 PlatformTransactionManager 接口，让各种实现（JTA、Seata 等）
 * 作为插件接入，而不是内置一种方案。</p>
 *
 * <p>这也是 Spring 的设计哲学——"提供框架，不绑定方案"。</p>
 *
 * @author Spring Interview QA
 */
public class DistributedTransactionDemo {

    /**
     * 运行本 Demo：
     * 演示 TCC 模式和本地消息表模式的简化实现。
     */
    public static void main(String[] args) {
        System.out.println("========== Q15: 分布式事务 Demo ==========\n");

        // ===== 方案一：TCC 模式 =====
        System.out.println("--- 方案一：TCC 模式（订单 + 库存）---");
        demoTCCOrderFlow();

        // ===== 方案二：本地消息表 =====
        System.out.println("\n--- 方案二：本地消息表（下单 + 发通知）---");
        demoLocalMessageTable();

        System.out.println("\n========== Demo 结束 ==========");
    }

    // ================================================================
    // 方案一：TCC 模式
    // ================================================================

    static void demoTCCOrderFlow() {
        TccOrderService orderService = new TccOrderService();
        TccInventoryService inventoryService = new TccInventoryService();

        String orderId = UUID.randomUUID().toString();
        String productId = "PROD-001";
        int quantity = 2;

        try {
            // Try 阶段：预留资源
            System.out.println("  [Try] 冻结库存...");
            inventoryService.tryFreeze(productId, quantity);

            System.out.println("  [Try] 创建订单（状态=PENDING）...");
            orderService.tryCreate(orderId, productId, quantity);

            // Confirm 阶段：确认
            System.out.println("  [Confirm] 确认扣减库存...");
            inventoryService.confirmDeduct(productId, quantity);

            System.out.println("  [Confirm] 确认订单（状态=CONFIRMED）...");
            orderService.confirmOrder(orderId);

            System.out.println("  ✅ TCC 下单成功！订单号=" + orderId);

        } catch (Exception e) {
            // Cancel 阶段：回滚
            System.out.println("  ❌ 异常发生，执行 Cancel...");
            System.out.println("  [Cancel] 释放冻结库存...");
            inventoryService.cancelFreeze(productId, quantity);

            System.out.println("  [Cancel] 取消订单...");
            orderService.cancelOrder(orderId);

            System.out.println("  ↩ TCC 回滚完成");
        }
        /*
         * TCC 的核心挑战：
         *
         * 1. 空回滚：Cancel 被调用时，Try 可能还没执行。
         *    → Cancel 需要判断是否存在 Try 的记录，没有则直接返回成功。
         *
         * 2. 悬挂：Cancel 比 Try 先到达。
         *    → Try 需要判断是否已有 Cancel 记录，有则拒绝执行。
         *
         * 3. 幂等：Confirm / Cancel 可能被重复调用。
         *    → 通过事务 ID 去重，已处理的直接返回成功。
         */
    }

    // TCC 资源接口
    interface TccResource {
        void tryPhase(String id, int amount);
        void confirmPhase(String id, int amount);
        void cancelPhase(String id, int amount);
    }

    static class TccInventoryService implements TccResource {
        // 冻结量（模拟数据库）
        private final Map<String, Integer> frozenStock = new ConcurrentHashMap<>();
        private final Map<String, Integer> realStock = new ConcurrentHashMap<>();
        {
            realStock.put("PROD-001", 100);
        }

        @Override
        public void tryPhase(String productId, int quantity) {
            tryFreeze(productId, quantity);
        }

        public void tryFreeze(String productId, int quantity) {
            Integer stock = realStock.get(productId);
            if (stock == null || stock < quantity) {
                throw new RuntimeException("库存不足");
            }
            // 扣减"可用库存"，增加"冻结库存"
            realStock.merge(productId, -quantity, Integer::sum);
            frozenStock.merge(productId, quantity, Integer::sum);
        }

        @Override
        public void confirmPhase(String productId, int quantity) {
            confirmDeduct(productId, quantity);
        }

        public void confirmDeduct(String productId, int quantity) {
            // 真正扣减"冻结库存"
            frozenStock.merge(productId, -quantity, Integer::sum);
        }

        @Override
        public void cancelPhase(String productId, int quantity) {
            cancelFreeze(productId, quantity);
        }

        public void cancelFreeze(String productId, int quantity) {
            // 释放"冻结库存"回"可用库存"
            frozenStock.merge(productId, -quantity, Integer::sum);
            realStock.merge(productId, quantity, Integer::sum);
        }
    }

    static class TccOrderService {
        private final Map<String, String> orders = new ConcurrentHashMap<>();

        public void tryCreate(String orderId, String productId, int quantity) {
            orders.put(orderId, "PENDING");
        }

        public void confirmOrder(String orderId) {
            orders.put(orderId, "CONFIRMED");
        }

        public void cancelOrder(String orderId) {
            orders.put(orderId, "CANCELLED");
        }
    }

    // ================================================================
    // 方案二：本地消息表
    // ================================================================

    static void demoLocalMessageTable() {
        LocalMessageTable messageTable = new LocalMessageTable();

        String orderId = UUID.randomUUID().toString();

        /*
         * 步骤 1：在同一个本地事务中，执行业务 + 写消息表
         *
         * 【为什么放在同一个事务？】
         * 如果分开：业务成功了但消息没写入 → 下游收不到通知
         *           消息写了但业务失败了 → 下游收到"假"通知
         *
         * 放在同一个事务中：要么都成功，要么都失败——保证原子性。
         */
        System.out.println("  [本地事务] 创建订单 + 写入消息表...");
        // 模拟：订单表 INSERT + 消息表 INSERT（同一个数据库事务）
        messageTable.insert(orderId, "ORDER_CREATED",
                "{\"orderId\":\"" + orderId + "\",\"amount\":100}");

        /*
         * 步骤 2：定时任务扫描"待发送"消息
         *
         * ScheduledTask.scanPendingMessages() → 发送到 MQ / HTTP
         * 发送成功 → 标记消息为 SENT
         * 发送失败 → 保留 PENDING，等待下次扫描
         */
        System.out.println("  [定时任务] 扫描待发送消息...");
        messageTable.scanAndSend();

        /*
         * 步骤 3：消费方保证幂等
         *
         * @Transactional
         * public void handleOrderCreated(Message msg) {
         *     // 通过唯一键（orderId + eventType）判断是否已处理
         *     if (processedEventRepo.exists(msg.getOrderId(), msg.getEventType())) {
         *         return; // 已处理，幂等返回
         *     }
         *     // 执行业务...
         *     processedEventRepo.save(msg.getOrderId(), msg.getEventType());
         * }
         */
        System.out.println("  [消费方] 检查幂等 → 执行业务 → 记录已处理");

        System.out.println("  ✅ 本地消息表方案：最终一致性保证！");

        /*
         * 这个方案的优缺点：
         *
         * 优点：
         * - 不依赖外部事务管理器
         * - 高可用（每个服务独立）
         * - 适用于长事务（定时任务重试）
         *
         * 缺点：
         * - 最终一致性（有延迟）
         * - 需要写定时任务
         * - 消费方必须实现幂等
         *
         * Spring 为什么没有内置这个方案？
         * 因为这个方案涉及 MQ、定时任务、业务表结构——
         * 这些都是"应用层"的决策，不应该由框架硬编码。
         * 框架提供 @Transactional 保证本地事务，
         * 分布式协调留给你根据业务需求自己设计。
         */
    }

    static class LocalMessageTable {
        static class Message {
            String id;
            String eventType;
            String payload;
            String status; // PENDING / SENT / FAILED
            LocalDateTime createTime;

            Message(String id, String eventType, String payload) {
                this.id = id;
                this.eventType = eventType;
                this.payload = payload;
                this.status = "PENDING";
                this.createTime = LocalDateTime.now();
            }
        }

        private final ConcurrentHashMap<String, Message> messages = new ConcurrentHashMap<>();

        void insert(String id, String eventType, String payload) {
            messages.put(id, new Message(id, eventType, payload));
            System.out.println("  消息已写入本地表: id=" + id + ", status=PENDING");
        }

        void scanAndSend() {
            for (Message msg : messages.values()) {
                if ("PENDING".equals(msg.status)) {
                    // 模拟发送到 MQ / HTTP
                    System.out.println("  发送消息: " + msg.eventType + " → " + msg.payload);
                    msg.status = "SENT"; // 标记已发送
                }
            }
        }
    }
}
