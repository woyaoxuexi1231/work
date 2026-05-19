package com.example.payment.callback;

import java.util.List;
import java.util.Map;

public class CallbackResult {
    private boolean success;
    private String failedAt;
    private String errorMsg;
    private long totalCostMs;
    private List<Map<String, Object>> steps;

    public static CallbackResult success(List<Map<String, Object>> steps, long totalCostMs) {
        CallbackResult r = new CallbackResult();
        r.success = true;
        r.steps = steps;
        r.totalCostMs = totalCostMs;
        return r;
    }

    public static CallbackResult fail(String handler, String error, List<Map<String, Object>> steps) {
        CallbackResult r = new CallbackResult();
        r.success = false;
        r.failedAt = handler;
        r.errorMsg = error;
        r.steps = steps;
        return r;
    }

    public boolean isSuccess() { return success; }
    public String getFailedAt() { return failedAt; }
    public String getErrorMsg() { return errorMsg; }
    public long getTotalCostMs() { return totalCostMs; }
    public List<Map<String, Object>> getSteps() { return steps; }
}
