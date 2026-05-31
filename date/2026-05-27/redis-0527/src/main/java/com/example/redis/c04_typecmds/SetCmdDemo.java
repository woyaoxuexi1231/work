package com.example.redis.c04_typecmds;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 4. 各类型核心命令 —— Set
 * <p>
 * Set 是无序、不重复的字符串集合。
 * <p>
 * 底层编码：
 * - intset: 全整数且元素少时（≤ set-max-intset-entries，默认 512）
 * - hashtable: 其他情况
 * <p>
 * 核心命令：
 * - 写入：SADD
 * - 删除：SREM
 * - 判断：SISMEMBER、SMISMEMBER（Redis 6.2+）
 * - 查询：SMEMBERS、SCARD、SRANDMEMBER、SPOP
 * - 集合运算：SINTER（交集）、SUNION（并集）、SDIFF（差集）
 * - 遍历：SSCAN
 * <p>
 * 应用场景：
 * - 标签系统：每篇文章的标签用 Set 存储
 * - 共同好友：两个用户的关注集合求交集
 * - 抽奖系统：SADD 参与者，SPOP/SRANDMEMBER 抽奖
 * - 去重：天然不重复
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SetCmdDemo {

    private final StringRedisTemplate redisTemplate;

    /**
     * Set 基础操作
     * <p>
     * SADD: 添加一个或多个成员（已存在的自动忽略）
     * SREM: 删除成员
     * SISMEMBER: 判断成员是否存在（O(1) 时间复杂度）
     * SMEMBERS: 获取所有成员（慎用：大集合会阻塞）
     * SCARD: 获取成员数量
     * SRANDMEMBER: 随机返回成员（不删除）
     * SPOP: 随机弹出成员（删除）
     */
    public String basicOps() {
        SetOperations<String, String> ops = redisTemplate.opsForSet();

        // SADD: 添加成员
        ops.add("set:tags:article:1", "Java", "Redis", "Spring", "分布式");
        ops.add("set:tags:article:2", "Java", "MySQL", "Spring", "微服务");

        // SMEMBERS: 获取所有成员
        Set<String> tags = ops.members("set:tags:article:1");
        log.info("[SMEMBERS] 文章1标签: {}", tags);

        // SCARD: 成员数量
        Long count = ops.size("set:tags:article:1");
        log.info("[SCARD] 文章1标签数: {}", count);

        // SISMEMBER: 判断是否存在
        Boolean hasJava = ops.isMember("set:tags:article:1", "Java");
        Boolean hasGo = ops.isMember("set:tags:article:1", "Go");
        log.info("[SISMEMBER] Java={}, Go={}", hasJava, hasGo);

        // SRANDMEMBER: 随机返回成员（不删除）
        String random = ops.randomMember("set:tags:article:1");
        log.info("[SRANDMEMBER] 随机标签: {}", random);

        // SRANDMEMBER 多个（可能重复）
        List<String> randomList = ops.randomMembers("set:tags:article:1", 3);
        log.info("[SRANDMEMBER x3] {}", randomList);

        // SRANDMEMBER distinct（不重复）
        Set<String> distinctRandom = ops.distinctRandomMembers("set:tags:article:1", 3);
        log.info("[SRANDMEMBER x3 distinct] {}", distinctRandom);

        // SPOP: 随机弹出（删除并返回）
        String popped = ops.pop("set:tags:article:1");
        log.info("[SPOP] 弹出: {}, 剩余标签数: {}", popped, ops.size("set:tags:article:1"));

        // SREM: 删除指定成员
        ops.remove("set:tags:article:1", "分布式");
        log.info("[SREM] 删除'分布式'后: {}", ops.members("set:tags:article:1"));

        redisTemplate.delete("set:tags:article:1");
        redisTemplate.delete("set:tags:article:2");

        return "tags=" + tags + ", popped=" + popped;
    }

    /**
     * Set 集合运算 —— 最强大的能力
     * <p>
     * SINTER: 交集（同时属于两个集合的成员）
     * SUNION: 并集（属于任一集合的成员）
     * SDIFF: 差集（属于第一个但不属于第二个的成员）
     * <p>
     * SINTERSTORE / SUNIONSTORE / SDIFFSTORE:
     * 将运算结果存储到目标键（覆盖已有值）
     * <p>
     * 应用：
     * - 共同好友 = SINTER user:A:friends user:B:friends
     * - 推荐好友 = SDIFF user:B:friends user:A:friends（B关注了但A没关注的）
     * - 合并标签 = SUNION article:1:tags article:2:tags
     */
    public String setOperations() {
        SetOperations<String, String> ops = redisTemplate.opsForSet();

        // 准备数据：用户关注关系
        ops.add("set:user:A:following", "B", "C", "D", "E");
        ops.add("set:user:B:following", "A", "C", "F", "G");

        // SINTER: 交集 —— 共同关注
        Set<String> common = ops.intersect("set:user:A:following", "set:user:B:following");
        log.info("[SINTER] A和B共同关注: {}", common); // [C]

        // SUNION: 并集 —— 合并关注
        Set<String> all = ops.union("set:user:A:following", "set:user:B:following");
        log.info("[SUNION] A和B的全部关注: {}", all);

        // SDIFF: 差集 —— A关注了但B没关注的
        Set<String> diff = ops.difference("set:user:A:following", "set:user:B:following");
        log.info("[SDIFF] A关注但B没关注: {}", diff); // [D, E]

        // SINTERSTORE: 将交集结果存到新键
        ops.intersectAndStore("set:user:A:following", "set:user:B:following", "set:common");
        log.info("[SINTERSTORE] 共同关注已存储, count={}", ops.size("set:common"));

        redisTemplate.delete("set:user:A:following");
        redisTemplate.delete("set:user:B:following");
        redisTemplate.delete("set:common");

        return "共同关注=" + common + ", 差集=" + diff;
    }

    /**
     * Set 应用场景：抽奖系统
     * <p>
     * 思路：
     * 1. 用户参与 → SADD lottery:2024 user_id
     * 2. 查看参与人数 → SCARD lottery:2024
     * 3. 随机抽取中奖者 → SRANDMEMBER（不删除，可重复抽奖）
     *                     或 SPOP（删除，不可重复中奖）
     */
    public String lotteryDemo() {
        SetOperations<String, String> ops = redisTemplate.opsForSet();

        // 用户参与抽奖
        ops.add("set:lottery", "user:1001", "user:1002", "user:1003", "user:1004", "user:1005");

        Long participants = ops.size("set:lottery");
        log.info("[抽奖] 参与人数: {}", participants);

        // 随机抽取 2 名中奖者（SRANDMEMBER 不删除，可重复中奖）
        Set<String> winners = ops.distinctRandomMembers("set:lottery", 2);
        log.info("[抽奖] 中奖者: {}", winners);

        // 抽取 1 名（SPOP 删除，保证不重复中奖）
        String grandPrize = ops.pop("set:lottery");
        log.info("[大奖] 获得者: {}, 剩余参与人数: {}", grandPrize, ops.size("set:lottery"));

        redisTemplate.delete("set:lottery");
        return "中奖者=" + winners + ", 大奖=" + grandPrize;
    }
}
