package com.example.springqa.Q16_MvcRequestFlow.fileupload;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;

/**
 * <h1>MultipartFile 上传（Tomcat 已预处理）</h1>
 *
 * <p><b>核心事实：你进入这个方法时，文件已经在磁盘上了。</b></p>
 *
 * <pre>
 * HTTP 请求到达
 *   → Tomcat 接收网络流
 *   → MultipartResolver 解析 multipart body → 把文件部分写进临时文件
 *   → 封装成 MultipartFile 对象
 *   → ★ 此时才进入你的 Controller 方法
 *   → 你调 file.transferTo(dest) → 临时文件 → 目标目录
 * </pre>
 *
 * <p>你在 Controller 里看到的内存占用很小——不是因为"流式"，
 * 而是因为 Tomcat 在<b>你不知情的情况下</b>已经把文件写到磁盘了。
 * 你只是在做一个"本地文件拷贝"。</p>
 */
@RestController
@RequestMapping("/q16/upload1")
public class MultipartUploadController {

    private static final Path SAVE_DIR = Paths.get(System.getProperty("java.io.tmpdir"), "spring-upload-multipart");

    static { try { Files.createDirectories(SAVE_DIR); } catch (IOException ignored) {} }

    @PostMapping("/multipart")
    public String multipartUpload(@RequestParam("file") MultipartFile file) throws Exception {
        long start = System.currentTimeMillis();

        MonitorUtil.printSeparator("MultipartFile 模式");
        MonitorUtil.printMemory("① 进入 Controller（此时 Tomcat 已经把文件写到临时目录了）");

        System.out.printf("  文件名: %s  大小: %d MB%n",
                file.getOriginalFilename(), file.getSize() / 1024 / 1024);
        System.out.printf("  原始大小: %d MB, Tomcat 已处理，此时数据在: %s%n",
                file.getSize() / 1024 / 1024,
                file.getSize() > 4096 ? "临时文件（磁盘）" : "内存");

        // ★ transferTo 内部：临时文件 → 目标文件（本地文件拷贝，不是网络 IO）
        Path dest = SAVE_DIR.resolve(System.currentTimeMillis() + "_" + file.getOriginalFilename());
        file.transferTo(dest);

        MonitorUtil.printMemory("② transferTo 完成后");

        long cost = System.currentTimeMillis() - start;
        MonitorUtil.printSeparator("完成 cost=" + cost + "ms  文件: " + dest);
        return String.format("MultipartFile done | cost=%dms | size=%dMB | saved=%s",
                cost, file.getSize() / 1024 / 1024, dest);
    }
}
