package com.example.payment;

import com.example.payment.callback.CallbackChainExecutor;
import com.example.payment.strategy.PaymentStrategyRegistry;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PaymentApplication implements CommandLineRunner {

    private final PaymentStrategyRegistry strategyRegistry;
    private final CallbackChainExecutor chainExecutor;

    public PaymentApplication(PaymentStrategyRegistry strategyRegistry, CallbackChainExecutor chainExecutor) {
        this.strategyRegistry = strategyRegistry;
        this.chainExecutor = chainExecutor;
    }

    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }

    @Override
    public void run(String... args) {
        System.out.println("\n========================================");
        System.out.println("  聚合支付模块 - Design Patterns Demo");
        System.out.println("========================================");
        System.out.println("已注册支付渠道: " + strategyRegistry.channels());
        System.out.println("----------------------------------------");
        for (String channel : chainExecutor.channels()) {
            System.out.println(" [" + channel + "] 回调链: " + chainExecutor.getHandlerDisplayNames(channel));
        }
        System.out.println("========================================\n");
    }
}
