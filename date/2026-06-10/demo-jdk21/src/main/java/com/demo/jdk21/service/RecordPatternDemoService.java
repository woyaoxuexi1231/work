package com.demo.jdk21.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 演示 JDK 21 Record Patterns（记录模式）—— 正式
 *
 * 核心思想：instanceof / switch 可以直接解构 Record 的每个字段
 * 替代方案：JDK 17 只能绑定整个 Record 对象，还需要手动调用 accessor
 */
@Service
public class RecordPatternDemoService {

    private static final Logger log = LoggerFactory.getLogger(RecordPatternDemoService.class);

    // 定义几个 Record
    public record Point(int x, int y) {}
    public record Line(Point start, Point end) {}
    public record User(String name, int age, String email) {}

    public Map<String, Object> demo() {
        var result = new LinkedHashMap<String, Object>();

        // === 1. instanceof + Record Pattern：直接解构字段 ===
        Object obj = new Point(10, 20);

        // JDK 17 写法：绑定整个对象
        if (obj instanceof Point p) {
            result.put("1_JDK17_整个对象", "Point: x=" + p.x() + ", y=" + p.y());
        }

        // JDK 21 写法：Record Pattern，直接解构每个字段
        if (obj instanceof Point(var x, var y)) {
            result.put("1_JDK21_解构字段", "x=" + x + ", y=" + y + "（直接使用变量 x, y）");
            result.put("1_计算距离", "到原点距离: " + Math.sqrt(x * x + y * y));
        }

        // === 2. 嵌套解构（Nested Record Pattern）===
        Object line = new Line(new Point(0, 0), new Point(3, 4));

        if (line instanceof Line(Point(var x1, var y1), Point(var x2, var y2))) {
            double length = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
            result.put("2_嵌套解构", "(%d,%d) → (%d,%d)".formatted(x1, y1, x2, y2));
            result.put("2_线段长度", String.format("%.2f", length));
        }

        // === 3. switch 中使用 Record Pattern ===
        Object[] objects = {
                new Point(5, 10),
                new User("Alice", 30, "alice@example.com"),
                new Line(new Point(0, 0), new Point(1, 1)),
                "plain string"
        };

        for (Object o : objects) {
            String desc = switch (o) {
                case Point(var x, var y) when x == 0 && y == 0 -> "原点";
                case Point(var x, var y) -> "坐标点 (" + x + ", " + y + ")";
                case User(var name, var age, var email) -> "用户 " + name + " (" + age + "岁) " + email;
                case Line(Point(var x1, var y1), Point(var x2, var y2)) ->
                        "线段: (%d,%d)→(%d,%d)".formatted(x1, y1, x2, y2);
                default -> "未知: " + o;
            };
            result.put("3_switch_" + o.getClass().getSimpleName(), desc);
        }

        // === 4. Guard（when 守卫条件，仅 switch 中可用）===
        Object p = new Point(100, 200);
        String guardResult = switch (p) {
            case Point(var x, var y) when x > 50 && y > 50 -> "Point(%d,%d) 在大范围内".formatted(x, y);
            case Point(var x, var y) -> "Point(%d,%d) 在小范围内".formatted(x, y);
        };
        result.put("4_when守卫", guardResult);

        log.info("✅ Record Pattern 演示完成");
        return result;
    }
}
