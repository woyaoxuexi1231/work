> 请将以下完整内容保存为 “MySQL_45天渐进编码指南.md”

# MySQL 45天渐进编码面试冲刺指南

## 总纲

### 45天知识图谱

| 天数 | 阶段 | 知识点 | 覆盖原理项 | 有性能题 |
|---|---|---|---|---|
| 1 | 使用期 | SELECT + WHERE 基础过滤 | 无 | 否 |
| 2 | 使用期 | ORDER BY + LIMIT 排序分页 | 无 | 否 |
| 3 | 使用期 | 聚合函数 + GROUP BY | 无 | 否 |
| 4 | 使用期 | HAVING 与 WHERE | 无 | 否 |
| 5 | 使用期 | IN/BETWEEN/LIKE/IS NULL | 无 | 否 |
| 6 | 使用期 | INNER JOIN 两表关联 | 无 | 否 |
| 7 | 使用期 | LEFT/RIGHT JOIN 与 NULL | 无 | 否 |
| 8 | 使用期 | 多表 JOIN 与别名 | 无 | 否 |
| 9 | 使用期 | 标量子查询与列子查询 | 无 | 否 |
| 10 | 使用期 | 相关子查询与非相关子查询 | 无 | 否 |
| 11 | 使用期 | UNION / UNION ALL | 无 | 否 |
| 12 | 使用期 | ROW_NUMBER / RANK / DENSE_RANK | 无 | 否 |
| 13 | 使用期 | LAG / LEAD / 窗口帧 | 无 | 否 |
| 14 | 使用期 | 日期、字符串、数值函数 | 无 | 否 |
| 15 | 使用期 | 销售大屏综合报表 | 无 | 否 |
| 16 | 原理期 | 索引基础、B+树、EXPLAIN | P1 | 是 |
| 17 | 原理期 | 覆盖索引、最左前缀 | P2,P3 | 是 |
| 18 | 原理期 | 索引下推、索引合并 | P4,P5 | 是 |
| 19 | 原理期 | 慢查询日志、优化器追踪 | P6,P7 | 是 |
| 20 | 原理期 | 事务隔离级别 | P8 | 是 |
| 21 | 原理期 | MVCC 与 Read View | P9 | 是 |
| 22 | 原理期 | 全局锁、表锁、行锁、间隙锁、临键锁 | P10 | 是 |
| 23 | 原理期 | 死锁检测与死锁日志 | P11 | 是 |
| 24 | 原理期 | binlog、redo log、undo log | P12,P13 | 是 |
| 25 | 原理期 | 两阶段提交与崩溃恢复 | P14,P15 | 是 |
| 26 | 原理期 | 主从复制与延迟分析 | P16,P17 | 是 |
| 27 | 原理期 | 读写分离、分库分表 | P18,P19 | 是 |
| 28 | 原理期 | SELECT 完整执行流程 | P20 | 是 |
| 29 | 原理期 | UPDATE 完整执行流程 | P21 | 是 |
| 30 | 原理期 | 慢查询优化全流程 | P22 | 是 |
| 31 | 大厂期 | 千万级深分页优化 | P23 | 是 |
| 32 | 大厂期 | 热点行更新与秒杀库存 | P24 | 是 |
| 33 | 大厂期 | 海量归档与分区表 | P25 | 是 |
| 34 | 大厂期 | Canal + MQ 异构同步 | P26 | 是 |
| 35 | 大厂期 | XA/TCC 分布式事务 | P27 | 是 |
| 36 | 大厂期 | 备库重建与一致性校验 | P28 | 是 |
| 37 | 大厂期 | 多机房主从延迟治理 | P29 | 是 |
| 38 | 大厂期 | 云原生数据库架构差异 | P30 | 是 |
| 39 | 大厂期 | 轻量级 ORM 设计 | P31 | 否 |
| 40 | 大厂期 | 全链路压测影子库 | P32 | 是 |
| 41 | 大厂期 | 慢查询智能诊断系统 | P33 | 是 |
| 42 | 大厂期 | 数据脱敏与加密存储 | P34 | 是 |
| 43 | 大厂期 | 不停机迁移、双写、回滚 | P35 | 是 |
| 44 | 大厂期 | 云数据库选型对比 | P36 | 否 |
| 45 | 大厂期 | 短 URL 服务数据库设计 | P37 | 是 |

### 面试必考原理总清单

1. P1: B+树索引结构与页分裂（第16天）
2. P2: 覆盖索引与回表（第17天）
3. P3: 联合索引最左前缀原则（第17天）
4. P4: 索引下推 ICP（第18天）
5. P5: Index Merge 索引合并（第18天）
6. P6: 慢查询日志字段含义（第19天）
7. P7: optimizer_trace 优化器决策（第19天）
8. P8: 事务隔离级别与脏读、不可重复读、幻读（第20天）
9. P9: MVCC、undo 版本链、Read View（第21天）
10. P10: 行锁、间隙锁、临键锁与锁范围（第22天）
11. P11: 死锁等待图与死锁检测（第23天）
12. P12: redo log WAL 机制（第24天）
13. P13: undo log 回滚与一致性读（第24天）
14. P14: binlog 与 redo log 两阶段提交（第25天）
15. P15: crash recovery 恢复流程（第25天）
16. P16: 主从复制链路、binlog dump、relay log（第26天）
17. P17: 复制延迟、半同步复制（第26天）
18. P18: 读写分离一致性策略（第27天）
19. P19: 分库分表路由算法（第27天）
20. P20: SELECT 从连接器到存储引擎的执行路径（第28天）
21. P21: UPDATE 的锁、undo、redo、binlog 协作（第29天）
22. P22: 索引设计与 SQL 改写黄金法则（第30天）
23. P23: 深分页延迟关联与书签法（第31天）
24. P24: 热点行排队、拆行、异步扣减（第32天）
25. P25: RANGE/HASH/LIST 分区与归档策略（第33天）
26. P26: binlog CDC、幂等消费、位点恢复（第34天）
27. P27: XA 二阶段提交与 TCC 补偿（第35天）
28. P28: 逻辑备份、物理备份、一致性校验（第36天）
29. P29: 多机房复制拓扑与读延迟兜底（第37天）
30. P30: 存储计算分离、日志即数据库、共享存储（第38天）
31. P31: SQL 解析、占位符绑定、连接池（第39天）
32. P32: 影子库路由与压测流量隔离（第40天）
33. P33: 慢查询指纹化与自动诊断规则（第41天）
34. P34: 字段加密、哈希检索、函数索引（第42天）
35. P35: 双写一致性、灰度迁移、回滚水位（第43天）
36. P36: RDS、PolarDB、TDSQL、Aurora 架构差异（第44天）
37. P37: 短码唯一性、冷热分层、读写路径优化（第45天）

> 说明：第1-15天只训练 MySQL 使用能力，不安排 EXPLAIN、源码、压测、索引原理等题目。下面每一天的 SQL 都可复制执行；如表已存在，请先在自己的练习库中执行 `DROP TABLE IF EXISTS ...`。

## 第1天：SELECT + WHERE 基础过滤

本日掌握：最基本单表查询、数值过滤、字符串过滤、NULL 判断；覆盖原理点：无；阶段：使用期

### 🟢 基础用法题

#### 题目1：查询北京高积分会员

**场景描述**：电商会员运营想找出北京且积分超过 500 的用户，推送高价值用户专属券。

**编码要求**：创建会员表，插入测试数据，用 `WHERE`、`AND` 完成过滤。

```sql
DROP TABLE IF EXISTS d1_members;
CREATE TABLE d1_members (
  member_id INT PRIMARY KEY,
  name VARCHAR(50),
  city VARCHAR(20),
  birth_date DATE,
  points INT
);

INSERT INTO d1_members VALUES
(1,'张三','北京','1990-05-20',800),
(2,'李四','上海','1988-12-15',300),
(3,'王五','北京','1995-07-01',600),
(4,'赵六','广州','1992-03-30',1200),
(5,'孙七','北京','1985-11-22',450);

SELECT member_id, name, city, points
FROM d1_members
WHERE city = '北京' AND points > 500;
```

**运行结果示例**：

```text
member_id | name | city | points
1         | 张三 | 北京 | 800
3         | 王五 | 北京 | 600
```

**🔍 反思**：`WHERE` 先帮你缩小行范围，再决定哪些列被投影出来；第1天只需要观察结果是否符合业务条件。

**💬 追问**：如果 `points` 为 `NULL`，`points > 500` 会返回 true、false，还是 unknown？

### 🟡 中级用法题

#### 题目2：组合条件筛选待跟进会员

**场景描述**：客服要找出“1990年后出生且积分小于1000”的年轻用户，或者城市缺失的用户。

**编码要求**：使用 `AND`、`OR`、括号、`IS NULL`。

```sql
INSERT INTO d1_members VALUES
(6,'周八',NULL,'1993-08-08',200),
(7,'吴九','北京','2000-01-01',NULL);

SELECT member_id, name, city, birth_date, points
FROM d1_members
WHERE (birth_date > '1990-01-01' AND points < 1000)
   OR city IS NULL;
```

**关键中间状态**：

```text
birth_date > 1990 且 points < 1000: 张三、王五
city IS NULL: 周八
points 为 NULL 的吴九不会命中 points < 1000
```

**🔍 反思**：括号能让业务意图直接落在 SQL 上，避免 `AND`、`OR` 混用时读错。

**💬 追问**：为什么 `city = NULL` 查不到城市为空的记录？

### 🔴 高级/探索用法题

#### 题目3：模糊匹配与特殊字符转义

**场景描述**：旧系统同步过来的用户昵称包含 `_`、`%`，运营要找出昵称里真实包含下划线的用户。

**编码要求**：使用 `LIKE`、`ESCAPE`，同时展示不转义时的差异。

```sql
INSERT INTO d1_members VALUES
(8,'F_name','深圳','1998-01-01',100),
(9,'A_B','成都','1999-06-06',300),
(10,'Percent%User','杭州','1997-07-07',700);

SELECT name AS raw_like
FROM d1_members
WHERE name LIKE '%_%';

SELECT name AS escaped_like
FROM d1_members
WHERE name LIKE '%!_%' ESCAPE '!';

SELECT name AS percent_user
FROM d1_members
WHERE name LIKE '%!%%' ESCAPE '!';
```

**逐步结果示例**：

```text
raw_like: 几乎所有非空 name 都匹配，因为 _ 是任意单字符
escaped_like: F_name, A_B
percent_user: Percent%User
```

**🔍 反思**：`LIKE` 能表达业务上的“包含、前缀、后缀”，但通配符本身是语法的一部分。

**💬 追问**：如果旧版本或规范不允许 `ESCAPE '!'`，如何查真实的 `_` 和 `%`？

### 🏢 大厂面试场景实战（使用层面）

**场景**：导入表 `d1_user_import` 中手机号可能为空、含空格、含短横线。请找出纯数字 11 位手机号。

```sql
DROP TABLE IF EXISTS d1_user_import;
CREATE TABLE d1_user_import (id INT PRIMARY KEY, phone VARCHAR(30));
INSERT INTO d1_user_import VALUES
(1,'13800138000'),(2,'138-0013-8000'),(3,' 13800138000'),
(4,''),(5,NULL),(6,'abc13800138');

SELECT id, phone
FROM d1_user_import
WHERE phone IS NOT NULL
  AND phone <> ''
  AND phone REGEXP '^[0-9]{11}$';
```

**结果示例**：只返回 `13800138000`。

**🔍 反思**：清洗类 SQL 要先明确“有效”的业务定义。

**💬 追问**：如果手机号前后可能有空格，你会选择查询时 `TRIM(phone)`，还是入库前清洗？

### 🎯 今日高频面试题速览

1. `WHERE` 中 `AND` 与 `OR` 的优先级是什么？
2. `IS NULL` 和 `= NULL` 有什么区别？
3. `LIKE '%abc%'`、`LIKE 'abc%'` 的匹配范围有什么区别？
4. 如何查找包含真实 `_` 字符的记录？
5. 字符串比较是否区分大小写由什么决定？

## 第2天：ORDER BY + LIMIT 排序与分页

本日掌握：单列排序、多列排序、分页写法、稳定排序；覆盖原理点：无；阶段：使用期

### 🟢 基础用法题

#### 题目1：会员积分 TOP 3

**场景描述**：积分商城首页要展示积分最高的前三名会员。

**编码要求**：创建数据，用 `ORDER BY ... DESC LIMIT` 查询。

```sql
DROP TABLE IF EXISTS d2_members;
CREATE TABLE d2_members (
  member_id INT PRIMARY KEY,
  name VARCHAR(50),
  points INT,
  birth_date DATE
);
INSERT INTO d2_members VALUES
(1,'张三',800,'1990-05-20'),
(2,'李四',300,'1988-12-15'),
(3,'王五',600,'1995-07-01'),
(4,'赵六',1200,'1992-03-30'),
(5,'孙七',450,'1985-11-22'),
(6,'钱十',600,'1996-01-01');

SELECT name, points
FROM d2_members
ORDER BY points DESC
LIMIT 3;
```

**运行结果示例**：

```text
赵六 1200
张三 800
王五 600
```

**🔍 反思**：排序方向默认是 `ASC`，积分榜通常要显式写 `DESC`，可读性更好。

**💬 追问**：`LIMIT 3`、`LIMIT 0,3`、`LIMIT 3 OFFSET 0` 是否等价？

### 🟡 中级用法题

#### 题目2：会员列表稳定分页

**场景描述**：后台列表每页 2 条，按积分升序；积分相同按生日倒序；仍相同按主键升序，避免翻页顺序漂移。

```sql
SELECT member_id, name, points, birth_date
FROM d2_members
ORDER BY points ASC, birth_date DESC, member_id ASC
LIMIT 2 OFFSET 2;
```

**关键中间状态**：

```text
排序键: points ASC -> birth_date DESC -> member_id ASC
第1页: points 最低的2人
第2页: OFFSET 2 后继续取2人
```

**🔍 反思**：分页最好有确定性排序键，否则相同分数的行可能在不同页之间跳动。

**💬 追问**：百万行时，`LIMIT 100000,20` 直觉上为什么可能变慢？

### 🔴 高级/探索用法题

#### 题目3：旧版本 MySQL 用用户变量生成行号

**场景描述**：公司还有 MySQL 5.7，只能用用户变量给积分榜加行号。

```sql
SET @rn := 0;

SELECT rn, member_id, name, points
FROM (
  SELECT
    (@rn := @rn + 1) AS rn,
    member_id,
    name,
    points
  FROM d2_members
  ORDER BY points DESC, member_id ASC
) ranked
WHERE rn BETWEEN 3 AND 5;
```

**逐步结果示例**：

```text
排序后第1行 rn=1
排序后第2行 rn=2
外层保留 rn=3 到 rn=5
```

**🔍 反思**：用户变量能解决旧版本行号问题，但可读性和稳定性不如窗口函数。

**💬 追问**：如果 MySQL 8.0 可用，应该用哪个窗口函数替代？

### 🏢 大厂面试场景实战（使用层面）

**场景**：订单后台按创建时间倒序展示第 100 页，每页 20 条。

```sql
DROP TABLE IF EXISTS d2_orders;
CREATE TABLE d2_orders (
  order_id BIGINT PRIMARY KEY,
  buyer_id BIGINT,
  amount DECIMAL(10,2),
  created_at DATETIME
);

SELECT order_id, buyer_id, amount, created_at
FROM d2_orders
WHERE created_at >= '2026-01-01'
ORDER BY created_at DESC, order_id DESC
LIMIT 20 OFFSET 1980;
```

**🔍 反思**：第2天先把语法写对，后面第31天再系统优化深分页。

**💬 追问**：为什么排序里补 `order_id DESC` 能减少重复或漏数据概率？

### 🎯 今日高频面试题速览

1. `ORDER BY a, b DESC` 的排序优先级是什么？
2. `LIMIT m,n` 中 `m` 和 `n` 分别代表什么？
3. 如何写第 5 页、每页 20 条的分页 SQL？
4. 为什么分页查询要有稳定排序键？
5. 旧版本 MySQL 如何生成查询行号？

## 第3天：聚合函数 + GROUP BY

本日掌握：COUNT、SUM、AVG、MAX、MIN 与分组统计；覆盖原理点：无；阶段：使用期

### 🟢 基础用法题

#### 题目1：统计每个城市会员数

**场景描述**：运营想知道每个城市有多少会员。

```sql
DROP TABLE IF EXISTS d3_members;
CREATE TABLE d3_members (
  member_id INT PRIMARY KEY,
  name VARCHAR(30),
  city VARCHAR(20),
  points INT
);
INSERT INTO d3_members VALUES
(1,'张三','北京',800),(2,'李四','上海',300),(3,'王五','北京',600),
(4,'赵六','广州',1200),(5,'孙七','上海',450),(6,'周八',NULL,200);

SELECT city, COUNT(*) AS member_count
FROM d3_members
GROUP BY city;
```

**运行结果示例**：

```text
NULL 1
上海 2
北京 2
广州 1
```

**🔍 反思**：`GROUP BY city` 会把 `NULL` 城市也归成一组。

**💬 追问**：`COUNT(*)`、`COUNT(city)`、`COUNT(1)` 的结果可能有什么差异？

### 🟡 中级用法题

#### 题目2：城市积分画像

**场景描述**：市场部要看每个城市的总积分、平均积分、最高积分、最低积分。

```sql
SELECT
  city,
  COUNT(*) AS user_cnt,
  SUM(points) AS total_points,
  ROUND(AVG(points), 2) AS avg_points,
  MAX(points) AS max_points,
  MIN(points) AS min_points
FROM d3_members
GROUP BY city
ORDER BY total_points DESC;
```

**关键中间状态**：

```text
北京: 800 + 600 = 1400
上海: 300 + 450 = 750
```

**🔍 反思**：聚合函数会把一组多行压缩成一行，适合报表统计。

**💬 追问**：如果 `points` 为 `NULL`，`SUM` 和 `AVG` 如何处理？

### 🔴 高级/探索用法题

#### 题目3：按订单状态统计金额并做条件聚合

**场景描述**：财务需要一张按商户汇总的订单表，同时拆出已支付金额和退款金额。

```sql
DROP TABLE IF EXISTS d3_orders;
CREATE TABLE d3_orders (
  order_id INT PRIMARY KEY,
  merchant_id INT,
  status VARCHAR(20),
  amount DECIMAL(10,2)
);
INSERT INTO d3_orders VALUES
(1,101,'PAID',100.00),(2,101,'REFUND',30.00),(3,101,'PAID',200.00),
(4,102,'PAID',80.00),(5,102,'CANCEL',10.00),(6,103,'REFUND',50.00);

SELECT
  merchant_id,
  COUNT(*) AS order_cnt,
  SUM(CASE WHEN status = 'PAID' THEN amount ELSE 0 END) AS paid_amount,
  SUM(CASE WHEN status = 'REFUND' THEN amount ELSE 0 END) AS refund_amount,
  SUM(CASE WHEN status = 'PAID' THEN 1 ELSE 0 END) AS paid_cnt
FROM d3_orders
GROUP BY merchant_id
ORDER BY merchant_id;
```

**逐步结果示例**：

```text
merchant 101: paid_amount=300.00, refund_amount=30.00
merchant 102: paid_amount=80.00, refund_amount=0.00
merchant 103: paid_amount=0.00, refund_amount=50.00
```

**🔍 反思**：条件聚合能把多次查询合并成一次分组统计。

**💬 追问**：旧版本没有窗口函数时，条件聚合能替代哪些排名前的预处理统计？

### 🏢 大厂面试场景实战（使用层面）

**场景**：广告平台要统计每个广告主最近一天的曝光、点击、点击率。

```sql
DROP TABLE IF EXISTS d3_ad_events;
CREATE TABLE d3_ad_events (
  event_id INT PRIMARY KEY,
  advertiser_id INT,
  event_type VARCHAR(20),
  event_time DATETIME
);
INSERT INTO d3_ad_events VALUES
(1,1,'IMPRESSION','2026-05-09 10:00:00'),
(2,1,'CLICK','2026-05-09 10:01:00'),
(3,1,'IMPRESSION','2026-05-09 10:02:00'),
(4,2,'IMPRESSION','2026-05-09 11:00:00');

SELECT
  advertiser_id,
  SUM(event_type = 'IMPRESSION') AS impressions,
  SUM(event_type = 'CLICK') AS clicks,
  ROUND(SUM(event_type = 'CLICK') / NULLIF(SUM(event_type = 'IMPRESSION'), 0), 4) AS ctr
FROM d3_ad_events
WHERE event_time >= '2026-05-09' AND event_time < '2026-05-10'
GROUP BY advertiser_id;
```

**🔍 反思**：`NULLIF` 可以避免除以 0。

**💬 追问**：如果某广告主没有曝光但有异常点击，CTR 应该展示 `NULL`、0，还是单独标记异常？

### 🎯 今日高频面试题速览

1. `COUNT(*)` 和 `COUNT(column)` 的区别是什么？
2. `GROUP BY` 后 select 中能直接写非分组列吗？
3. 如何做条件聚合？
4. `AVG` 会不会统计 `NULL`？
5. 如何计算点击率并避免除以 0？

## 第4天：HAVING 与 WHERE 的区别

本日掌握：分组前过滤、分组后过滤、聚合条件筛选；覆盖原理点：无；阶段：使用期

### 🟢 基础用法题

#### 题目1：筛出订单数超过2的商户

**场景描述**：平台招商要找出至少有 3 笔订单的活跃商户。

```sql
DROP TABLE IF EXISTS d4_orders;
CREATE TABLE d4_orders (
  order_id INT PRIMARY KEY,
  merchant_id INT,
  status VARCHAR(20),
  amount DECIMAL(10,2)
);
INSERT INTO d4_orders VALUES
(1,101,'PAID',100),(2,101,'PAID',200),(3,101,'CANCEL',20),
(4,102,'PAID',80),(5,102,'REFUND',30),(6,103,'PAID',300);

SELECT merchant_id, COUNT(*) AS order_cnt
FROM d4_orders
GROUP BY merchant_id
HAVING COUNT(*) >= 3;
```

**运行结果示例**：`101 | 3`

**🔍 反思**：`WHERE` 不能直接写 `COUNT(*) >= 3`，因为它发生在分组之前。

**💬 追问**：`HAVING order_cnt >= 3` 在 MySQL 中可以吗？

### 🟡 中级用法题

#### 题目2：先过滤已支付，再筛高价值商户

**场景描述**：财务要找“已支付订单总额超过 250”的商户。

```sql
SELECT
  merchant_id,
  COUNT(*) AS paid_cnt,
  SUM(amount) AS paid_amount
FROM d4_orders
WHERE status = 'PAID'
GROUP BY merchant_id
HAVING SUM(amount) > 250
ORDER BY paid_amount DESC;
```

**关键中间状态**：

```text
WHERE 后只保留 PAID
GROUP BY 后按 merchant 聚合
HAVING 后留下 paid_amount > 250 的商户
```

**🔍 反思**：能在 `WHERE` 过滤的行级条件，应优先写在 `WHERE`；聚合结果条件写 `HAVING`。

**💬 追问**：百万行时，把 `status='PAID'` 写在 `HAVING` 里会有什么直觉风险？

### 🔴 高级/探索用法题

#### 题目3：多指标门槛筛选异常商户

**场景描述**：风控要找退款率超过 30% 且支付金额超过 100 的商户。

```sql
SELECT
  merchant_id,
  SUM(status = 'PAID') AS paid_cnt,
  SUM(status = 'REFUND') AS refund_cnt,
  SUM(CASE WHEN status = 'PAID' THEN amount ELSE 0 END) AS paid_amount,
  ROUND(SUM(status = 'REFUND') / NULLIF(COUNT(*), 0), 4) AS refund_rate
FROM d4_orders
GROUP BY merchant_id
HAVING refund_rate > 0.30 AND paid_amount > 100;
```

**逐步结果示例**：

```text
merchant 102: refund_cnt=1, total=2, refund_rate=0.5000, paid_amount=80，不满足 paid_amount > 100
```

**🔍 反思**：`HAVING` 适合表达“聚合之后才知道”的业务规则。

