# 电商测试库表结构与关系

```
  categories                products                 inventory
 ┌──────────────┐        ┌──────────────┐        ┌──────────────┐
 │ id (PK)      │──┐     │ id (PK)      │──┐     │ product_id   │
 │ name         │  │     │ name         │  │     │ stock_quantity│
 │ parent_id ───┘  │     │ category_id ─┘  │     │ locked_qty   │
 │ sort_order    │      │ price          │     │ version      │
 └──────────────┘      │ cost           │     │ updated_at   │
                       │ stock          │     └──────────────┘
  users                │ sales_count    │
 ┌──────────────┐      │ rating         │      reviews
 │ id (PK)      │──┐   │ is_on_sale     │     ┌──────────────┐
 │ name         │  │   │ created_at     │     │ id (PK)      │
 │ email        │  │   └──────────────┘     │ order_id     │──┐
 │ phone        │  │         │              │ product_id ──┘  │
 │ city         │  │         │              │ user_id ────────┤
 │ age          │  │   order_items          │ rating          │
 │ gender       │  │  ┌──────────────┐      │ content         │
 │ vip_level    │  │  │ id (PK)      │      │ is_anonymous   │
 │ balance      │  │  │ order_id ────┤──┐   │ created_at     │
 │ created_at   │  │  │ product_id ──┘  │   └──────────────┘
 │ status       │  │  │ quantity       │
 └──────────────┘  │  │ unit_price     │
        │          │  └──────────────┘
        │          │
     orders        │     payments
    ┌──────────────┐│    ┌──────────────┐      order_logs
    │ id (PK)      ││    │ id (PK)      │     ┌──────────────┐
    │ order_no     ││    │ order_id ────┘     │ id (PK)      │
    │ user_id ─────┘│    │ amount             │ order_id     │
    │ total_amount  │    │ method             │ action       │
    │ discount_amt  │    │ transaction_id     │ old_status   │
    │ pay_amount    │    │ status             │ new_status   │
    │ status        │    │ paid_at            │ operator     │
    │ created_at    │    └──────────────┘     │ remark       │
    │ updated_at    │                         │ created_at   │
    └──────────────┘                         └──────────────┘
```

## 字段说明

### users — 用户表 (10,000行)

| 字段 | 中文 | 备注 |
|---|---|---|
| id | 用户ID | 主键 |
| name | 姓名 | |
| email | 邮箱 | 5%为NULL |
| phone | 手机号 | 3%为NULL |
| city | 城市 | 部分为NULL |
| age | 年龄 | 18-70 |
| gender | 性别 | M/F/部分NULL |
| vip_level | 会员等级 | 0-5 |
| balance | 余额 | |
| created_at | 注册时间 | |
| status | 状态 | 1正常 0禁用 |

### categories — 分类表 (50行)

| 字段 | 中文 | 备注 |
|---|---|---|
| id | 分类ID | 主键 |
| name | 分类名 | 3级树：电子产品→手机→... |
| parent_id | 父分类ID | NULL=一级分类 |
| sort_order | 排序 | |

### products — 商品表 (1,000行)

| 字段 | 中文 | 备注 |
|---|---|---|
| id | 商品ID | 主键 |
| name | 商品名 | |
| category_id | 所属分类 | 关联categories.id |
| price | 售价 | |
| cost | 成本价 | 毛利率用 |
| stock | 库存数量 | 0=缺货 |
| sales_count | 销量 | 排序用 |
| rating | 评分 | 1.00-5.00 |
| description | 描述 | TEXT |
| is_on_sale | 是否上架 | 1上架 0下架 |
| created_at | 上架时间 | |

### orders — 订单表 (50,000行)

| 字段 | 中文 | 备注 |
|---|---|---|
| id | 订单ID | 主键 |
| order_no | 订单号 | 唯一 |
| user_id | 用户ID | 关联users.id |
| total_amount | 订单总额 | |
| discount_amount | 优惠金额 | |
| pay_amount | 实付金额 | total - discount |
| status | 订单状态 | pending/paid/shipped/completed/cancelled/refunded |
| created_at | 下单时间 | |
| updated_at | 更新时间 | |

### order_items — 订单明细表 (~150,000行)

