package com.demo.mybatis.entity;

import java.util.List;

/**
 * 学生 — 多对多的一方
 * 一个学生可以选多门课
 */
public class Student {
    private Long id;
    private String name;

    // ▼ collection：多对多 — 学生选的课程列表
    private List<Course> courses;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<Course> getCourses() { return courses; }
    public void setCourses(List<Course> courses) { this.courses = courses; }

    @Override
    public String toString() {
        return "Student{id=" + id + ", name='" + name + "', courses=" + courses + "}";
    }
}
