show status like 'Threads_connected';

SHOW FULL PROCESSLIST;
SELECT * FROM information_schema.processlist;
# 查询最大连接数
SHOW VARIABLES LIKE 'max_connections';



# =============================================================  分析 sql
EXPLAIN ANALYZE
SELECT
    request_uri,
    COUNT(*) as error_count,
    AVG(request_time) as avg_time
FROM nginx_access_log
WHERE status = 500
  AND create_time >= DATE_SUB(NOW(), INTERVAL 7 DAY)
  AND is_deleted = 0
GROUP BY request_uri
ORDER BY avg_time DESC
LIMIT 10;

# -> Limit: 10 row(s)  (actual time=0.16..0.16 rows=0 loops=1)
#     -> Sort: avg_time DESC, limit input to 10 row(s) per chunk  (actual time=0.159..0.159 rows=0 loops=1)
#         -> Table scan on <temporary>  (actual time=0.134..0.134 rows=0 loops=1)
#             -> Aggregate using temporary table  (actual time=0.132..0.132 rows=0 loops=1)
#                 -> Filter: ((nginx_access_log.is_deleted = 0) and (nginx_access_log.create_time >= <cache>((now() - interval 7 day))))  (cost=0.26 rows=0.1) (actual time=0.0654..0.0654 rows=0 loops=1)
#                     -> Index lookup on nginx_access_log using idx_status (status=500)  (cost=0.26 rows=1) (actual time=0.0639..0.0639 rows=0 loops=1)
#
#  Index lookup (索引查找)
# 动作：数据库首先找到了 idx_status 这个索引，并直接定位到 status=500 的数据节点。
# 解读：这是整个查询的起点。优化器认为通过状态码查找是最快的入口。
# Filter (过滤)
# 动作：拿着上一步找到的数据，逐行检查是否同时满足 is_deleted = 0（未删除）和 create_time >= 7天前 这两个条件。
# 解读：这一步是在索引查找的基础上做“二次筛选”。
# Aggregate using temporary table (临时表聚合)
# 动作：因为你的 SQL 中有 GROUP BY request_uri 以及 COUNT()、AVG() 等聚合函数，数据库需要把上一步筛选出来的结果集，放到一个内存或磁盘的临时表中进行分组和计算。
# Table scan on (扫描临时表)
# 动作：聚合计算完成后，数据库对这个生成的临时表进行了一次全表扫描，准备提取最终需要的字段。
# 解读：这里的 <temporary> 指的就是上一步生成的那个中间结果集。
# Sort (排序)
# 动作：根据你的 ORDER BY avg_time DESC 要求，对临时表中的数据按照平均耗时进行降序排列。
# Limit (截取)
# 动作：最后，根据你的 LIMIT 10 要求，只保留排序后的前 10 条数据返回给你，其余的全部丢弃。
# 解读：这是整个流水线的最后一环。


-- 创建测试表
DROP TABLE IF EXISTS t;
CREATE TABLE t (
                   id INT PRIMARY KEY,
                   name VARCHAR(100),
                   created_at DATETIME DEFAULT NOW(),
                   INDEX idx_name (name)
) ENGINE=InnoDB;

-- 插入测试数据
INSERT INTO t (id, name) VALUES
                             (1, 'Alice'), (2, 'Bob'), (3, 'Charlie'), (4, 'David');

-- 查看版本
SELECT VERSION();


# ============================================================================
# mysql8 如何 开启 `performance_schema` 或使用 `SHOW PROFILE` (在 5.7 测试)，执行 `SELECT * FROM t WHERE id=1`，将分析结果输出为 JSON 格式，识别出“连接”“解析”“优化”“执行”各阶段耗时。
