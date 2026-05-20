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
 * 可灵 (Kling) 适配器 — 模拟实现（随机 2-5 分钟延迟）
 * <p>
 * 图生视频任务模拟，返回预设的视频 URL。
 */
@Component
public class KlingAdapter implements ModelAdapter {

    private static final Logger log = LoggerFactory.getLogger(KlingAdapter.class);

    private final Map<String, LocalDateTime> completionMap = new ConcurrentHashMap<>();

    @Override
    public String vendor() { return "kling"; }

    @Override
    public boolean supports(ModelType type) {
        return type == ModelType.IMAGE_TO_VIDEO;
    }

    @Override
    public String submit(GenerateRequest request) {
        int delaySeconds = 5 + (int)(Math.random() * 10); // 5-15 秒
        String taskId = UUID.randomUUID().toString();
        completionMap.put(taskId, LocalDateTime.now().plusSeconds(delaySeconds));
        log.info("[Kling 模拟] 提交图生视频: taskId={}, refImage={}, 预计 {} 秒后完成",
            taskId, request.getReferenceImageUrl(), delaySeconds);
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
        String videoUrl = "https://example.com/videos/" + vendorTaskId + ".mp4";
        log.info("[Kling 模拟] 视频生成完成: vendorTaskId={}, url={}", vendorTaskId, videoUrl);
        completionMap.remove(vendorTaskId);
        return TaskStatus.SUCCESS.withRawData(videoUrl);
    }

    @Override
    public GenerateResponse parseResult(String vendorTaskId, Object vendorResponse) {
        GenerateResponse response = new GenerateResponse();
        response.setResultUrl(vendorResponse.toString());
        response.getExtensions().put("duration", 10);
        response.getExtensions().put("format", "mp4");
        return response;
    }
}
