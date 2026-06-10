你这个问题问得特别好，很多人在用了 MyBatis 之后都会有类似的“错觉”。答案很明确：

**MySQL 本身没有“一对多”或“多对多”的查询，它只能返回一张扁平的二维表。**  
MyBatis 的 `collection` 和 `association` 把这件事做得太自然了，才让你感觉像是数据库自己在做嵌套。

---

### 1. MySQL 的真实能力：只有行和列
你给 MySQL 发任意一条 SQL，无论多复杂的关联，它返回给你的永远是一个**由行和列组成的矩形结果集**。  
比如经典的“用户—订单”一对多查询：
```sql
SELECT u.id, u.name, o.id AS order_id, o.amount
FROM user u
LEFT JOIN orders o ON u.id = o.user_id;
```
返回的结果是：
| id | name | order_id | amount |
|----|------|----------|--------|
| 1  | 张三 | 101      | 99     |
| 1  | 张三 | 102      | 199    |
| 2  | 李四 | 103      | 50     |

你看，**用户“张三”出现了两行**，数据库只是把两张表按条件拼成了一张大宽表，没有自动把它合并成 `{name:"张三", orders:[...]}` 这样的结构。

所以，MySQL 根本不认识什么叫“一个用户对应多个订单的对象”。它只是在做**关系运算**，结果还是**关系（表）**。

### 2. MyBatis 帮你做的“魔法”
你用的 `collection`（一对多）和 `association`（一对一）就负责把上面那张扁平的表格，**在 Java 内存里**重新组装成你期望的对象树。

- `association`：把多列映射到一个嵌套对象。  
  比如把 `u.id, u.name` 映射为 `User` 对象。

- `collection`：在映射时根据某个“分组键”（比如 `id` 列），把属于同一父对象的多个子行，自动装进一个 `List` 里。  
  比如上面的结果集，MyBatis 看到 `id=1` 的行有两条，它就把 `order_id=101` 和 `102` 放进张三的 `orders` 列表，而 `id=2` 只有一条，就放进李四的列表。

**所以，你的“错觉”完全是因为 MyBatis 封装得太好了——你用 Java 对象的方式去思考，而 MyBatis 让你几乎感觉不到中间那张扁平的中间表存在。**

### 3. MySQL 真的不能直接返回嵌套结果吗？
现在也不能说完全没有，但和你理解的“一对多查询”不是一回事：

- **JSON 聚合函数（MySQL 5.7+ / 8.0）**  
  你可以用 `JSON_ARRAYAGG` 和 `JSON_OBJECT` 在 SQL 里直接拼出一个 JSON 数组：
  ```sql
  SELECT u.id, u.name,
         JSON_ARRAYAGG(JSON_OBJECT('order_id', o.id, 'amount', o.amount)) AS orders
  FROM user u
  LEFT JOIN orders o ON u.id = o.user_id
  GROUP BY u.id, u.name;
  ```
  这样数据库确实返回了一个包含嵌套数组的 JSON 列。  
  但这本质上是**把应用层的组装逻辑搬到了 SQL 里**，MySQL 本身的核心模型还是关系表，并没有变成面向对象数据库。而且这种用法主要用于直接输出 JSON 给前端，不是 MyBatis 那种自动映射成 Java 集合对象的机制。

- **多值索引与数组（MySQL 8.0.17+ 的 Multi-Valued Indexes）**  
  那是针对 JSON 数组列索引的，和查询的嵌套返回无关。

所以可以这样总结：
> **关系型数据库只负责“连接”，不负责“嵌套”。把扁平表变成对象的活，是 MyBatis 替你干的。**

### 你的“错觉”其实是对 MyBatis 最大的褒奖
框架设计得好，就会让人以为这是底层本来就有的能力。你当时想不起来的另一个标签就是 `association`，它对应一对一映射，`collection` 对应一对多。多对多在 MyBatis 里也是用 `collection` 来实现的（只是中间多张关联表，SQL 里多关联一次而已），同样不是 MySQL 原生提供的。

如果你有正在写的映射文件或者一个具体的例子，我可以帮你拆解一下从 SQL 到 Java 对象的完整转换过程。