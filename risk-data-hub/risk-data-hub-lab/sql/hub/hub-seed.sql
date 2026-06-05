-- ==========================================
-- Hub 中台库种子数据
-- 全部使用 INSERT IGNORE，可重复执行
-- ==========================================

-- Leaf 发号器初始值（dict_item 的 max_id 预留 10 给 seed 数据中硬编码的 ID 2-7）
INSERT IGNORE INTO leaf_alloc (biz_tag, max_id, step, description) VALUES
('sync_task',             1,      10, 'sync_task主键'),
('sync_business_record',  1,      10, 'sync_business_record主键'),
('dict_item',           10,     20, 'dict_item主键'),
('oms_stock_snapshot',  1,      20, 'oms_stock_snapshot主键'),
('oms_trade_order',     1,      20, 'oms_trade_order主键'),
('oms_position_holding', 1,     20, 'oms_position_holding主键'),
('oms_cash_asset',      1,      20, 'oms_cash_asset主键'),
('broker_stock_quote',  1,      20, 'broker_stock_quote主键'),
('broker_trade_deal',   1,      20, 'broker_trade_deal主键'),
('broker_position_balance', 1,  20, 'broker_position_balance主键'),
('broker_fund_account', 1,      20, 'broker_fund_account主键'),
('clean_stock',         50000,  20000, 'clean_stock主键'),
('clean_trade',         100000, 20000, 'clean_trade主键'),
('clean_position',      200000, 20000, 'clean_position主键'),
('clean_asset',         300000, 20000, 'clean_asset主键'),
('event_message',       500000, 20, 'event_message主键'),
('sync_batch_metrics',  1,      10, 'sync_batch_metrics主键'),
('tx_audit',            900000, 10, 'tx_audit主键');

-- 字典初始值
INSERT IGNORE INTO dict_item (id, dict_type, dict_code, dict_name, dict_desc) VALUES
(2, 'trade_status_oms',    'NEW',    '待确认', 'OMS待确认'),
(3, 'trade_status_oms',    'DONE',   '已成交', 'OMS成交完成'),
(4, 'trade_status_oms',    'CANCEL', '已撤单', 'OMS撤单状态'),
(5, 'trade_status_broker', 'A',      '待确认', 'Broker待确认'),
(6, 'trade_status_broker', 'S',      '已成交', 'Broker成交完成'),
(7, 'trade_status_broker', 'X',      '已撤单', 'Broker撤单状态');
