package com.example.payment.callback;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 回调上下文 —— 携带数据贯穿整个责任链。
 */
public class CallbackContext {
    private final String channel;
    private final String orderNo;
    private final Map<String, String> rawParams;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    /** 步骤执行记录 */
    private final List<Map<String, Object>> steps = Collections.synchronizedList(new ArrayList<>());

    /** 幂等标记集合（模拟） */
    private static final Set<String> PROCESSED_ORDERS = ConcurrentHashMap.newKeySet();

    public CallbackContext(String channel, String orderNo, Map<String, String> rawParams) {
        this.channel = channel;
        this.orderNo = orderNo;
        this.rawParams = rawParams != null ? rawParams : Collections.emptyMap();
    }

    // ---- 幂等模拟 ----
    public boolean isProcessed(String key) { return PROCESSED_ORDERS.contains(key); }
    public void markProcessed(String key) { PROCESSED_ORDERS.add(key); }

    // ---- 属性存取 ----
    public void set(String key, Object value) { attributes.put(key, value); }
    @SuppressWarnings("unchecked")
    public <T> T get(String key) { return (T) attributes.get(key); }

    // ---- 步骤记录 ----
    public void addStep(String handler, String status, long costMs) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("handler", handler);
        step.put("status", status);
        step.put("costMs", costMs);
        steps.add(step);
    }
    public List<Map<String, Object>> getSteps() { return new ArrayList<>(steps); }

    // ---- getters ----
    public String getChannel() { return channel; }
    public String getOrderNo() { return orderNo; }
    public Map<String, String> getRawParams() { return rawParams; }
    public String getParam(String key) { return rawParams.get(key); }
}
