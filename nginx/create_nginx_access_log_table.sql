-- Nginx 访问记录表
CREATE TABLE IF NOT EXISTS `nginx_access_log` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `remote_addr` VARCHAR(64) DEFAULT NULL COMMENT '客户端IP地址(代理IP)',
    `remote_user` VARCHAR(128) DEFAULT NULL COMMENT '认证用户名',
    `time_local` DATETIME DEFAULT NULL COMMENT '请求时间',
    `request_method` VARCHAR(16) DEFAULT NULL COMMENT '请求方法(GET/POST等)',
    `request_uri` VARCHAR(2048) DEFAULT NULL COMMENT '请求URI',
    `request_protocol` VARCHAR(16) DEFAULT NULL COMMENT '请求协议(HTTP/1.1等)',
    `status` INT DEFAULT NULL COMMENT 'HTTP状态码',
    `body_bytes_sent` BIGINT DEFAULT NULL COMMENT '响应体字节数',
    `http_referer` VARCHAR(2048) DEFAULT NULL COMMENT '来源页面',
    `http_user_agent` VARCHAR(1024) DEFAULT NULL COMMENT '客户端User-Agent',
    `http_x_forwarded_for` VARCHAR(256) DEFAULT NULL COMMENT 'X-Forwarded-For头',
    `upstream_addr` VARCHAR(128) DEFAULT NULL COMMENT '上游服务器地址',
    `upstream_response_time` DECIMAL(10,3) DEFAULT NULL COMMENT '上游响应时间(秒)',
    `request_time` DECIMAL(10,3) DEFAULT NULL COMMENT '请求总耗时(秒)',
    `host` VARCHAR(256) DEFAULT NULL COMMENT 'Host头',
    `real_ip` VARCHAR(64) DEFAULT NULL COMMENT '真实客户端IP(从自定义头提取，唯一存储点)',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-未删除 1-已删除',
    `create_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
    `update_by` BIGINT DEFAULT NULL COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    KEY `idx_time_local` (`time_local`),
    KEY `idx_remote_addr` (`remote_addr`),
    KEY `idx_real_ip` (`real_ip`),
    KEY `idx_status` (`status`),
    KEY `idx_request_uri` (`request_uri`(255)),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Nginx访问日志记录表';

-- 如果表已存在，确保real_ip字段存在
ALTER TABLE `nginx_access_log` ADD COLUMN IF NOT EXISTS `real_ip` VARCHAR(64) DEFAULT NULL COMMENT '真实客户端IP(从自定义头提取，唯一存储点)';
ALTER TABLE `nginx_access_log` ADD INDEX IF NOT EXISTS `idx_real_ip` (`real_ip`);

-- 如果需要清理旧的冗余字段（可选执行）
-- ALTER TABLE `nginx_access_log` DROP COLUMN IF EXISTS `http_x_custom_real_ip`;
-- ALTER TABLE `nginx_access_log` DROP COLUMN IF EXISTS `http_x_real_ip`;
-- ALTER TABLE `nginx_access_log` DROP COLUMN IF EXISTS `http_x_client_ip`;
-- ALTER TABLE `nginx_access_log` DROP COLUMN IF EXISTS `http_x_remote_addr`;
-- ALTER TABLE `nginx_access_log` DROP COLUMN IF EXISTS `http_x_public_ip`;
