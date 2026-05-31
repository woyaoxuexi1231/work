#
#
# 出三份榜单放一起对比：一份消费相同的给不同排名，一份消费相同的并列但下一个跳号，一份消费相同的并列且下一个不跳号。只看前 30 名。
# 按消费总额排名
# 1.先从订单表找出所有用户的已经消费的订单


SELECT u.name,
       t.spent,
       ROW_NUMBER() OVER (ORDER BY t.spent DESC) AS rn,
       RANK() OVER (ORDER BY t.spent DESC)       AS rk,
       DENSE_RANK() OVER (ORDER BY t.spent DESC) AS dr
FROM (SELECT user_id, SUM(pay_amount) AS spent
      FROM orders
      WHERE status NOT IN ('pending', 'cancelled')
      GROUP BY user_id) t
         JOIN users u ON t.user_id = u.id
ORDER BY t.spent DESC
LIMIT 30;

#
# 控怀疑有盗刷——某个用户突然一笔订单金额比上一单翻了 3 倍以上。
# 找出所有这样的异常订单，列出用户名、这笔金额、上一笔金额、涨幅百分比。

# 现在要做就是对比用户的每一笔订单和上一笔订单的金额的涨幅百分比，如果涨幅百分比大于300%那么就属于异常

SELECT name, cur, prev, pct
FROM (SELECT u.name,
             o.id,
             o.pay_amount                                                          AS cur,
             # 用 LAG 获取上一笔订单的 pay_amount，记为 prev
             # 以用户id分组，按创建时间排序，获取当前行的上一行的金额
             LAG(o.pay_amount) OVER (PARTITION BY o.user_id ORDER BY o.created_at) AS prev,
             ROUND((o.pay_amount - LAG(o.pay_amount) OVER (PARTITION BY o.user_id ORDER BY o.created_at))
                       / NULLIF(LAG(o.pay_amount) OVER (PARTITION BY o.user_id ORDER BY o.created_at), 0) * 100,
                   1)                                                              AS pct
      FROM orders o
               JOIN users u ON o.user_id = u.id
      WHERE o.status NOT IN ('pending', 'cancelled')) t
WHERE prev IS NOT NULL
  AND pct > 200
ORDER BY pct DESC;