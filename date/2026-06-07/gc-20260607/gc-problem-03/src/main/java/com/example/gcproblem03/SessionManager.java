package com.example.gcproblem03;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * gc-problem-03: 观察老年代使用率趋势
 */
@RestController
public class SessionManager {

    private final List<SessionObject> activeSessions = new ArrayList<>();
    private final AtomicLong sessionIdCounter = new AtomicLong(0);

    /**
     * 每 3 秒创建一批新"会话"
     */
    @Scheduled(fixedRate = 3000)
    public void manageSessions() {
        // 创建新会话
        for (int i = 0; i < 20; i++) {
            SessionObject session = new SessionObject(sessionIdCounter.incrementAndGet());
            activeSessions.add(session);
        }

        // 每 10 轮才清理一次旧会话
        if (sessionIdCounter.get() % 200 == 0 && activeSessions.size() > 200) {
            // 只清理最老的 30 个
            int removeCount = Math.min(30, activeSessions.size());
            for (int i = 0; i < removeCount; i++) {
                activeSessions.remove(0);
            }
        }
    }

    @GetMapping("/status")
    public String status() {
        return "Active sessions: " + activeSessions.size()
                + ", Total created: " + sessionIdCounter.get();
    }

    /**
     * 模拟一个"会话"对象
     */
    static class SessionObject {
        private final long id;
        private final byte[] data;       // 约 5KB

        SessionObject(long id) {
            this.id = id;
            this.data = new byte[5 * 1024];
        }
    }
}
