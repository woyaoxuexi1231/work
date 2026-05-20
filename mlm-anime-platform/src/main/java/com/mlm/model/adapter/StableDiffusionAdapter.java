package com.mlm.model.adapter;

import com.mlm.common.enums.ModelType;
import com.mlm.model.core.GenerateRequest;
import com.mlm.model.core.GenerateResponse;
import com.mlm.model.core.ModelAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stable Diffusion 适配器 — 模拟实现（随机 2-5 分钟延迟）
 * <p>
 * 文生图任务模拟，返回预设的图片 URL。
 */
@Component
public class StableDiffusionAdapter implements ModelAdapter {

    private static final Logger log = LoggerFactory.getLogger(StableDiffusionAdapter.class);

    private final Map<String, LocalDateTime> completionMap = new ConcurrentHashMap<>();

    private static final String[] MOCK_IMAGES = {
        "https://picsum.photos/seed/1/1920/1080",
        "https://picsum.photos/seed/2/1920/1080",
        "https://picsum.photos/seed/3/1920/1080",
        "https://picsum.photos/seed/4/1920/1080",
        "https://picsum.photos/seed/5/1920/1080"
    };

    @Override
    public String vendor() { return "stable_diffusion"; }

    @Override
    public boolean supports(ModelType type) {
        return type == ModelType.TEXT_TO_IMAGE;
    }

    @Override
    public String submit(GenerateRequest request) {
        int delaySeconds = 5 + (int)(Math.random() * 10); // 5-15 秒
        String taskId = UUID.randomUUID().toString();
        completionMap.put(taskId, LocalDateTime.now().plusSeconds(delaySeconds));
        log.info("[SD 模拟] 提交文生图: taskId={}, prompt={}, 预计 {} 秒后完成",
            taskId, truncate(request.getPrompt(), 30), delaySeconds);
        return taskId;
    }

    @Override
    public TaskStatus queryStatus(String vendorTaskId) {
        LocalDateTime doneAt = completionMap.get(vendorTaskId);
        if (doneAt == null) {
            return TaskStatus.FAILED.withRawData("任务不存在");
        }
        if (LocalDateTime.now().isBefore(doneAt)) {
            return TaskStatus.PROCESSING.withRawData(null);
        }
        // 随机返回一张预设图片
        String imageUrl = MOCK_IMAGES[(int)(Math.random() * MOCK_IMAGES.length)];
        log.info("[SD 模拟] 文生图完成: vendorTaskId={}, url={}", vendorTaskId, imageUrl);
        completionMap.remove(vendorTaskId);
        return TaskStatus.SUCCESS.withRawData(imageUrl);
    }

    @Override
    public GenerateResponse parseResult(String vendorTaskId, Object vendorResponse) {
        GenerateResponse response = new GenerateResponse();
        response.setResultUrl(vendorResponse.toString());
        return response;
    }

    private static String truncate(String s, int max) {
        return s == null ? "" : (s.length() <= max ? s : s.substring(0, max) + "...");
    }
}
