package com.demo.mybatis.entity;

import java.util.List;

/**
 * 课程 — 多对多的另一方
 */
public class Course {
    private Long id;
    private String name;

    // 如果要做双向查询，课程被哪些学生选了
    private List<Student> students;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<Student> getStudents() { return students; }
    public void setStudents(List<Student> students) { this.students = students; }

    @Override
    public String toString() {
        return "Course{id=" + id + ", name='" + name + "'}";
    }
}
