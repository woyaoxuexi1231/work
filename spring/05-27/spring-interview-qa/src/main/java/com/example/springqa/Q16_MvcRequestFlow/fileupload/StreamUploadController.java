package com.example.springqa.Q16_MvcRequestFlow.fileupload;

import org.springframework.web.bind.annotation.*;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/q16/upload2")
public class StreamUploadController {

    @PostMapping("/stream")
    public String streamUpload(
            HttpServletRequest request
    ) throws Exception {

        long start = System.currentTimeMillis();

        System.out.println("===== Stream Controller Enter =====");

        MonitorUtil.printMemory("before stream read");

        ServletInputStream in = request.getInputStream();

        byte[] buffer = new byte[1024 * 1024];

        long total = 0;

        int len;

        while ((len = in.read(buffer)) != -1) {

            total += len;

            System.out.println(
                    "stream chunk: "
                            + len
                            + " total="
                            + total / 1024 / 1024
                            + "MB"
            );

            if (total % (50 * 1024 * 1024) == 0) {
                MonitorUtil.printMemory(
                        "stream progress "
                                + total / 1024 / 1024
                                + "MB"
                );
            }

            Thread.sleep(50);
        }

        MonitorUtil.printMemory("after stream");

        long cost = System.currentTimeMillis() - start;

        return "stream done cost=" + cost + "ms";
    }
}