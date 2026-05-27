# 聚合支付模块 — 架构与设计解析

## 项目概览

这是一个**学习型项目**，通过一个"聚合支付"业务场景，展示了两个经典设计模式在 Spring Boot 中的落地实现：

| 设计模式 | 解决的问题 | 业务场景 |
|---------|-----------|---------|
| **策略模式** (Strategy) | 多支付渠道的统一路由与扩展 | 支付请求：支付宝 / 微信 / 银联 |
| **责任链模式** (Chain of Responsibility) | 回调通知的有序处理与灵活编排 | 支付回调：验签 → 幂等 → 金额 → 状态 → 风控 → 通知 → 日志 |

技术栈：Spring Boot 3.2 + Java 8 + Maven

---

## 目录结构

```
sheji/
└── payment/                          # 聚合支付模块
    ├── pom.xml                       # Spring Boot 3.2 父POM + spring-boot-starter-web
    └── src/main/
        ├── java/com/example/payment/
        │   ├── PaymentApplication.java          # 启动类，启动时打印已注册的渠道和链
        │   │
        │   ├── strategy/                        # ===== 策略模式 =====
        │   │   ├── PaymentStrategy.java         #    策略接口（channel + pay + query）
        │   │   ├── PaymentStrategyRegistry.java #    策略注册表（核心：自动注册 + O(1)路由）
        │   │   └── impl/
        │   │       ├── AlipayStrategy.java      #    支付宝策略实现
        │   │       ├── WechatStrategy.java      #    微信支付策略实现
        │   │       └── UnionPayStrategy.java    #    银联支付策略实现
        │   │
        │   ├── callback/                        # ===== 责任链模式 =====
        │   │   ├── CallbackHandler.java         #    处理器接口（@FunctionalInterface）
        │   │   ├── AbstractCallbackHandler.java #    抽象处理器（skip/fail 快捷方法）
        │   │   ├── CallbackChainExecutor.java   #    链执行器（核心：遍历 + 异常分流）
        │   │   ├── CallbackContext.java         #    回调上下文（携带数据贯穿全链）
        │   │   ├── CallbackResult.java          #    执行结果（成功/失败 + 步骤记录）
        │   │   ├── CallbackException.java       #    业务异常（抛出 → 终止链）
        │   │   ├── SkipException.java           #    跳过异常（抛出 → 跳过当前节点，链继续）
        │   │   └── handler/
        │   │       ├── SignatureVerifyHandler.java    # ① 验签
        │   │       ├── IdempotencyCheckHandler.java   # ② 幂等校验
        │   │       ├── AmountVerifyHandler.java       # ③ 金额校验
        │   │       ├── StatusTransitionHandler.java   # ④ 状态流转
        │   │       ├── RiskReportHandler.java         # ⑤ 风控上报
        │   │       ├── NotifyMerchantHandler.java     # ⑥ 通知商户
        │   │       └── LogPersistenceHandler.java     # ⑦ 日志持久化
        │   │
        │   ├── controller/
        │   │   ├── PaymentController.java       # 支付API（渠道列表 + 发起支付 + 查询）
        │   │   └── CallbackController.java      # 回调API（查看链结构 + 模拟回调通知）
        │   │
        │   └── model/
        │       ├── PaymentRequest.java          # 支付请求 DTO
        │       └── ApiResult.java               # 统一响应体
        │
        └── resources/
            ├── application.yml                  # 端口 8080, 日志 DEBUG
            └── static/index.html                # 前端演示页面（毛玻璃 UI）
```

---

## 设计一：策略模式 — 支付渠道路由

### 1. 要解决什么问题？

一个聚合支付系统需要对接多个支付渠道（支付宝、微信、银联…）。最简单的做法是在 Controller 里写 `if/else if`：

```java
// ❌ 差的做法：每加一个渠道就要加 else-if
if ("ALIPAY".equals(channel)) {
    alipayService.pay(request);
} else if ("WECHAT".equals(channel)) {
    wechatService.pay(request);
} else if ("UNIONPAY".equals(channel)) {
    unionPayService.pay(request);
}
```

