package com.example.springqa.Q04_FactoryBean.jdk0;

/**
 * @author hulei
 * @since 2026/5/28 18:48
 */

public class Test {
    public static void main(String[] args) {
        LoggingProxyFactory loggingProxyFactory = new LoggingProxyFactory();
        Calculator calculator = (Calculator) loggingProxyFactory.getProxy();
        System.out.println(calculator.add(1, 2));
    }
}
