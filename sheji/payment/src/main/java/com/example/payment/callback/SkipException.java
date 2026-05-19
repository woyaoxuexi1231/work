package com.example.payment.callback;

/**
 * 跳过异常 —— 抛出此异常表示"当前步骤跳过，责任链继续执行后续节点"。
 * 与普通异常的区别：普通异常会终止链，SkipException 不会。
 */
public class SkipException extends RuntimeException {
    public SkipException(String message) {
        super(message);
    }
}