这违反了**开闭原则**（对扩展开放，对修改关闭）——每次加新渠道都要改动已有代码。

### 2. 解决方案核心思路

```
┌────────────────────────────┐
│   PaymentController        │  ← 只依赖接口 + Registry，不感知具体渠道
└─────────────┬──────────────┘
              │  registry.get(channel)   O(1) 路由
              ▼
┌────────────────────────────┐
│ PaymentStrategyRegistry    │  ← 构造时从 List → Map，自动收录所有实现
│   Map<"ALIPAY", bean>     │
│   Map<"WECHAT", bean>     │
│   Map<"UNIONPAY", bean>   │
└─────────────┬──────────────┘
              │  所有实现类都标注 @Component，Spring 自动注入到 List
              ▼
┌─────────────────────────────┐
│ <<interface>>               │
│ PaymentStrategy             │
│  + getChannel(): String     │
│  + pay(request)             │
│  + query(orderNo)           │
└──────┬───────┬───────┬──────┘
       │       │       │
  Alipay  Wechat  UnionPay   ← 新渠道只需加一个 @Component 实现类
```

### 3. 关键代码走读

**接口定义** (`PaymentStrategy.java`):

```java
public interface PaymentStrategy {
    String getChannel();                   // 渠道标识，如 "ALIPAY"
    String getDisplayName();               // 渠道中文名
    Map<String, Object> pay(PaymentRequest request);
    Map<String, Object> query(String orderNo);
}
```

**核心：策略注册表** (`PaymentStrategyRegistry.java`):

```java
@Component
public class PaymentStrategyRegistry {

    private final Map<String, PaymentStrategy> registry;

    // Spring 会把所有 PaymentStrategy 的实现自动注入到这个 List
    public PaymentStrategyRegistry(List<PaymentStrategy> strategies) {
        this.registry = strategies.stream()
                .collect(Collectors.toMap(
                        s -> s.getChannel().toUpperCase(),  // key = "ALIPAY"
                        Function.identity()
                ));
    }

    // O(1) 路由，无 if-else
    public PaymentStrategy get(String channel) {
        return registry.get(channel.toUpperCase());
    }
}
```

Spring 的 `List<PaymentStrategy>` 注入是这个设计的**关键机制**：新渠道只需写一个标注了 `@Component` 的实现类，Spring 自动把它加入这个 List，Registry 构造时自动收录到 Map。**零修改已有代码。**

**一个具体实现** (`AlipayStrategy.java`):

```java
@Component
public class AlipayStrategy implements PaymentStrategy {
    @Override public String getChannel() { return "ALIPAY"; }
    @Override public String getDisplayName() { return "支付宝"; }
    @Override
    public Map<String, Object> pay(PaymentRequest request) {
        // 模拟支付宝 API 调用
        result.put("tradeNo", "ALI" + System.currentTimeMillis());
        result.put("qrCode", "https://qr.alipay.com/" + request.getOrderNo());
        return result;
    }
    // query() 同理
}
```

### 4. 这个设计的价值

| 能力 | 实现方式 |
|------|---------|
| 新渠道接入 | 新建类 `implements PaymentStrategy` + `@Component`，零改已有代码 |
| 路由 | `Map.get(channel)` → O(1)，不遍历不 if-else |
| 渠道发现 | Registry 启动时打印 `"已注册渠道: [ALIPAY, WECHAT, UNIONPAY]"` |
| 前端联动 | 渠道列表 API 从 Registry.all() 动态获取，前端下拉框自动同步 |

---

## 设计二：责任链模式 — 回调通知处理

### 1. 要解决什么问题？

支付渠道回调（付款成功通知）到达后，需执行一系列步骤：验签 → 幂等校验 → 金额校验 → 状态更新 → 风控上报 → 商户通知 → 日志记录。如果全写在一个方法里，会是一个几百行的"上帝方法"，难以测试、难以调整步骤顺序、难以按需增删步骤。

