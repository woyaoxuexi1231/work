package com.example.payment.model;

import java.math.BigDecimal;

public class PaymentRequest {
    private String channel;
    private String orderNo;
    private BigDecimal amount;
    private String subject;

    public PaymentRequest() {}

    public PaymentRequest(String channel, String orderNo, BigDecimal amount, String subject) {
        this.channel = channel;
        this.orderNo = orderNo;
        this.amount = amount;
        this.subject = subject;
    }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
}
