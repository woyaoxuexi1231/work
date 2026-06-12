package com.demo.jdk8.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Supplier;

/**
 * 演示 JDK 8 核心特性：方法引用（Method Reference）
 *
 * 核心思想：Lambda 的语法糖，当 Lambda 体只有一个方法调用时，可用 :: 简写
 * 四种形式：静态方法、实例方法、任意对象的实例方法、构造器
 */
@Slf4j
@Service
public class MethodRefDemoService {

    public Map<String, Object> demo() {
        Map<String, Object> result = new LinkedHashMap<>();

        List<String> names = Arrays.asList("charlie", "alice", "bob", "david");

        // 1. 静态方法引用：Class::staticMethod
        // Lambda: s -> Integer.parseInt(s)
        List<String> numbers = Arrays.asList("1", "2", "3", "4", "5");
        List<Integer> parsed = new ArrayList<>();
        numbers.stream().map(Integer::parseInt).forEach(parsed::add);
        result.put("1_静态方法_Integer_parseInt", parsed);

        // 2. 实例方法引用：instance::method
        // Lambda: s -> s.toUpperCase()
        List<String> upper = new ArrayList<>();
        names.stream().map(String::toUpperCase).forEach(upper::add);
        result.put("2_实例方法_String_toUpperCase", upper);

        // 3. 任意对象的实例方法：Class::method（第一个参数变成调用者）
        // Lambda: (a, b) -> a.compareTo(b)
        List<String> sorted = new ArrayList<>(names);
        sorted.sort(String::compareTo);
        result.put("3_任意对象方法_String_compareTo", sorted);

        // 4. 构造器引用：Class::new
        // Lambda: () -> new ArrayList<>()
        Supplier<List<String>> listFactory = ArrayList::new;
        List<String> newList = listFactory.get();
        newList.addAll(names);
        result.put("4_构造器引用_ArrayList_new", newList);

        // 5. 数组构造器引用
        // Lambda: size -> new String[size]
        String[] arr = names.stream().toArray(String[]::new);
        result.put("5_数组构造器", Arrays.toString(arr));

        log.info("✅ 方法引用演示完成");
        return result;
    }
}
