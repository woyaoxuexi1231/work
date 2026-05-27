package com.example.springqa.Q20_AsyncRequest;

/**
 * <h1>Q20：异步请求 — DeferredResult / Callable / SSE</h1>
 *
 * <h2>面试点</h2>
 * <ul>
 *   <li>DeferredResult 和 Callable 的处理流程？</li>
 *   <li>如何释放容器线程提高吞吐？</li>
 *   <li>Spring MVC 如何支持 SSE？</li>
 * </ul>
 *
 * <h2>同步 vs 异步</h2>
 *
 * <p>传统同步模型：每个请求占用一个 Tomcat 线程直到响应返回。
 * 如果有很多慢请求（调用外部 API），线程池很快耗尽。</p>
 *
 * <p>异步模型：Tomcat 线程接受请求 → 提交异步任务 → 线程立即释放。
 * 任务完成后 → 从线程池拿新线程 → 写响应。</p>
 *
 * <h2>Callable vs DeferredResult</h2>
 * <pre>
 * Callable:       返回一个 Callable，Spring 用 TaskExecutor 执行
 * DeferredResult: 完全控制"什么时候返回"和"在哪返回"
 *                 可以在任意线程中调用 setResult()
 * </pre>
 *
 * <h2>SSE（Server-Sent Events）</h2>
 * <p>服务器向浏览器推送事件的技术，基于 HTTP 长连接。
 * 比 WebSocket 更简单，适合单向推送（通知、进度条、日志流）。</p>
 *
 * <h2>Spring 为什么设计异步请求支持？</h2>
 * <p>Servlet 3.0 引入了异步处理（request.startAsync()），
 * Spring MVC 在此基础上提供了更友好的编程模型。</p>
 *
 * @author Spring Interview QA
 */
public class AsyncRequestDemo {

    public static void main(String[] args) {
        System.out.println("========== Q20: 异步请求 Demo ==========\n");

        compareSyncAsync();
        System.out.println();
        demoCallable();
        System.out.println();
        demoDeferredResult();
        System.out.println();
        demoSSE();

        System.out.println("\n========== Demo 结束 ==========");
    }

    static void compareSyncAsync() {
        System.out.println("同步模型:");
        System.out.println("  请求 → Tomcat线程1 → 业务处理(阻塞5s) → 返回");
        System.out.println("  请求 → Tomcat线程2 → 业务处理(阻塞5s) → 返回");
        System.out.println("  200 个线程全部阻塞 → 第 201 个请求排队等待");
        System.out.println();
        System.out.println("异步模型 (Callable):");
        System.out.println("  请求 → Tomcat线程1 → 提交任务 → 线程1立即释放 🔄");
        System.out.println("                    ↓");
        System.out.println("              业务线程池处理(5s)");
        System.out.println("                    ↓");
        System.out.println("              Tomcat线程X → 写响应 → 线程X释放 🔄");
        System.out.println();
        System.out.println("同样是 200 个 Tomcat 线程，但能处理更多请求！");
        System.out.println("容器线程只负责\"接客\"，不负责\"干活\"。");
    }

    static void demoCallable() {
        System.out.println("Callable 异步:");
        System.out.println("@GetMapping(\"/async-callable\")");
        System.out.println("public Callable<String> asyncCallable() {");
        System.out.println("    return () -> {");
        System.out.println("        // 这段代码在 TaskExecutor 线程中执行");
        System.out.println("        Thread.sleep(3000);  // 模拟耗时操作");
        System.out.println("        return \"异步处理完成\";");
        System.out.println("    };");
        System.out.println("    // 主线程（Tomcat 线程）在这之后立即释放！");
        System.out.println("}");
        System.out.println();
        System.out.println("处理流程:");
        System.out.println("1. Tomcat 线程接收请求 → 拿到 Callable");
        System.out.println("2. Spring 把 Callable 提交给 TaskExecutor");
        System.out.println("3. Tomcat 线程释放 → 可以接下一个请求");
        System.out.println("4. TaskExecutor 线程执行 Callable.call()");
        System.out.println("5. 执行完毕，Spring 用新 Tomcat 线程写响应");
    }

    static void demoDeferredResult() {
        System.out.println("DeferredResult 异步:");
        System.out.println("@GetMapping(\"/async-deferred\")");
        System.out.println("public DeferredResult<String> asyncDeferred() {");
        System.out.println("    DeferredResult<String> result = new DeferredResult<>(30000L, \"timeout\");");
        System.out.println("    result.onTimeout(() -> result.setErrorResult(\"请求超时\"));");
        System.out.println("    pendingRequests.put(\"req-\" + UUID.randomUUID(), result);");
        System.out.println("    return result;  // Tomcat 线程立即释放！");
        System.out.println("}");
        System.out.println();
        System.out.println("// 在其他线程（如 MQ 消费者）中：");
        System.out.println("@EventListener");
        System.out.println("public void onOrderReady(OrderEvent event) {");
        System.out.println("    DeferredResult<String> result = pendingRequests.get(event.getRequestId());");
        System.out.println("    if (result != null) {");
        System.out.println("        result.setResult(\"订单就绪: \" + event.getOrderId());");
        System.out.println("        // setResult 可以在任何线程中调用！");
        System.out.println("    }");
        System.out.println("}");
        System.out.println();
        System.out.println("Callable vs DeferredResult:");
        System.out.println("  Callable: \"返回一个线程去执行\"，适合简单异步");
        System.out.println("  DeferredResult: \"等待外部事件\"，适合 MQ回调/长轮询");
    }

    static void demoSSE() {
        System.out.println("SSE（Server-Sent Events）:");
        System.out.println("@GetMapping(value = \"/sse\", produces = MediaType.TEXT_EVENT_STREAM_VALUE)");
        System.out.println("public SseEmitter streamEvents() {");
        System.out.println("    SseEmitter emitter = new SseEmitter(60000L);");
        System.out.println("    Executors.newSingleThreadExecutor().execute(() -> {");
        System.out.println("        try {");
        System.out.println("            for (int i = 1; i <= 10; i++) {");
        System.out.println("                emitter.send(SseEmitter.event()");
        System.out.println("                    .name(\"progress\")");
        System.out.println("                    .data(\"处理进度: \" + (i * 10) + \"%\"));");
        System.out.println("                Thread.sleep(1000);");
        System.out.println("            }");
        System.out.println("            emitter.complete();");
        System.out.println("        } catch (Exception e) {");
        System.out.println("            emitter.completeWithError(e);");
        System.out.println("        }");
        System.out.println("    });");
        System.out.println("    return emitter;  // 连接保持，服务器持续推送");
        System.out.println("}");
        System.out.println();
        System.out.println("SSE vs WebSocket:");
        System.out.println("  SSE: 单向（服务器→浏览器），HTTP协议，自动重连，更简单");
        System.out.println("  WebSocket: 双向，独立协议，需额外处理心跳和重连");
        System.out.println();
        System.out.println("Spring 提供 SseEmitter 和 Flux<ServerSentEvent> 两种方式，");
        System.out.println("分别对应 Servlet 和 Reactive 编程模型。");
    }
}
