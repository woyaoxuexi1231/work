drop database if exists trade_oms;
drop database if exists trade_broker;
drop database if exists risk_hub;

create database trade_oms default character set utf8mb4;
create database trade_broker default character set utf8mb4;
create database risk_hub default character set utf8mb4;

use trade_oms;

create table oms_stock_snapshot (
    id bigint not null auto_increment primary key,
    symbol varchar(16) not null,
    exchange_code varchar(32),
    market_day varchar(16) not null,
    open_price decimal(18,4) not null,
    high_price decimal(18,4) not null,
    low_price decimal(18,4) not null,
    close_price decimal(18,4) not null,
    volume_qty bigint not null,
    turnover_amount decimal(18,2) not null,
    sync_flag int default 0 not null,
    unique key uk_oms_symbol_day(symbol, market_day)
);

create table oms_trade_order (
    id bigint not null auto_increment primary key,
    order_no varchar(64) not null,
    stock_code varchar(16),
    investor_name varchar(64) not null,
    side_code varchar(8) not null,
    trade_qty bigint default 0 not null,
    trade_price decimal(18,4) default 0 not null,
    order_amount decimal(18,2) not null,
    trade_status varchar(32) not null,
    trade_time varchar(32) not null,
    sync_flag int default 0 not null
);

create table oms_position_holding (
    id bigint not null auto_increment primary key,
    investor_name varchar(64) not null,
    stock_code varchar(16) not null,
    holding_qty bigint not null,
    available_qty bigint not null,
    cost_price decimal(18,4) not null,
    market_value decimal(18,2) not null,
    stat_day varchar(16) not null
);

create table oms_cash_asset (
    id bigint not null auto_increment primary key,
    investor_name varchar(64) not null,
    account_no varchar(64) not null,
    cash_balance decimal(18,2) not null,
    frozen_balance decimal(18,2) not null,
    total_asset decimal(18,2) not null,
    stat_day varchar(16) not null
);

insert into oms_stock_snapshot(symbol, exchange_code, market_day, open_price, high_price, low_price, close_price, volume_qty, turnover_amount, sync_flag) values
('AAPL', 'XNAS', '2026-05-16', 210.1200, 214.7800, 208.3100, 213.6700, 58234000, 12442760780.00, 0),
('MSFT', 'XNAS', '2026-05-16', 435.2000, 439.6500, 432.1000, 438.2400, 21456000, 9402867840.00, 0),
('NVDA', 'XNAS', '2026-05-16', 122.1000, 126.5400, 121.8000, 125.8300, 98321000, 12370911430.00, 0);

insert into oms_trade_order(order_no, stock_code, investor_name, side_code, trade_qty, trade_price, order_amount, trade_status, trade_time, sync_flag) values
('OMS-AAPL-00001-1', 'AAPL', '量化一号', 'B', 1200, 213.6700, 256404.00, 'NEW', '2026-05-16 09:30:00', 0),
('OMS-MSFT-00002-2', 'MSFT', '量化二号', 'S', 800, 438.2400, 350592.00, 'DONE', '2026-05-16 10:00:00', 0),
('OMS-NVDA-00003-3', 'NVDA', '高频策略', 'B', 2000, 125.8300, 251660.00, 'CANCEL', '2026-05-16 10:30:00', 0);

insert into oms_position_holding(investor_name, stock_code, holding_qty, available_qty, cost_price, market_value, stat_day) values
('量化一号', 'AAPL', 5200, 5000, 209.8800, 1111084.00, '2026-05-16'),
('量化二号', 'MSFT', 3100, 2900, 431.5500, 1358544.00, '2026-05-16');

insert into oms_cash_asset(investor_name, account_no, cash_balance, frozen_balance, total_asset, stat_day) values
('量化一号', 'OMS-ACCT-0001', 4200000.00, 180000.00, 7560000.00, '2026-05-16'),
('量化二号', 'OMS-ACCT-0002', 3150000.00, 120000.00, 5480000.00, '2026-05-16');

use trade_broker;

create table broker_stock_quote (
    id bigint not null auto_increment primary key,
    quote_code varchar(64) not null,
    secu_code varchar(16) not null,
    trade_day varchar(16) not null,
    exchange_name varchar(32),
    open_px decimal(18,4) not null,
    high_px decimal(18,4) not null,
    low_px decimal(18,4) not null,
    close_px decimal(18,4) not null,
    vol_num bigint not null,
    turnover_amt decimal(18,2) not null,
    sync_flag int default 0 not null,
    unique key uk_broker_quote(quote_code)
);

create table broker_trade_deal (
    id bigint not null auto_increment primary key,
    deal_code varchar(64) not null,
    secu_code varchar(16),
    client_full_name varchar(64) not null,
    bs_flag varchar(8) not null,
    deal_volume bigint default 0 not null,
    deal_price decimal(18,4) default 0 not null,
    turnover_amount decimal(18,2) not null,
    status_mark varchar(32) not null,
    deal_at varchar(32) not null,
    sync_flag int default 0 not null
);

