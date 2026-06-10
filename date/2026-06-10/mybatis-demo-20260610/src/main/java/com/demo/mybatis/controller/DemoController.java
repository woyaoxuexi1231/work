package com.demo.mybatis.controller;

import com.demo.mybatis.entity.*;
import com.demo.mybatis.mapper.*;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 提供 REST 接口，方便用 Postman/浏览器测试
 */
@RestController
@RequestMapping("/api")
public class DemoController {

    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final StudentMapper studentMapper;
    private final CourseMapper courseMapper;

    public DemoController(OrderMapper orderMapper, UserMapper userMapper,
                          StudentMapper studentMapper, CourseMapper courseMapper) {
        this.orderMapper = orderMapper;
        this.userMapper = userMapper;
        this.studentMapper = studentMapper;
        this.courseMapper = courseMapper;
    }

    @GetMapping("/orders/{id}")
    public Order getOrderWithUser(@PathVariable Long id) {
        return orderMapper.selectOrderWithUser(id);
    }

    @GetMapping("/users/{id}")
    public User getUserWithOrders(@PathVariable Long id) {
        return userMapper.selectUserWithOrders(id);
    }

    @GetMapping("/students/{id}")
    public Student getStudentWithCourses(@PathVariable Long id) {
        return studentMapper.selectStudentWithCourses(id);
    }

    @GetMapping("/courses/{id}")
    public Course getCourseWithStudents(@PathVariable Long id) {
        return courseMapper.selectCourseWithStudents(id);
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        Map<String, Object> map = new HashMap<>();
        map.put("users", userMapper.selectAll());
        map.put("orders", orderMapper.selectAll());
        return map;
    }
}
