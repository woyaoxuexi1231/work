package com.demo.jdk17.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 演示 JDK 11 String 新方法 + JDK 9 Optional 增强
 *
 * String 新方法：isBlank() / strip() / lines() / repeat()
 * Optional 增强：ifPresentOrElse() / or() / stream() / isEmpty()
 */
@Service
public class StringOptionalDemoService {

    private static final Logger log = LoggerFactory.getLogger(StringOptionalDemoService.class);

    public Map<String, Object> demo() {
        var result = new LinkedHashMap<String, Object>();

        // === String 新方法（JDK 11）===

        // 1. isBlank()：空字符串或全是空白 → true（比 isEmpty() 更实用）
        result.put("1_isBlank_空串", "".isBlank());         // true
        result.put("1_isBlank_空格", "   ".isBlank());       // true
        result.put("1_isBlank_有内容", "hello".isBlank());   // false

        // 2. strip()：去除前后空白（支持 Unicode 空白，比 trim() 更强大）
        String text = "  hello world  ";
        result.put("2_strip_去前后空白", "'" + text.strip() + "'");
        result.put("2_stripLeading", "'" + text.stripLeading() + "'");
        result.put("2_stripTrailing", "'" + text.stripTrailing() + "'");

        // 3. lines()：按行分割成 Stream
        String multiline = "line1\nline2\nline3\nline4";
        var lineList = multiline.lines().toList();
        result.put("3_lines_分割", lineList);
        result.put("3_lines_行数", multiline.lines().count());

        // 4. repeat()：重复拼接
        result.put("4_repeat", "abc".repeat(3));           // abcabcabc
        result.put("4_repeat_分隔线", "-".repeat(30));       // ------------------------------

        // === Optional 增强（JDK 9+）===

        // 5. ifPresentOrElse()：有值 / 无值 两个分支
        var sb = new StringBuilder();
        Optional<String> present = Optional.of("有数据");
        Optional<String> empty = Optional.empty();
        present.ifPresentOrElse(
                v -> sb.append("有值: ").append(v),
                () -> sb.append("无值")
        );
        result.put("5_ifPresentOrElse_有值", sb.toString());

        sb.setLength(0);
        empty.ifPresentOrElse(
                v -> sb.append("有值: ").append(v),
                () -> sb.append("无值（走 else 分支）")
        );
        result.put("5_ifPresentOrElse_无值", sb.toString());

        // 6. or()：如果为空，提供另一个 Optional（惰性求值）
        var value = empty.or(() -> Optional.of("备用值"));
        result.put("6_or_备用值", value.get());

        // 7. stream()：Optional 转 Stream（0 或 1 个元素）
        var streamResult = present.stream()
                .map(String::toUpperCase)
                .toList();
        result.put("7_stream_转Stream", streamResult);

        // 8. isEmpty()：和 isPresent() 相反（JDK 11）
        result.put("8_isEmpty_有值", present.isEmpty());   // false
        result.put("8_isEmpty_无值", empty.isEmpty());     // true

        log.info("✅ String + Optional 增强演示完成");
        return result;
    }
}
