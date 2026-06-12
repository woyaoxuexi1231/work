package com.demo.jdk17.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 演示 JDK 12+ Switch 表达式（正式版 JDK 14）+ 模式匹配（预览 JDK 17）
 *
 * 核心思想：switch 可以返回值（表达式），箭头语法（->），不需要 break
 * 替代方案：传统 switch + break（冗长、容易漏写 break 导致 bug）
 */
@Service
public class SwitchDemoService {

    private static final Logger log = LoggerFactory.getLogger(SwitchDemoService.class);

    public Map<String, Object> demo() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. Switch 表达式：有返回值（不再是语句）
        int dayOfWeek = 3;
        // 旧写法：需要用变量接收，且要 break
        String dayNameOld;
        switch (dayOfWeek) {
            case 1: dayNameOld = "周一"; break;
            case 2: dayNameOld = "周二"; break;
            case 3: dayNameOld = "周三"; break;
            case 4: dayNameOld = "周四"; break;
            case 5: dayNameOld = "周五"; break;
            default: dayNameOld = "周末"; break;
        }
        result.put("1_旧写法", dayNameOld);

        // 新写法：箭头语法 + 表达式
        String dayName = switch (dayOfWeek) {
            case 1 -> "周一";
            case 2 -> "周二";
            case 3 -> "周三";
            case 4 -> "周四";
            case 5 -> "周五";
            default -> "周末";
        };
        result.put("1_新写法", dayName);

        // 2. 多值 case（一个 case 匹配多个值）
        String type = switch (dayOfWeek) {
            case 1, 2, 3, 4, 5 -> "工作日";
            case 6, 7 -> "周末";
            default -> "未知";
        };
        result.put("2_多值case", type);

        // 3. yield：块内需要多行逻辑时用 yield 返回值
        int score = 85;
        String grade = switch (score / 10) {
            case 10, 9 -> "A";
            case 8 -> {
                String prefix = "B";
                yield prefix + "+";  // 多行逻辑用 yield
            }
            case 7 -> "C";
            default -> "D";
        };
        result.put("3_yield多行", grade);

        // 4. Switch 模式匹配（JDK 17 preview）：对类型做匹配
        Object[] testObjects = {42, "hello", 3.14, true, null};
        for (Object obj : testObjects) {
            String desc = switch (obj) {
                case Integer i -> "整数: " + i;
                case String s -> "字符串, 长度: " + s.length();
                case Double d -> "浮点数: " + d;
                case Boolean b -> "布尔: " + b;
                case null -> "null值";
                default -> "其他: " + obj.getClass().getSimpleName();
            };
            result.put("4_pattern_" + (obj == null ? "null" : obj.getClass().getSimpleName()), desc);
        }

        log.info("✅ Switch 表达式演示完成");
        return result;
    }
}
