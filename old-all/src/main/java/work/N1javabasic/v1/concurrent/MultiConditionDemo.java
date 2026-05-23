package work.N1javabasic.v1.concurrent;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 多条件示例：三个线程按 A->B->C 顺序交替打印，共10轮。
 * 使用 ReentrantLock 的三个 Condition 实现精确唤醒。
 */
public class MultiConditionDemo {

    private final ReentrantLock lock = new ReentrantLock();
    
    // 三个条件，分别对应 A、B、C 线程的等待队列
    private final Condition conditionA = lock.newCondition();
    private final Condition conditionB = lock.newCondition();
    private final Condition conditionC = lock.newCondition();
    
    // 共享状态：当前应该哪个线程执行 ('A', 'B', 'C')
    private char turn = 'A';
    
    // 总打印轮数
    private final int rounds;
    
    public MultiConditionDemo(int rounds) {
        this.rounds = rounds;
    }
    
    /**
     * 打印 A 的线程方法
     */
    public void printA() {
        lock.lock();
        try {
            for (int i = 0; i < rounds; i++) {
                // 1. 如果不是 A 的轮次，进入 conditionA 等待
                while (turn != 'A') {
                    conditionA.await();  // 释放锁并阻塞，等待被唤醒
                }
                
                // 2. 执行任务
                System.out.print("A");
                if (i < rounds - 1) {
                    System.out.print(" -> ");
                }
                
                // 3. 将轮次交给 B，并精确唤醒等待在 conditionB 上的线程
                turn = 'B';
                conditionB.signal();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 打印 B 的线程方法
     */
    public void printB() {
        lock.lock();
        try {
            for (int i = 0; i < rounds; i++) {
                // 等待轮到自己
                while (turn != 'B') {
                    conditionB.await();
                }
                
                System.out.print("B");
                if (i < rounds - 1) {
                    System.out.print(" -> ");
                }
                
                // 交给 C
                turn = 'C';
                conditionC.signal();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 打印 C 的线程方法
     */
    public void printC() {
        lock.lock();
        try {
            for (int i = 0; i < rounds; i++) {
                while (turn != 'C') {
                    conditionC.await();
                }
                
                System.out.print("C");
                if (i < rounds - 1) {
                    System.out.print(" -> ");
                }
                
                // 交给 A，开始下一轮
                turn = 'A';
                conditionA.signal();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }
    }
    
    public static void main(String[] args) throws InterruptedException {
        MultiConditionDemo demo = new MultiConditionDemo(10); // 打印10轮
        
        // 创建三个线程，分别执行 printA、printB、printC
        Thread threadA = new Thread(demo::printA, "Thread-A");
        Thread threadB = new Thread(demo::printB, "Thread-B");
        Thread threadC = new Thread(demo::printC, "Thread-C");
        
        threadA.start();
        threadB.start();
        threadC.start();
        
        // 等待所有线程结束
        threadA.join();
        threadB.join();
        threadC.join();
        
        System.out.println("\nDone! 总共打印了 " + (demo.rounds * 3) + " 个字符。");
    }
}