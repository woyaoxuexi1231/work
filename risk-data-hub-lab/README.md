# 精简版数据中台同步实验室

## 1. 这版的真实含义

这次两个上游库不是两个不同业务，而是两个不同交易系统。

也就是说：

- 他们存的都是交易数据
- 业务含义是一样的
- 但表结构、字段名、状态码不一致

中台要做的事就是：

1. 指定一个上游交易系统
2. 分页拉取这个系统里未同步的交易数据
3. 按这个系统自己的字段结构做转换
4. 落到中台统一交易表
5. 回写源表同步标记

前台仍然只保留：

- 数据源维护
- 一个同步按钮

## 1.1 这次又新增了什么

这次我又继续按你的要求补了几件事：

- 全部核心数据对象改成 `lombok`
- 项目启动时只保证 schema 和表结构存在，不再自动灌数
- 初始化演示数据改成前端手动触发
- 初始化时先调用 Marketstack 拉大量股票行情
- 再基于这批股票行情派生出多个业务表，方便你做多线程压测
- 同步流程改成模板模式，固定走 拉取 -> 转换 -> 落库
- 每种业务使用一对线程处理：一个拉取线程，一个落库线程
- 股票、交易、持仓、资金四类业务会并发执行
- 如果 Marketstack 免费额度耗尽，会自动切到本地兜底股票数据，不影响项目启动

## 2. 三个数据库

现在只有 3 个数据库：

- `trade_oms`
  - 上游交易系统 A
  - 类型 `TRADE_OMS`
  - 表 `oms_stock_snapshot`
  - 表 `oms_trade_order`
  - 表 `oms_position_holding`
  - 表 `oms_cash_asset`
- `trade_broker`
  - 上游交易系统 B
  - 类型 `TRADE_BROKER`
  - 表 `broker_stock_quote`
  - 表 `broker_trade_deal`
  - 表 `broker_position_balance`
  - 表 `broker_fund_account`
- `risk_hub`
  - 中台库
  - 类型 `HUB`
  - 存标准化交易结果、字典、Leaf 号段、事件消息

初始化 SQL：

