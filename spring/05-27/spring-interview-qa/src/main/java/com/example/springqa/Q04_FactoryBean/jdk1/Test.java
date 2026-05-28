package com.example.springqa.Q04_FactoryBean.jdk1;

/**
 * @author hulei
 * @since 2026/5/28 18:54
 */

public class Test {

    public static void main(String[] args) {
        TimingProxyFactory timingProxyFactory =new TimingProxyFactory();
        DataService proxy = timingProxyFactory.getProxy();
        String s = proxy.fetchData(1);
        System.out.println(s);
    }
}
