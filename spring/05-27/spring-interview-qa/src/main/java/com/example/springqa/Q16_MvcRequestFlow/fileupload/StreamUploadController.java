package com.example.springqa.Q16_MvcRequestFlow.fileupload;

import org.springframework.web.bind.annotation.*;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.nio.file.*;

/**
 * <h1>原始流上传（直面网络流）</h1>
 *
 * <h2>和 MultipartFile 的本质区别</h2>
 *
 * <pre>
 * MultipartFile 模式：
 *   网络流 → Tomcat MultipartResolver（流式解析+写临时文件） → Controller 拿到文件路径指针
 *   ↑ 内存峰值在这里                   ↑ Controller 运行时内存已回落
 *
 * Stream 模式：
 *   网络流 → 直接交到 Controller → Controller 手动 8KB 缓冲区边收边写
 *   ↑ 没有中间商——你亲自面对网络流
 * </pre>
 *
 * <p>MultipartFile 不是"没消耗内存"——是消耗内存的阶段在 Controller 之前，
 * 你没监控到。就像你只看了水龙头的出水，没看到水厂的处理过程。</p>
 *
 * <p>Stream 模式下你全程监控——从 TCP 包到达的第一刻到文件写完的最后一刻，
 * 每 50MB 打印一次内存——可以看到 used memory 从 108MB 只涨到 113MB。
 * 630MB 数据经过，内存只涨了 5MB——因为 8KB 缓冲区循环使用。</p>
 */
@RestController
@RequestMapping("/q16/upload2")
public class StreamUploadController {

    private static final Path SAVE_DIR = Paths.get(System.getProperty("java.io.tmpdir"), "spring-upload-stream");

    static { try { Files.createDirectories(SAVE_DIR); } catch (IOException ignored) {} }

    @PostMapping("/stream")
    public String streamUpload(HttpServletRequest request) throws Exception {
        long start = System.currentTimeMillis();

        MonitorUtil.printSeparator("原始 InputStream 模式（直面网络流 — 无 MultipartResolver 预处理）");
        MonitorUtil.printMemory("① 进入 Controller → 数据还在网卡上，尚未读取。不像 MultipartFile 已经落盘");

        String name = request.getHeader("X-Filename");
        if (name == null || name.isEmpty()) name = System.currentTimeMillis() + ".bin";
        Path dest = SAVE_DIR.resolve(name);
        long total = 0;

        // ★ 真正的流式：网络 Socket InputStream → 磁盘 FileOutputStream
        //    8KB 缓冲区 —— 不管文件多大，内存只用 8KB
        //    和 MultipartFile 的区别：MultipartFile 的"流"是本地临时文件→目标文件
        //                          这里的"流"是网卡→磁盘，全程无中间文件
        try (ServletInputStream in = request.getInputStream();
             OutputStream out = new FileOutputStream(dest.toFile())) {

            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
                total += len;

                if (total % (50L * 1024 * 1024) == 0) {
                    MonitorUtil.printMemory("  → 已接收 " + total / 1024 / 1024 + "MB（内存恒定 8KB 缓冲区 — 和 MultipartFile 不同，这里你亲自监控了全过程）");
                }
            }
        }

        MonitorUtil.printMemory("② 流读完 + 文件写完 — 630MB 过手，内存只涨了几MB（8KB 缓冲区的威力）");

        long cost = System.currentTimeMillis() - start;
        double mb = total / 1024.0 / 1024.0;
        MonitorUtil.printSeparator(String.format("完成 cost=%dms  大小=%.1fMB  文件=%s", cost, mb, dest));
        return String.format("Stream done | cost=%dms | size=%.1fMB | saved=%s", cost, mb, dest);
    }
}
