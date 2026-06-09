package com.example.gcproblem04;

import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * gc-problem-04: 观察 GC 后的内存回收情况
 */
@RestController
@EnableAsync
public class AsyncTaskService {

    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final ThreadLocal<byte[]> localBuffer = new ThreadLocal<>();
    private final AtomicLong taskCounter = new AtomicLong(0);

    /**
     * 每 5 秒提交一批异步任务
     */
    @Scheduled(fixedRate = 5000)
    public void submitTasks() {
        for (int i = 0; i < 8; i++) {
            final long taskId = taskCounter.incrementAndGet();
            pool.submit(() -> processTask(taskId));
        }
    }

    @Async
    public void processTask(long taskId) {
        // 为当前线程分配约 2MB 的缓冲区
        byte[] buf = new byte[2 * 1024 * 1024];
        localBuffer.set(buf);
        // 模拟短暂处理
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // 注意：这里用完后的处理
    }

    @GetMapping("/status")
    public String status() {
        return "Tasks submitted: " + taskCounter.get()
                + ", Active threads in pool: " + pool.toString();
    }

}
