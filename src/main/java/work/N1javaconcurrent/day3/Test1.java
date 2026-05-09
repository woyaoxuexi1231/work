package work.N1javaconcurrent.day3;

import lombok.SneakyThrows;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;

/**
 * @author hulei
 * @since 2026/5/8 19:23
 */

public class Test1 {

    @SneakyThrows
    public static void main(String[] args) {


        CountDownLatch countDownLatch= new CountDownLatch(3);

        CyclicBarrier cyclicBarrier = new CyclicBarrier(3, () -> {
            System.out.println("所有线程任务完成");
        });

        Thread t1 = new Thread(() -> {
            System.out.println("t1 start");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("t1 end");
            countDownLatch.countDown();

            try {
                cyclicBarrier.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (BrokenBarrierException e) {
                throw new RuntimeException(e);
            }
            // 开始处理数据
            System.out.println("t1 process start");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("t1 process end");

            try {
                cyclicBarrier.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (BrokenBarrierException e) {
                throw new RuntimeException(e);
            }
            // 开始处理数据
            System.out.println("t1 process start");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("t1 process end");
            
        });
        Thread t2 = new Thread(() -> {
            System.out.println("t2 start");
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("t2 end");
            countDownLatch.countDown();

            try {
                cyclicBarrier.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (BrokenBarrierException e) {
                throw new RuntimeException(e);
            }
            // 开始处理数据
            System.out.println("t2 process start");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("t2 process end");



            try {
                cyclicBarrier.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (BrokenBarrierException e) {
                throw new RuntimeException(e);
            }
            // 开始处理数据
            System.out.println("t2 process start");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("t2 process end");
            
        });
        Thread t3 = new Thread(() -> {
            System.out.println("t3 start");
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("t3 end");
            countDownLatch.countDown();

            try {
                cyclicBarrier.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (BrokenBarrierException e) {
                throw new RuntimeException(e);
            }
            // 开始处理数据
            System.out.println("t3 process start");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("t3 process end");



            try {
                cyclicBarrier.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (BrokenBarrierException e) {
                throw new RuntimeException(e);
            }
            // 开始处理数据
            System.out.println("t3 process start");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("t3 process end");

        });

        t1.start();
        t2.start();
        t3.start();

        countDownLatch.await();

        System.out.println("CountDownLatch 方式完成");

        Thread.sleep(20000);
    }
}
