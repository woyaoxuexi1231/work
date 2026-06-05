package com.mlm.model.adapter;

import com.mlm.common.enums.ModelType;
import com.mlm.model.core.GenerateRequest;
import com.mlm.model.core.GenerateResponse;
import com.mlm.model.core.ModelAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenAI 适配器 — 文生文（剧本润色/分镜拆分）模型接入
 * <p>
 * 【当前实现：模拟模式】
 * 当前为开发/演示阶段的模拟实现，提交后随机延迟 5-15 秒返回预设结果。
 * 上线前需要替换为真实的 OpenAI API 调用。
 * <p>
 * 【模拟特性】
 * <ul>
 *   <li>submit: 生成随机 UUID 作为任务 ID，记录预计完成时间</li>
 *   <li>queryStatus: 根据当前时间判断返回 PROCESSING 或 SUCCESS</li>
 *   <li>parseResult: 返回预设的剧本分场 JSON</li>
 * </ul>
 * <p>
 * 【生产迁移】
 * 将 submit 改为调用 {@code POST https://api.openai.com/v1/chat/completions}，
 * queryStatus 改为查询 OpenAI 的批处理任务状态，
 * parseResult 改为解析 OpenAI 的响应格式。
 *
 * @author mlm
 */
@Component
public class OpenAiAdapter implements ModelAdapter {

    private static final Logger log = LoggerFactory.getLogger(OpenAiAdapter.class);

    /** vendorTaskId → 预计完成时间戳映射 */
    private final Map<String, LocalDateTime> completionMap = new ConcurrentHashMap<>();

    /**
     * {@inheritDoc}
     * <p>
     * 返回 {@code "openai"}。
     */
    @Override
    public String vendor() {
        return "openai";
    }

    /**
     * {@inheritDoc}
     * <p>
     * 支持 {@link ModelType#TEXT_TO_TEXT}（文生文）。
     */
    @Override
    public boolean supports(ModelType type) {
        return type == ModelType.TEXT_TO_TEXT;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 【模拟实现】生成随机延迟 5-15 秒，返回 UUID 作为厂商任务 ID。
     * 生产环境应改为调用 OpenAI Chat Completion API。
     */
    @Override
    public String submit(GenerateRequest request) {
        int delaySeconds = 5 + (int) (Math.random() * 10);
        String taskId = UUID.randomUUID().toString();
        completionMap.put(taskId, LocalDateTime.now().plusSeconds(delaySeconds));
        log.info("[OpenAI 模拟] 提交文本生成: taskId={}, promptLength={}, 预计{}秒后完成",
                taskId, request.getPrompt() != null ? request.getPrompt().length() : 0, delaySeconds);
        return taskId;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 【模拟实现】根据预计完成时间判断状态。
     * 超时后返回预设的剧本分场 JSON。
     */
    @Override
    public TaskStatus queryStatus(String vendorTaskId) {
        LocalDateTime doneAt = completionMap.get(vendorTaskId);
        if (doneAt == null) {
            log.warn("[OpenAI 模拟] 任务不存在: vendorTaskId={}", vendorTaskId);
            return TaskStatus.FAILED.withRawData("任务不存在");
        }
        if (LocalDateTime.now().isBefore(doneAt)) {
            return TaskStatus.PROCESSING.withRawData(null);
        }

        // 模拟 AI 生成的剧本分场结果
        String mockResult = "{"
                + "\"text\": \"第1场 校园晨光 [场景: 校门口，清晨] [角色: 小明, 小红] "
                + "(小明) 早上好！ ... [画面: 樱花飘落]\""
                + "}";
        log.info("[OpenAI 模拟] 文本生成完成: vendorTaskId={}", vendorTaskId);
        completionMap.remove(vendorTaskId);
        return TaskStatus.SUCCESS.withRawData(mockResult);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 将模拟的 JSON 文本包装为统一响应格式。
     */
    @Override
    public GenerateResponse parseResult(String vendorTaskId, Object vendorResponse) {
        GenerateResponse response = new GenerateResponse();
        response.setResultUrl("https://mock.example.com/script/" + vendorTaskId + ".json");
        response.setExtensions(Collections.singletonMap("rawText", vendorResponse.toString()));
        return response;
    }
}
