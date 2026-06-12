package com.demo.jdk17.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Stream;

/**
 * 演示 JDK 10 var + JDK 9 集合工厂 + JDK 16 Stream.toList()
 *
 * var: 局部变量类型推断，不需要显式写类型
 * 集合工厂: List.of() / Set.of() / Map.of() 一行创建不可变集合
 * Stream.toList(): 替代 .collect(Collectors.toList())
 */
@Service
public class VarCollectionDemoService {

    private static final Logger log = LoggerFactory.getLogger(VarCollectionDemoService.class);

    public Map<String, Object> demo() {
        // === var 类型推断 ===
        var result = new LinkedHashMap<String, Object>(); // 不需要写左边完整类型

        // 1. var 基础用法
        var name = "Alice";          // String
        var age = 30;                // int
        var pi = 3.14;               // double
        var list = new ArrayList<String>(); // ArrayList<String>
        result.put("1_var_类型推断", "name=%s, age=%d, pi=%.2f".formatted(name, age, pi));

        // 2. var 在增强 for 中
        var map = Map.of("a", 1, "b", 2, "c", 3);
        var entries = new ArrayList<String>();
        for (var entry : map.entrySet()) {  // Map.Entry<String, Integer>
            entries.add(entry.getKey() + "=" + entry.getValue());
        }
        result.put("2_var_增强for", entries);

        // 3. var 在 try-with-resources 中
        var sb = new StringBuilder();
        try (var reader = new java.io.StringReader("hello world")) {
            int ch;
            while ((ch = reader.read()) != -1) {
                sb.append((char) ch);
            }
        } catch (Exception e) {
            log.error("🚫 读取异常", e);
        }
        result.put("3_var_try资源", sb.toString());

        // === 集合工厂（JDK 9）===

        // 4. List.of() / Set.of() / Map.of()：一行创建不可变集合
        var fruits = List.of("苹果", "香蕉", "橙子");
        var uniqueIds = Set.of(1, 2, 3, 4, 5);
        var config = Map.of("host", "localhost", "port", 8080);
        result.put("4_List_of", fruits);
        result.put("4_Set_of", uniqueIds);
        result.put("4_Map_of", config);

        // 5. 不可变验证
        try {
            fruits.add("西瓜");
            result.put("5_不可变验证", "应该抛出异常");
        } catch (UnsupportedOperationException e) {
            result.put("5_不可变验证", "❌ List.of() 创建的集合不可修改！");
        }

        // 6. copyOf()：从可变集合创建不可变副本
        var mutableList = new ArrayList<>(List.of("a", "b", "c"));
        var immutableCopy = List.copyOf(mutableList);
        mutableList.add("d");  // 修改原列表不影响副本
        result.put("6_copyOf_原列表", mutableList);
        result.put("6_copyOf_副本", immutableCopy);

        // === Stream.toList()（JDK 16）===

        // 7. 旧写法 vs 新写法
        var numbers = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        var evensOld = numbers.stream()
                .filter(n -> n % 2 == 0)
                .collect(java.util.stream.Collectors.toList()); // 旧写法
        var evensNew = numbers.stream()
                .filter(n -> n % 2 == 0)
                .toList();  // 新写法（JDK 16+），返回不可变 List
        result.put("7_旧写法_collect", evensOld);
        result.put("7_新写法_toList", evensNew);

        log.info("✅ var + 集合工厂 + Stream.toList() 演示完成");
        return result;
    }
}
