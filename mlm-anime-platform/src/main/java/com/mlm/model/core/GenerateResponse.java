package com.mlm.model.core;

import com.mlm.common.enums.StepStatus;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一 AI 生成响应
 * <p>
 * 各厂商适配器将厂商原始返回解析为此通用格式。
 * 厂商特有字段通过 extensions 透传，前端按需展示。
 * 业务层通过 status 判断任务是否完成，通过 resultUrl 获取产出物。
 *
 * @see com.mlm.model.core.ModelAdapter#parseResult(String, Object)
 */
@Data
public class GenerateResponse {

    /** 本地任务 ID（关联 task 表） */
    private Long taskId;

    /** 任务状态：PROCESSING / SUCCESS / FAILED */
    private StepStatus status;

    /** 生成结果 OSS URL */
    private String resultUrl;

    /** 厂商特有扩展字段（如时长、格式、风格标签等） */
    private Map<String, Object> extensions = new HashMap<>();

    /** 创建处理中的响应（提交成功后调用） */
    public static GenerateResponse processing(Long taskId) {
        GenerateResponse r = new GenerateResponse();
        r.setTaskId(taskId);
        r.setStatus(StepStatus.PROCESSING);
        return r;
    }
}