| 字段 | 中文 | 备注 |
|---|---|---|
| id | 明细ID | 主键 |
| order_id | 订单ID | 关联orders.id |
| product_id | 商品ID | 关联products.id |
| quantity | 数量 | |
| unit_price | 单价 | |

### payments — 支付表 (~45,000行)

| 字段 | 中文 | 备注 |
|---|---|---|
| id | 支付ID | 主键 |
| order_id | 订单ID | 唯一 |
| amount | 支付金额 | |
| method | 支付方式 | alipay/wechat/credit_card/debit_card |
| transaction_id | 交易流水号 | |
| status | 支付状态 | success/failed/refunding/refunded |
| paid_at | 支付时间 | |

### inventory — 库存表 (1,000行)

| 字段 | 中文 | 备注 |
|---|---|---|
| product_id | 商品ID | 主键 |
| stock_quantity | 可用库存 | |
| locked_quantity | 锁定库存 | 秒杀预占 |
| version | 版本号 | 乐观锁 |
| updated_at | 更新时间 | |

### order_logs — 订单日志表 (~100,000+行, 分区)

| 字段 | 中文 | 备注 |
|---|---|---|
| id | 日志ID | 主键 |
| order_id | 订单ID | |
| action | 操作 | pay/ship/complete/cancel/refund |
| old_status | 旧状态 | |
| new_status | 新状态 | |
| operator | 操作人 | system/admin/user |
| remark | 备注 | |
| created_at | 操作时间 | 按年RANGE分区 |

### reviews — 评价表 (~30,000行)

| 字段 | 中文 | 备注 |
|---|---|---|
| id | 评价ID | 主键 |
| order_id | 订单ID | |
| product_id | 商品ID | |
| user_id | 用户ID | |
| rating | 评分 | 1-5 |
| content | 评价内容 | 部分为NULL |
| is_anonymous | 是否匿名 | 0实名 1匿名 |
| created_at | 评价时间 | |

---

# 窗口函数练习

### 题 1

运营要给每个用户打标——按消费总额排名。但有个问题：很多用户消费总额一样。

出三份榜单放一起对比：一份消费相同的给不同排名，一份消费相同的并列但下一个跳号，一份消费相同的并列且下一个不跳号。只看前 30 名。

<details>
<summary>参考</summary>

```sql
SELECT u.name                                         AS `用户名`,
       t.spent                                        AS `消费总额`,
       ROW_NUMBER() OVER (ORDER BY t.spent DESC)       AS `不重复排名`,
       RANK()       OVER (ORDER BY t.spent DESC)       AS `并列跳号排名`,
       DENSE_RANK() OVER (ORDER BY t.spent DESC)       AS `并列不跳号排名`
FROM (
    SELECT user_id, SUM(pay_amount) AS spent
    FROM orders
    WHERE status NOT IN ('pending', 'cancelled')
    GROUP BY user_id
) t
JOIN users u ON t.user_id = u.id
ORDER BY t.spent DESC
LIMIT 30;
```
</details>

### 题 2

风控怀疑有盗刷——某个用户突然一笔订单金额比上一单翻了 3 倍以上。找出所有这样的异常订单，列出用户名、这笔金额、上一笔金额、涨幅百分比。

<details>
<summary>参考</summary>

```sql
SELECT name   AS `用户名`,
       cur    AS `这笔金额`,
       prev   AS `上一笔金额`,
       pct    AS `涨幅百分比`
FROM (
    SELECT u.name,
           o.pay_amount AS cur,
           LAG(o.pay_amount) OVER (PARTITION BY o.user_id ORDER BY o.created_at) AS prev,
           ROUND((o.pay_amount - LAG(o.pay_amount) OVER (PARTITION BY o.user_id ORDER BY o.created_at))
                 / NULLIF(LAG(o.pay_amount) OVER (PARTITION BY o.user_id ORDER BY o.created_at), 0) * 100, 1) AS pct
    FROM orders o JOIN users u ON o.user_id = u.id
    WHERE o.status NOT IN ('pending', 'cancelled')
) t
WHERE prev IS NOT NULL AND pct > 200
ORDER BY pct DESC;
```
</details>
