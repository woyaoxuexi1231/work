package com.demo.mybatis.mapper;

import com.demo.mybatis.entity.Student;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StudentMapper {

    /**
     * 🔑 collection 演示（多对多）：查学生时查出所有课程
     * 多对多在 MyBatis 里也是用 <collection>，只是 SQL 多 JOIN 一张中间表
     */
    Student selectStudentWithCourses(Long studentId);

    /**
     * 🔑 子查询方式，分两步查
     */
    Student selectStudentWithCoursesBySubQuery(Long studentId);
}
