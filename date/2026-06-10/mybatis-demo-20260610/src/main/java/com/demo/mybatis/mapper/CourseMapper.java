package com.demo.mybatis.mapper;

import com.demo.mybatis.entity.Course;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CourseMapper {

    /**
     * 反向查询：查一门课时，查出选了这门课的所有学生
     * 同样用 <collection>
     */
    Course selectCourseWithStudents(Long courseId);
}
