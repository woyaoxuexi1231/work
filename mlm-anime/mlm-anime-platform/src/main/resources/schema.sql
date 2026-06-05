-- MLM 动漫平台 数据库建表脚本（int 编码状态）

-- 用户表
CREATE TABLE IF NOT EXISTS mlm_user (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    username    VARCHAR(50)  NOT NULL UNIQUE COMMENT '用户名',
    password    VARCHAR(100) NOT NULL COMMENT '密码',
    role        VARCHAR(20)  DEFAULT 'USER' COMMENT '角色',
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 项目表
CREATE TABLE IF NOT EXISTS project (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    name         VARCHAR(200)  NOT NULL COMMENT '项目名称',
    resource_id  BIGINT        COMMENT '引用资源ID',
    episodes_count     INT     DEFAULT 0 COMMENT '总集数',
    completed_count    INT     DEFAULT 0 COMMENT '已完成集数',
    created_by   BIGINT        COMMENT '创建者用户ID',
    is_public    TINYINT       DEFAULT 1 COMMENT '是否公开',
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目表';

-- 项目阶段负责人表
CREATE TABLE IF NOT EXISTS project_stage_member (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id  BIGINT NOT NULL COMMENT '项目ID',
    stage       INT    NOT NULL COMMENT '阶段',
    user_id     BIGINT NOT NULL COMMENT '负责人用户ID',
    INDEX idx_project_stage (project_id, stage)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目阶段负责人';

-- 剧集表
CREATE TABLE IF NOT EXISTS episode (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id      BIGINT        NOT NULL,
    episode_number  INT           NOT NULL,
    title           VARCHAR(200),
    status          INT           NOT NULL DEFAULT 2,
    step_status     INT           NOT NULL DEFAULT 0,
    script_content  TEXT,
    storyboard_content TEXT,
    result_resource_id BIGINT,
    error_msg       VARCHAR(500),
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 生成任务表
CREATE TABLE IF NOT EXISTS task (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    episode_id      BIGINT        NOT NULL,
    step            VARCHAR(30)   NOT NULL,
    model_type      VARCHAR(30)   NOT NULL,
    vendor          VARCHAR(30)   NOT NULL,
    vendor_task_id  VARCHAR(100),
    status          INT           NOT NULL DEFAULT 0,
    request_json    TEXT,
    result_json     TEXT,
    poll_count      INT           DEFAULT 0,
    max_poll_count  INT           DEFAULT 60,
    poll_interval   INT           DEFAULT 30,
    next_poll_at    DATETIME,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_episode_step (episode_id, step),
    INDEX idx_status_next_poll (status, next_poll_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 模型配置表
CREATE TABLE IF NOT EXISTS model_config (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    vendor          VARCHAR(30)  NOT NULL,
    model_type      VARCHAR(30)  NOT NULL,
    api_endpoint    VARCHAR(500),
    api_key         VARCHAR(200),
    poll_interval   INT          DEFAULT 30,
    max_poll_count  INT          DEFAULT 60,
    max_retries     INT          DEFAULT 3,
    is_enabled      TINYINT      DEFAULT 1,
    UNIQUE KEY uk_vendor_type (vendor, model_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 资源表
CREATE TABLE IF NOT EXISTS resource (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    name        VARCHAR(200)  NOT NULL,
    type        VARCHAR(30)   NOT NULL,
    oss_key     VARCHAR(500)  NOT NULL,
    oss_url     VARCHAR(500),
    file_size   BIGINT,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 审核消息表
CREATE TABLE IF NOT EXISTS review_message (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    episode_id      BIGINT        NOT NULL,
    project_id      BIGINT        NOT NULL,
    episode_number  INT,
    type            VARCHAR(30)   NOT NULL,
    title           VARCHAR(200),
    content         TEXT,
    is_read         TINYINT       DEFAULT 0,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_type_read (type, is_read),
    INDEX idx_episode (episode_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
