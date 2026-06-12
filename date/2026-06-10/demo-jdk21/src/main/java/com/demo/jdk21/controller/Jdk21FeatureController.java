package com.demo.jdk21.controller;

import com.demo.jdk21.service.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JDK 21 所有新特性的统一演示入口
 *
 * GET /api/virtual-thread         → 虚线程基础
 * GET /api/virtual-thread-concurrent → 虚线程并发
 * GET /api/structured-task        → StructuredTaskScope
 * GET /api/record-pattern         → Record Pattern
 * GET /api/switch-pattern         → Switch 模式匹配（正式）
 * GET /api/sequenced              → Sequenced Collections
 * GET /api/unnamed                → Unnamed Variables
 */
@RestController
@RequestMapping("/api")
public class Jdk21FeatureController {

    private final VirtualThreadDemoService virtualThreadService;
    private final StructuredTaskDemoService structuredTaskService;
    private final RecordPatternDemoService recordPatternService;
    private final SwitchPatternDemoService switchPatternService;
    private final SequencedCollectionDemoService sequencedCollectionService;
    private final UnnamedDemoService unnamedService;

    public Jdk21FeatureController(
            VirtualThreadDemoService virtualThreadService,
            StructuredTaskDemoService structuredTaskService,
            RecordPatternDemoService recordPatternService,
            SwitchPatternDemoService switchPatternService,
            SequencedCollectionDemoService sequencedCollectionService,
            UnnamedDemoService unnamedService) {
        this.virtualThreadService = virtualThreadService;
        this.structuredTaskService = structuredTaskService;
        this.recordPatternService = recordPatternService;
        this.switchPatternService = switchPatternService;
        this.sequencedCollectionService = sequencedCollectionService;
        this.unnamedService = unnamedService;
    }

    @GetMapping("/virtual-thread")
    public Map<String, Object> virtualThread() {
        return virtualThreadService.demoBasic();
    }

    @GetMapping("/virtual-thread-concurrent")
    public Map<String, Object> virtualThreadConcurrent() {
        return virtualThreadService.demoConcurrent();
    }

    @GetMapping("/structured-task")
    public Map<String, Object> structuredTask() {
        return structuredTaskService.demo();
    }

    @GetMapping("/record-pattern")
    public Map<String, Object> recordPattern() {
        return recordPatternService.demo();
    }

    @GetMapping("/switch-pattern")
    public Map<String, Object> switchPattern() {
        return switchPatternService.demo();
    }

    @GetMapping("/sequenced")
    public Map<String, Object> sequenced() {
        return sequencedCollectionService.demo();
    }

    @GetMapping("/unnamed")
    public Map<String, Object> unnamed() {
        return unnamedService.demo();
    }

    @GetMapping("/features")
    public Map<String, String> listFeatures() {
        var features = new LinkedHashMap<String, String>();
        features.put("GET /api/virtual-thread", "Virtual Threads 基础：创建、isVirtual()、executor");
        features.put("GET /api/virtual-thread-concurrent", "Virtual Threads 并发：1000 个任务并发执行");
        features.put("GET /api/structured-task", "StructuredTaskScope：ShutdownOnFailure / ShutdownOnSuccess");
        features.put("GET /api/record-pattern", "Record Pattern：instanceof/switch 解构 Record 字段");
        features.put("GET /api/switch-pattern", "Switch 模式匹配（正式）：类型/null/数组匹配");
        features.put("GET /api/sequenced", "Sequenced Collections：getFirst/getLast/reversed");
        features.put("GET /api/unnamed", "Unnamed Variables：用 _ 忽略不需要的变量（预览）");
        return features;
    }
}
