# MySQL 查询从 100ms 突增到 5 秒 —— 实战排查手册

> 本指南需要你有一台本地 MySQL（建议 8.0+），逐步复现问题并亲手排查。

---

## 一、环境准备：建表与造数据

```sql
-- 1. 创建测试库
CREATE DATABASE IF NOT EXISTS perf_test;
USE perf_test;

-- 2. 订单主表（100 万行）
DROP TABLE IF EXISTS orders;
CREATE TABLE orders (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    order_no    VARCHAR(32) NOT NULL,
    status      TINYINT NOT NULL DEFAULT 0 COMMENT '0待支付 1已支付 2已取消 3已退款',
    amount      DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at),
    INDEX idx_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. 订单明细表（500 万行，模拟大表）
DROP TABLE IF EXISTS order_items;
CREATE TABLE order_items (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id    BIGINT NOT NULL,
    product_id  BIGINT NOT NULL,
    quantity    INT NOT NULL DEFAULT 1,
    price       DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order_id (order_id),
    INDEX idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 造数据存储过程

```sql
DELIMITER $$
DROP PROCEDURE IF EXISTS generate_data$$
CREATE PROCEDURE generate_data(IN total_orders INT)
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE batch_size INT DEFAULT 1000;
    DECLARE start_id BIGINT;
    DECLARE end_id BIGINT;

    SET autocommit = 0;

    -- 造 orders
    WHILE i <= total_orders DO
        SET start_id = i;
        SET end_id = i + batch_size - 1;

        INSERT INTO orders (user_id, order_no, status, amount, created_at)
        SELECT
            FLOOR(1 + RAND() * 100000),
            CONCAT('ORD', LPAD(seq, 12, '0')),
            FLOOR(RAND() * 4),
            ROUND(RAND() * 10000, 2),
            DATE_ADD('2025-01-01', INTERVAL FLOOR(RAND() * 500) DAY)
        FROM (
            SELECT (start_id + n.num) AS seq
            FROM (
                SELECT a.N + b.N*10 + c.N*100 AS num
                FROM (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
                      UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a,
                     (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
                      UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b,
                     (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
                      UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) c
            ) n
            WHERE (start_id + n.num) <= end_id
        ) t;

        COMMIT;
        SET i = i + batch_size;
    END WHILE;

    -- 造 order_items（每个订单 2~8 个明细）
    SET i = 1;
    WHILE i <= total_orders DO
        INSERT INTO order_items (order_id, product_id, quantity, price, created_at)
        SELECT
            o.id,
            FLOOR(1 + RAND() * 5000),
            FLOOR(1 + RAND() * 10),
            ROUND(RAND() * 500, 2),
            o.created_at
        FROM orders o
        WHERE o.id BETWEEN i AND i + batch_size - 1;

        -- 随机再插入 1~7 条
        INSERT INTO order_items (order_id, product_id, quantity, price, created_at)
        SELECT
            o.id,
            FLOOR(1 + RAND() * 5000),
            FLOOR(1 + RAND() * 10),
            ROUND(RAND() * 500, 2),
            o.created_at
        FROM orders o
        JOIN (
            SELECT 1 AS n UNION SELECT 2 UNION SELECT 3
             UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7
        ) multi
        WHERE o.id BETWEEN i AND i + batch_size - 1
          AND RAND() > 0.5;

        COMMIT;
        SET i = i + batch_size;
    END WHILE;

    SET autocommit = 1;
END$$
DELIMITER ;

-- 执行造数据（100 万订单 + ~500 万明细，约需 3~5 分钟）
CALL generate_data(1000000);
```

### 更新统计信息（基线状态）

```sql
ANALYZE TABLE orders;
ANALYZE TABLE order_items;

-- 确认数据量
SELECT COUNT(*) FROM orders;       -- 应为 1000000
SELECT COUNT(*) FROM order_items;  -- 约 4000000~5000000
```

---

## 二、基线查询：记录正常时的性能

### 靶子 SQL（就是这条查询从 100ms → 5秒）

```sql
-- 查某用户最近 30 天的待支付订单及明细
SELECT
    o.id,
    o.order_no,
    o.amount,
    o.created_at,
    oi.product_id,
    oi.quantity,
    oi.price
FROM orders o
JOIN order_items oi ON o.id = oi.order_id
WHERE o.user_id = 12345
  AND o.status = 0
  AND o.created_at > DATE_SUB(NOW(), INTERVAL 30 DAY)
ORDER BY o.created_at DESC;
```

### 建立基线

```sql
-- 1. 看执行计划（记录作为基线对照）
EXPLAIN
SELECT o.id, o.order_no, o.amount, o.created_at, oi.product_id, oi.quantity, oi.price
FROM orders o
JOIN order_items oi ON o.id = oi.order_id
WHERE o.user_id = 12345
  AND o.status = 0
  AND o.created_at > DATE_SUB(NOW(), INTERVAL 30 DAY)
ORDER BY o.created_at DESC;
```

**把此时的输出截图保存**。关注：`key`（用到哪个索引）、`rows`（扫描行数）、`Extra`。

```sql
-- 2. 测量基线响应时间
SET profiling = 1;

SELECT o.id, o.order_no, o.amount, o.created_at, oi.product_id, oi.quantity, oi.price
FROM orders o
JOIN order_items oi ON o.id = oi.order_id
WHERE o.user_id = 12345
  AND o.status = 0
  AND o.created_at > DATE_SUB(NOW(), INTERVAL 30 DAY)
ORDER BY o.created_at DESC;

SHOW PROFILES;                    -- 记录 Query_ID
SHOW PROFILE FOR QUERY <Query_ID>;
```

---

## 三、故障场景 1：统计信息过期 → 执行计划突变

> 🎯 **这是最常见的原因**。表频繁增删改后统计信息不准，优化器"误判"选了全表扫描。

### 第 1 步：模拟统计信息过期

```sql
-- 人为让统计信息"变脏"：大量插入但不更新统计信息
INSERT INTO orders (user_id, order_no, status, amount, created_at)
SELECT
    FLOOR(1 + RAND() * 100000),
    CONCAT('ORD', LPAD(1000000 + seq, 12, '0')),
    0,
    ROUND(RAND() * 10000, 2),
    NOW()
FROM (
    SELECT a.N + b.N*10 + c.N*100 + d.N*1000 AS seq
    FROM (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a,
         (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b,
         (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) c,
         (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) d
    LIMIT 100000
) t;

-- 同时大量 DELETE（让页碎片变多）
DELETE FROM orders WHERE id BETWEEN 1000 AND 20000;
-- 重启或等一段时间让它慢慢变"脏"
```

### 第 2 步：观察执行计划变化

```sql
-- 再次看执行计划
EXPLAIN FORMAT=JSON
SELECT o.id, o.order_no, o.amount, o.created_at, oi.product_id, oi.quantity, oi.price
FROM orders o
JOIN order_items oi ON o.id = oi.order_id
WHERE o.user_id = 12345
  AND o.status = 0
  AND o.created_at > DATE_SUB(NOW(), INTERVAL 30 DAY)
ORDER BY o.created_at DESC\G
```

**对比重点**：
| 关注项 | 正常 | 异常 |
|--------|------|------|
| `access_type` | `ref` / `range` | `ALL`（全表扫描） |
| `rows_examined_per_scan` | 几百~几千 | 几十万~上百万 |
| `query_cost` | 小 | 大 |
| `used_key_parts` | 有值 | 空或不同索引 |

```sql
-- 查看优化器代价值
SHOW STATUS LIKE 'Last_query_cost';
```

### 第 3 步：立即修复

```sql
-- 方案 A：更新统计信息（首选）
ANALYZE TABLE orders;
ANALYZE TABLE order_items;

-- 再次执行，观察时间是否恢复
```

```sql
-- 方案 B：紧急情况下强制走索引
SELECT o.id, o.order_no, o.amount, o.created_at, oi.product_id, oi.quantity, oi.price
FROM orders o FORCE INDEX (idx_user_id)   -- ← 强制指定索引
JOIN order_items oi ON o.id = oi.order_id
WHERE o.user_id = 12345
  AND o.status = 0
  AND o.created_at > DATE_SUB(NOW(), INTERVAL 30 DAY)
ORDER BY o.created_at DESC;
```

### 第 4 步：记录排查过程

```sql
-- 查看统计信息详情（MySQL 8.0）
SELECT
    TABLE_NAME,
    INDEX_NAME,
    STAT_NAME,
    STAT_VALUE,
    STAT_DESCRIPTION
FROM mysql.innodb_index_stats
WHERE table_name IN ('orders', 'order_items')
  AND database_name = 'perf_test';
```

---

## 四、故障场景 2：锁阻塞 → 等锁等了 5 秒

> 🎯 查询本身不慢，但被其他会话的锁卡住了。

### 第 1 步：模拟锁阻塞

打开 **两个 MySQL 会话窗口**：

**Session A（制造锁）**：
```sql
USE perf_test;
SET autocommit = 0;

-- 开启一个事务，修改 user_id=12345 的行但不提交
START TRANSACTION;
UPDATE orders SET amount = amount + 1 WHERE user_id = 12345 AND status = 0;
-- ← 不要 COMMIT！保持事务打开
```

**Session B（受害者查询）**：
```sql
USE perf_test;

-- 执行靶子查询，会被阻塞
SELECT o.id, o.order_no, o.amount, o.created_at, oi.product_id, oi.quantity, oi.price
FROM orders o
JOIN order_items oi ON o.id = oi.order_id
WHERE o.user_id = 12345
  AND o.status = 0
  AND o.created_at > DATE_SUB(NOW(), INTERVAL 30 DAY)
ORDER BY o.created_at DESC;

-- 观察：明显卡住，超过 5 秒才返回或直接超时
```

### 第 2 步：排查锁等待——站在"受害者"角度

```sql
-- ⭐ 方法 1（MySQL 8.0 推荐）：查看当前锁等待
SELECT
    r.trx_id                AS waiting_trx_id,
    r.trx_mysql_thread_id   AS waiting_thread,
    r.trx_query             AS waiting_query,
    b.trx_id                AS blocking_trx_id,
    b.trx_mysql_thread_id   AS blocking_thread,
    b.trx_query             AS blocking_query,
    TIMESTAMPDIFF(SECOND, r.trx_wait_started, NOW()) AS wait_seconds
FROM       information_schema.innodb_lock_waits w
INNER JOIN information_schema.innodb_trx r ON w.requesting_trx_id = r.trx_id
INNER JOIN information_schema.innodb_trx b ON w.blocking_trx_id  = b.trx_id\G

-- ⭐ 方法 2（MySQL 8.0 sys schema，更直观）
SELECT * FROM sys.innodb_lock_waits\G

-- ⭐ 方法 3：查看所有活跃事务
SELECT
    trx_id,
    trx_state,
    trx_started,
    TIMESTAMPDIFF(SECOND, trx_started, NOW()) AS running_seconds,
    trx_mysql_thread_id,
    trx_query,
    trx_rows_locked,
    trx_rows_modified
FROM information_schema.innodb_trx
ORDER BY trx_started;
```

```sql
-- ⭐ 方法 4：查看 InnoDB 整体状态
SHOW ENGINE INNODB STATUS\G
-- 重点看 LATEST DETECTED DEADLOCK 和 TRANSACTIONS 两段
```

### 第 3 步：终止阻塞源

```sql
-- 先找到阻塞线程 ID（从上面查到 blocking_thread）
-- 然后用 KILL 终结它
KILL <blocking_thread_id>;

-- 回到 Session B 观察查询是否立即返回
```

### 第 4 步：检查 MDL 锁（元数据锁）

```sql
-- 模拟 DDL 造成的 MDL 阻塞
-- Session A：执行一个慢 DDL（不提交）
-- ALTER TABLE orders ADD COLUMN tmp_col INT;  -- 会卡住

-- 排查 MDL 等待
SELECT * FROM sys.schema_table_lock_waits\G

-- 或直接查 performance_schema（MySQL 8.0）
SELECT
    waiting_pid,
    waiting_query,
    blocking_pid,
    blocking_query,
    wait_age
FROM sys.schema_table_lock_waits
WHERE waiting_query NOT LIKE '%schema_table_lock_waits%'\G
```

---

## 五、故障场景 3：Buffer Pool 命中率骤降

> 🎯 数据被大量换出，查询突然变成大量物理读（磁盘 I/O）。

### 第 1 步：读取基线 Buffer Pool 指标

```sql
-- 记录当前状态（作为对比基线）
SELECT
    (SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME='Innodb_buffer_pool_read_requests') AS read_requests,
    (SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME='Innodb_buffer_pool_reads')        AS physical_reads,
    ROUND(
        (1 - (SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME='Innodb_buffer_pool_reads')
           / (SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME='Innodb_buffer_pool_read_requests')
        ) * 100, 2
    ) AS hit_rate_pct;

-- 期望 hit_rate_pct > 99%
```

### 第 2 步：模拟 Buffer Pool 被"污染"

```sql
-- 执行一个大范围扫描，把热数据挤出 Buffer Pool
SELECT COUNT(*), SUM(amount)
FROM orders
WHERE created_at > '2024-01-01';  -- 全表扫描 100 万行

SELECT COUNT(*)
FROM order_items
WHERE price > 10;  -- 走全表或大范围扫描
```

### 第 3 步：立即执行靶子查询，观察变慢

```sql
SET profiling = 1;

SELECT o.id, o.order_no, o.amount, o.created_at, oi.product_id, oi.quantity, oi.price
FROM orders o
JOIN order_items oi ON o.id = oi.order_id
WHERE o.user_id = 12345
  AND o.status = 0
  AND o.created_at > DATE_SUB(NOW(), INTERVAL 30 DAY)
ORDER BY o.created_at DESC;

SHOW PROFILE FOR QUERY <Query_ID>;
-- 看 Sending data 阶段的时间是否暴增
```

### 第 4 步：再次检查命中率

```sql
-- 执行与第 1 步相同的查询，对比 hit_rate_pct
SELECT
    ROUND(
        (1 - (SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME='Innodb_buffer_pool_reads')
           / (SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME='Innodb_buffer_pool_read_requests')
        ) * 100, 2
    ) AS hit_rate_pct;

-- 正常应回升，说明物理读只是暂时的
```

```sql
-- 进阶：看 Buffer Pool 使用详情
SELECT
    POOL_ID,
    POOL_SIZE,
    FREE_BUFFERS,
    DATABASE_PAGES,
    PAGES_MADE_YOUNG,
    PAGES_NOT_MADE_YOUNG
FROM information_schema.innodb_buffer_pool_stats\G
```

---

## 六、故障场景 4：碎片过多 / 索引被删

### 第 1 步：模拟索引被误删

```sql
-- ⚠️ 危险操作：模拟有人误删了索引
SHOW INDEX FROM orders;  -- 先看一眼有哪些索引

DROP INDEX idx_user_id ON orders;   -- 删掉 user_id 的索引
```

### 第 2 步：靶子查询立刻变慢

```sql
EXPLAIN
SELECT o.id, o.order_no, o.amount, o.created_at, oi.product_id, oi.quantity, oi.price
FROM orders o
JOIN order_items oi ON o.id = oi.order_id
WHERE o.user_id = 12345
  AND o.status = 0
  AND o.created_at > DATE_SUB(NOW(), INTERVAL 30 DAY)
ORDER BY o.created_at DESC;

-- type=ALL, rows≈100万 → 全表扫描
```

### 第 3 步：索引恢复

```sql
ALTER TABLE orders ADD INDEX idx_user_id (user_id);
ANALYZE TABLE orders;
-- 再次查询，时间恢复
```

### 第 4 步：检查表碎片

```sql
-- 查看碎片率
SELECT
    TABLE_SCHEMA,
    TABLE_NAME,
    ROUND(DATA_LENGTH / 1024 / 1024, 2) AS data_mb,
    ROUND(DATA_FREE / 1024 / 1024, 2)   AS free_mb,
    ROUND(DATA_FREE / DATA_LENGTH * 100, 2) AS fragmentation_pct
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'perf_test'
  AND DATA_LENGTH > 0
ORDER BY fragmentation_pct DESC;

-- 如果碎片率高（>30%），执行优化
OPTIMIZE TABLE orders;   -- 会重建表，回收空间
```

---

## 七、综合排查脚本（一键诊断）

把以下脚本保存为 `diagnose.sql`，问题发生时直接跑：

```sql
-- ========== MySQL 查询变慢一键诊断脚本 ==========

-- [1] 当前正在执行的所有查询
SELECT id, user, host, db, command, time, state, info
FROM information_schema.processlist
WHERE command != 'Sleep'
ORDER BY time DESC;

-- [2] 锁等待（MySQL 8.0）
SELECT * FROM sys.innodb_lock_waits\G

-- [3] 最近执行时间 > 1 秒的语句
SELECT
    DIGEST_TEXT,
    COUNT_STAR,
    AVG_TIMER_WAIT / 1000000000 AS avg_seconds,
    MAX_TIMER_WAIT / 1000000000 AS max_seconds,
    SUM_ROWS_EXAMINED,
    SUM_ROWS_SENT,
    SUM_CREATED_TMP_TABLES,
    SUM_CREATED_TMP_DISK_TABLES
FROM performance_schema.events_statements_summary_by_digest
WHERE AVG_TIMER_WAIT > 1000000000000  -- >1 秒
ORDER BY AVG_TIMER_WAIT DESC
LIMIT 10;

-- [4] 活跃事务（运行超过 30 秒）
SELECT
    trx_id,
    trx_state,
    TIMESTAMPDIFF(SECOND, trx_started, NOW()) AS sec_running,
    trx_mysql_thread_id,
    trx_query,
    trx_rows_locked,
    trx_rows_modified
FROM information_schema.innodb_trx
WHERE TIMESTAMPDIFF(SECOND, trx_started, NOW()) > 30;

-- [5] Buffer Pool 命中率
SELECT
    ROUND(
        (1 - v_reads.v / v_requests.v) * 100, 2
    ) AS bp_hit_rate_pct
FROM
    (SELECT VARIABLE_VALUE AS v FROM performance_schema.global_status WHERE VARIABLE_NAME='Innodb_buffer_pool_reads') v_reads,
    (SELECT VARIABLE_VALUE AS v FROM performance_schema.global_status WHERE VARIABLE_NAME='Innodb_buffer_pool_read_requests') v_requests;

-- [6] 表碎片
SELECT
    TABLE_NAME,
    ROUND(DATA_LENGTH / 1024 / 1024, 2) AS data_mb,
    ROUND(DATA_FREE / 1024 / 1024, 2)   AS free_mb,
    ROUND(DATA_FREE / DATA_LENGTH * 100, 2) AS fragment_pct
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'perf_test'
  AND DATA_LENGTH > 0;

-- [7] 最近错误日志（需要 FILE 权限）
-- SHOW GLOBAL VARIABLES LIKE 'log_error';
-- 然后去文件系统 tail 那个日志文件
```

---

## 八、排查清单（现场使用）

当查询突然变慢，按以下顺序**逐项打勾**：

| 步骤 | 检查项 | 执行的命令 | ✅/❌ |
|------|--------|------------|-------|
| 1 | SQL 是否被改过？ | 对比应用日志/代码 diff | |
| 2 | 执行计划是否变了？ | `EXPLAIN FORMAT=JSON` | |
| 3 | 统计信息是否过期？ | 对比 `innodb_index_stats` | |
| 4 | 有锁阻塞吗？ | `sys.innodb_lock_waits` | |
| 5 | Buffer Pool 命中率骤降？ | 命中率公式 | |
| 6 | 有大量磁盘 I/O？ | `iostat -x 1` / 系统监控 | |
| 7 | 索引被误删？ | `SHOW INDEX FROM <table>` | |
| 8 | 表碎片过高？ | `information_schema.TABLES` | |
| 9 | 有慢 DDL 在执行？ | `sys.schema_table_lock_waits` | |
| 10 | 参数被在线调小？ | 检查 `innodb_buffer_pool_size` | |

### 修复速查

| 原因 | 修复 |
|------|------|
| 统计信息过期 | `ANALYZE TABLE` |
| 执行计划选错 | `FORCE INDEX` / SQL Plan Baseline |
| 锁阻塞 | `KILL <blocking_thread>` |
| Buffer Pool 污染 | 调整 `innodb_buffer_pool_size`，重启后预热 |
| 索引缺失 | 加索引 + `ANALYZE TABLE` |
| 碎片化 | `OPTIMIZE TABLE`（会锁表，注意时间窗口） |

---

## 九、预防措施

```sql
-- 1. 开启自动统计信息更新（innodb_stats_auto_recalc 默认已开启）
ALTER TABLE orders STATS_AUTO_RECALC = 1;

-- 2. 增大统计采样页数（让统计信息更准）
ALTER TABLE orders STATS_SAMPLE_PAGES = 100;

-- 3. 开启慢查询日志
SET GLOBAL slow_query_log = ON;
SET GLOBAL long_query_time = 0.1;    -- 100ms 就算慢
SET GLOBAL log_queries_not_using_indexes = ON;

-- 4. 确认 performance_schema 开启（MySQL 8.0 默认开）
SHOW VARIABLES LIKE 'performance_schema';
```

---

> **练习建议**：每个故障场景独立操作一遍，感受从"发现变慢"到"定位根因"再到"修复验证"的完整链路。建议每做完一个场景就恢复环境（重建表/加回索引/提交事务），避免场景之间互相干扰。
