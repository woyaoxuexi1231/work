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
        long start = System.currentTimeMillis();

        // 模拟耗时操作（查数据库 / 调外部 API）
        // ★ 这 3 秒钟，Tomcat 线程什么也不干——就在这等着
        Thread.sleep(3000);

        long ms = System.currentTimeMillis() - start;
        return "Era0 同步阻塞：耗时 " + ms + "ms —— 这 3 秒 Tomcat 线程被白白占用";
    }
}
