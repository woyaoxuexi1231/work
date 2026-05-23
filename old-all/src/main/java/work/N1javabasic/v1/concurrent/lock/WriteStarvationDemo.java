package work.N1javabasic.v1.concurrent.lock;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 演示写线程饥饿：读线程长时间占用读锁，写线程一直阻塞。
 */
public class WriteStarvationDemo {

    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private int data = 0; // 模拟共享数据

    // 读操作：持有读锁一段时间
    public void read() {
        String name = Thread.currentThread().getName();
        rwLock.readLock().lock();
        try {
            System.out.println(name + " 获得读锁，开始读取...");
            // 模拟长时间读取（比如读取大文件），这里休眠2秒
            Thread.sleep(2000);
            System.out.println(name + " 读取完成，数据值: " + data);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    // 写操作：简单修改数据，但需要获取写锁
    public void write(int newVal) {
        String name = Thread.currentThread().getName();
        System.out.println(name + " 尝试获取写锁...");
        rwLock.writeLock().lock();
        try {
            System.out.println(name + " 获得写锁，开始写入 " + newVal);
            data = newVal;
            // 模拟写操作耗时
            Thread.sleep(500);
            System.out.println(name + " 写入完成");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            rwLock.writeLock().unlock();
            System.out.println(name + " 释放写锁");
        }
    }

    public static void main(String[] args) throws InterruptedException {
        WriteStarvationDemo demo = new WriteStarvationDemo();

        // 启动3个读线程，持续快速申请读锁
        for (int i = 0; i < 3; i++) {
            Thread reader = new Thread(() -> {
                while (true) {
                    demo.read();
                    try {
                        // 读线程稍作休息后立即再次申请读锁
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }, "Reader-" + i);
            reader.setDaemon(true); // 设为守护线程，方便主线程退出
            reader.start();
        }

        // 稍微延迟，让读线程先跑起来
        Thread.sleep(200);

        // 启动一个写线程
        Thread writer = new Thread(() -> demo.write(999), "Writer");
        writer.start();

        // 让写线程有足够时间观察到饥饿现象
        Thread.sleep(8000);
        System.out.println("主线程结束，程序退出");
        System.exit(0);
    }
}