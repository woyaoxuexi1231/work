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

这次我又继续按你的要求补了 3 件事：

- 全部核心数据对象改成 `lombok`
- 项目启动时自动创建 schema 和表，存在就跳过
- 项目启动时先调用 Marketstack 拉一批真实股票数据，再按两个上游交易系统的不同字段结构入库

## 2. 三个数据库

现在只有 3 个数据库：

- `trade_oms`
  - 上游交易系统 A
  - 类型 `TRADE_OMS`
  - 表 `oms_trade_order`
- `trade_broker`
  - 上游交易系统 B
  - 类型 `TRADE_BROKER`
  - 表 `broker_trade_deal`
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
5. 再创建各自的表

也就是说：

- 数据库不存在，会自动创建
- 表不存在，会自动创建
- 已存在，就不会重复创建

## 3. 为什么这里必须有 datasourceType

因为两个交易系统虽然表达的是同一种“交易成交”数据，但字段根本不一样。

### 交易系统 A

表是 `oms_trade_order`，字段示例：

- `order_no`
- `investor_name`
- `side_code`
- `order_amount`
- `trade_status`
- `trade_time`

### 交易系统 B

表是 `broker_trade_deal`，字段示例：

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

主流程固定就是：

1. 传入 `dataSourceKey`
2. 根据 `dataSourceKey` 找到 `datasourceType`
3. 按类型分页拉取未同步数据
4. 按类型做字段映射和状态转换
5. 写入中台库 `clean_trade`
6. 回写源表 `sync_flag = 1`
7. 写入标准事件 `event_message`

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
- `biz_type = 证券交易`

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
- `biz_type = 证券交易`

这才是你说的“字段可能不一致，但是意思是一个意思”。

## 6. 中台统一结果表

中台最关键的表是 `clean_trade`。

它统一保存：

- 来源系统 `source_system`
- 来源类型 `source_type`
- 来源行 ID `source_row_id`
- 上游交易单号 `vendor_trade_no`
- 统一后的买卖方向 `direction`
- 统一后的状态名 `status_name`
- 金额 `amount`
- 交易时间 `trade_time`
- 同步批次 `clean_batch`

相关代码：

- [CleanTrade.java](file:///d:/project/work/risk-data-hub-lab/src/main/java/com/example/dynamicds/entity/CleanTrade.java)

## 7. 页面现在保留什么

页面文件：

- [index.html](file:///d:/project/work/risk-data-hub-lab/src/main/resources/static/index.html)

页面只保留：

- 数据源维护
  - 新增
  - 查看
  - 删除
- 一个同步按钮
  - 选择交易系统数据源
  - 指定分页大小
  - 触发同步

这样页面不会跑偏成大而全演示台。

## 8. 启动初始化

启动后 [PlatformBootstrapService.java](file:///d:/project/work/risk-data-hub-lab/src/main/java/com/example/dynamicds/service/PlatformBootstrapService.java) 会自动：

1. 自动创建 3 个 schema
2. 注册三套默认数据源
3. 创建三库表结构
4. 调用 Marketstack 拉股票数据
5. 按两套交易系统结构分别写入上游库
6. 写入中台字典和 Leaf 初始号段

默认配置在：

- [application.yml](file:///d:/project/work/risk-data-hub-lab/src/main/resources/application.yml)

Marketstack 配置也放在这里，默认从环境变量读取：

- `MARKETSTACK_ACCESS_KEY`

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
> 所以我给每个数据源配置了 datasourceType，同步时先按指定数据源分页拉取数据，再根据 datasourceType 走不同字段映射规则，最后统一落到中台标准交易表。  
> 启动时项目会先自动建 schema 和表，再调用 Marketstack 拉一批真实股票数据，按两个交易系统各自的字段结构灌入上游库。  
> 前台只保留数据源维护和一个同步按钮，这样更贴近真实工作里的同步入口。
