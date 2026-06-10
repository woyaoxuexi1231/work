-- ============================
-- 1. 一对多场景：用户(User) → 订单(Order)
-- ============================
SET FOREIGN_KEY_CHECKS = 0;
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
