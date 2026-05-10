-- 创建测试数据库
CREATE DATABASE IF NOT EXISTS test_privileges;
USE test_privileges;

-- 创建测试表
CREATE TABLE IF NOT EXISTS test_table (
                                          id INT AUTO_INCREMENT PRIMARY KEY,
                                          data VARCHAR(100) NOT NULL
);

-- 插入测试数据
INSERT INTO test_table (data) VALUES ('初始测试数据');

-- 创建测试用户（如果不存在）
-- 'test_user'@'%'	允许任何地址连接（远程也能连）
CREATE USER IF NOT EXISTS 'test_user'@'%' IDENTIFIED BY 'TestPass123!';
-- 授予基本SELECT权限
GRANT SELECT ON test_privileges.test_table TO 'test_user'@'%';
FLUSH PRIVILEGES;