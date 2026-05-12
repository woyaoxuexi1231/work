package work.N1javabasic.deepseek.day10;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author hulei
 * @since 2026/5/12 19:39
 */

class Stock {
    private int count = 100;
    private final ReentrantLock lock = new ReentrantLock(); // 默认非公平锁

    public void deduct() {
        lock.lock();
        try {
            count--;
            System.out.println(Thread.currentThread().getName()
                    + " 抢到！剩余: " + count);
        } finally {
            lock.unlock();
        }
    }

    // 🔑 可重入演示：同一个线程可以重复加锁
    public void reentrantDemo() {
        lock.lock();
        try {
            System.out.println("第一次加锁，state=" + lock.getHoldCount());
            lock.lock(); // 同一个线程再次加锁 → 不会死锁！
            try {
                System.out.println("第二次加锁，state=" + lock.getHoldCount());
            } finally {
                lock.unlock();
            }
        } finally {
            lock.unlock();
        }
    }
}


class StockWithCondition {
    private int count = 10; // 只有10台！
    private final ReentrantLock lock = new ReentrantLock();
    // 🔑 关键：一个锁可以绑定多个Condition！
    private final Condition notEmpty  = lock.newCondition(); // 库存>0，通知抢购线程
    private final Condition notFull   = lock.newCondition(); // 库存<100，通知补货线程

    // 📦 抢购方法
    public void buy(String user) throws InterruptedException {
        lock.lock();
        try {
            // ⭐ 条件等待：while循环防止虚假唤醒（必须用while，不能用if！）
            while (count <= 0) {
                System.out.println(user + " 库存不足，进入等待...");
                notEmpty.await(); // 🔥 释放锁 + 挂起线程（两个动作原子性完成！）
            }
            count--;
            System.out.println("🎉 " + user + " 抢到！剩余 " + count);
            // 抢完通知补货线程：库存不满了，快来补货
            notFull.signal();
        } finally {
            lock.unlock();
        }
    }

    // 🚚 补货方法
    public void restock(int num) throws InterruptedException {
        lock.lock();
        try {
            while (count >= 100) {
                System.out.println("📦 仓库已满，补货线程等待...");
                notFull.await();
            }
            int add = Math.min(num, 100 - count);
            count += add;
            System.out.println("📦 补货 +" + add + "，当前库存: " + count);
            // 补完通知抢购线程：有货了，快来抢！
            notEmpty.signalAll(); // 🔥 唤醒所有等待的抢购线程
        } finally {
            lock.unlock();
        }
    }
}



public class ReentrantLockTest {



}
