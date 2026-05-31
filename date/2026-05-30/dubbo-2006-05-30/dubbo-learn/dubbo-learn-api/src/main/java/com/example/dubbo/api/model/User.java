package com.example.dubbo.api.model;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户模型 — Dubbo RPC 传输的 POJO。
 *
 * 【面试要点：为什么 Dubbo 的 POJO 必须实现 Serializable？】
 * Dubbo 默认使用 Hessian2 序列化，要求所有传输对象实现 java.io.Serializable。
 * 不实现 → 调用时抛 NotSerializableException。
 */
public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private Integer age;
    private String email;
    private Date createTime;

    public User() {}

    public User(Long id, String name, Integer age) {
        this.id = id;
        this.name = name;
        this.age = age;
        this.createTime = new Date();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    @Override
    public String toString() {
        return "User{id=" + id + ", name='" + name + "', age=" + age
                + ", email='" + email + "'}";
    }
}
