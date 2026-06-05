package com.mlm.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.NoArgsConstructor;

/**
 * 用户实体 — 对应数据库 mlm_user 表
 * <p>
 * 存储系统用户的基本信息。用户认证和登录功能已迁移至
 * 外部认证网关（Gateway），本实体仅用于用户信息的查询
 * 和阶段负责人选择。
 * <p>
 * 【安全说明】
 * password 字段标记了 {@link JsonIgnore}，确保 HTTP 响应中
 * 不会泄露密码哈希。
 *
 * @author mlm
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@TableName("mlm_user")
public class User {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户名 */
    private String username;

    /** 密码哈希（JSON 序列化时忽略） */
    @JsonIgnore
    private String password;

    /** 用户角色 */
    private String role;
}
