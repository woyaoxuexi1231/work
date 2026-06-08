-- ==========================================
-- Hub 中台库 DDL
-- 全部使用 IF NOT EXISTS，可重复执行
-- ==========================================

CREATE TABLE IF NOT EXISTS dict_item
(
    id        BIGINT PRIMARY KEY,
    dict_type VARCHAR(64)  NOT NULL,
    dict_code VARCHAR(64)  NOT NULL,
    dict_name VARCHAR(128) NOT NULL,
    dict_desc VARCHAR(256),
    UNIQUE KEY uk_dict_type_code (dict_type, dict_code)
);

CREATE TABLE IF NOT EXISTS leaf_alloc
(
    biz_tag     VARCHAR(64) PRIMARY KEY,
    max_id      BIGINT NOT NULL,
    step        INT    NOT NULL,
    description VARCHAR(256)
);

CREATE TABLE IF NOT EXISTS clean_stock
(
    global_id       BIGINT PRIMARY KEY,
    source_system   VARCHAR(64)    NOT NULL,
    source_type     VARCHAR(32)    NOT NULL,
    source_row_id   BIGINT         NOT NULL,
    stock_code      VARCHAR(32)    NOT NULL,
    exchange_code   VARCHAR(32),
    market_day      VARCHAR(16)    NOT NULL,
    open_price      DECIMAL(18, 4) NOT NULL,
    high_price      DECIMAL(18, 4) NOT NULL,
    low_price       DECIMAL(18, 4) NOT NULL,
    close_price     DECIMAL(18, 4) NOT NULL,
    volume_qty      BIGINT         NOT NULL,
    turnover_amount DECIMAL(18, 2) NOT NULL,
    clean_batch     VARCHAR(64)    NOT NULL,
    created_at      TIMESTAMP      NOT NULL,
    UNIQUE KEY uk_stock_source (source_row_id, source_system)
);

CREATE TABLE IF NOT EXISTS clean_trade
(
    global_id         BIGINT PRIMARY KEY,
    source_system     VARCHAR(64)    NOT NULL,
    source_type       VARCHAR(32)    NOT NULL,
    source_row_id     BIGINT         NOT NULL,
    vendor_trade_no   VARCHAR(64)    NOT NULL,
    biz_type          VARCHAR(64)    NOT NULL,
    direction         VARCHAR(32)    NOT NULL,
    amount            DECIMAL(18, 2) NOT NULL,
    status_name       VARCHAR(64)    NOT NULL,
    counterparty_name VARCHAR(128),
    clean_mode        VARCHAR(32)    NOT NULL,
    clean_batch       VARCHAR(64)    NOT NULL,
    trade_time        TIMESTAMP      NOT NULL,
    created_at        TIMESTAMP      NOT NULL,
    UNIQUE KEY uk_trade_source (source_row_id, source_system)
);

CREATE TABLE IF NOT EXISTS clean_position
(
    global_id     BIGINT PRIMARY KEY,
    source_system VARCHAR(64)    NOT NULL,
    source_type   VARCHAR(32)    NOT NULL,
    source_row_id BIGINT         NOT NULL,
    account_name  VARCHAR(128)   NOT NULL,
    stock_code    VARCHAR(32)    NOT NULL,
    holding_qty   BIGINT         NOT NULL,
    available_qty BIGINT         NOT NULL,
    cost_price    DECIMAL(18, 4) NOT NULL,
    market_value  DECIMAL(18, 2) NOT NULL,
    stat_day      VARCHAR(16)    NOT NULL,
    clean_batch   VARCHAR(64)    NOT NULL,
    created_at    TIMESTAMP      NOT NULL,
    UNIQUE KEY uk_position_source (source_row_id, source_system)
);

CREATE TABLE IF NOT EXISTS clean_asset
(
    global_id      BIGINT PRIMARY KEY,
    source_system  VARCHAR(64)    NOT NULL,
    source_type    VARCHAR(32)    NOT NULL,
    source_row_id  BIGINT         NOT NULL,
    account_name   VARCHAR(128)   NOT NULL,
    account_no     VARCHAR(64)    NOT NULL,
    cash_balance   DECIMAL(18, 2) NOT NULL,
    frozen_balance DECIMAL(18, 2) NOT NULL,
    total_asset    DECIMAL(18, 2) NOT NULL,
    stat_day       VARCHAR(16)    NOT NULL,
    clean_batch    VARCHAR(64)    NOT NULL,
    created_at     TIMESTAMP      NOT NULL,
    UNIQUE KEY uk_asset_source (source_row_id, source_system)
);

