package com.demo.mybatis.runner;

import com.demo.mybatis.entity.*;
import com.demo.mybatis.exercise.TestBean;
import com.demo.mybatis.mapper.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时自动执行，演示所有 association 和 collection 用法
 * 控制台会打印出 SQL 和结果，方便理解
 */
@Component
public class DemoRunner implements CommandLineRunner {

    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final StudentMapper studentMapper;
    private final CourseMapper courseMapper;
    private final TestBean testBean;

    public DemoRunner(OrderMapper orderMapper, UserMapper userMapper,
                      StudentMapper studentMapper, CourseMapper courseMapper,
                      TestBean testBean
                      ) {
        this.orderMapper = orderMapper;
        this.userMapper = userMapper;
        this.studentMapper = studentMapper;
        this.courseMapper = courseMapper;
        this.testBean = testBean;
    }

    @Override
    public void run(String... args) {
        printHeader("========== MyBatis collection / association 练习 ==========");

        demoAssociation();   // association：多对一
        demoCollection();    // collection：一对多（两种方式）
        demoManyToMany();    // collection：多对多
        testBean.v1();
        testBean.v2();
    }

    // ================================
    // 1. association — 多对一
    // ================================
    private void demoAssociation() {
        printHeader("1️⃣  association（多对一）：查订单 → 嵌套所属用户");

        // 查订单 id=101，同时查出对应的用户
        Order order = orderMapper.selectOrderWithUser(101L);

        System.out.println("【结果】");
        System.out.println("  订单: " + order.getProductName() + " | 金额: " + order.getAmount());
        System.out.println("  所属用户: " + order.getUser().getName());
        System.out.println();
        System.out.println("  → Order.user 属性被 <association> 自动填充为 User 对象");
        System.out.println("  → SQL 只发了一条 JOIN，MyBatis 把扁平结果拆成嵌套对象");
    }

    // ================================
    // 2. collection — 一对多
    // ================================
    private void demoCollection() {
        printHeader("2️⃣  collection（一对多）：查用户 → 嵌套所有订单");

        // 方式一：一条 JOIN SQL
        User user1 = userMapper.selectUserWithOrders(1L);

        System.out.println("【方式一：一条 JOIN SQL + <collection> 结果映射】");
        System.out.println("  用户: " + user1.getName());
        System.out.println("  订单数: " + user1.getOrders().size());
        for (Order o : user1.getOrders()) {
            System.out.println("    → " + o.getProductName() + " ¥" + o.getAmount());
        }
        System.out.println();
        System.out.println("  → 原始 SQL 返回 2 行（张三重复2次），MyBatis 按 id 去重合并");
        System.out.println("  → <id> 标签标记分组键，ofType 指定集合元素类型");

        // 方式二：子查询
        User user1b = userMapper.selectUserWithOrdersBySubQuery(1L);
        System.out.println();
        System.out.println("【方式二：子查询（N+1）】");
        System.out.println("  用户: " + user1b.getName());
        System.out.println("  订单数: " + user1b.getOrders().size());
        System.out.println("  → 控制台会看到 2 条 SQL（先查用户 + 再查订单）");
    }

    // ================================
    // 3. collection — 多对多
    // ================================
    private void demoManyToMany() {
        printHeader("3️⃣  collection（多对多）：学生 ↔ 课程");

        // 查小明，看他选了哪些课
        Student s1 = studentMapper.selectStudentWithCourses(1L);
        System.out.println("【小明选的课】");
        System.out.println("  学生: " + s1.getName());
        for (Course c : s1.getCourses()) {
            System.out.println("    → " + c.getName());
        }
        System.out.println("  → 多对多和一对多用的都是 <collection>！");
        System.out.println("  → 区别只在 SQL：多 JOIN 了一张中间表 student_course");

        // 反向：查数学课，看哪些学生选了
        Course c1 = courseMapper.selectCourseWithStudents(1L);
        System.out.println();
        System.out.println("【选了数学课的学生】");
        System.out.println("  课程: " + c1.getName());
        for (Student s : c1.getStudents()) {
            System.out.println("    → " + s.getName());
        }
        System.out.println("  → Collection 方向无所谓，SQL 反过来 JOIN 即可");

        // 子查询方式
        Student s2 = studentMapper.selectStudentWithCoursesBySubQuery(1L);
        System.out.println();
        System.out.println("【子查询方式（多对多）】小明选了 " + s2.getCourses().size() + " 门课");
    }

    private static void printHeader(String title) {
        System.out.println();
        System.out.println("=".repeat(60));
        System.out.println(title);
        System.out.println("=".repeat(60));
    }
}
