create database if not exists trade_oms default character set utf8mb4;
create database if not exists trade_broker default character set utf8mb4;
create database if not exists risk_hub default character set utf8mb4;

use trade_oms;

create table if not exists oms_trade_order (
    id bigint not null auto_increment primary key,
    order_no varchar(64) not null,
    investor_name varchar(64) not null,
    side_code varchar(8) not null,
    order_amount decimal(18,2) not null,
    trade_status varchar(32) not null,
    trade_time varchar(32) not null,
    sync_flag int default 0 not null
);

insert into oms_trade_order(order_no, investor_name, side_code, order_amount, trade_status, trade_time, sync_flag) values
('OMS-1001', '张三', 'B', 20000.00, 'NEW', '2026-05-20 09:00:00', 0),
('OMS-1002', '李四', 'S', 35000.00, 'DONE', '2026-05-20 09:05:00', 0),
('OMS-1003', '王五', 'B', 18000.00, 'CANCEL', '2026-05-20 09:10:00', 0);

use trade_broker;

create table if not exists broker_trade_deal (
    id bigint not null auto_increment primary key,
    deal_code varchar(64) not null,
    client_full_name varchar(64) not null,
    bs_flag varchar(8) not null,
    turnover_amount decimal(18,2) not null,
    status_mark varchar(32) not null,
    deal_at varchar(32) not null,
    sync_flag int default 0 not null
);

insert into broker_trade_deal(deal_code, client_full_name, bs_flag, turnover_amount, status_mark, deal_at, sync_flag) values
('BRK-2001', '华泰证券资产户', '1', 1280.00, 'A', '2026-05-20 09:15:00', 0),
('BRK-2002', '中信证券资管户', '2', 2260.00, 'S', '2026-05-20 09:18:00', 0),
('BRK-2003', '国泰君安机构户', '1', 980.00, 'X', '2026-05-20 09:20:00', 0);

use risk_hub;

create table if not exists dict_item (
    id bigint not null auto_increment primary key,
    dict_type varchar(64) not null,
    dict_code varchar(64) not null,
    dict_name varchar(128) not null,
    dict_desc varchar(256),
    unique key uk_dict_type_code(dict_type, dict_code)
);

create table if not exists leaf_alloc (
    biz_tag varchar(64) primary key,
    max_id bigint not null,
    step int not null,
    description varchar(256)
);

create table if not exists clean_trade (
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

create table if not exists event_message (
    message_id bigint primary key,
    topic varchar(64) not null,
    biz_key varchar(128) not null,
    payload text not null,
    status varchar(32) not null,
    created_at varchar(32) not null
);

create table if not exists tx_coordination_log (
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
