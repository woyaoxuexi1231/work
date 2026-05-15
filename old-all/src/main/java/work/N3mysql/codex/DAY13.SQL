DROP TABLE IF EXISTS d13_daily_sales;
CREATE TABLE d13_daily_sales
(
    stat_date DATE PRIMARY KEY,
    amount    DECIMAL(10, 2)
);
INSERT INTO d13_daily_sales
VALUES ('2026-05-01', 100),
       ('2026-05-02', 150),
       ('2026-05-03', 120),
       ('2026-05-04', 200);

SELECT stat_date,
       amount,
       LAG(amount) OVER (ORDER BY stat_date)          AS prev_amount,
       amount - LAG(amount) OVER (ORDER BY stat_date) AS diff_amount
FROM d13_daily_sales;

SELECT stat_date,
       amount,
       round(amount-lag(amount) over ())
FROM d13_daily_sales;