### 2. 解决方案核心思路

```
回调请求 → CallbackChainExecutor（遍历 handler 列表）
                │
                ├─ ① SignatureVerifyHandler   (验签)       → 签名非法则终止链
                ├─ ② IdempotencyCheckHandler  (幂等校验)    → 已处理则跳过继续
                ├─ ③ AmountVerifyHandler      (金额校验)    → 金额异常则终止链
                ├─ ④ StatusTransitionHandler  (状态流转)    → 状态异常则终止或跳过
                ├─ ⑤ RiskReportHandler        (风控上报)    → 低风险跳过，黑名单终止
                ├─ ⑥ NotifyMerchantHandler    (通知商户)    → 通知失败则终止链
                └─ ⑦ LogPersistenceHandler    (日志持久化)   → 永不失败（审计需要）
                │
                ▼
          CallbackResult（含每步的状态、耗时）
```

### 3. 异常控制的三个分支（关键设计）

这是这个责任链最精妙的地方——**用异常类型控制链的走向**：

```
handler.handle(ctx)
    │
    ├── 正常执行 return ──────→ 记录 "PASSED"，继续下一个 handler
    │
    ├── 抛出 SkipException ───→ 记录 "SKIPPED"，继续下一个 handler
    │                           （用于：幂等跳过、低风险跳风控、终态跳流转）
    │
    └── 抛出 CallbackException ──→ 记录 "FAILED"，终止链，返回失败
        （或其他 Exception）
```

三种异常的分工：

| 异常 | 含义 | 使用场景 |
|------|------|---------|
| `SkipException` | 当前步骤跳过，链**继续** | 幂等命中、低风险订单不需要风控、订单已是终态无需流转 |
| `CallbackException` | 业务失败，链**终止** | 签名无效、金额异常、风控黑名单、商户通知失败 |
| 其他 `Exception` | 未知错误，链**终止** | 未预料的运行时错误 |

### 4. 关键代码走读

**处理器接口** (`CallbackHandler.java`):

```java
@FunctionalInterface
public interface CallbackHandler {
    void handle(CallbackContext ctx);
}
```

**抽象基类** (`AbstractCallbackHandler.java`) — 提供 `skip()` / `fail()` 快捷方法：

```java
public abstract class AbstractCallbackHandler implements CallbackHandler {
    @Override
    public void handle(CallbackContext ctx) {
        doHandle(ctx);  // 模板方法：子类只实现 doHandle
    }

    protected abstract void doHandle(CallbackContext ctx);

    protected void skip(String reason) { throw new SkipException(reason); } // 跳过 → 链继续
    protected void fail(String reason) { throw new CallbackException(reason); } // 失败 → 链终止
}
```

子类只需实现 `doHandle()`，在需要时调用 `skip("原因")` 或 `fail("原因")`，非常简洁：

```java
@Component @Order(2)
public class IdempotencyCheckHandler extends AbstractCallbackHandler {
    @Override
    protected void doHandle(CallbackContext ctx) {
        String key = ctx.getChannel() + ":" + ctx.getOrderNo();
        if (ctx.isProcessed(key)) {
            skip("订单已处理过，幂等跳过");  // ← 链继续，后面的步骤照样执行
        }
        ctx.markProcessed(key);
    }
}
```

**核心：链执行器** (`CallbackChainExecutor.java`) — 每渠道独立链：

```java
@Component
public class CallbackChainExecutor {

    // 改造后: Map<渠道, 该渠道的责任链>
    private final Map<String, List<CallbackHandler>> chains;

    // 注入所有渠道策略 + ApplicationContext，按 bean name 查找 Handler
    public CallbackChainExecutor(
            List<CallbackChannelStrategy> channelStrategies,
            ApplicationContext ctx) {

        // 遍历每个渠道策略，按 getHandlerNames() 查 bean，构建该渠道的链
        for (CallbackChannelStrategy strategy : channelStrategies) {
            List<CallbackHandler> chain = strategy.getHandlerNames().stream()
                .map(name -> ctx.getBean(name, CallbackHandler.class))
                .toList();
            chains.put(strategy.getChannel(), chain);
        }
    }

    // 执行指定渠道的责任链
    public CallbackResult execute(String channel, CallbackContext ctx) {
        List<CallbackHandler> handlers = chains.get(channel.toUpperCase());
        for (CallbackHandler handler : handlers) {
            // ... 异常分流逻辑不变
        }
    }
}
```

