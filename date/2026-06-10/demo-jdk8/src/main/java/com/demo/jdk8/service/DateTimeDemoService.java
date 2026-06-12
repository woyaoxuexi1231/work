package com.demo.jdk8.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 演示 JDK 8 核心特性：新日期时间 API（java.time）
 *
 * 核心思想：不可变、线程安全、API 直观（对比 Date/Calendar 的混乱）
 * 替代方案：java.util.Date（可变、线程不安全、月份从0开始...）
 */
@Slf4j
@Service
public class DateTimeDemoService {

    public Map<String, Object> demo() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. LocalDate / LocalTime / LocalDateTime（无时区）
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        LocalDateTime dateTime = LocalDateTime.now();
        result.put("1_LocalDate", today.toString());
        result.put("1_LocalTime", now.toString());
        result.put("1_LocalDateTime", dateTime.toString());

        // 2. 创建指定日期
        LocalDate birthday = LocalDate.of(1995, 6, 15);
        LocalTime meetingTime = LocalTime.of(14, 30, 0);
        result.put("2_指定日期", birthday.toString());
        result.put("2_指定时间", meetingTime.toString());

        // 3. 日期计算（不可变，返回新对象）
        LocalDate tomorrow = today.plusDays(1);
        LocalDate nextMonth = today.plusMonths(1);
        LocalDate lastDayOfYear = today.withDayOfYear(today.lengthOfYear());
        result.put("3_明天", tomorrow.toString());
        result.put("3_下个月", nextMonth.toString());
        result.put("3_今年最后一天", lastDayOfYear.toString());

        // 4. Duration（时间间隔：秒/纳秒级别）
        LocalTime start = LocalTime.of(9, 0);
        LocalTime end = LocalTime.of(17, 30);
        Duration workDuration = Duration.between(start, end);
        result.put("4_工作时长", workDuration.toHours() + "小时" + (workDuration.toMinutes() % 60) + "分钟");

        // 5. Period（日期间隔：年/月/日级别）
        Period age = Period.between(birthday, today);
        result.put("5_年龄", age.getYears() + "岁" + age.getMonths() + "月" + age.getDays() + "天");

        // 6. 格式化（DateTimeFormatter 线程安全，替代 SimpleDateFormat）
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss");
        String formatted = dateTime.format(formatter);
        result.put("6_格式化", formatted);

        // 7. 时区处理（ZonedDateTime）
        ZonedDateTime shanghai = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));
        ZonedDateTime tokyo = ZonedDateTime.now(ZoneId.of("Asia/Tokyo"));
        result.put("7_上海时间", shanghai.format(formatter));
        result.put("7_东京时间", tokyo.format(formatter));

        // 8. 计算两个日期之间的天数差
        LocalDate newYear = LocalDate.of(today.getYear(), 1, 1);
        long daysSinceNewYear = ChronoUnit.DAYS.between(newYear, today);
        result.put("8_今年已过天数", daysSinceNewYear);

        log.info("✅ java.time 演示完成");
        return result;
    }
}
