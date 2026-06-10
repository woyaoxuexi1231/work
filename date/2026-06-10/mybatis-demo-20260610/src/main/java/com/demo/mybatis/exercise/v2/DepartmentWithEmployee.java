package com.demo.mybatis.exercise.v2;

import lombok.Data;

import java.util.List;

/**
 * @author hulei
 * @since 2026/6/10 16:44
 */

@Data
public class DepartmentWithEmployee {
    private Long departmentId;
    private String departmentName;
    private List<Employee> employees;
}
