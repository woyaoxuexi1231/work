package com.demo.jdk8.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 演示 JDK 8 核心特性：Optional
 *
 * 核心思想：显式表达"值可能不存在"，替代 null（消除 NullPointerException）
 * 替代方案：到处写 if (obj != null) { ... }
 */
@Slf4j
@Service
public class OptionalDemoService {

    public Map<String, Object> demo() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 模拟可能为 null 的数据
        Optional<String> presentOpt = Optional.of("Hello");
        Optional<String> emptyOpt = Optional.empty();

        // 1. isPresent + get（最基础，但不推荐，类似 null 检查）
        result.put("1_isPresent", presentOpt.isPresent());
        result.put("1_get值", presentOpt.get());

        // 2. ifPresent：值存在时执行操作（不关心不存在的情况）
        StringBuilder sb = new StringBuilder();
        presentOpt.ifPresent(v -> sb.append("有值: ").append(v));
        emptyOpt.ifPresent(v -> sb.append("这不会执行"));
        result.put("2_ifPresent", sb.toString());

        // 3. orElse：不存在时提供默认值
        String value1 = presentOpt.orElse("默认值");
        String value2 = emptyOpt.orElse("默认值");
        result.put("3_orElse_有值", value1);
        result.put("3_orElse_无值", value2);

        // 4. map：链式转换（不存在则返回 empty）
        Optional<Integer> length = presentOpt.map(String::length);
        Optional<Integer> emptyLength = emptyOpt.map(String::length);
        result.put("4_map_有值的长度", length.orElse(-1));
        result.put("4_map_无值的长度", emptyLength.orElse(-1));

        // 5. flatMap：嵌套 Optional 扁平化
        Optional<Optional<String>> nested = Optional.of(Optional.of("nested"));
        Optional<String> flat = nested.flatMap(v -> v);
        result.put("5_flatMap", flat.orElse("empty"));

        // 6. filter：条件过滤（不满足条件变成 empty）
        Optional<String> filtered1 = presentOpt.filter(v -> v.length() > 3);
        Optional<String> filtered2 = presentOpt.filter(v -> v.length() > 10);
        result.put("6_filter_长度大于3", filtered1.orElse("过滤掉了"));
        result.put("6_filter_长度大于10", filtered2.orElse("过滤掉了"));

        log.info("✅ Optional 演示完成");
        return result;
    }
}
