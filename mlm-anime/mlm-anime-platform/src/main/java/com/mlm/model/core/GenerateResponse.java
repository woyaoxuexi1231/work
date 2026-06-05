package com.mlm.model.core;

import com.mlm.common.enums.StepStatus;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一 AI 生成响应 — 各厂商适配器返回的统一格式
 * <p>
 * 【用途】
 * 各厂商适配器将厂商原始返回解析为此通用格式后返回到 {@link ModelGateway}。
 * 业务层通过 status 判断任务是否完成，通过 resultUrl 获取产出物。
 * <p>
 * 【数据字段】
 * <ul>
 *   <li>taskId — 本地任务 ID（关联 task 表，用于追踪）</li>
 *   <li>status — 任务状态（PROCESSING / SUCCESS / FAILED）</li>
 *   <li>resultUrl — 生成结果的 OSS URL（图片/视频）</li>
 *   <li>extensions — 厂商特有扩展字段（如时长、格式、风格标签等）</li>
 * </ul>
 *
 * @author mlm
 * @see ModelAdapter#parseResult(String, Object)
 * @see GenerateRequest
 */
@Data
public class GenerateResponse {

    /** 本地任务 ID（关联 task 表） */
    private Long taskId;

    /** 任务状态：PROCESSING / SUCCESS / FAILED */
    private StepStatus status;

    /** 生成结果的 OSS URL（图片/视频文件） */
    private String resultUrl;

    /** 厂商特有扩展字段（如时长、格式、风格标签等） */
    private Map<String, Object> extensions = new HashMap<>();

    /**
     * 创建处理中的响应（提交成功后调用）
     *
     * @param taskId 本地任务 ID
     * @return 处理中状态的响应
     */
    public static GenerateResponse processing(Long taskId) {
        GenerateResponse response = new GenerateResponse();
        response.setTaskId(taskId);
        response.setStatus(StepStatus.PROCESSING);
        return response;
    }
}
