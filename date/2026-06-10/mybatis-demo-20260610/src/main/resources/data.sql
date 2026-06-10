-- 用户（INSERT IGNORE 防止重复启动报错）
INSERT IGNORE INTO user_info (id, name) VALUES (1, '张三');
INSERT IGNORE INTO user_info (id, name) VALUES (2, '李四');

-- 订单（张三有2个订单，李四有1个订单）
INSERT IGNORE INTO user_order (id, user_id, product_name, amount) VALUES (101, 1, 'iPhone 16', 6999.00);
INSERT IGNORE INTO user_order (id, user_id, product_name, amount) VALUES (102, 1, 'AirPods Pro', 1899.00);
INSERT IGNORE INTO user_order (id, user_id, product_name, amount) VALUES (103, 2, 'MacBook Pro', 14999.00);

-- 学生
INSERT IGNORE INTO student (id, name) VALUES (1, '小明');
INSERT IGNORE INTO student (id, name) VALUES (2, '小红');

-- 课程
INSERT IGNORE INTO course (id, name) VALUES (1, '数学');
INSERT IGNORE INTO course (id, name) VALUES (2, '语文');
INSERT IGNORE INTO course (id, name) VALUES (3, '英语');

-- 多对多关系：小明选了数学+语文+英语，小红选了数学+英语
INSERT IGNORE INTO student_course (student_id, course_id) VALUES (1, 1);
INSERT IGNORE INTO student_course (student_id, course_id) VALUES (1, 2);
INSERT IGNORE INTO student_course (student_id, course_id) VALUES (1, 3);
INSERT IGNORE INTO student_course (student_id, course_id) VALUES (2, 1);
INSERT IGNORE INTO student_course (student_id, course_id) VALUES (2, 3);
