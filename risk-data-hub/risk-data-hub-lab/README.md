# 精简版数据中台同步实验室

多数据源 ETL 同步演示项目，模拟金融行业数据中台从多个异构交易系统同步数据到标准化中台库的完整流程。

## 场景

两个上游交易系统（OMS / Broker）存储同一种业务含义的交易数据，但表结构、字段命名、状态码不一致。中台按统一模型清洗、转换后落库。

| 系统 | 类型 | 表 |
|------|------|----|
| trade_oms | TRADE_OMS | oms_stock_snapshot, oms_trade_order, oms_position_holding, oms_cash_asset |
| trade_broker | TRADE_BROKER | broker_stock_quote, broker_trade_deal, broker_position_balance, broker_fund_account |
| risk_hub | HUB | clean_stock, clean_trade, clean_position, clean_asset, event_message, init_task, sync_task, sync_business_record |

## 架构

```
上游系统             中台同步引擎                  目标库
┌──────────┐    ┌─────────────────┐    ┌──────────┐
│ trade_oms │───▶│  StockSync      │───▶│ clean_*  │
│           │    │  TradeSync      │    │          │
│trade_broker│───▶│  PositionSync   │───▶│event_msg │
└──────────┘    │  AssetSync       │    └──────────┘
                └─────────────────┘
```

- **策略模式**：4 类业务（股票/交易/持仓/资金）各自实现 `BusinessSyncTemplate`，Spring 自动注入
- **模板方法**：`AbstractBusinessSyncTemplate` 固定三步 — 拉取 → 转换 → 落库
- **生产者-消费者**：每类业务内部 `ArrayBlockingQueue(4)` + 拉取/落库双线程
- **并发执行**：4 类业务提交到 `syncBusinessExecutor`（4 线程）并发执行
- **游标翻页**：`id > lastId ORDER BY id ASC LIMIT pageSize`，避免分页偏移问题
- **发件箱模式**：同步完成后写入 `event_message` 表，通过 RabbitMQ 推送通知

## 包结构

```
com.riskdatahub
├── common          — 常量、异常、统一响应、工具类（TimeUtils / DistributedLockTemplate）
├── config          — 数据源、线程池、CORS 配置
├── datasource      — 动态路由数据源、连接池管理、RoutingMybatisExecutor
├── id              — Leaf 号段发号器（双缓冲 + 20% 水位线预加载）
├── dictionary      — 字典服务（状态码翻译）
├── sync            — ETL 同步引擎（编排器 + 4 类业务模板 + 实体 + Mapper）
├── task            — 初始化/同步任务管理
├── message         — 事件消息发件箱 + RabbitMQ 发送
├── overview        — 系统总览
├── controller      — RESTful 控制器
├── demo            — 演示数据播种器
└── mapper          — 动态 SQL 执行器
```

## 技术栈

| 组件 | 版本 |
|------|------|
| Spring Boot | 2.7.18 |
| Java | 1.8 |
| MyBatis-Plus | 3.5.7 |
| MySQL | 8.x |
| HikariCP | （Spring Boot 管理） |
| Redisson | 3.27.2 |
| RabbitMQ | （Spring Boot 管理） |
| Lombok | 1.18.34 |

## 前置条件

- JDK 1.8+
- Maven 3.6+
- MySQL 8.x
- RabbitMQ（可选，不影响同步主流程）
- Redis（Redisson 分布式锁依赖）

## 快速开始

### 1. 初始化数据库和表

```bash
pip install PyMySQL
python scripts/init_databases.py
```

### 2. 灌入种子数据

```bash
python scripts/seed_data.py
```

### 3. 启动应用

```bash
mvn spring-boot:run
```

访问 `http://localhost:8501/`

### 4. 注册上游数据源

通过 API 注册上游数据源（配置定义在 `application.yml` 的 `hub.datasource.items`）：

```bash
curl -X POST http://localhost:8501/api/datasource/register \
  -H "Content-Type: application/json" \
  -d '{"key":"trade_oms","name":"交易系统A库","datasourceType":"TRADE_OMS","url":"jdbc:mysql://host.docker.internal:3306/trade_oms?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai","username":"root","password":"123456"}'
```

### 5. 初始化演示数据

```bash
curl -X POST http://localhost:8501/api/hub/init-data
```

### 6. 执行同步

```bash
curl -X POST http://localhost:8501/api/hub/sync \
  -H "Content-Type: application/json" \
  -d '{"dataSourceKey":"trade_oms","pageSize":100}'
```

## API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/hub/overview | 系统总览 |
| GET | /api/hub/init-task | 初始化任务状态 |
| GET | /api/hub/sync-task | 同步任务状态 |
| GET | /api/hub/cleaned-trades | 清洗交易记录 |
| POST | /api/hub/init-data | 提交初始化任务 |
| POST | /api/hub/sync | 提交同步任务 |
| GET | /api/datasource | 数据源列表 |
| GET | /api/datasource/{key} | 数据源详情 |
| POST | /api/datasource/register | 注册数据源 |
| DELETE | /api/datasource/{key} | 删除数据源 |

## SQL 脚本

统一管理在项目根目录 `sql/` 下：

```
sql/
├── ddl/           — 建表（CREATE TABLE）
└── dml/           — 数据操作（种子数据、清表）
```

`init_databases.py` 读取 `sql/ddl/` 中的 schema 文件建表，`seed_data.py` 读取 `sql/dml/` 灌入种子数据。
