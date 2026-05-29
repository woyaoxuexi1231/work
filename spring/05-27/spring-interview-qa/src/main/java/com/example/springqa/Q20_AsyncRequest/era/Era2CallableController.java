package com.example.springqa.Q20_AsyncRequest.era;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.Callable;

/**
 * <h1>第2代：Callable —— Spring 帮你管理线程</h1>
 *
 * <p>Controller 返回 Callable&lt;T&gt; —— Spring 自动提交给 TaskExecutor，
 * 自动管理 AsyncContext，自动处理超时和异常。</p>
 *
 * <p>和 Era1 的区别：
 * Era1 你要手动 startAsync()、newCachedThreadPool()、complete()——10 行样板。
 * 这里只需要把耗时逻辑包在 Callable 里——1 行。</p>
 *
 * <p>访问: http://localhost:8080/q20-era2/callable</p>
 */
@RestController
@RequestMapping("/q20-era2")
public class Era2CallableController {

    @GetMapping("/callable")
    public Callable<String> asyncCallable() {
        System.out.println("  [Era2] Tomcat 线程进入 Controller → 立刻返回 Callable → Tomcat 线程释放");

        // ★ 返回 Callable —— Spring 自动提交给 TaskExecutor 执行
        //     方法返回的那一刻，Tomcat 线程就释放了！
        return () -> {
            System.out.println("  [Era2] TaskExecutor 线程开始执行…");
            Thread.sleep(3000); // 模拟耗时操作（在 TaskExecutor 线程里执行）
            System.out.println("  [Era2] TaskExecutor 线程执行完毕 → Spring 取 Tomcat 线程写响应");
            return "Era2 Callable：耗时 3s，但 Tomcat 线程在 0ms 时就释放了";
        };
    }
}
