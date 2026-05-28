package com.example.springqa.Q16_MvcRequestFlow.fileupload;

import org.springframework.web.bind.annotation.*;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.nio.file.*;

/**
 * <h1>原始流上传（真正的网络流）</h1>
 *
 * <p><b>核心事实：你拿到的 InputStream 就是 TCP 连接上的网络流。</b></p>
 *
 * <pre>
 * HTTP 请求到达
 *   → Tomcat 接收网络流
 *   → ★ 直接进入你的 Controller 方法——没有任何预处理
 *   → request.getInputStream() 拿到的就是原始 HTTP body
 *   → 你手动从网络流读 → 写本地文件
 * </pre>
 *
 * <p>和 MultipartFile 的本质区别：MultipartResolver 在 Controller 之前
 * 就把文件从 HTTP body 里"拆出来"写到了临时文件。而这里——<b>你直面网络流</b>，
 * 数据从网卡到你的 buffer 再到磁盘，<b>全程没有中间商</b>。</p>
 */
@RestController
@RequestMapping("/q16/upload2")
public class StreamUploadController {

    private static final Path SAVE_DIR = Paths.get(System.getProperty("java.io.tmpdir"), "spring-upload-stream");

    static { try { Files.createDirectories(SAVE_DIR); } catch (IOException ignored) {} }

    @PostMapping("/stream")
    public String streamUpload(HttpServletRequest request) throws Exception {
        long start = System.currentTimeMillis();

        MonitorUtil.printSeparator("原始 InputStream 模式（直面网络流）");
        MonitorUtil.printMemory("① 进入 Controller（数据还在网络中，尚未读取）");

        // 文件名：请求头 X-Filename > 默认
        String name = request.getHeader("X-Filename");
        if (name == null || name.isEmpty()) name = System.currentTimeMillis() + ".bin";
        Path dest = SAVE_DIR.resolve(name);
        long total = 0;

        // ★ 这里：网络 InputStream → 磁盘 OutputStream，边收边写
        //    8KB 缓冲区——不管文件多大，内存只用 8KB
        try (ServletInputStream in = request.getInputStream();
             OutputStream out = new FileOutputStream(dest.toFile())) {

            byte[] buf = new byte[8192];  // ← 8KB 水桶
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
                total += len;

                // 每 50MB 打印一次内存——证明始终低水位
                if (total % (50L * 1024 * 1024) == 0) {
                    MonitorUtil.printMemory("  → 已接收 " + total / 1024 / 1024 + "MB（内存恒定 8KB 缓冲区）");
                }
            }
        }

        MonitorUtil.printMemory("② 流读完 + 文件写完（内存无变化——全程 8KB 缓冲区）");

        long cost = System.currentTimeMillis() - start;
        double mb = total / 1024.0 / 1024.0;
        MonitorUtil.printSeparator(String.format("完成 cost=%dms  大小=%.1fMB  文件=%s", cost, mb, dest));
        return String.format("Stream done | cost=%dms | size=%.1fMB | saved=%s", cost, mb, dest);
    }
}
