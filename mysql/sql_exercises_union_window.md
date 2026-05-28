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

| 关系 | 类型 |
|---|---|
| users → orders | 1:N |
| orders → order_items | 1:N |
| order_items → products | N:1 |
| products → categories | N:1 |
| categories → categories | 自引用 (3级树) |
| orders → payments | 1:1 |
| orders → order_logs | 1:N |
| orders → reviews | 1:N |
| products → inventory | 1:1 |

| 表 | 行数 |
|---|---|
| users | 10,000 |
| categories | 50 |
| products | 1,000 |
| orders | 50,000 |
| order_items | ~150,000 |
| payments | ~45,000 |
| inventory | 1,000 |
| order_logs | ~100,000+ (分区表) |
| reviews | ~30,000 |

---

# 窗口函数练习

### 题 1

运营要给每个用户打标——按消费总额排名。但有个问题：很多用户消费总额一样。

出三份榜单放一起对比：一份消费相同的给不同排名，一份消费相同的并列但下一个跳号，一份消费相同的并列且下一个不跳号。只看前 30 名。

<details>
<summary>参考</summary>

```sql
SELECT u.name, t.spent,
    ROW_NUMBER() OVER (ORDER BY t.spent DESC) AS rn,
    RANK()       OVER (ORDER BY t.spent DESC) AS rk,
    DENSE_RANK() OVER (ORDER BY t.spent DESC) AS dr
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
SELECT name, cur, prev, pct
FROM (
    SELECT u.name, o.id,
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
