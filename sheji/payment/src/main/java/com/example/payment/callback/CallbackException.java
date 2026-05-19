package com.example.payment.callback;

/**
 * 回调业务异常 —— 抛出此异常会终止责任链。
 */
public class CallbackException extends RuntimeException {
    public CallbackException(String message) {
        super(message);
    }
}
