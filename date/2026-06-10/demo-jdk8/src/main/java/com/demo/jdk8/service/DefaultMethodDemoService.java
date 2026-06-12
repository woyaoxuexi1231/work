package com.demo.jdk8.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 演示 JDK 8 核心特性：默认方法（default method）
 *
 * 核心思想：接口中可以有具体实现，解决"接口演进"问题
 * 典型场景：JDK 8 给 List 加了 forEach()，不需要所有实现类都重写
 */
@Slf4j
@Service
public class DefaultMethodDemoService {

    // 定义一个带默认方法的接口
    interface Greeter {
        // 抽象方法（必须实现）
        String name();

        // 默认方法（可以有实现，子类可以选择重写或使用默认）
        default String greet() {
            return "Hello, " + name() + "!";
        }

        // 静态方法（JDK 8 也允许接口有 static 方法）
        static String format(String msg) {
            return "[" + msg + "]";
        }
    }

    // 实现类1：使用默认 greet
    static class ChineseGreeter implements Greeter {
        @Override
        public String name() {
            return "张三";
        }
    }

    // 实现类2：重写默认 greet
    static class FormalGreeter implements Greeter {
        @Override
        public String name() {
            return "李四";
        }

        @Override
        public String greet() {
            return "尊敬的 " + name() + "，您好！";
        }
    }

    public Map<String, Object> demo() {
        Map<String, Object> result = new LinkedHashMap<>();

        Greeter chinese = new ChineseGreeter();
        Greeter formal = new FormalGreeter();

        // 1. 使用默认实现
        result.put("1_默认greet", chinese.greet());

        // 2. 重写后的实现
        result.put("2_重写greet", formal.greet());

        // 3. 接口的静态方法
        result.put("3_static方法", Greeter.format("这是静态方法调用"));

        // 4. 多继承冲突解决：如果两个接口有相同默认方法，子类必须重写
        // 这就是 JDK 8 解决"菱形继承"问题的方式
        result.put("4_说明", "多接口同名默认方法冲突时，实现类必须重写并显式选择");

        log.info("✅ 默认方法演示完成");
        return result;
    }
}
