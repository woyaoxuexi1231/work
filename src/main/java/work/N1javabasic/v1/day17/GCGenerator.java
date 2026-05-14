package work.N1javabasic.v1.day17;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * GC日志生成器 —— 适用于 JDK 17
 * <p>
 * 通过不断分配大对象并随机丢弃，触发 Young GC 与 Full GC。
 * 需搭配 JVM 参数指定垃圾收集器及 GC 日志输出。
 * <p>
 * 编译：javac GCGenerator.java
 * 运行示例（请根据所需收集器选择对应参数）：
 * <pre>
 * # Serial GC
 * -XX:+UseSerialGC -Xms128m -Xmx256m -Xlog:gc*=info:file=gc_serial.log:time,level,tags
 *
 * # Parallel GC
 * -XX:+UseParallelGC -Xms128m -Xmx256m -Xlog:gc*=info:file=gc_parallel.log:time,level,tags
 *
 * # G1 GC (JDK 17 默认)
 * -XX:+UseG1GC -Xms128m -Xmx256m -Xlog:gc*=info:file=gc_g1.log:time,level,tags
 *
 * # ZGC (需 JDK 17+)
 * -XX:+UseZGC -Xms128m -Xmx256m -Xlog:gc*=info:file=gc_zgc.log:time,level,tags
 * </pre>
 * GC 日志将写入当前目录对应的 .log 文件，也可将 file= 改为 stdout 直接打印到控制台。
 */
public class GCGenerator {
    // 每次分配的对象大小 (1MB)
    private static final int CHUNK_SIZE = 1024 * 1024;
    // 对象池最大容量 (个数)
    private static final int POOL_SIZE = 200;
    // 总分配次数
    private static final int ALLOCATION_ROUNDS = 500;

    public static void main(String[] args) {
        List<byte[]> pool = new ArrayList<>(POOL_SIZE);
        Random random = new Random(42); // 固定种子便于重现

        System.out.println("GC Generator started. GC type: " + detectGC());
        System.out.println("Allocating " + ALLOCATION_ROUNDS + " chunks of " + (CHUNK_SIZE / 1024) + "KB each...");

        for (int i = 0; i < ALLOCATION_ROUNDS; i++) {
            // 分配新对象
            byte[] chunk = new byte[CHUNK_SIZE];
            pool.add(chunk);

            // 随机丢弃一些旧对象，制造垃圾
            if (pool.size() > POOL_SIZE / 2 && random.nextDouble() < 0.4) {
                int removeCount = random.nextInt(pool.size() / 3) + 1;
                for (int j = 0; j < removeCount && !pool.isEmpty(); j++) {
                    pool.remove(random.nextInt(pool.size()));
                }
            }

            // 每100次分配打印一次进度
            if (i % 100 == 0) {
                System.out.printf("Round %d / %d, pool size: %d%n", i, ALLOCATION_ROUNDS, pool.size());
            }

            // 主动触发一次 System.gc()，增加 Full GC 出现概率 (视收集器而定)
            if (i % 200 == 0 && i > 0) {
                System.out.println("Requesting System.gc()...");
                System.gc();
            }
        }

        System.out.println("Allocation finished. Final pool size: " + pool.size());
        System.out.println("GC Generator exiting.");
    }

    /** 简单检测当前使用的垃圾收集器名称 */
    private static String detectGC() {
        var mxBeans = java.lang.management.ManagementFactory.getGarbageCollectorMXBeans();
        StringBuilder sb = new StringBuilder();
        for (var bean : mxBeans) {
            sb.append(bean.getName()).append(" ");
        }
        return sb.toString().trim();
    }
}