package work.N1javabasic.v1.day6;

import java.util.*;
import java.util.concurrent.*;

public class CHMBasicTest {
    public static void main(String[] args) throws InterruptedException {
        // 准备两个Map
        ConcurrentHashMap<String, String> chm = new ConcurrentHashMap<>();
        Map<String, String> syncMap = Collections.synchronizedMap(new HashMap<>());

        // 测试写入性能
        long chmTime = concurrentWrite(chm, 20, 1000000);
        long syncTime = concurrentWrite(syncMap, 20, 1000000);
        System.out.println("ConcurrentHashMap time: " + chmTime + "ms, size=" + chm.size());
        System.out.println("SynchronizedMap time: " + syncTime + "ms, size=" + syncMap.size());

        // 测试弱一致性迭代
        System.out.println("\n--- 弱一致性迭代测试 ---");
        chm.clear();
        for (int i = 0; i < 5; i++) chm.put("key" + i, "val");
        for (String key : chm.keySet()) {
            if (key.equals("key2")) {
                chm.put("key_new", "new_val"); // 迭代过程中修改
                chm.remove("key3");
            }
            System.out.println("Iterating: " + key);
        }
        System.out.println("After iteration: " + chm.keySet());
        // 与 HashMap 对比：HashMap 会抛 ConcurrentModificationException
        // 这里不会抛异常，且可能遍历到或遍历不到新插入的 key_new
    }

    private static long concurrentWrite(Map<String, String> map, int threads, int perThread)
            throws InterruptedException {
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(threads);
        Thread[] workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            final int t = i;
            workers[i] = new Thread(() -> {
                try { startSignal.await(); } catch (InterruptedException ignored) {}
                for (int j = 0; j < perThread; j++) {
                    map.put("t" + t + "_k" + j, "v");
                }
                doneSignal.countDown();
            });
            workers[i].start();
        }
        long start = System.currentTimeMillis();
        startSignal.countDown();
        doneSignal.await();
        return System.currentTimeMillis() - start;
    }
}