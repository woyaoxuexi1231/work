package com.demo.jdk17.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 演示 JDK 15+ Sealed Classes / Interfaces（正式版 JDK 17）
 *
 * 核心思想：限定哪些类可以实现/继承，让编译器知道所有可能的子类
 * 典型场景：状态机（订单状态只有几种），配合 switch 强制穷举
 */
@Service
public class SealedDemoService {

    private static final Logger log = LoggerFactory.getLogger(SealedDemoService.class);

    // 1. Sealed Interface：permits 限定实现类
    public sealed interface Shape permits Circle, Square, Triangle {
        double area();
    }

    // 2. 每个实现类必须声明为 final / sealed / non-sealed
    public record Circle(double radius) implements Shape {
        @Override
        public double area() {
            return Math.PI * radius * radius;
        }
    }

    public record Square(double side) implements Shape {
        @Override
        public double area() {
            return side * side;
        }
    }

    // non-sealed：允许任意子类继续继承（打破密封）
    public non-sealed static class Triangle implements Shape {
        private final double base;
        private final double height;

        public Triangle(double base, double height) {
            this.base = base;
            this.height = height;
        }

        @Override
        public double area() {
            return 0.5 * base * height;
        }
    }

    // 3. Sealed 的实际价值：配合 switch 模式匹配
    public String describe(Shape shape) {
        // JDK 17 preview：switch 可以对 sealed 类型自动穷举，不需要 default
        return switch (shape) {
            case Circle c -> "圆形, 半径: " + c.radius() + ", 面积: " + String.format("%.2f", c.area());
            case Square s -> "正方形, 边长: " + s.side() + ", 面积: " + s.area();
            case Triangle t -> "三角形, 面积: " + t.area();
            // 不需要 default！因为 sealed 让编译器知道只有这 3 种
        };
    }

    public Map<String, Object> demo() {
        Map<String, Object> result = new LinkedHashMap<>();

        List<Shape> shapes = List.of(
                new Circle(5),
                new Square(4),
                new Triangle(3, 6)
        );

        for (Shape shape : shapes) {
            result.put(shape.getClass().getSimpleName(), describe(shape));
        }

        result.put("4_说明", "sealed + switch: 编译器强制穷举，漏写一种 case 会编译报错！");

        log.info("✅ Sealed Interface 演示完成");
        return result;
    }
}
