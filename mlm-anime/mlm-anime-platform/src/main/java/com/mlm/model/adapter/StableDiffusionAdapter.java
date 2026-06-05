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
 * Stable Diffusion 适配器 — 文生图（场景生成）模型接入
 * <p>
 * 【当前实现：模拟模式】
 * 当前为开发/演示阶段的模拟实现，提交后随机延迟 5-15 秒返回预设图片 URL。
 * 上线前需要替换为真实的 Stable Diffusion API（或 Stability AI API）调用。
 * <p>
 * 【模拟特性】
 * <ul>
 *   <li>submit: 生成随机 UUID，记录预计完成时间</li>
 *   <li>queryStatus: 根据当前时间判断，超时后返回随机预设图片</li>
 *   <li>parseResult: 包装图片 URL 到统一响应</li>
 * </ul>
 *
 * @author mlm
 */
@Component
public class StableDiffusionAdapter implements ModelAdapter {

    private static final Logger log = LoggerFactory.getLogger(StableDiffusionAdapter.class);

    /** vendorTaskId → 预计完成时间戳映射 */
    private final Map<String, LocalDateTime> completionMap = new ConcurrentHashMap<>();

    /** 模拟图片 URL 池（随机返回） */
    private static final String[] MOCK_IMAGES = {
            "https://picsum.photos/seed/1/1920/1080",
            "https://picsum.photos/seed/2/1920/1080",
            "https://picsum.photos/seed/3/1920/1080",
            "https://picsum.photos/seed/4/1920/1080",
            "https://picsum.photos/seed/5/1920/1080"
    };

    /**
     * {@inheritDoc}
     * <p>
     * 返回 {@code "stable_diffusion"}。
     */
    @Override
    public String vendor() {
        return "stable_diffusion";
    }

    /**
     * {@inheritDoc}
     * <p>
     * 支持 {@link ModelType#TEXT_TO_IMAGE}（文生图）。
     */
    @Override
    public boolean supports(ModelType type) {
        return type == ModelType.TEXT_TO_IMAGE;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 【模拟实现】生成随机延迟 5-15 秒，返回 UUID 作为厂商任务 ID。
     * 生产环境应改为调用 Stability AI 或本地 SD WebUI API。
     */
    @Override
    public String submit(GenerateRequest request) {
        int delaySeconds = 5 + (int) (Math.random() * 10);
        String taskId = UUID.randomUUID().toString();
        completionMap.put(taskId, LocalDateTime.now().plusSeconds(delaySeconds));
        log.info("[SD 模拟] 提交文生图: taskId={}, prompt={}, 预计{}秒后完成",
                taskId, truncate(request.getPrompt(), 30), delaySeconds);
        return taskId;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 【模拟实现】根据预计完成时间判断状态。
     * 超时后从预设图片池中随机返回一张。
     */
    @Override
    public TaskStatus queryStatus(String vendorTaskId) {
        LocalDateTime doneAt = completionMap.get(vendorTaskId);
        if (doneAt == null) {
            log.warn("[SD 模拟] 任务不存在: vendorTaskId={}", vendorTaskId);
            return TaskStatus.FAILED.withRawData("任务不存在");
        }
        if (LocalDateTime.now().isBefore(doneAt)) {
            return TaskStatus.PROCESSING.withRawData(null);
        }

        String imageUrl = MOCK_IMAGES[(int) (Math.random() * MOCK_IMAGES.length)];
        log.info("[SD 模拟] 文生图完成: vendorTaskId={}, url={}", vendorTaskId, imageUrl);
        completionMap.remove(vendorTaskId);
        return TaskStatus.SUCCESS.withRawData(imageUrl);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 将模拟图片 URL 包装为统一响应格式。
     */
    @Override
    public GenerateResponse parseResult(String vendorTaskId, Object vendorResponse) {
        GenerateResponse response = new GenerateResponse();
        response.setResultUrl(vendorResponse.toString());
        return response;
    }

    /**
     * 截断字符串到指定长度（用于日志显示）
     *
     * @param s   原始字符串
     * @param max 最大长度
     * @return 截断后的字符串
     */
    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
