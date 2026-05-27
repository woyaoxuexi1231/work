package com.example.mybatis.interceptor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mybatis.interceptor.TenantPermission;
import com.example.mybatis.interceptor.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.io.Serializable;
import java.util.List;

/**
 * 用户Mapper接口
 *
 * 【注解使用说明】
 * 1. 在类上加 @TenantPermission：该接口所有方法都会被拦截
 * 2. 在方法上加 @TenantPermission：只拦截该方法
 * 3. @TenantPermission(enabled = false)：跳过拦截（如管理员查询所有数据）
 *
 * 【注解优先级】
 * 方法注解 > 类注解 > 默认行为
 */
@Mapper
@TenantPermission(enabled = true) // 类级别：默认所有方法都添加租户过滤
public interface UserMapper extends BaseMapper<User> {

    /**
     * 查询所有用户（会被拦截，添加租户条件）
     */
    @Select("SELECT * FROM t_user WHERE deleted = 0")
    List<User> selectAllUsers();

    /**
     * 根据ID查询用户（会被拦截，添加租户条件）
     */
    @Select("SELECT * FROM t_user WHERE id = #{id} AND deleted = 0")
    User selectUserById(@Param("id") Serializable id);

    /**
     * 管理员查询所有租户的用户（不拦截）
     *
     * 使用 @TenantPermission(enabled = false) 跳过拦截
     * 场景：超级管理员需要查看所有租户的数据
     */
    @TenantPermission(enabled = false)
    @Select("SELECT * FROM t_user WHERE deleted = 0")
    List<User> selectAllUsersForAdmin();

    /**
     * 查询特定租户的用户（使用自定义租户字段）
     *
     * 场景：某些表的租户字段不叫tenant_id，而是叫company_id
     */
    @TenantPermission(tenantColumn = "company_id")
    @Select("SELECT * FROM t_user WHERE deleted = 0")
    List<User> selectUsersByCompanyId();
}
