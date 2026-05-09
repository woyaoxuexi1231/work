package work.N5redis.day1;

import org.apache.poi.ss.formula.functions.T;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * @author hulei
 * @since 2026/5/2 8:57
 */
@RequestMapping("/redis/day1")
@RestController
public class Day1Controller {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RedisLockUtil redisLockUtil;

    // ========== 基础：忘记设置过期时间 ==========
    // 问题：一旦 setnx 成功后线程崩溃或忘记释放锁，锁将永远存在（死锁）
    @GetMapping("/demo/basic")
    public String basicDemo() throws InterruptedException {
        // 使用 setIfAbsent 就是 setnx，但这里故意不设置过期时间
        Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent("LOCK_KEY", Thread.currentThread().getName());
        if (Boolean.TRUE.equals(lockAcquired)) {
            try {
                // 模拟业务执行，可能抛出异常
                System.out.println("获取锁成功，执行业务...");
                // 假设这里抛出了未捕获的异常，或服务突然宕机
                // throw new RuntimeException("模拟异常");
                return "业务执行完成，锁已正常释放";
            } finally {
                // 正常释放锁
                redisTemplate.delete("LOCK_KEY");
                System.out.println("锁已释放");
            }
        } else {
            return "获取锁失败，其他客户端持有锁（可能永远无法释放）";
        }
    }

    // ========== 中级：原子性设置锁与过期时间 ==========
    // 正确做法：使用 setIfAbsent 并同时指定过期时间（SET NX PX）
    @GetMapping("/demo/correct")
    public String correctLock() throws InterruptedException {
        // 原子性：判断 key 不存在 + 设置值 + 设置过期时间（10秒）
        Boolean lockAcquired = redisTemplate.opsForValue()
                .setIfAbsent("LOCK_KEY2", Thread.currentThread().getName(), Duration.ofSeconds(10));
        if (Boolean.TRUE.equals(lockAcquired)) {
            try {
                // 执行业务（务必在10秒内完成，否则锁自动释放会引发高级问题）
                System.out.println("原子锁获取成功，有效期10秒");
                TimeUnit.SECONDS.sleep(2); // 模拟业务耗时
                return "业务完成，锁已自动到期或手动释放";
            } finally {
                // 释放锁前需要校验是否仍属于自己的锁（避免误删）
                String currentId = redisTemplate.opsForValue().get("LOCK_KEY2");
                if (Thread.currentThread().getName().equals(currentId)) {
                    redisTemplate.delete("LOCK_KEY2");
                    System.out.println("锁已手动释放");
                }
            }
        } else {
            return "锁已被占用";
        }
    }

    // ========== 高级：锁过期但业务未完成 + 看门狗模拟 ==========
    // 场景：业务耗时 > 锁过期时间，导致锁自动释放，其他线程拿到锁造成数据覆盖
    @GetMapping("/demo/expire-problem")
    public String expireProblem() throws InterruptedException {
        // 设置锁过期时间为 3 秒
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent("LOCK_KEY3", Thread.currentThread().getName(), Duration.ofSeconds(3));
        if (Boolean.TRUE.equals(locked)) {
            try {
                System.out.println("锁获取成功，有效期3秒，但业务需要5秒...");
                // 业务耗时 5 秒，大于 3 秒
                for (int i = 1; i <= 5; i++) {
                    TimeUnit.SECONDS.sleep(1);
                    System.out.println("业务进行中..." + i + "秒");
                    // 注意：3 秒后锁已被 Redis 自动删除，此时其他线程可以获取到锁
                }
                return "业务完成，但期间锁已超时释放（数据可能被其他线程污染）";
            } finally {
                // 释放时可能已经把其他线程的锁删除了（更严重的问题）
                String currentId = redisTemplate.opsForValue().get("LOCK_KEY3");
                if (Thread.currentThread().getName().equals(currentId)) {
                    redisTemplate.delete("LOCK_KEY3");
                }
            }
        } else {
            return "获取锁失败";
        }
    }

    // 解决方案模拟：简易看门狗（定期续期）
    // 实际生产中推荐 Redisson，此处展示核心思想：启动一个守护线程每 2 秒续期一次
    @GetMapping("/demo/watchdog")
    public String withWatchDog() throws InterruptedException {
        // 锁过期时间 3 秒
        String name = Thread.currentThread().getName();

        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent("LOCK_KEY3", name, Duration.ofSeconds(3));
        if (Boolean.TRUE.equals(locked)) {
            // 启动看门狗线程，每 2 秒续期一次（使锁一直有效直到业务结束）
            Thread watchdog = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        TimeUnit.SECONDS.sleep(2);
                        // 续期前提：锁还存在且是自己的
                        String curId = redisTemplate.opsForValue().get("LOCK_KEY3");
                        if (name.equals(curId)) {
                            // 通过设置相同值并重置过期时间来实现续期（注意原子性）
                            redisTemplate.expire("LOCK_KEY3", 3, TimeUnit.SECONDS);
                            System.out.println("看门狗续期，剩余3秒");
                        } else {
                            break;
                        }
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            });
            watchdog.setDaemon(true); // 守护线程，随主线程结束而结束
            watchdog.start();

            try {
                System.out.println("锁获取成功，看门狗已启动，业务执行 8 秒...");
                for (int i = 1; i <= 8; i++) {
                    TimeUnit.SECONDS.sleep(1);
                    System.out.println("业务进行中..." + i);
                }
                return "业务完成，看门狗保证了锁不会提前释放";
            } finally {
                // 中断看门狗线程
                watchdog.interrupt();
                // 释放锁（仍然需要校验）
                String curId = redisTemplate.opsForValue().get("LOCK_KEY3");
                if (name.equals(curId)) {
                    redisTemplate.delete("LOCK_KEY3");
                    System.out.println("主业务结束，手动释放锁");
                }
            }
        } else {
            return "获取锁失败";
        }
    }


    @GetMapping("/demo/redisson")
    public void redisson() {
        // redisson 内部通过 redis.call('hincrby', KEYS[1], ARGV[2], 1); 对value进行+1实现可重入操作
        RLock lock4 = redissonClient.getLock("lock4");
        if (lock4.tryLock()) {
            try {
                if (lock4.tryLock()) {
                    try {
                        System.out.println("重入成功");
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        lock4.unlock();
                    }
                }
                System.out.println("获取锁成功");
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                lock4.unlock();
            }
        }
    }


    @GetMapping("/demo/util")
    public void utilTest() {
        Boolean b = redisLockUtil.tryLock();
        if (b) {
            try {
                System.out.println("获取锁成功");
                Thread.sleep(5000);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Boolean unlock = redisLockUtil.unlock();
                if (unlock) {
                    System.out.println("释放锁成功");
                } else {
                    System.out.println("释放锁失败");
                }
            }
        }
    }
}
