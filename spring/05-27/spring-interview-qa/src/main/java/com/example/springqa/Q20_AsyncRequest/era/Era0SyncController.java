package com.example.springqa.Q20_AsyncRequest.era;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/q20-era0")
public class Era0SyncController {

    @GetMapping("/sync")
    public String sync() throws Exception {
        String threadName = Thread.currentThread().getName();
        log.info(">>> Tomcat线程 [{}] 开始处理（同步阻塞）", threadName);
        Thread.sleep(3000);
        log.info("<<< Tomcat线程 [{}] 处理完成（阻塞结束）", threadName);
        return "Era0 同步：线程 [" + threadName + "] 被阻塞了 3 秒";
    }
}
