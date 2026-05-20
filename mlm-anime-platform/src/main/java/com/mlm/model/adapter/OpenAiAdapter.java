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
 * OpenAI 适配器 — 模拟实现（随机 2-5 分钟延迟）
 * <p>
 * submit 时记录预计完成时间（当前时间 + 随机 delay），
 * queryStatus 根据当前时间判断返回 PROCESSING 或 SUCCESS，
 * parseResult 返回预设 JSON。
 */
@Component
public class OpenAiAdapter implements ModelAdapter {

    private static final Logger log = LoggerFactory.getLogger(OpenAiAdapter.class);

    /** vendorTaskId → 预计完成时间 */
    private final Map<String, LocalDateTime> completionMap = new ConcurrentHashMap<>();

    @Override
    public String vendor() { return "openai"; }

    @Override
    public boolean supports(ModelType type) {
        return type == ModelType.TEXT_TO_TEXT;
    }

    @Override
    public String submit(GenerateRequest request) {
        int delaySeconds = 5 + (int)(Math.random() * 10); // 5-15 秒（测试用，正式改为 120-300）
        String taskId = UUID.randomUUID().toString();
        completionMap.put(taskId, LocalDateTime.now().plusSeconds(delaySeconds));
        log.info("[OpenAI 模拟] 提交文本生成: taskId={}, 预计 {} 秒后完成", taskId, delaySeconds);
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
        // 预设回复
        String mockResult = "{\"text\": \"第1场 校园晨光 [场景: 校门口，清晨] [角色: 小明, 小红] (小明) 早上好！ ... [画面: 樱花飘落]\"}";
        log.info("[OpenAI 模拟] 任务完成: vendorTaskId={}", vendorTaskId);
        completionMap.remove(vendorTaskId);
        return TaskStatus.SUCCESS.withRawData(mockResult);
    }

    @Override
    public GenerateResponse parseResult(String vendorTaskId, Object vendorResponse) {
        GenerateResponse response = new GenerateResponse();
        response.setResultUrl("https://mock.example.com/script/" + vendorTaskId + ".json");
        response.setExtensions(Map.of("rawText", vendorResponse.toString()));
        return response;
    }
}
