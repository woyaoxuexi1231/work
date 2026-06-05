# Risk Data Hub Lab — 多数据源 ETL 同步中台

模拟金融行业数据中台从多个异构交易系统（OMS / Broker）实时同步数据到标准化中台库的完整 ETL 工程。覆盖数据源动态注册、断点续传、并发同步、进度追踪、强制刷新等生产级能力。

---

## 目录

- [场景](#场景)
- [架构总览](#架构总览)
- [同步引擎详解](#同步引擎详解)
  - [三层调度](#三层调度)
  - [两种线程协作模式](#两种线程协作模式)
  - [同步生命周期](#同步生命周期)
- [数据库](#数据库)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
- [API 参考](#api-参考)
- [设计决策与踩坑记录](#设计决策与踩坑记录)

---

## 场景

两个上游交易系统存储同一业务含义的数据，但表结构、字段命名、状态码完全不同：

| 系统 | 类型 | 数据库 | 表 |
|------|------|--------|------|
| trade_oms | TRADE_OMS | trade_oms | oms_stock_snapshot, oms_trade_order, oms_position_holding, oms_cash_asset |
| trade_broker | TRADE_BROKER | trade_broker | broker_stock_quote, broker_trade_deal, broker_position_balance, broker_fund_account |

中台（risk_hub）按统一模型清洗转换后落库为 4 张标准表：`clean_stock` / `clean_trade` / `clean_position` / `clean_asset`。

**同步维度**：每个数据源的每种业务类型独立拉取—转换—写入，互不干扰。

---

## 架构总览

```
┌─────────────────────────────────────────────────────────────────────┐
│                       前端 (Vue 3 + Tailwind)                       │
│   同步任务提交 / 实时进度展示 / 数据源管理 / 清洗数据预览            │
└──────────────────────────┬──────────────────────────────────────────┘
                           │ REST API
                           ▼
┌──────────────────────────────────────────────────────────────────────┐
│  SyncTaskService                     SyncProgressEventListener       │
│  ┌───────────┐  ┌──────────┐        ┌─────────────────────────┐    │
│  │ 任务管理   │  │ 状态追踪  │        │ 进度事件监听 → 写入DB   │    │
│  │ QUEUED→RUN │  │ SUCCESS  │        │ (1s 节流, 终态强制写)   │    │
│  │ NING→DONE  │  │ /FAILED  │        └─────────────────────────┘    │
│  └─────┬─────┘  └──────────┘                                        │
│        │ scanAndExecute (3s 定时)                                   │
│        ▼                                                             │
│  SyncOrchestrator                                                    │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │ StockTemplate  TradeTemplate  PositionTemplate  AssetTemplate│     │
│  │ (策略模式: 4 类业务各自实现 BusinessSyncTemplate 接口)       │     │
│  │ syncBusinessExecutor (4 线程) 并发派发                      │     │
│  │ 每个 Template 内部:                                         │     │
│  │   ├─ AbstractBusinessSyncTemplate  (有界队列模式)            │     │
│  │   │   拉取线程 ──[ArrayBlockingQueue(4)]──→ 落库线程         │     │
│  │   └─ AbstractSemaphoreSyncTemplate (信号量交替模式)          │     │
│  │       拉取线程 ──[Semaphore 交替]──→ 落库线程                │     │
│  └────────────────────────────────────────────────────────────┘     │
└──────────────────────────────────────────────────────────────────────┘
```

### 核心模式

| 模式 | 用途 | 实现 |
|------|------|------|
| 策略模式 | 4 类业务各自实现 `BusinessSyncTemplate`，Spring 自动注入 | `SyncOrchestrator` 遍历 `List<BusinessSyncTemplate>` |
| 模板方法 | `execute()` 固定流程：拉取→转换→落库，子类定制 5 个抽象方法 | `AbstractBusinessSyncTemplate.execute()` 为 `final` |
| 生产者-消费者 | 每类业务内部双线程 + 有界队列背压 | `ArrayBlockingQueue(4)` + `put()`/`take()` |
| 信号量交替 | 每类业务内部双线程严格交替，零内存积压 | `Semaphore(1/0)` + 共享页 |
| 发件箱模式 | 同步完成后写入 `event_message` 表，异步推送 MQ | `MessageOutboxService.publish()` |
| 断点续传 | 清洗表 `MAX(source_row_id)` 决定起始游标 | `SyncTaskService.runTask()` 中查询业务表 |

---

## 同步引擎详解

### 三层调度

```
SyncTaskService.scanAndExecute()     ← 每 3 秒定时扫描 QUEUED 任务
  └─ syncTaskExecutor.submit(runTask)  ← 异步执行
       └─ SyncOrchestrator.syncByDataSource()
            ├─ StockTemplate.execute()    ← syncBusinessExecutor (4线程池)
            ├─ TradeTemplate.execute()    ← 每个模板独立线程
            ├─ PositionTemplate.execute()
            └─ AssetTemplate.execute()
                 └─ pairExecutor           ← 每个模板内部 2 线程 (拉取+落库)
```

**线程模型**：1（调度）+ 4（业务并行）+ 4×2（内部双线程）= 13 线程峰值。

### 两种线程协作模式

#### 1. 有界队列模式（`AbstractBusinessSyncTemplate`）

```java
BlockingQueue<PageChunk<S>> queue = new ArrayBlockingQueue<>(4);
```

| | 拉取线程（生产者） | 落库线程（消费者） |
|---|---|---|
| 职责 | 游标翻页拉取上游数据，`put()` 入队 | `take()` 出队，转换后 `saveBatch()` 写入中台 |
| 结束信号 | `finally` 块发送 `PageChunk.end()` 哨兵对象 | 收到哨兵后 `break` |
| 背压 | `put()` 在队列满时阻塞 | `take()` 在队列空时阻塞 |

**优点**：最多 4 页缓冲，拉取和落库可以并行工作。
**缺点**：内存中最多缓存 4 页数据。

#### 2. 信号量交替模式（`AbstractSemaphoreSyncTemplate`）

```java
Semaphore fetchPermit = new Semaphore(1);
Semaphore insertPermit = new Semaphore(0);
List<S> sharedPage = new ArrayList<>();
```

| | 拉取线程 | 落库线程 |
|---|---|---|
| 同步 | `fetchPermit.acquire()` → 拉取 → `insertPermit.release()` | `insertPermit.acquire()` → 处理 → `fetchPermit.release()` |
| 内存 | 写入共享页后立即释放信号量 | 复制出数据后立即清空共享页 |
| 退出 | `noMoreData=true` + `insertPermit.release()` | 检查 `noMoreData && sharedPage.isEmpty` 后 `break` |

**优点**：任意时刻最多 1 页数据在内存，内存零积压。
**缺点**：拉取和落库完全串行，无并行度。

> **为什么两种模式都存在？** 队列模式适合上游拉取慢、下游落库也慢的场景（通过缓冲调节速度差）；信号量模式适合内存敏感场景（严格交替，最多一页）。

### 同步生命周期

```
createOrResetTask()          → status=QUEUED, errorMessage=null
scanAndExecute() (3s 定时)  → status=RUNNING
  └─ doForceCleanIfNeeded()  → 如果是强制刷新，DELETE 4 张清洗表 + 清 Redis
  └─ 创建 SyncBusinessRecord  → status=RUNNING, pulledCount=0, savedCount=0
  └─ 查询 MAX(source_row_id) → 初始化断点续传游标
  └─ syncByDataSource()
       ├─ 每个业务独立执行 execute()
       │    ├─ 拉取线程: fetchPage() → queue.put() / sharedPage
       │    ├─ 落库线程: take() → transform() → saveBatch()
       │    ├─ 进度事件: publishProgress() → SyncProgressEventListener → 写 DB
       │    └─ 完成: executeTemplate() → SyncBusinessRecord.status=SUCCESS
       └─ 汇总 totalPulled / totalSaved
  └─ updateTaskFields()      → status=SUCCESS, progress=100, running=false
```

---

## 项目结构

```
risk-data-hub-lab/
├── src/main/java/com/riskdatahub/
│   ├── common/               — 常量、异常、统一响应、工具类
│   ├── config/               — 线程池、CORS 配置
│   ├── datasource/           — 动态路由数据源、连接池管理
│   ├── id/                   — Leaf 号段发号器（双缓冲 + 20% 水位线预加载）
│   ├── dictionary/           — 字典服务（状态码翻译）
│   ├── sync/
│   │   ├── template/         — 同步模板（核心）
│   │   │   ├── AbstractBaseSyncTemplate     — 基类（共享字段 + publishProgress）
│   │   │   ├── AbstractBusinessSyncTemplate — 有界队列模式
│   │   │   ├── AbstractSemaphoreSyncTemplate — 信号量交替模式
│   │   │   ├── StockSyncTemplate            — 股票同步
│   │   │   ├── TradeSyncTemplate            — 交易同步
│   │   │   ├── PositionSyncTemplate         — 持仓同步
│   │   │   └── AssetSyncTemplate            — 资金同步
│   │   ├── model/            — 同步模型（SyncSupport.PageChunk, SyncCounter 等）
│   │   ├── entity/           — 清洗表实体（CleanStock/Trade/Position/Asset）
│   │   ├── mapper/           — MyBatis Mapper
│   │   ├── SyncOrchestrator  — 编排引擎
│   │   └── SyncController    — REST 控制器
│   ├── task/
│   │   ├── entity/           — SyncTask, SyncBusinessRecord
│   │   ├── mapper/           — MyBatis Mapper
│   │   ├── SyncTaskService   — 任务调度、状态管理
│   │   └── SyncProgressEventListener — 进度事件监听
│   ├── message/              — 发件箱 + RabbitMQ 发送
│   └── overview/             — 系统总览
├── src/main/resources/
│   └── application.yml       — 配置
├── sql/                      — DDL + 种子数据
└── scripts/                  — Python 初始化脚本
```

---

## 数据库

### 中台核心表

| 表 | 说明 | 关键字段 |
|----|------|---------|
| `clean_stock` | 清洗股票数据 | global_id, source_row_id, stock_code, source_system |
| `clean_trade` | 清洗交易数据 | global_id, source_row_id, vendor_trade_no, source_system |
| `clean_position` | 清洗持仓数据 | global_id, source_row_id, account_no, stock_code |
| `clean_asset` | 清洗资金数据 | global_id, source_row_id, account_no, account_name |
| `sync_task` | 同步任务 | id, status(QUEUED/RUNNING/SUCCESS/FAILED), progress |
| `sync_business_record` | 业务同步明细 | task_id, business_code, status, pulled_count, saved_count, last_row_id |
| `event_message` | 发件箱事件 | event_type, payload, status |

### 断点续传机制

每次同步前查询清洗表的 `MAX(source_row_id)` 作为上游游标起始点：

```sql
-- 对应代码 SyncTaskService.runTask() 中按 businessCode 分支
SELECT MAX(source_row_id) FROM clean_stock;   -- STOCK
SELECT MAX(source_row_id) FROM clean_trade;   -- TRADE
SELECT MAX(source_row_id) FROM clean_position; -- POSITION
SELECT MAX(source_row_id) FROM clean_asset;    -- ASSET
```

这样不依赖 `sync_business_record` 的中间状态，也不会因为任务重置而丢失游标。

---

## 技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 2.7.18 | 应用框架 |
| Java | 1.8 | 运行环境 |
| MyBatis-Plus | 3.5.7 | ORM |
| MySQL | 8.x | 数据库 |
| HikariCP | (Spring Boot) | 连接池 |
| Redisson | 3.27.2 | 分布式锁 |
| RabbitMQ | (Spring Boot) | 异步消息 |
| Leaf 号段 | 自研 | 分布式 ID |
| Lombok | 1.18.34 | 代码生成 |
| Vue 3 | - | 前端 |
| Tailwind CSS | - | 前端样式 |

---

## 快速开始

### 前置条件

- JDK 1.8+, Maven 3.6+
- MySQL 8.x, Redis, RabbitMQ（可选）
- Python 3 + PyMySQL

### 1. 初始化数据库和表

```bash
pip install PyMySQL
python scripts/init_databases.py
```

自动创建 trade_oms / trade_broker / risk_hub 三个数据库并执行 DDL。

### 2. 灌入种子数据

```bash
# 建表 + 种子数据（幂等）
python scripts/seed_data.py

# 先清空再写入
python scripts/seed_data.py --force
```

### 3. 灌入演示数据

```bash
# 默认 5000 只股票 × 200 交易日 ≈ 1400 万条
python scripts/seed_upstream.py

# 自定义
python scripts/seed_upstream.py --stocks 1000 --days 30 --force
```

### 4. 启动应用

```bash
cd risk-data-hub-lab
mvn spring-boot:run
```

访问 `http://localhost:8501/`

### 5. 注册数据源并同步

通过前端页面或 API：

```bash
# 注册
curl -X POST http://localhost:8501/api-datasource-register \
  -H "Content-Type: application/json" \
  -d '{"key":"trade_oms","name":"OMS交易系统","datasourceType":"TRADE_OMS","url":"jdbc:mysql://localhost:3306/trade_oms?...","username":"root","password":"123456"}'

# 同步
curl -X POST http://localhost:8501/api-hub-sync \
  -H "Content-Type: application/json" \
  -d '{"dataSourceKey":"trade_oms","pageSize":10000}'

# 强制刷新（清数据后重同步）
curl -X POST http://localhost:8501/api-hub-sync-force-refresh \
  -H "Content-Type: application/json" \
  -d '{"dataSourceKey":"trade_oms","pageSize":10000}'
```

> `pageSize` 默认 10000，上限 100000。`forceRefresh` 立即返回，数据清除在后台异步执行。

---

## API 参考

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api-hub-overview | 系统总览（拓扑、表统计、Leaf 状态） |
| POST | /api-hub-sync | 提交同步任务 |
| POST | /api-hub-sync-force-refresh | 强制刷新（异步清数据+全量同步） |
| POST | /api-hub-sync-task | 当前同步任务状态 |
| POST | /api-hub-sync-business-records | 业务同步明细列表 |
| POST | /api-hub-cleaned-trades | 最近 30 条清洗交易记录 |
| POST | /api-datasource-list | 列出所有数据源 |
| POST | /api-datasource-get | 查看单个数据源 |
| POST | /api-datasource-register | 注册新数据源 |
| POST | /api-datasource-remove | 删除数据源 |

---

## 设计决策与踩坑记录

### 1. 为什么用 `ArrayBlockingQueue(4)` 而不是无界队列？

有界队列提供背压：上游拉取再快，队列满时 `put()` 也会阻塞，防止内存溢出。容量 4 是经验值——太小则拉取频繁阻塞降低吞吐，太大则背压响应迟钝。

### 2. 为什么有队列和信号量两种实现？

| | 队列模式 | 信号量模式 |
|---|---|---|
| 并行度 | 拉取和落库可同时进行 | 严格串行交替 |
| 内存占用 | 最多 4 页 | 最多 1 页 |
| 适用场景 | 上游拉取较慢的场景 | 内存敏感或单页较大的场景 |

### 3. 信号量模式踩坑：partial last page 死锁

**Bug**：当最后一条数据不足 pageSize 时，拉取线程只释放了一次 `insertPermit`（给数据页），消费者处理完后再循环时永久阻塞在 `insertPermit.acquire()`。

**修复**：partial page 退出路径多释放一次 `insertPermit`，让消费者检查 `noMoreData` 后退出。

### 4. 断点续传为什么不用 `sync_business_record.lastRowId`？

该字段依赖任务状态流转，force refresh 复用同一 taskId 时可能读到中间状态。直接查清洗表的 `MAX(source_row_id)` 更可靠，不依赖任何中间表。

### 5. 进度事件 1 秒节流导致前端永远"堆积中"

`SyncProgressEventListener` 对同任务每秒最多写一次 DB。如果最后一页处理速度小于 1s，终态事件（`pulledCount == savedCount`）被节流丢弃，DB 中`已拉取 > 已落库`，前端永远显示"堆积中"。

**修复**：`pulledCount == savedCount` 时强制写入，跳过节流。

### 6. 跨线程计数可见性

`SyncCounter.pulledCount` 和 `savedCount` 分别由拉取线程和落库线程写入，且被对方线程在 `publishProgress()` 中读取。没有 `volatile` 时读到的可能是 CPU 缓存的陈旧 0 值。

**修复**：两个字段加 `volatile`。

### 7. 为什么状态更新从 `runTask` 移到了 `executeTemplate`？

原设计：`runTask` 等所有业务同步完成后统一更新 `SyncBusinessRecord` 状态。有问题：业务 A 跑完了但 B 还在跑，前端一直看不到 A 的完成状态。

**修复**：每个业务在 `SyncOrchestrator.executeTemplate()` 中独立更新自己的状态，各管各的。