create table broker_position_balance (
    id bigint not null auto_increment primary key,
    client_full_name varchar(64) not null,
    secu_code varchar(16) not null,
    current_volume bigint not null,
    enable_volume bigint not null,
    cost_px decimal(18,4) not null,
    market_amt decimal(18,2) not null,
    biz_date varchar(16) not null
);

create table broker_fund_account (
    id bigint not null auto_increment primary key,
    client_full_name varchar(64) not null,
    fund_account_no varchar(64) not null,
    current_balance decimal(18,2) not null,
    frozen_capital decimal(18,2) not null,
    total_asset decimal(18,2) not null,
    biz_date varchar(16) not null
);

insert into broker_stock_quote(quote_code, secu_code, trade_day, exchange_name, open_px, high_px, low_px, close_px, vol_num, turnover_amt, sync_flag) values
('AAPL-2026-05-16', 'AAPL', '2026-05-16', 'XNAS', 210.1200, 214.7800, 208.3100, 213.6700, 58234000, 12442760780.00, 0),
('MSFT-2026-05-16', 'MSFT', '2026-05-16', 'XNAS', 435.2000, 439.6500, 432.1000, 438.2400, 21456000, 9402867840.00, 0),
('NVDA-2026-05-16', 'NVDA', '2026-05-16', 'XNAS', 122.1000, 126.5400, 121.8000, 125.8300, 98321000, 12370911430.00, 0);

insert into broker_trade_deal(deal_code, secu_code, client_full_name, bs_flag, deal_volume, deal_price, turnover_amount, status_mark, deal_at, sync_flag) values
('BRK-AAPL-00001-1', 'AAPL', '华泰资管账户', '1', 1800, 213.6900, 384642.00, 'A', '2026-05-16 09:32:00', 0),
('BRK-MSFT-00002-2', 'MSFT', '中信机构账户', '2', 900, 438.2700, 394443.00, 'S', '2026-05-16 10:05:00', 0),
('BRK-NVDA-00003-3', 'NVDA', '招商自营账户', '1', 2600, 125.8900, 327314.00, 'X', '2026-05-16 10:40:00', 0);

insert into broker_position_balance(client_full_name, secu_code, current_volume, enable_volume, cost_px, market_amt, biz_date) values
('华泰资管账户', 'AAPL', 6500, 6300, 210.1500, 1388855.00, '2026-05-16'),
('中信机构账户', 'MSFT', 2800, 2600, 432.8800, 1227072.00, '2026-05-16');

insert into broker_fund_account(client_full_name, fund_account_no, current_balance, frozen_capital, total_asset, biz_date) values
('华泰资管账户', 'FUND-0001', 4680000.00, 220000.00, 7820000.00, '2026-05-16'),
('中信机构账户', 'FUND-0002', 3520000.00, 160000.00, 6010000.00, '2026-05-16');

use risk_hub;

create table dict_item (
    id bigint not null auto_increment primary key,
    dict_type varchar(64) not null,
    dict_code varchar(64) not null,
    dict_name varchar(128) not null,
    dict_desc varchar(256),
    unique key uk_dict_type_code(dict_type, dict_code)
);

create table leaf_alloc (
    biz_tag varchar(64) primary key,
    max_id bigint not null,
    step int not null,
    description varchar(256)
);

create table clean_trade (
    global_id bigint primary key,
    source_system varchar(64) not null,
    source_type varchar(32) not null,
    source_row_id bigint not null,
    vendor_trade_no varchar(64) not null,
    biz_type varchar(64) not null,
    direction varchar(32) not null,
    amount decimal(18,2) not null,
    status_name varchar(64) not null,
    counterparty_name varchar(128),
    clean_mode varchar(32) not null,
    clean_batch varchar(64) not null,
    trade_time varchar(32) not null,
    created_at varchar(32) not null
);

create table event_message (
    message_id bigint primary key,
    topic varchar(64) not null,
    biz_key varchar(128) not null,
    payload text not null,
    status varchar(32) not null,
    created_at varchar(32) not null
);

create table tx_coordination_log (
    id bigint primary key,
    source_system varchar(64) not null,
    phase varchar(32) not null,
    detail varchar(256) not null,
    created_at varchar(32) not null
);

insert into dict_item(dict_type, dict_code, dict_name, dict_desc) values
('trade_status_oms', 'NEW', '待确认', '交易系统A待确认状态'),
('trade_status_oms', 'DONE', '已成交', '交易系统A成交完成'),
('trade_status_oms', 'CANCEL', '已撤单', '交易系统A撤单状态'),
('trade_status_broker', 'A', '待确认', '交易系统B待确认状态'),
('trade_status_broker', 'S', '已成交', '交易系统B成交完成'),
('trade_status_broker', 'X', '已撤单', '交易系统B撤单状态');

insert into leaf_alloc(biz_tag, max_id, step, description) values
('clean_trade', 100000, 20, '中台标准交易主键'),
('event_message', 500000, 20, '同步事件主键'),
('tx_audit', 900000, 10, '事务审计主键');
