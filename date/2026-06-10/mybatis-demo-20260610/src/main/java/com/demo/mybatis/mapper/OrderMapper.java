package com.demo.mybatis.mapper;

import com.demo.mybatis.entity.Order;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface OrderMapper {

    /**
     * 🔑 association 演示：查订单时，嵌套查出所属用户
     * XML 里用 <association> 做多对一映射
     */
    Order selectOrderWithUser(Long orderId);

    /**
     * 所有订单（不含嵌套）
     */
    List<Order> selectAll();
}
