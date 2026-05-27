package com.example.dubbo.demo.api.model;

import java.io.Serializable;
import java.util.StringJoiner;

/**
 * <h3>用户领域模型</h3>
 * <p>
 * 在 Dubbo RPC 调用中，参数与返回值都需要跨 JVM 传输，
 * 因此必须实现 {@link Serializable} 接口以支持序列化与反序列化。
 * </p>
 *
 * <p><b>Dubbo 知识点映射：</b></p>
 * <ul>
 *   <li>§4 协议与序列化 — POJO 需实现 Serializable，由 Hessian2 / Kryo 等序列化器处理</li>
 *   <li>§1 核心概念 — Consumer 调用远程方法时，参数对象在网络上以二进制流传输</li>
 * </ul>
 *
 * @author Dubbo Demo Team
 * @version 1.0.0
 * @see Serializable
 */
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户唯一标识 */
    private Long id;

    /** 用户名 */
    private String username;

    /** 邮箱地址 */
    private String email;

    /** 年龄 */
    private Integer age;

    // ==================== 构造方法 ====================

    /** 无参构造（序列化框架要求） */
    public User() {}

    /**
     * 便捷构造方法。
     *
     * @param id       用户 ID
     * @param username 用户名
     * @param email    邮箱
     * @param age      年龄
     */
    public User(Long id, String username, String email, Integer age) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.age = age;
    }

    // ==================== Getter / Setter ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", User.class.getSimpleName() + "[", "]")
                .add("id=" + id)
                .add("username='" + username + "'")
                .add("email='" + email + "'")
                .add("age=" + age)
                .toString();
    }
}
