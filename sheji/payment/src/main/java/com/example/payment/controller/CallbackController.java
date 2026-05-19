package com.example.payment.controller;

import com.example.payment.callback.CallbackChainExecutor;
import com.example.payment.callback.CallbackContext;
import com.example.payment.callback.CallbackResult;
import com.example.payment.model.ApiResult;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/callback")
public class CallbackController {

    private final CallbackChainExecutor chainExecutor;

    public CallbackController(CallbackChainExecutor chainExecutor) {
        this.chainExecutor = chainExecutor;
    }

    /** 获取所有渠道的链结构 */
    @GetMapping("/chains")
    public ApiResult chains() {
        Map<String, Object> data = new LinkedHashMap<>();
        for (String channel : chainExecutor.channels()) {
            Map<String, Object> channelInfo = new LinkedHashMap<>();
            channelInfo.put("handlers", chainExecutor.getHandlerDisplayNames(channel));
            data.put(channel, channelInfo);
        }
        return ApiResult.ok(data);
    }

    /** 获取指定渠道的回调处理链 */
    @GetMapping("/chain")
    public ApiResult chain(@RequestParam String channel) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("channel", channel.toUpperCase());
        data.put("handlers", chainExecutor.getHandlerDisplayNames(channel));
        return ApiResult.ok(data);
    }

    /**
     * 模拟回调通知 —— 执行该渠道专属责任链。
     *
     * 请求参数可控制各步骤行为：
     * - sign/signature=INVALID → 步骤1验签失败
     * - orderNo=重复值        → 步骤2幂等跳过（第二次请求）
     * - amount=0              → 步骤3金额异常失败
     * - currentStatus=X       → 步骤4状态流转控制
     * - riskLevel=LOW         → 步骤5跳过低风险
     * - riskLevel=BLACK       → 步骤5风控拦截
     * - notifyUrl含fail       → 步骤6通知失败
     */
    @PostMapping("/notify/{channel}")
    public ApiResult notify(@PathVariable String channel, @RequestBody Map<String, String> params) {
        String orderNo = params.getOrDefault("orderNo", "ORD" + System.currentTimeMillis());
        CallbackContext ctx = new CallbackContext(channel.toUpperCase(), orderNo, params);
        CallbackResult result = chainExecutor.execute(channel, ctx);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("channel", channel);
        data.put("orderNo", orderNo);
        data.put("success", result.isSuccess());
        data.put("failedAt", result.getFailedAt());
        data.put("errorMsg", result.getErrorMsg());
        data.put("totalCostMs", result.getTotalCostMs());
        data.put("steps", result.getSteps());

        if (result.isSuccess()) {
            return ApiResult.ok("回调处理完成", data);
        } else {
            return ApiResult.ok("回调处理异常终止于 [" + result.getFailedAt() + "]", data);
        }
    }
}