### 5. 七个 Handler 一览

Handler 本身不再带有顺序信息（去掉了 @Order），顺序由各渠道的 `getHandlerNames()` 决定：

| Handler | Bean Name | 核心逻辑 | skip 场景 | fail 场景 |
|---------|-----------|---------|-----------|-----------|
| SignatureVerifyHandler | signatureVerify | 委托给渠道策略验签 | — | 签名缺失 / 签名无效 |
| IdempotencyCheckHandler | idempotencyCheck | 幂等防重 | 已处理过 | — |
| AmountVerifyHandler | amountVerify | 金额校验 | — | 金额缺失 / 金额异常 |
| StatusTransitionHandler | statusTransition | 状态机流转 | 已是终态 | 状态异常无法流转 |
| RiskReportHandler | riskReport | 风控上报 | 低风险订单 | 黑名单订单 |
| NotifyMerchantHandler | notifyMerchant | 通知商户 | 未配置通知地址 | 通知失败 |
| LogPersistenceHandler | logPersistence | 日志持久化 | — | (永不失败) |

### 6. 链的编排方式 — 每渠道独立配置

不再使用全局 @Order。每个渠道的 `CallbackChannelStrategy.getHandlerNames()` 直接声明自己的链：

```java
// 支付宝: 完整 7 步
public List<String> getHandlerNames() {
    return List.of("signatureVerify", "idempotencyCheck", "amountVerify",
                   "statusTransition", "riskReport", "notifyMerchant", "logPersistence");
}

// 微信: 6 步（无风控上报）
public List<String> getHandlerNames() {
    return List.of("signatureVerify", "idempotencyCheck", "amountVerify",
                   "statusTransition", "notifyMerchant", "logPersistence");
}

// 银联: 5 步（无幂等 + 无风控）
public List<String> getHandlerNames() {
    return List.of("signatureVerify", "amountVerify",
                   "statusTransition", "notifyMerchant", "logPersistence");
}
```

增删调序只需修改对应的 `getHandlerNames()` 返回值，Handler 和执行器代码完全不动。

---

## 设计三：渠道差异化的责任链 + 每渠道独立链配置

### 要解决什么问题？

改造前的问题：
1. 责任链是全局单例，所有渠道共用同一条链，无法按渠道增减步骤
2. Handler 内部不做渠道差异化（比如支付宝 RSA 验签 vs 微信 MD5 验签全写死）

### 解决方案

引入 `CallbackChannelStrategy` 接口，一个渠道一个实现，承担两个职责：

**职责一：渠道差异化处理逻辑**

```java
public interface CallbackChannelStrategy {
    String getChannel();
    boolean verifySignature(Map<String, String> params);   // 各渠道验签不同
    Map<String, Object> parseCallback(Map<String, String> params); // 数据解析不同
    List<String> getHandlerNames();                         // 该渠道的链配置
}
```

`SignatureVerifyHandler` 改造为注入 `CallbackChannelRegistry`，验签逻辑委托给渠道策略：

```java
@Component("signatureVerify")
public class SignatureVerifyHandler extends AbstractCallbackHandler {
    private final CallbackChannelRegistry channelRegistry;

    @Override
    protected void doHandle(CallbackContext ctx) {
        CallbackChannelStrategy strategy = channelRegistry.get(ctx.getChannel());
        if (!strategy.verifySignature(ctx.getRawParams())) {
            fail("签名验证失败（" + ctx.getChannel() + "）");
        }
    }
}
```

