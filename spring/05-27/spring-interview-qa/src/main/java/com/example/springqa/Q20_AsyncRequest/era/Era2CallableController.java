package com.example.springqa.Q20_AsyncRequest.era;

import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@RestController
@RequestMapping("/q20-era2")
public class Era2CallableController {

    @GetMapping("/callable")
    public Callable<String> asyncCallable() {
        String submitThread = Thread.currentThread().getName();
        log.info(">>> Tomcat线程 [{}] 接到请求 → 返回 Callable → 线程立刻释放！", submitThread);

        return () -> {
            String workThread = Thread.currentThread().getName();
            log.info("★★★ 业务线程 [{}] 开始执行耗时操作（不是 Tomcat 线程！）", workThread);
            Thread.sleep(3000);
            log.info("<<< 业务线程 [{}] 执行完毕", workThread);
            return "Era2 Callable：提交线程 [" + submitThread + "] → 执行线程 [" + workThread + "]（两个不同线程！）";
        };
    }
}
