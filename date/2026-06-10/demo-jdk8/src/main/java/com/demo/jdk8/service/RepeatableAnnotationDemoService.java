package com.demo.jdk8.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.annotation.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 演示 JDK 8 核心特性：重复注解（@Repeatable）
 *
 * 核心思想：同一个注解可以在同一个地方使用多次（以前只能一次）
 * 典型场景：一个方法需要多个角色才能访问
 */
@Slf4j
@Service
public class RepeatableAnnotationDemoService {

    // 1. 定义"容器注解"（JDK 8 要求）
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Roles {
        Role[] value();
    }

    // 2. 定义可重复注解，用 @Repeatable 指向容器
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Repeatable(Roles.class)
    @interface Role {
        String value();
    }

    // 3. 在方法上使用多次
    @Role("ADMIN")
    @Role("MANAGER")
    @Role("OWNER")
    public void adminMethod() {
        // 业务逻辑...
    }

    public Map<String, Object> demo() throws NoSuchMethodException {
        Map<String, Object> result = new LinkedHashMap<>();

        // 4. 反射读取重复注解
        Role[] roles = RepeatableAnnotationDemoService.class
                .getMethod("adminMethod")
                .getAnnotationsByType(Role.class);

        result.put("1_注解个数", roles.length);
        for (int i = 0; i < roles.length; i++) {
            result.put("2_角色" + (i + 1), roles[i].value());
        }

        // 5. 对比旧方案：JDK 8 之前必须用数组属性
        // @AccessRoles({"ADMIN", "MANAGER"}) — 不够直观
        result.put("3_说明", "JDK 8 前: @Roles({\"ADMIN\",\"MANAGER\"})；JDK 8 后: 直接写多个 @Role");

        log.info("✅ 重复注解演示完成");
        return result;
    }
}
public class RepeatableAnnotationDemoService {
    
}
