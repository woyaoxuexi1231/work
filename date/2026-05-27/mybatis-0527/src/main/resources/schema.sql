-- MyBatis演示项目数据库初始化脚本

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS mybatis_demo DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE mybatis_demo;

-- 用户表（用于演示Interceptor数据权限拦截器）
DROP TABLE IF EXISTS t_user;
CREATE TABLE t_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    password VARCHAR(100) NOT NULL COMMENT '密码',
    email VARCHAR(100) COMMENT '邮箱',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 订单表（用于演示Interceptor数据权限拦截器）
DROP TABLE IF EXISTS t_order;
CREATE TABLE t_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '订单ID',
    order_no VARCHAR(50) NOT NULL COMMENT '订单号',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    amount DECIMAL(10,2) NOT NULL COMMENT '订单金额',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '订单状态：0-待支付，1-已支付，2-已发货，3-已完成，4-已取消',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- 商品表（用于演示Cursor游标查询）
DROP TABLE IF EXISTS t_product;
CREATE TABLE t_product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '商品ID',
    name VARCHAR(100) NOT NULL COMMENT '商品名称',
    category VARCHAR(50) COMMENT '商品分类',
    price DECIMAL(10,2) NOT NULL COMMENT '商品价格',
    stock INT NOT NULL DEFAULT 0 COMMENT '库存数量',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '商品状态：0-下架，1-上架',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- 日志表（用于演示Executor执行器）
DROP TABLE IF EXISTS t_log;
CREATE TABLE t_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID',
    level VARCHAR(20) NOT NULL COMMENT '日志级别：INFO, WARN, ERROR',
    message TEXT COMMENT '日志消息',
    operator VARCHAR(50) COMMENT '操作人',
    ip VARCHAR(50) COMMENT '操作IP',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日志表';

-- 文章表（用于演示DatabaseId多数据库分页）
DROP TABLE IF EXISTS t_article;
CREATE TABLE t_article (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '文章ID',
    title VARCHAR(200) NOT NULL COMMENT '文章标题',
    content TEXT COMMENT '文章内容',
    author VARCHAR(50) COMMENT '作者',
    category VARCHAR(50) COMMENT '分类',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-草稿，1-已发布，2-已下架',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_status (status),
    INDEX idx_author (author)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章表';

-- 插入测试数据

-- 用户数据
INSERT INTO t_user (username, password, email, tenant_id) VALUES
('admin', '123456', 'admin@example.com', 1),
('user1', '123456', 'user1@example.com', 1),
('user2', '123456', 'user2@example.com', 1),
('tenant2_admin', '123456', 'tenant2_admin@example.com', 2),
('tenant2_user', '123456', 'tenant2_user@example.com', 2);

-- 订单数据
INSERT INTO t_order (order_no, user_id, amount, tenant_id, status) VALUES
('ORD001', 1, 100.00, 1, 1),
('ORD002', 2, 200.00, 1, 1),
('ORD003', 3, 300.00, 1, 0),
('ORD004', 4, 150.00, 2, 1),
('ORD005', 5, 250.00, 2, 0);

-- 商品数据（批量插入，用于演示游标查询）
INSERT INTO t_product (name, category, price, stock, status) VALUES
('iPhone 15', '手机', 7999.00, 100, 1),
('iPhone 15 Pro', '手机', 9999.00, 80, 1),
('MacBook Pro', '电脑', 14999.00, 50, 1),
('MacBook Air', '电脑', 9999.00, 60, 1),
('iPad Pro', '平板', 6999.00, 70, 1),
('iPad Air', '平板', 4999.00, 90, 1),
('AirPods Pro', '耳机', 1999.00, 200, 1),
('AirPods Max', '耳机', 4399.00, 30, 1),
('Apple Watch', '手表', 3299.00, 100, 1),
('Apple Watch Ultra', '手表', 6299.00, 40, 1);

-- 日志数据
INSERT INTO t_log (level, message, operator, ip) VALUES
('INFO', '用户登录成功', 'admin', '192.168.1.100'),
('INFO', '查询订单列表', 'user1', '192.168.1.101'),
('WARN', '库存不足警告', 'system', '127.0.0.1'),
('ERROR', '数据库连接失败', 'system', '127.0.0.1'),
('INFO', '用户注册成功', 'user3', '192.168.1.102');

-- 文章数据
INSERT INTO t_article (title, content, author, category, status) VALUES
('MyBatis入门教程', 'MyBatis是一个优秀的持久层框架...', '张三', '技术', 1),
('Spring Boot实战', 'Spring Boot让Java开发更简单...', '李四', '技术', 1),
('Java并发编程', 'Java并发编程是面试的重点...', '王五', '技术', 1),
('数据库优化指南', '数据库优化是提高系统性能的关键...', '赵六', '数据库', 1),
('设计模式详解', '设计模式是软件设计的经验总结...', '钱七', '架构', 1);
