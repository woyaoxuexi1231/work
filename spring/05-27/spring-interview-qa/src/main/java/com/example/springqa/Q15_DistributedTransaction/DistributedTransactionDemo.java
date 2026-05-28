package com.example.springqa.Q15_DistributedTransaction;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class DistributedTransactionDemo {

    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Q15: 分布式事务 ===\n\n");

        // TCC 演示
        Q15TccInventoryService inv = new Q15TccInventoryService();
        Q15TccOrderService ord = new Q15TccOrderService();
        String id = UUID.randomUUID().toString().substring(0, 8);
        try {
            inv.tryFreeze("PROD-001", 2);
            ord.tryCreate(id, "PROD-001", 2);
            inv.confirmDeduct("PROD-001", 2);
            ord.confirmOrder(id);
            sb.append("TCC 下单成功: ").append(id).append(" → ").append(ord.getStatus(id)).append("\n");
        } catch (Exception e) {
            inv.cancelFreeze("PROD-001", 2);
            ord.cancelOrder(id);
            sb.append("TCC 已回滚\n");
        }

        // 本地消息表演示
        Q15LocalMessageTable tbl = new Q15LocalMessageTable();
        tbl.insert("ORD-001", "ORDER_CREATED", "{\"amount\":100}");
        tbl.scanAndSend();
        sb.append("本地消息表: ORD-001 已发送\n\n");

        sb.append("【三种方案】\n");
        sb.append("1. TCC: Try→Confirm/Cancel，自实现幂等和补偿\n");
        sb.append("2. 本地消息表: 业务+消息同一事务→定时扫描→消费方幂等\n");
        sb.append("3. JTA: 配置 Atomikos + XA 数据源\n\n");
        sb.append("【Spring 为什么没有内置分布式事务？】\n");
        sb.append("CAP 理论——一致性/可用性/分区容错不可兼得。\n");
        sb.append("Spring 提供 PlatformTransactionManager SPI 让各种实现作插件。\n");

        return sb.toString();
    }
}