**💬 追问**：如果旧版本不允许在 `HAVING` 中引用别名，怎么改写？

### 🏢 大厂面试场景实战（使用层面）

**场景**：内容平台要找最近 7 天发布超过 3 篇文章，且平均阅读量超过 1000 的作者。

```sql
DROP TABLE IF EXISTS d4_articles;
CREATE TABLE d4_articles (
  article_id INT PRIMARY KEY,
  author_id INT,
  view_cnt INT,
  published_at DATETIME
);
INSERT INTO d4_articles VALUES
(1,10,1200,'2026-05-01'),(2,10,1400,'2026-05-02'),
(3,10,900,'2026-05-03'),(4,10,1600,'2026-05-04'),
(5,11,2000,'2026-05-02');

SELECT author_id, COUNT(*) AS article_cnt, ROUND(AVG(view_cnt),2) AS avg_views
FROM d4_articles
WHERE published_at >= '2026-05-01' AND published_at < '2026-05-08'
GROUP BY author_id
HAVING COUNT(*) > 3 AND AVG(view_cnt) > 1000;
```

**🔍 反思**：日期范围是行条件，文章数和平均阅读量是组条件。

**💬 追问**：如果要展示所有作者，包括不达标作者，该用 `HAVING` 还是额外加标记列？

### 🎯 今日高频面试题速览

1. `WHERE` 和 `HAVING` 的核心区别是什么？
2. 为什么 `WHERE COUNT(*) > 1` 不合法？
3. `HAVING` 能不能使用聚合别名？
4. 行级过滤应写在哪里？
5. 如何筛选平均分大于 90 的班级？

## 第5天：IN/BETWEEN/LIKE/IS NULL

本日掌握：列表匹配、范围匹配、模糊匹配、空值处理；覆盖原理点：无；阶段：使用期

### 🟢 基础用法题

#### 题目1：按状态筛选订单

**场景描述**：客服只关心待支付和已支付订单。

```sql
DROP TABLE IF EXISTS d5_orders;
CREATE TABLE d5_orders (
  order_id INT PRIMARY KEY,
  buyer_id INT,
  status VARCHAR(20),
  amount DECIMAL(10,2),
  created_at DATETIME,
  remark VARCHAR(100)
);
INSERT INTO d5_orders VALUES
(1,1,'WAIT_PAY',100,'2026-05-01 10:00:00','new'),
(2,1,'PAID',200,'2026-05-01 11:00:00',NULL),
(3,2,'CANCEL',50,'2026-05-02 12:00:00','cancel by user'),
(4,3,'REFUND',80,'2026-05-03 13:00:00','refund'),
(5,4,'PAID',300,'2026-05-04 14:00:00','vip');

SELECT order_id, status, amount
FROM d5_orders
WHERE status IN ('WAIT_PAY','PAID');
```

**运行结果示例**：订单 1、2、5。

**🔍 反思**：`IN` 比多个 `OR` 更像业务枚举。

**💬 追问**：`status IN (...)` 中列表包含 `NULL` 会怎样？

### 🟡 中级用法题

#### 题目2：找出指定金额区间且备注不为空的订单

```sql
SELECT order_id, buyer_id, amount, remark
FROM d5_orders
WHERE amount BETWEEN 80 AND 250
  AND remark IS NOT NULL
  AND remark <> '';
```

**关键中间状态**：

```text
BETWEEN 80 AND 250 包含边界 80 和 250
remark IS NOT NULL 排除 NULL
remark <> '' 排除空字符串
```

**🔍 反思**：空值和空字符串不是一回事。

**💬 追问**：`BETWEEN '2026-05-01' AND '2026-05-02'` 查询 DATETIME 时有什么边界风险？

### 🔴 高级/探索用法题

#### 题目3：多条件搜索订单后台

**场景描述**：后台支持按买家列表、金额范围、备注关键词组合搜索。

```sql
SET @buyer_ids := '1,3';
SET @keyword := 'vip';

SELECT order_id, buyer_id, status, amount, remark
FROM d5_orders
WHERE buyer_id IN (1,3)
  AND amount BETWEEN 50 AND 500
  AND (remark LIKE CONCAT('%', @keyword, '%') OR @keyword IS NULL);
```

**逐步结果示例**：

```text
buyer_id 先限制为 1 或 3
amount 再限制 50 到 500
keyword=vip 时只保留备注含 vip 的订单
```

**🔍 反思**：搜索 SQL 要把每个筛选项都写成可独立检查的条件。

**💬 追问**：如果旧系统不支持正则，如何做更复杂的关键词匹配？

### 🏢 大厂面试场景实战（使用层面）

**场景**：风控要找“金额在 1000 到 5000、备注包含 refund 或 dispute、状态不是 CANCEL”的订单。

```sql
SELECT order_id, buyer_id, status, amount, remark
FROM d5_orders
WHERE amount BETWEEN 1000 AND 5000
  AND status NOT IN ('CANCEL')
  AND (remark LIKE '%refund%' OR remark LIKE '%dispute%');
```

**🔍 反思**：范围、枚举、模糊匹配可组合成可读的风控规则。

**💬 追问**：如果 `status` 为 `NULL`，`status NOT IN ('CANCEL')` 会命中吗？

### 🎯 今日高频面试题速览

1. `BETWEEN` 是否包含左右边界？
2. `IN` 和多个 `OR` 在表达上有什么区别？
3. `NOT IN` 遇到 `NULL` 有什么坑？
4. `LIKE '%abc'` 和 `LIKE 'abc%'` 分别表示什么？
5. `IS NULL` 和空字符串如何同时排除？

## 第6天：INNER JOIN 两表关联

本日掌握：内连接、关联条件、表别名、字段消歧；覆盖原理点：无；阶段：使用期

### 🟢 基础用法题

#### 题目1：查询订单及买家姓名

**场景描述**：订单列表要展示买家姓名。

```sql
DROP TABLE IF EXISTS d6_users;
DROP TABLE IF EXISTS d6_orders;
CREATE TABLE d6_users (user_id INT PRIMARY KEY, name VARCHAR(30));
CREATE TABLE d6_orders (order_id INT PRIMARY KEY, user_id INT, amount DECIMAL(10,2));
INSERT INTO d6_users VALUES (1,'张三'),(2,'李四'),(3,'王五');
INSERT INTO d6_orders VALUES (101,1,100),(102,1,200),(103,2,50),(104,9,999);

SELECT o.order_id, u.name, o.amount
FROM d6_orders o
INNER JOIN d6_users u ON o.user_id = u.user_id;
```

**运行结果示例**：订单 101、102、103；订单 104 因无用户匹配不返回。

**🔍 反思**：内连接只保留两边都匹配成功的行。

**💬 追问**：如果漏写 `ON` 条件会发生什么结果？

### 🟡 中级用法题

#### 题目2：筛选指定用户的已支付订单明细

```sql
ALTER TABLE d6_orders ADD COLUMN status VARCHAR(20) DEFAULT 'PAID';
UPDATE d6_orders SET status='CANCEL' WHERE order_id=103;

SELECT u.user_id, u.name, o.order_id, o.amount
FROM d6_users u
JOIN d6_orders o ON u.user_id = o.user_id
WHERE u.name IN ('张三','李四') AND o.status = 'PAID'
ORDER BY u.user_id, o.order_id;
```

**关键中间状态**：

```text
JOIN 建立用户-订单行
WHERE 再筛用户与订单状态
```

**🔍 反思**：连接条件写 `ON`，业务过滤写 `WHERE`，更便于阅读。

**💬 追问**：百万行时，先筛用户再关联和先关联再筛选，直觉上哪个更省？

### 🔴 高级/探索用法题

#### 题目3：自连接找同城用户组合

**场景描述**：社交产品要推荐同城用户相互认识，但不能自己和自己配对，也不能 A-B、B-A 重复。

```sql
ALTER TABLE d6_users ADD COLUMN city VARCHAR(20);
UPDATE d6_users SET city='北京' WHERE user_id IN (1,3);
UPDATE d6_users SET city='上海' WHERE user_id=2;
INSERT INTO d6_users VALUES (4,'赵六','北京');

SELECT
  u1.user_id AS user_a,
  u1.name AS name_a,
  u2.user_id AS user_b,
  u2.name AS name_b,
  u1.city
FROM d6_users u1
JOIN d6_users u2
  ON u1.city = u2.city
 AND u1.user_id < u2.user_id
ORDER BY u1.city, user_a, user_b;
```

**逐步结果示例**：

```text
北京: 张三-王五, 张三-赵六, 王五-赵六
上海: 只有李四，不能组成组合
```

**🔍 反思**：自连接能把同一张表当作两个角色来匹配。

**💬 追问**：如果旧系统不允许复杂自连接，你会把推荐关系放到临时表还是应用层生成？

### 🏢 大厂面试场景实战（使用层面）

**场景**：找出每个已支付订单的用户姓名和订单金额，只返回金额大于 100 的记录。

```sql
SELECT o.order_id, u.name, o.amount
FROM d6_orders o
JOIN d6_users u ON o.user_id = u.user_id
WHERE o.status = 'PAID' AND o.amount > 100
ORDER BY o.amount DESC;
```

**🔍 反思**：业务字段来自不同表时，JOIN 是最自然的组合方式。

**💬 追问**：如果有订单但用户被删除，内连接还能返回订单吗？

### 🎯 今日高频面试题速览

1. `INNER JOIN` 只返回什么数据？
2. `JOIN ... ON` 和 `WHERE` 分别适合写什么条件？
3. 自连接适合解决什么问题？
4. 多表字段同名时如何消歧？
5. 漏写连接条件会产生什么结果？

## 第7天：LEFT/RIGHT JOIN 与 NULL 处理

本日掌握：外连接、保留左表/右表、未匹配 NULL、反向查缺；覆盖原理点：无；阶段：使用期

### 🟢 基础用法题

#### 题目1：查询所有用户及其订单

**场景描述**：用户运营要看所有用户是否下过单，没有订单也要显示。

```sql
DROP TABLE IF EXISTS d7_users;
DROP TABLE IF EXISTS d7_orders;
CREATE TABLE d7_users (user_id INT PRIMARY KEY, name VARCHAR(30));
CREATE TABLE d7_orders (order_id INT PRIMARY KEY, user_id INT, amount DECIMAL(10,2));
INSERT INTO d7_users VALUES (1,'张三'),(2,'李四'),(3,'王五');
INSERT INTO d7_orders VALUES (101,1,100),(102,1,200),(103,2,50);

SELECT u.user_id, u.name, o.order_id, o.amount
FROM d7_users u
LEFT JOIN d7_orders o ON u.user_id = o.user_id
ORDER BY u.user_id, o.order_id;
```

**运行结果示例**：王五也返回，订单字段为 `NULL`。

**🔍 反思**：`LEFT JOIN` 的“左”决定了哪些行必须被保留。

**💬 追问**：`RIGHT JOIN` 能不能改写成 `LEFT JOIN`？

### 🟡 中级用法题

#### 题目2：找出从未下单的用户

```sql
SELECT u.user_id, u.name
FROM d7_users u
LEFT JOIN d7_orders o ON u.user_id = o.user_id
WHERE o.order_id IS NULL;
```

**关键中间状态**：

```text
LEFT JOIN 先保留所有用户
没有匹配订单的用户，o.order_id 为 NULL
WHERE o.order_id IS NULL 得到未下单用户
```

**🔍 反思**：外连接 + 右表主键为空，是常见“查缺”写法。

**💬 追问**：如果把 `o.amount > 100` 写在 `WHERE`，会不会把无订单用户过滤掉？

### 🔴 高级/探索用法题

#### 题目3：统计每个用户订单金额，未下单显示0

```sql
SELECT
  u.user_id,
  u.name,
  COUNT(o.order_id) AS order_cnt,
  COALESCE(SUM(o.amount), 0) AS total_amount
FROM d7_users u
LEFT JOIN d7_orders o ON u.user_id = o.user_id
GROUP BY u.user_id, u.name
ORDER BY total_amount DESC;
```

**逐步结果示例**：

```text
张三 total=300
李四 total=50
王五 total=0
```

**🔍 反思**：外连接后聚合时，`COUNT(o.order_id)` 比 `COUNT(*)` 更符合“订单数”语义。

**💬 追问**：旧版本没有 `COALESCE` 时，MySQL 还有哪个函数能替代？

### 🏢 大厂面试场景实战（使用层面）

**场景**：增长团队要找“注册后 7 天内没有下单”的用户。

```sql
ALTER TABLE d7_users ADD COLUMN registered_at DATETIME DEFAULT '2026-05-01 00:00:00';
ALTER TABLE d7_orders ADD COLUMN created_at DATETIME DEFAULT '2026-05-03 00:00:00';

SELECT u.user_id, u.name
FROM d7_users u
LEFT JOIN d7_orders o
  ON u.user_id = o.user_id
 AND o.created_at >= u.registered_at
 AND o.created_at < DATE_ADD(u.registered_at, INTERVAL 7 DAY)
WHERE o.order_id IS NULL;
```

**🔍 反思**：外连接的匹配条件放在 `ON` 里，才能保留“没有匹配订单”的用户。

**💬 追问**：如果把时间条件放到 `WHERE`，结果会怎样变化？

### 🎯 今日高频面试题速览

1. `LEFT JOIN` 与 `INNER JOIN` 的结果差异是什么？
2. 如何找出 A 表有、B 表没有的数据？
3. 外连接后 `COUNT(*)` 与 `COUNT(b.id)` 有何区别？
4. 右表过滤条件应该放在 `ON` 还是 `WHERE`？
5. `RIGHT JOIN` 为什么在工程中较少使用？

## 第8天：多表 JOIN 与表别名

本日掌握：三表以上关联、别名、维表补充信息、星型查询；覆盖原理点：无；阶段：使用期

### 🟢 基础用法题

#### 题目1：订单明细展示用户和商品名

```sql
DROP TABLE IF EXISTS d8_users;
DROP TABLE IF EXISTS d8_products;
DROP TABLE IF EXISTS d8_orders;
CREATE TABLE d8_users (user_id INT PRIMARY KEY, name VARCHAR(30));
CREATE TABLE d8_products (product_id INT PRIMARY KEY, product_name VARCHAR(50), category VARCHAR(20));
CREATE TABLE d8_orders (order_id INT PRIMARY KEY, user_id INT, product_id INT, amount DECIMAL(10,2));
INSERT INTO d8_users VALUES (1,'张三'),(2,'李四');
INSERT INTO d8_products VALUES (10,'键盘','数码'),(11,'咖啡','食品');
INSERT INTO d8_orders VALUES (1001,1,10,199),(1002,1,11,39),(1003,2,10,199);

SELECT o.order_id, u.name, p.product_name, o.amount
FROM d8_orders o
JOIN d8_users u ON o.user_id = u.user_id
JOIN d8_products p ON o.product_id = p.product_id;
```

**运行结果示例**：每笔订单带上用户与商品名称。

**🔍 反思**：别名让多表查询更短，也能清晰表达每列来源。

**💬 追问**：如果两个表都有 `name` 字段，`SELECT name` 会怎样？

### 🟡 中级用法题

#### 题目2：统计每个品类的购买用户数和销售额

```sql
SELECT
  p.category,
  COUNT(DISTINCT o.user_id) AS buyer_cnt,
  SUM(o.amount) AS total_amount
FROM d8_orders o
JOIN d8_products p ON o.product_id = p.product_id
GROUP BY p.category
ORDER BY total_amount DESC;
```

**关键中间状态**：

```text
JOIN 后订单有了 category
GROUP BY category 后统计 buyer_cnt 和 total_amount
```

**🔍 反思**：事实表保存交易，维表保存描述信息，多表 JOIN 负责把它们拼成报表。

**💬 追问**：百万行时，`COUNT(DISTINCT user_id)` 为什么可能比 `COUNT(*)` 更慢？

### 🔴 高级/探索用法题

#### 题目3：订单、商品、优惠券四表综合分析

```sql
DROP TABLE IF EXISTS d8_coupons;
CREATE TABLE d8_coupons (coupon_id INT PRIMARY KEY, coupon_name VARCHAR(50), discount DECIMAL(10,2));
ALTER TABLE d8_orders ADD COLUMN coupon_id INT NULL;
UPDATE d8_orders SET coupon_id = 1 WHERE order_id = 1001;
INSERT INTO d8_coupons VALUES (1,'新客券',20),(2,'满减券',50);

SELECT
  o.order_id,
  u.name,
  p.category,
  p.product_name,
  o.amount,
  COALESCE(c.discount, 0) AS discount,
  o.amount - COALESCE(c.discount, 0) AS pay_amount
FROM d8_orders o
JOIN d8_users u ON o.user_id = u.user_id
JOIN d8_products p ON o.product_id = p.product_id
LEFT JOIN d8_coupons c ON o.coupon_id = c.coupon_id
ORDER BY o.order_id;
```

**逐步结果示例**：

```text
1001 使用新客券 pay_amount=179
未用券订单 discount=0
```

**🔍 反思**：可选关系用 `LEFT JOIN`，必选关系用 `JOIN`。

**💬 追问**：如果优惠券被删除，订单历史是显示 NULL，还是冗余保存券名更好？

### 🏢 大厂面试场景实战（使用层面）

**场景**：写一条 SQL 输出每个用户购买过的品类数、订单数、总消费。

```sql
SELECT
  u.user_id,
  u.name,
  COUNT(*) AS order_cnt,
  COUNT(DISTINCT p.category) AS category_cnt,
  SUM(o.amount) AS total_amount
FROM d8_users u
JOIN d8_orders o ON u.user_id = o.user_id
JOIN d8_products p ON o.product_id = p.product_id
GROUP BY u.user_id, u.name
ORDER BY total_amount DESC;
```

**🔍 反思**：多表 JOIN 是把“实体关系图”翻译为查询结果。

**💬 追问**：如果要包含从未下单用户，第一段 JOIN 应该如何改？

### 🎯 今日高频面试题速览

1. 多表 JOIN 时为什么推荐写表别名？
2. 必选关联和可选关联分别用什么 JOIN？
3. `COUNT(DISTINCT ...)` 适合什么场景？
4. 多表字段重名如何处理？
5. 如何基于订单表和商品表统计品类销售额？

## 第9天：标量子查询 + 列子查询

本日掌握：单值子查询、IN 子查询、EXISTS 子查询入门；覆盖原理点：无；阶段：使用期

### 🟢 基础用法题

#### 题目1：查找高于平均金额的订单

```sql
DROP TABLE IF EXISTS d9_orders;
CREATE TABLE d9_orders (
  order_id INT PRIMARY KEY,
  user_id INT,
  amount DECIMAL(10,2),
  status VARCHAR(20)
);
INSERT INTO d9_orders VALUES
(1,1,100,'PAID'),(2,1,200,'PAID'),(3,2,50,'PAID'),(4,3,500,'REFUND');

SELECT order_id, user_id, amount
FROM d9_orders
WHERE amount > (SELECT AVG(amount) FROM d9_orders);
```

**运行结果示例**：金额大于全表平均值的订单。

**🔍 反思**：标量子查询返回一个值，可以放在比较表达式右侧。

**💬 追问**：如果标量子查询返回多行会怎样？

### 🟡 中级用法题

#### 题目2：找出买过数码商品的用户

```sql
DROP TABLE IF EXISTS d9_products;
CREATE TABLE d9_products (product_id INT PRIMARY KEY, category VARCHAR(20));
ALTER TABLE d9_orders ADD COLUMN product_id INT;
UPDATE d9_orders SET product_id=10 WHERE order_id IN (1,2);
UPDATE d9_orders SET product_id=11 WHERE order_id IN (3,4);
INSERT INTO d9_products VALUES (10,'数码'),(11,'食品');

SELECT DISTINCT user_id
FROM d9_orders
WHERE product_id IN (
  SELECT product_id
  FROM d9_products
  WHERE category = '数码'
);
```

**关键中间状态**：

```text
子查询先得到数码 product_id: 10
外层订单筛出 product_id=10 的 user_id
```

**🔍 反思**：列子查询适合表达“外层字段属于内层集合”。

**💬 追问**：`IN` 子查询返回 `NULL` 时，结果判断有什么坑？

### 🔴 高级/探索用法题

#### 题目3：使用 EXISTS 判断用户是否有支付订单

```sql
DROP TABLE IF EXISTS d9_users;
CREATE TABLE d9_users (user_id INT PRIMARY KEY, name VARCHAR(30));
INSERT INTO d9_users VALUES (1,'张三'),(2,'李四'),(3,'王五'),(4,'赵六');

SELECT u.user_id, u.name
FROM d9_users u
WHERE EXISTS (
  SELECT 1
  FROM d9_orders o
  WHERE o.user_id = u.user_id
    AND o.status = 'PAID'
);
```

**逐步结果示例**：

```text
对每个用户判断是否存在至少一笔 PAID 订单
有则返回该用户
```

**🔍 反思**：`EXISTS` 关注“有没有”，不关注子查询具体返回哪些列。

**💬 追问**：旧版本中，`IN` 和 `EXISTS` 哪个更直观？如何用 JOIN 改写？

### 🏢 大厂面试场景实战（使用层面）

**场景**：找出“下单金额超过自己历史平均金额”的订单。

```sql
SELECT o.order_id, o.user_id, o.amount
FROM d9_orders o
WHERE o.amount > (
  SELECT AVG(o2.amount)
  FROM d9_orders o2
  WHERE o2.user_id = o.user_id
);
```

**🔍 反思**：这已经是相关子查询雏形，内层会引用外层用户。

**💬 追问**：如果用户只有一笔订单，这笔订单会不会大于自己的平均金额？

### 🎯 今日高频面试题速览

1. 什么是标量子查询？
2. `IN` 子查询适合什么场景？
3. `EXISTS` 通常返回哪些列？
4. 子查询返回多行时能否用于 `=`？
5. 如何查高于全表平均值的记录？

## 第10天：相关子查询与非相关子查询

本日掌握：外层引用、逐行判断、子查询与 JOIN 改写；覆盖原理点：无；阶段：使用期

### 🟢 基础用法题

#### 题目1：查询每个用户最后一笔订单

```sql
DROP TABLE IF EXISTS d10_orders;
CREATE TABLE d10_orders (
  order_id INT PRIMARY KEY,
  user_id INT,
  amount DECIMAL(10,2),
  created_at DATETIME
);
INSERT INTO d10_orders VALUES
(1,1,100,'2026-05-01 10:00:00'),
(2,1,200,'2026-05-02 10:00:00'),
(3,2,50,'2026-05-01 11:00:00'),
(4,2,80,'2026-05-03 11:00:00');

SELECT order_id, user_id, amount, created_at
FROM d10_orders o
WHERE created_at = (
  SELECT MAX(o2.created_at)
  FROM d10_orders o2
  WHERE o2.user_id = o.user_id
);
```

**运行结果示例**：用户 1 的订单 2，用户 2 的订单 4。

**🔍 反思**：相关子查询的内层会用到外层当前行的字段。

**💬 追问**：如果同一用户最后时间有两笔订单，会返回几行？

### 🟡 中级用法题

#### 题目2：找消费超过同城平均值的用户

```sql
DROP TABLE IF EXISTS d10_users;
CREATE TABLE d10_users (
  user_id INT PRIMARY KEY,
  name VARCHAR(30),
  city VARCHAR(20),
  total_amount DECIMAL(10,2)
);
INSERT INTO d10_users VALUES
(1,'张三','北京',1000),(2,'李四','北京',500),
(3,'王五','上海',700),(4,'赵六','上海',900);

SELECT user_id, name, city, total_amount
FROM d10_users u
WHERE total_amount > (
  SELECT AVG(u2.total_amount)
  FROM d10_users u2
  WHERE u2.city = u.city
);
```

**关键中间状态**：

```text
北京平均 750，张三命中
上海平均 800，赵六命中
```

**🔍 反思**：相关子查询能表达“和自己所在分组的统计值比较”。

**💬 追问**：百万行时，这种写法可能变慢；你猜测原因是什么？

### 🔴 高级/探索用法题

#### 题目3：用 NOT EXISTS 找缺失行为

