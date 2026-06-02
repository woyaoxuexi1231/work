# 练习 09：标量子查询与列子查询

> 基于 `ecommerce` 数据库（`seed_data.py` 生成）

## 相关表结构

| 表 | 关键列 |
|---|---|
| `products` | id, name, category_id, price, cost, sales_count, rating |
| `orders` | id, order_no, user_id, total_amount, pay_amount, status |
| `order_items` | id, order_id, product_id, quantity, unit_price |
| `users` | id, name, email, city, age, vip_level, balance |
| `categories` | id, name, parent_id |
| `reviews` | id, product_id, user_id, rating, content |

---

## 概念速览

| 类型 | 返回 | 常用位置 | 典型关键词 |
|------|------|----------|-----------|
| **标量子查询** | 1 行 1 列 | SELECT / WHERE / HAVING | `=`, `>`, `<`, `>=`, `<=`, `<>` |
| **列子查询** | N 行 1 列 | WHERE / HAVING | `IN`, `NOT IN`, `ANY`, `SOME`, `ALL` |
| **行子查询** | 1 行 N 列 | WHERE | `=`, `<>`（少用） |
| **表子查询** | N 行 N 列 | FROM / JOIN | 派生表 |

**关键区分**：

- **非相关子查询（Uncorrelated）**：子查询独立于外层，只执行一次。
- **相关子查询（Correlated）**：子查询引用外层列，每行都需要重新执行。

---

## 练习 1：标量子查询 — SELECT 子句中

**场景**：查询每个商品的名称、价格，以及该商品所属分类的平均价格。

```sql
SELECT
    p.name,
    p.price,
    (SELECT ROUND(AVG(p2.price), 2)
     FROM products p2
     WHERE p2.category_id = p.category_id
    ) AS category_avg_price
FROM products p
ORDER BY p.category_id, p.price DESC
LIMIT 20;
```

**要点**：

- SELECT 中的子查询必须返回**单行单列**（标量）。
- `p2.category_id = p.category_id` 是相关条件，每行执行一次。
- MySQL 会对相同 category_id 的结果做缓存优化。

---

## 练习 2：标量子查询 — WHERE 中比较运算符

**场景**：查询价格高于所有商品平均价格的商品。

```sql
SELECT name, price, category_id
FROM products
WHERE price > (SELECT AVG(price) FROM products)
ORDER BY price DESC
LIMIT 20;
```

**要点**：

- 子查询不引用外层列 → **非相关子查询**，只执行一次。
- `>`、`<`、`=`、`>=`、`<=`、`<>` 都可以接标量子查询。

---

## 练习 3：标量子查询 — WHERE 中相关子查询

**场景**：查询评分高于该商品所属分类平均评分的商品。

```sql
SELECT name, rating, category_id
FROM products p
WHERE rating > (
    SELECT AVG(p2.rating)
    FROM products p2
    WHERE p2.category_id = p.category_id
      AND p2.rating > 0          -- 排除无评分的商品
)
ORDER BY category_id, rating DESC
LIMIT 20;
```

**要点**：

- 子查询引用外层 `p.category_id` → **相关子查询**。
- 每行外层数据都会驱动一次子查询执行。
- 数据量大时建议用 `JOIN` + `GROUP BY` 改写（后续练习会涉及）。

---

## 练习 4：列子查询 — IN

**场景**：查询购买过"手机"类商品的用户。

```sql
SELECT id, name, city
FROM users
WHERE id IN (
    SELECT DISTINCT o.user_id
    FROM orders o
    JOIN order_items oi ON o.id = oi.order_id
    WHERE oi.product_id IN (
        SELECT id
        FROM products
        WHERE category_id = (
            SELECT id FROM categories WHERE name = '手机'
        )
    )
)
LIMIT 20;
```

**要点**：

- `IN` 右边是一个**列子查询**（N 行 1 列）。
- 多层嵌套：`categories → products → order_items → orders → users`。
- `NOT IN` 同理，但注意子查询结果中不能有 NULL。

---

## 练习 5：列子查询 — `> ALL`

**场景**：查询价格高于所有"耳机"类商品价格的其他商品。

