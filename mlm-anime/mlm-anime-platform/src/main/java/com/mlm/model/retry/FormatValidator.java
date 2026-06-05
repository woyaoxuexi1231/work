package com.mlm.model.retry;

import com.mlm.common.enums.ModelType;
import com.mlm.model.core.GenerateResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AI 返回格式校验器 — 判断厂商返回的内容是否符合预期格式
 * <p>
 * 【职责】
 * 校验 AI 厂商返回的数据是否包含必要字段，防止因厂商返回格式异常
 * 导致下游处理出错。校验失败会触发自动重试机制。
 * <p>
 * 【校验规则 — 按模型类型区分】
 * <ul>
 *   <li>{@link ModelType#TEXT_TO_TEXT} — 有 resultUrl 或 extensions 字段即可</li>
 *   <li>{@link ModelType#TEXT_TO_IMAGE} — 必须有非空的 resultUrl</li>
 *   <li>{@link ModelType#IMAGE_TO_VIDEO} — 必须有非空的 resultUrl</li>
 * </ul>
 *
 * @author mlm
 * @see com.mlm.model.core.ModelGateway#handleTaskSuccess
 */
@Component
public class FormatValidator {

    private static final Logger log = LoggerFactory.getLogger(FormatValidator.class);

    /**
     * 校验 AI 响应格式是否合法
     * <p>
     * 根据模型类型执行业差异化校验规则，确保响应数据包含必要字段。
     *
     * @param response AI 模型统一响应
     * @param type     模型类型
     * @return true=格式合法，false=格式异常（将触发自动重试）
     */
    public boolean validate(GenerateResponse response, ModelType type) {
        if (response == null) {
            log.warn("[FormatValidator] 校验失败: response 为 null");
            return false;
        }

        switch (type) {
            case TEXT_TO_TEXT:
                return validateTextResponse(response);
            case TEXT_TO_IMAGE:
            case IMAGE_TO_VIDEO:
                return validateMediaResponse(response);
            default:
                log.warn("[FormatValidator] 未知模型类型，默认通过: type={}", type);
                return true;
        }
    }

    /**
     * 校验文本类响应（文生文）
     * <p>
     * 文本类结果可以是通过 resultUrl 引用的文件，也可以是
     * extensions 中内嵌的原始文本。
     */
    private boolean validateTextResponse(GenerateResponse response) {
        if (response.getResultUrl() == null && response.getExtensions().isEmpty()) {
            log.warn("[FormatValidator] 文本响应缺少结果数据: resultUrl=null, extensions=empty");
            return false;
        }
        return true;
    }

    /**
     * 校验媒体类响应（文生图/图生视频）
     * <p>
     * 图片和视频必须包含可访问的 resultUrl。
     */
    private boolean validateMediaResponse(GenerateResponse response) {
        if (response.getResultUrl() == null || response.getResultUrl().trim().isEmpty()) {
            log.warn("[FormatValidator] 媒体响应缺少 resultUrl: type={}", response.getStatus());
            return false;
        }
        return true;
    }
}
