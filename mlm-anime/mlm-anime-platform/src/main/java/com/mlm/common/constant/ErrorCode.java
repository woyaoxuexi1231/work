package com.mlm.common.constant;

/**
 * 业务错误码常量定义
 * <p>
 * 统一管理系统中使用的业务错误码，避免 magic number。
 * 错误码按模块划分区间：
 * <ul>
 *   <li>400-499: 通用请求错误</li>
 *   <li>1000-1999: 项目/剧集模块</li>
 *   <li>2000-2999: Pipeline/状态机模块</li>
 *   <li>3000-3999: AI 模型模块</li>
 *   <li>4000-4999: 资源/文件模块</li>
 *   <li>5000-5999: 通知/消息模块</li>
 * </ul>
 *
 * @author mlm
 */
public final class ErrorCode {

    private ErrorCode() {
        // 常量类，禁止实例化
    }

    // ==================== 通用 (400-499) ====================

    /** 请求参数不合法 */
    public static final int INVALID_REQUEST = 400;
    /** 未登录或 Token 无效 */
    public static final int UNAUTHORIZED = 401;
    /** 无操作权限 */
    public static final int FORBIDDEN = 403;
    /** 请求的资源不存在 */
    public static final int NOT_FOUND = 404;

    // ==================== 项目/剧集 (1000-1999) ====================

    /** 项目不存在 */
    public static final int PROJECT_NOT_FOUND = 1001;
    /** 项目名称不能为空 */
    public static final int PROJECT_NAME_EMPTY = 1002;
    /** 项目名称过长 */
    public static final int PROJECT_NAME_TOO_LONG = 1003;
    /** 剧集不存在 */
    public static final int EPISODE_NOT_FOUND = 1101;
    /** 剧集参数不完整 */
    public static final int EPISODE_PARAMS_INCOMPLETE = 1102;

    // ==================== Pipeline (2000-2999) ====================

    /** 非法状态流转 */
    public static final int ILLEGAL_TRANSITION = 2001;
    /** 步骤未失败，无需重试 */
    public static final int STEP_NOT_FAILED = 2002;
    /** 非法阶段操作 */
    public static final int STAGE_PERMISSION_DENIED = 2003;

    // ==================== AI 模型 (3000-3999) ====================

    /** 未找到匹配的模型适配器 */
    public static final int ADAPTER_NOT_FOUND = 3001;
    /** 模型配置未找到 */
    public static final int CONFIG_NOT_FOUND = 3002;

    private ErrorCode(int code) {
        // 防止误用
    }
}
