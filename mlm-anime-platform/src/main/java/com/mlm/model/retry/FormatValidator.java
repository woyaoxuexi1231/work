package com.mlm.model.retry;

import com.mlm.common.enums.ModelType;
import com.mlm.model.core.GenerateResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AI 返回格式校验器 — 判断厂商返回的内容是否符合预期格式
 * <p>
 * 校验失败会触发自动重试（在 {@link com.mlm.model.core.ModelGateway#pollAndUpdate} 中处理）。
 * 不同模型类型的校验规则不同：
 * <ul>
 *   <li>TEXT_TO_TEXT — 有扩展字段即可</li>
 *   <li>TEXT_TO_IMAGE / IMAGE_TO_VIDEO — 必须有 resultUrl</li>
 * </ul>
 */
@Component
public class FormatValidator {

    private static final Logger log = LoggerFactory.getLogger(FormatValidator.class);

    /**
     * 校验响应格式是否合法
     *
     * @param response AI 模型统一响应
     * @param type     模型类型
     * @return true=合法
     */
    public boolean validate(GenerateResponse response, ModelType type) {
        if (response == null) {
            log.warn("格式校验失败: response 为 null");
            return false;
        }

        switch (type) {
            case TEXT_TO_TEXT:
                // 文本类：只要包含扩展数据即可
                if (response.getResultUrl() == null && response.getExtensions().isEmpty()) {
                    log.warn("格式校验失败: 文本类型缺少结果数据");
                    return false;
                }
                return true;

            case TEXT_TO_IMAGE:
            case IMAGE_TO_VIDEO:
                // 图片/视频类：必须有可访问的结果 URL
                if (response.getResultUrl() == null || response.getResultUrl().trim().isEmpty()) {
                    log.warn("格式校验失败: 媒体类型缺少 resultUrl");
                    return false;
                }
                return true;

            default:
                return true;
        }
    }
}
