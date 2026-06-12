package com.demo.jdk17.service;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 演示 JDK 14+ Record（正式版 JDK 16）
 *
 * 核心思想：不可变数据载体，自动生成 equals/hashCode/toString/getter
 * 替代方案：POJO + getter/setter + 手写 equals/hashCode（冗长且易出错）
 */
@Service
public class RecordDemoService {

    private static final Logger log = LoggerFactory.getLogger(RecordDemoService.class);

    // 1. 基础 Record：一行定义数据类
    public record User(String name, int age, String email) {}

    // 2. 带紧凑构造器的 Record（可以加验证逻辑）
    public record Product(String name, BigDecimal price) {
        // 紧凑构造器：不需要写参数赋值（自动完成）
        public Product {
            if (price.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("价格不能为负数");
            }
        }
    }

    // 3. Record 可以实现接口
    public record ApiResponse<T>(int code, String message, T data) {
        // 静态工厂方法
        public static <T> ApiResponse<T> ok(T data) {
            return new ApiResponse<>(200, "success", data);
        }

        public static ApiResponse<Void> error(int code, String message) {
            return new ApiResponse<>(code, message, null);
        }
    }

    public Map<String, Object> demo() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 4. 创建和使用 Record
        User user1 = new User("Alice", 30, "alice@example.com");
        User user2 = new User("Alice", 30, "alice@example.com");
        User user3 = new User("Bob", 25, "bob@example.com");

        // 自动 toString
        result.put("1_toString", user1.toString());

        // 自动 equals（按字段值比较，不是引用比较）
        result.put("2_equals_相同数据", user1.equals(user2));   // true
        result.put("2_equals_不同数据", user1.equals(user3));   // false

        // 自动 getter（不需要 getXxx，直接用字段名）
        result.put("3_getter_name", user1.name());
        result.put("3_getter_age", user1.age());

        // 紧凑构造器验证
        try {
            new Product("商品", new BigDecimal("-1"));
            result.put("4_负价格", "应该抛出异常");
        } catch (IllegalArgumentException e) {
            result.put("4_负价格验证", "❌ " + e.getMessage());
        }

        // 静态工厂方法
        var okResponse = ApiResponse.ok(user1);
        var errResponse = ApiResponse.error(404, "Not Found");
        result.put("5_ApiResponse_ok", okResponse.toString());
        result.put("5_ApiResponse_error", errResponse.toString());

        log.info("✅ Record 演示完成");
        return result;
    }
}
