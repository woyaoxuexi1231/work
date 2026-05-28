package com.example.springqa.Q20_AsyncRequest;

import org.springframework.stereotype.Component;

/**
 * <h1>Q20：异步请求 — DeferredResult / Callable / SSE</h1>
 */
@Component
public class AsyncRequestDemo {

    public String runDemo() {
        return "=== Q20: 异步请求 ===\n\n" +
            "同步模型问题:\n" +
            "  每个请求占用一个 Tomcat 线程直到响应返回。\n" +
            "  200 个线程全部阻塞 → 第 201 个请求排队。\n\n" +
            "异步模型:\n" +
            "  Tomcat 线程接请求 → 提交异步任务 → 线程立即释放 🔄\n" +
            "  → 业务线程池执行 → 完成 → Tomcat 线程写响应\n\n" +
            "Callable 异步:\n" +
            "  @GetMapping(\"/async\")\n" +
            "  public Callable<String> async() {\n" +
            "      return () -> { Thread.sleep(3000); return \"OK\"; };\n" +
            "  }\n" +
            "  Spring 用 TaskExecutor 执行 Callable，Tomcat 线程立即释放。\n\n" +
            "DeferredResult 异步:\n" +
            "  完全控制\"什么时候返回\"和\"在哪返回\"。\n" +
            "  可在任意线程调用 setResult()——适合 MQ 回调、长轮询。\n\n" +
            "SSE (Server-Sent Events):\n" +
            "  服务器→浏览器单向推送。基于 HTTP，自动重连。\n" +
            "  SseEmitter / Flux<ServerSentEvent> 两种方式。\n" +
            "  SSE vs WebSocket: SSE 更简单（HTTP协议），适合通知/进度推送。\n\n" +
            "【Spring 为什么设计异步请求支持？】\n" +
            "Servlet 3.0 引入 request.startAsync()，Spring MVC 在此基础上提供更友好的编程模型——\n" +
            "Callable / DeferredResult / SseEmitter 都是这一层的封装。\n";
    }
}