```sql
SELECT name, price
FROM products
WHERE price > ALL (
    SELECT price
    FROM products
    WHERE category_id = (
        SELECT id FROM categories WHERE name = '耳机'
    )
)
ORDER BY price DESC
LIMIT 20;
```

**要点**：

- `> ALL` = 大于子查询结果中的**每一个值** = 大于最大值。
- `< ALL` = 小于子查询结果中的每一个值 = 小于最小值。
- 等价于 `> (SELECT MAX(price) FROM ...)`，但 `ALL` 语义更直观。

---

## 练习 6：列子查询 — `< ANY` / `SOME`

**场景**：查询"平板"类中，价格低于任意一款"电脑"类商品的平板。

```sql
SELECT name, price
FROM products
WHERE category_id = (SELECT id FROM categories WHERE name = '平板')
  AND price < ANY (
      SELECT price
      FROM products
      WHERE category_id = (
          SELECT id FROM categories WHERE name = '电脑'
      )
  )
ORDER BY price DESC;
```

**要点**：

- `< ANY` = 只要小于子查询结果中的**某一个值** = 小于最大值。
- `> ANY` = 只要大于子查询结果中的某一个值 = 大于最小值。
- `SOME` 是 `ANY` 的别名，语义完全相同。
- 等价关系：
  - `x > ANY (subquery)` ⟺ `x > (SELECT MIN(...) FROM subquery)`
  - `x < ANY (subquery)` ⟺ `x < (SELECT MAX(...) FROM subquery)`

---

## 练习 7：综合 — 标量 + 列子查询

**场景**：查询下单金额超过该用户历史平均消费金额的订单。

```sql
SELECT
    o.id AS order_id,
    o.user_id,
    o.pay_amount,
    (SELECT ROUND(AVG(o2.pay_amount), 2)
     FROM orders o2
     WHERE o2.user_id = o.user_id
       AND o2.status IN ('paid', 'shipped', 'completed')
    ) AS user_avg_pay
FROM orders o
WHERE o.status IN ('paid', 'shipped', 'completed')
  AND o.pay_amount > (
      SELECT AVG(o3.pay_amount)
      FROM orders o3
      WHERE o3.user_id = o.user_id
        AND o3.status IN ('paid', 'shipped', 'completed')
  )
ORDER BY o.pay_amount DESC
LIMIT 20;
```

**要点**：

- SELECT 和 WHERE 中各有一个**相同的相关子查询**——MySQL 8.0+ 可用 CTE 优化（后续练习）。
- 限制状态为 `paid/shipped/completed`，排除 pending/cancelled/refunded。
- 用户历史均值只统计有效订单。

---

## `ANY` / `ALL` 速查表

| 表达式 | 等价于 |
|--------|--------|
| `x > ALL (subquery)` | `x > (SELECT MAX(...) FROM subquery)` |
| `x < ALL (subquery)` | `x < (SELECT MIN(...) FROM subquery)` |
| `x > ANY (subquery)` | `x > (SELECT MIN(...) FROM subquery)` |
| `x < ANY (subquery)` | `x < (SELECT MAX(...) FROM subquery)` |
| `x = ANY (subquery)` | `x IN (subquery)` |
| `x <> ALL (subquery)` | `x NOT IN (subquery)` |

**注意**：`IN` / `= ANY` 遇到子查询结果含 NULL 时行为不同：

- `x IN (1, 2, NULL)` → 可能返回 TRUE
- `x <> ALL (1, 2, NULL)` → 永远返回 NULL（因为 `x <> NULL` 是 NULL）

---

## 常见坑

1. **标量子查询返回多行** → 运行时错误 `Subquery returns more than 1 row`。用 `LIMIT 1` 或聚合函数保底。
2. **NOT IN + NULL** → 如果子查询结果包含 NULL，整个 `NOT IN` 返回空集。用 `NOT EXISTS` 替代（后续练习）。
3. **相关子查询性能** → 数据量大时每行执行一次子查询会很慢，优先考虑 `JOIN` + `GROUP BY` 改写。
4. **列子查询中 `= ANY` vs `IN`** → 语义相同，但 `IN` 可读性更好，推荐用 `IN`。
