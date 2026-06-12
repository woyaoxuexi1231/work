package com.demo.jdk8.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 演示 JDK 8 核心特性：Stream API
 *
 * 核心思想：声明式数据处理管道（filter → map → collect）
 * 替代方案：手动 for 循环 + 临时集合（代码多、不直观）
 */
@Slf4j
@Service
public class StreamDemoService {

    public Map<String, Object> demo() {
        Map<String, Object> result = new LinkedHashMap<>();

        List<String> words = Arrays.asList("apple", "banana", "cherry", "date", "elderberry", "fig", "grape");

        // 1. filter + collect：过滤长度 > 5 的单词
        List<String> longWords = words.stream()
                .filter(w -> w.length() > 5)
                .collect(Collectors.toList());
        result.put("1_filter_长度大于5", longWords);

        // 2. map：转换（转大写）
        List<String> upperCase = words.stream()
                .map(String::toUpperCase)
                .collect(Collectors.toList());
        result.put("2_map_转大写", upperCase);

        // 3. reduce：聚合求和
        List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        int sum = numbers.stream().reduce(0, Integer::sum);
        result.put("3_reduce_求和1到10", sum);

        // 4. collect(Collectors.groupingBy)：分组
        Map<Integer, List<String>> groupByLength = words.stream()
                .collect(Collectors.groupingBy(String::length));
        result.put("4_groupingBy_按长度分组", groupByLength);

        // 5. flatMap：扁平化
        List<List<Integer>> nested = Arrays.asList(
                Arrays.asList(1, 2), Arrays.asList(3, 4), Arrays.asList(5, 6));
        List<Integer> flat = nested.stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        result.put("5_flatMap_扁平化", flat);

        // 6. distinct + sorted：去重 + 排序
        List<Integer> messy = Arrays.asList(3, 1, 4, 1, 5, 9, 2, 6, 5, 3, 5);
        List<Integer> clean = messy.stream()
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        result.put("6_distinct_sorted", clean);

        // 7. 统计（summaryStatistics）
        IntSummaryStatistics stats = numbers.stream()
                .mapToInt(Integer::intValue)
                .summaryStatistics();
        Map<String, Object> statsMap = new LinkedHashMap<>();
        statsMap.put("count", stats.getCount());
        statsMap.put("sum", stats.getSum());
        statsMap.put("min", stats.getMin());
        statsMap.put("max", stats.getMax());
        statsMap.put("average", stats.getAverage());
        result.put("7_summaryStatistics", statsMap);

        log.info("✅ Stream API 演示完成");
        return result;
    }
}
