package com.example.springqa.Q10_AopPrinciple;

public class Q10BusinessService {
    public String doBusiness() {
        System.out.println("    🎯 Q10BusinessService.doBusiness()");
        return "OK";
    }

    public String anotherMethod() {
        System.out.println("    Q10BusinessService.anotherMethod()（不匹配）");
        return "OK";
    }
}
