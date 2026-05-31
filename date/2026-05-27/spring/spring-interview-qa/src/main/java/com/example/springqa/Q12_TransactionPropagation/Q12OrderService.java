package com.example.springqa.Q12_TransactionPropagation;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class Q12OrderService {

    @Transactional(propagation = Propagation.REQUIRED, transactionManager = "q12_transactionManager")
    public void requiredOuter() {
        print("requiredOuter");
        requiredInner();
    }

    @Transactional(propagation = Propagation.REQUIRED, transactionManager = "q12_transactionManager")
    public void requiredInner() {
        print("requiredInner（同一事务）");
    }

    @Transactional(propagation = Propagation.REQUIRED, transactionManager = "q12_transactionManager")
    public void requiresNewOuter() {
        print("requiresNewOuter（事务A）");
        requiresNewInner();
        print("requiresNewOuter 恢复（A仍在）");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, transactionManager = "q12_transactionManager")
    public void requiresNewInner() {
        print("requiresNewInner（独立B——A被挂起）");
    }

    @Transactional(propagation = Propagation.REQUIRED, transactionManager = "q12_transactionManager")
    public void nestedOuter() {
        print("nestedOuter（主事务）");
        try { nestedInner(); } catch (Exception ignored) {}
        print("nestedOuter（主事务仍可提交）");
    }

    @Transactional(propagation = Propagation.NESTED, transactionManager = "q12_transactionManager")
    public void nestedInner() {
        print("nestedInner（子事务）");
        throw new RuntimeException("模拟回滚到 savepoint");
    }

    private void print(String ctx) {
        System.out.println("  [" + ctx + "] 活跃="
                + TransactionSynchronizationManager.isActualTransactionActive());
    }
}
