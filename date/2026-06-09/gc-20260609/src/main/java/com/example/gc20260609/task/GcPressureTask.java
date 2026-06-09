package com.example.gc20260609.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * <h2>后台任务 —— 模拟正常业务流量，制造 Young GC 压力</h2>
 *
 * <h3>🎯 这个任务的目的是什么？</h3>
 * <p>
 * 真实生产环境中，接口请求本身就会产生大量临时对象（String、DTO、VO 等），<br>
 * 这些临时对象会导致 Young GC 频繁执行。<br>
 * Young GC 过程中，存活的对象会被复制到 Survivor 区，<br>
 * 经历多次 Young GC 仍然存活的对象会<strong>晋升到老年代</strong>（-XX:MaxTenuringThreshold）。
 * </p>
 *
 * <h3>🔥 加速老年代填满的机制</h3>
 * <pre>
 *   1. 每 200ms 创建一批临时对象（模拟请求处理中的临时变量）
 *   2. 这些对象很快变成垃圾 → 触发 Young GC
 *   3. MemoryLeakService 中缓存的 byte[] 是长期存活的
 *   4. 每次 Young GC 时，这些 byte[] 的"年龄"+1
 *   5. 当年龄达到 MaxTenuringThreshold（默认 15），对象晋升到老年代
 *   6. 老年代填满 → Full GC → STW → 接口毛刺
 * </pre>
 *
 * <h3>💡 为什么需要这个任务？</h3>
 * <p>
 * 如果只有 MemoryLeakService 的定时写入，Young GC 频率太低，<br>
 * 对象晋升老年代的速度会很慢，可能需要很久才能触发 Full GC。<br>
 * 加入这个任务后，Young GC 频率提高，对象晋升更快，<br>
 * Full GC 大约在启动后 3~5 分钟内就会出现。
 * </p>
 */
@Component
public class GcPressureTask {

    private static final Logger log = LoggerFactory.getLogger(GcPressureTask.class);

    /** 累计 Young GC 压力产生的临时对象批次 */
    private long batchCount = 0;

    /**
     * 每 200ms 创建一批临时对象，模拟业务请求产生的短期存活对象
     * <p>
     * 每次创建约 512KB 的临时数据，这些数据很快就会变成垃圾。<br>
     * 高频率的 Young GC 会加速 MemoryLeakService 中缓存对象的晋升。
     * </p>
     */
    @Scheduled(fixedRate = 200)
    public void generateTraffic() {
        batchCount++;

        // 模拟一批请求产生的临时对象
        List<byte[]> tempObjects = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            // 每个 100KB，共 500KB 临时数据
            byte[] temp = new byte[100 * 1024];
            temp[0] = (byte) i;
            tempObjects.add(temp);
        }

        // 临时对象在这里失去引用，下一轮 Young GC 就会被回收
        // 但 MemoryLeakService 中的 byte[] 不会被回收，它们的"年龄"会持续增长
        processTempData(tempObjects);

        // 每 500 批次（约 100 秒）打印一次
        if (batchCount % 500 == 0) {
            log.info("✅ [GcPressureTask] 已产生 {} 批临时对象（模拟正常业务流量）", batchCount);
        }
    }

    /**
     * 模拟对临时数据的处理（处理后数据即可被 GC 回收）
     */
    private void processTempData(List<byte[]> data) {
        // 模拟一些计算操作
        long sum = 0;
        for (byte[] bytes : data) {
            sum += bytes[0];
        }
        // 计算完成后，data 列表即可被回收
    }
}
