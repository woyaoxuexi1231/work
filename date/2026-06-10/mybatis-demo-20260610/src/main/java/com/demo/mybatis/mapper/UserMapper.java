package com.demo.mybatis.mapper;

import com.demo.mybatis.entity.User;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface UserMapper {

    /**
     * 🔑 collection 演示（一对多）：查用户时，嵌套查出所有订单
     * XML 里用 <collection> 做一对多映射
     */
    User selectUserWithOrders(Long userId);

    /**
     * 🔑 collection 子查询方式：用 N+1 子查询代替一条 JOIN SQL
     */
    User selectUserWithOrdersBySubQuery(Long userId);

    /**
     * 所有用户（不带订单）
     */
    List<User> selectAll();
}
