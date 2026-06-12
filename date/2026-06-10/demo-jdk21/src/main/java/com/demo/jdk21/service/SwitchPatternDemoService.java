package com.demo.jdk21.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 演示 JDK 21 Switch 模式匹配（正式版，JDK 17 是预览）
 *
 * 核心变化：switch 可以对类型、null、数组做匹配，不再局限于值
 */
@Service
public class SwitchPatternDemoService {

    private static final Logger log = LoggerFactory.getLogger(SwitchPatternDemoService.class);

    public Map<String, Object> demo() {
        var result = new LinkedHashMap<String, Object>();

        // 1. 类型匹配（JDK 17 预览 → JDK 21 正式）
        Object[] testObjects = {42, "hello", 3.14, true, new int[]{1, 2, 3}, null};
        for (Object obj : testObjects) {
            String desc = switch (obj) {
                case Integer i -> "整数: " + i;
                case String s when s.length() > 10 -> "长字符串: " + s;
                case String s -> "字符串(" + s.length() + "): " + s;
                case Double d -> "浮点数: " + d;
                case Boolean b -> "布尔: " + b;
                case int[] arr -> "int数组, 长度: " + arr.length;  // 数组匹配！
                case null -> "null值";                              // null 匹配！
                default -> "其他: " + obj;
            };
            String key = obj == null ? "null" : obj.getClass().getSimpleName();
            result.put("1_类型匹配_" + key, desc);
        }

        // 2. when 守卫：在 case 后加条件（使用 Object 类型匹配）
        Object[] numbers = {5, 15, 25, 50};
        for (Object n : numbers) {
            String category = switch (n) {
                case Integer i when i < 10 -> "小: " + i;
                case Integer i when i < 30 -> "中: " + i;
                case Integer i -> "大: " + i;
                default -> "其他";
            };
            result.put("2_when守卫_" + n, category);
        }

        // 3. null 处理（以前 switch 遇到 null 直接 NPE，现在可以匹配）
        Object nullObj = null;
        String nullResult = switch (nullObj) {
            case null -> "安全处理 null（不会 NPE）";
            case String s -> "字符串: " + s;
            default -> "其他";
        };
        result.put("3_null处理", nullResult);

        result.put("4_对比", "JDK 17: 预览 + 需要 --enable-preview；JDK 21: 正式，直接用");

        log.info("✅ Switch 模式匹配（正式）演示完成");
        return result;
    }
}
public class SwitchPatternDemoService {
    
}
