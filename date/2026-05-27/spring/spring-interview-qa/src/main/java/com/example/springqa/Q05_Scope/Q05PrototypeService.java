package com.example.springqa.Q05_Scope;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class Q05PrototypeService {
    public Q05PrototypeService() {
        System.out.println("  Q05PrototypeService 创建");
    }
}
