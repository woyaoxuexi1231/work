-- ==========================================
-- Broker 券商库 DDL
-- ==========================================

CREATE TABLE IF NOT EXISTS broker_stock_quote (
    id BIGINT PRIMARY KEY,
    quote_code VARCHAR(64) NOT NULL,
    secu_code VARCHAR(32) NOT NULL,
    trade_day VARCHAR(16),
    exchange_name VARCHAR(32),
    open_px DECIMAL(18,4),
    high_px DECIMAL(18,4),
    low_px DECIMAL(18,4),
    close_px DECIMAL(18,4),
    vol_num BIGINT,
    turnover_amt DECIMAL(18,2),
    sync_flag INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS broker_trade_deal (
    id BIGINT PRIMARY KEY,
    deal_code VARCHAR(64) NOT NULL,
    secu_code VARCHAR(32) NOT NULL,
    client_full_name VARCHAR(128),
    bs_flag VARCHAR(4),
    deal_volume BIGINT,
    deal_price DECIMAL(18,4),
    turnover_amount DECIMAL(18,2),
    status_mark VARCHAR(4),
    deal_at VARCHAR(32),
    sync_flag INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS broker_position_balance (
    id BIGINT PRIMARY KEY,
    client_full_name VARCHAR(128),
    secu_code VARCHAR(32) NOT NULL,
    current_volume BIGINT,
    enable_volume BIGINT,
    cost_px DECIMAL(18,4),
    market_amt DECIMAL(18,2),
    biz_date VARCHAR(16),
    sync_flag INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS broker_fund_account (
    id BIGINT PRIMARY KEY,
    client_full_name VARCHAR(128),
    fund_account_no VARCHAR(64),
    current_balance DECIMAL(18,2),
    frozen_capital DECIMAL(18,2),
    total_asset DECIMAL(18,2),
    biz_date VARCHAR(16),
    sync_flag INT DEFAULT 0
);
