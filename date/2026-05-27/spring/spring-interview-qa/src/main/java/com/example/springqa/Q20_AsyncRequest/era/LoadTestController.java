package com.example.springqa.Q20_AsyncRequest.era;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;

/**
 * <h1>真实压测——发 HTTP 请求到真实端点</h1>
 *
 * <p>访问 /q20-test/sync-load → 对同步端点压测 16 个并发请求
 *    访问 /q20-test/async-load → 对 Callable 端点压测 16 个并发请求</p>
 */
@Slf4j
@RestController
@RequestMapping("/q20-test")
public class LoadTestController {

    @GetMapping("/sync-load")
    public Map<String, Object> syncLoad() throws Exception {
        return doLoadTest("http://localhost:8080/q20-era0/sync", "同步");
    }

    @GetMapping("/async-load")
    public Map<String, Object> asyncLoad() throws Exception {
        return doLoadTest("http://localhost:8080/q20-era2/callable", "Callable 异步");
    }

    private Map<String, Object> doLoadTest(String url, String label) throws Exception {
        int N = 16;
        ExecutorService pool = Executors.newFixedThreadPool(N);
        CountDownLatch latch = new CountDownLatch(N);
        List<Map<String, Object>> rows = new CopyOnWriteArrayList<>();

        log.info("═══════════════════════════════════════");
        log.info("  [压测] {} — {} 个并发请求 → {}", label, N, url);
        log.info("═══════════════════════════════════════");

        long start = System.currentTimeMillis();

        for (int i = 0; i < N; i++) {
            final int idx = i;
            pool.submit(() -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("req", idx);
                long t0 = System.currentTimeMillis();
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(15000);
                    conn.connect();
                    String body = readAll(conn.getInputStream());
                    long elapsed = System.currentTimeMillis() - t0;
                    row.put("httpStatus", conn.getResponseCode());
                    row.put("elapsedMs", elapsed);
                    row.put("response", body.length() > 100 ? body.substring(0, 100) + "..." : body);
                    conn.disconnect();
                } catch (Exception e) {
                    row.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
                }
                rows.add(row);
                latch.countDown();
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        long total = System.currentTimeMillis() - start;

        long maxMs = rows.stream().filter(r -> r.containsKey("elapsedMs"))
                .mapToLong(r -> (Long) r.get("elapsedMs")).max().orElse(0);
        long minMs = rows.stream().filter(r -> r.containsKey("elapsedMs"))
                .mapToLong(r -> (Long) r.get("elapsedMs")).min().orElse(0);
        double avgMs = rows.stream().filter(r -> r.containsKey("elapsedMs"))
                .mapToLong(r -> (Long) r.get("elapsedMs")).average().orElse(0);
        long errors = rows.stream().filter(r -> r.containsKey("error")).count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("test", label + " — " + N + " 并发");
        result.put("totalMs", total);
        result.put("minMs", minMs);
        result.put("maxMs", maxMs);
        result.put("avgMs", String.format("%.0f", avgMs));
        result.put("errors", errors);

        if (label.contains("同步")) {
            result.put("note", "同步：min=" + minMs + "ms, max=" + maxMs + "ms（差距大=排队）。"
                    + "Tomcat 线程被大量占用。如果并发超过 Tomcat 线程池，后面的请求必须等前面的释放。");
        } else {
            result.put("note", "Callable 异步：min=" + minMs + "ms, max=" + maxMs + "ms（几乎一样=无排队）。"
                    + "Tomcat 线程提交 Callable 后立刻释放，耗时操作在 TaskExecutor 线程里并发执行。"
                    + "16 个请求不需要 16 个 Tomcat 线程——几个就够了。");
        }
        result.put("details", rows);

        log.info("  [压测结果] min={}ms max={}ms avg={}ms 错误={}", minMs, maxMs, String.format("%.0f", avgMs), errors);
        return result;
    }

    private String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return out.toString("UTF-8");
    }
}
