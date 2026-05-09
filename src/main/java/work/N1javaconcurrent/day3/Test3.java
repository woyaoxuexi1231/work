package work.N1javaconcurrent.day3;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * @author hulei
 * @since 2026/5/8 19:41
 */

public class Test3 {

    public static void main(String[] args) {
        Semaphore semaphore = new Semaphore(3);
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    if (semaphore.tryAcquire(5, TimeUnit.SECONDS)) {
                        System.out.println(Thread.currentThread().getName() + "开始执行" + LocalDateTime.now());
                        Thread.sleep(2000);
                        System.out.println(Thread.currentThread().getName() + "执行完成" + LocalDateTime.now());
                        semaphore.release();
                    }else {
                        System.out.println(Thread.currentThread().getName() + "获取信号量失败" + LocalDateTime.now());
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        for (int i = 0; i < 10; i++) {
            new Thread(runnable).start();
        }
    }
}
