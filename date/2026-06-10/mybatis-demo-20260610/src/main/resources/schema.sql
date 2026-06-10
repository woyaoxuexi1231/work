-- ============================
-- 1. 一对多场景：用户(User) → 订单(Order)
-- ============================
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS shipment;
DROP TABLE IF EXISTS employee;
DROP TABLE IF EXISTS department;
DROP TABLE IF EXISTS user_order;
DROP TABLE IF EXISTS user_info;
DROP TABLE IF EXISTS student_course;
DROP TABLE IF EXISTS student;
DROP TABLE IF EXISTS course;
SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE user_info (
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL
);

CREATE TABLE user_order (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT NOT NULL,
    product_name VARCHAR(100),
    amount       DECIMAL(10, 2),
    FOREIGN KEY (user_id) REFERENCES user_info(id)
);

-- ============================
-- 2. 多对多场景：学生(Student) ↔ 课程(Course)
-- ============================
CREATE TABLE student (
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL
);

CREATE TABLE course (
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL
);

-- 中间表
CREATE TABLE student_course (
    student_id BIGINT NOT NULL,
    course_id  BIGINT NOT NULL,
    PRIMARY KEY (student_id, course_id),
    FOREIGN KEY (student_id) REFERENCES student(id),
    FOREIGN KEY (course_id)  REFERENCES course(id)
);

-- ============================
-- 3. 练习题1：物流单(Shipment) ↔ 订单(Order) — association
-- ============================
CREATE TABLE shipment (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id    BIGINT NOT NULL,
    tracking_no VARCHAR(50),
    status      VARCHAR(20),
    FOREIGN KEY (order_id) REFERENCES user_order(id)
);

-- ============================
-- 4. 练习题2：部门(Department) ↔ 员工(Employee) — collection
-- ============================
CREATE TABLE department (
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL
);

CREATE TABLE employee (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    name    VARCHAR(50) NOT NULL,
    dept_id BIGINT NOT NULL,
    FOREIGN KEY (dept_id) REFERENCES department(id)
);
