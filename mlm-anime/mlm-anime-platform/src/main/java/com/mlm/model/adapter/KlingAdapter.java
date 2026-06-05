package com.mlm.model.adapter;

import com.mlm.common.enums.ModelType;
import com.mlm.model.core.GenerateRequest;
import com.mlm.model.core.GenerateResponse;
import com.mlm.model.core.ModelAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 可灵 (Kling) 适配器 — 图生视频（成片合成）模型接入
 * <p>
 * 【当前实现：模拟模式】
 * 当前为开发/演示阶段的模拟实现，提交后随机延迟 5-15 秒返回预设视频 URL。
 * 上线前需要替换为真实的可灵 Kling API 调用。
 * <p>
 * 【模拟特性】
 * <ul>
 *   <li>submit: 生成随机 UUID，记录预计完成时间</li>
 *   <li>queryStatus: 根据当前时间判断，超时后返回模拟视频 URL</li>
 *   <li>parseResult: 返回模拟视频 URL 及扩展属性（时长、格式）</li>
 * </ul>
 *
 * @author mlm
 */
@Component
public class KlingAdapter implements ModelAdapter {

    private static final Logger log = LoggerFactory.getLogger(KlingAdapter.class);

    /** vendorTaskId → 预计完成时间戳映射 */
    private final Map<String, LocalDateTime> completionMap = new ConcurrentHashMap<>();

    /**
     * {@inheritDoc}
     * <p>
     * 返回 {@code "kling"}。
     */
    @Override
    public String vendor() {
        return "kling";
    }

    /**
     * {@inheritDoc}
     * <p>
     * 支持 {@link ModelType#IMAGE_TO_VIDEO}（图生视频）。
     */
    @Override
    public boolean supports(ModelType type) {
        return type == ModelType.IMAGE_TO_VIDEO;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 【模拟实现】生成随机延迟 5-15 秒，返回 UUID 作为厂商任务 ID。
     * 生产环境应改为调用可灵 Kling API。
     */
    @Override
    public String submit(GenerateRequest request) {
        int delaySeconds = 5 + (int) (Math.random() * 10);
        String taskId = UUID.randomUUID().toString();
        completionMap.put(taskId, LocalDateTime.now().plusSeconds(delaySeconds));
        log.info("[Kling 模拟] 提交图生视频: taskId={}, refImage={}, 预计{}秒后完成",
                taskId, request.getReferenceImageUrl(), delaySeconds);
        return taskId;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 【模拟实现】根据预计完成时间判断状态。
     * 超时后返回模拟视频 URL。
     */
    @Override
    public TaskStatus queryStatus(String vendorTaskId) {
        LocalDateTime doneAt = completionMap.get(vendorTaskId);
        if (doneAt == null) {
            log.warn("[Kling 模拟] 任务不存在: vendorTaskId={}", vendorTaskId);
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

    /**
     * {@inheritDoc}
     * <p>
     * 将模拟视频 URL 包装为统一响应格式，附带扩展属性（时长、格式）。
     */
    @Override
    public GenerateResponse parseResult(String vendorTaskId, Object vendorResponse) {
        GenerateResponse response = new GenerateResponse();
        response.setResultUrl(vendorResponse.toString());

        Map<String, Object> extensions = new HashMap<>();
        extensions.put("duration", 10);
        extensions.put("format", "mp4");
        response.setExtensions(extensions);

        return response;
    }
}
