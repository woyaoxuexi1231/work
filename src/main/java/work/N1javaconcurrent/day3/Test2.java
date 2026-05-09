package work.N1javaconcurrent.day3;

import lombok.SneakyThrows;

import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author hulei
 * @since 2026/5/8 19:33
 */

public class Test2 {

    private static int count = 0;

    @SneakyThrows
    public static void main(String[] args) {
        Semaphore semaphore = new Semaphore(1);

        Runnable runnable = () -> {
            for (int i = 0; i < 1000; i++) {
                try {
                    semaphore.acquire();
                    count++;
                    semaphore.release();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

            }
        };

        for (int i = 0; i < 10; i++) {
            new Thread(runnable).start();
        }

        Thread.sleep(2000);

        System.out.println(count);

        ReentrantLock reentrantLock = new ReentrantLock();

        Runnable runnable2 = () -> {
            for (int i = 0; i < 1000; i++) {
                reentrantLock.lock();
                count++;
                reentrantLock.unlock();
            }
        };

        for (int i = 0; i < 10; i++) {
            new Thread(runnable2).start();
        }

        Thread.sleep(2000);

        System.out.println(count);
    }
}
