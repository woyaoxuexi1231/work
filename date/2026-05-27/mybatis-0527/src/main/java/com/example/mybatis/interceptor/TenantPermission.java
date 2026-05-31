package com.example.mybatis.interceptor;

import java.lang.annotation.*;

/**
 * 租户权限注解
 *
 * 标注在Mapper方法或Mapper接口上，用于控制是否添加租户过滤条件
 *
 * @author example
 * @date 2024-01-01
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TenantPermission {

    /**
     * 是否启用租户过滤
     * true: 添加租户条件（默认）
     * false: 不添加租户条件（如超级管理员查询所有数据）
     */
    boolean enabled() default true;

    /**
     * 租户字段名
     * 默认为 tenant_id，可根据表结构调整
     */
    String tenantColumn() default "tenant_id";
}
