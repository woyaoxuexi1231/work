-- ============================================================================
-- redis-code-0531 初始化 SQL
-- 执行方式: mysql -h 192.168.3.100 -u root -p123456 < init.sql
-- ============================================================================

CREATE DATABASE IF NOT EXISTS redis_demo
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE redis_demo;

-- ============================================================================
-- Q4: SAGA 分布式事务——补偿日志表
-- 用于演示跨槽事务的最终一致性，记录每一步的状态流转
-- ============================================================================
CREATE TABLE IF NOT EXISTS saga_transaction (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id    VARCHAR(64)  NOT NULL COMMENT '业务订单号',
    step        VARCHAR(32)  NOT NULL COMMENT '当前步骤: PREPARE/STOCK_CHECK/STATUS_UPDATE/LIST_APPEND/COMPENSATE',
    status      VARCHAR(16)  NOT NULL COMMENT '步骤状态: RUNNING/SUCCESS/FAILED/COMPENSATED',
    payload     TEXT         COMMENT '步骤携带的 JSON 数据',
    error_msg   VARCHAR(512) COMMENT '失败原因',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_order (order_id),
    INDEX idx_step  (step, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SAGA 分布式事务日志';

-- ============================================================================
-- Q8: 槽位迁移——迁移任务状态机
-- 记录扩容过程中每个 slot 的迁移状态
-- ============================================================================
CREATE TABLE IF NOT EXISTS reshard_task (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    slot         INT          NOT NULL COMMENT '槽位编号 0-16383',
    source_node  VARCHAR(128) NOT NULL COMMENT '源节点标识',
    target_node  VARCHAR(128) NOT NULL COMMENT '目标节点标识',
    state        VARCHAR(32)  NOT NULL COMMENT '状态: PENDING / IMPORTING / MIGRATING / COMPLETED / ROLLBACK / FAILED',
    key_count    INT          DEFAULT 0 COMMENT '已迁移 key 数量',
    error_msg    VARCHAR(512) COMMENT '错误信息',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_slot (slot),
    INDEX idx_state (state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='槽位迁移任务状态机';

-- ============================================================================
-- 插入一条示例演示数据
-- ============================================================================
INSERT INTO saga_transaction (order_id, step, status, payload)
VALUES ('demo-order-001', 'PREPARE', 'RUNNING', '{"amount": 1, "sku": "phone-123"}');
