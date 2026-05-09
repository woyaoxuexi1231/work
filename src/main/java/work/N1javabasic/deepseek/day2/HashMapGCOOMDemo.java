package work.N1javabasic.deepseek.day2;

import java.util.HashMap;

/**
 * 演示 HashMap 扩容引发的 GC 频繁 / Full GC
 * 启动参数（JDK 9+）：
 *   -Xms32m -Xmx32m -Xlog:gc*=info:file=gc.log:time,uptime,tid
 * (Oracle JDK 8 可用 -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -Xloggc:gc.log)
 */
public class HashMapGCOOMDemo {

    // 用于占用内存的 Value，模拟实际业务对象
    static class Payload {
        private byte[] data = new byte[256]; // 每个 Value 256 字节
    }

    /**
     * 测试 1：默认初始容量 16，任由 HashMap 反复扩容
     */
    public static void testDefaultCapacity() {
        System.out.println("===== 默认容量 16 测试 =====");
        HashMap<Integer, Payload> map = new HashMap<>();
        long start = System.nanoTime();
        try {
            for (int i = 0; i < 50_000; i++) {
                map.put(i, new Payload());
            }
        } catch (OutOfMemoryError e) {
            System.out.println("OOM 发生！当前 size = " + map.size());
        }
        System.out.println("耗时(ms): " + (System.nanoTime() - start) / 1_000_000);
    }

    /**
     * 测试 2：指定足够大的初始容量，避免扩容
     * 预期需要 50000 个元素，初始容量至少 = (int)(50000 / 0.75) + 1 ≈ 66667
     * tableSizeFor 后为 131072（2^17）
     */
    public static void testSufficientCapacity() {
        System.out.println("===== 指定初始容量 131072 测试 =====");
        HashMap<Integer, Payload> map = new HashMap<>(131072);
        long start = System.nanoTime();
        try {
            for (int i = 0; i < 50_000; i++) {
                map.put(i, new Payload());
            }
        } catch (OutOfMemoryError e) {
            System.out.println("OOM 发生！当前 size = " + map.size());
        }
        System.out.println("耗时(ms): " + (System.nanoTime() - start) / 1_000_000);
    }

    public static void main(String[] args) {
        // 分开运行两次，分别观察 GC 日志
        testDefaultCapacity();
        // testSufficientCapacity();
    }
}