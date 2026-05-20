-- MLM 动漫平台 数据库建表脚本

-- 项目表（容器，不走 Pipeline）
CREATE TABLE IF NOT EXISTS project (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    name         VARCHAR(200)  NOT NULL COMMENT '项目名称',
    resource_id  BIGINT        COMMENT '可选的引用资源ID（从资源库创建时）',
    episodes_count     INT     DEFAULT 0 COMMENT '总集数',
    completed_count    INT     DEFAULT 0 COMMENT '已完成集数',
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目表';

-- 剧集表（每集独立走 Pipeline）
CREATE TABLE IF NOT EXISTS episode (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id      BIGINT        NOT NULL COMMENT '所属项目ID',
    episode_number  INT           NOT NULL COMMENT '集号',
    title           VARCHAR(200)  COMMENT '本集标题',
    status          VARCHAR(30)   NOT NULL DEFAULT 'SCRIPT_DRAFT' COMMENT 'Pipeline主状态',
    step_status     VARCHAR(30)   NOT NULL DEFAULT 'PENDING' COMMENT '当前步骤子状态',
    script_content  TEXT          COMMENT '剧本内容',
    storyboard_content TEXT       COMMENT '分镜JSON',
    result_resource_id BIGINT    COMMENT '成片资源ID',
    error_msg       VARCHAR(500)  COMMENT '失败原因',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_project (project_id),
    INDEX idx_project_episode (project_id, episode_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='剧集表';

-- 生成任务表（关联 episode 而非 project）
CREATE TABLE IF NOT EXISTS task (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    episode_id      BIGINT        NOT NULL COMMENT '所属剧集ID',
    step            VARCHAR(30)   NOT NULL COMMENT '所属步骤',
    model_type      VARCHAR(30)   NOT NULL COMMENT '模型类型',
    vendor          VARCHAR(30)   NOT NULL COMMENT '厂商标识',
    vendor_task_id  VARCHAR(100)  COMMENT '厂商任务ID',
    status          VARCHAR(30)   NOT NULL DEFAULT 'PENDING' COMMENT '任务状态',
    request_json    TEXT          COMMENT '请求参数JSON',
    result_json     TEXT          COMMENT '结果JSON',
    poll_count      INT           DEFAULT 0 COMMENT '已轮询次数',
    max_poll_count  INT           DEFAULT 60 COMMENT '最大轮询次数',
    poll_interval   INT           DEFAULT 30 COMMENT '轮询间隔(秒)',
    next_poll_at    DATETIME      COMMENT '下次轮询时间',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_episode_step (episode_id, step),
    INDEX idx_status_next_poll (status, next_poll_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生成任务表';

-- 模型配置表
CREATE TABLE IF NOT EXISTS model_config (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    vendor          VARCHAR(30)  NOT NULL COMMENT '厂商',
    model_type      VARCHAR(30)  NOT NULL COMMENT '模型类型',
    api_endpoint    VARCHAR(500) COMMENT 'API地址',
    api_key         VARCHAR(200) COMMENT 'API密钥',
    poll_interval   INT          DEFAULT 30 COMMENT '轮询间隔(秒)',
    max_poll_count  INT          DEFAULT 60 COMMENT '最大轮询次数',
    max_retries     INT          DEFAULT 3 COMMENT '最大重试次数',
    is_enabled      TINYINT      DEFAULT 1 COMMENT '是否启用',
    UNIQUE KEY uk_vendor_type (vendor, model_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型配置表';

-- 资源表
CREATE TABLE IF NOT EXISTS resource (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    name        VARCHAR(200)  NOT NULL COMMENT '资源名称',
    type        VARCHAR(30)   NOT NULL COMMENT '类型: IMAGE/VIDEO/AUDIO/TEXT',
    oss_key     VARCHAR(500)  NOT NULL COMMENT 'MinIO存储Key',
    oss_url     VARCHAR(500)  COMMENT '预签名URL',
    file_size   BIGINT        COMMENT '文件大小(bytes)',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源表';

-- 审核消息表（取代 MQ，纯数据库通知）
CREATE TABLE IF NOT EXISTS review_message (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    episode_id      BIGINT        NOT NULL COMMENT '关联剧集ID',
    project_id      BIGINT        NOT NULL COMMENT '关联项目ID',
    episode_number  INT           COMMENT '集号',
    type            VARCHAR(30)   NOT NULL COMMENT '消息类型: SCRIPT_REVIEW/EPISODE_REVIEW',
    title           VARCHAR(200)  COMMENT '消息标题',
    content         TEXT          COMMENT '消息正文',
    is_read         TINYINT       DEFAULT 0 COMMENT '是否已读',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_type_read (type, is_read),
    INDEX idx_episode (episode_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审核消息表';
