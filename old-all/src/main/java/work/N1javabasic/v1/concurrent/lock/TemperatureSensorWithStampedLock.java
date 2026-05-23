package work.N1javabasic.v1.concurrent.lock;

import java.util.concurrent.locks.StampedLock;
import java.util.concurrent.TimeUnit;

/**
 * 温度传感器监控 —— 使用 StampedLock
 * 
 * 业务：写线程模拟传感器，定时更新温度；多个读线程频繁查询最新温度。
 * StampedLock 支持三种模式：
 *  - 写锁（writeLock）
 *  - 悲观读锁（readLock）
 *  - 乐观读（tryOptimisticRead）
 */
public class TemperatureSensorWithStampedLock {

    // 传感器数据
    private double temperature;
    private long timestamp;

    private final StampedLock stampedLock = new StampedLock();

    /**
     * 写操作：更新温度（模拟传感器上报）
     * 使用写锁，保证独占写入。
     */
    public void updateTemperature(double newTemp) {
        long stamp = stampedLock.writeLock(); // 获取写锁，返回 stamp
        try {
            // 模拟传感器采集耗时
            TimeUnit.MILLISECONDS.sleep(200);
            this.temperature = newTemp;
            this.timestamp = System.currentTimeMillis();
            System.out.printf("[写入] 更新温度: %.1f°C, 时间戳: %d, 当前线程: %s%n",
                    newTemp, timestamp, Thread.currentThread().getName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            stampedLock.unlockWrite(stamp); // 释放写锁
        }
    }

    /**
     * 读操作（悲观读锁方式）
     * 适用于读多写少，但与写锁互斥。当乐观读失败时可回退至此。
     */
    public double readWithPessimisticLock() {
        long stamp = stampedLock.readLock(); // 获取悲观读锁
        try {
            // 模拟一些处理时间
            TimeUnit.MILLISECONDS.sleep(10);
            return temperature;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        } finally {
            stampedLock.unlockRead(stamp);
        }
    }

    /**
     * 读操作（乐观读 + 失败升级为悲观读）
     * 这是 StampedLock 最经典的用法，适用于读多写少的场景。
     * 乐观读没有获取任何锁，仅在最后验证版本号是否变化。
     */
    public double readWithOptimisticLock() {
        // 1. 尝试乐观读，获取一个 stamp（相当于版本号）
        long stamp = stampedLock.tryOptimisticRead();
        
        // 2. 读取数据（无锁）
        double currentTemp = this.temperature;
        long currentTimestamp = this.timestamp;
        
        // 3. 模拟处理数据耗时（比如格式化、计算等）
        try {
            TimeUnit.MILLISECONDS.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 4. 验证在读取期间是否有写操作发生（版本号是否变化）
        if (!stampedLock.validate(stamp)) {
            // 版本号已变，说明数据可能不一致，升级为悲观读锁重试
            System.out.println("  [乐观读失败，升级为悲观读锁] " + Thread.currentThread().getName());
            stamp = stampedLock.readLock(); // 获取悲观读锁
            try {
                currentTemp = this.temperature;
                currentTimestamp = this.timestamp;
            } finally {
                stampedLock.unlockRead(stamp); // 释放悲观读锁
            }
        }
        
        System.out.printf("[读取] 温度: %.1f°C, 时间戳: %d, 读取线程: %s%n",
                currentTemp, currentTimestamp, Thread.currentThread().getName());
        return currentTemp;
    }

    public static void main(String[] args) throws InterruptedException {
        TemperatureSensorWithStampedLock sensor = new TemperatureSensorWithStampedLock();

        // 启动3个读线程，持续读取温度（使用乐观读）
        for (int i = 0; i < 3; i++) {
            Thread reader = new Thread(() -> {
                for (int j = 0; j < 10; j++) {
                    sensor.readWithOptimisticLock();
                    try {
                        TimeUnit.MILLISECONDS.sleep(150); // 读取间隔
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }, "Reader-" + i);
            reader.start();
        }

        // 启动1个写线程，定期更新温度
        Thread writer = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                double newTemp = 20 + Math.random() * 10; // 20~30°C
                sensor.updateTemperature(newTemp);
                try {
                    TimeUnit.MILLISECONDS.sleep(500); // 更新间隔比读取稍长
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "Writer");
        writer.start();

        // 等待写线程结束后再退出
        writer.join();
        System.out.println("========= 写入结束，等待读线程完成 =========");
        TimeUnit.SECONDS.sleep(2); // 让读线程跑完
        System.exit(0);
    }
}