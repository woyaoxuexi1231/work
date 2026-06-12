package com.demo.jdk21.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 演示 JDK 21 Unnamed Patterns and Variables（匿名模式变量）—— 预览
 *
 * 核心思想：用 _ 表示不关心的变量，不需要给它起名字
 * 替代方案：起个名字然后不用（编译器警告）
 */
@Service
public class UnnamedDemoService {

    private static final Logger log = LoggerFactory.getLogger(UnnamedDemoService.class);

    public record Order(long id, String orderNo, double amount, String status) {}

    public Map<String, Object> demo() {
        var result = new LinkedHashMap<String, Object>();

        // === 1. Record Pattern 中使用 _ 忽略不需要的字段 ===
        Object obj = new Order(1L, "ORD-001", 99.9, "PAID");

        // 只关心订单号，其他字段用 _
        if (obj instanceof Order(_, var orderNo, _, _)) {
            result.put("1_忽略其他字段", "订单号: " + orderNo);
        }

        // 在 switch 中也可以用
        String desc = switch (obj) {
            case Order(_, var no, var amount, _) -> "订单 " + no + ": ¥" + amount;
            default -> "未知";
        };
        result.put("2_switch中用_", desc);

        // === 2. 增强 for 中忽略循环变量 ===
        List<String> items = List.of("a", "b", "c", "d", "e");
        int count = 0;
        for (var _ : items) {  // 不关心每个元素的值，只计数
            count++;
        }
        result.put("3_for计数", "集合大小: " + count);

        // === 3. catch 中忽略异常变量 ===
        try {
            int x = 1 / 0;
        } catch (Exception _) {  // 不关心异常对象
            result.put("4_catch忽略异常", "除零错误已捕获（不需要异常变量）");
        }

        // === 4. try-with-resources 中忽略 ===
        try (var _ = new java.io.StringReader("unused")) {
            result.put("5_try资源忽略", "资源已打开但不使用（用 _ 替代）");
        } catch (Exception _) {
            log.error("🚫 资源处理异常");
        }

        result.put("6_说明", "_ 表示'我不关心这个变量'，减少命名噪音，代码意图更清晰");

        log.info("✅ Unnamed Variables 演示完成");
        return result;
    }
}
