package com.mlm.model.core;

import com.mlm.common.enums.ModelType;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一 AI 生成请求 — 涵盖三种生成场景的通用入参
 * <p>
 * 【用途】
 * 涵盖文生文/文生图/图生视频三种场景的通用入参。
 * 各厂商适配器在 submit 时将此通用参数转为厂商自定义的 JSON 格式。
 * <p>
 * 【扩展字段】
 * 厂商特有参数通过 extraParams 透传（如采样步数、风格强度、CFG Scale 等），
 * 避免为每个厂商新增字段导致 GenerateRequest 膨胀。
 *
 * @author mlm
 * @see ModelAdapter#submit(GenerateRequest)
 * @see GenerateResponse
 */
@Data
public class GenerateRequest {

    /** 模型类型：文生文 / 文生图 / 图生视频 */
    private ModelType type;

    /** 厂商标识：openai / stable_diffusion / kling */
    private String vendor;

    /** 正向提示词（通用，所有模型类型均使用） */
    private String prompt;

    /** 反向提示词（用于图片/视频生成，排除不想要的内容） */
    private String negativePrompt;

    /** 生成宽度（像素），默认 1920 */
    private Integer width = 1920;

    /** 生成高度（像素），默认 1080 */
    private Integer height = 1080;

    /** 参考图 URL（图生视频时使用） */
    private String referenceImageUrl;

    /** 所属项目 ID */
    private Long projectId;

    /** 所属剧集 ID */
    private Long episodeId;

    /** 厂商特有参数透传（如采样步数、风格强度、CFG Scale 等） */
    private Map<String, Object> extraParams = new HashMap<>();
}
