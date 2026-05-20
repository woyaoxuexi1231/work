package com.mlm.model.core;

import com.mlm.common.enums.ModelType;

/**
 * 模型适配器接口 — 各 AI 厂商接入的统一契约
 * <p>
 * 适配器模式：每种厂商/模型类型一个实现类。
 * 通过 {@link com.mlm.model.core.ModelGateway} 统一调度。
 * 新增厂商只需实现此接口并注册为 Spring Bean。
 * <p>
 * 当前实现：
 * <ul>
 *   <li>{@link com.mlm.model.adapter.OpenAiAdapter} — 文生文（剧本/分镜）</li>
 *   <li>{@link com.mlm.model.adapter.StableDiffusionAdapter} — 文生图</li>
 *   <li>{@link com.mlm.model.adapter.KlingAdapter} — 图生视频</li>
 * </ul>
 *
 * @see com.mlm.model.core.ModelGateway
 */
public interface ModelAdapter {

    /** 厂商标识，如 openai / stable_diffusion / kling */
    String vendor();

    /** 该适配器是否支持指定模型类型 */
    boolean supports(ModelType type);

    /**
     * 提交生成任务到厂商
     *
     * @param request 统一生成请求
     * @return 厂商侧的任务 ID（用于后续轮询）
     */
    String submit(GenerateRequest request);

    /**
     * 查询厂商侧任务状态
     *
     * @param vendorTaskId submit 返回的厂商任务 ID
     * @return 封装了状态和原始数据的 TaskStatus
     */
    TaskStatus queryStatus(String vendorTaskId);

    /**
     * 将厂商返回的原始结果解析为统一响应
     *
     * @param vendorTaskId  厂商任务 ID
     * @param vendorResponse 厂商返回的原始数据
     * @return 统一格式的响应
     */
    GenerateResponse parseResult(String vendorTaskId, Object vendorResponse);

    /** 厂商任务状态枚举 */
    enum TaskStatus {
        PROCESSING, SUCCESS, FAILED;

        private Object rawData;

        public Object getRawData() { return rawData; }

        public TaskStatus withRawData(Object rawData) {
            this.rawData = rawData;
            return this;
        }
    }
}
