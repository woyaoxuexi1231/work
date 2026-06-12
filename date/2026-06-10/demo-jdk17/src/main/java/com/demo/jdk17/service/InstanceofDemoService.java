package com.demo.jdk17.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 演示 JDK 14+ instanceof 模式匹配（正式版 JDK 16）
 *
 * 核心思想：类型判断 + 变量绑定一步完成，不需要手动强转
 * 替代方案：if (obj instanceof String) { String s = (String) obj; s.length(); }
 */
@Service
public class InstanceofDemoService {

    private static final Logger log = LoggerFactory.getLogger(InstanceofDemoService.class);

    // 旧写法：两步走（判断 + 强转）
    public String describeOld(Object obj) {
        if (obj instanceof String) {
            String s = (String) obj;  // 必须手动强转
            return "字符串，长度: " + s.length();
        } else if (obj instanceof Integer) {
            Integer i = (Integer) obj;
            return "整数，值: " + i;
        } else if (obj instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) obj;
            return "Map，大小: " + m.size();
        }
        return "未知类型: " + (obj == null ? "null" : obj.getClass().getSimpleName());
    }

    // 新写法：一步完成（判断 + 绑定变量）
    public String describeNew(Object obj) {
        if (obj instanceof String s) {       // 判断 + 绑定到 s
            return "字符串，长度: " + s.length();
        } else if (obj instanceof Integer i) { // 绑定到 i
            return "整数，值: " + i;
        } else if (obj instanceof Map<?, ?> m) { // 泛型也可以
            return "Map，大小: " + m.size();
        }
        return "未知类型: " + (obj == null ? "null" : obj.getClass().getSimpleName());
    }

    public Map<String, Object> demo() {
        Map<String, Object> result = new LinkedHashMap<>();

        Object[] testObjects = {"Hello World", 42, Map.of("a", 1, "b", 2), 3.14};

        for (Object obj : testObjects) {
            String typeName = obj.getClass().getSimpleName();
            result.put("旧写法_" + typeName, describeOld(obj));
            result.put("新写法_" + typeName, describeNew(obj));
        }

        // 作用域：绑定变量只在 if 块内有效
        // if (obj instanceof String s) { s.length(); } ← 这里 s 有效
        // ← 这里 s 已经无效了
        result.put("说明", "绑定变量作用域仅限 if 块内，出了块就消失，更安全");

        log.info("✅ instanceof 模式匹配演示完成");
        return result;
    }
}