**职责二：每渠道独立链配置**

通过 `getHandlerNames()` 返回该渠道专属的 Handler bean name 列表，`CallbackChainExecutor` 启动时为每个渠道构建独立的链。

新渠道接入只需新建一个 `@Component` 实现 `CallbackChannelStrategy`：
- 链自动生成（按 getHandlerNames() 配置）
- 差异化逻辑自动生效（Handler 通过 Registry 查策略）
- **零改已有代码**

## 多种模式的联动

```
用户请求
    │
    ├─ 支付场景 ──→ PaymentController
    │                   │
    │                   ▼
    │           PaymentStrategyRegistry   ← 策略模式（选渠道）
    │                   │
    │           Alipay / Wechat / UnionPay
    │                   │
    │                   ▼  发起支付 → 第三方渠道
    │
    └─ 回调场景     第三方渠道通知 ←──────────────────┘
                        │
                        ▼
                CallbackController
                        │
                        ▼
                CallbackChainExecutor       ← 责任链模式（管流程）
                   │          │
                   ▼          ▼
          CallbackChannelRegistry    chains Map<渠道, List<Handler>>
                   │
          AlipayCallbackStrategy    → 链: ①②③④⑤⑥⑦
          WechatCallbackStrategy    → 链: ①②③④⑥⑦
          UnionPayCallbackStrategy  → 链: ①③④⑥⑦
```

四种模式各司其职：
- **策略模式**负责"选哪个渠道发起支付"
- **责任链模式**负责"回调来了按什么流程处理"
- **策略模式（回调层）**负责"同一流程节点，不同渠道怎么实现"
- **链配置**负责"不同渠道走哪些节点、按什么顺序"

---

## 上下文对象设计 (CallbackContext)

`CallbackContext` 是贯穿责任链的数据载体，设计上值得注意的点：

1. **不可变输入 + 可变属性**：原始回调参数 (`rawParams`) 不可变，属性 (`attributes`) 使用 `ConcurrentHashMap` 允许 Handler 之间传递中间结果（如 `signVerified`、`verifiedAmount`、`newStatus`）
2. **步骤跟踪**：`steps` 列表记录每个 Handler 的执行状态和耗时，最终返回给调用方，方便排查问题
3. **幂等模拟**：通过内存 `Set` 模拟已处理订单标记（生产环境应该用 Redis/DB）

---

## 前端演示页

`index.html` 是一个"毛玻璃风格"的可视化演示页，通过 API 调用展示：

- **左侧**：已注册渠道列表 + 模拟支付（策略模式演示）
- **右侧**：7 步责任链可视化 + 模拟回调（责任链模式演示），每步执行后实时显示 PASSED / SKIPPED / FAILED 状态和耗时

可以通过调整 URL 参数（如 `sign=INVALID`、`riskLevel=BLACK`）观察链的不同走向。

---

## 关键学习点总结

| 层级 | 学到什么 |
|------|---------|
| **Spring 注入机制** | `List<Interface>` 注入所有实现类，是策略模式自动注册的基石 |
| **开闭原则** | 新渠道 = 新 @Component 类，零改已有代码 |
| **异常驱动流程控制** | 三种异常控制责任链的"继续 / 跳过 / 终止"三态 |
| **模板方法模式** | `AbstractCallbackHandler.handle()` 是模板，`doHandle()` 留给子类 |
| **每渠道独立链配置** | `getHandlerNames()` 声明链步骤 + ApplicationContext 按名查 bean |
| **策略嵌套责任链** | `CallbackChannelStrategy` 同时承担差异化逻辑 + 链配置，两种模式正交组合 |
| **不可变上下文** | `CallbackContext` 作为贯穿对象，输入不可变、属性可变 |
| **统一返回体** | `ApiResult` / `CallbackResult` 封装成功/失败信息，前端统一消费 |

---

> 运行方式：在 `payment/` 目录下执行 `mvn spring-boot:run`，然后浏览器打开 `http://localhost:8080` 即可体验前端演示页。
