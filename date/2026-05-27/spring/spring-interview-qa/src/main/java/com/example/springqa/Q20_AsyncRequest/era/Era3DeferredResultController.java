package com.example.springqa.Q20_AsyncRequest.era;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/q20-era3")
public class Era3DeferredResultController {

    private final Map<String, DeferredResult<String>> pending = new ConcurrentHashMap<>();

    @GetMapping("/submit")
    public DeferredResult<String> submit() {
        String requestId = String.valueOf(System.currentTimeMillis());
        String submitThread = Thread.currentThread().getName();
        log.info(">>> Tomcat线程 [{}] 接到请求 → 返回 DeferredResult → 线程立刻释放！", submitThread);

        DeferredResult<String> result = new DeferredResult<>(30000L, "请求超时");
        result.onTimeout(() -> log.warn("requestId={} 超时", requestId));
        result.onCompletion(() -> pending.remove(requestId));
        pending.put(requestId, result);

        log.info("○○○ 请求挂起 requestId={}。不占任何线程在等！", requestId);
        return result;
    }

    @GetMapping("/complete")
    public String complete(String requestId) {
        String triggerThread = Thread.currentThread().getName();
        DeferredResult<String> result = pending.remove(requestId);
        if (result == null) return "没找到 requestId=" + requestId;

        log.info("★★★ 线程 [{}] 触发了 setResult → 挂起的请求收到响应！", triggerThread);
        result.setResult("DeferredResult 完成——线程 [" + triggerThread + "] 触发了响应");
        return "已触发";
    }
}
