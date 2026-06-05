-- 创建用户 readonly_broker，仅对 trade_broker 库有只读权限
CREATE USER IF NOT EXISTS 'readonly_broker'@'%' IDENTIFIED BY '123456';
GRANT SELECT ON trade_broker.* TO 'readonly_broker'@'%';

-- 创建用户 readonly_oms，仅对 trade_oms 库有只读权限
CREATE USER IF NOT EXISTS 'readonly_oms'@'%' IDENTIFIED BY '123456';
GRANT SELECT ON trade_oms.* TO 'readonly_oms'@'%';

FLUSH PRIVILEGES;