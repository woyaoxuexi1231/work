在京东这类电商场景下，用 Redis 做排行榜确实很常见，比如**商品销量榜、热榜、用户积分排名**等。我参与过的项目里就使用 Redis 的有序集合（Sorted Set / ZSET）来实现过，下面详细说说。

---

### 一、为什么用 Redis 有序集合？

Redis 的 Sorted Set 是一个**元素唯一、按 score 排序**的集合，它底层是跳表 + 哈希表，使得以下操作都非常高效：
- **更新分数**：O(log N)
- **查询排名**：O(log N)
- **获取 Top N**：O(log N + M)

和数据库 `ORDER BY` + `LIMIT` 相比，高并发下 Redis 可以轻松扛住每秒数万次的读写，且不会对数据库造成压力。

---

### 二、核心实现思路

**数据结构设计**

假设我们做「商品销量日榜」：
- Key：`rank:sales:daily:20260609`
- Member：商品 ID，如 `product:1001`
- Score：销量数值

**基本操作命令**

```bash
# 销量增加（商品每卖出一件）
ZINCRBY rank:sales:daily:20260609 1 product:1001

# 获取 Top 10
ZREVRANGE rank:sales:daily:20260609 0 9 WITHSCORES

# 查询某个商品的排名（从 0 开始，所以实际排名要 +1）
ZREVRANK rank:sales:daily:20260609 product:1001

# 查询某个商品的销量
ZSCORE rank:sales:daily:20260609 product:1001
```

业务代码（Java 使用 Spring Data Redis）示例：

```java
// 商品卖出，增加销量
redisTemplate.opsForZSet().incrementScore("rank:sales:daily:20260609", "product:1001", 1);

// 查询 Top 10
Set<ZSetOperations.TypedTuple<String>> top10 =
    redisTemplate.opsForZSet().reverseRangeWithScores("rank:sales:daily:20260609", 0, 9);

// 查询商品排名（返回的是索引，需 +1 得到真实排名）
Long rank = redisTemplate.opsForZSet().reverseRank("rank:sales:daily:20260609", "product:1001");
```

---

### 三、解决“相同分数”的排名问题

直接按销量排序时，如果销量相同，Redis 默认**按 member 字典序排列**，但这不符合业务“先到该销量的排前面”的需求。

我们可以通过**组合 score** 来精确控制：

```java
// score = 销量 * 10^13 + (最大时间戳 - 达成时间戳)
long sales = 100;
long achieveTime = System.currentTimeMillis(); // 毫秒
long score = sales * 10_000_000_000_000L + (9999999999999L - achieveTime);
redisTemplate.opsForZSet().add("rank:sales:daily:20260609", "product:1001", score);
```

这样，销量越高 score 越大，销量相同时**先达到的时间戳更小**，经过 `最大时间戳 - 达成时间戳` 换算后其分数反而更大，从而排得更靠前。取分数时只需将 score 除以 10^13 即可还原销量。

---

### 四、多维度排行榜与周期重置

- **日榜/周榜/月榜**：使用不同 Key 区分，如 `rank:sales:weekly:202623`（2026 年第 23 周）。
- **总榜**：可以通过 `ZUNIONSTORE` 将每日榜单取并集后合并分数，例如：
  ```bash
  ZUNIONSTORE rank:sales:monthly:202606 30 rank:sales:daily:20260601 ... rank:sales:daily:20260630 AGGREGATE SUM
  ```
  这样就能快速算出月销量总排名。

- **榜单重置**：每天凌晨切换新 Key，旧 Key 可以设置过期时间（如保留 7 天）自动清理。

---

### 五、高并发下的优化

1. **本地缓存 + 异步更新**  
   对于展示量极大的首页排行榜，可以先读 Redis，只有 ZINCRBY 等写操作直接进 Redis，读操作可加短时间本地缓存（如 Caffeine），减轻 Redis 压力。

2. **榜单截断**  
   如果只需要前 100 名，可以定期执行：
   ```bash
   ZREMRANGEBYRANK rank:sales:daily:20260609 0 -101
   ```
   保留前 100 名，避免集合无限膨胀。

3. **Pipeline 批量操作**  
   批量增加销量时用 Pipeline 减少网络往返。

---

### 六、面试中可能追问的点（提前准备好）

- **大 Key 问题**：如果榜单集合达到百万级，要警惕删除大 Key 时会阻塞 Redis。可以用 `UNLINK` 异步删除，或用 `ZREMRANGEBYRANK` 逐步清理。
- **一致性**：如果 Redis 数据丢失如何修复？一般榜单数据都是从数据库流水异步聚合写入，Redis 仅做缓存，可以全量或增量重算。
- **和数据库排行榜的对比**：Redis 在实时性和高并发下远优于 MySQL，但成本高，数据不永久保存。通常 Redis 放热数据，MySQL 做持久化。

---

以上就是我在项目中使用 Redis 实现排行榜的完整思路，从数据结构选择、基本操作、同分处理，到周期管理和性能优化，都有实操经验。如果有更细的场景，比如需要带权重的多维排序，也可以进一步展开。