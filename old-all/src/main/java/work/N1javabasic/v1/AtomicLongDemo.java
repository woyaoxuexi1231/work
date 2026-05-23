package work.N1javabasic.v1;

import java.util.concurrent.atomic.AtomicLong;

public class AtomicLongDemo {

    // 订单号生成器，保证全局唯一递增
    private static final AtomicLong orderId = new AtomicLong(System.currentTimeMillis());

    // 大数据量统计，比如实时QPS、消息总量
    private static final AtomicLong totalMessages = new AtomicLong(0);

    public static void main(String[] args) throws InterruptedException {
        // 1. 订单号自增
        System.out.println("新订单号1: " + orderId.incrementAndGet());
        System.out.println("新订单号2: " + orderId.incrementAndGet());
        System.out.println("新订单号3: " + orderId.incrementAndGet());

        // 2. 大数据量计数（多线程累加）
        Thread[] threads = new Thread[20];
        for (int i = 0; i < 20; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 50_000; j++) {
                    totalMessages.incrementAndGet();
                }
            });
            threads[i].start();
        }
        for (Thread t : threads) t.join();
        System.out.println("累计消息总量: " + totalMessages.get()); // 应为 1,000,000

        // 3. 高并发下获取并重置（如每秒清空计数器）
        long snapshot = totalMessages.getAndSet(0);
        System.out.println("上一周期消息数: " + snapshot);
        System.out.println("重置后当前值: " + totalMessages.get());
    }
}