CREATE TABLE IF NOT EXISTS event_message
(
    message_id BIGINT PRIMARY KEY,
    topic      VARCHAR(64)  NOT NULL,
    biz_key    VARCHAR(128) NOT NULL,
    payload    TEXT         NOT NULL,
    status     VARCHAR(32)  NOT NULL,
    created_at TIMESTAMP    NOT NULL
);

CREATE TABLE IF NOT EXISTS tx_coordination_log
(
    id            BIGINT PRIMARY KEY,
    source_system VARCHAR(64)  NOT NULL,
    phase         VARCHAR(32)  NOT NULL,
    detail        VARCHAR(256) NOT NULL,
    created_at    TIMESTAMP    NOT NULL
);

CREATE TABLE IF NOT EXISTS sync_task
(
    id                 BIGINT PRIMARY KEY,
    data_source_key    VARCHAR(64),
    data_source_name   VARCHAR(128),
    datasource_type    VARCHAR(32),
    page_size          INT                  DEFAULT 2,
    sync_type          VARCHAR(16)          DEFAULT 'INCREMENTAL' COMMENT 'FULL 全量 / INCREMENTAL 增量',
    status             VARCHAR(32) NOT NULL DEFAULT 'IDLE',
    progress           INT                  DEFAULT 0,
    total_pulled_count INT                  DEFAULT 0,
    total_saved_count  INT                  DEFAULT 0,
    submitted_at       TIMESTAMP,
    started_at         TIMESTAMP,
    finished_at        TIMESTAMP,
    message            VARCHAR(256),
    error_message      VARCHAR(1024)
);

CREATE TABLE IF NOT EXISTS sync_business_record
(
    id            BIGINT PRIMARY KEY,
    task_id       BIGINT      NOT NULL,
    business_code VARCHAR(32) NOT NULL,
    status        VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
    page_count    INT                  DEFAULT 0,
    pulled_count  INT                  DEFAULT 0,
    saved_count   INT                  DEFAULT 0,
    last_row_id   BIGINT               DEFAULT 0,
    error_message VARCHAR(1024),
    started_at    TIMESTAMP,
    finished_at   TIMESTAMP,

    KEY idx_record_task_id (task_id)
);

-- 批次耗时明细：每批落库一条记录，用于性能分析（非常详细）
CREATE TABLE IF NOT EXISTS sync_batch_metrics
(
    id                          BIGINT PRIMARY KEY,
    record_id                   BIGINT       NOT NULL COMMENT '关联 sync_business_record.id',
    batch_no                    INT          NOT NULL COMMENT '页码',

    -- 数据量
    pulled_count                INT       DEFAULT 0 COMMENT '本页拉取行数',
    saved_count                 INT       DEFAULT 0 COMMENT '本页落库行数',
    insert_count                INT       DEFAULT 0 COMMENT '本页新增行数',
    update_count                INT       DEFAULT 0 COMMENT '本页更新行数',

    -- 各阶段时间戳（由外部自行计算耗时，TIMESTAMP(3)精确到毫秒）
    fetch_started_at            TIMESTAMP(3) NULL COMMENT '拉取上游数据开始节点',
    fetch_finished_at           TIMESTAMP(3) NULL COMMENT '拉取上游数据结束节点',
    fetch_queued_at             TIMESTAMP(3) NULL COMMENT '上游数据进入阻塞队列的时间节点',
    fetch_queued_finished_at    TIMESTAMP(3) NULL COMMENT '上游数据移出阻塞队列的时间节点',
    id_gen_started_at           TIMESTAMP(3) NULL COMMENT '为上游数据生成分布式ID的时间节点',
    id_gen_finished_at          TIMESTAMP(3) NULL COMMENT '为上游数据生成分布式ID结束节点',
    transform_started_at        TIMESTAMP(3) NULL COMMENT '数据转换开始',
    transform_finished_at       TIMESTAMP(3) NULL COMMENT '数据转换结束',
    existing_query_started_at   TIMESTAMP(3) NULL COMMENT '查询已存在的数据 开始时间',
    existing_query_finished_at  TIMESTAMP(3) NULL COMMENT '查询已存在的数据 结束时间',
    split_started_at            TIMESTAMP(3) NULL COMMENT '数据拆分开始',
    split_finished_at           TIMESTAMP(3) NULL COMMENT '数据拆分结束',
    insert_started_at           TIMESTAMP(3) NULL COMMENT '插入新数据开始',
    insert_finished_at          TIMESTAMP(3) NULL COMMENT '插入新数据结束',
    update_started_at           TIMESTAMP(3) NULL COMMENT '更新已存在数据开始',
    update_finished_at          TIMESTAMP(3) NULL COMMENT '批量更新已存在数据结束',
    error_message               TEXT NULL COMMENT '异常信息',
    recorded_at                 TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_batch_record (record_id)
);
