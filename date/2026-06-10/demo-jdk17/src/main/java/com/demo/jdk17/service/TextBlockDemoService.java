package com.demo.jdk17.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 演示 JDK 13+ Text Blocks（正式版 JDK 15）
 *
 * 核心思想：用 """...""" 写多行字符串，不需要 + 拼接和 \n 转义
 * 典型场景：JSON、SQL、HTML 模板、正则表达式
 */
@Service
public class TextBlockDemoService {

    private static final Logger log = LoggerFactory.getLogger(TextBlockDemoService.class);

    public Map<String, Object> demo() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. 旧写法：拼接 JSON
        String jsonOld = "{\n" +
                "  \"name\": \"Alice\",\n" +
                "  \"age\": 30,\n" +
                "  \"email\": \"alice@example.com\"\n" +
                "}";
        result.put("1_旧写法_JSON", jsonOld);

        // 新写法：Text Block
        String jsonNew = """
                {
                  "name": "Alice",
                  "age": 30,
                  "email": "alice@example.com"
                }
                """;
        result.put("1_新写法_JSON", jsonNew);

        // 2. SQL 查询
        String sql = """
                SELECT u.id, u.name, u.email
                FROM users u
                WHERE u.status = 'ACTIVE'
                  AND u.created_at > '2024-01-01'
                ORDER BY u.created_at DESC
                """;
        result.put("2_SQL", sql);

        // 3. 字符串插值（用 formatted / String.format）
        String name = "Bob";
        int score = 95;
        String report = """
                学生: %s
                分数: %d
                等级: %s
                """.formatted(name, score, score >= 90 ? "A" : "B");
        result.put("3_模板替换", report);

        // 4. 转义特殊字符（\" 不需要了）
        String html = """
                <div class="card">
                  <h1>标题</h1>
                  <p>这是一段<strong>HTML</strong></p>
                </div>
                """;
        result.put("4_HTML", html);

        // 5. 去除公共缩进（自动处理缩进）
        String stripped = """
                第一行
                第二行（有缩进）
                第三行
                """;
        result.put("5_自动去缩进", stripped);

        log.info("✅ Text Blocks 演示完成");
        return result;
    }
}
