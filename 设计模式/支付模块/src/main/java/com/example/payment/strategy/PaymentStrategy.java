package com.example.payment.strategy;

import com.example.payment.model.PaymentRequest;
import java.util.Map;

/**
 * 支付渠道策略接口。
 * 新渠道只需实现此接口并标注 @Component，即可零修改自动注册到上下文。
 */
public interface PaymentStrategy {

    /** 渠道标识，如 "ALIPAY"、"WECHAT" */
    String getChannel();

    /** 渠道中文名 */
    String getDisplayName();

    /** 发起支付 */
    Map<String, Object> pay(PaymentRequest request);

    /** 查询订单 */
    Map<String, Object> query(String orderNo);
}