**场景描述**：找出从未购买过数码商品的用户。

```sql
DROP TABLE IF EXISTS d10_order_items;
CREATE TABLE d10_order_items (
  item_id INT PRIMARY KEY,
  user_id INT,
  category VARCHAR(20)
);
INSERT INTO d10_order_items VALUES
(1,1,'数码'),(2,1,'食品'),(3,2,'食品'),(4,3,'服饰');

SELECT u.user_id, u.name
FROM d10_users u
WHERE NOT EXISTS (
  SELECT 1
  FROM d10_order_items i
  WHERE i.user_id = u.user_id
    AND i.category = '数码'
);
```

**逐步结果示例**：排除张三，保留李四、王五、赵六。

**🔍 反思**：`NOT EXISTS` 适合查“从未发生过”的业务行为。

**💬 追问**：如何用 `LEFT JOIN ... IS NULL` 改写？

### 🏢 大厂面试场景实战（使用层面）

**场景**：找出每个部门薪资最高的员工，允许并列。

```sql
DROP TABLE IF EXISTS d10_employees;
CREATE TABLE d10_employees (
  emp_id INT PRIMARY KEY,
  dept_id INT,
  name VARCHAR(30),
  salary INT
);
INSERT INTO d10_employees VALUES
(1,10,'A',100),(2,10,'B',120),(3,10,'C',120),(4,20,'D',90),(5,20,'E',80);

SELECT emp_id, dept_id, name, salary
FROM d10_employees e
WHERE salary = (
  SELECT MAX(e2.salary)
  FROM d10_employees e2
  WHERE e2.dept_id = e.dept_id
);
```

**🔍 反思**：相关子查询天然支持“每组最大值并列返回”。

**💬 追问**：如果只要每个部门一个人，如何处理并列？

### 🎯 今日高频面试题速览

1. 相关子查询和非相关子查询的区别是什么？
2. 如何查询每组最大值对应的整行？
3. `NOT EXISTS` 适合解决什么问题？
4. 相关子查询能不能引用外层表别名？
5. 如何把相关子查询改写成 JOIN？

## 第11天：UNION / UNION ALL 合并结果集

本日掌握：纵向合并、去重合并、保留重复、字段对齐；覆盖原理点：无；阶段：使用期

### 🟢 基础用法题

#### 题目1：合并 App 和 Web 注册用户

```sql
DROP TABLE IF EXISTS d11_app_users;
DROP TABLE IF EXISTS d11_web_users;
CREATE TABLE d11_app_users (user_id INT, name VARCHAR(30), registered_at DATE);
CREATE TABLE d11_web_users (user_id INT, name VARCHAR(30), registered_at DATE);
INSERT INTO d11_app_users VALUES (1,'张三','2026-05-01'),(2,'李四','2026-05-02');
INSERT INTO d11_web_users VALUES (2,'李四','2026-05-02'),(3,'王五','2026-05-03');

SELECT user_id, name, registered_at FROM d11_app_users
UNION
SELECT user_id, name, registered_at FROM d11_web_users;
```

**运行结果示例**：用户 2 只出现一次。

**🔍 反思**：`UNION` 会合并并去重完全相同的行。

**💬 追问**：两边 SELECT 的列数不一致会怎样？

### 🟡 中级用法题

#### 题目2：保留来源渠道并避免误去重

```sql
SELECT 'APP' AS source, user_id, name, registered_at FROM d11_app_users
UNION ALL
SELECT 'WEB' AS source, user_id, name, registered_at FROM d11_web_users
ORDER BY registered_at, user_id;
```

**关键中间状态**：

```text
UNION ALL 保留重复来源
source 字段帮助分析渠道归因
```

**🔍 反思**：如果业务需要计数来源，通常不能随便去重。

**💬 追问**：百万行时，`UNION` 比 `UNION ALL` 可能慢在哪里？这里只做猜测。

### 🔴 高级/探索用法题

#### 题目3：合并不同业务表为统一消息流

```sql
DROP TABLE IF EXISTS d11_comments;
DROP TABLE IF EXISTS d11_likes;
CREATE TABLE d11_comments (comment_id INT, user_id INT, content VARCHAR(100), created_at DATETIME);
CREATE TABLE d11_likes (like_id INT, user_id INT, target_id INT, created_at DATETIME);
INSERT INTO d11_comments VALUES (1,1,'hello','2026-05-01 10:00:00');
INSERT INTO d11_likes VALUES (10,2,1,'2026-05-01 10:05:00');

SELECT 'COMMENT' AS event_type, comment_id AS event_id, user_id, content AS detail, created_at
FROM d11_comments
UNION ALL
SELECT 'LIKE' AS event_type, like_id AS event_id, user_id, CONCAT('target=', target_id) AS detail, created_at
FROM d11_likes
ORDER BY created_at DESC;
```

**逐步结果示例**：

```text
不同表被规整为 event_type/event_id/user_id/detail/created_at
前端可按统一消息流展示
```

**🔍 反思**：`UNION ALL` 可把不同实体投影成同一接口模型。

**💬 追问**：如果某列类型不同，如一个是数字一个是字符串，如何统一？

### 🏢 大厂面试场景实战（使用层面）

**场景**：搜索页要同时展示商品、店铺、文章，每类最多返回 5 条，合并后按发布时间排序。

```sql
SELECT 'PRODUCT' AS item_type, product_id AS item_id, product_name AS title, created_at
FROM products
WHERE product_name LIKE '%键盘%'
LIMIT 5;

-- 完整合并写法：
SELECT * FROM (
  SELECT 'PRODUCT' AS item_type, product_id AS item_id, product_name AS title, created_at
  FROM products
  WHERE product_name LIKE '%键盘%'
  LIMIT 5
) p
UNION ALL
SELECT * FROM (
  SELECT 'SHOP' AS item_type, shop_id AS item_id, shop_name AS title, created_at
  FROM shops
  WHERE shop_name LIKE '%键盘%'
  LIMIT 5
) s
UNION ALL
SELECT * FROM (
  SELECT 'ARTICLE' AS item_type, article_id AS item_id, title, created_at
  FROM articles
  WHERE title LIKE '%键盘%'
  LIMIT 5
) a
ORDER BY created_at DESC;
```

**🔍 反思**：多来源聚合需要统一列名、列数和含义。

**💬 追问**：每类先 `LIMIT 5` 和全量合并后再 `LIMIT 15` 的结果有什么区别？

### 🎯 今日高频面试题速览

1. `UNION` 和 `UNION ALL` 的区别是什么？
2. `UNION` 两边 SELECT 需要满足什么条件？
3. 如何给合并结果增加来源标记？
4. `ORDER BY` 应该放在单个 SELECT 后还是整体末尾？
5. 什么时候不能使用 `UNION` 去重？

## 第12天：窗口函数 ROW_NUMBER / RANK / DENSE_RANK

本日掌握：分区排名、并列排名、组内 Top N；覆盖原理点：无；阶段：使用期

### 🟢 基础用法题

#### 题目1：按部门给员工薪资排名

```sql
DROP TABLE IF EXISTS d12_employees;
CREATE TABLE d12_employees (
  emp_id INT PRIMARY KEY,
  dept_id INT,
  name VARCHAR(30),
  salary INT
);
INSERT INTO d12_employees VALUES
(1,10,'A',100),(2,10,'B',120),(3,10,'C',120),(4,10,'D',90),
(5,20,'E',200),(6,20,'F',180);

SELECT
  dept_id, name, salary,
  ROW_NUMBER() OVER (PARTITION BY dept_id ORDER BY salary DESC, emp_id ASC) AS rn
FROM d12_employees;
```

**运行结果示例**：每个部门内从 1 开始编号。

**🔍 反思**：窗口函数不会把多行压缩成一行，而是在原行上追加计算结果。

**💬 追问**：`PARTITION BY` 和 `GROUP BY` 的结果形态有什么不同？

### 🟡 中级用法题

#### 题目2：每个部门薪资前2名

```sql
SELECT *
FROM (
  SELECT
    dept_id, emp_id, name, salary,
    ROW_NUMBER() OVER (PARTITION BY dept_id ORDER BY salary DESC, emp_id ASC) AS rn
  FROM d12_employees
) t
WHERE rn <= 2
ORDER BY dept_id, rn;
```

**关键中间状态**：

```text
内层生成每部门排名
外层过滤 rn <= 2
```

**🔍 反思**：窗口函数非常适合 Top N、去重取最新等需求。

**💬 追问**：百万行时，分区排序可能变慢；你猜瓶颈在哪里？

### 🔴 高级/探索用法题

#### 题目3：比较 ROW_NUMBER、RANK、DENSE_RANK

```sql
SELECT
  dept_id, name, salary,
  ROW_NUMBER() OVER (PARTITION BY dept_id ORDER BY salary DESC) AS row_no,
  RANK() OVER (PARTITION BY dept_id ORDER BY salary DESC) AS rank_no,
  DENSE_RANK() OVER (PARTITION BY dept_id ORDER BY salary DESC) AS dense_rank_no
FROM d12_employees
ORDER BY dept_id, salary DESC, emp_id;
```

**逐步结果示例**：

```text
部门10中 B 和 C 都是 120
ROW_NUMBER: 1,2
RANK: 1,1,3
DENSE_RANK: 1,1,2
```

**🔍 反思**：选择哪种排名函数取决于是否允许并列，以及并列后是否跳号。

**💬 追问**：旧版本 MySQL 没有窗口函数，如何取每组前2名？

### 🏢 大厂面试场景实战（使用层面）

**场景**：写 SQL 找出每个品类销量前三的商品，允许并列。

```sql
DROP TABLE IF EXISTS d12_sales;
CREATE TABLE d12_sales (product_id INT, category VARCHAR(20), sale_cnt INT);
INSERT INTO d12_sales VALUES
(1,'数码',100),(2,'数码',100),(3,'数码',80),(4,'数码',70),
(5,'食品',60),(6,'食品',50),(7,'食品',50);

SELECT product_id, category, sale_cnt, rnk
FROM (
  SELECT
    product_id, category, sale_cnt,
    RANK() OVER (PARTITION BY category ORDER BY sale_cnt DESC) AS rnk
  FROM d12_sales
) t
WHERE rnk <= 3;
```

**🔍 反思**：允许并列时，用 `RANK` 或 `DENSE_RANK` 比 `ROW_NUMBER` 更符合业务。

**💬 追问**：如果要求每个品类严格返回 3 个商品，该用哪个函数？

### 🎯 今日高频面试题速览

1. `ROW_NUMBER()`、`RANK()`、`DENSE_RANK()` 的区别是什么？
2. 如何查询每组 Top N？
3. 窗口函数和聚合函数有什么结果差异？
4. `PARTITION BY` 的作用是什么？
5. 并列排名时如何保证结果稳定？

## 第13天：LAG / LEAD + 滑动窗口帧

本日掌握：前后行比较、环比、移动求和、窗口帧；覆盖原理点：无；阶段：使用期

### 🟢 基础用法题

#### 题目1：计算每日销售额环比

```sql
DROP TABLE IF EXISTS d13_daily_sales;
CREATE TABLE d13_daily_sales (
  stat_date DATE PRIMARY KEY,
  amount DECIMAL(10,2)
);
INSERT INTO d13_daily_sales VALUES
('2026-05-01',100),('2026-05-02',150),('2026-05-03',120),('2026-05-04',200);

SELECT
  stat_date,
  amount,
  LAG(amount) OVER (ORDER BY stat_date) AS prev_amount,
  amount - LAG(amount) OVER (ORDER BY stat_date) AS diff_amount
FROM d13_daily_sales;
```

**运行结果示例**：第一天 `prev_amount` 为 `NULL`，第二天差值 50。

**🔍 反思**：`LAG` 能把上一行数据拉到当前行，方便做环比。

**💬 追问**：第一行没有上一行时如何用默认值替代 `NULL`？

### 🟡 中级用法题

#### 题目2：计算三日移动销售额

```sql
SELECT
  stat_date,
  amount,
  SUM(amount) OVER (
    ORDER BY stat_date
    ROWS BETWEEN 2 PRECEDING AND CURRENT ROW
  ) AS rolling_3d_amount
FROM d13_daily_sales
ORDER BY stat_date;
```

**关键中间状态**：

```text
05-01: 只包含自己
05-02: 包含 05-01 到 05-02
05-03: 包含 05-01 到 05-03
05-04: 包含 05-02 到 05-04
```

**🔍 反思**：窗口帧定义“当前行附近哪些行参与计算”。

**💬 追问**：百万行日报计算移动窗口时，可能在哪些步骤变慢？

### 🔴 高级/探索用法题

#### 题目3：识别连续增长区间

```sql
WITH s AS (
  SELECT
    stat_date,
    amount,
    LAG(amount) OVER (ORDER BY stat_date) AS prev_amount
  FROM d13_daily_sales
),
flagged AS (
  SELECT
    stat_date,
    amount,
    CASE WHEN prev_amount IS NOT NULL AND amount > prev_amount THEN 1 ELSE 0 END AS is_growth
  FROM s
)
SELECT
  stat_date,
  amount,
  is_growth,
  SUM(CASE WHEN is_growth = 0 THEN 1 ELSE 0 END) OVER (ORDER BY stat_date) AS segment_id
FROM flagged;
```

**逐步结果示例**：

```text
先用 LAG 得到昨日金额
再判断今天是否增长
再用累计分段标记增长段
```

**🔍 反思**：窗口函数可以把“跨行状态”变成可查询字段。

**💬 追问**：旧版本没有 CTE 和窗口函数时，如何用自连接处理昨日比较？

### 🏢 大厂面试场景实战（使用层面）

**场景**：统计每个用户最近三次订单的金额合计。

```sql
DROP TABLE IF EXISTS d13_orders;
CREATE TABLE d13_orders (order_id INT PRIMARY KEY, user_id INT, amount DECIMAL(10,2), created_at DATETIME);
INSERT INTO d13_orders VALUES
(1,1,100,'2026-05-01'),(2,1,200,'2026-05-02'),(3,1,300,'2026-05-03'),(4,1,400,'2026-05-04'),
(5,2,50,'2026-05-02'),(6,2,70,'2026-05-03');

SELECT user_id, order_id, amount, recent_3_amount
FROM (
  SELECT
    user_id, order_id, amount,
    SUM(amount) OVER (
      PARTITION BY user_id
      ORDER BY created_at DESC
      ROWS BETWEEN CURRENT ROW AND 2 FOLLOWING
    ) AS recent_3_amount,
    ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY created_at DESC) AS rn
  FROM d13_orders
) t
WHERE rn = 1;
```

**🔍 反思**：排序方向会影响窗口帧的“前后”含义。

**💬 追问**：如果用户不足三单，结果应该如何解释？

### 🎯 今日高频面试题速览

1. `LAG` 和 `LEAD` 分别取哪一行？
2. 如何计算日环比？
3. `ROWS BETWEEN 2 PRECEDING AND CURRENT ROW` 表示什么？
4. 移动平均如何用窗口帧实现？
5. 没有上一行时如何设置默认值？

## 第14天：常用日期/字符串/数值函数

本日掌握：DATE_ADD、DATEDIFF、DATE_FORMAT、CONCAT、SUBSTRING、ROUND；覆盖原理点：无；阶段：使用期

### 🟢 基础用法题

#### 题目1：计算会员注册天数

```sql
DROP TABLE IF EXISTS d14_users;
CREATE TABLE d14_users (
  user_id INT PRIMARY KEY,
  name VARCHAR(30),
  registered_at DATE,
  phone VARCHAR(20),
  amount DECIMAL(10,4)
);
INSERT INTO d14_users VALUES
(1,'张三','2026-05-01','13800138000',123.4567),
(2,'李四','2026-04-20','13900139000',88.8888);

SELECT
  user_id,
  name,
  DATEDIFF('2026-05-10', registered_at) AS registered_days
FROM d14_users;
```

**运行结果示例**：张三 9 天，李四 20 天。

**🔍 反思**：日期函数让业务时间口径显性化。

**💬 追问**：`DATEDIFF` 是否考虑时分秒？

### 🟡 中级用法题

#### 题目2：手机号脱敏与金额格式化

```sql
SELECT
  user_id,
  CONCAT(SUBSTRING(phone, 1, 3), '****', SUBSTRING(phone, 8, 4)) AS masked_phone,
  ROUND(amount, 2) AS rounded_amount,
  DATE_FORMAT(registered_at, '%Y-%m') AS registered_month
FROM d14_users;
```

**关键中间状态**：

```text
13800138000 -> 138****8000
123.4567 -> 123.46
```

**🔍 反思**：展示层处理可以用 SQL 函数快速完成，但复杂脱敏通常应统一封装。

**💬 追问**：如果手机号长度不固定，直接 `SUBSTRING` 会有什么问题？

### 🔴 高级/探索用法题

#### 题目3：按自然周统计注册用户

```sql
SELECT
  YEARWEEK(registered_at, 1) AS iso_week,
  MIN(registered_at) AS first_day_in_data,
  MAX(registered_at) AS last_day_in_data,
  COUNT(*) AS user_cnt
FROM d14_users
GROUP BY YEARWEEK(registered_at, 1)
ORDER BY iso_week;
```

**逐步结果示例**：

```text
YEARWEEK(date,1) 使用周一作为一周开始
按周聚合后输出用户数
```

**🔍 反思**：时间统计必须明确自然日、自然周、自然月的口径。

**💬 追问**：旧版本或跨数据库迁移时，如何避免 `YEARWEEK` 口径差异？

### 🏢 大厂面试场景实战（使用层面）

**场景**：生成一份“本月新注册用户”报表，展示脱敏手机号、注册日期、注册后 7 天到期日。

```sql
SELECT
  user_id,
  name,
  CONCAT(SUBSTRING(phone, 1, 3), '****', SUBSTRING(phone, 8, 4)) AS masked_phone,
  DATE_FORMAT(registered_at, '%Y-%m-%d') AS registered_date,
  DATE_ADD(registered_at, INTERVAL 7 DAY) AS trial_expire_date
FROM d14_users
WHERE registered_at >= '2026-05-01'
  AND registered_at < '2026-06-01';
```

**🔍 反思**：月报查询建议用半开区间，而不是只比较月份字符串。

**💬 追问**：为什么 `WHERE DATE_FORMAT(registered_at,'%Y-%m')='2026-05'` 在大表上可能不理想？第14天只做现象猜测。

### 🎯 今日高频面试题速览

1. `DATE_ADD` 如何加 7 天？
2. `DATEDIFF` 返回什么？
3. 如何用 SQL 做手机号脱敏？
4. `ROUND(x,2)` 的作用是什么？
5. 月份筛选为什么常用半开区间？

## 第15天：综合实战：销售大屏报表

本日掌握：多表、聚合、条件聚合、窗口排名、日期函数综合使用；覆盖原理点：无；阶段：使用期

### 🟢 基础用法题

#### 题目1：搭建销售大屏基础数据集

```sql
DROP TABLE IF EXISTS d15_users;
DROP TABLE IF EXISTS d15_products;
DROP TABLE IF EXISTS d15_orders;
CREATE TABLE d15_users (user_id INT PRIMARY KEY, city VARCHAR(20));
CREATE TABLE d15_products (product_id INT PRIMARY KEY, category VARCHAR(20), product_name VARCHAR(50));
CREATE TABLE d15_orders (
  order_id INT PRIMARY KEY,
  user_id INT,
  product_id INT,
  status VARCHAR(20),
  amount DECIMAL(10,2),
  created_at DATETIME
);
INSERT INTO d15_users VALUES (1,'北京'),(2,'上海'),(3,'北京');
INSERT INTO d15_products VALUES (10,'数码','键盘'),(11,'食品','咖啡'),(12,'数码','鼠标');
INSERT INTO d15_orders VALUES
(1,1,10,'PAID',199,'2026-05-01 10:00:00'),
(2,1,11,'PAID',39,'2026-05-01 11:00:00'),
(3,2,10,'REFUND',199,'2026-05-02 10:00:00'),
(4,3,12,'PAID',99,'2026-05-02 12:00:00'),
(5,3,10,'PAID',199,'2026-05-03 09:00:00');

SELECT DATE(created_at) AS stat_date, COUNT(*) AS order_cnt, SUM(amount) AS gross_amount
FROM d15_orders
GROUP BY DATE(created_at)
ORDER BY stat_date;
```

**运行结果示例**：按日输出订单数和总金额。

**🔍 反思**：综合报表通常先确定事实表和统计口径。

**💬 追问**：退款订单是否应该计入销售额，要由什么决定？

### 🟡 中级用法题

#### 题目2：按城市和品类统计支付销售额

```sql
SELECT
  u.city,
  p.category,
  COUNT(*) AS paid_order_cnt,
  SUM(o.amount) AS paid_amount
FROM d15_orders o
JOIN d15_users u ON o.user_id = u.user_id
JOIN d15_products p ON o.product_id = p.product_id
WHERE o.status = 'PAID'
GROUP BY u.city, p.category
ORDER BY paid_amount DESC;
```

**关键中间状态**：

```text
订单 JOIN 用户得到 city
订单 JOIN 商品得到 category
WHERE 仅保留 PAID
GROUP BY city, category
```

**🔍 反思**：报表 SQL 要让每一层转换都对应业务口径。

**💬 追问**：如果城市为空，报表要显示“未知”还是过滤掉？

### 🔴 高级/探索用法题

#### 题目3：每个城市销售额最高的品类

```sql
WITH city_category AS (
  SELECT
    u.city,
    p.category,
    SUM(o.amount) AS paid_amount
  FROM d15_orders o
  JOIN d15_users u ON o.user_id = u.user_id
  JOIN d15_products p ON o.product_id = p.product_id
  WHERE o.status = 'PAID'
  GROUP BY u.city, p.category
),
ranked AS (
  SELECT
    city,
    category,
    paid_amount,
    DENSE_RANK() OVER (PARTITION BY city ORDER BY paid_amount DESC) AS rnk
  FROM city_category
)
SELECT city, category, paid_amount
FROM ranked
WHERE rnk = 1;
```

**逐步结果示例**：

```text
第一步形成城市-品类汇总
第二步在每个城市内排名
第三步取 rnk=1
```

**🔍 反思**：CTE 能把复杂报表拆成可命名步骤。

**💬 追问**：旧版本没有 CTE 和窗口函数时，如何用派生表和相关子查询改写？

### 🏢 大厂面试场景实战（使用层面）

**场景**：输出大屏 4 个指标：今日 GMV、支付订单数、支付用户数、退款金额。

```sql
SELECT
  SUM(CASE WHEN status = 'PAID' THEN amount ELSE 0 END) AS today_gmv,
  SUM(status = 'PAID') AS paid_order_cnt,
  COUNT(DISTINCT CASE WHEN status = 'PAID' THEN user_id END) AS paid_user_cnt,
  SUM(CASE WHEN status = 'REFUND' THEN amount ELSE 0 END) AS refund_amount
FROM d15_orders
WHERE created_at >= '2026-05-01'
  AND created_at < '2026-05-02';
```

**🔍 反思**：一个大屏指标背后通常是多个条件聚合。

**💬 追问**：如果订单支付时间和创建时间不同，GMV 统计应该用哪个时间？

### 🎯 今日高频面试题速览

1. 如何用 SQL 计算 GMV？
2. 支付订单数和支付用户数有什么区别？
3. 如何求每个城市销售额最高的品类？
4. 报表 SQL 中为什么要明确时间口径？
5. 条件聚合如何写多个指标？

## 第16天：索引基础：B+树结构 + EXPLAIN 初识

本日掌握：创建索引、阅读基础 EXPLAIN、理解 B+树为何适合范围查询；覆盖原理点：P1；阶段：原理期

### 🟢 基础用法题

#### 题目1：给城市字段加索引并观察查询计划

```sql
DROP TABLE IF EXISTS d16_members;
CREATE TABLE d16_members (
  id INT PRIMARY KEY AUTO_INCREMENT,
  city VARCHAR(20),
  points INT,
  created_at DATETIME,
  KEY idx_city(city)
);
INSERT INTO d16_members(city, points, created_at) VALUES
('北京',800,'2026-05-01'),('上海',300,'2026-05-02'),
('北京',600,'2026-05-03'),('广州',1200,'2026-05-04');

EXPLAIN SELECT * FROM d16_members WHERE city = '北京';
```

