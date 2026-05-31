package com.example.springqa.Q04_FactoryBean.jdk1;

public interface DataService {
    String fetchData(int id);      // 模拟延迟（比如 Thread.sleep）
    void saveData(String data);
}