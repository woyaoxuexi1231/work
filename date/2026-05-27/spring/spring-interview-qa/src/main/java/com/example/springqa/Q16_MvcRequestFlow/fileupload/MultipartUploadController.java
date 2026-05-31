package com.example.springqa.Q16_MvcRequestFlow.fileupload;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;

/**
 * <h1>MultipartFile 上传</h1>
 *
 * <h2>核心认知：MultipartFile 不是"文件内容"，是"临时文件的路径指针"</h2>
 *
 * <p>你调用 <code>file.getInputStream()</code> 时，读的不是网络流——是<b>本地磁盘上的临时文件</b>。
 * 你调用 <code>file.transferTo(dest)</code> 时，做的是<b>本地文件拷贝</b>（同分区甚至只是 rename）。</p>
 *
 * <p>Tomcat 在你进入 Controller <b>之前</b>已经做了以下事情：</p>
 * <pre>
 * HTTP 请求到达 Tomcat
 *   → Socket 接收 TCP 包（64KB 一个，流水一样到）
 *   → MultipartResolver 拦截（看到 Content-Type: multipart/form-data）
 *   → 解析 boundary 分隔符，找到 file 字段
 *   → ★ 边收边写：每收到 8KB → 判断文件大小 → 小于阈值放内存，大于阈值写临时文件
 *   → 封装成 MultipartFile 对象（里面存的是临时文件路径，不是文件内容！）
 *   → ★ 此时 Controller 才被调用
 * </pre>
 *
 * <p>所以你在 Controller 里看到 memory used 几乎不变——不是因为"没消耗内存"，
 * 而是因为消耗内存的阶段在 Controller 之前。630MB 文件 → Tomcat 流式写到磁盘 →
 * MultipartFile 对象里只有一个临时文件路径字符串。你调 transferTo 只是本地文件拷贝。</p>
 *
 * <p>对比 StreamUploadController：那里没有 MultipartResolver 预处理，你直接从
 * request.getInputStream() 读原始网络流——那才是真正的"边收边写"。</p>
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
        MonitorUtil.printMemory("① 进入 Controller → file 已经是临时文件的路径指针，不是文件内容");

        System.out.printf("  文件名: %s  大小: %d MB%n", file.getOriginalFilename(), file.getSize() / 1024 / 1024);
        System.out.printf("  数据位置: %s%n", file.getSize() > 4096 ? "临时文件（磁盘）— MultipartFile 只存了路径" : "内存");

        // ★ transferTo：临时文件 → 目标目录
        //    同磁盘分区 = rename（几乎零耗时）
        //    跨磁盘分区 = FileCopyUtils.copy（流式拷贝，8KB 缓冲区）
        Path dest = SAVE_DIR.resolve(System.currentTimeMillis() + "_" + file.getOriginalFilename());
        file.transferTo(dest);

        MonitorUtil.printMemory("② transferTo 完成 — 只是把临时文件移了个位置，没有额外的网络 IO");

        long cost = System.currentTimeMillis() - start;
        MonitorUtil.printSeparator("完成 cost=" + cost + "ms  文件: " + dest);
        return String.format("MultipartFile done | cost=%dms | size=%dMB | saved=%s",
                cost, file.getSize() / 1024 / 1024, dest);
    }
}
