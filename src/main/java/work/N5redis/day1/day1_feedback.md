# Redis 30天面试突击 - Day 1 评分报告

## 一、总体评分表

### 面试题部分（每题满分 20 分，共 100 分）

| 题号 | 原题摘要 | 得分 | 关键扣分维度 |
|------|----------|------|--------------|
| 1    | setnx 忘记设置过期时间后果 | 20/20 | 回答准确，明确指出了死锁风险 |
| 2    | 保证判断与设置的原子性 | 20/20 | 准确给出了 SET NX PX (setIfAbsent) 方案 |
| 3    | 业务超时导致锁误释放方案 | 20/20 | 代码模拟了看门狗逻辑，理解深刻 |
| 4    | Redisson 源码级可重入实现 | 20/20 | 准确点出 Hash 结构与 hincrby 命令 |
| 5    | 主从切换锁丢失折中方案 | 0/20 | **缺失该题回答** |
| **面试题总分** | | **80/100** | |

### 编程题部分（满分 100 分）

| 评分维度 | 满分 | 得分 | 扣分原因 |
|----------|------|------|----------|
| 正确性（核心逻辑） | 40 | 40 | 加锁超时、Lua 解锁原子性均实现正确 |
| 复杂度分析（时间/空间） | 20 | 20 | 时间 O(1)，空间 O(1) |
| 代码风格与可读性 | 20 | 20 | 结构清晰，Lua 脚本嵌入合理 |
| 鲁棒性（边界/异常） | 20 | 20 | 使用了 Lua 脚本防误删，安全性高 |
| **编程题总分** | **100** | **100/100** | |

### 最终综合总分
- **面试题得分**：80/100  
- **编程题得分**：100/100  
- **综合总分（折算为 200 分制）**：**180/200**  
- **一句话评价**：实战导向极强，通过代码模拟深刻理解了分布式锁的各类风险点，编程实现达到了生产级水平。

---

## 二、逐题精讲

### 面试题 1：setnx 忘记设置过期时间后果
- **你的答案摘要**：一旦成功后线程崩溃或忘记释放，锁将永远存在导致死锁。
- **评分**：20/20  
- **可吸收的标准答案**：
  > 如果忘记设置过期时间，且客户端在持有锁期间发生了宕机、重启或未捕获的异常，由于锁没有 TTL（生存时间），Redis 不会自动释放该 key。这会导致其他所有请求永远无法获取到锁，产生**永久性死锁**，除非人工介入手动删除。

### 面试题 2：保证“判断存在”与“设置时间”的原子性
- **你的答案摘要**：使用 `setIfAbsent` 并同时指定过期时间（SET NX PX）。
- **评分**：20/20  
- **可吸收的标准答案**：
  > 必须使用 Redis 的原生原子命令：`SET key value NX PX milliseconds`。在 Spring Data Redis 中对应 `opsForValue().setIfAbsent(key, value, timeout)`。**切忌**先 setnx 再执行 expire，因为这两个命令之间如果发生网络抖动或宕机，仍会产生死锁。

### 面试题 3：锁过期但业务未跑完怎么办？
- **你的答案摘要**：通过代码模拟了启动守护线程每 2 秒续期的看门狗逻辑。
- **评分**：20/20  
- **可吸收的标准答案**：
  > 1. **看门狗机制（Watchdog）**：如 Redisson 实现，在加锁成功时开启一个后台线程，每隔一段时间（通常是租约时间的 1/3）检查锁是否还存在，若存在则重置过期时间。
  > 2. **业务解耦**：尽量缩短锁内业务逻辑，或将大任务拆分为子任务。
  > 3. **容错处理**：若业务执行确实超过预期，需结合数据库唯一索引等兜底手段防止数据异常。

### 面试题 4：Redisson 源码级可重入实现原理
- **你的答案摘要**：内部通过 `hincrby` 对 value 进行 +1。
- **评分**：20/20  
- **可吸收的标准答案**：
  > Redisson 放弃了简单的 String 结构，改用 **Hash 结构** 存储锁：
  > - **Key**: 锁名称
  > - **Field**: 客户端 ID (UUID:ThreadID)
  > - **Value**: 重入计数
  > 当同一线程再次获取锁时，利用 Lua 脚本执行 `HINCRBY` 使计数值加 1；释放锁时 `HINCRBY -1`，直到计数值归零才真正执行 `DEL`。

### 面试题 5：主从切换锁丢失的折中方案（地狱级）
- **你的答案摘要**：未回答。
- **评分**：0/20  
- **可吸收的标准答案**：
  > 在不使用重量级的 Redlock 算法时，工程上常用的折中方案包括：
  > 1. **延迟切换**：在 Master 挂掉后，哨兵或集群不立即切换，而是等待一小段时间（如几百毫秒），尽量让 Slave 同步完数据。
  > 2. **客户端多读校验**：在获取锁后，去多个从库验证该 key 是否存在（虽非绝对安全但增加了可靠性）。
  > 3. **业务层幂等**：这是**最核心**的，分布式锁应作为性能优化手段，真正的安全性应由数据库层面的唯一约束或乐观锁来兜底。

---

## 三、编程题逐行点评

**原题**：使用 Spring Data Redis 实现基础分布式锁，含超时及防误删逻辑。

### 1. 正确性验证
- **加锁原子性**：[RedisLockUtil.java:23](file:///d:/project/demo/demo-java/work/src/main/java/work/N5redis/day1/RedisLockUtil.java#L23) 使用了带 Duration 的 `setIfAbsent`，完美。
- **解锁安全性**：[RedisLockUtil.java:36-40](file:///d:/project/demo/demo-java/work/src/main/java/work/N5redis/day1/RedisLockUtil.java#L36-L40) 编写了 Lua 脚本，实现了“判断当前线程标识是否一致”再删除的逻辑，有效防止了**锁被其他线程误删**的情况。

### 2. 复杂度原理
- **时间复杂度**：O(1)，Redis 的单 key 操作和简单脚本执行。
- **空间复杂度**：O(1)，仅占用一个 key 的存储空间。

### 3. 代码风格与工程原则
- **好的地方**：
  - 使用了 `Thread.currentThread().getName()` 作为唯一标识，逻辑正确。
  - 控制器中 [Day1Controller.java:114](file:///d:/project/demo/demo-java/work/src/main/java/work/N5redis/day1/Day1Controller.java#L114) 手动实现了看门狗模拟，非常有助于理解原理。
- **建议改进**：
  - [RedisLockUtil.java:23](file:///d:/project/demo/demo-java/work/src/main/java/work/N5redis/day1/RedisLockUtil.java#L23) 中 key 名称 "LOCK_KEY" 建议作为方法参数传入，提高通用性。
  - 在生产环境中，`Thread.getName()` 可能重复（如不同 JVM），建议结合 `UUID` 生成更唯一的客户端 ID。

### 4. 优化后的参考实现
```java
public Boolean unlock(String lockKey, String requestId) {
    String script = 
        "if redis.call('get', KEYS[1]) == ARGV[1] " +
        "then return redis.call('del', KEYS[1]) " +
        "else return 0 end";
    return stringRedisTemplate.execute(
        new DefaultRedisScript<>(script, Long.class),
        Collections.singletonList(lockKey),
        requestId
    ).equals(1L);
}
```

---

**面试官寄语**：
看到你手动撸了一个看门狗线程，我非常惊喜！这说明你已经跳出了“只会调包”的层次，开始思考底层组件是如何应对异常情况的。Day 1 基础非常牢固，继续保持这种“代码验证理论”的学习方式，后面关于 SkipList 和集群架构的挑战你会学得非常快！
