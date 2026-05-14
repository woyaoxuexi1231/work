package work.N1javabasic.v1.day10;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class SecKillSystem {
    private int stock = 5;
    private final ReentrantLock lock = new ReentrantLock(false); // 非公平锁，性能优先
    private final Condition hasStock = lock.newCondition();

    public void seckill(String user) throws InterruptedException {
        lock.lock();
        try {
            while (stock <= 0) {
                System.out.println("😩 " + user + " 没货了，等待补货...");
                hasStock.await(); // Condition等待
            }
            stock--;
            System.out.println("🏆 " + user + " 秒杀成功！库存剩余: " + stock);
        } finally {
            lock.unlock();
        }
    }

    public void restock(int num) throws InterruptedException {
        lock.lock();
        try {
            stock += num;
            System.out.println("📦 补货完成，当前库存: " + stock);
            hasStock.signalAll(); // Condition唤醒
        } finally {
            lock.unlock();
        }
    }

    public static void main(String[] args) {
        SecKillSystem system = new SecKillSystem();

        // 100个用户抢购，只有5个库存
        for (int i = 0; i < 100; i++) {
            final int id = i;
            new Thread(() -> {
                try {
                    system.seckill("用户" + id);
                } catch (InterruptedException e) {
                    System.out.println("用户" + id + " 线程被中断");
                }
            }).start();
        }

        for (int i = 0; i < 10; i++) {
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                System.out.println("补货线程被中断");
            }
            // 1秒后补货
            new Thread(() -> {
                try {
                    system.restock(10);
                } catch (InterruptedException e) {
                    System.out.println("补货线程被中断");
                }
            }).start();
        }
    }
}