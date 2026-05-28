package com.example.springqa.Q23_StartupFlow;

import org.springframework.stereotype.Component;

/**
 * <h1>Q23：启动流程 — SpringApplication.run() 内部核心工作</h1>
 */
@Component
public class StartupFlowDemo {

    public String runDemo() {
        return "=== Q23: 启动流程 ===\n\n" +
            "SpringApplication.run() 8 个阶段:\n\n" +
            "① 创建 SpringApplication 实例\n" +
            "  ├── 推断应用类型 (SERVLET / REACTIVE / NONE)\n" +
            "  └── 加载 Initializers 和 Listeners (从 spring.factories)\n\n" +
            "② 准备 Environment\n" +
            "  ├── 创建 ApplicationEnvironment\n" +
            "  └── 加载配置源（命令行/环境变量/yml...）\n\n" +
            "③ 创建 ApplicationContext\n" +
            "  └── SERVLET → AnnotationConfigServletWebServerApplicationContext\n\n" +
            "④ 准备 ApplicationContext\n" +
            "  └── 执行 Initializer，注册主配置类\n\n" +
            "⑤ 加载 BeanDefinition\n" +
            "  └── ConfigurationClassParser 解析 @Configuration\n\n" +
            "⑥ 刷新 ApplicationContext ★核心\n" +
            "  ├── invokeBeanFactoryPostProcessors()  → 解析 @Configuration\n" +
            "  ├── registerBeanPostProcessors()       → 注册 BPP\n" +
            "  ├── onRefresh()                        → 创建内嵌 Tomcat\n" +
            "  ├── finishBeanFactoryInitialization()  → 实例化所有单例 Bean\n" +
            "  └── finishRefresh()                    → 启动 WebServer\n\n" +
            "⑦ 回调 Runner\n" +
            "  ├── ApplicationRunner.run(args)\n" +
            "  └── CommandLineRunner.run(args)\n\n" +
            "⑧ 发布 StartedEvent / ReadyEvent\n\n" +
            "【为什么这样设计？】\n" +
            "模板方法模式——SpringApplication 定义骨架，每个步骤都是扩展点。\n" +
            "Initializer / Listener / Runner 让启动过程高度可定制。\n";
    }
}
