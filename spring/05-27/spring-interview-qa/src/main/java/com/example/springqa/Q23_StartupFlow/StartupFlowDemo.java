package com.example.springqa.Q23_StartupFlow;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;

/**
 * <h1>Q23：启动流程 — SpringApplication.run() 内部核心工作</h1>
 *
 * <h2>面试点</h2>
 * <p>SpringApplication.run() 内部做了哪些核心工作？</p>
 *
 * <h2>完整启动流程（分 8 个阶段）</h2>
 *
 * <pre>
 * SpringApplication.run(SpringInterviewQaApplication.class, args)
 *   │
 *   ├── ① 创建 SpringApplication 实例
 *   │    ├── 推断应用类型（WebApplicationType.deduceFromClasspath()）
 *   │    │   ├── 有 spring-webflux → REACTIVE
 *   │    │   ├── 有 spring-webmvc → SERVLET
 *   │    │   └── 都没有 → NONE
 *   │    └── 设置 Initializers 和 Listeners
 *   │        └── SpringFactoriesLoader.loadFactoryNames(...)
 *   │           从 spring.factories 加载
 *   │           ApplicationContextInitializer 和 ApplicationListener
 *   │
 *   ├── ② 准备 Environment
 *   │    ├── 创建 ApplicationEnvironment
 *   │    ├── 加载配置源（命令行参数、系统属性、环境变量等）
 *   │    └── 触发 ApplicationEnvironmentPreparedEvent
 *   │
 *   ├── ③ 创建 ApplicationContext
 *   │    ├── SERVLET → AnnotationConfigServletWebServerApplicationContext
 *   │    ├── REACTIVE → AnnotationConfigReactiveWebServerApplicationContext
 *   │    └── NONE → AnnotationConfigApplicationContext
 *   │
 *   ├── ④ 准备 ApplicationContext
 *   │    ├── 设置 Environment
 *   │    ├── 执行 ApplicationContextInitializer
 *   │    ├── 注册启动源（主配置类）
 *   │    └── 触发 ApplicationContextInitializedEvent
 *   │
 *   ├── ⑤ 加载 BeanDefinition
 *   │    ├── ConfigurationClassParser 解析 @Configuration 类
 *   │    ├── ComponentScan 扫描 @Component 等
 *   │    └── 触发 ApplicationPreparedEvent
 *   │
 *   ├── ⑥ 刷新 ApplicationContext  ← Spring IOC 核心！
 *   │    ├── prepareRefresh()           — 准备刷新
 *   │    ├── obtainFreshBeanFactory()   — 获取 BeanFactory
 *   │    ├── prepareBeanFactory()       — 配置 BeanFactory
 *   │    │   └── 注册 BeanPostProcessor, 设置 ClassLoader 等
 *   │    ├── postProcessBeanFactory()   — BeanFactoryPostProcessor 回调
 *   │    ├── invokeBeanFactoryPostProcessors()
 *   │    │   └── ConfigurationClassPostProcessor 解析 @Configuration
 *   │    ├── registerBeanPostProcessors()
 *   │    │   └── 注册 AutowiredAnnotationBeanPostProcessor 等
 *   │    ├── initMessageSource()
 *   │    ├── initApplicationEventMulticaster()
 *   │    ├── onRefresh()
 *   │    │   └── Servlet容器 → 创建内嵌 WebServer (Tomcat/Jetty/Undertow)
 *   │    ├── registerListeners()
 *   │    ├── finishBeanFactoryInitialization()
 *   │    │   └── ★ 实例化所有非懒加载的单例 Bean ★
 *   │    │       └── getBean() 触发完整的 Bean 生命周期（Q01）
 *   │    └── finishRefresh()
 *   │        └── 启动 WebServer，发布 ContextRefreshedEvent
 *   │
 *   ├── ⑦ 回调 Runner
 *   │    ├── ApplicationRunner.run(args)  — 参数已解析
 *   │    └── CommandLineRunner.run(args)  — 原始字符串数组
 *   │
 *   └── ⑧ 发布 StartedEvent / ReadyEvent
 *        ├── ApplicationStartedEvent  — 容器已启动，Runner 执行前
 *        └── ApplicationReadyEvent   — 一切就绪，可以接收请求
 * </pre>
 *
 * <h2>Spring 为什么这样设计？</h2>
 * <p>启动流程体现了"模板方法模式"——SpringApplication 定义了启动的骨架流程，
 * 每个步骤都是扩展点（Initializer、Listener、Runner）。
 * 这种设计让 Spring Boot 的启动过程高度可定制，同时保持核心流程不变。</p>
 *
 * @author Spring Interview QA
 */
public class StartupFlowDemo {

    /**
     * 运行本 Demo: 直接执行 main 方法，观察启动日志。
     */
    public static void main(String[] args) {
        System.out.println("========== Q23: 启动流程 Demo ==========\n");

        /*
         * 启动 Spring Boot 应用，观察控制台的启动日志。
         * 你会看到：
         * - Starting SpringInterviewQaApplication...
         * - The following 1 profile is active: ...
         * - Tomcat started on port(s): 8080
         * - Started SpringInterviewQaApplication in X.XXX seconds
         *
         * 每行日志背后都对应上述启动流程的一个步骤。
         */
        SpringApplication app = new SpringApplication(StartupFlowDemo.class);

        // 添加自定义 Listener 观察启动事件
        app.addListeners((ApplicationListener<ApplicationEvent>) event -> {
            String eventType = event.getClass().getSimpleName();
            // 只打印关键事件
            if (eventType.contains("Starting") || eventType.contains("Environment")
                    || eventType.contains("Prepared") || eventType.contains("Started")
                    || eventType.contains("Ready")) {
                System.out.println("  📢 事件: " + eventType);
            }
        });

        ApplicationContext ctx = app.run(args);

        /*
         * 启动完成后，Spring 容器已经：
         * 1. 加载了所有 BeanDefinition
         * 2. 实例化了所有单例 Bean
         * 3. 启动了内嵌 Tomcat
         * 4. 执行了所有 Runner 和 Listener
         * 5. 准备就绪，可以接受 HTTP 请求
         */

        System.out.println("\n========== Demo 结束 ==========");
    }

    /**
     * ApplicationRunner 和 CommandLineRunner 的区别：
     *
     * - CommandLineRunner.run(String... args) → 原始命令行参数
     * - ApplicationRunner.run(ApplicationArguments args) → 已解析的参数
     *   (getOptionNames(), getOptionValues(), getNonOptionArgs())
     *
     * Spring 为什么设计两个 Runner？
     * CommandLineRunner 更简单（只是一个 String[]），
     * ApplicationRunner 提供更多便利方法（选项 vs 非选项参数）。
     * 两者都保留是因为向后兼容 + 不同场景的选择。
     */
    @Bean
    CommandLineRunner demoCommandLineRunner() {
        return args -> {
            System.out.println("\n  🏃 CommandLineRunner: 应用已启动！");
            System.out.println("  CommandLineRunner 参数: " + String.join(", ", args));
        };
    }

    @Bean
    ApplicationRunner demoApplicationRunner() {
        return args -> {
            System.out.println("  🏃 ApplicationRunner: 应用已启动！");
            System.out.println("  ApplicationRunner 选项: " + args.getOptionNames());
        };
    }
}
