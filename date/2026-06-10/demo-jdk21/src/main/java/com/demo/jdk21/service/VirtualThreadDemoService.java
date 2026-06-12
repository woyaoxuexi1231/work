package com.demo.jdk21.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 演示 JDK 21 Virtual Threads（虚线程）
 *
 * 核心思想：M:N 调度模型，创建开销极小（~1KB），可以轻松创建百万个
 * 替代方案：平台线程（~1MB/个，通常限制几百个）或 CompletableFuture/响应式
 */
@Service
public class VirtualThreadDemoService {

    private static final Logger log = LoggerFactory.getLogger(VirtualThreadDemoService.class);

    // 模拟 I/O 密集型操作
    private String simulateIO(String taskName, int delayMs) throws InterruptedException {
        Thread.sleep(delayMs);
        return taskName + " 完成 (线程: " + Thread.currentThread() + ", isVirtual=" + Thread.currentThread().isVirtual() + ")";
    }

    public Map<String, Object> demoBasic() {
        var result = new LinkedHashMap<String, Object>();

        // 1. 直接创建虚线程
        var vt = Thread.startVirtualThread(() -> {
            log.info("✅ 虚线程运行中: isVirtual={}", Thread.currentThread().isVirtual());
        });
        result.put("1_虚线程名称", vt.toString());
        result.put("1_isVirtual", vt.isVirtual());

        // 2. 使用 ExecutorService（推荐方式）
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var f1 = executor.submit(() -> simulateIO("任务A", 100));
            var f2 = executor.submit(() -> simulateIO("任务B", 100));
            var f3 = executor.submit(() -> simulateIO("任务C", 100));

            result.put("2_任务A", f1.get());
            result.put("2_任务B", f2.get());
            result.put("2_任务C", f3.get());
        } catch (Exception e) {
            log.error("🚫 虚线程任务失败", e);
        }

        // 3. 对比平台线程 vs 虚线程
        result.put("3_说明", "平台线程: ~1MB/个，通常限制几百个；虚线程: ~1KB/个，可创建百万个");

        log.info("✅ Virtual Thread 基础演示完成");
        return result;
    }

    public Map<String, Object> demoConcurrent() {
        var result = new LinkedHashMap<String, Object>();
        int taskCount = 1000;

        // 虚线程：创建 1000 个并发任务
        long vtStart = System.currentTimeMillis();
        AtomicInteger vtCompleted = new AtomicInteger(0);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new Future<?>[taskCount];
            for (int i = 0; i < taskCount; i++) {
                final int taskId = i;
                futures[i] = executor.submit(() -> {
                    Thread.sleep(50); // 模拟 50ms I/O
                    vtCompleted.incrementAndGet();
                    return taskId;
                });
            }
            // 等待所有完成
            for (var f : futures) {
                f.get();
            }
        } catch (Exception e) {
            log.error("🚫 并发测试失败", e);
        }
        long vtElapsed = System.currentTimeMillis() - vtStart;

        result.put("1_任务总数", taskCount);
        result.put("2_完成任务数", vtCompleted.get());
        result.put("3_虚线程总耗时", vtElapsed + "ms（1000个50ms任务并发，总耗时约50ms）");
        result.put("4_说明", "如果是平台线程：1000个线程 x 1MB = 1GB 内存！虚线程：1000 x 1KB = 1MB");

        log.info("✅ Virtual Thread 并发演示完成，耗时: {}ms", vtElapsed);
        return result;
    }
}