- [init-three-databases.sql](file:///d:/project/work/risk-data-hub-lab/sql/init-three-databases.sql)

不过现在项目已经不是“必须先手动跑 SQL 才能启动”的模式了。

现在启动时会自动：

1. 连接 MySQL
2. 创建 `trade_oms`
3. 创建 `trade_broker`
4. 创建 `risk_hub`
5. 以 `if not exists` 方式确认各自表结构存在

也就是说：

- 数据库不存在，会自动创建
- 表不存在，会自动创建
- 启动阶段不再主动删表或灌数
- 这版默认不考虑历史迁移，只保证当前开发结构可用

## 3. 为什么这里必须有 datasourceType

因为两个交易系统虽然表达的是同一种“交易成交”数据，但字段根本不一样。

### 交易系统 A

交易系统 A 现在不再只有一张交易表，而是至少 4 张常用业务表：

- `oms_stock_snapshot`
- `oms_trade_order`
- `oms_position_holding`
- `oms_cash_asset`

同步主表仍然是 `oms_trade_order`，字段示例：

- `order_no`
- `investor_name`
- `side_code`
- `order_amount`
- `trade_status`
- `trade_time`

### 交易系统 B

交易系统 B 也扩成了 4 张异构业务表：

- `broker_stock_quote`
- `broker_trade_deal`
- `broker_position_balance`
- `broker_fund_account`

同步主表仍然是 `broker_trade_deal`，字段示例：

- `deal_code`
- `client_full_name`
- `bs_flag`
- `turnover_amount`
- `status_mark`
- `deal_at`

如果没有 `datasourceType`，那服务层根本不知道：

- 该查哪张表
- 该取哪些列
- 买卖方向怎么翻译
- 状态码该走哪套字典
- 最后怎么拼成统一交易对象

相关代码：

- [DataSourceConfigDTO.java](file:///d:/project/work/risk-data-hub-lab/src/main/java/com/example/dynamicds/dto/DataSourceConfigDTO.java)
- [DynamicDataSourceManager.java](file:///d:/project/work/risk-data-hub-lab/src/main/java/com/example/dynamicds/datasource/DynamicDataSourceManager.java)

## 3.1 为什么现在全部改成 Lombok

你要求“全部要使用 lombok”，所以这版我已经把核心这些类切过去了：

- DTO
- 实体
- 配置类
- 大部分控制层 / 服务层构造注入

常用的是：

- `@Data`
- `@RequiredArgsConstructor`
- `@Slf4j`

这样做的好处很直接：

- 代码更短
- 结构更清晰
- 你看业务逻辑时不会被 getter / setter 淹没

## 4. ETL 主流程

核心代码：

- [TradeEtlService.java](file:///d:/project/work/risk-data-hub-lab/src/main/java/com/example/dynamicds/service/TradeEtlService.java)

现在同步不是只处理交易表，而是统一抽成模板流程。主流程固定就是：

1. 传入 `dataSourceKey`
2. 根据 `dataSourceKey` 找到 `datasourceType`
3. 同时启动 4 类业务模板：
4. 股票、交易、持仓、资金各自并发执行
5. 每类业务内部固定一对线程：
6. 拉取线程按 `id > lastId` 分页拉未同步数据
7. 落库线程从队列取数据，做字段转换并落到中台标准表
8. 回写源表 `sync_flag = 1`
9. 写入标准事件 `event_message`

也就是说，现在已经不是“一个 while 循环从头跑到尾”的模型了，而是：

- 外层按业务并发
- 内层按业务拆成 拉取线程 + 落库线程
- 模板统一控制通用骨架

### 为什么按 `id > lastId` 分页

因为同步完一页后，会立刻回写 `sync_flag = 1`。

如果这里用简单 `offset`：

- 第一页处理完
- 数据集就变了
- 下一页很容易跳过部分数据

所以这里用：

- `id > lastId`
- `order by id asc`
- `limit pageSize`

这种方式更稳。

## 5. 两套交易系统的转换规则

### `TRADE_OMS`

从 `oms_trade_order` 读取：

- `order_no`
- `investor_name`
- `side_code`
- `order_amount`
- `trade_status`
- `trade_time`

转换到中台：

- `vendor_trade_no = order_no`
- `counterparty_name = investor_name`
- `direction = B -> BUY, S -> SELL`
- `amount = order_amount`
- `status_name = translate("trade_status_oms", trade_status)`
- `biz_type = 股票交易`

### `TRADE_BROKER`

从 `broker_trade_deal` 读取：

- `deal_code`
- `client_full_name`
- `bs_flag`
- `turnover_amount`
- `status_mark`
- `deal_at`

转换到中台：

- `vendor_trade_no = deal_code`
- `counterparty_name = client_full_name`
- `direction = 1 -> BUY, 2 -> SELL`
- `amount = turnover_amount`
- `status_name = translate("trade_status_broker", status_mark)`
- `biz_type = 股票交易`

这才是你说的“字段可能不一致，但是意思是一个意思”。

## 6. 中台统一结果表

中台现在不只是一张 `clean_trade`，而是 4 张标准结果表：

- `clean_stock`
- `clean_trade`
- `clean_position`
- `clean_asset`

其中：

- `clean_stock` 统一保存股票行情
- `clean_trade` 统一保存交易成交
- `clean_position` 统一保存持仓
- `clean_asset` 统一保存资金资产

这 4 张表都会记录：

- 来源系统 `source_system`
- 来源类型 `source_type`
- 来源行 ID `source_row_id`
- 同步批次 `clean_batch`
- 创建时间 `created_at`

相关代码：

- [CleanTrade.java](file:///d:/project/work/risk-data-hub-lab/src/main/java/com/example/dynamicds/entity/CleanTrade.java)

## 7. 页面现在保留什么

页面文件：

- [index.html](file:///d:/project/work/risk-data-hub-lab/src/main/resources/static/index.html)

页面现在保留：

- 数据源维护
  - 新增
  - 查看
  - 删除
- 一个初始化演示数据按钮
  - 手动清空并重灌当前演示数据
  - 不再放到启动阶段自动执行
- 一个同步按钮
  - 选择交易系统数据源
  - 指定分页大小
  - 触发异步同步任务

这样页面不会跑偏成大而全演示台。

## 8. 启动初始化

启动后 [PlatformBootstrapService.java](file:///d:/project/work/risk-data-hub-lab/src/main/java/com/example/dynamicds/service/PlatformBootstrapService.java) 现在只会自动：

1. 自动创建 3 个 schema
2. 注册三套默认数据源
3. 校验三库表结构存在

不会再在启动阶段自动：

- 拉股票数据
- 清空业务表
- 灌演示数据

这些动作已经挪到前端按钮 `/api/hub/init-data`。

## 8.1 为什么这次要扩成多业务表

因为你后面要做的是：

- 多线程
- 多表
- 多批量
- 不同系统字段结构

如果只给你一张小交易表，那你根本测不出什么东西。

所以现在的初始化演示数据改成两层：

1. 第一层是真实股票行情
2. 第二层是基于行情派生出的业务表

这样你可以同时拿到：

- 大量股票基础数据
- 大量交易数据
- 大量持仓数据
- 大量资金资产数据

默认配置在：

- [application.yml](file:///d:/project/work/risk-data-hub-lab/src/main/resources/application.yml)

Marketstack 配置也放在这里，默认从环境变量读取：

- `MARKETSTACK_ACCESS_KEY`

如果出现：

- `429 Too Many Requests`
- `usage_limit_reached`
- 或者本机没配 key

项目现在不会直接启动失败，而是会自动生成一批本地股票样本继续灌库。

相关代码：

- [MarketstackProperties.java](file:///d:/project/work/risk-data-hub-lab/src/main/java/com/example/dynamicds/config/MarketstackProperties.java)
- [MarketstackService.java](file:///d:/project/work/risk-data-hub-lab/src/main/java/com/example/dynamicds/service/MarketstackService.java)
- [PlatformBootstrapService.java](file:///d:/project/work/risk-data-hub-lab/src/main/java/com/example/dynamicds/service/PlatformBootstrapService.java)

## 9. 如何运行

### 9.1 先执行 SQL

现在这一步不是必须了，因为启动已经会自动创建 schema 和表。

这个 SQL 文件主要用于：

- 你想手动初始化
- 你想单独检查建表语句
- 你想不依赖应用自己建库

注意：

- 这个 SQL 主要用于你手动检查建表语句
- 应用启动默认只做 `create database if not exists` 和 `create table if not exists`
- 演示数据初始化才会主动清表再灌数

### 9.2 启动项目

```bash
cd d:\project\work\risk-data-hub-lab
set MARKETSTACK_ACCESS_KEY=你的key
mvn spring-boot:run
```

页面地址：

- `http://localhost:8501/`

## 10. 面试时怎么讲

你可以这样说：

> 我做过一个精简版数据中台同步项目。  
> 中台要接多个交易系统的数据，这些系统表达的是同一种交易业务，但字段名、状态码、表结构不一致。  
> 所以我给每个数据源配置了 datasourceType，同步时先按指定数据源找到对应转换规则。  
> 同步骨架我用模板模式统一成 拉取 -> 转换 -> 落库，每种业务内部固定一对线程，一个拉取线程，一个落库线程；股票、交易、持仓、资金四类业务并发执行。  
> 启动阶段只负责保证 schema 和表结构正常，演示数据初始化从前端手动触发，这样既方便调试，也更贴近真实项目里的任务入口。
