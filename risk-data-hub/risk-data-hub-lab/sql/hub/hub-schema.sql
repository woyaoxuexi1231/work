-- ==========================================
-- Hub 中台库 DDL
-- 全部使用 IF NOT EXISTS，可重复执行
-- ==========================================

CREATE TABLE IF NOT EXISTS dict_item (
    id BIGINT PRIMARY KEY,
    dict_type VARCHAR(64) NOT NULL,
    dict_code VARCHAR(64) NOT NULL,
    dict_name VARCHAR(128) NOT NULL,
    dict_desc VARCHAR(256),
    UNIQUE KEY uk_dict_type_code(dict_type, dict_code)
);

CREATE TABLE IF NOT EXISTS leaf_alloc (
    biz_tag VARCHAR(64) PRIMARY KEY,
    max_id BIGINT NOT NULL,
    step INT NOT NULL,
    description VARCHAR(256)
);

CREATE TABLE IF NOT EXISTS clean_stock (
    global_id BIGINT PRIMARY KEY,
    source_system VARCHAR(64) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_row_id BIGINT NOT NULL,
    stock_code VARCHAR(32) NOT NULL,
    exchange_code VARCHAR(32),
    market_day VARCHAR(16) NOT NULL,
    open_price DECIMAL(18,4) NOT NULL,
    high_price DECIMAL(18,4) NOT NULL,
    low_price DECIMAL(18,4) NOT NULL,
    close_price DECIMAL(18,4) NOT NULL,
    volume_qty BIGINT NOT NULL,
    turnover_amount DECIMAL(18,2) NOT NULL,
    clean_batch VARCHAR(64) NOT NULL,
    created_at VARCHAR(32) NOT NULL,
    UNIQUE KEY uk_stock_source(source_system, source_row_id)
);

CREATE TABLE IF NOT EXISTS clean_trade (
    global_id BIGINT PRIMARY KEY,
    source_system VARCHAR(64) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_row_id BIGINT NOT NULL,
    vendor_trade_no VARCHAR(64) NOT NULL,
    biz_type VARCHAR(64) NOT NULL,
    direction VARCHAR(32) NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    status_name VARCHAR(64) NOT NULL,
    counterparty_name VARCHAR(128),
    clean_mode VARCHAR(32) NOT NULL,
    clean_batch VARCHAR(64) NOT NULL,
    trade_time VARCHAR(32) NOT NULL,
    created_at VARCHAR(32) NOT NULL,
    UNIQUE KEY uk_trade_source(source_system, source_row_id)
);

CREATE TABLE IF NOT EXISTS clean_position (
    global_id BIGINT PRIMARY KEY,
    source_system VARCHAR(64) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_row_id BIGINT NOT NULL,
    account_name VARCHAR(128) NOT NULL,
    stock_code VARCHAR(32) NOT NULL,
    holding_qty BIGINT NOT NULL,
    available_qty BIGINT NOT NULL,
    cost_price DECIMAL(18,4) NOT NULL,
    market_value DECIMAL(18,2) NOT NULL,
    stat_day VARCHAR(16) NOT NULL,
    clean_batch VARCHAR(64) NOT NULL,
    created_at VARCHAR(32) NOT NULL,
    UNIQUE KEY uk_position_source(source_system, source_row_id)
);

CREATE TABLE IF NOT EXISTS clean_asset (
    global_id BIGINT PRIMARY KEY,
    source_system VARCHAR(64) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_row_id BIGINT NOT NULL,
    account_name VARCHAR(128) NOT NULL,
    account_no VARCHAR(64) NOT NULL,
    cash_balance DECIMAL(18,2) NOT NULL,
    frozen_balance DECIMAL(18,2) NOT NULL,
    total_asset DECIMAL(18,2) NOT NULL,
    stat_day VARCHAR(16) NOT NULL,
    clean_batch VARCHAR(64) NOT NULL,
    created_at VARCHAR(32) NOT NULL,
    UNIQUE KEY uk_asset_source(source_system, source_row_id)
);

CREATE TABLE IF NOT EXISTS event_message (
    message_id BIGINT PRIMARY KEY,
    topic VARCHAR(64) NOT NULL,
    biz_key VARCHAR(128) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at VARCHAR(32) NOT NULL
);

CREATE TABLE IF NOT EXISTS tx_coordination_log (
    id BIGINT PRIMARY KEY,
    source_system VARCHAR(64) NOT NULL,
    phase VARCHAR(32) NOT NULL,
    detail VARCHAR(256) NOT NULL,
    created_at VARCHAR(32) NOT NULL
);

CREATE TABLE IF NOT EXISTS sync_task (
    id BIGINT PRIMARY KEY,
    data_source_key VARCHAR(64),
    data_source_name VARCHAR(128),
    datasource_type VARCHAR(32),
    page_size INT DEFAULT 2,
    status VARCHAR(32) NOT NULL DEFAULT 'IDLE',
    progress INT DEFAULT 0,
    total_pulled_count INT DEFAULT 0,
    total_saved_count INT DEFAULT 0,
    submitted_at VARCHAR(32),
    started_at VARCHAR(32),
    finished_at VARCHAR(32),
    message VARCHAR(256),
    error_message VARCHAR(1024)
);

CREATE TABLE IF NOT EXISTS sync_business_record (
    id BIGINT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    business_code VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
    page_count INT DEFAULT 0,
    pulled_count INT DEFAULT 0,
    saved_count INT DEFAULT 0,
    last_row_id BIGINT DEFAULT 0,
    error_message VARCHAR(1024),
    started_at VARCHAR(32),
    finished_at VARCHAR(32),
    KEY idx_record_task_id(task_id)
);
