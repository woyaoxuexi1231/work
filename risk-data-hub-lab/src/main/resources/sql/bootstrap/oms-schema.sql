-- ==========================================
-- OMS 交易库 DDL
-- ==========================================

CREATE TABLE IF NOT EXISTS oms_stock_snapshot (
    id BIGINT PRIMARY KEY,
    symbol VARCHAR(16) NOT NULL,
    exchange_code VARCHAR(32),
    market_day VARCHAR(16) NOT NULL,
    open_price DECIMAL(18,4),
    high_price DECIMAL(18,4),
    low_price DECIMAL(18,4),
    close_price DECIMAL(18,4),
    volume_qty BIGINT,
    turnover_amount DECIMAL(18,2),
    sync_flag INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS oms_trade_order (
    id BIGINT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL,
    stock_code VARCHAR(32) NOT NULL,
    investor_name VARCHAR(128),
    side_code VARCHAR(8),
    trade_qty BIGINT,
    trade_price DECIMAL(18,4),
    order_amount DECIMAL(18,2),
    trade_status VARCHAR(32),
    trade_time VARCHAR(32),
    sync_flag INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS oms_position_holding (
    id BIGINT PRIMARY KEY,
    investor_name VARCHAR(128),
    stock_code VARCHAR(32) NOT NULL,
    holding_qty BIGINT,
    available_qty BIGINT,
    cost_price DECIMAL(18,4),
    market_value DECIMAL(18,2),
    stat_day VARCHAR(16),
    sync_flag INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS oms_cash_asset (
    id BIGINT PRIMARY KEY,
    investor_name VARCHAR(128),
    account_no VARCHAR(64),
    cash_balance DECIMAL(18,2),
    frozen_balance DECIMAL(18,2),
    total_asset DECIMAL(18,2),
    stat_day VARCHAR(16),
    sync_flag INT DEFAULT 0
);
