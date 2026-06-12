package com.demo.jdk8.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 演示 JDK 8 核心特性：Lambda 表达式
 *
 * 核心思想：函数式编程 —— 把"行为"当作参数传递
 * 替代方案：匿名内部类（冗长、不直观）
 */
@Slf4j
@Service
public class LambdaDemoService {

    public Map<String, Object> demo() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. 排序：匿名内部类 vs Lambda
        List<String> names = new ArrayList<>(Arrays.asList("Charlie", "Alice", "Bob", "David"));
        // 旧写法：names.sort(new Comparator<String>() { ... })
        // 新写法：一行 Lambda
        names.sort((a, b) -> a.compareTo(b));
        result.put("1_排序结果", names);

        // 2. 过滤：用 Predicate 函数式接口
        List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        Predicate<Integer> isEven = n -> n % 2 == 0;
        List<Integer> evenNumbers = numbers.stream().filter(isEven).collect(Collectors.toList());
        result.put("2_过滤偶数", evenNumbers);

        // 3. 转换：用 Function 函数式接口
        Function<String, Integer> getLength = String::length;
        List<Integer> lengths = names.stream().map(getLength).collect(Collectors.toList());
        result.put("3_名字长度", lengths);

        // 4. 多参数 Lambda
        // 计算两个数的和、差
        BinaryOp add = (a, b) -> a + b;
        BinaryOp subtract = (a, b) -> a - b;
        result.put("4_加法_10+3", add.apply(10, 3));
        result.put("4_减法_10-3", subtract.apply(10, 3));

        // 5. Lambda 捕获外部变量（effectively final）
        int factor = 10;
        Function<Integer, Integer> multiply = n -> n * factor;
        List<Integer> multiplied = numbers.stream().map(multiply).collect(Collectors.toList());
        result.put("5_乘以10", multiplied);

        log.info("✅ Lambda 演示完成");
        return result;
    }

    // 自定义函数式接口（只有一个抽象方法 = SAM）
    @FunctionalInterface
    interface BinaryOp {
        int apply(int a, int b);
    }
}
