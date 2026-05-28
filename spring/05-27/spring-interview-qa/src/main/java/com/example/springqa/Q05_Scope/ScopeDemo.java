package com.example.springqa.Q05_Scope;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * <h1>Q5：作用域 — singleton / prototype / request / session</h1>
 *
 * <h2>核心要点</h2>
 * <ul>
 *   <li>singleton：容器中只有一个实例，启动时创建</li>
 *   <li>prototype：每次 getBean() 创建新实例，Spring 不管理销毁</li>
 *   <li>singleton 里注入 prototype → 只注入一次！后续都返回同一个</li>
 * </ul>
 *
 * <h2>三种解决方案</h2>
 * <ol>
 *   <li>@Lookup — Spring 通过 CGLIB 生成子类重写方法</li>
 *   <li>ObjectProvider — 注入工厂，需要时 getObject()</li>
 *   <li>ApplicationContext.getBean() — 直接获取（耦合容器）</li>
 * </ol>
 */
@Component
public class ScopeDemo {

    private final ApplicationContext ctx;
    private final MixedService mixed;

    public ScopeDemo(ApplicationContext ctx, MixedService mixed) {
        this.ctx = ctx;
        this.mixed = mixed;
    }

    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Q05: 作用域 ===\n\n");

        // singleton
        SingletonService s1 = ctx.getBean(SingletonService.class);
        SingletonService s2 = ctx.getBean(SingletonService.class);
        sb.append("singleton: s1 == s2 ? ").append(s1 == s2).append("\n");

        // prototype
        PrototypeService p1 = ctx.getBean(PrototypeService.class);
        PrototypeService p2 = ctx.getBean(PrototypeService.class);
        sb.append("prototype: p1 == p2 ? ").append(p1 == p2).append("\n\n");

        // @Lookup
        PrototypeService lp1 = mixed.getPrototypeByLookup();
        PrototypeService lp2 = mixed.getPrototypeByLookup();
        sb.append("@Lookup:      lp1 == lp2 ? ").append(lp1 == lp2).append("\n");

        // ObjectProvider
        PrototypeService op1 = mixed.getPrototypeByProvider();
        PrototypeService op2 = mixed.getPrototypeByProvider();
        sb.append("ObjectProvider: op1 == op2 ? ").append(op1 == op2).append("\n\n");

        sb.append("【singleton 注入 prototype 的陷阱】\n");
        sb.append("singleton Bean 只创建一次 → 依赖注入也只执行一次 →\n");
        sb.append("prototype Bean 在注入时创建一次后，singleton 一直持有同一个引用。\n\n");

        sb.append("【为什么 prototype 不管理销毁？】\n");
        sb.append("Spring 是\"容器\"不是\"垃圾回收器\"。如果管理 prototype 的销毁，\n");
        sb.append("需要持有每个实例的引用 → 内存泄漏。谁创建谁负责销毁。\n");

        return sb.toString();
    }

    // ================================================================
    @Component
    static class SingletonService {
        public SingletonService() { System.out.println("  SingletonService 创建"); }
    }

    @Component
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    static class PrototypeService {
        public PrototypeService() { System.out.println("  PrototypeService 创建"); }
    }

    @Component
    static class MixedService {

        private final ObjectProvider<PrototypeService> provider;

        public MixedService(ObjectProvider<PrototypeService> provider) {
            this.provider = provider;
        }

        @Lookup
        public PrototypeService getPrototypeByLookup() {
            return null; // CGLIB 子类覆盖
        }

        public PrototypeService getPrototypeByProvider() {
            return provider.getObject();
        }
    }
}
