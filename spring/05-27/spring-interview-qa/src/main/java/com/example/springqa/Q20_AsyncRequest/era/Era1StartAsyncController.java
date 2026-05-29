package com.example.springqa.Q20_AsyncRequest.era;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.Executors;

/**
 * <h1>第1代：Servlet 3.0 startAsync —— 手动释放 Tomcat 线程</h1>
 *
 * <p>Servlet 3.0（2009）引入了 request.startAsync()。
 * Tomcat 线程可以提前释放，后台线程处理完后写响应。</p>
 *
 * <p><b>问题：代码非常啰嗦。</b>要手动调 startAsync()、管理线程池、
 * 管理 AsyncContext、处理超时和异常。</p>
 *
 * <p>访问: http://localhost:8080/q20-era1/async</p>
 */
@RestController
public class Era1StartAsyncController {

    @GetMapping("/q20-era1/async")
    public void async(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // ★ 手动开启异步模式 —— Tomcat 线程在这里之后就可以释放了
        AsyncContext ctx = request.startAsync();
        ctx.setTimeout(10000); // 10s 超时

        Executors.newCachedThreadPool().submit(() -> {
            try {
                // ○○ 耗时操作在业务线程里执行 —— Tomcat 线程已经释放去接下一个请求了 ○○
                Thread.sleep(3000);

                // 业务线程写响应
                ctx.getResponse().setContentType("text/plain;charset=UTF-8");
                ctx.getResponse().getWriter().write("Era1 startAsync：耗时操作在业务线程执行，Tomcat 线程已释放");
                ctx.complete(); // ★ 通知容器：响应完毕

            } catch (Exception e) {
                ctx.complete();
            }
        });

        // ★ Tomcat 线程走到这里就返回了——不会等 3 秒！
        // 它立刻可以去接下一个请求
    }
}
