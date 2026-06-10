package com.demo.mybatis.exercise;

import com.demo.mybatis.exercise.v1.OrderWithShipment;
import com.demo.mybatis.exercise.v2.DepartmentWithEmployee;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author hulei
 * @since 2026/6/10 16:20
 */

@Slf4j
@Component
public class TestBean {

    @Resource
    TestMapper testMapper;

    public void v1() {
        // 练习题1：物流单(Shipment) ↔ 订单(Order) — association
        List<OrderWithShipment> orderWithShipments = testMapper.v1();
        log.info("物流单(Shipment) ↔ 订单(Order)");
        orderWithShipments.forEach(orderWithShipment -> log.info("{}", orderWithShipment));
    }

    public void v2() {
        // 练习题2：部门(Department) ↔ 员工(Employee) — collection
        List<DepartmentWithEmployee> departmentWithEmployees = testMapper.v2();
        log.info("部门(Department) ↔ 员工(Employee)");
        departmentWithEmployees.forEach(departmentWithEmployee -> log.info("{}", departmentWithEmployee));
    }
}
