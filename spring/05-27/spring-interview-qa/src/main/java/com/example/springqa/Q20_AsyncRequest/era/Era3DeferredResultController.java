package com.example.springqa.Q20_AsyncRequest.era;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <h1>第3代：DeferredResult —— 等待外部事件</h1>
 *
 * <p>Callable 只能"返回一个线程去执行"。但如果你的响应不是"计算出来的"，
 * 而是"等 MQ 消息"、"等另一个用户的操作"——Callable 不行。
 * DeferredResult 让你在<b>任意线程、任意时机</b>通过 setResult() 触发响应。</p>
 *
 * <p>这里有三个接口演示：</p>
 * <pre>
 * /q20-era3/submit    — 提交一个等待请求（不阻塞 Tomcat 线程）
 * /q20-era3/complete  — 模拟外部事件触发，完成等待的请求
 * </pre>
 *
 * <p>访问: http://localhost:8080/q20-era3/submit → 拿一个 ID → 另一个窗口访问 /q20-era3/complete?requestId=xxx</p>
 */
@RestController
@RequestMapping("/q20-era3")
public class Era3DeferredResultController {

    // 模拟"等待中的请求"存储
    private final Map<String, DeferredResult<String>> pending = new ConcurrentHashMap<>();

    /**
     * 提交等待请求——Tomcat 线程立即释放。
     * DeferredResult 挂起，等待外部事件触发 setResult()。
     */
    @GetMapping("/submit")
    public DeferredResult<String> submit() {
        String requestId = String.valueOf(System.currentTimeMillis());

        // ★ 30 秒超时，超时返回 timeout
        DeferredResult<String> result = new DeferredResult<>(30000L, "请求超时");

        result.onTimeout(() -> System.out.println("  [Era3] requestId=" + requestId + " 超时"));
        result.onCompletion(() -> pending.remove(requestId));

        // 把这个 DeferredResult 存起来——等外部事件触发它
        pending.put(requestId, result);

        System.out.println("  [Era3] 请求挂起。requestId=" + requestId + "。Tomcat 线程已释放！");

        // ★ Tomcat 线程在这里就释放了——不会等！
        //   什么时候有人调 /complete?requestId=xxx，什么时候响应才返回
        return result;
    }

    /**
     * 模拟"外部事件"触发——完成之前挂起的请求。
     * 访问: /q20-era3/complete?requestId=刚才拿到的ID
     */
    @GetMapping("/complete")
    public String complete(String requestId) {
        DeferredResult<String> result = pending.remove(requestId);
        if (result == null) {
            return "没找到 requestId=" + requestId + " 的等待请求（可能已超时）";
        }
        // ★ 在任意线程、任意时机调用 setResult → 挂起的请求立刻返回！
        result.setResult("DeferredResult 完成！requestId=" + requestId + " ← 这个响应是外部事件触发的，不是线程计算出来的");

        System.out.println("  [Era3] 外部事件触发 setResult → 挂起的请求得到响应！");
        return "OK——等待中的请求已收到响应";
    }
}
