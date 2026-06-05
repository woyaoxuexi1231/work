package com.mlm.common.exception;



/**
 * Pipeline 状态流异常 — 非法状态跳转、项目不存在等
 * <p>
 * 由 {@link GlobalExceptionHandler} 捕获后返回 400 状态码。
 * 通常用于以下场景：
 * <ul>
 *   <li>剧集不存在</li>
 *   <li>非法的状态流转（如从 COMPLETED 继续推进）</li>
 *   <li>不允许的驳回路径</li>
 *   <li>步骤未失败但尝试重试</li>
 * </ul>
 *
 * @author mlm
 * @see com.mlm.pipeline.engine.PipelineEngine
 * @see com.mlm.pipeline.engine.StateMachine
 */
public class PipelineException extends RuntimeException {

    /**
     * 创建 Pipeline 异常
     *
     * @param message 错误描述
     */
    public PipelineException(String message) {
        super(message);
    }

    /**
     * 创建 Pipeline 异常（含根因）
     *
     * @param message 错误描述
     * @param cause   根因异常
     */
    public PipelineException(String message, Throwable cause) {
        super(message, cause);
    }
}
