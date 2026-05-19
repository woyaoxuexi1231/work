package com.example.payment.callback;

import java.util.List;
import java.util.Map;

/**
 * 回调渠道差异化策略接口。
 * 每个支付渠道实现此接口，定义：
 * 1. 渠道差异化的处理逻辑（验签、数据解析）
 * 2. 该渠道专属的责任链步骤列表
 *
 * 新渠道接入：新建 @Component 实现类，零改已有代码。
 */
public interface CallbackChannelStrategy {

    /** 渠道标识，如 "ALIPAY" */
    String getChannel();

    /** 渠道差异化验签 */
    boolean verifySignature(Map<String, String> params);

    /** 渠道差异化回调数据解析 */
    Map<String, Object> parseCallback(Map<String, String> params);

    /**
     * 该渠道的责任链步骤列表（按顺序）。
     * 返回的是 Handler 的 Spring bean name，如：
     * ["signatureVerify", "idempotencyCheck", "amountVerify", ...]
     */
    List<String> getHandlerNames();
}
