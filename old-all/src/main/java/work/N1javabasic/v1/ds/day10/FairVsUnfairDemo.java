package work.N1javabasic.v1.ds.day10;

import java.util.concurrent.locks.ReentrantLock;

public class FairVsUnfairDemo {

    // 🔥 公平锁：先来后到，严禁插队
    private static final ReentrantLock fairLock = new ReentrantLock(true);
    // ⚡ 非公平锁（默认）：允许插队，性能更高
    private static final ReentrantLock unfairLock = new ReentrantLock(false);

    public static void main(String[] args) throws InterruptedException {
        System.out.println("===== 公平锁测试 =====");
        testLock(fairLock, "公平锁");

        Thread.sleep(2000);

        System.out.println("\n===== 非公平锁测试 =====");
        testLock(unfairLock, "非公平锁");
    }

    private static void testLock(ReentrantLock lock, String name) throws InterruptedException {
        lock.lock(); // 先让一个线程持有锁

        Runnable task = () -> {
            try {
                Thread.sleep(100); // 模拟先做点事再来抢锁
            } catch (InterruptedException e) { }

            lock.lock();
            try {
                System.out.println(Thread.currentThread().getName() 
                    + " 拿到了" + name + " ✅");
            } finally {
                lock.unlock();
            }
        };

        // 启动5个线程，它们几乎同时到达
        for (int i = 0; i < 5; i++) {
            new Thread(task, "Thread-" + i).start();
        }

        Thread.sleep(500); // 等所有线程都到了
        lock.unlock(); // 释放锁，让它们抢！
    }
}
