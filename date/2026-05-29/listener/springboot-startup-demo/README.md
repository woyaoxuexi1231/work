# Spring Boot 启动后执行代码的多种方案

## 项目说明

本演示项目通过 7 种不同方式，在 Spring Boot 应用启动（就绪）时执行自定义代码。运行时日志会按实际执行顺序打印，直观对比各方案的时机差异。

## 快速启动

```bash
cd springboot-startup-demo
mvn spring-boot:run
```

## 项目结构

```
src/main/java/com/example/startupdemo/
├── StartupDemoApplication.java          # 启动类
├── config/
│   └── StartupConfig.java               # @Bean(initMethod) + WebServerInitializedEvent
├── event/
│   └── ContextEventListener.java        # @EventListener 监听 ApplicationReadyEvent
├── init/
│   ├── MyInitializingBean.java          # InitializingBean 接口
│   ├── PostConstructBean.java           # @PostConstruct 注解
│   └── SmartSingleton.java              # SmartInitializingSingleton 接口
├── runner/
│   ├── AppStartupRunner.java            # ApplicationRunner 接口
│   └── CmdLineRunner.java               # CommandLineRunner 接口
└── web/
    └── HelloController.java             # 简单的 REST 控制器
```

## 7 种方案对比

| 方案 | 执行时机 | 依赖 Spring Boot | 参数访问 | 适用场景 | 核心理由 |
|------|----------|------------------|----------|----------|----------|
| **ApplicationRunner** | `run()` 返回前，Context 已刷新 | ✅ 是 | `ApplicationArguments`（支持选项解析） | 需解析 `--key=val` 启动参数 | 最正统的 Boot 方案，参数模型丰富 |
| **CommandLineRunner** | 同上 | ✅ 是 | `String[]`（原始参数） | 只需原始参数、轻量逻辑 | 签名最简单，适合快速启动脚本 |
| **@EventListener(ApplicationReadyEvent)** | 内嵌 Web 服务器已启动，应用真正就绪 | ✅ 是 | 无（可注入其他 Bean） | 需要 Web 环境已就绪（如健康检查注册） | 最晚执行时机，Web 服务器保证已上线 |
| **@EventListener(ContextRefreshedEvent)** | Context 刷新后（早于 Web 就绪） | ❌ 否 | 无 | 普通 Spring 项目迁移 | 不依赖 Spring Boot，纯 Spring 可用 |
| **@PostConstruct** | Bean 初始化阶段、DI 完成后立即触发 | ❌ 否 | 无 | 轻量初始化（无运行时依赖） | 写法最轻量，但执行太早容易踩坑 |
| **InitializingBean** | 同上，略晚于 @PostConstruct | ❌ 否 | 无 | 需要 Spring 原生接口（不推荐） | 耦合 Spring API，建议用 @PostConstruct 替代 |
| **SmartInitializingSingleton** | 全部单例 Bean 实例化后 | ❌ 否 | 无 | "所有 Bean 就绪后再初始化" | 比 @PostConstruct 晚，但早于 Web 启动 |
| **@Bean(initMethod)** | 同 @PostConstruct | ❌ 否 | 无 | XML 配置迁移场景 | 不侵入代码，纯配置方式 |
| **WebServerInitializedEvent** | Web 服务器刚启动时 | ✅ 是 | 事件提供端口等 | 需要获取 Web 服务器端口/地址 | 比 ApplicationReadyEvent 更早、更具体 |

重点面试回答：

**推荐的选择：**

1. 需要启动参数 → `ApplicationRunner`
2. 只需做点事，不需要参数 → `CommandLineRunner`
3. 必须等 Web 服务器就绪 → `@EventListener(ApplicationReadyEvent)`
4. 普通 Spring 项目（无 Boot） → `@EventListener(ContextRefreshedEvent)`

**常见踩坑：**

- ❌ 用 `@PostConstruct` 做"启动后"的事情 — 它比 Web 服务器启动早太多
- ❌ 用 `InitializingBean` — 已被 `@PostConstruct` 替代，增加框架耦合

## 执行时序（从早到晚）

```
1. @PostConstruct
2. InitializingBean.afterPropertiesSet()
3. @Bean(initMethod)
4. SmartInitializingSingleton.afterSingletonsInstantiated()
   ─── ApplicationContext 刷新完毕 ───
5. @EventListener(ContextRefreshedEvent)
6. WebServerInitializedEvent
7. @EventListener(ApplicationReadyEvent)
8. ApplicationRunner.run()
   CommandLineRunner.run()
```

> 注：8 组内多个 Runner 之间通过 `@Order` 排序；5-8 之间时间差极小（毫秒级），但在面试中应当指出确实存在执行顺序的先后。

---

## 附：自定义 Banner

Spring Boot 启动时那个大号 ASCII 艺术字，有 4 种方式自定义。

### 方式一：`banner.txt` 文件（最常用）

把文件放在 `src/main/resources/banner.txt`，Spring Boot 自动加载。

**支持的占位符：**

| 占位符 | 说明 |
|--------|------|
| `${spring-boot.version}` | Spring Boot 版本号 |
| `${application.version}` | 应用版本（取自 MANIFEST.MF） |
| `${application.title}` | 应用标题 |
| `${application.formatted-version}` | 格式化版本号 |
| `${java.version}` | Java 版本 |
| `${AnsiColor.BRIGHT_YELLOW}` | ANSI 颜色（支持 RED/GREEN/YELLOW/BLUE/...） |
| `${AnsiBackground.*}` | ANSI 背景色 |
| `${AnsiStyle.BOLD}` | ANSI 样式（BOLD/ITALIC/...） |

示例：

```text
${AnsiColor.BRIGHT_GREEN}
   _____          _   _
  / ____|        | | (_)
 | (___     ___  | |  _   _ __
  \___ \   / _ \ | | | | | '_ \
  ____) | |  __/ | | | | | | | |
 |_____/   \___| |_| |_| |_| |_|

${AnsiColor.DEFAULT}
 :: ${application.title:Demo} :: v${application.version:?}
```

### 方式二：指定路径

```properties
# 文本 Banner
spring.banner.location=classpath:my-banner.txt

# 图片转 ASCII Banner
spring.banner.image.location=classpath:banner.png
```

图片 Banner 支持 jpg / png / gif，框架自动转成灰度 ASCII。

### 方式三：程序式 Banner（Java 代码）

```java
SpringApplication app = new SpringApplication(DemoApplication.class);
app.setBanner((environment, sourceClass, out) -> {
    out.println("  ╔══════════════════════╗");
    out.println("  ║   自定义 Banner     ║");
    out.println("  ╚══════════════════════╝");
});
app.run(args);
```

**优先级：** 程序式 > `banner.txt` > Spring Boot 默认 Banner

### 方式四：关闭 Banner

```properties
spring.main.banner-mode=off
```

或代码：`app.setBannerMode(Banner.Mode.OFF);`

### 面试回答要点

- Banner 本质是在 `SpringApplication.run()` 中、`ApplicationContext` 刷新**之前**打印的——它不属于"启动后执行"，而是"启动时装饰"
- 文件式自动化（`banner.txt`）基于 `BannerPrinter` 在 `SpringApplication` 启动流程的第 2-3 步触发
- 如果要动态 Banner（如读取数据库），用程序式方式 + 配置文件中的 `spring.banner.location` 置空