**运行结果示例**：

```text
type=ref, possible_keys=idx_city, key=idx_city, rows=2
```

**🔍 反思**：索引像有序目录，等值查询可直接定位到匹配范围。

**💬 追问**：`possible_keys` 有值但 `key` 为 `NULL` 说明什么？

### 🟡 中级用法题

#### 题目2：比较全表扫描与索引查询

```sql
EXPLAIN SELECT * FROM d16_members WHERE points = 800;
CREATE INDEX idx_points ON d16_members(points);
EXPLAIN SELECT * FROM d16_members WHERE points = 800;
```

**关键中间状态**：

```text
建索引前: key 可能为 NULL
建索引后: key 可能为 idx_points
```

**🔍 反思**：不是所有字段天然适合查询，常用过滤列才值得考虑索引。

**💬 追问**：低区分度字段加索引一定有效吗？

### 🔴 高级/探索用法题

#### 题目3：联合索引下的范围查询观察

```sql
CREATE INDEX idx_city_points ON d16_members(city, points);

EXPLAIN
SELECT id, city, points
FROM d16_members
WHERE city = '北京' AND points > 500
ORDER BY points;
```

**逐步结果示例**：

```text
key=idx_city_points
type 可能为 range
Extra 可能出现 Using index condition 或 Using where
```

**🔍 反思**：联合索引中的列顺序会影响能否连续利用索引。

**💬 追问**：为什么 `(city, points)` 和 `(points, city)` 对这个查询不等价？

### ⚙️ 性能压测/证据题

**实验**：批量插入 10 万行，比较有无 `idx_city` 的查询耗时。

```sql
SET profiling = 1;
SELECT SQL_NO_CACHE COUNT(*) FROM d16_members WHERE city = '北京';
SHOW PROFILES;
-- 记录耗时后 DROP INDEX idx_city ON d16_members，再执行同样查询对比
```

| 方案 | key | rows 估计 | 耗时示例 |
|---|---|---:|---:|
| 无索引 | NULL | 100000 | 120ms |
| idx_city | idx_city | 25000 | 18ms |

**🔍 反思**：索引减少了需要检查的行范围，但低区分度列收益有限。

**💬 追问**：如何继续找出索引收益的拐点？

### 🔷 原理剖析题

**题目：B+树高度与页访问次数**

```sql
SHOW INDEX FROM d16_members;
SHOW TABLE STATUS LIKE 'd16_members';
```

**探针日志示例**：

```text
Cardinality 显示索引基数估计
Data_length/Index_length 显示表和索引占用
```

**🔍 反思**：B+树把数据按页组织，高扇出让千万级数据也能保持较低树高。

**💬 追问**：B+树叶子节点为什么要用链表连接？

### 🏢 大厂面试场景实战

**场景**：`orders` 表按 `order_status='PAID' AND amount > 100` 查询慢，请给出排查 SQL 和初始优化。

```sql
EXPLAIN SELECT order_id, user_id, amount
FROM orders
WHERE order_status = 'PAID' AND amount > 100;

CREATE INDEX idx_status_amount ON orders(order_status, amount);
```

**🔍 反思**：先看执行计划确认是否扫描过多行，再根据等值列和范围列设计索引。

**💬 追问**：如果 `PAID` 占全表 90%，这个索引还一定好吗？

### 🎯 今日高频面试题速览

1. 为什么 MySQL 常用 B+树索引？
2. `EXPLAIN` 中 `type`、`key`、`rows` 分别怎么看？
3. 聚簇索引和二级索引有什么区别？
4. 什么情况下有索引也可能不用？
5. 范围查询为什么适合 B+树？

## 第17天：覆盖索引、最左前缀原则

本日掌握：覆盖索引减少回表、联合索引列顺序、最左前缀匹配；覆盖原理点：P2,P3；阶段：原理期

### 🟢 基础用法题

#### 题目1：创建覆盖索引查询订单列表

```sql
DROP TABLE IF EXISTS d17_orders;
CREATE TABLE d17_orders (
  id BIGINT PRIMARY KEY,
  user_id BIGINT,
  status VARCHAR(20),
  created_at DATETIME,
  amount DECIMAL(10,2),
  KEY idx_user_status_time(user_id, status, created_at)
);
INSERT INTO d17_orders VALUES
(1,10,'PAID','2026-05-01',100),(2,10,'CANCEL','2026-05-02',50),(3,11,'PAID','2026-05-03',80);

EXPLAIN SELECT user_id, status, created_at
FROM d17_orders
WHERE user_id = 10 AND status = 'PAID';
```

**输出示例**：`Extra=Using index`。

**🔍 反思**：查询列都在索引中时，可少一次回表。

**💬 追问**：`SELECT *` 为什么经常破坏覆盖索引？

### 🟡 中级用法题

#### 题目2：验证最左前缀

```sql
EXPLAIN SELECT * FROM d17_orders WHERE user_id = 10;
EXPLAIN SELECT * FROM d17_orders WHERE user_id = 10 AND status = 'PAID';
EXPLAIN SELECT * FROM d17_orders WHERE status = 'PAID';
```

**关键中间状态**：

```text
user_id 命中最左列
user_id + status 命中连续前缀
只查 status 通常不能完整利用 idx_user_status_time
```

**🔍 反思**：联合索引不是多个单列索引的简单叠加。

**💬 追问**：为什么业务最高频查询要决定联合索引列顺序？

### 🔴 高级/探索用法题

#### 题目3：排序与联合索引

```sql
EXPLAIN
SELECT id, user_id, status, created_at
FROM d17_orders
WHERE user_id = 10 AND status = 'PAID'
ORDER BY created_at DESC
LIMIT 20;
```

**逐步结果示例**：

```text
WHERE 使用 user_id,status
ORDER BY created_at 可沿联合索引顺序读取
避免额外排序的概率更高
```

**🔍 反思**：好索引同时服务过滤、排序和返回列。

**💬 追问**：如果排序是 `ORDER BY amount`，当前索引还能避免排序吗？

### ⚙️ 性能压测/证据题

| 查询 | 索引 | Extra | 耗时示例 |
|---|---|---|---:|
| `SELECT *` | idx_user_status_time | Using where | 35ms |
| 只查索引列 | idx_user_status_time | Using index | 12ms |

```sql
SET profiling=1;
SELECT SQL_NO_CACHE * FROM d17_orders WHERE user_id=10 AND status='PAID';
SELECT SQL_NO_CACHE user_id,status,created_at FROM d17_orders WHERE user_id=10 AND status='PAID';
SHOW PROFILES;
```

**🔍 反思**：覆盖索引快在减少回表和数据页读取。

**💬 追问**：覆盖索引是否意味着索引越宽越好？

### 🔷 原理剖析题

**题目：二级索引叶子节点保存什么**

```sql
SHOW INDEX FROM d17_orders;
EXPLAIN FORMAT=JSON
SELECT user_id,status,created_at FROM d17_orders WHERE user_id=10 AND status='PAID';
```

**探针日志示例**：JSON 中可观察 `used_key_parts`。

**🔍 反思**：InnoDB 二级索引叶子节点保存索引列和主键，回表就是拿主键再查聚簇索引。

**💬 追问**：为什么主键过长会让二级索引变大？

### 🏢 大厂面试场景实战

**场景**：订单列表页按用户、状态、时间倒序查询，只展示 `id,status,created_at,amount`，如何设计索引？

```sql
CREATE INDEX idx_user_status_time_amount ON orders(user_id, status, created_at, amount);
EXPLAIN SELECT id, status, created_at, amount
FROM orders
WHERE user_id = 1001 AND status = 'PAID'
ORDER BY created_at DESC
LIMIT 20;
```

**🔍 反思**：索引设计要贴着页面查询条件与展示字段。

**💬 追问**：把 `amount` 放进索引是收益还是写入负担？

### 🎯 今日高频面试题速览

1. 什么是覆盖索引？
2. 什么是回表？
3. 最左前缀原则怎么判断？
4. 联合索引列顺序如何设计？
5. 为什么不建议无脑 `SELECT *`？

## 第18天：索引下推、索引合并

本日掌握：ICP 过滤下推、Index Merge、多个单列索引的取舍；覆盖原理点：P4,P5；阶段：原理期

### 🟢 基础用法题

#### 题目1：观察索引下推 Extra

```sql
DROP TABLE IF EXISTS d18_users;
CREATE TABLE d18_users (
  id INT PRIMARY KEY,
  city VARCHAR(20),
  name VARCHAR(30),
  age INT,
  KEY idx_city_name(city, name)
);
INSERT INTO d18_users VALUES
(1,'北京','张三',20),(2,'北京','张小明',30),(3,'北京','李四',25),(4,'上海','张五',40);

EXPLAIN SELECT * FROM d18_users
WHERE city = '北京' AND name LIKE '张%';
```

**输出示例**：`Extra` 可能包含 `Using index condition`。

**🔍 反思**：能在索引层判断的条件，尽量提前过滤。

**💬 追问**：`LIKE '%张'` 还能很好利用索引前缀吗？

### 🟡 中级用法题

#### 题目2：观察索引合并

```sql
CREATE INDEX idx_age ON d18_users(age);
CREATE INDEX idx_city ON d18_users(city);

EXPLAIN SELECT * FROM d18_users
WHERE city = '北京' OR age = 40;
```

**关键中间状态**：

```text
可能看到 type=index_merge
key 可能显示 idx_city,idx_age
Extra 可能包含 union/intersect
```

**🔍 反思**：索引合并是优化器尝试利用多个单列索引的方式。

**💬 追问**：索引合并一定比联合索引好吗？

### 🔴 高级/探索用法题

#### 题目3：联合索引与索引合并对比

```sql
EXPLAIN SELECT * FROM d18_users
WHERE city = '北京' AND age = 30;

CREATE INDEX idx_city_age ON d18_users(city, age);

EXPLAIN SELECT * FROM d18_users
WHERE city = '北京' AND age = 30;
```

**逐步结果示例**：

```text
创建联合索引前可能使用单索引或 index_merge
创建联合索引后更可能 key=idx_city_age
```

**🔍 反思**：高频 AND 条件通常优先考虑联合索引。

**💬 追问**：多个单列索引在 OR 查询中有什么优势？

### ⚙️ 性能压测/证据题

| 方案 | 查询条件 | key | 耗时示例 |
|---|---|---|---:|
| 单列索引合并 | city OR age | idx_city,idx_age | 25ms |
| 联合索引 | city AND age | idx_city_age | 8ms |

```sql
SET optimizer_switch='index_merge=on';
EXPLAIN SELECT * FROM d18_users WHERE city='北京' OR age=40;
SET optimizer_switch='index_merge=off';
EXPLAIN SELECT * FROM d18_users WHERE city='北京' OR age=40;
```

**🔍 反思**：索引合并有合并结果集成本，不能替代所有联合索引。

**💬 追问**：如何构造数据分布验证 index_merge 的拐点？

### 🔷 原理剖析题

**题目：ICP 在哪里过滤**

```sql
EXPLAIN FORMAT=JSON
SELECT * FROM d18_users WHERE city='北京' AND name LIKE '张%';
```

**探针日志示例**：关注 `index_condition` 与 `attached_condition`。

**🔍 反思**：ICP 体现了“能在存储引擎层做的过滤，不必把行都交给 Server 层”。

**💬 追问**：如果查询列不在索引中，ICP 和回表是什么关系？

### 🏢 大厂面试场景实战

**场景**：用户搜索页支持 `city`、`age`、`name_prefix`，如何设计索引并验证？

```sql
CREATE INDEX idx_city_age_name ON users(city, age, name);
EXPLAIN SELECT id, city, age, name
FROM users
WHERE city='北京' AND age BETWEEN 20 AND 30 AND name LIKE '张%';
```

**🔍 反思**：组合搜索要按等值、范围、排序、返回列综合设计。

**💬 追问**：如果筛选项非常动态，单个联合索引能覆盖所有组合吗？

### 🎯 今日高频面试题速览

1. 什么是索引下推？
2. `Using index condition` 表示什么？
3. 什么是 Index Merge？
4. 联合索引和多个单列索引如何选择？
5. OR 条件为什么常触发索引合并？

## 第19天：慢查询日志与优化器追踪

本日掌握：开启慢查询、阅读慢日志、optimizer_trace 分析优化器选择；覆盖原理点：P6,P7；阶段：原理期

### 🟢 基础用法题

#### 题目1：查看慢查询配置

```sql
SHOW VARIABLES LIKE 'slow_query_log';
SHOW VARIABLES LIKE 'long_query_time';
SHOW VARIABLES LIKE 'slow_query_log_file';
```

**输出示例**：

```text
slow_query_log=ON
long_query_time=1.000000
slow_query_log_file=/var/lib/mysql/xxx-slow.log
```

**🔍 反思**：慢查询日志是线上定位慢 SQL 的第一手证据。

**💬 追问**：线上是否应该把 `long_query_time` 设置为 0？

### 🟡 中级用法题

#### 题目2：构造并捕获慢查询

```sql
SET GLOBAL slow_query_log = 'ON';
SET GLOBAL long_query_time = 0.1;
SELECT SLEEP(0.2);
SHOW VARIABLES LIKE 'slow_query_log_file';
```

**关键中间状态**：

```text
执行时间超过 long_query_time
慢日志中可看到 Query_time、Rows_examined 等字段
```

**🔍 反思**：慢不只看耗时，还要看扫描行数、锁等待、返回行数。

**💬 追问**：为什么只用平均耗时定位慢 SQL 不可靠？

### 🔴 高级/探索用法题

#### 题目3：使用 optimizer_trace 观察选择过程

```sql
SET optimizer_trace='enabled=on';
SELECT * FROM d18_users WHERE city='北京' AND age=30;
SELECT TRACE FROM information_schema.OPTIMIZER_TRACE\G
SET optimizer_trace='enabled=off';
```

**逐步结果示例**：

```text
trace 中可看到候选访问路径
可看到 chosen=true 的路径
```

**🔍 反思**：优化器不是“永远用你想的索引”，而是基于成本估算选择路径。

**💬 追问**：统计信息不准会导致什么问题？

### ⚙️ 性能压测/证据题

| SQL | Query_time | Rows_examined | Rows_sent |
|---|---:|---:|---:|
| 无条件报表 | 2.8s | 5000000 | 100 |
| 加日期过滤 | 0.4s | 300000 | 100 |
| 加合适索引 | 0.05s | 1000 | 100 |

```sql
EXPLAIN ANALYZE SELECT * FROM d18_users WHERE city='北京' AND age=30;
```

**🔍 反思**：优化建议要绑定证据：耗时、扫描行数、执行计划变化。

**💬 追问**：如何判断是 CPU、IO、锁等待，还是网络传输慢？

### 🔷 原理剖析题

**题目：优化器成本决策**

```sql
ANALYZE TABLE d18_users;
EXPLAIN FORMAT=JSON SELECT * FROM d18_users WHERE city='北京' AND age=30;
```

**探针日志示例**：`cost_info`、`rows_examined_per_scan`、`filtered`。

**🔍 反思**：成本模型是工程折中，依赖统计信息，不保证永远最优。

**💬 追问**：什么时候会考虑使用 `FORCE INDEX`？

### 🏢 大厂面试场景实战

 

```sql
SHOW FULL PROCESSLIST;
SHOW VARIABLES LIKE 'slow_query_log%';
EXPLAIN FORMAT=JSON
SELECT order_id, user_id, amount
FROM orders
WHERE user_id = 1001 AND status = 'PAID'
ORDER BY created_at DESC
LIMIT 20;
SELECT TRACE FROM information_schema.OPTIMIZER_TRACE\G
```

**🔍 反思**：先确认慢 SQL，再看执行计划、锁等待、数据量变化、索引变化。

**💬 追问**：如果慢日志没记录但接口慢，下一步查什么？

### 🎯 今日高频面试题速览

1. 慢查询日志里重点看哪些字段？
2. `Rows_examined` 和 `Rows_sent` 有何区别？
3. optimizer_trace 能解决什么问题？
4. `EXPLAIN` 和 `EXPLAIN ANALYZE` 有何区别？
5. 什么时候应重新 `ANALYZE TABLE`？

## 第20天：事务隔离级别与脏读、不可重复读、幻读

本日掌握：事务语法、隔离级别、并发读写现象；覆盖原理点：P8；阶段：原理期

### 🟢 基础用法题

#### 题目1：开启事务并提交转账

```sql
DROP TABLE IF EXISTS d20_accounts;
CREATE TABLE d20_accounts (id INT PRIMARY KEY, balance INT);
INSERT INTO d20_accounts VALUES (1,1000),(2,500);

START TRANSACTION;
UPDATE d20_accounts SET balance = balance - 100 WHERE id = 1;
UPDATE d20_accounts SET balance = balance + 100 WHERE id = 2;
COMMIT;

SELECT * FROM d20_accounts;
```

**输出示例**：账户 1 为 900，账户 2 为 600。

**🔍 反思**：事务把多条语句包成一个业务原子操作。

**💬 追问**：第二条更新失败时为什么要 `ROLLBACK`？

### 🟡 中级用法题

#### 题目2：模拟不可重复读

```sql
-- 会话A
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;
START TRANSACTION;
SELECT balance FROM d20_accounts WHERE id=1;

-- 会话B
UPDATE d20_accounts SET balance=800 WHERE id=1;
COMMIT;

-- 会话A
SELECT balance FROM d20_accounts WHERE id=1;
COMMIT;
```

**关键中间状态**：

```text
READ COMMITTED 下，会话A两次读可能看到不同值
```

**🔍 反思**：隔离级别越低，并发可见性越强，但一致性现象越复杂。

**💬 追问**：MySQL InnoDB 默认隔离级别是什么？

### 🔴 高级/探索用法题

#### 题目3：模拟幻读场景

```sql
DROP TABLE IF EXISTS d20_orders;
CREATE TABLE d20_orders (id INT PRIMARY KEY AUTO_INCREMENT, amount INT);
INSERT INTO d20_orders(amount) VALUES (100),(200);

-- 会话A
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;
START TRANSACTION;
SELECT COUNT(*) FROM d20_orders WHERE amount >= 100;

-- 会话B
INSERT INTO d20_orders(amount) VALUES (300);
COMMIT;

-- 会话A
SELECT COUNT(*) FROM d20_orders WHERE amount >= 100;
COMMIT;
```

**逐步结果示例**：第二次 `COUNT` 可能多一行。

**🔍 反思**：幻读关注的是同一范围再次查询时出现新行。

**💬 追问**：如果使用 `REPEATABLE READ`，普通一致性读现象会怎样？

### ⚙️ 性能压测/证据题

| 隔离级别 | 并发读写吞吐 | 异常现象风险 |
|---|---:|---|
| READ COMMITTED | 高 | 不可重复读 |
| REPEATABLE READ | 中高 | 普通读避免不可重复读 |
| SERIALIZABLE | 低 | 最强隔离 |

```sql
SHOW VARIABLES LIKE 'transaction_isolation';
```

**🔍 反思**：隔离级别是并发性能和一致性的权衡。

**💬 追问**：如何设计压测来观察不同隔离级别下 TPS 变化？

### 🔷 原理剖析题

**题目：事务可见性实验日志**

```sql
SELECT @@transaction_isolation;
SELECT CONNECTION_ID();
```

**探针日志示例**：

```text
会话A第一次读: 1000
会话B提交更新: 800
会话A第二次读: RC 下 800，RR 下一致性读仍可能是 1000
```

**🔍 反思**：隔离级别不是抽象概念，它直接决定会话能看到哪些已提交变更。

**💬 追问**：为什么强隔离会降低并发？

### 🏢 大厂面试场景实战

**场景**：余额转账系统如何避免扣款成功但加款失败？

```sql
START TRANSACTION;
UPDATE accounts SET balance = balance - 100 WHERE id = 1 AND balance >= 100;
UPDATE accounts SET balance = balance + 100 WHERE id = 2;
COMMIT;
```

**🔍 反思**：事务保证单库内多语句一致提交，业务还要检查受影响行数。

**💬 追问**：如果两个账户分属不同数据库，单机事务还能解决吗？

### 🎯 今日高频面试题速览

1. ACID 分别是什么？
2. 脏读、不可重复读、幻读分别是什么？
3. MySQL 默认隔离级别是什么？
4. `READ COMMITTED` 和 `REPEATABLE READ` 有何区别？
5. 事务中为什么要检查更新影响行数？

## 第21天：MVCC 与 Read View

本日掌握：一致性读、当前读、版本链、Read View 规则；覆盖原理点：P9；阶段：原理期

### 🟢 基础用法题

#### 题目1：观察一致性读

```sql
DROP TABLE IF EXISTS d21_docs;
CREATE TABLE d21_docs (id INT PRIMARY KEY, content VARCHAR(50));
INSERT INTO d21_docs VALUES (1,'v1');

-- 会话A
SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;
START TRANSACTION;
SELECT content FROM d21_docs WHERE id=1;

-- 会话B
UPDATE d21_docs SET content='v2' WHERE id=1;
COMMIT;

-- 会话A
SELECT content FROM d21_docs WHERE id=1;
COMMIT;
```

**输出示例**：会话 A 两次普通读都看到 `v1`。

**🔍 反思**：MVCC 让读不阻塞写，写不阻塞普通读。

**💬 追问**：`SELECT ... FOR UPDATE` 还是一致性读吗？

### 🟡 中级用法题

#### 题目2：当前读看到最新数据

```sql
-- 会话A 在会话B提交后执行
START TRANSACTION;
SELECT content FROM d21_docs WHERE id=1;
SELECT content FROM d21_docs WHERE id=1 FOR UPDATE;
COMMIT;
```

**关键中间状态**：

```text
普通 SELECT 是一致性读
FOR UPDATE 是当前读，会加锁并读取最新已提交版本
```

**🔍 反思**：是否加锁决定了读行为完全不同。

**💬 追问**：哪些语句属于当前读？

### 🔴 高级/探索用法题

#### 题目3：RC 与 RR 的 Read View 生成差异

```sql
-- 分别在 READ COMMITTED 与 REPEATABLE READ 下重复第1题实验
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;
START TRANSACTION;
SELECT content FROM d21_docs WHERE id=1;
-- 另一会话更新提交
SELECT content FROM d21_docs WHERE id=1;
COMMIT;
```

**逐步结果示例**：

```text
RC: 每次一致性读生成新 Read View
RR: 事务内第一次一致性读生成 Read View 后复用
```

**🔍 反思**：Read View 生成时机决定了“快照”是否随语句变化。

**💬 追问**：长事务为什么可能导致 undo 无法及时清理？

### ⚙️ 性能压测/证据题

| 场景 | 活跃长事务 | undo 压力 | 查询影响 |
|---|---:|---:|---|
| 无长事务 | 0 | 低 | 稳定 |
| 长事务持续读 | 1 | 升高 | 版本链可能变长 |

```sql
SELECT * FROM information_schema.INNODB_TRX\G
```

**🔍 反思**：MVCC 的代价是维护历史版本，长事务会拖住清理。

**💬 追问**：如何在线发现长事务？

```sq
-- 查看所有事务、运行时长、SQL、事务状态
SELECT
  trx_id,
  trx_state,
  trx_started,
  NOW() - trx_started AS trx_run_sec,
  trx_query,
  trx_rows_locked,
  trx_rows_modified,
  trx_isolation_level
FROM information_schema.innodb_trx
ORDER BY trx_run_sec DESC;

-- 运行时间大于10秒的
SELECT * 
FROM information_schema.innodb_trx 
WHERE TIMESTAMPDIFF(SECOND, trx_started, NOW()) > 10
ORDER BY trx_started;

-- 通过 inforamtion_schema.innodb_trx 和 information_schema.processlist 关联进程 ID，找到连接、可 Kill

SELECT
  t.trx_id,
  t.trx_started,
  TIMESTAMPDIFF(SECOND, t.trx_started, NOW()) AS run_sec,
  p.id AS process_id,
  p.user,
  p.host,
  p.db,
  p.command,
  p.time,
  t.trx_query
FROM information_schema.innodb_trx t
JOIN information_schema.processlist p 
  ON t.trx_mysql_thread_id = p.id
ORDER BY run_sec DESC;

-- 但是看不到已经执行完成的sql，如果只是事务没提交，那只会看到null
SELECT
  trx_id,
  trx_started,
  TIMESTAMPDIFF(SECOND,trx_started,NOW()) AS run_sec,
  trx_query AS 当前执行SQL,
  trx_state,
  trx_mysql_thread_id AS 线程ID
FROM information_schema.innodb_trx
ORDER BY run_sec DESC;
```



