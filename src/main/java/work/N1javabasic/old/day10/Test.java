package work.N1javabasic.old.day10;

import java.util.concurrent.locks.ReentrantLock;

/**
 * @author hulei
 * @since 2026/5/4 16:36
 */

public class Test {

    public static void main(String[] args) {

        ReentrantLock reentrantLock = new ReentrantLock();

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                boolean b = reentrantLock.tryLock();
                if (b) {
                    try {
                        // 执行业务代码
                        System.out.println("获取锁成功");
                        Thread.sleep(5000);
                    } catch (Exception e) {
                        System.out.println("业务代码执行异常" + e.getMessage());
                        e.printStackTrace();
                    } finally {
                        reentrantLock.unlock();
                    }
                } else {
                    System.out.println("获取锁失败");
                }
            }
        };
        Thread thread = new Thread(runnable);
        Thread thread1 = new Thread(runnable);

        thread.start();
        thread1.start();

        try {
            thread.join();
            thread1.join();
        } catch (Exception e) {
            System.out.println("线程等待异常" + e.getMessage());
            e.printStackTrace();
        }
    }
}
