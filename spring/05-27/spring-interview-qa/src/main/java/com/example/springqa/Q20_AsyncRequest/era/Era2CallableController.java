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
        String submitThread = Thread.currentThread().getName();
        System.out.println("[Era2-Callable] >>> Tomcat 线程 [" + submitThread + "] 接到请求 → 返回 Callable → 线程立刻释放！");

        // ★ 返回 Callable —— Spring 自动提交给 TaskExecutor 执行
        //     方法返回的那一刻，Tomcat 线程就释放了！
        return () -> {
            String workThread = Thread.currentThread().getName();
            System.out.println("[Era2-Callable] ★★★ 业务线程 [" + workThread + "] 开始执行耗时操作（不是 Tomcat 线程！）");
            Thread.sleep(3000);
            System.out.println("[Era2-Callable] <<< 业务线程 [" + workThread + "] 执行完毕，Spring 取 Tomcat 线程写响应");
            return "Era2 Callable：提交线程 [" + submitThread + "] → 执行线程 [" + workThread + "]（两个不同线程！）";
        };
    }
}
