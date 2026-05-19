package com.example.payment.callback.handler;

import com.example.payment.callback.AbstractCallbackHandler;
import com.example.payment.callback.CallbackContext;
import org.springframework.stereotype.Component;

/**
 * 步骤5: 风控上报 —— 将支付信息上报风控系统。
 * 低风险订单 → 抛出 SkipException 跳过（可选步骤）。
 * 高风险订单 → 必须上报，失败则终止链。
 */
@Component("riskReport")
public class RiskReportHandler extends AbstractCallbackHandler {

    @Override
    protected void doHandle(CallbackContext ctx) {
        String riskLevel = ctx.getParam("riskLevel");
        if (riskLevel == null) riskLevel = "LOW";

        if ("LOW".equalsIgnoreCase(riskLevel)) {
            // 低风险 → 跳过风控上报，但链继续
            skip("低风险订单，跳过低风险上报");
        }

        // 模拟风控上报
        if ("BLACK".equalsIgnoreCase(riskLevel)) {
            fail("风控黑名单订单，支付被拦截");
        }

        ctx.set("riskReported", true);
    }
}
