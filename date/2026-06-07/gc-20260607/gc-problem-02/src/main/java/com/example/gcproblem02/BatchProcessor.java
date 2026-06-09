package com.example.gcproblem02;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * gc-problem-02: 观察 GC 频率和停顿时间
 */
@RestController
public class BatchProcessor {

    private final List<byte[]> tempBuffer = new ArrayList<>();

    /**
     * 每 8 秒执行一次任务
     */
    @Scheduled(fixedRate = 8000)
    public void processBatch() {
        // 每次处理约 15MB 的临时数据
        for (int i = 0; i < 5; i++) {
            tempBuffer.add(new byte[3 * 1024 * 1024]); // 3MB each
        }
        // 处理完后清理
        tempBuffer.clear();
    }

    @GetMapping("/status")
    public String status() {
        Runtime rt = Runtime.getRuntime();
        return String.format("Max: %dMB, Total: %dMB, Free: %dMB",
                rt.maxMemory() / 1024 / 1024,
                rt.totalMemory() / 1024 / 1024,
                rt.freeMemory() / 1024 / 1024);
    }

    @GetMapping("/trigger-gc")
    public String triggerGc() {
        System.gc();
        return "GC triggered. " + status();
    }
}
