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
    stage       INT    NOT NULL COMMENT '阶段(2=剧本创作,3=剧本审核,4=拆分镜,5=AI成片,6=终审)',
    user_id     BIGINT NOT NULL COMMENT '负责人用户ID',
    INDEX idx_project_stage (project_id, stage)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目阶段负责人';

-- 剧集表
CREATE TABLE IF NOT EXISTS episode (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id      BIGINT        NOT NULL COMMENT '所属项目ID',
    episode_number  INT           NOT NULL COMMENT '集号',
    title           VARCHAR(200)  COMMENT '本集标题',
    status          INT           NOT NULL DEFAULT 2 COMMENT 'Pipeline主状态(-1失败,0初始,1成功,2=剧本创作,3=审核,4=拆分镜,5=AI成片,6=终审,7=完成)',
    step_status     INT           NOT NULL DEFAULT 0 COMMENT '步骤子状态(-1失败,0待处理,1成功,2处理中)',
    script_content  TEXT          COMMENT '剧本内容',
    storyboard_content TEXT       COMMENT '分镜JSON',
    result_resource_id BIGINT    COMMENT '成片资源ID',
    error_msg       VARCHAR(500)  COMMENT '失败原因',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='剧集表';

-- 生成任务表
CREATE TABLE IF NOT EXISTS task (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    episode_id      BIGINT        NOT NULL COMMENT '所属剧集ID',
    step            VARCHAR(30)   NOT NULL COMMENT '所属步骤',
    model_type      VARCHAR(30)   NOT NULL COMMENT '模型类型',
    vendor          VARCHAR(30)   NOT NULL COMMENT '厂商标识',
    vendor_task_id  VARCHAR(100)  COMMENT '厂商任务ID',
    status          INT           NOT NULL DEFAULT 0 COMMENT '任务状态(-1失败,0待处理,1成功,2处理中)',
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

-- 审核消息表
CREATE TABLE IF NOT EXISTS review_message (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    episode_id      BIGINT        NOT NULL COMMENT '关联剧集ID',
    project_id      BIGINT        NOT NULL COMMENT '关联项目ID',
    episode_number  INT           COMMENT '集号',
    type            VARCHAR(30)   NOT NULL COMMENT '消息类型',
    title           VARCHAR(200)  COMMENT '消息标题',
    content         TEXT          COMMENT '消息正文',
    is_read         TINYINT       DEFAULT 0 COMMENT '是否已读',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_type_read (type, is_read),
    INDEX idx_episode (episode_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审核消息表';
