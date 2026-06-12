package com.demo.jdk17.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 演示 JDK 14+ 增强的 NullPointerException 信息
 *
 * 核心思想：NPE 错误信息精准指出哪个变量是 null
 * 旧信息：NullPointerException（完全不知道哪个变量是 null）
 * 新信息：Cannot invoke "String.length()" because "a.b.name" is null
 */
@Service
public class NpeDemoService {

    private static final Logger log = LoggerFactory.getLogger(NpeDemoService.class);

    static class User {
        String name;
        Address address;

        User(String name, Address address) {
            this.name = name;
            this.address = address;
        }
    }

    static class Address {
        String city;
        String street;

        Address(String city, String street) {
            this.city = city;
            this.street = street;
        }
    }

    public Map<String, Object> demo() {
        var result = new LinkedHashMap<String, Object>();

        // 1. 简单 NPE
        try {
            String text = null;
            int len = text.length();  // 会抛出增强 NPE
        } catch (NullPointerException e) {
            result.put("1_简单NPE", e.getMessage());
        }

        // 2. 链式调用 NPE（精准指出哪一环是 null）
        try {
            User user = new User("Alice", null);  // address 是 null
            String city = user.address.city;      // 会指出 "user.address" is null
        } catch (NullPointerException e) {
            result.put("2_链式NPE", e.getMessage());
        }

        // 3. 嵌套更深的 NPE
        try {
            User user = new User("Bob", new Address(null, null));
            String upperCity = user.address.city.toUpperCase();  // city is null
        } catch (NullPointerException e) {
            result.put("3_深层NPE", e.getMessage());
        }

        result.put("4_说明", "JDK 14+ 的 NPE 会告诉你具体哪个变量是 null，再也不用对着堆栈猜了");

        log.info("✅ NPE 增强演示完成");
        return result;
    }
}
