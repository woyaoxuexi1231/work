package work.N1javabasic.old.day11;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.StampedLock;

/**
 * @author hulei
 * @since 2026/5/4 19:56
 */

public class Test {

    public static void main(String[] args) {
        // test1();
        // test2();
        test3();
    }


    public static void test1() {

        ReentrantReadWriteLock reentrantReadWriteLock = new ReentrantReadWriteLock();

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                // 这是一个读操作的线程
                ReentrantReadWriteLock.ReadLock readLock = reentrantReadWriteLock.readLock();
                boolean b = readLock.tryLock();
                if (b) {
                    try {
                        System.out.println("开始读");
                        Thread.sleep(1000);
                        System.out.println("结束读");
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } finally {
                        readLock.unlock();
                    }
                } else {
                    System.out.println("读锁被其他线程占用");
                }
            }
        };


        Runnable runnable1 = new Runnable() {
            @Override
            public void run() {
                // 这是一个写线程
                ReentrantReadWriteLock.WriteLock writeLock = reentrantReadWriteLock.writeLock();
                boolean b = writeLock.tryLock();
                if (b) {
                    try {
                        System.out.println("开始写");
                        Thread.sleep(1000);
                        System.out.println("结束写");
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } finally {
                        writeLock.unlock();
                    }
                } else {
                    System.out.println("写锁被其他线程占用");
                }
            }
        };


        try {
            // 两个读线程，是不会进行阻塞的。
            Thread read1 = new Thread(runnable);
            Thread read2 = new Thread(runnable);
            read1.start();
            read2.start();

            read1.join();
            read2.join();


            // 读锁和写锁是互斥的
            Thread read3 = new Thread(runnable);
            Thread write1 = new Thread(runnable1);
            read3.start();
            write1.start();
            read3.join();
            write1.join();

            // 写和写也是互斥的
            Thread write2 = new Thread(runnable1);
            Thread write3 = new Thread(runnable1);
            write2.start();
            write3.start();
            write2.join();
            write3.join();


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void test2() {

        ReentrantReadWriteLock reentrantReadWriteLock = new ReentrantReadWriteLock();

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                ReentrantReadWriteLock.WriteLock writeLock = reentrantReadWriteLock.writeLock();
                // 现在进行锁降级，锁降级就是将写锁降级为读锁
                ReentrantReadWriteLock.ReadLock readLock = reentrantReadWriteLock.readLock();
                boolean b = writeLock.tryLock();
                if (b) {

                    AtomicBoolean b1 = new AtomicBoolean();

                    try {
                        System.out.println("开始写");
                        Thread.sleep(1000);
                        System.out.println("结束写");

                        System.out.println("开始降级");
                        b1.set(readLock.tryLock());

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } finally {
                        writeLock.unlock();
                    }

                    if (b1.get()) {
                        System.out.println("降级成功");
                        try {
                            System.out.println("开始读");
                            Thread.sleep(1000);
                            System.out.println("结束读");
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        } finally {
                            readLock.unlock();
                        }
                    } else {
                        System.out.println("读锁被其他线程占用");
                    }

                } else {
                    System.out.println("写锁被其他线程占用");
                }
            }
        };
    }



    public static void test3() {
        Point point = new Point();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                // point.set(1);
                // point.doubleX();
                point.changeWhenZero();
            }
        };
        Thread thread = new Thread(runnable);
        Thread thread2 = new Thread(runnable);
        // thread.start();
        thread2.start();


        try {
            thread.join();
            thread2.join();
        } catch (Exception e){
            e.printStackTrace();
        }




    }
}


class Point {

    private int x;

    private final StampedLock sl = new StampedLock();

    // 写操作：排他锁
    void set(int newx){
        long l = sl.writeLock();
        System.out.println("成功获取到写锁");
        try {
            x = newx;
            Thread.sleep(1000);
            System.out.println("写操作完成");
        } catch (Exception e){
            System.out.println("异常");
            e.printStackTrace();
        } finally {
            sl.unlockWrite(l);
            System.out.println("释放写锁");
        }
    }

    // 乐观读模式：读取距离原点的直线距离
    double doubleX() {
        long stamp = sl.tryOptimisticRead();
        int returnx = x*2;
        // 验证戳
        // 二进制输出stamp
        System.out.println(Long.toBinaryString(stamp));
        if (!sl.validate(stamp)) {
            System.out.println("乐观读模式失败，获取读锁");
            // 验证戳无效，获取读锁
            stamp = sl.readLock();
            try {
                returnx = x*2;
            } finally {
                sl.unlockRead(stamp);
            }
        } else {
            System.out.println("乐观读模式成功");
        }
        return returnx;
    }

    // 悲观读模式：读取，并在满足条件时尝试升级为写锁
    void changeWhenZero() {
        long stamp = sl.readLock(); // 获取悲观读锁
        System.out.println(Long.toBinaryString(stamp));
        try {
            while (x == 0) {
                // tryConvertToWriteLock：尝试将读戳升级为写戳
                long ws = sl.tryConvertToWriteLock(stamp);
                if (ws != 0L) {
                    System.out.println("尝试将读戳升级为写戳成功");
                    stamp = ws; // 升级成功，现在持有写锁
                    x = -1;
                    break;
                } else { // 升级失败，释放读锁，显式获取写锁
                    System.out.println("尝试将读戳升级为写戳失败，释放读锁，显式获取写锁");
                    sl.unlockRead(stamp);
                    stamp = sl.writeLock();
                }
            }
        } finally {
            sl.unlock(stamp); // unlock 会智能判断是读锁还是写锁并释放
        }
    }
}