### 🔷 原理剖析题

**题目：Read View 可见性规则**

```sql
SELECT trx_id, trx_started, trx_query
FROM information_schema.INNODB_TRX;
```

**探针日志示例**：

```text
活跃事务列表: [100, 105]
当前版本 trx_id=102
根据低水位、高水位、活跃列表判断是否可见
```

**🔍 反思**：MVCC 用空间和清理复杂度换取高并发读写。

**💬 追问**：如果让你改进 MVCC，你会优先优化长事务治理还是版本存储？

### 🏢 大厂面试场景实战

**场景**：报表事务跑了 2 小时，线上 undo 暴涨，如何处理？

```sql
SELECT trx_id, trx_started, trx_mysql_thread_id, trx_query
FROM information_schema.INNODB_TRX
ORDER BY trx_started;
```

**🔍 反思**：先定位长事务，再评估能否拆分报表、降低隔离级别或迁到只读库。

**💬 追问**：直接 kill 长事务有哪些风险？

### 🎯 今日高频面试题速览

1. 什么是 MVCC？
2. Read View 在 RC 和 RR 下生成时机有什么不同？
3. 一致性读和当前读的区别是什么？
4. undo log 在 MVCC 中有什么作用？
5. 长事务为什么危险？

## 第22天：锁：全局锁、表锁、行锁、间隙锁、临键锁

本日掌握：锁类型、锁等待、范围更新风险；覆盖原理点：P10；阶段：原理期

### 🟢 基础用法题

#### 题目1：使用 FOR UPDATE 锁定库存行

```sql
DROP TABLE IF EXISTS d22_stock;
CREATE TABLE d22_stock (
  sku_id INT PRIMARY KEY,
  stock INT
);
INSERT INTO d22_stock VALUES (1,10),(2,20);

START TRANSACTION;
SELECT stock FROM d22_stock WHERE sku_id=1 FOR UPDATE;
UPDATE d22_stock SET stock = stock - 1 WHERE sku_id=1 AND stock > 0;
COMMIT;
```

**输出示例**：库存 10 变 9。

**🔍 反思**：当前读加锁能保护随后更新的业务判断。

**💬 追问**：只 `SELECT` 不 `FOR UPDATE` 是否能保护库存不被别人改？

### 🟡 中级用法题

#### 题目2：观察锁等待

```sql
-- 会话A
START TRANSACTION;
UPDATE d22_stock SET stock=stock-1 WHERE sku_id=1;

-- 会话B
START TRANSACTION;
UPDATE d22_stock SET stock=stock-1 WHERE sku_id=1;

-- 会话C
SHOW PROCESSLIST;
```

**关键中间状态**：

```text
会话B等待会话A提交或回滚
SHOW PROCESSLIST 可看到等待状态
```

**🔍 反思**：热点行写入会串行化。

**💬 追问**：`innodb_lock_wait_timeout` 控制什么？

### 🔴 高级/探索用法题

#### 题目3：范围查询触发间隙锁观察

```sql
DROP TABLE IF EXISTS d22_orders;
CREATE TABLE d22_orders (
  id INT PRIMARY KEY,
  amount INT,
  KEY idx_amount(amount)
) ENGINE=InnoDB;
INSERT INTO d22_orders VALUES (1,100),(2,200),(3,300);

-- 会话A
SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;
START TRANSACTION;
SELECT * FROM d22_orders WHERE amount BETWEEN 100 AND 200 FOR UPDATE;

-- 会话B
INSERT INTO d22_orders VALUES (4,150);
```

**逐步结果示例**：会话 B 可能等待。

**🔍 反思**：RR 下当前读的范围锁会保护范围，避免其他事务插入“幻影”。

**💬 追问**：如果没有 `idx_amount`，锁范围可能发生什么变化？

### ⚙️ 性能压测/证据题

| 场景 | 并发数 | TPS | 等待现象 |
|---|---:|---:|---|
| 更新不同 sku | 100 | 高 | 少 |
| 更新同一 sku | 100 | 低 | 多 |

```sql
SELECT * FROM performance_schema.data_locks;
SELECT * FROM performance_schema.data_lock_waits;
```

**🔍 反思**：锁粒度越小并发越好，但范围锁可能扩大影响。

**💬 追问**：如何压测热点库存的行锁竞争拐点？

### 🔷 原理剖析题

**题目：临键锁 = 记录锁 + 间隙锁**

```sql
SELECT ENGINE_TRANSACTION_ID, OBJECT_NAME, INDEX_NAME, LOCK_TYPE, LOCK_MODE, LOCK_DATA
FROM performance_schema.data_locks
WHERE OBJECT_NAME IN ('d22_orders','d22_stock');
```

**探针日志示例**：

```text
LOCK_TYPE=RECORD
LOCK_MODE=X,GAP 或 X
LOCK_DATA=索引记录或 supremum pseudo-record
```

**🔍 反思**：临键锁用更复杂的锁范围换取 RR 下的范围一致性。

**💬 追问**：如何通过唯一索引等值查询减少锁范围？

### 🏢 大厂面试场景实战

**场景**：促销库存更新大量锁等待，如何排查？

```sql
SHOW ENGINE INNODB STATUS\G
SELECT * FROM performance_schema.data_lock_waits;
SELECT * FROM performance_schema.data_locks;
```

**🔍 反思**：先看是否同一热点行，再看 SQL 是否范围过大、索引是否缺失。

**💬 追问**：库存扣减可以如何拆行或异步化？

### 🎯 今日高频面试题速览

1. 行锁、表锁、全局锁分别用于什么？
2. 什么是间隙锁？
3. 什么是临键锁？
4. `SELECT ... FOR UPDATE` 有什么作用？
5. 如何查看锁等待？

## 第23天：死锁检测与死锁日志分析

本日掌握：死锁构造、死锁日志、加锁顺序治理；覆盖原理点：P11；阶段：原理期

### 🟢 基础用法题

#### 题目1：构造最小死锁

```sql
DROP TABLE IF EXISTS d23_account;
CREATE TABLE d23_account (id INT PRIMARY KEY, balance INT);
INSERT INTO d23_account VALUES (1,100),(2,100);

-- 会话A
START TRANSACTION;
UPDATE d23_account SET balance=balance-10 WHERE id=1;

-- 会话B
START TRANSACTION;
UPDATE d23_account SET balance=balance-20 WHERE id=2;

-- 会话A
UPDATE d23_account SET balance=balance+10 WHERE id=2;

-- 会话B
UPDATE d23_account SET balance=balance+20 WHERE id=1;
```

**输出示例**：其中一个会话收到 `Deadlock found when trying to get lock`。

**🔍 反思**：死锁来自事务之间互相等待对方持有的资源。

**💬 追问**：为什么数据库会主动回滚其中一个事务？

### 🟡 中级用法题

#### 题目2：查看最近一次死锁日志

```sql
SHOW ENGINE INNODB STATUS\G
```

**关键中间状态**：

```text
LATEST DETECTED DEADLOCK
TRANSACTION 1 waits for ...
TRANSACTION 2 holds ...
WE ROLL BACK TRANSACTION ...
```

**🔍 反思**：死锁日志要读出“谁持有什么锁，谁在等什么锁”。

**💬 追问**：线上如何保留更多死锁信息？

### 🔴 高级/探索用法题

#### 题目3：统一加锁顺序避免死锁

```sql
-- 永远先锁小 id，再锁大 id
START TRANSACTION;
SELECT * FROM d23_account WHERE id IN (1,2) ORDER BY id FOR UPDATE;
UPDATE d23_account SET balance=balance-10 WHERE id=1;
UPDATE d23_account SET balance=balance+10 WHERE id=2;
COMMIT;
```

**逐步结果示例**：

```text
两个事务都按 id 从小到大拿锁
不会形成 A等B、B等A 的环
```

**🔍 反思**：固定资源访问顺序是死锁治理的核心手段之一。

**💬 追问**：如果业务无法固定顺序，应用层如何重试？

### ⚙️ 性能压测/证据题

| 策略 | 死锁次数/分钟 | 成功率 | 平均耗时 |
|---|---:|---:|---:|
| 随机加锁顺序 | 30 | 96% | 80ms |
| 固定加锁顺序 | 0 | 99.9% | 35ms |

```sql
SHOW GLOBAL STATUS LIKE 'Innodb_deadlocks';
```

**🔍 反思**：死锁不是只能靠数据库处理，应用设计能显著降低发生率。

**💬 追问**：如何区分死锁和普通锁超时？

### 🔷 原理剖析题

**题目：等待图与死锁检测**

```sql
SELECT * FROM performance_schema.data_lock_waits;
```

**探针日志示例**：

```text
requesting_engine_transaction_id -> blocking_engine_transaction_id
形成环即死锁
```

**🔍 反思**：死锁检测有成本，高并发热点写下检测本身也可能成为负担。

**💬 追问**：什么时候会考虑关闭死锁检测并依赖超时？

### 🏢 大厂面试场景实战

**场景**：转账服务偶发死锁，如何设计重试与治理方案？

```sql
-- 伪代码
for retry in 1..3:
  begin
    lock accounts by sorted account_id
    update debit
    update credit
    commit
  catch deadlock:
    rollback
    sleep random_jitter
```

**🔍 反思**：治理包括固定加锁顺序、缩短事务、失败重试、监控告警。

**💬 追问**：重试如何保证幂等？

### 🎯 今日高频面试题速览

1. 什么是死锁？
2. 如何查看 MySQL 最近一次死锁？
3. 如何避免转账死锁？
4. 死锁和锁等待超时有什么区别？
5. 应用层如何处理死锁异常？

## 第24天：binlog / redo log / undo log 协作

本日掌握：三大日志职责、WAL、回滚、一致性复制基础；覆盖原理点：P12,P13；阶段：原理期

### 🟢 基础用法题

#### 题目1：查看日志相关配置

```sql
SHOW VARIABLES LIKE 'log_bin';
SHOW VARIABLES LIKE 'binlog_format';
SHOW VARIABLES LIKE 'innodb_flush_log_at_trx_commit';
SHOW VARIABLES LIKE 'sync_binlog';
```

**输出示例**：

```text
log_bin=ON
binlog_format=ROW
innodb_flush_log_at_trx_commit=1
sync_binlog=1
```

**🔍 反思**：binlog 服务复制与恢复，redo 保证崩溃恢复，undo 支持回滚和 MVCC。

**💬 追问**：为什么 binlog 不属于 InnoDB 专有日志？

### 🟡 中级用法题

#### 题目2：事务回滚观察 undo 效果

```sql
DROP TABLE IF EXISTS d24_t;
CREATE TABLE d24_t (id INT PRIMARY KEY, val INT);
INSERT INTO d24_t VALUES (1,10);

START TRANSACTION;
UPDATE d24_t SET val=20 WHERE id=1;
SELECT * FROM d24_t;
ROLLBACK;
SELECT * FROM d24_t;
```

**关键中间状态**：

```text
事务内看到 val=20
ROLLBACK 后 val 回到 10
```

**🔍 反思**：undo 记录让数据库知道如何撤销修改。

**💬 追问**：undo 只用于回滚吗？

### 🔴 高级/探索用法题

#### 题目3：查看 binlog 事件

```sql
SHOW BINARY LOGS;
SHOW MASTER STATUS;
SHOW BINLOG EVENTS LIMIT 10;
```

**逐步结果示例**：

```text
binlog 文件名
当前位置 Position
事件类型 Query/Rows_query/Table_map/Write_rows 等
```

**🔍 反思**：ROW 格式更利于复制一致性，但日志体积可能更大。

**💬 追问**：STATEMENT、ROW、MIXED 格式各有什么取舍？

### ⚙️ 性能压测/证据题

| 配置 | 持久性 | 写入性能 | 风险 |
|---|---|---:|---|
| flush=1 sync=1 | 高 | 较低 | 最小 |
| flush=2 sync=0 | 中 | 高 | 掉电可能丢日志 |

```sql
SHOW GLOBAL STATUS LIKE 'Innodb_os_log_written';
SHOW GLOBAL STATUS LIKE 'Binlog_cache%';
```

**🔍 反思**：日志刷盘策略本质是性能与持久性的交换。

**💬 追问**：金融场景如何选择这些参数？

### 🔷 原理剖析题

**题目：一次 UPDATE 的日志角色**

```sql
START TRANSACTION;
UPDATE d24_t SET val=val+1 WHERE id=1;
COMMIT;
```

**探针日志示例**：

```text
undo: 记录旧值用于回滚
redo: 记录页修改用于崩溃恢复
binlog: 记录逻辑变更用于复制/恢复
```

**🔍 反思**：三种日志分别面向回滚、崩溃恢复、复制归档。

**💬 追问**：为什么 redo 是物理日志，binlog 更偏逻辑日志？

### 🏢 大厂面试场景实战

**场景**：误删数据后如何通过 binlog 恢复？

```bash
mysqlbinlog --start-datetime="2026-05-10 10:00:00" --stop-datetime="2026-05-10 10:10:00" mysql-bin.000123 > recover.sql
```

**🔍 反思**：恢复前要确认备份点、binlog 位点、误操作时间和回放范围。

**💬 追问**：ROW 格式下如何反向生成补偿 SQL？

### 🎯 今日高频面试题速览

1. redo log、undo log、binlog 分别做什么？
2. 什么是 WAL？
3. undo log 和 MVCC 有什么关系？
4. binlog 三种格式区别是什么？
5. 刷盘参数如何影响性能和可靠性？

## 第25天：两阶段提交与 crash recovery

本日掌握：redo 与 binlog 一致提交、prepare/commit、崩溃恢复判断；覆盖原理点：P14,P15；阶段：原理期

### 🟢 基础用法题

#### 题目1：观察提交相关状态

```sql
SHOW VARIABLES LIKE 'sync_binlog';
SHOW VARIABLES LIKE 'innodb_flush_log_at_trx_commit';
SHOW GLOBAL STATUS LIKE 'Com_commit';
SHOW GLOBAL STATUS LIKE 'Com_rollback';
```

**输出示例**：提交和回滚计数持续变化。

**🔍 反思**：提交并不只是执行完 SQL，还涉及日志一致落盘。

**💬 追问**：为什么不能只写 redo 不写 binlog？

### 🟡 中级用法题

#### 题目2：事务提交路径演示

```sql
DROP TABLE IF EXISTS d25_order;
CREATE TABLE d25_order (id INT PRIMARY KEY, status VARCHAR(20));
INSERT INTO d25_order VALUES (1,'INIT');

START TRANSACTION;
UPDATE d25_order SET status='PAID' WHERE id=1;
COMMIT;

SELECT * FROM d25_order;
```

**关键中间状态**：

```text
redo prepare
写 binlog
redo commit
事务对外提交成功
```

**🔍 反思**：两阶段提交确保引擎日志和 Server 层 binlog 状态一致。

**💬 追问**：如果写完 binlog 后崩溃，恢复时如何判断？

### 🔴 高级/探索用法题

#### 题目3：分析崩溃点恢复矩阵

```text
场景A: redo prepare 前崩溃 -> 回滚
场景B: redo prepare 后、binlog 前崩溃 -> 回滚
场景C: binlog 写完、redo commit 前崩溃 -> 提交
场景D: redo commit 后崩溃 -> 提交
```

**编码要求**：把上述矩阵写进故障演练文档，并关联 binlog 位点。

```sql
SHOW MASTER STATUS;
```

**🔍 反思**：恢复逻辑的核心是根据 redo prepare 与 binlog 完整性判断事务命运。

**💬 追问**：binlog 写入成功但未 fsync，掉电风险由哪个参数控制？

### ⚙️ 性能压测/证据题

| sync_binlog | innodb_flush_log_at_trx_commit | TPS | 最坏丢失风险 |
|---:|---:|---:|---|
| 1 | 1 | 低 | 最低 |
| 100 | 2 | 高 | 可能丢最近事务 |

```sql
SHOW GLOBAL STATUS LIKE 'Binlog_commits';
SHOW GLOBAL STATUS LIKE 'Innodb_log_waits';
```

**🔍 反思**：高可靠和高吞吐往往要通过组提交、硬件和参数共同平衡。

**💬 追问**：如何在压测中评估组提交收益？

### 🔷 原理剖析题

**题目：两阶段提交时序**

```text
1. InnoDB 写 redo prepare
2. Server 写 binlog
3. InnoDB 写 redo commit
4. 返回客户端成功
```

**探针日志示例**：

```sql
SHOW ENGINE INNODB STATUS\G
SHOW MASTER STATUS;
```

**🔍 反思**：两阶段提交是单机内跨组件一致性的工程解法。

**💬 追问**：它和分布式事务二阶段提交有什么相似与不同？

### 🏢 大厂面试场景实战

**场景**：MySQL 异常宕机重启后，如何确认数据是否恢复一致？

```sql
CHECK TABLE d25_order;
SHOW MASTER STATUS;
SHOW SLAVE STATUS\G
SELECT * FROM d25_order WHERE id=1;
```

**🔍 反思**：恢复后要查错误日志、主从位点、核心业务校验 SQL。

**💬 追问**：如果主库恢复但从库位点不一致怎么办？

### 🎯 今日高频面试题速览

1. 什么是两阶段提交？
2. redo prepare 后崩溃如何恢复？
3. binlog 和 redo 为什么要保持一致？
4. `sync_binlog` 控制什么？
5. crash recovery 的核心判断是什么？

## 第26天：主从复制原理与延迟分析

本日掌握：复制链路、位点、延迟、半同步；覆盖原理点：P16,P17；阶段：原理期

### 🟢 基础用法题

#### 题目1：查看复制状态

```sql
SHOW SLAVE STATUS\G
-- MySQL 8.0.22+ 也可能使用:
SHOW REPLICA STATUS\G
```

**输出示例**：

```text
Slave_IO_Running: Yes
Slave_SQL_Running: Yes
Seconds_Behind_Master: 0
Relay_Master_Log_File: mysql-bin.000123
Exec_Master_Log_Pos: 456789
```

**🔍 反思**：复制健康至少要看 IO 线程、SQL 线程和延迟。

**💬 追问**：`Seconds_Behind_Master=0` 一定代表无延迟吗？

### 🟡 中级用法题

#### 题目2：定位复制中断原因

```sql
SHOW REPLICA STATUS\G
SELECT LAST_ERROR_NUMBER, LAST_ERROR_MESSAGE
FROM performance_schema.replication_applier_status_by_worker;
```

**关键中间状态**：

```text
IO 线程失败: 常见网络/权限/位点问题
SQL 线程失败: 常见主键冲突/DDL 不一致
```

**🔍 反思**：复制故障要先区分拉日志失败还是应用日志失败。

**💬 追问**：跳过错误事务有什么风险？

### 🔴 高级/探索用法题

#### 题目3：GTID 与位点迁移演练

```sql
SHOW VARIABLES LIKE 'gtid_mode';
SHOW MASTER STATUS;
SELECT @@GLOBAL.gtid_executed;
```

**逐步结果示例**：

```text
file/pos 模式依赖文件与位置
GTID 模式以全局事务 ID 标识已执行事务集合
```

**🔍 反思**：GTID 简化主从切换和故障恢复，但要求运维规范更严格。

**💬 追问**：GTID_PURGED 设置错误会造成什么后果？

### ⚙️ 性能压测/证据题

| 负载 | 主库 TPS | 从库延迟 | 主要原因 |
|---|---:|---:|---|
| 小事务 | 5000 | 1s | 应用快 |
| 大事务 | 500 | 30s | 单事务回放慢 |
| DDL | 低 | 60s+ | 元数据锁/重放阻塞 |

```sql
SHOW PROCESSLIST;
SELECT * FROM performance_schema.replication_applier_status;
```

**🔍 反思**：复制延迟常由大事务、DDL、从库资源不足、并行复制配置不当引起。

**💬 追问**：如何压测并行复制 worker 数的收益？

### 🔷 原理剖析题

**题目：复制三线程模型**

```text
主库 binlog dump 线程 -> 从库 IO 线程 -> relay log -> 从库 SQL 线程/worker
```

```sql
SHOW VARIABLES LIKE 'slave_parallel_workers';
SHOW VARIABLES LIKE 'rpl_semi_sync%';
```

**🔍 反思**：异步复制吞吐高，但主库提交成功不代表从库已应用。

**💬 追问**：半同步复制解决了什么，又没解决什么？

### 🏢 大厂面试场景实战

**场景**：用户刚下单后马上查订单，从库读不到，如何解决？

```text
方案1: 写后读主库，按 user_id 标记短时间强制走主
方案2: 等待从库追上位点后读从
方案3: 对强一致接口禁用读写分离
```

```sql
SHOW MASTER STATUS;
SHOW REPLICA STATUS\G
```

**🔍 反思**：读写分离必须对“写后读一致性”做业务兜底。

**💬 追问**：如何避免所有读都打回主库？

### 🎯 今日高频面试题速览

1. MySQL 主从复制链路是什么？
2. `Seconds_Behind_Master` 有什么局限？
3. GTID 有什么作用？
4. 复制延迟常见原因有哪些？
5. 半同步复制解决了什么问题？

## 第27天：读写分离与分库分表

本日掌握：路由规则、一致性读、水平拆分、分片键选择；覆盖原理点：P18,P19；阶段：原理期

### 🟢 基础用法题

#### 题目1：读写分离路由伪代码

```java
if (sql.startsWith("SELECT") && !forceMaster) {
  datasource = replicaPool.pick();
} else {
  datasource = master;
}
```

**输出日志示例**：

```text
write order -> master
read order list -> replica-1
read after write -> master
```

**🔍 反思**：读写分离不是 SQL 语法，而是连接层路由策略。

**💬 追问**：哪些 SELECT 必须走主库？

### 🟡 中级用法题

#### 题目2：按 user_id 水平分表

```sql
CREATE TABLE d27_order_00 (order_id BIGINT PRIMARY KEY, user_id BIGINT, amount DECIMAL(10,2));
CREATE TABLE d27_order_01 (order_id BIGINT PRIMARY KEY, user_id BIGINT, amount DECIMAL(10,2));

-- 路由规则:
-- table_index = user_id % 2
```

```java
String table = "d27_order_" + String.format("%02d", userId % 2);
String sql = "SELECT * FROM " + table + " WHERE user_id = ?";
```

**关键中间状态**：

```text
user_id=100 -> d27_order_00
user_id=101 -> d27_order_01
```

**🔍 反思**：分片键决定大多数查询能否精准路由。

**💬 追问**：按 user_id 分片后，按 order_id 查询怎么办？

### 🔴 高级/探索用法题

#### 题目3：跨分片聚合订单总额

```sql
SELECT SUM(amount) AS total_amount FROM (
  SELECT amount FROM d27_order_00 WHERE user_id IN (100,101)
  UNION ALL
  SELECT amount FROM d27_order_01 WHERE user_id IN (100,101)
) t;
```

**逐步结果示例**：

```text
广播到多个分片
各分片返回局部数据
中间层合并聚合
```

**🔍 反思**：分库分表牺牲了单库 JOIN 和全局聚合的便利。

**💬 追问**：如何设计全局唯一订单 ID？

### ⚙️ 性能压测/证据题

| 查询类型 | 路由方式 | 分片数 | 耗时示例 |
|---|---|---:|---:|
| 按分片键查询 | 单分片 | 1 | 5ms |
| 不带分片键查询 | 广播 | 16 | 80ms |
| 跨片聚合 | 多分片合并 | 16 | 120ms |

**🔍 反思**：分库分表提升容量，但查询模式必须配合分片键。

**💬 追问**：如何评估是否真的需要分库分表？

