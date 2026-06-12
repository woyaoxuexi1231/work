你列出的这四个，正是 MySQL 默认自带的 **4 个核心系统数据库**。它们不存储你的业务数据，而是用于保存数据库自身运行所需的元数据、权限、性能指标等。

下面分别说明它们的作用、大致表/视图数量，以及各自的关键表。

---

### 1. mysql —— 核心权限与运行数据
这是 MySQL 的“大脑”，存储用户账户、权限、时区、事件调度器等核心信息。里面的表是**真实基表**，直接关系到数据库能不能正常启动。

- **大致数量**：约 30～35 张表（MySQL 5.7/8.0）
- **关键表**：
    - `user`、`db`、`tables_priv`、`columns_priv`：用户权限控制
    - `time_zone`、`time_zone_name`、`time_zone_transition`：时区数据
    - `event`、`proc`：存储事件和存储过程元数据
    - `general_log`、`slow_log`：查询日志（如果开启会写入这里）

---

### 2. information_schema —— 元数据字典
它提供 **只读视图**，让你像查表一样查看所有数据库、表、列、索引、约束等定义信息。它是 SQL 标准的一部分，数据来源于 MySQL 内部的数据字典。

- **大致数量**：约 60～70 个视图（MySQL 8.0），因版本而异
- **关键视图**：
    - `TABLES`：所有库的表信息
    - `COLUMNS`：所有表的列信息
    - `STATISTICS`：索引统计信息
    - `SCHEMATA`：所有数据库名
    - `PROCESSLIST`：当前连接线程
    - `KEY_COLUMN_USAGE`：主键、外键等约束
    - `INNODB_*` 系列：InnoDB 引擎的详细内部状态

---

### 3. performance_schema —— 运行时性能监控
用于实时监控 MySQL 内部的等待、锁、内存、语句执行、事务等非常底层的运行指标，是排查性能问题的利器。

- **大致数量**：超过 100 张表（MySQL 8.0 更多），全部是**基表**（存内存，不存磁盘）
- **关键表举例**：
    - `events_statements_current/history/history_long`：SQL 语句执行统计
    - `events_waits_current`：当前等待事件
    - `memory_summary_*`：内存使用汇总
    - `table_io_waits_summary_by_table`：表级别的 I/O 等待
    - `mutex_instances`、`rwlock_instances`：同步对象锁信息

---

### 4. sys —— 易用的诊断视图与工具
从 MySQL 5.7 开始内置，它基于 `information_schema` 和 `performance_schema` 封装出了一套更人性化的视图、函数和存储过程，方便直接查看“哪个库占用空间大”“哪个 SQL 最慢”等问题。

- **大致数量**：100 多个对象（视图为主 + 部分函数和存储过程）
- **关键视图/工具**：
    - `schema_table_statistics`：每个表的增删改查和 I/O 统计
    - `statements_with_runtimes_in_95th_percentile`：耗时最长的 SQL 排行
    - `schema_unused_indexes`：从未使用的索引
    - `ps_is_instrument_default_enabled()` 等诊断函数
    - `version` 视图：当前 sys 版本

---

### 怎样快速查看每个库有多少表？

你可以在 MySQL 里运行这条 SQL（注意 `sys` 里的视图和存储过程也会被统计为“表”类型）：

```sql
SELECT TABLE_SCHEMA, COUNT(*) AS table_count
FROM information_schema.tables
WHERE TABLE_SCHEMA IN ('mysql','information_schema','performance_schema','sys')
GROUP BY TABLE_SCHEMA;
```

如果你还想进一步了解某个库里的具体表名，也可以追问，我会帮你列出某个库下的典型表和它们的用途。