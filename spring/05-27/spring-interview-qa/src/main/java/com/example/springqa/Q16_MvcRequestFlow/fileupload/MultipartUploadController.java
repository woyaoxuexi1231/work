package com.example.springqa.Q16_MvcRequestFlow.fileupload;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@RestController
@RequestMapping("/q16/upload1")
public class MultipartUploadController {

    @PostMapping("/multipart")
    public String multipartUpload(
            @RequestParam("file") MultipartFile file
    ) throws Exception {

        long start = System.currentTimeMillis();

        System.out.println("===== Controller Enter =====");

        MonitorUtil.printMemory("before read");

        InputStream in = file.getInputStream();

        byte[] buffer = new byte[1024 * 1024];

        long total = 0;

        int len;

        while ((len = in.read(buffer)) != -1) {

            total += len;

            System.out.println(
                    "read chunk: "
                            + len
                            + " total="
                            + total / 1024 / 1024
                            + "MB"
            );

            Thread.sleep(50);
        }

        MonitorUtil.printMemory("after read");

        long cost = System.currentTimeMillis() - start;

        return "multipart done cost=" + cost + "ms";
    }
}