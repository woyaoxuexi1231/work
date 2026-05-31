package com.example.springqa.Q05_Scope;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.stereotype.Component;

@Component
public class Q05MixedService {

    private final ObjectProvider<Q05PrototypeService> provider;

    public Q05MixedService(ObjectProvider<Q05PrototypeService> provider) {
        this.provider = provider;
    }

    @Lookup
    public Q05PrototypeService getPrototypeByLookup() {
        return null; // CGLIB 子类覆盖
    }

    public Q05PrototypeService getPrototypeByProvider() {
        return provider.getObject();
    }
}
