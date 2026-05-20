CREATE TABLE IF NOT EXISTS auth_user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(64)  NOT NULL UNIQUE,
    password    VARCHAR(128) NOT NULL,
    role        VARCHAR(32)  DEFAULT 'USER',
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 初始管理员用户（密码 123456 明文，生产请加密）
INSERT IGNORE INTO auth_user (username, password, role) VALUES ('admin', '123456', 'ADMIN');
INSERT IGNORE INTO auth_user (username, password, role) VALUES ('demo', '123456', 'USER');
