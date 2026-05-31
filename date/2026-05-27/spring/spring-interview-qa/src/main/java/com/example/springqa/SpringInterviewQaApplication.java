package com.example.springqa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Spring 面试 24 问 — 代码答题项目
 *
 * <p>每个包对应一个面试题，包含可运行的 Demo 代码和详细注释，
 * 解释 Spring 为什么这样设计。</p>
 *
 * <h3>包索引</h3>
 * <pre>
 * Q01_BeanLifecycle          — Bean 生命周期（BeanFactory vs ApplicationContext）
 * Q02_CircularDependency     — 循环依赖 + 三级缓存
 * Q03_DependencyInjection    — @Autowired vs @Resource + 构造器注入
 * Q04_FactoryBean            — FactoryBean vs BeanFactory
 * Q05_Scope                  — 作用域 + @Lookup / ObjectFactory
 * Q06_BeanOverride           — Bean 覆盖与冲突
 * Q07_ProxySelection         — JDK 动态代理 vs CGLIB
 * Q08_InternalCallFailure    — 内部调用失效 + 三种解法
 * Q09_InterceptorChain       — 拦截链 + @Order
 * Q10_AopPrinciple           — Advisor / Advice / Pointcut 关系
 * Q11_CustomAop              — 自研简化版 AOP 引擎
 * Q12_TransactionPropagation — REQUIRED / REQUIRES_NEW / NESTED
 * Q13_TransactionFailure     — @Transactional 失效 5+ 场景
 * Q14_TransactionPrinciple   — TransactionSynchronization + afterCommit
 * Q15_DistributedTransaction — TCC + 本地消息表
 * Q16_MvcRequestFlow         — DispatcherServlet 完整流程
 * Q17_ParameterBinding       — HttpMessageConverter 机制
 * Q18_InterceptorFilter      — HandlerInterceptor vs Filter
 * Q19_ExceptionHandling      — @ControllerAdvice + ResponseBodyAdvice
 * Q20_AsyncRequest           — DeferredResult / Callable / SSE
 * Q21_AutoConfiguration      — spring.factories + 条件注解
 * Q22_CustomStarter          — autoconfigure + starter 双模块
 * Q23_StartupFlow            — SpringApplication.run() 流程
 * Q24_ConfigPriority         — 外部化配置优先级 + 多环境
 * </pre>
 *
 * <h3>运行方式</h3>
 * <pre>
 * mvn spring-boot:run
 * # 或运行 main 方法后访问 http://localhost:8080
 * </pre>
 *
 * @author Spring Interview QA
 */
@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy = true)   // 全局启用 AOP + 暴露代理（Q07-Q11 需要）
@EnableTransactionManagement                   // 全局启用声明式事务（Q12-Q14 需要）
// 自动扫描所有 Q* 包中的 @Component / @Configuration ——
// 每个包是 Spring Boot 环境下的独立模块，通过 REST 端点触发演示。
public class SpringInterviewQaApplication {

    public static void main(String[] args) {
        /*
         * 【设计意图】
         * 为什么只是一个 main 方法 + 注解就能启动整个 Spring 容器？
         *
         * @SpringBootApplication 背后做了三件事：
         * 1. @SpringBootConfiguration — 标记为配置类（可以定义 @Bean）
         * 2. @EnableAutoConfiguration  — 触发自动装配（Q21 详解）
         * 3. @ComponentScan            — 扫描当前包及子包的所有组件
         *
         * SpringApplication.run() 内部做了什么？（Q23 详解）
         * 1. 推断应用类型（SERVLET / REACTIVE / NONE）
         * 2. 加载 spring.factories 中的 ApplicationContextInitializer
         * 3. 加载 spring.factories 中的 ApplicationListener
         * 4. 创建 ApplicationContext（默认 AnnotationConfigServletWebServerApplicationContext）
         * 5. 准备 Environment（加载配置源）
         * 6. 刷新上下文（这是 Spring IOC 的核心 — 实例化所有单例 Bean）
         * 7. 启动内嵌 Tomcat
         * 8. 回调 CommandLineRunner / ApplicationRunner
         *
         * Spring 这样设计是为了"约定优于配置"——
         * 99% 的应用都不需要自定义启动流程，一行 main 方法就够。
         */
        SpringApplication.run(SpringInterviewQaApplication.class, args);
    }
}
