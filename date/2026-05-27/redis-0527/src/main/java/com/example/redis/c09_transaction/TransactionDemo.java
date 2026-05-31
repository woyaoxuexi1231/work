package com.example.redis.c09_transaction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 9. 事务
 * <p>
 * Redis 事务通过 MULTI / EXEC 实现，保证命令按顺序原子执行。
 * <p>
 * 核心命令：
 * - MULTI: 开始事务（后续命令进入队列）
 * - EXEC: 执行事务（队列中的命令依次执行）
 * - DISCARD: 放弃事务（清空队列）
 * - WATCH: 监视键（乐观锁，键被修改则事务失败）
 * - UNWATCH: 取消所有监视
 * <p>
 * 事务特性：
 * 1. 命令入队：MULTI 后的命令不立即执行，而是进入队列
 * 2. 原子执行：EXEC 时队列中的命令依次执行，不会被其他命令插入
 * 3. 无回滚：运行时错误不会回滚已执行的命令（与关系型数据库不同！）
 * 4. 编译时错误：语法错误会导致整个事务被拒绝
 * <p>
 * WATCH 乐观锁：
 * - WATCH 监视的键在 EXEC 前被其他客户端修改，则 EXEC 返回 nil
 * - 实现 CAS（Compare-And-Swap）语义
 * - 适用于：余额扣减、库存扣减等需要检查后再操作的场景
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionDemo {

    private final StringRedisTemplate redisTemplate;

    /**
     * 基础事务演示
     * <p>
     * MULTI → 命令入队 → EXEC
     * 所有命令在 EXEC 时一次性执行，中间不会被其他客户端的命令打断。
     */
    public String basicTransaction() {
        // 清空测试数据
        redisTemplate.delete("tx:account:A");
        redisTemplate.delete("tx:account:B");

        ValueOperations<String, String> ops = redisTemplate.opsForValue();
        ops.set("tx:account:A", "1000");
        ops.set("tx:account:B", "500");

        // 使用 SessionCallback 执行事务
        // SessionCallback 保证所有命令在同一个连接中执行
        List<Object> results = redisTemplate.execute(new SessionCallback<List<Object>>() {
            @Override
            @SuppressWarnings("unchecked")
            public List<Object> execute(org.springframework.data.redis.core.RedisOperations operations) {
                // MULTI: 开始事务
                operations.multi();

                // 命令入队（不会立即执行）
                operations.opsForValue().increment("tx:account:A", -200);
                operations.opsForValue().increment("tx:account:B", 200);

                // EXEC: 执行事务
                return operations.exec();
            }
        });

        String balanceA = ops.get("tx:account:A");
        String balanceB = ops.get("tx:account:B");
        log.info("[事务] A余额={}, B余额={}, 执行结果={}", balanceA, balanceB, results);

        redisTemplate.delete("tx:account:A");
        redisTemplate.delete("tx:account:B");

        return "A=" + balanceA + ", B=" + balanceB;
    }

    /**
     * WATCH 乐观锁 —— 转账场景
     * <p>
     * 流程：
     * 1. WATCH 监视账户键
     * 2. 读取账户余额
     * 3. 检查余额是否充足
     * 4. MULTI + 扣款 + EXEC
     * 5. 若 EXEC 返回 nil，说明监视的键被修改，重试
     * <p>
     * 这实现了无锁的 CAS 乐观并发控制。
     * 适合读多写少、冲突概率低的场景。
     */
    public String watchOptimisticLock() {
        redisTemplate.delete("tx:lock:A");
        redisTemplate.delete("tx:lock:B");

        ValueOperations<String, String> ops = redisTemplate.opsForValue();
        ops.set("tx:lock:A", "1000");
        ops.set("tx:lock:B", "500");

        int maxRetries = 3;
        boolean success = false;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                List<Object> results = redisTemplate.execute(new SessionCallback<List<Object>>() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public List<Object> execute(org.springframework.data.redis.core.RedisOperations operations) {
                        // WATCH: 监视账户键
                        operations.watch(Arrays.asList("tx:lock:A", "tx:lock:B"));

                        // 读取余额
                        String aBalance = (String) operations.opsForValue().get("tx:lock:A");
                        int a = Integer.parseInt(aBalance);

                        if (a < 200) {
                            // 余额不足，取消事务
                            operations.unwatch();
                            return null;
                        }

                        // 开始事务
                        operations.multi();
                        operations.opsForValue().increment("tx:lock:A", -200);
                        operations.opsForValue().increment("tx:lock:B", 200);

                        // EXEC: 如果监视的键被其他客户端修改，返回 nil
                        return operations.exec();
                    }
                });

                if (results != null) {
                    success = true;
                    log.info("[WATCH] 转账成功, 尝试次数={}", attempt + 1);
                    break;
                } else {
                    log.info("[WATCH] 事务冲突, 重试第{}次", attempt + 1);
                }
            } catch (Exception e) {
                log.warn("[WATCH] 异常: {}", e.getMessage());
            }
        }

        String balanceA = ops.get("tx:lock:A");
        String balanceB = ops.get("tx:lock:B");
        log.info("[WATCH] 最终余额 A={}, B={}", balanceA, balanceB);

        redisTemplate.delete("tx:lock:A");
        redisTemplate.delete("tx:lock:B");

        return "success=" + success + ", A=" + balanceA + ", B=" + balanceB;
    }
}
