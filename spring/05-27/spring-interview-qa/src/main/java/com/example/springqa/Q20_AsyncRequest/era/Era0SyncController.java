package com.example.springqa.Q20_AsyncRequest.era;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <h1>第0代：同步阻塞</h1>
 *
 * <p>每个请求进来，Tomcat 分配一个线程。线程全程被占用——
 * 包括等待数据库、等待外部 API 的时间。</p>
 *
 * <pre>
 * Tomcat 默认 200 线程。如果有 200 个请求都在等 5 秒的外部 API——
 * 全部 200 个线程阻塞，第 201 个请求排队。CPU 基本空闲——
 * 瓶颈不是 CPU，是线程数。
 * </pre>
 *
 * <p>测试：打开浏览器连点 10 次 /q20-era0/sync —— 每个请求耗时 3 秒，
 * 总共需要 30 秒以上（因为线程不够用了）。</p>
 *
 * <p>访问: http://localhost:8080/q20-era0/sync</p>
 */
@RestController
@RequestMapping("/q20-era0")
public class Era0SyncController {

    @GetMapping("/sync")
    public String sync() throws Exception {
        String threadName = Thread.currentThread().getName();
        System.out.println("[Era0-同步] >>> 线程 [" + threadName + "] 开始处理请求（Tomcat 线程）");

        // ★ 这 3 秒——Tomcat 线程被阻塞在这里！
        //    如果此时收到新请求，Tomcat 必须从线程池里拿另一个线程
        //    如果线程池满了（默认 200），新请求排队
        Thread.sleep(3000);

        System.out.println("[Era0-同步] <<< 线程 [" + threadName + "] 处理完成（阻塞结束）");
        return "Era0 同步：线程 [" + threadName + "] 被阻塞了 3 秒";
    }
}
