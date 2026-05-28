package com.example.springqa.Q16_MvcRequestFlow.fileupload;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * <h1>文件上传 — 四种方式对比</h1>
 *
 * <pre>
 *   方式一：@RequestParam MultipartFile     ← 最常用，Spring 已处理好流
 *   方式二：@RequestBody byte[]              ← 整个文件读进内存！危险！
 *   方式三：HttpServletRequest.getInputStream() ← 手动流式读取（原始方式）
 *   方式四：大文件分片上传                     ← 断点续传
 * </pre>
 */
@RestController
@RequestMapping("/upload")
public class FileUploadController {

    private static final String UPLOAD_DIR = System.getProperty("java.io.tmpdir") + "/spring-uploads";

    public FileUploadController() {
        new File(UPLOAD_DIR).mkdirs();
    }

    // ═══════════════════ 方式一：MultipartFile（推荐）═══════════════════
    /**
     * <b>curl -F "file=@test.jpg" http://localhost:8080/upload/multipart</b>
     *
     * <p>Spring 的 StandardServletMultipartResolver 在 DispatcherServlet 之前
     * 拦截请求，把 multipart/form-data 解析成 MultipartFile 对象。
     * 小于 file-size-threshold 的文件内容在内存里，大于的已经写到临时文件。</p>
     */
    @PostMapping("/multipart")
    public ResponseEntity<Map<String, Object>> multipartUpload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "desc", required = false) String desc) {

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("method", "MultipartFile");
        result.put("originalName", file.getOriginalFilename());
        result.put("contentType", file.getContentType());
        result.put("size", file.getSize());
        result.put("sizeInMemory", file.getSize() < 4096 ? "✅ 在内存（<4KB阈值）" : "⚠️ 已写临时文件（≥4KB阈值）");

        // ★ Spring 已经处理了流！你拿到的是一个包装好的对象
        // file.getBytes() 内部才去读流——调用前数据在临时文件或内存里
        try {
            Path dest = Paths.get(UPLOAD_DIR, UUID.randomUUID() + "_" + file.getOriginalFilename());
            file.transferTo(dest);  // ← 内部：InputStream → OutputStream 拷贝
            result.put("savedPath", dest.toString());
            result.put("status", "OK");
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            result.put("status", "FAILED");
            result.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    // ═══════════════════ 方式二：byte[]（危险！）═══════════════════
    /**
     * <b>curl -d "@test.jpg" http://localhost:8080/upload/bytes</b>
     *
     * <p><b>❌ 整个文件读进堆内存。</b>500MB 文件 = 500MB 堆 → OOM。</p>
     * <p>这里加了 1MB 上限保护——超过直接拒绝。</p>
     */
    @PostMapping("/bytes")
    public ResponseEntity<Map<String, Object>> bytesUpload(@RequestBody byte[] body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("method", "@RequestBody byte[]（全量读内存 ⚠️）");
        result.put("size", body.length);

        if (body.length > 1024 * 1024) {
            result.put("status", "REJECTED");
            result.put("reason", "超过 1MB 保护上限，请使用 /multipart 接口");
            return ResponseEntity.badRequest().body(result);
        }

        try {
            Path dest = Paths.get(UPLOAD_DIR, UUID.randomUUID() + ".bin");
            Files.write(dest, body);  // byte[] 已经在内存里了——直接写磁盘
            result.put("savedPath", dest.toString());
            result.put("status", "OK");
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            result.put("status", "FAILED");
            return ResponseEntity.internalServerError().body(result);
        }
    }

    // ═══════════════════ 方式三：手动流式（原始方式）═══════════════════
    /**
     * <b>curl --data-binary "@test.jpg" http://localhost:8080/upload/stream</b>
     *
     * <p><b>流式传输的本质：</b>建立一个 8KB 的缓冲区，循环"读一块 → 写一块"，
     * 直到读完整个流。任何时刻，内存里只有 8KB 的数据。</p>
     *
     * <p>这就是"流"的含义——不是把整条河搬过来，而是用桶一桶一桶舀。</p>
     */
    @PostMapping("/stream")
    public ResponseEntity<Map<String, Object>> streamUpload(HttpServletRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("method", "手动 InputStream（流式，真正的边读边写）");
        long startTime = System.currentTimeMillis();
        Path dest = Paths.get(UPLOAD_DIR, UUID.randomUUID() + ".stream");
        long totalBytes = 0;

        try (InputStream in = request.getInputStream();
             OutputStream out = new FileOutputStream(dest.toFile())) {

            byte[] buffer = new byte[8192];  // ★ 8KB 缓冲区——这就是流的秘密
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
                // ★ 每一轮循环，内存里只有这 8KB
                // ★ 无论文件是 1MB 还是 1GB——内存用量恒定为 8KB
            }
        } catch (IOException e) {
            result.put("status", "FAILED");
            result.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        result.put("totalBytes", totalBytes);
        result.put("elapsedMs", elapsed);
        result.put("savedPath", dest.toString());
        result.put("memoryUsed", "恒定为 8KB 缓冲区——无论文件多大");
        result.put("status", "OK");
        return ResponseEntity.ok(result);
    }

    // ═══════════════════ 方式四：分片上传（大文件/断点续传）═══════════════════
    private final Map<String, Map<Integer, Path>> chunkStore = new HashMap<>();

    /**
     * <p>大文件（>100MB）应该分片上传。每个分片独立传输，全部到达后在服务端合并。</p>
     *
     * <b>上传分片：curl -F "chunk=@part1" -F "fileId=xxx" -F "chunkIndex=0" -F "totalChunks=5" .../chunk</b><br>
     * <b>合并分片：POST .../chunk/merge?fileId=xxx</b>
     */
    @PostMapping("/chunk")
    public ResponseEntity<Map<String, Object>> chunkUpload(
            @RequestParam("chunk") MultipartFile chunk,
            @RequestParam String fileId,
            @RequestParam int chunkIndex,
            @RequestParam int totalChunks) {

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("method", "分片上传");
        result.put("fileId", fileId);
        result.put("chunk", chunkIndex + "/" + totalChunks);
        result.put("chunkSize", chunk.getSize());

        try {
            chunkStore.computeIfAbsent(fileId, k -> new HashMap<>());
            Path chunkPath = Paths.get(UPLOAD_DIR, fileId + ".part" + chunkIndex);
            chunk.transferTo(chunkPath);
            chunkStore.get(fileId).put(chunkIndex, chunkPath);
            result.put("status", "OK");
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            result.put("status", "FAILED");
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @PostMapping("/chunk/merge")
    public ResponseEntity<Map<String, Object>> mergeChunks(@RequestParam String fileId) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<Integer, Path> parts = chunkStore.remove(fileId);
        if (parts == null) {
            result.put("status", "FAILED");
            result.put("error", "没找到分片");
            return ResponseEntity.badRequest().body(result);
        }

        Path merged = Paths.get(UPLOAD_DIR, fileId + ".merged");
        try (OutputStream out = new FileOutputStream(merged.toFile())) {
            for (int i = 0; i < parts.size(); i++) {
                Files.copy(parts.get(i), out);  // 按顺序拼接
            }
            result.put("savedPath", merged.toString());
            result.put("totalChunks", parts.size());
            result.put("status", "OK");
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            result.put("status", "FAILED");
            return ResponseEntity.internalServerError().body(result);
        }
    }
}
