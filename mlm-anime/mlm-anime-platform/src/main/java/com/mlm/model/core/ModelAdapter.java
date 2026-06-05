package com.mlm.model.core;

import com.mlm.common.enums.ModelType;

/**
 * 模型适配器接口 — 各 AI 厂商接入的统一契约
 * <p>
 * 【设计模式】适配器模式（Adapter Pattern）
 * 每种厂商/模型类型一个实现类，通过 {@link ModelGateway} 统一调度。
 * 新增厂商只需实现此接口并注册为 Spring Bean（@Component），
 * ModelGateway 会自动注入并管理。
 * <p>
 * 【当前实现】
 * <ul>
 *   <li>{@link com.mlm.model.adapter.OpenAiAdapter} — 文生文（剧本润色/分镜拆分）</li>
 *   <li>{@link com.mlm.model.adapter.StableDiffusionAdapter} — 文生图（场景生成）</li>
 *   <li>{@link com.mlm.model.adapter.KlingAdapter} — 图生视频（成片合成）</li>
 * </ul>
 * <p>
 * 【接入规范】
 * <ol>
 *   <li>实现此接口，添加 @Component 注解</li>
 *   <li>{@link #vendor()} 返回唯一的厂商标识字符串</li>
 *   <li>{@link #supports(ModelType)} 声明支持的模型类型</li>
 *   <li>{@link #submit(GenerateRequest)} 提交任务并返回厂商侧任务 ID</li>
 *   <li>{@link #queryStatus(String)} 轮询厂商侧任务进度</li>
 *   <li>{@link #parseResult(String, Object)} 解析厂商返回数据为统一格式</li>
 * </ol>
 *
 * @author mlm
 * @see ModelGateway
 * @see GenerateRequest
 * @see GenerateResponse
 */
public interface ModelAdapter {

    /**
     * 返回厂商标识
     * <p>
     * 用于 {@link ModelGateway} 在选择适配器时进行精确匹配。
     * 必须全局唯一，建议使用小写字母和下划线，如 "openai"、"stable_diffusion"。
     *
     * @return 唯一的厂商标识字符串
     */
    String vendor();

    /**
     * 判断该适配器是否支持指定模型类型
     * <p>
     * {@link ModelGateway#findAdapter} 使用 vendor + type 的组合
     * 筛选匹配的适配器。
     *
     * @param type 模型类型（文生文/文生图/图生视频）
     * @return true=支持该类型
     */
    boolean supports(ModelType type);

    /**
     * 提交生成任务到厂商
     * <p>
     * 【职责】
     * <ol>
     *   <li>将统一的 {@link GenerateRequest} 转为厂商自定义的 API 请求</li>
     *   <li>调用厂商 HTTP API 提交任务</li>
     *   <li>返回厂商侧的任务 ID，用于后续轮询查询进度</li>
     * </ol>
     *
     * @param request 统一生成请求（包含 prompt、参数、episodeId 等）
     * @return 厂商侧的任务 ID（用于 queryStatus）
     */
    String submit(GenerateRequest request);

    /**
     * 查询厂商侧任务状态
     * <p>
     * 根据 submit 返回的 vendorTaskId 轮询厂商 API 获取任务当前状态。
     * 返回 {@link TaskStatus} 枚举 + 原始数据（用于后续 parseResult）。
     *
     * @param vendorTaskId submit 返回的厂商任务 ID
     * @return 封装了状态和原始数据的 TaskStatus
     */
    TaskStatus queryStatus(String vendorTaskId);

    /**
     * 将厂商返回的原始结果解析为统一响应格式
     * <p>
     * 不同厂商的返回格式各异（JSON / XML / 文本等），此方法负责
     * 将厂商原始数据转为统一的 {@link GenerateResponse} 供上层使用。
     *
     * @param vendorTaskId   厂商任务 ID（用于日志和追踪）
     * @param vendorResponse 厂商返回的原始数据（来自 TaskStatus.getRawData()）
     * @return 统一格式的生成响应
     */
    GenerateResponse parseResult(String vendorTaskId, Object vendorResponse);

    /**
     * 厂商任务状态枚举
     * <p>
     * 三种状态 + 关联原始数据，供 {@link ModelGateway#pollAndUpdate} 根据状态
     * 执行不同处理分支。
     */
    enum TaskStatus {
        /** 处理中 — 继续轮询 */
        PROCESSING,
        /** 成功 — 调用 parseResult 解析结果 */
        SUCCESS,
        /** 失败 — 标记任务失败 */
        FAILED;

        /** 厂商返回的原始数据（如 JSON 字符串、URL 等） */
        private Object rawData;

        /**
         * 获取厂商返回的原始数据
         *
         * @return 原始数据对象
         */
        public Object getRawData() {
            return rawData;
        }

        /**
         * 设置原始数据并返回自身（链式调用）
         *
         * @param rawData 厂商返回的原始数据
         * @return 当前 TaskStatus 实例
         */
        public TaskStatus withRawData(Object rawData) {
            this.rawData = rawData;
            return this;
        }
    }
}
