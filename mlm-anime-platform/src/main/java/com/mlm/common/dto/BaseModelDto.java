package com.mlm.common.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 模型返回基类 — 通用字段 + 扩展 Map 存放厂商特有字段
 * <p>
 * {@link com.mlm.model.core.GenerateResponse} 已替代此类的部分职责，
 * 保留作为下层 DTO 供特定场景使用。
 */
@Data
public class BaseModelDto {
    /** 厂商侧任务 ID */
    private String taskId;
    /** 任务状态描述 */
    private String status;
    /** 结果访问 URL */
    private String resultUrl;
    /** 厂商特有扩展字段 */
    private Map<String, Object> extensions = new HashMap<>();
}