### 🔷 原理剖析题

**题目：读写分离一致性策略**

```text
1. 写后短时间读主
2. 记录写入 binlog 位点，读前等待从库追上
3. 强一致接口禁用读从
4. 弱一致页面允许读从
```

**探针日志示例**：

```text
user=100 write at pos=456789
read requires replica exec_pos >= 456789
```

**🔍 反思**：一致性策略要按接口等级区分，不能一刀切。

**💬 追问**：如何实现位点等待的超时降级？

### 🏢 大厂面试场景实战

**场景**：订单表 20 亿行，按用户查询最多，按订单号查询也常见，如何拆？

```text
主表按 user_id 分片: order_{user_id % N}
订单号生成包含分片位: shard_id + timestamp + sequence
order_id 查询先解析 shard_id 精准路由
```

```sql
SELECT * FROM order_07 WHERE order_id = ?;
SELECT * FROM order_07 WHERE user_id = ? ORDER BY created_at DESC LIMIT 20;
```

**🔍 反思**：好的分片方案让核心查询都能精准路由。

**💬 追问**：跨用户商家后台查询如何支持？

### 🎯 今日高频面试题速览

1. 读写分离有什么一致性问题？
2. 分片键如何选择？
3. 水平分表和垂直分库有什么区别？
4. 跨分片 JOIN 如何处理？
5. 全局唯一 ID 如何设计？

## 第28天：一条 SELECT 的完整执行流程

本日掌握：连接器、解析器、优化器、执行器、存储引擎调用；覆盖原理点：P20；阶段：原理期

### 🟢 基础用法题

#### 题目1：用 EXPLAIN 串起 SELECT 流程

```sql
DROP TABLE IF EXISTS d28_users;
CREATE TABLE d28_users (
  id INT PRIMARY KEY,
  city VARCHAR(20),
  age INT,
  KEY idx_city_age(city, age)
);
INSERT INTO d28_users VALUES (1,'北京',20),(2,'北京',30),(3,'上海',25);

EXPLAIN SELECT id, city, age
FROM d28_users
WHERE city='北京' AND age > 18
ORDER BY age;
```

**输出示例**：`key=idx_city_age`，`type=range/ref`。

**🔍 反思**：执行流程不是按 SQL 书写顺序机械运行，优化器会选择访问路径。

**💬 追问**：SQL 语法错误在哪个阶段发现？

### 🟡 中级用法题

#### 题目2：观察优化器选择的 key parts

```sql
EXPLAIN FORMAT=JSON
SELECT id, city, age
FROM d28_users
WHERE city='北京' AND age > 18
ORDER BY age;
```

**关键中间状态**：

```text
解析: 生成语法树
优化: 选择 idx_city_age
执行: 调用 InnoDB 读取索引范围
返回: 发送结果给客户端
```

**🔍 反思**：JSON 计划比传统 EXPLAIN 更能看到优化器判断。

**💬 追问**：优化器为什么可能选择全表扫描？

### 🔴 高级/探索用法题

#### 题目3：派生表与条件下推观察

```sql
EXPLAIN FORMAT=JSON
SELECT *
FROM (
  SELECT id, city, age FROM d28_users WHERE age > 18
) t
WHERE city = '北京';
```

**逐步结果示例**：

```text
优化器可能合并派生表
city 条件可能被下推到内层
```

**🔍 反思**：SQL 写法影响优化空间，简单派生表可能被优化器改写。

**💬 追问**：哪些复杂派生表不容易被合并？

### ⚙️ 性能压测/证据题

| 写法 | 是否可下推 | rows | 耗时 |
|---|---|---:|---:|
| 简单派生表 | 可能 | 低 | 快 |
| 聚合派生表 | 较难 | 中 | 中 |

```sql
EXPLAIN ANALYZE SELECT id FROM d28_users WHERE city='北京' AND age>18;
```

**🔍 反思**：最终证据以真实执行耗时和行数为准。

**💬 追问**：为什么 `EXPLAIN` 估算行数和实际可能不同？

### 🔷 原理剖析题

**题目：SELECT 执行链路**

```text
客户端 -> 连接器 -> 解析器 -> 预处理器 -> 优化器 -> 执行器 -> InnoDB -> 返回结果
```

**探针日志示例**：

```sql
SHOW SESSION STATUS LIKE 'Com_select';
SHOW SESSION STATUS LIKE 'Handler_read%';
```

**🔍 反思**：Handler 状态能侧面反映引擎读取行为。

**💬 追问**：Server 层和存储引擎层职责如何划分？

### 🏢 大厂面试场景实战

**场景**：解释 `SELECT ... WHERE ... ORDER BY ... LIMIT` 在 MySQL 中大致如何执行。

```sql
EXPLAIN FORMAT=JSON
SELECT id, city, age
FROM d28_users
WHERE city='北京'
ORDER BY age
LIMIT 10;
```

**🔍 反思**：面试回答要覆盖解析、优化、索引扫描、排序/避免排序、返回。

**💬 追问**：如果没有合适索引，排序在哪里发生？

### 🎯 今日高频面试题速览

1. 一条 SELECT 经过哪些组件？
2. 优化器主要做什么？
3. 执行器和存储引擎如何交互？
4. `Handler_read%` 指标有什么用？
5. filesort 是否一定落盘？

## 第29天：一条 UPDATE 的完整执行流程

本日掌握：UPDATE 的读、锁、改、写日志、提交；覆盖原理点：P21；阶段：原理期

### 🟢 基础用法题

#### 题目1：更新订单状态

```sql
DROP TABLE IF EXISTS d29_orders;
CREATE TABLE d29_orders (
  id INT PRIMARY KEY,
  status VARCHAR(20),
  amount INT,
  updated_at DATETIME
);
INSERT INTO d29_orders VALUES (1,'INIT',100,NULL);

UPDATE d29_orders
SET status='PAID', updated_at=NOW()
WHERE id=1;

SELECT * FROM d29_orders;
```

**输出示例**：订单状态变为 `PAID`。

**🔍 反思**：UPDATE 先找到行，再加锁修改，并产生日志。

**💬 追问**：`WHERE` 条件不走索引时可能锁多少行？

### 🟡 中级用法题

#### 题目2：安全扣减库存

```sql
DROP TABLE IF EXISTS d29_stock;
CREATE TABLE d29_stock (sku_id INT PRIMARY KEY, stock INT);
INSERT INTO d29_stock VALUES (1,5);

UPDATE d29_stock
SET stock = stock - 1
WHERE sku_id = 1 AND stock > 0;

SELECT ROW_COUNT() AS affected_rows;
```

**关键中间状态**：

```text
affected_rows=1 表示扣减成功
affected_rows=0 表示库存不足或商品不存在
```

**🔍 反思**：条件更新可以把校验和修改合并为一次原子操作。

**💬 追问**：为什么先 SELECT 再 UPDATE 在高并发下可能超卖？

### 🔴 高级/探索用法题

#### 题目3：批量更新与事务边界

```sql
START TRANSACTION;
UPDATE d29_orders SET status='CLOSED'
WHERE status='INIT' AND updated_at IS NULL
LIMIT 100;
SELECT ROW_COUNT() AS closed_cnt;
COMMIT;
```

**逐步结果示例**：

```text
每批最多更新100行
减少单次事务持锁时间
便于循环处理大批量任务
```

**🔍 反思**：大 UPDATE 拆批能降低锁等待、日志压力和回滚成本。

**💬 追问**：批量更新没有稳定排序时会有什么问题？

### ⚙️ 性能压测/证据题

| 方案 | 单事务行数 | 锁持有时间 | 回滚风险 |
|---|---:|---:|---|
| 一次更新 100万 | 高 | 长 | 高 |
| 每批 1000 | 中 | 短 | 低 |

```sql
SHOW GLOBAL STATUS LIKE 'Innodb_rows_updated';
SHOW GLOBAL STATUS LIKE 'Innodb_os_log_written';
```

**🔍 反思**：写操作慢常和锁、redo、binlog、脏页刷新相关。

**💬 追问**：如何确定最佳批大小？

### 🔷 原理剖析题

**题目：UPDATE 内部路径**

```text
定位记录 -> 加当前读锁 -> 写 undo -> 修改内存页 -> 写 redo -> 写 binlog -> 两阶段提交
```

```sql
EXPLAIN UPDATE d29_stock SET stock=stock-1 WHERE sku_id=1 AND stock>0;
```

**🔍 反思**：UPDATE 是读路径、锁系统和日志系统的组合题。

**💬 追问**：为什么 UPDATE 即使修改很少数据也可能很慢？

### 🏢 大厂面试场景实战

**场景**：秒杀扣库存 SQL 如何写，如何防止超卖？

```sql
UPDATE stock
SET stock = stock - 1
WHERE sku_id = ? AND stock > 0;
-- 应用层判断 affected_rows == 1 才算成功
```

**🔍 反思**：单行条件更新天然具备原子性，但热点行会带来排队。

**💬 追问**：热点行排队严重时怎么办？第32天深入。

### 🎯 今日高频面试题速览

1. UPDATE 大致执行流程是什么？
2. 条件扣库存为什么能防止超卖？
3. 大事务有什么风险？
4. UPDATE 不走索引会有什么锁风险？
5. 为什么要检查 `ROW_COUNT()`？

## 第30天：综合实战：慢查询优化全流程

本日掌握：从慢日志到执行计划、索引设计、SQL 改写、验证闭环；覆盖原理点：P22；阶段：原理期

### 🟢 基础用法题

#### 题目1：建立慢查询案例

```sql
DROP TABLE IF EXISTS d30_orders;
CREATE TABLE d30_orders (
  id BIGINT PRIMARY KEY,
  user_id BIGINT,
  status VARCHAR(20),
  amount DECIMAL(10,2),
  created_at DATETIME
);
INSERT INTO d30_orders VALUES
(1,10,'PAID',100,'2026-05-01'),(2,10,'PAID',200,'2026-05-02'),(3,11,'CANCEL',50,'2026-05-03');

EXPLAIN SELECT *
FROM d30_orders
WHERE user_id=10 AND status='PAID'
ORDER BY created_at DESC
LIMIT 20;
```

**输出示例**：无索引时 `key=NULL`。

**🔍 反思**：优化第一步是复现和测量，不是直接拍脑袋建索引。

**💬 追问**：为什么只看单次耗时不够？

### 🟡 中级用法题

#### 题目2：设计并验证联合索引

```sql
CREATE INDEX idx_user_status_time ON d30_orders(user_id, status, created_at);

EXPLAIN SELECT id, user_id, status, amount, created_at
FROM d30_orders
WHERE user_id=10 AND status='PAID'
ORDER BY created_at DESC
LIMIT 20;
```

**关键中间状态**：

```text
user_id/status 用于过滤
created_at 用于排序
LIMIT 降低返回行
```

**🔍 反思**：索引列顺序要贴合等值过滤、排序和分页。

**💬 追问**：如果查询要返回 `amount`，是否要把它加入索引？

### 🔴 高级/探索用法题

#### 题目3：SQL 改写减少回表

```sql
SELECT o.*
FROM d30_orders o
JOIN (
  SELECT id
  FROM d30_orders
  WHERE user_id=10 AND status='PAID'
  ORDER BY created_at DESC
  LIMIT 20
) x ON o.id = x.id
ORDER BY o.created_at DESC;
```

**逐步结果示例**：

```text
内层用窄索引找到 id
外层只回表 20 行
```

**🔍 反思**：延迟回表适合宽表分页和列表页。

**💬 追问**：什么情况下改写后反而更复杂且收益不明显？

### ⚙️ 性能压测/证据题

| 步骤 | key | rows | 耗时示例 |
|---|---|---:|---:|
| 原 SQL | NULL | 1000000 | 1500ms |
| 加联合索引 | idx_user_status_time | 300 | 25ms |
| 延迟回表 | idx_user_status_time | 20 | 10ms |

```sql
EXPLAIN ANALYZE
SELECT id, user_id, status, amount, created_at
FROM d30_orders
WHERE user_id=10 AND status='PAID'
ORDER BY created_at DESC
LIMIT 20;
SHOW PROFILES;
```

**🔍 反思**：优化报告必须有优化前后对照数据。

**💬 追问**：如何避免为了一个慢 SQL 建太多索引？

### 🔷 原理剖析题

**题目：索引设计黄金法则**

```text
高频优先、等值在前、范围靠后、排序复用、覆盖适度、控制索引数量
```

```sql
SHOW INDEX FROM d30_orders;
```

**🔍 反思**：索引是读性能和写成本之间的长期契约。

**💬 追问**：如何识别冗余索引？

### 🏢 大厂面试场景实战

**场景**：给你一条线上慢 SQL，要求 30 分钟内定位并优化。

```text
1. 慢日志确认 SQL、耗时、扫描行
2. EXPLAIN/EXPLAIN ANALYZE 看访问路径
3. SHOW INDEX 看现有索引
4. 根据过滤、排序、返回列设计索引或改写 SQL
5. 灰度验证耗时、CPU、IO、写入影响
```

```sql
EXPLAIN FORMAT=JSON
SELECT id, user_id, status, amount, created_at
FROM d30_orders
WHERE user_id=10 AND status='PAID'
ORDER BY created_at DESC
LIMIT 20;
SHOW INDEX FROM target_table;
```

**🔍 反思**：优化是闭环，不是只给一个索引名。

**💬 追问**：上线新索引前要评估哪些风险？

### 🎯 今日高频面试题速览

1. 慢 SQL 优化标准流程是什么？
2. 联合索引如何设计？
3. 延迟回表适合什么场景？
4. 如何验证优化真的有效？
5. 索引过多有什么副作用？

## 第31天：千万级数据分页优化

本日掌握：深分页问题、延迟关联、书签法、游标分页；覆盖原理点：P23；阶段：大厂期

### 🟢 基础用法题

#### 题目1：写出传统分页与问题样例

```sql
SELECT id, title, created_at
FROM articles
WHERE status='PUBLISHED'
ORDER BY created_at DESC, id DESC
LIMIT 20 OFFSET 1000000;
```

**输出示例**：语法正确但深页非常慢。

**🔍 反思**：OFFSET 越大，数据库需要跳过的记录越多。

**💬 追问**：为什么第 1 页快，第 50000 页慢？

### 🟡 中级用法题

#### 题目2：延迟关联分页

```sql
SELECT a.*
FROM articles a
JOIN (
  SELECT id
  FROM articles
  WHERE status='PUBLISHED'
  ORDER BY created_at DESC, id DESC
  LIMIT 20 OFFSET 1000000
) x ON a.id = x.id
ORDER BY a.created_at DESC, a.id DESC;
```

**关键中间状态**：

```text
内层尽量只扫窄索引
外层只回表 20 行
```

**🔍 反思**：延迟关联缓解宽表回表成本，但无法彻底消灭深 OFFSET。

**💬 追问**：这个方案在极深分页时还有什么瓶颈？

### 🔴 高级/探索用法题

#### 题目3：书签法游标分页

```sql
-- 第一页
SELECT id, title, created_at
FROM articles
WHERE status='PUBLISHED'
ORDER BY created_at DESC, id DESC
LIMIT 20;

-- 下一页，带上上一页最后一条的 created_at 和 id
SELECT id, title, created_at
FROM articles
WHERE status='PUBLISHED'
  AND (created_at < '2026-05-01 10:00:00'
       OR (created_at = '2026-05-01 10:00:00' AND id < 987654))
ORDER BY created_at DESC, id DESC
LIMIT 20;
```

**逐步结果示例**：

```text
last_created_at=上一页最后一条时间
last_id=上一页最后一条 id
下一页从书签后继续取
```

**🔍 反思**：游标分页牺牲跳页能力，换取稳定的下一页性能。

**💬 追问**：如果前端必须跳到第 500 页，如何折中？

### ⚙️ 性能压测/证据题

| 方案 | 深页耗时 | 是否支持任意跳页 | 适用场景 |
|---|---:|---|---|
| OFFSET | 高 | 是 | 后台小数据 |
| 延迟关联 | 中 | 是 | 宽表列表 |
| 书签法 | 低 | 否 | 信息流/滚动加载 |

```sql
CREATE INDEX idx_article_status_time_id ON articles(status, created_at, id);
EXPLAIN ANALYZE
SELECT id,title
FROM articles
WHERE status='PUBLISHED'
ORDER BY created_at DESC,id DESC
LIMIT 20 OFFSET 100000;
```

**🔍 反思**：分页方案要和交互方式一起设计。

**💬 追问**：如何压测 10 万、100 万、1000 万 offset 的曲线？

### 🔷 原理剖析题

**题目：深分页为什么慢**

```text
OFFSET N LIMIT M 通常要读取或跳过 N+M 条有序记录
如果还要回表，成本会随 N 放大
```

**探针日志示例**：`EXPLAIN ANALYZE` 中 actual rows 明显大于返回 rows。

**🔍 反思**：深分页慢的根源是“跳过也要付出代价”。

**💬 追问**：搜索引擎为什么常限制只能翻到前若干页？

### 🏢 大厂面试场景实战

**场景**：设计千万级文章列表分页，支持首页、下一页、后台跳页。

```text
前台信息流: 书签法
后台管理: 延迟关联 + 限制最大页码 + 条件筛选
热门列表: 预计算榜单缓存
索引: (status, created_at, id)
```

```sql
SELECT id,title FROM articles
WHERE status='PUBLISHED'
  AND (created_at,id) < (?,?)
ORDER BY created_at DESC,id DESC
LIMIT 20;
```

**🔍 反思**：高并发产品列表不应默认开放无限跳页。

**💬 追问**：分页过程中有新数据插入，如何避免重复或漏看？

### 🎯 今日高频面试题速览

1. 深分页为什么慢？
2. 什么是延迟关联？
3. 什么是书签法分页？
4. 游标分页有什么缺点？
5. 分页索引如何设计？

## 第32天：热点行更新与秒杀场景的行锁优化

本日掌握：热点行瓶颈、拆库存、队列化、异步扣减；覆盖原理点：P24；阶段：大厂期

### 🟢 基础用法题

#### 题目1：最小库存扣减 SQL

```sql
CREATE TABLE flash_stock (
  sku_id BIGINT PRIMARY KEY,
  stock INT
);

UPDATE flash_stock
SET stock = stock - 1
WHERE sku_id = ? AND stock > 0;
```

**输出示例**：`affected_rows=1` 成功，`0` 失败。

**🔍 反思**：单行原子扣减能防止超卖，但所有请求排队争同一行。

**💬 追问**：为什么加缓存不能完全替代数据库最终扣减？

### 🟡 中级用法题

#### 题目2：库存分桶降低热点

```sql
CREATE TABLE flash_stock_bucket (
  sku_id BIGINT,
  bucket_id INT,
  stock INT,
  PRIMARY KEY (sku_id, bucket_id)
);

UPDATE flash_stock_bucket
SET stock = stock - 1
WHERE sku_id = ?
  AND bucket_id = ?
  AND stock > 0;
```

**关键中间状态**：

```text
sku 1 拆成 100 个 bucket
请求随机或按用户 hash 命中 bucket
热点从 1 行扩散到 100 行
```

**🔍 反思**：拆行用一致性合并复杂度换并发能力。

**💬 追问**：某个 bucket 扣完但总库存还有，如何重试？

### 🔴 高级/探索用法题

#### 题目3：异步扣减与订单状态机

```sql
CREATE TABLE flash_order (
  order_id BIGINT PRIMARY KEY,
  user_id BIGINT,
  sku_id BIGINT,
  status VARCHAR(20),
  created_at DATETIME
);

-- 接入层先写入 WAIT_CONFIRM，消息队列异步扣减库存
INSERT INTO flash_order VALUES (?, ?, ?, 'WAIT_CONFIRM', NOW());

-- 消费者扣库存成功
UPDATE flash_order SET status='CONFIRMED' WHERE order_id=? AND status='WAIT_CONFIRM';
```

**逐步结果示例**：

```text
请求快速入队
消费者削峰扣库存
失败订单置为 CLOSED
```

**🔍 反思**：秒杀是数据库、缓存、队列、限流的组合题。

**💬 追问**：异步确认会带来什么用户体验问题？

### ⚙️ 性能压测/证据题

| 方案 | 并发 | 成功 TPS | 锁等待 |
|---|---:|---:|---:|
| 单行库存 | 1000 | 低 | 高 |
| 100 桶库存 | 1000 | 中高 | 中 |
| 队列削峰 | 1000 | 稳定 | 低 |

**🔍 反思**：热点优化核心是减少同一行的同步竞争。

**💬 追问**：如何压测桶数从 10 到 1000 的收益递减？

### 🔷 原理剖析题

**题目：热点行为什么慢**

```sql
SELECT * FROM performance_schema.data_lock_waits;
```

**探针日志示例**：

```text
大量事务等待同一 PRIMARY key record lock
```

**🔍 反思**：行锁粒度已经很小，但热点集中时仍然会串行。

**💬 追问**：拆库存如何保证最终汇总不超卖？

### 🏢 大厂面试场景实战

**场景**：设计 10 万 QPS 秒杀数据库扣库存方案。

```text
入口限流 -> Redis 预扣 -> MQ 削峰 -> MySQL 分桶最终扣减 -> 订单状态机 -> 对账补偿
```

```sql
UPDATE flash_stock_bucket
SET stock=stock-1
WHERE sku_id=? AND bucket_id=? AND stock>0;
```

**🔍 反思**：数据库负责最终一致的落库，不应独自承受所有瞬时流量。

**💬 追问**：缓存预扣成功但数据库失败如何补偿？

### 🎯 今日高频面试题速览

1. 秒杀库存如何防超卖？
2. 热点行为什么会成为瓶颈？
3. 库存拆桶如何设计？
4. 异步扣减如何保证最终一致？
5. 秒杀订单状态机有哪些状态？

## 第33天：海量数据归档与分区表设计

本日掌握：冷热分离、分区表、归档批处理、在线清理；覆盖原理点：P25；阶段：大厂期

### 🟢 基础用法题

#### 题目1：创建按月 RANGE 分区订单表

```sql
CREATE TABLE archive_orders (
  id BIGINT NOT NULL,
  created_at DATE NOT NULL,
  amount DECIMAL(10,2),
  PRIMARY KEY (id, created_at)
)
PARTITION BY RANGE COLUMNS(created_at) (
  PARTITION p202601 VALUES LESS THAN ('2026-02-01'),
  PARTITION p202602 VALUES LESS THAN ('2026-03-01'),
  PARTITION pmax VALUES LESS THAN (MAXVALUE)
);
```

**输出示例**：表创建成功。

**🔍 反思**：分区键通常要出现在唯一键中。

**💬 追问**：按月分区适合哪些查询？

### 🟡 中级用法题

#### 题目2：查看分区裁剪

```sql
EXPLAIN SELECT *
FROM archive_orders
WHERE created_at >= '2026-02-01'
  AND created_at < '2026-03-01';
```

**关键中间状态**：

```text
partitions 只出现 p202602
说明分区裁剪生效
```

**🔍 反思**：查询条件必须包含分区键，分区才更容易发挥作用。

**💬 追问**：分区表是否等于自动提升所有查询性能？

### 🔴 高级/探索用法题

#### 题目3：批量归档历史订单

```sql
CREATE TABLE archive_orders_history LIKE archive_orders;

INSERT INTO archive_orders_history
SELECT * FROM archive_orders
WHERE created_at < '2026-02-01'
LIMIT 1000;

DELETE FROM archive_orders
WHERE created_at < '2026-02-01'
LIMIT 1000;
```

**逐步结果示例**：

```text
每批搬迁1000行
校验数量和金额
再删除源表数据
```

**🔍 反思**：归档要小批量、可重入、可校验。

**💬 追问**：为什么大 DELETE 会造成严重日志和锁压力？

### ⚙️ 性能压测/证据题

| 清理方式 | 删除 1 月数据耗时 | 锁影响 | 回滚成本 |
|---|---:|---|---|
| DELETE 大批量 | 高 | 高 | 高 |
| 小批 DELETE | 中 | 中 | 中 |
| DROP PARTITION | 低 | 低 | 低 |

```sql
ALTER TABLE archive_orders DROP PARTITION p202601;
```

