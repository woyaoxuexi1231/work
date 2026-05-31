package com.example.springqa.Q03_DependencyInjection;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class Q03Restaurant {

    private final Q03Chef chef;

    @Resource(name = "waiter")
    private Q03Waiter waiter;

    public Q03Restaurant(@Qualifier("q03_chineseChef") Q03Chef chef) {
        this.chef = chef;
        System.out.println("  Q03Restaurant 构造器注入: " + chef);
    }

    public Q03Chef getChef() { return chef; }
    public Q03Waiter getWaiter() { return waiter; }
}
