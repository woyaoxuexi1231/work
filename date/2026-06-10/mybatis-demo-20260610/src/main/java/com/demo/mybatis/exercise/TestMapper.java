package com.demo.mybatis.exercise;

import com.demo.mybatis.entity.Order;
import com.demo.mybatis.exercise.v1.OrderWithShipment;
import com.demo.mybatis.exercise.v2.DepartmentWithEmployee;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * @author hulei
 * @since 2026/6/10 16:28
 */


@Mapper
public interface TestMapper {

    List<OrderWithShipment> v1();

    List<DepartmentWithEmployee> v2();
}