**🔍 反思**：按分区清理比逐行删除更适合历史数据淘汰。

**💬 追问**：DROP PARTITION 前如何确保数据已归档？

### 🔷 原理剖析题

**题目：分区表类型选择**

```text
RANGE: 时间归档
HASH: 均匀打散
LIST: 枚举区域
KEY: MySQL 内置 hash
```

```sql
SELECT PARTITION_NAME, TABLE_ROWS
FROM information_schema.PARTITIONS
WHERE TABLE_NAME='archive_orders';
```

**🔍 反思**：分区是管理手段，不是分库分表的完全替代。

**💬 追问**：分区过多有什么问题？

### 🏢 大厂面试场景实战

**场景**：订单表 50 亿行，只查最近 6 个月，历史需要可审计归档。

```text
在线表保留 6-12 个月 RANGE 分区
历史表/对象存储归档
归档任务按分区校验 count、sum(amount)
确认后 DROP PARTITION
审计查询走历史库
```

```sql
SELECT COUNT(*), SUM(amount)
FROM archive_orders
WHERE created_at >= '2026-01-01' AND created_at < '2026-02-01';
```

**🔍 反思**：归档方案必须同时考虑查询性能、存储成本、审计可查。

**💬 追问**：归档过程中业务仍在写入怎么办？

### 🎯 今日高频面试题速览

1. MySQL 分区表有哪些类型？
2. 分区裁剪是什么？
3. 为什么大 DELETE 风险高？
4. DROP PARTITION 适合什么场景？
5. 分区表和分库分表有什么区别？

## 第34天：异构数据同步（Canal + 消息队列）

本日掌握：CDC、binlog 订阅、幂等消费、位点恢复；覆盖原理点：P26；阶段：大厂期

### 🟢 基础用法题

#### 题目1：准备可同步的订单表

```sql
CREATE TABLE sync_orders (
  id BIGINT PRIMARY KEY,
  user_id BIGINT,
  status VARCHAR(20),
  amount DECIMAL(10,2),
  updated_at DATETIME
);

INSERT INTO sync_orders VALUES (1,100,'PAID',99.00,NOW());
UPDATE sync_orders SET status='REFUND', updated_at=NOW() WHERE id=1;
```

**输出示例**：Canal 可捕获 INSERT 与 UPDATE 行变更。

**🔍 反思**：CDC 同步依赖数据库变更日志，而不是定时全表扫。

**💬 追问**：为什么建议 binlog 使用 ROW 格式？

### 🟡 中级用法题

#### 题目2：消费者幂等写入搜索索引表

```sql
CREATE TABLE search_order_doc (
  id BIGINT PRIMARY KEY,
  user_id BIGINT,
  status VARCHAR(20),
  amount DECIMAL(10,2),
  version BIGINT
);

INSERT INTO search_order_doc(id,user_id,status,amount,version)
VALUES (1,100,'REFUND',99.00,12345)
ON DUPLICATE KEY UPDATE
  status=VALUES(status),
  amount=VALUES(amount),
  version=GREATEST(version, VALUES(version));
```

**关键中间状态**：

```text
重复消息到达
主键冲突触发更新
version 防止旧消息覆盖新消息
```

**🔍 反思**：消息系统默认要按“至少一次”处理，消费者必须幂等。

**💬 追问**：乱序消息如何处理？

### 🔴 高级/探索用法题

#### 题目3：位点表设计

```sql
CREATE TABLE cdc_checkpoint (
  consumer_name VARCHAR(50) PRIMARY KEY,
  binlog_file VARCHAR(100),
  binlog_pos BIGINT,
  gtid_set TEXT,
  updated_at DATETIME
);

UPDATE cdc_checkpoint
SET binlog_file='mysql-bin.000123',
    binlog_pos=456789,
    updated_at=NOW()
WHERE consumer_name='order-search-sync';
```

**逐步结果示例**：

```text
消费一批消息
业务写入成功
提交 MQ offset 和 binlog checkpoint
失败则从 checkpoint 继续
```

**🔍 反思**：位点是 CDC 系统故障恢复的生命线。

**💬 追问**：先提交 MQ offset 再写业务表会有什么风险？

### ⚙️ 性能压测/证据题

| 批大小 | 吞吐 | 延迟 | 失败重放成本 |
|---:|---:|---:|---:|
| 1 | 低 | 低 | 低 |
| 100 | 中 | 中 | 中 |
| 1000 | 高 | 高 | 高 |

**🔍 反思**：CDC 消费在吞吐、延迟、失败恢复之间取平衡。

**💬 追问**：如何压测消息堆积到恢复完成的时间？

### 🔷 原理剖析题

**题目：Canal 模拟从库订阅 binlog**

```text
Canal 伪装成 MySQL slave
向 master dump binlog
解析 ROW event
投递到 MQ 或直接调用消费者
```

**探针日志示例**：

```text
destination=order
binlog=mysql-bin.000123:456789
event=UPDATE sync_orders id=1
```

**🔍 反思**：异构同步的难点不在捕获，而在顺序、幂等、延迟、补偿。

**💬 追问**：DDL 变更如何同步到下游？

### 🏢 大厂面试场景实战

**场景**：订单 MySQL 同步到 Elasticsearch，要求 5 秒内可搜索。

```text
MySQL ROW binlog -> Canal -> MQ -> 消费者批量写 ES
幂等键 order_id
版本号使用 binlog pos 或 updated_at
失败进入重试队列
定时全量校验补偿
```

```sql
SELECT id, status, updated_at
FROM sync_orders
WHERE updated_at >= NOW() - INTERVAL 10 MINUTE;
```

**🔍 反思**：近实时搜索通常接受短暂延迟，但必须能补偿丢失和乱序。

**💬 追问**：如果 MQ 积压 1 小时，业务如何降级？

### 🎯 今日高频面试题速览

1. 什么是 CDC？
2. Canal 如何订阅 MySQL 变更？
3. 消费者为什么要幂等？
4. 如何处理消息乱序？
5. 位点恢复如何设计？

## 第35天：分布式事务（XA 或 TCC）在 MySQL 上的实现

本日掌握：XA、TCC、最终一致、补偿、事务消息；覆盖原理点：P27；阶段：大厂期

### 🟢 基础用法题

#### 题目1：XA 事务基本语法

```sql
XA START 'xid-1001';
UPDATE account_a SET balance = balance - 100 WHERE id = 1;
XA END 'xid-1001';
XA PREPARE 'xid-1001';
XA COMMIT 'xid-1001';
```

**输出示例**：XA 事务提交成功。

**🔍 反思**：XA 把单库事务扩展到可被协调器管理的 prepare/commit。

**💬 追问**：XA prepared 状态长时间不提交有什么风险？

### 🟡 中级用法题

#### 题目2：TCC Try 阶段冻结余额

```sql
CREATE TABLE tcc_account (
  id BIGINT PRIMARY KEY,
  balance INT,
  frozen INT
);

UPDATE tcc_account
SET balance = balance - 100,
    frozen = frozen + 100
WHERE id = 1 AND balance >= 100;
```

**关键中间状态**：

```text
Try: 冻结资源
Confirm: 扣除冻结
Cancel: 释放冻结
```

**🔍 反思**：TCC 把资源预留和最终确认拆开，业务侵入更强。

**💬 追问**：Cancel 先于 Try 到达怎么办？

### 🔴 高级/探索用法题

#### 题目3：TCC 幂等控制表

```sql
CREATE TABLE tcc_action_log (
  xid VARCHAR(64),
  branch_id VARCHAR(64),
  action VARCHAR(20),
  status VARCHAR(20),
  PRIMARY KEY (xid, branch_id, action)
);

INSERT IGNORE INTO tcc_action_log VALUES ('x1','b1','CONFIRM','DONE');
SELECT ROW_COUNT() AS first_execute;
```

**逐步结果示例**：

```text
第一次插入 ROW_COUNT=1
重复 Confirm ROW_COUNT=0，直接返回成功
```

**🔍 反思**：分布式事务里，幂等、空回滚、悬挂处理和业务 SQL 一样重要。

**💬 追问**：如何识别“悬挂”请求？

### ⚙️ 性能压测/证据题

| 模式 | 一致性 | 性能 | 业务侵入 |
|---|---|---:|---|
| XA | 强 | 低 | 低 |
| TCC | 最终/强业务语义 | 中 | 高 |
| 事务消息 | 最终一致 | 高 | 中 |

**🔍 反思**：分布式事务没有银弹，交易核心和普通业务可选不同策略。

**💬 追问**：如何压测 XA prepared 锁持有时间？

### 🔷 原理剖析题

**题目：XA 二阶段提交**

```text
阶段1: prepare，各资源管理器保证可以提交
阶段2: commit/rollback，由协调器统一决策
```

```sql
XA RECOVER;
```

**🔍 反思**：XA 牺牲可用性和性能换取跨资源强一致。

**💬 追问**：协调器宕机后如何处理 prepared 事务？

### 🏢 大厂面试场景实战

**场景**：下单要同时扣库存、扣余额、创建订单，如何保证一致？

```text
强一致: XA，适合低并发核心账务
高并发: TCC，库存 Try 冻结、余额 Try 冻结、订单 Try 预创建
最终一致: 本地事务 + 消息 + 补偿
```

```sql
UPDATE stock SET available=available-1, frozen=frozen+1 WHERE sku_id=? AND available>0;
UPDATE tcc_account SET balance=balance-?, frozen=frozen+? WHERE id=? AND balance>=?;
```

**🔍 反思**：方案选择要看一致性等级、吞吐、失败补偿能力。

**💬 追问**：如何设计超时未 Confirm 的补偿任务？

### 🎯 今日高频面试题速览

1. XA 两阶段提交流程是什么？
2. TCC 的 Try/Confirm/Cancel 分别做什么？
3. 什么是空回滚、悬挂、幂等？
4. XA 和 TCC 如何选择？
5. 本地事务 + 消息如何保证最终一致？

## 第36天：备库重建与数据一致性校验

本日掌握：备份恢复、复制重建、checksum 校验、修复差异；覆盖原理点：P28；阶段：大厂期

### 🟢 基础用法题

#### 题目1：逻辑备份恢复命令

```bash
mysqldump --single-transaction --master-data=2 --databases appdb > appdb.sql
mysql < appdb.sql
```

**输出示例**：备份文件包含一致性快照和主库位点注释。

**🔍 反思**：`--single-transaction` 适合 InnoDB 一致性备份。

**💬 追问**：为什么备份期间仍要关注 DDL？

### 🟡 中级用法题

#### 题目2：重建从库复制关系

```sql
CHANGE MASTER TO
  MASTER_HOST='10.0.0.1',
  MASTER_USER='repl',
  MASTER_PASSWORD='******',
  MASTER_LOG_FILE='mysql-bin.000123',
  MASTER_LOG_POS=456789;
START SLAVE;
SHOW SLAVE STATUS\G
```

**关键中间状态**：

```text
恢复数据快照
设置主库位点
启动复制
检查 IO/SQL 线程
```

**🔍 反思**：重建从库最怕快照和位点不匹配。

**💬 追问**：GTID 模式下重建有什么不同？

### 🔴 高级/探索用法题

#### 题目3：校验主从数据一致性

```bash
pt-table-checksum --host=master --user=checksum --password=xxx --databases=appdb
pt-table-sync --print --sync-to-master h=replica,u=checksum,p=xxx,D=appdb,t=orders
```

**逐步结果示例**：

```text
checksum 表记录每个 chunk 的校验值
发现差异后 pt-table-sync 生成修复 SQL
先 --print 审核，再执行
```

**🔍 反思**：一致性校验要低峰执行，防止影响线上。

**💬 追问**：校验过程中数据持续写入怎么办？

### ⚙️ 性能压测/证据题

| 方法 | 速度 | 锁影响 | 适用 |
|---|---:|---|---|
| mysqldump | 慢 | 低/中 | 小中库 |
| xtrabackup | 快 | 低 | 大库 |
| 快照 + binlog | 快 | 依赖存储 | 云盘 |

**🔍 反思**：备份方案要定期恢复演练，否则备份等于没备。

**💬 追问**：如何衡量 RPO 和 RTO？

### 🔷 原理剖析题

**题目：一致性备份与恢复链路**

```text
全量备份 + binlog 增量 -> 指定时间点恢复 -> 校验核心表 -> 重建复制
```

```sql
SHOW MASTER STATUS;
SHOW BINARY LOGS;
```

**🔍 反思**：备份恢复是“数据快照 + 变更日志”的组合。

**💬 追问**：误删后如何恢复到误删前一秒？

### 🏢 大厂面试场景实战

**场景**：从库磁盘损坏，需要 2 小时内重建可用只读库。

```text
1. 选择最近物理备份
2. 恢复到新机器
3. 根据备份位点追 binlog
4. 启动复制
5. 校验核心表 checksum
6. 接入读流量灰度
```

```sql
SHOW REPLICA STATUS\G
SELECT COUNT(*), SUM(amount) FROM orders WHERE created_at >= CURDATE();
```

**🔍 反思**：重建流程要预案化，否则事故时会被细节拖垮。

**💬 追问**：如果主库 binlog 已过期怎么办？

### 🎯 今日高频面试题速览

1. 逻辑备份和物理备份区别是什么？
2. `--single-transaction` 有什么作用？
3. 如何重建从库？
4. 如何校验主从一致？
5. RPO 和 RTO 分别是什么？

## 第37天：多机房部署下的主从延迟解决方案

本日掌握：跨机房复制、延迟读、就近访问、故障切换；覆盖原理点：P29；阶段：大厂期

### 🟢 基础用法题

#### 题目1：记录写入位点用于读一致性

```sql
SHOW MASTER STATUS;
-- 应用把 File/Position 或 GTID 绑定到用户会话
```

**输出示例**：`mysql-bin.000123:456789`。

**🔍 反思**：写后读一致性需要知道“读库是否追到写入点”。

**💬 追问**：跨机房网络延迟会影响哪些指标？

### 🟡 中级用法题

#### 题目2：读前等待从库追位点

```text
if replica.exec_pos >= required_pos:
  read replica
else if wait < 50ms:
  wait and retry
else:
  read master or local fallback
```

**关键中间状态**：

```text
强一致查询: 等位点或读主
弱一致查询: 直接读就近从库
```

**🔍 反思**：不是所有读都需要同样一致性。

**💬 追问**：等待位点会如何影响接口 P99？

### 🔴 高级/探索用法题

#### 题目3：多机房读路由策略表

```sql
CREATE TABLE dc_read_policy (
  api_name VARCHAR(100) PRIMARY KEY,
  consistency_level VARCHAR(20),
  max_replica_lag_ms INT,
  fallback VARCHAR(20)
);
INSERT INTO dc_read_policy VALUES
('get_order_detail','strong',50,'MASTER'),
('list_hot_products','weak',5000,'LOCAL_REPLICA');
```

**逐步结果示例**：

```text
订单详情强一致
热门商品弱一致
根据 API 策略路由
```

**🔍 反思**：一致性策略产品化后，系统才可治理。

**💬 追问**：如何动态摘除延迟过高的从库？

### ⚙️ 性能压测/证据题

| 路由策略 | 平均延迟 | P99 | 一致性 |
|---|---:|---:|---|
| 全部读主 | 高 | 高 | 强 |
| 全部读本地从 | 低 | 低 | 弱 |
| 按接口分级 | 中 | 中 | 可控 |

**🔍 反思**：跨机房架构的核心不是消灭延迟，而是管理延迟。

**💬 追问**：如何制造 100ms 网络延迟做演练？

### 🔷 原理剖析题

**题目：跨机房复制拓扑**

```text
单主多从: 简单但远端写延迟高
同城双活: 写冲突需要解决
异地多活: 需要业务分区或冲突合并
```

**探针日志示例**：

```sql
SHOW REPLICA STATUS\G
```

**🔍 反思**：数据库跨机房强一致通常成本极高，业务常做分级一致性。

**💬 追问**：多活下自增主键冲突如何处理？

### 🏢 大厂面试场景实战

**场景**：全国用户订单系统，北京主库，上海机房读延迟偶发 5 秒，如何设计？

```text
订单写后读: 会话粘主或位点等待
列表弱一致: 就近读上海从
支付结果: 读主或强一致通道
监控: 从库延迟、位点差、网络 RTT
降级: 延迟超阈值摘除从库
```

```sql
SHOW REPLICA STATUS\G
```

**🔍 反思**：业务分级是跨机房复制延迟治理的关键。

**💬 追问**：如果北京机房故障，上海如何提升为主？

### 🎯 今日高频面试题速览

1. 跨机房主从延迟原因有哪些？
2. 写后读一致性如何保证？
3. 什么是按接口一致性分级？
4. 多机房读路由如何设计？
5. 异地多活的主要难点是什么？

## 第38天：云原生数据库（如 Aurora）的架构差异

本日掌握：存储计算分离、共享存储、日志即数据库、云数据库选型；覆盖原理点：P30；阶段：大厂期

### 🟢 基础用法题

#### 题目1：识别普通 MySQL 与云数据库能力差异

```sql
SHOW VARIABLES LIKE 'version%';
SHOW VARIABLES LIKE 'innodb_flush_log_at_trx_commit';
SHOW VARIABLES LIKE 'read_only';
```

**输出示例**：不同云厂商会返回定制版本信息。

**🔍 反思**：云数据库兼容 MySQL 语法，但底层架构可能完全不同。

**💬 追问**：兼容 MySQL 是否等于所有行为完全一致？

### 🟡 中级用法题

#### 题目2：读写节点分离连接配置

```text
writer endpoint: app-cluster.cluster-xxx.rds.amazonaws.com
reader endpoint: app-cluster.cluster-ro-xxx.rds.amazonaws.com
```

```java
DataSource writer = ds("writer-endpoint");
DataSource reader = ds("reader-endpoint");
```

**关键中间状态**：

```text
写请求走 writer
弱一致读走 reader endpoint
强一致读走 writer
```

**🔍 反思**：云数据库常把复制、故障切换、备份封装成服务能力。

**💬 追问**：reader endpoint 是否保证读到最新写入？

### 🔴 高级/探索用法题

#### 题目3：迁移兼容性检查清单

```sql
SHOW VARIABLES LIKE 'sql_mode';
SHOW VARIABLES LIKE 'lower_case_table_names';
SHOW VARIABLES LIKE 'character_set_server';
SHOW VARIABLES LIKE 'collation_server';
SHOW VARIABLES LIKE 'time_zone';
```

**逐步结果示例**：

```text
SQL mode 差异可能影响插入
大小写规则影响表名
字符集排序规则影响比较
时区影响时间字段
```

**🔍 反思**：云迁移不是只导数据，还要校验参数和行为差异。

**💬 追问**：如何验证存储过程、触发器、事件调度兼容性？

### ⚙️ 性能压测/证据题

| 架构 | 扩容方式 | 读扩展 | 恢复能力 |
|---|---|---|---|
| 自建 MySQL | 人工扩容 | 复制从库 | 依赖备份 |
| RDS | 实例规格 | 只读实例 | 自动备份 |
| Aurora 类 | 存储计算分离 | 快速加读节点 | 共享存储恢复快 |

**🔍 反思**：云原生架构优势常体现在弹性、恢复和运维托管。

**💬 追问**：如何压测云数据库读节点扩容前后吞吐？

### 🔷 原理剖析题

**题目：存储计算分离**

```text
传统 MySQL: 计算节点拥有本地存储
Aurora 类: 计算节点写日志到分布式存储，多个读节点共享底层存储
```

**探针日志示例**：

```text
failover time
replica lag
storage autoscaling event
```

**🔍 反思**：存储计算分离减少复制数据页的成本，但带来云厂商绑定和行为差异。

**💬 追问**：共享存储下读节点为什么仍可能有延迟？

### 🏢 大厂面试场景实战

**场景**：从自建 MySQL 迁到云原生数据库，怎么做技术评估？

```text
兼容性: SQL mode、函数、DDL、权限
性能: 核心 SQL 压测、连接数、事务延迟
可靠性: 备份、恢复、故障切换演练
成本: 存储、IO、读节点、跨区流量
回滚: 双写或 CDC 回切方案
```

```sql
EXPLAIN ANALYZE
SELECT user_id, COUNT(*) AS order_cnt
FROM orders
WHERE created_at >= '2026-05-01'
GROUP BY user_id
ORDER BY order_cnt DESC
LIMIT 100;
```

**🔍 反思**：选型要同时看技术、成本、运维、回滚。

**💬 追问**：如何避免迁移后被云厂商能力锁死？

### 🎯 今日高频面试题速览

1. 云原生数据库和自建 MySQL 有何区别？
2. 什么是存储计算分离？
3. reader endpoint 是否强一致？
4. 云数据库迁移前要检查哪些参数？
5. Aurora 类数据库为什么恢复快？

## 第39天：从零设计一个轻量级 ORM

本日掌握：SQL 生成、参数绑定、连接池、事务封装、MySQL 协议意识；覆盖原理点：P31；阶段：大厂期

### 🟢 基础用法题

#### 题目1：安全参数绑定查询

```java
String sql = "SELECT id, name FROM users WHERE id = ?";
PreparedStatement ps = conn.prepareStatement(sql);
ps.setLong(1, userId);
ResultSet rs = ps.executeQuery();
```

**输出日志示例**：`sql=SELECT id,name FROM users WHERE id=? params=[1001]`。

**🔍 反思**：ORM 首先要避免字符串拼接带来的 SQL 注入。

**💬 追问**：表名和列名能否用 `?` 占位？

### 🟡 中级用法题

#### 题目2：实体到 SQL 映射

```java
class User {
  Long id;
  String name;
  Integer age;
}

String sql = "INSERT INTO users(id,name,age) VALUES (?,?,?)";
bind(1, user.id);
bind(2, user.name);
bind(3, user.age);
```

**关键中间状态**：

```text
实体字段 -> 列名
字段值 -> 参数数组
执行结果 -> 影响行数
```

**🔍 反思**：轻量 ORM 的核心是元数据映射和参数绑定。

**💬 追问**：NULL 值如何绑定？

### 🔴 高级/探索用法题

#### 题目3：事务模板封装

```java
txTemplate.execute(conn -> {
  update(conn, "UPDATE account SET balance=balance-? WHERE id=?", 100, 1);
  update(conn, "UPDATE account SET balance=balance+? WHERE id=?", 100, 2);
  return true;
});
```

**逐步结果示例**：

```text
获取连接
setAutoCommit(false)
执行业务
成功 commit
异常 rollback
归还连接
```

**🔍 反思**：ORM 不能只会生成 SQL，还要正确管理连接与事务边界。

**💬 追问**：嵌套事务如何设计？

### 🔴 高级/探索用法题

#### 题目4：分页方言与 MySQL 专属 SQL

```java
String pageSql = originalSql + " LIMIT ? OFFSET ?";
params.add(pageSize);
params.add(offset);
```

**内部状态日志**：

```text
original=SELECT * FROM users WHERE city=?
page=SELECT * FROM users WHERE city=? LIMIT ? OFFSET ?
params=[北京,20,40]
```

**🔍 反思**：ORM 要隔离不同数据库方言，但不能掩盖 MySQL 的性能边界。

**💬 追问**：深分页如何在 ORM 层提供游标分页 API？

### 🔷 原理剖析题

**题目：连接池与 MySQL 协议关键点**

```text
连接建立成本高
PreparedStatement 可复用执行计划/协议包
连接池要做最大连接数、空闲检测、泄漏检测
```

**探针日志示例**：

```text
borrow connection cost=2ms
execute cost=10ms
return connection
```

**🔍 反思**：ORM 设计不好会把数据库问题放大成连接耗尽。

**💬 追问**：连接池过大为什么可能拖垮 MySQL？

### 🏢 大厂面试场景实战

**场景**：设计一个只支持 MySQL 的轻量 ORM，需要支持 CRUD、事务、分页、日志。

```text
模块: Mapper 元数据、SQL Builder、ParameterBinder、Executor、TransactionManager、ConnectionPool、SlowSqlLogger
原则: 全部参数化，默认禁止 SELECT *，慢 SQL 打印参数，分页支持游标
```

