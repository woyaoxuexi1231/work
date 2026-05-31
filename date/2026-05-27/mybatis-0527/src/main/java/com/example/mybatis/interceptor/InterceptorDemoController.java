package com.example.mybatis.interceptor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.mybatis.interceptor.entity.User;
import com.example.mybatis.interceptor.entity.Order;
import com.example.mybatis.interceptor.mapper.UserMapper;
import com.example.mybatis.interceptor.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Interceptor演示Controller
 *
 * 演示两个拦截器的效果：
 * 1. DataPermissionInterceptor - 数据权限拦截器
 * 2. SqlCostInterceptor - SQL执行时间监控拦截器
 */
@Slf4j
@RestController
@RequestMapping("/interceptor")
@RequiredArgsConstructor
public class InterceptorDemoController {

    private final UserMapper userMapper;
    private final OrderMapper orderMapper;

    /**
     * 查询用户列表 - 会触发数据权限拦截器
     */
    @GetMapping("/users")
    public List<User> listUsers() {
        // 拦截器会自动添加 tenant_id = xxx 条件
        return userMapper.selectList(null);
    }

    /**
     * 查询订单列表 - 会触发数据权限拦截器
     */
    @GetMapping("/orders")
    public List<Order> listOrders() {
        // 拦截器会自动添加 tenant_id = xxx 条件
        return orderMapper.selectList(null);
    }

    /**
     * 模拟慢SQL - 会触发SQL监控拦截器
     */
    @GetMapping("/slow-sql")
    public String slowSql() {
        // 模拟一个耗时操作
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "slow sql executed";
    }

    /**
     * 添加测试用户
     */
    @PostMapping("/user")
    public String addUser(@RequestBody User user) {
        user.setTenantId(1L);
        userMapper.insert(user);
        return "success";
    }

    /**
     * 添加测试订单
     */
    @PostMapping("/order")
    public String addOrder(@RequestBody Order order) {
        order.setTenantId(1L);
        orderMapper.insert(order);
        return "success";
    }

    /**
     * 按条件查询用户
     */
    @GetMapping("/user/{username}")
    public User getUser(@PathVariable String username) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        return userMapper.selectOne(wrapper);
    }
}
