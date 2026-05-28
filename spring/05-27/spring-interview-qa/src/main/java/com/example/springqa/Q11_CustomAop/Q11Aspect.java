package com.example.springqa.Q11_CustomAop;

import java.util.ArrayList;
import java.util.List;

class Q11Aspect {
    final String name;
    final Q11Pointcut pointcut;
    final List<Q11Advice> advices = new ArrayList<>();
    int order;

    Q11Aspect(String name, Q11Pointcut pointcut) {
        this.name = name; this.pointcut = pointcut;
    }

    Q11Aspect addAdvice(Q11Advice a) { advices.add(a); return this; }
    Q11Aspect order(int o) { this.order = o; return this; }
}