```java
User user = orm.selectOne(User.class)
  .where("id = ?", 1001)
  .execute();
```

**🔍 反思**：优秀 ORM 是把最佳实践固化，不是遮蔽数据库。

**💬 追问**：如何避免 N+1 查询？

### 🎯 今日高频面试题速览

1. ORM 如何防 SQL 注入？
2. PreparedStatement 有什么作用？
3. 连接池为什么需要上限？
4. ORM 如何管理事务？
5. N+1 查询如何发现和解决？

## 第40天：全链路压测中的数据库影子库方案

本日掌握：影子库、压测标记、流量隔离、数据清理；覆盖原理点：P32；阶段：大厂期

### 🟢 基础用法题

#### 题目1：压测流量打标

```sql
CREATE TABLE shadow_orders (
  id BIGINT PRIMARY KEY,
  user_id BIGINT,
  amount DECIMAL(10,2),
  is_shadow TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME
);
```

**输出示例**：压测订单 `is_shadow=1`。

**🔍 反思**：压测流量必须和真实业务数据隔离。

**💬 追问**：只靠字段打标有什么风险？

### 🟡 中级用法题

#### 题目2：影子库路由伪代码

```java
if (request.headers["X-Pressure-Test"] == "true") {
  datasource = shadowDatasource;
} else {
  datasource = prodDatasource;
}
```

**关键中间状态**：

```text
压测请求 -> shadow db
真实请求 -> prod db
日志带 traceId 和 shadow 标记
```

**🔍 反思**：影子库比同库打标更安全，但环境成本更高。

**💬 追问**：影子库表结构如何保持与生产一致？

### 🔴 高级/探索用法题

#### 题目3：影子数据清理与校验

```sql
DELETE FROM shadow_orders
WHERE is_shadow = 1
  AND created_at < NOW() - INTERVAL 1 DAY
LIMIT 1000;

SELECT COUNT(*) FROM shadow_orders WHERE is_shadow = 0;
```

**逐步结果示例**：

```text
小批删除压测数据
校验影子库无真实数据
监控清理延迟
```

**🔍 反思**：压测结束后的清理和隔离审计同样重要。

**💬 追问**：如果压测流量误入生产库，如何快速发现？

### ⚙️ 性能压测/证据题

| 指标 | 压测前 | 压测中 | 阈值 |
|---|---:|---:|---:|
| QPS | 1000 | 8000 | 10000 |
| P99 | 30ms | 120ms | 200ms |
| DB CPU | 30% | 75% | 85% |
| Lock waits | 10/min | 200/min | 500/min |

**🔍 反思**：压测要看全链路指标，不只看数据库 QPS。

**💬 追问**：如何找到系统容量拐点？

### 🔷 原理剖析题

**题目：影子库隔离模型**

```text
流量标记 -> 网关识别 -> 服务透传 -> DAO 路由 -> 影子库 -> 影子 MQ/缓存
```

**探针日志示例**：

```text
traceId=abc shadow=true datasource=shadow-order-db
```

**🔍 反思**：只隔离数据库不够，缓存、消息、第三方调用都要考虑。

**💬 追问**：如何防止影子订单触发真实发货？

### 🏢 大厂面试场景实战

**场景**：双 11 前要做全链路压测，数据库层如何设计影子方案？

```text
影子库同构建表
压测流量全链路标记
DAO 层强制路由
禁止调用真实支付/物流
监控真实库是否出现 shadow trace
压测后清理和容量报告
```

```sql
SELECT COUNT(*) FROM prod_orders WHERE trace_id LIKE 'PT_%';
```

**🔍 反思**：全链路压测的第一原则是不能污染生产业务。

**💬 追问**：如何模拟真实数据分布而不泄露隐私？

### 🎯 今日高频面试题速览

1. 什么是影子库？
2. 压测流量如何全链路透传？
3. 同库打标和影子库有什么区别？
4. 全链路压测关注哪些数据库指标？
5. 如何防止压测污染生产？

## 第41天：慢查询智能诊断系统设计

本日掌握：SQL 指纹、指标采集、规则诊断、优化建议生成；覆盖原理点：P33；阶段：大厂期

### 🟢 基础用法题

#### 题目1：慢 SQL 指纹化

```text
原 SQL: SELECT * FROM orders WHERE user_id=1001 AND status='PAID'
指纹: SELECT * FROM orders WHERE user_id=? AND status=?
```

```sql
CREATE TABLE slow_sql_fingerprint (
  fingerprint VARCHAR(255) PRIMARY KEY,
  sample_sql TEXT,
  avg_time_ms INT,
  rows_examined BIGINT,
  last_seen DATETIME
);
```

**🔍 反思**：指纹化能把同一类 SQL 聚合分析。

**💬 追问**：为什么不能只按完整 SQL 文本聚合？

### 🟡 中级用法题

#### 题目2：采集慢 SQL 指标

```sql
INSERT INTO slow_sql_fingerprint
VALUES (
  'SELECT * FROM orders WHERE user_id=?',
  'SELECT * FROM orders WHERE user_id=1001',
  800,
  100000,
  NOW()
)
ON DUPLICATE KEY UPDATE
  avg_time_ms = VALUES(avg_time_ms),
  rows_examined = VALUES(rows_examined),
  last_seen = NOW();
```

**关键中间状态**：

```text
采样 SQL
归一化指纹
聚合耗时、扫描行、出现次数
```

**🔍 反思**：诊断系统必须持续采集，而不是事故时临时查。

**💬 追问**：如何避免采集系统影响数据库？

### 🔴 高级/探索用法题

#### 题目3：规则引擎生成诊断建议

```text
规则1: rows_examined/rows_sent > 1000 -> 疑似扫描过多
规则2: Extra contains filesort -> 检查排序索引
规则3: select * + 覆盖索引缺失 -> 建议裁剪列
规则4: type=ALL -> 检查过滤列索引
```

```sql
EXPLAIN FORMAT=JSON SELECT * FROM orders WHERE user_id=1001 ORDER BY created_at DESC;
```

**逐步结果示例**：

```text
提取 key/type/rows/Extra
匹配规则
输出建议和风险等级
```

**🔍 反思**：智能诊断应先给证据，再给建议，避免误导。

**💬 追问**：如何防止系统建议创建重复索引？

### ⚙️ 性能压测/证据题

| 诊断前 | 诊断后 | 目标 |
|---|---|---|
| 人工排查 30min | 自动初判 1min | 提升定位效率 |
| 建议准确率 60% | 规则迭代到 85% | 降低误报 |

**🔍 反思**：诊断系统也要度量准确率、召回率和节省时间。

**💬 追问**：如何构建慢 SQL 训练集？

### 🔷 原理剖析题

**题目：慢查询自动诊断流水线**

```text
慢日志采集 -> SQL 解析 -> 指纹聚合 -> EXPLAIN 采样 -> 规则/模型诊断 -> 工单建议 -> 验证闭环
```

**探针日志示例**：

```text
fingerprint=abc type=ALL rows=1000000 suggestion=add index(user_id,created_at)
```

**🔍 反思**：自动化不是代替 DBA，而是把重复判断标准化。

**💬 追问**：生产自动执行建索引是否安全？

### 🏢 大厂面试场景实战

**场景**：设计一个公司级慢 SQL 平台。

```text
采集: slow log/performance_schema
处理: 指纹化、聚合、保留样本
诊断: 规则引擎 + EXPLAIN + 索引元数据
输出: 风险等级、建议索引、SQL 改写、负责人
闭环: 优化前后对比
```

```sql
SELECT * FROM performance_schema.events_statements_summary_by_digest
ORDER BY AVG_TIMER_WAIT DESC
LIMIT 20;
```

**🔍 反思**：平台化的价值在闭环和治理，而不是单次诊断。

**💬 追问**：如何给慢 SQL 自动归属到应用和负责人？

### 🎯 今日高频面试题速览

1. 什么是 SQL 指纹？
2. 慢 SQL 自动诊断需要哪些数据？
3. 如何识别全表扫描风险？
4. 如何避免重复索引建议？
5. 慢 SQL 平台如何形成治理闭环？

## 第42天：数据脱敏与加密存储

本日掌握：展示脱敏、加密存储、哈希检索、函数索引；覆盖原理点：P34；阶段：大厂期

### 🟢 基础用法题

#### 题目1：手机号展示脱敏

```sql
CREATE TABLE secure_users (
  id BIGINT PRIMARY KEY,
  phone VARCHAR(20),
  id_card VARCHAR(30)
);

SELECT
  id,
  CONCAT(SUBSTRING(phone,1,3),'****',SUBSTRING(phone,8,4)) AS phone_masked
FROM secure_users;
```

**输出示例**：`138****8000`。

**🔍 反思**：展示脱敏不等于存储安全。

**💬 追问**：日志里是否可以打印明文手机号？

### 🟡 中级用法题

#### 题目2：哈希列支持等值查询

```sql
ALTER TABLE secure_users
  ADD COLUMN phone_hash CHAR(64),
  ADD INDEX idx_phone_hash(phone_hash);

-- 应用层写入 SHA256(phone + salt)
SELECT id
FROM secure_users
WHERE phone_hash = SHA2(CONCAT('13800138000','fixed_salt'), 256);
```

**关键中间状态**：

```text
明文加密存储
哈希列用于等值检索
盐值降低彩虹表风险
```

**🔍 反思**：加密后通常不能直接 LIKE 查询，需另设检索方案。

**💬 追问**：固定盐和每用户随机盐各有什么取舍？

### 🔴 高级/探索用法题

#### 题目3：MySQL 函数索引/生成列

```sql
CREATE TABLE secure_login (
  id BIGINT PRIMARY KEY,
  email VARCHAR(100),
  email_lower VARCHAR(100) GENERATED ALWAYS AS (LOWER(email)) STORED,
  INDEX idx_email_lower(email_lower)
);

SELECT * FROM secure_login
WHERE email_lower = LOWER('User@Example.com');
```

**逐步结果示例**：

```text
生成列保存规范化值
索引用于大小写不敏感查询
```

**🔍 反思**：函数索引/生成列能兼顾表达式查询和索引可用性。

**💬 追问**：为什么直接 `WHERE LOWER(email)=...` 可能不理想？

### ⚙️ 性能压测/证据题

| 查询 | 索引 | 耗时示例 |
|---|---|---:|
| `LOWER(email)=...` | 无法直接用普通 email 索引 | 800ms |
| `email_lower=...` | idx_email_lower | 5ms |

**🔍 反思**：安全处理不能无视查询性能，需要提前建模。

**💬 追问**：如何支持加密字段的前缀搜索？

### 🔷 原理剖析题

**题目：加密、哈希、脱敏职责区别**

```text
脱敏: 展示隐藏部分信息
加密: 可解密还原明文
哈希: 不可逆，用于校验或等值匹配
```

**探针日志示例**：

```text
store: phone_cipher, phone_hash
display: phone_masked
query: phone_hash
```

**🔍 反思**：安全方案是查询能力、合规要求、密钥管理的平衡。

**💬 追问**：密钥轮换如何设计？

### 🏢 大厂面试场景实战

**场景**：用户手机号必须加密存储，但客服要按手机号精确查用户。

```text
phone_cipher: 应用层 KMS 加密
phone_hash: SHA256(phone + salt) 建索引
展示: 只返回脱敏手机号
日志: 禁止明文
权限: 解密接口单独审计
```

```sql
SELECT id, phone_cipher
FROM secure_users
WHERE phone_hash = ?;
```

**🔍 反思**：加密存储常用“密文保存 + 哈希检索 + 脱敏展示”组合。

**💬 追问**：如果需要模糊查询手机号后四位怎么办？

### 🎯 今日高频面试题速览

1. 脱敏、加密、哈希有什么区别？
2. 加密字段如何做等值查询？
3. 生成列如何帮助表达式查询？
4. 为什么日志不能打印敏感明文？
5. 密钥轮换有哪些难点？

## 第43天：数据库迁移方案（不停机、双写、回滚）

本日掌握：双写、回放、校验、灰度切流、回滚水位；覆盖原理点：P35；阶段：大厂期

### 🟢 基础用法题

#### 题目1：迁移前后表结构对齐

```sql
SHOW CREATE TABLE old_orders;
SHOW CREATE TABLE new_orders;
```

**输出示例**：比较字段、索引、字符集、默认值。

**🔍 反思**：迁移第一步是结构一致，字段语义一致。

**💬 追问**：默认值不一致会造成什么问题？

### 🟡 中级用法题

#### 题目2：双写伪代码

```java
begin local transaction
  write old_db.orders
  write new_db.orders
commit
```

**关键中间状态**：

```text
旧库仍为读主
新库持续追数据
双写失败要有重试/补偿
```

**🔍 反思**：双写不是简单写两次，必须处理部分成功。

**💬 追问**：先写旧库还是先写新库？

### 🔴 高级/探索用法题

#### 题目3：校验与回滚水位

```sql
CREATE TABLE migration_checkpoint (
  task_name VARCHAR(50) PRIMARY KEY,
  last_order_id BIGINT,
  status VARCHAR(20),
  updated_at DATETIME
);

SELECT COUNT(*), SUM(amount) FROM old_orders WHERE id BETWEEN 1 AND 100000;
SELECT COUNT(*), SUM(amount) FROM new_orders WHERE id BETWEEN 1 AND 100000;
```

**逐步结果示例**：

```text
按 id 分块校验 count/sum/hash
记录已校验水位
切流后保留回滚窗口
```

**🔍 反思**：没有水位，失败后不知道从哪里继续或回滚。

**💬 追问**：校验 count 相同是否代表数据完全一致？

### ⚙️ 性能压测/证据题

| 阶段 | 风险 | 指标 |
|---|---|---|
| 全量同步 | 源库压力 | QPS/IO |
| 增量同步 | 延迟 | lag |
| 双写 | 业务延迟 | P99 |
| 切流 | 错误率 | error rate |

**🔍 反思**：迁移压测要覆盖同步、双写和回滚路径。

**💬 追问**：如何演练切流失败回滚？

### 🔷 原理剖析题

**题目：不停机迁移状态机**

```text
准备 -> 全量同步 -> 增量追平 -> 双写 -> 灰度读新 -> 全量切读 -> 停旧写 -> 观察 -> 下线旧库
```

**探针日志示例**：

```text
checkpoint=order_id 987654
diff_count=0
new_db_lag=2s
```

**🔍 反思**：迁移是状态机，任何阶段都要定义进入条件和回退条件。

**💬 追问**：双写期间新库写失败如何补偿？

### 🏢 大厂面试场景实战

**场景**：将 5TB 订单库从自建 MySQL 迁到云 RDS，要求不停机。

```text
1. 结构兼容检查
2. 物理/逻辑全量导入
3. binlog 增量同步
4. 分块校验
5. 开启双写
6. 灰度读新库
7. 切主写
8. 保留旧库回滚窗口
```

```sql
SELECT CRC32(GROUP_CONCAT(id,':',status,':',amount ORDER BY id))
FROM orders WHERE id BETWEEN ? AND ?;
```

**🔍 反思**：不停机迁移最重要的是可验证和可回滚。

**💬 追问**：`GROUP_CONCAT` 校验大块数据有什么限制？

### 🎯 今日高频面试题速览

1. 不停机迁移有哪些阶段？
2. 双写有什么一致性风险？
3. 如何做数据校验？
4. 什么是回滚水位？
5. 迁移切流前要满足哪些条件？

## 第44天：云数据库选型对比

本日掌握：RDS、PolarDB、TDSQL、Aurora 的核心差异与选型方法；覆盖原理点：P36；阶段：大厂期

### 🟢 基础用法题

#### 题目1：列出选型指标表

```text
指标: 兼容性、性能、扩展、可用性、备份恢复、成本、生态、锁定风险
```

```sql
SHOW VARIABLES LIKE 'version%';
SHOW VARIABLES LIKE 'max_connections';
SHOW VARIABLES LIKE 'innodb_buffer_pool_size';
```

**🔍 反思**：选型不是只看 QPS，还要看故障恢复和长期成本。

**💬 追问**：为什么要先跑核心 SQL 压测？

### 🟡 中级用法题

#### 题目2：核心 SQL 兼容验证

```sql
EXPLAIN ANALYZE
SELECT user_id, COUNT(*) AS order_cnt
FROM orders
WHERE created_at >= '2026-05-01'
GROUP BY user_id
ORDER BY order_cnt DESC
LIMIT 100;
```

**关键中间状态**：

```text
同一数据集
同一 SQL
比较执行计划、耗时、资源消耗
```

**🔍 反思**：兼容 MySQL 的产品也可能在优化器和执行细节上不同。

**💬 追问**：如何避免只用空库压测得出错误结论？

### 🔴 高级/探索用法题

#### 题目3：选型评分矩阵

| 项 | 权重 | RDS | PolarDB | TDSQL | Aurora |
|---|---:|---:|---:|---:|---:|
| MySQL 兼容 | 25 | 9 | 8 | 8 | 8 |
| 弹性扩展 | 20 | 7 | 9 | 8 | 9 |
| 故障恢复 | 20 | 8 | 9 | 8 | 9 |
| 成本 | 20 | 8 | 7 | 7 | 7 |
| 运维生态 | 15 | 9 | 8 | 8 | 8 |

**编码要求**：结合自己业务权重计算总分。

```sql
-- 真实选型时将压测结果落表再计算
SELECT product, SUM(score * weight) AS total_score
FROM db_selection_score
GROUP BY product
ORDER BY total_score DESC;
```

**🔍 反思**：评分矩阵能让技术争论变成可复核的决策。

**💬 追问**：权重由谁决定？

### 🔷 原理剖析题

**题目：几类云数据库架构差异**

```text
RDS: 托管传统 MySQL
PolarDB/Aurora 类: 存储计算分离
TDSQL 类: 分布式数据库能力更强
```

**探针日志示例**：

```text
failover_time=30s
read_scale_nodes=5
storage_auto_extend=true
```

**🔍 反思**：不同架构对应不同故障模式和性能边界。

**💬 追问**：分布式数据库是否一定比单机 MySQL 更好？

### 🏢 大厂面试场景实战

**场景**：公司核心交易系统要从自建 MySQL 迁云，请给选型方案。

```text
交易强一致、高兼容: 优先托管 RDS/高可用 MySQL
读多写少、弹性读扩展: 考虑 PolarDB/Aurora 类
超大规模分片与金融级容灾: 评估 TDSQL/分布式数据库
最终以核心 SQL 压测、故障演练、成本模型决策
```

```sql
SELECT api_name, avg_latency_ms, p99_latency_ms, error_rate
FROM db_benchmark_result
ORDER BY p99_latency_ms DESC;
```

**🔍 反思**：选型答案要体现业务约束，而不是背产品广告。

**💬 追问**：如果迁云后成本翻倍，如何优化？

### 🎯 今日高频面试题速览

1. RDS 和云原生数据库有什么区别？
2. PolarDB/Aurora 类产品核心架构是什么？
3. 云数据库选型看哪些指标？
4. 为什么要做故障切换演练？
5. 分布式数据库适合哪些场景？

## 第45天：终极系统设计：短 URL 服务数据库层深度优化

本日掌握：万级并发写、毫秒级读、唯一短码、冷热分层、缓存与数据库协同；覆盖原理点：P37；阶段：大厂期

### 🟢 基础用法题

#### 题目1：短 URL 核心表设计

```sql
CREATE TABLE short_url (
  id BIGINT PRIMARY KEY,
  code VARCHAR(16) NOT NULL UNIQUE,
  long_url TEXT NOT NULL,
  user_id BIGINT,
  status VARCHAR(20),
  expire_at DATETIME,
  created_at DATETIME,
  KEY idx_user_time(user_id, created_at),
  KEY idx_expire(expire_at)
);
```

**输出示例**：`code` 唯一索引用于毫秒级跳转查询。

**🔍 反思**：短 URL 的读路径核心是 `code -> long_url`。

**💬 追问**：`long_url` 很长时是否适合放在主表？

### 🟡 中级用法题

#### 题目2：短码生成与插入冲突处理

```sql
INSERT INTO short_url(id, code, long_url, user_id, status, expire_at, created_at)
VALUES (?, ?, ?, ?, 'ACTIVE', ?, NOW());
-- 如果 code 唯一冲突，应用层重新生成 code 后重试
```

**关键中间状态**：

```text
生成全局 id
base62(id) 或随机码生成 code
唯一索引兜底防冲突
```

**🔍 反思**：唯一性不能只靠算法自信，数据库约束要兜底。

**💬 追问**：base62(id) 和随机短码各有什么优缺点？

### 🔴 高级/探索用法题

#### 题目3：跳转读路径与缓存回填

```java
String longUrl = redis.get("su:" + code);
if (longUrl == null) {
  row = jdbc.queryOne("SELECT long_url,status,expire_at FROM short_url WHERE code=?", code);
  if (row != null && row.status.equals("ACTIVE") && row.expireAt.after(now)) {
    redis.setex("su:" + code, ttl(row.expireAt), row.longUrl);
    longUrl = row.longUrl;
  }
}
return redirect(longUrl);
```

**逐步结果示例**：

```text
缓存命中: 1-2ms
缓存未命中: 查 MySQL code 唯一索引
查到后按过期时间回填缓存
```

**🔍 反思**：短链跳转读多写少，缓存能挡住绝大部分读流量。

**💬 追问**：热点短链缓存击穿怎么办？

### ⚙️ 性能压测/证据题

| 路径 | QPS | P99 | 瓶颈 |
|---|---:|---:|---|
| Redis 命中 | 100000 | 5ms | 网络/Redis |
| MySQL 命中 | 10000 | 30ms | code 索引查询 |
| 写入生成 | 5000 | 50ms | 唯一冲突/日志 |

```sql
EXPLAIN SELECT long_url,status,expire_at FROM short_url WHERE code='abc123';
```

**🔍 反思**：读写路径要分别压测，不能只看平均值。

**💬 追问**：如何压测热门 code 占 80% 流量的情况？

### 🔷 原理剖析题

**题目：短 URL 数据库层架构**

```text
写路径: 发号器 -> 生成 code -> MySQL 唯一写入 -> 删除/预热缓存
读路径: CDN/边缘缓存 -> Redis -> MySQL code 唯一索引 -> 回填缓存
治理: 过期清理、黑名单、访问统计异步化、冷热分层
```

**探针日志示例**：

```text
code=abc123 cache=miss db=hit cost=18ms
code=hot999 cache=hit cost=2ms
```

**🔍 反思**：数据库只承担权威存储，热点读和统计写要尽量旁路。

**💬 追问**：访问统计为什么不应该同步更新主表计数？

### 🏢 大厂面试场景实战

**场景**：设计支持万级并发写、毫秒级查询的短 URL 服务，重点说明数据库设计。

```text
表设计:
  short_url(code unique, long_url, user_id, status, expire_at, created_at)
索引:
  uk_code(code), idx_user_time(user_id, created_at), idx_expire(expire_at)
写入:
  雪花 ID/base62 生成短码，唯一索引兜底，冲突重试
读取:
  Redis 优先，MySQL code 唯一索引兜底，按 expire_at 设置 TTL
分库:
  code hash 或 id 分片；code 中可嵌入 shard 位
统计:
  点击日志写 MQ，异步聚合到统计表
治理:
  黑名单、过期清理、冷热分层、备份恢复、限流防刷
```

```sql
CREATE TABLE short_url_click_daily (
  code VARCHAR(16),
  stat_date DATE,
  click_cnt BIGINT,
  PRIMARY KEY(code, stat_date)
);

INSERT INTO short_url_click_daily(code, stat_date, click_cnt)
VALUES ('abc123', CURDATE(), 1)
ON DUPLICATE KEY UPDATE click_cnt = click_cnt + 1;
```

**🔍 反思**：短链系统的数据库设计要把跳转、管理、统计三种负载拆开。

**💬 追问**：如果短码被恶意枚举，数据库层如何配合防护？

### 🎯 今日高频面试题速览

1. 短 URL 核心表如何设计？
2. 短码如何保证唯一？
3. 短链跳转读路径如何优化？
4. 点击统计为什么要异步化？
5. 短 URL 服务如何分库分表？
