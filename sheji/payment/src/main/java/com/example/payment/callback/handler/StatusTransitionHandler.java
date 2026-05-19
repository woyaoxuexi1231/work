package com.example.payment.callback.handler;

import com.example.payment.callback.AbstractCallbackHandler;
import com.example.payment.callback.CallbackContext;
import org.springframework.stereotype.Component;

/**
 * 步骤4: 状态流转 —— 将订单状态从"待支付"更新为"已支付"。
 * 状态机校验：只有"待支付"状态才能流转，否则说明状态异常。
 */
@Component("statusTransition")
public class StatusTransitionHandler extends AbstractCallbackHandler {

    @Override
    protected void doHandle(CallbackContext ctx) {
        String currentStatus = ctx.getParam("currentStatus");
        if (currentStatus == null) {
            currentStatus = "WAIT_PAY"; // 模拟默认
        }
        if ("SUCCESS".equals(currentStatus)) {
            skip("订单已是终态，无需流转");
        }
        if (!"WAIT_PAY".equals(currentStatus)) {
            fail("订单状态异常: " + currentStatus + "，无法从当前状态流转到已支付");
        }
        ctx.set("newStatus", "SUCCESS");
    }
}
