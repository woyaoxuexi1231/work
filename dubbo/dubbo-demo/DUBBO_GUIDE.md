# Apache Dubbo 深度解析 — 从背景到架构，从原理到手写代码

> 本文档伴随 `dubbo-demo` 示例项目，逐层剖析 Apache Dubbo 的诞生背景、核心架构、设计思想，
> 并将每一个知识点映射回项目中的具体代码，让你不仅"会用"，更能"理解为什么这么设计"。

---

## 目录

- [一、Dubbo 基于什么？——技术根基](#一dubbo-基于什么技术根基)
- [二、出现的背景是什么？——从单体到分布式的阵痛](#二出现的背景是什么从单体到分布式的阵痛)
- [三、Dubbo 要解决什么问题？——分布式服务的四大难题](#三dubbo-要解决什么问题分布式服务的四大难题)
- [四、Dubbo 采用什么架构？——五大角色 + 三层体系](#四dubbo-采用什么架构五大角色--三层体系)
- [五、为什么这么设计？——三大设计思想的深度剖析](#五为什么这么设计三大设计思想的深度剖析)
- [六、RPC 调用全链路追踪](#六rpc-调用全链路追踪)
- [七、项目代码走读 — 每个文件对应什么概念](#七项目代码走读--每个文件对应什么概念)
- [八、启动与验证](#八启动与验证)

---

## 一、Dubbo 基于什么？——技术根基

### 1.1 基石技术

Dubbo 不是凭空造轮子，它构建在以下成熟技术之上：

| 层级 | 技术 | 作用 | 在我们的 demo 中 |
|------|------|------|-----------------|
| **网络通信** | Netty (NIO) | 高性能异步网络框架，处理 TCP 字节流 | Provider 启动时绑定 20880 端口，底层即 Netty `ServerBootstrap` |
| **序列化** | Hessian2 | 将 Java 对象转换为二进制流，跨 JVM 传输 | `User` / `Order` 类实现 `Serializable`，由 Hessian2 序列化 |
| **注册中心** | Nacos | 服务地址的"电话簿"，存储 Provider 的 IP:Port，同时支持动态配置管理 | `application.yml` 中 `address: nacos://192.168.3.100:8848?username=nacos&password=nacos` |
| **动态代理** | Javassist / JDK Proxy | 为 Consumer 端的接口生成代理对象，拦截方法调用 | `@DubboReference private UserService userService` — 这个字段被注入的就是动态代理 |
| **IoC 容器** | Spring Framework | 管理 Bean 生命周期，整合 Dubbo 组件 | `@EnableDubbo` + `@Configuration` 将 Dubbo 融入 Spring 容器 |
| **SPI** | Dubbo 自定义 SPI | 微内核 + 插件化，所有组件可替换 | `META-INF/dubbo/org.apache.dubbo.rpc.Filter` 文件自动加载自定义 Filter |

### 1.2 依赖关系图

```
┌──────────────────────────────────────────────────────┐
│                   Dubbo Framework                     │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌─────────┐ │
│  │ 动态代理  │ │ 集群容错  │ │ 协议编解码│ │ SPI 内核 │ │
│  └──────────┘ └──────────┘ └──────────┘ └─────────┘ │
├──────────────────────────────────────────────────────┤
│  Netty (网络传输)     Hessian2 (序列化)                 │
├──────────────────────────────────────────────────────┤
│  Nacos (注册中心)  Spring (IoC 容器)                    │
├──────────────────────────────────────────────────────┤
│  JDK 17+ (运行环境)                                    │
└──────────────────────────────────────────────────────┘
```

**关键理解：** Dubbo 本质上是一个 **RPC 框架**——它在 Netty 的字节传输之上，叠加了"像调用本地方法一样调用远程方法"的能力。所有复杂的集群策略、负载均衡、服务发现，最终都是为了"让远程调用对开发者透明"。

---

## 二、出现的背景是什么？——从单体到分布式的阵痛

### 2.1 单体架构时代（2008 年前后）

阿里巴巴早期（淘宝、B2B）的 Java 应用是典型的 **单体架构**：

```
┌────────────────────────────────────────────┐
│              一个 .war 包                    │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐   │
│  │ 用户模块  │ │ 商品模块  │ │ 订单模块  │   │
│  │          │ │          │ │          │   │
│  │ 本地调用  │←→│ 本地调用  │←→│ 本地调用  │   │
│  └──────────┘ └──────────┘ └──────────┘   │
│           ↓ 同一个数据库连接池 ↓              │
│  ┌──────────────────────────────────────┐  │
│  │           MySQL 单库                   │  │
│  └──────────────────────────────────────┘  │
└────────────────────────────────────────────┘
```

**问题随着规模增长而爆发：**

1. **代码耦合**：用户模块改了接口，订单模块也得重新编译打包
2. **部署耦合**：改一行用户模块代码 → 整个 war 包重新部署 → 所有模块重启
3. **扩展困难**：订单模块 CPU 打满，但只能整体扩容（加机器 = 连用户模块一起扩，浪费资源）
4. **数据库瓶颈**：所有模块共享一个数据库连接池，互相影响

### 2.2 分布式拆分运动（2009-2011）

阿里开始将单体应用 **垂直拆分** 为独立服务：

```
单体战争 → 单一职能服务
───────────────────────────────────────────
用户中心.war    商品中心.war    订单中心.war    交易中心.war
    ↓               ↓               ↓               ↓
各自独立部署、独立数据库、独立扩容
```

**但拆分后立即遇到了新问题：这些独立部署的服务之间如何通信？**

### 2.3 早期的土办法

在 Dubbo 出现之前，阿里内部各团队用各种野路子通信：

| 方式 | 做法 | 痛点 |
|------|------|------|
| **HTTP + JSON** | 手动写 HttpClient 调 REST 接口 | 没有类型安全；需要手写序列化/反序列化；没有服务发现 |
| **Hessian + 直连** | 配置对方 IP:Port，Hessian 序列化 | IP 变更 = 改配置重启；无法负载均衡；没有容错 |
| **数据库共享** | 订单模块直接读用户库 | 数据耦合，死锁频发；DBA 暴怒 |
| **消息队列** | 异步通过 ActiveMQ / RocketMQ 通信 | 异步场景可以，同步查询场景不适用 |
| **WebService (SOAP)** | XML 重量级协议 | 性能差，XML 解析开销大 |

**核心痛点：** 每家都在重复造"服务调用"的轮子——服务发现、负载均衡、容错、序列化、协议封装……这些应该是基础设施，不该每个业务团队各写一套。

### 2.4 Dubbo 的诞生

2008 年，梁飞（@梁飞）在阿里 B2B 部门开始开发 Dubbo，并于 2011 年开源。
它的定位非常清晰：

> **Dubbo 是一个高性能、轻量级的 Java RPC 框架，提供三大核心能力：**
> 1. **面向接口的远程方法调用**
> 2. **智能容错与负载均衡**
> 3. **服务自动注册与发现**

对于业务开发来说，用了 Dubbo 之后，调用远程服务和调用本地方法**几乎一模一样**：

```java
// 不需要关心：
//   - 对方在哪台机器
//   - 有几台机器
//   - 网络怎么传
//   - 失败怎么办
// 只需要像调本地方法一样：
User user = userService.getUserById(1L);
```

---

## 三、Dubbo 要解决什么问题？——分布式服务的四大难题

### 3.1 服务发现——"我该调谁？"

在没有注册中心的世界里，Consumer 需要硬编码 Provider 的地址：

```properties
# 硬编码 — 噩梦
user.service.host=192.168.1.101
user.service.port=20880
order.service.host=192.168.1.102
order.service.port=20880
```

**问题：**
- Provider 宕机了 → Consumer 不知道，继续往死机器发请求
- Provider 扩容了 → Consumer 不知道新节点，流量打不上去
- Provider 换 IP 了 → 所有 Consumer 改配置重启

**Dubbo 的解法 — 注册中心（Registry）：**

```
Provider 启动                      Consumer 启动
    │                                  │
    │ ① 注册: "我是 UserService        │ ② 订阅: "我要调 UserService
    │    地址 192.168.1.100:20880"     │    有哪些 Provider？"
    ▼                                  ▼
         ┌─────────────────────────┐
         │     Nacos 注册中心       │
         │   192.168.3.100:8848     │
         │                         │
         │  服务列表 → UserService  │
         │    实例:                  │
         │      192.168.1.100:20880 │ ← 临时实例（心跳超时自动下线）
         │      192.168.1.101:20880 │
         │                         │
         │  订阅者 → Consumer 列表   │
         │      192.168.1.200       │
         └─────────────────────────┘
                  │
                  │ ③ UDP 推送变更:
                  │    "Provider 列表变了！"
                  ▼
             Consumer 更新本地地址缓存
```

**Demo 代码对应：**

```yaml
# dubbo-demo-provider/src/main/resources/application.yml
dubbo:
  registry:
    address: nacos://192.168.3.100:8848?username=nacos&password=nacos
```

```java
// Provider: 启动时自动向 Nacos 注册
@DubboService(version = "1.0.0", group = "demo")
public class UserServiceImpl implements UserService { ... }

// Consumer: 启动时从 Nacos 订阅 Provider 地址
@DubboReference(version = "1.0.0", group = "demo")
private UserService userService;
```

### 3.2 负载均衡——"调哪个？"

当一个服务有 10 个 Provider 实例时，Consumer 的请求该发给谁？

**Dubbo 内置四种策略（在 `@DubboReference(loadbalance = "xxx")` 中配置）：**

| 策略 | 全称 | 行为 | 适用场景 |
|------|------|------|---------|
| `random` | 随机 + 权重 | 按权重随机选一个 Provider | 通用，默认策略 |
| `roundrobin` | 轮询 + 权重 | 轮流分配，权重高的多分 | 请求耗时均匀 |
| `leastactive` | 最少活跃调用数 | 选当前处理请求最少的 Provider | 请求耗时差异大 |
| `consistenthash` | 一致性哈希 | 相同参数请求固定发同一 Provider | 有状态服务、缓存亲和 |

**Demo 代码对应：**

```java
// UserController.java — UserService 使用 random，OrderService 使用 roundrobin
@DubboReference(loadbalance = "random")
private UserService userService;

@DubboReference(loadbalance = "roundrobin")
private OrderService orderService;
```

### 3.3 容错——"调失败了怎么办？"

网络是不可靠的。Provider 随时可能宕机、超时、返回异常。

**Dubbo 内置的集群容错策略：**

| 策略 | 行为 | 适用场景 |
|------|------|---------|
| `failfast` | 快速失败，立即抛异常 | 非幂等写操作 |
| `failsafe` | 吞掉异常，返回 null | 旁路日志、审计 |
| `failback` | 失败后后台定时重试 | 消息通知 |
| `forking` | 并行调多台，一个成功即返回 | 实时性要求极高 |
| `broadcast` | 逐个调用所有 Provider | 通知类操作 |

**Demo 代码对应：**

```java
@DubboService(retries = 2)  // Provider 端配置：失败后重试 2 次
public class UserServiceImpl implements UserService { ... }
```

### 3.4 协议与序列化——"数据怎么传？"

两台 JVM 之间，对象无法直接传递，必须转换成字节流。

**Dubbo 默认协议：`dubbo://`**

```
Dubbo 协议帧结构：
┌──────────────────────────────────────────────┐
│ Magic (2B) │ Flag (1B) │ Status (1B)         │ ← 协议头 16 字节
│ Request ID (8B)                              │
│ Body Length (4B)                             │
├──────────────────────────────────────────────┤
│ Body (Hessian2 序列化的 Invocation 数据)       │ ← 变长
└──────────────────────────────────────────────┘
```

**为什么用 Hessian2？**
- 二进制序列化，体积远小于 JSON
- 跨语言支持（虽然 Dubbo 主要用于 Java，但 Hessian 本身跨语言）
- 相比 Java 原生序列化：更快、更小、更安全（Java 序列化有反序列化漏洞）

---

## 四、Dubbo 采用什么架构？——五大角色 + 三层体系

### 4.1 五大角色

Dubbo 官方文档定义的五种角色，在我们这个 demo 项目中全部有对应：

```
┌─────────────┐      ┌─────────────┐
│  Container  │      │   Monitor   │
│ (Spring容器) │      │ (可选,监控)  │
└──────┬──────┘      └──────▲──────┘
       │ 启动/停止           │ 统计上报
       ▼                    │
┌─────────────┐  注册服务  ┌──┴──────────┐
│  Provider   │ ────────→ │  Registry    │
│ (服务提供者) │ ←──────── │  (注册中心)   │
└──────┬──────┘  订阅服务  └──────┬──────┘
       │                         │ 发现
       │   RPC 调用              │
       ▼                         ▼
┌─────────────┐            ┌─────────────┐
│  Consumer   │  调用统计   │   Monitor   │
│ (服务消费者) │ ─────────→ │  (监控中心)   │
└─────────────┘            └─────────────┘
```

| 角色 | 职责 | Demo 项目映射 |
|------|------|--------------|
| **Provider** | 暴露服务、实现业务逻辑、监听端口 | `dubbo-demo-provider` 模块，`UserServiceImpl`、`OrderServiceImpl` |
| **Consumer** | 调用远程服务、发起 RPC 请求 | `dubbo-demo-consumer` 模块，`UserController` 中的 `@DubboReference` |
| **Registry** | 服务注册与发现、健康检查、动态配置 | Nacos（外部服务，地址 `192.168.3.100:8848`） |
| **Monitor** | 统计调用次数、耗时（可选） | 本项目未配置（独立部署组件） |
| **Container** | 管理 Provider 的生命周期 | Spring Boot 容器（`ProviderBootstrap.main()` 启动） |

### 4.2 三层体系

Dubbo 自身的设计分为三个清晰的分层：

```
┌──────────────────────────────────────────────────┐
│  Business 层 (用户代码)                            │
│  UserService / UserServiceImpl / UserController  │
├──────────────────────────────────────────────────┤
│  RPC 层 (Dubbo 核心)                              │
│  ┌──────────┬──────────┬──────────┬───────────┐ │
│  │ Config   │ 配置层   │ ServiceConfig,        │ │
│  │          │          │ ReferenceConfig       │ │
│  ├──────────┼──────────┼──────────────────────┤ │
│  │ Proxy    │ 代理层   │ JavassistProxyFactory │ │
│  │          │          │ (生成 RPC 代理对象)     │ │
│  ├──────────┼──────────┼──────────────────────┤ │
│  │ Registry │ 注册层   │ NacosRegistry         │ │
│  │          │          │ (服务的注册/订阅)       │ │
│  ├──────────┼──────────┼──────────────────────┤ │
│  │ Cluster  │ 集群层   │ FailoverCluster       │ │
│  │          │          │ (容错 + 负载均衡)       │ │
│  ├──────────┼──────────┼──────────────────────┤ │
│  │ Protocol │ 协议层   │ DubboProtocol         │ │
│  │          │          │ (编解码 + 序列化)       │ │
│  ├──────────┼──────────┼──────────────────────┤ │
│  │ Filter   │ 过滤器链  │ ProviderLogFilter     │ │
│  │          │          │ (AOP 拦截)             │ │
│  └──────────┴──────────┴──────────────────────┘ │
├──────────────────────────────────────────────────┤
│  Transport 层                                     │
│  Netty (NIO 网络通信)                              │
├──────────────────────────────────────────────────┤
│  Serialize 层                                     │
│  Hessian2 / Kryo / Protobuf (序列化引擎)           │
└──────────────────────────────────────────────────┘
```

**为什么分三层？——分层的好处：**

1. **每一层只做一件事**：协议层不管网络怎么建连，传输层不管 RPC 怎么路由
2. **每层可独立替换**：可以把 Netty 换成 Mina，不影响上层；可以把 Hessian2 换成 Kryo，不影响下层
3. **面向 SPI 设计**：每层对外暴露 SPI 接口，实现类通过 SPI 机制加载，实现"微内核 + 插件化"

---

## 五、为什么这么设计？——三大设计思想的深度剖析

### 5.1 核心思想一：微内核 + 插件化（SPI）

这是 Dubbo 架构的灵魂。

**传统框架的做法：**

```java
// 传统做法：硬编码依赖
public class RpcFramework {
    private NettyServer server = new NettyServer();      // 写死 Netty
    private HessianSerializer serializer = new HessianSerializer(); // 写死 Hessian
    private RandomLoadBalance loadBalance = new RandomLoadBalance(); // 写死随机策略
    // 想换？改代码 + 重新编译 + 发布
}
```

**Dubbo 的做法 — SPI（Service Provider Interface）：**

```
微内核 (ExtensionLoader)
    │
    │  扫描 META-INF/dubbo/ 目录下的扩展点配置文件
    │
    ├── org.apache.dubbo.rpc.Protocol
    │     dubbo=org.apache.dubbo.rpc.protocol.dubbo.DubboProtocol
    │     rest=org.apache.dubbo.rpc.protocol.rest.RestProtocol
    │     tri=org.apache.dubbo.rpc.protocol.tri.TripleProtocol
    │
    ├── org.apache.dubbo.rpc.Filter
    │     cache=org.apache.dubbo.rpc.filter.CacheFilter
    │     token=org.apache.dubbo.rpc.filter.TokenFilter
    │     providerLogFilter=com.example...ProviderLogFilter  ← 我们的自定义 Filter!
    │
    ├── org.apache.dubbo.registry.RegistryFactory
    │     nacos=org.apache.dubbo.registry.nacos.NacosRegistryFactory   ← 本项目使用
    │     zookeeper=org.apache.dubbo.registry.zookeeper.ZookeeperRegistryFactory
    │
    └── org.apache.dubbo.rpc.cluster.LoadBalance
          random=org.apache.dubbo.rpc.cluster.loadbalance.RandomLoadBalance
          roundrobin=org.apache.dubbo.rpc.cluster.loadbalance.RoundRobinLoadBalance
```

**关键源码逻辑（简化）：**

```java
// Dubbo 的 ExtensionLoader 核心逻辑（简化版）
public class ExtensionLoader<T> {

    // 1. 扫描 META-INF/dubbo/ 下的配置文件
    private Map<String, Class<?>> loadExtensionClasses() {
        // 读取文件中的每一行: filterName=com.xxx.XXXFilter
        // 缓存到 cachedClasses 中
    }

    // 2. 根据名称获取扩展点实例
    public T getExtension(String name) {
        // 从 cachedClasses 中找到对应 Class → 反射实例化 → 返回
    }

    // 3. 自适应扩展 — 运行时动态决定使用哪个实现
    public T getAdaptiveExtension() {
        // 运行时从 URL 参数中读取协议名称
        // url.getParameter("protocol", "dubbo")
        // → 根据名称动态选择对应的 Protocol 实现
    }
}
```

**Demo 代码对应：**

```java
// ProviderLogFilter.java — 通过 SPI 自动加载
@Activate(group = CommonConstants.PROVIDER, order = 100)
public class ProviderLogFilter implements Filter { ... }

// META-INF/dubbo/org.apache.dubbo.rpc.Filter — SPI 配置文件
// providerLogFilter=com.example.dubbo.demo.provider.filter.ProviderLogFilter
```

**这个设计的威力：** 你想把序列化从 Hessian2 换成 Kryo？改一行配置——`dubbo.protocol.serialization=kryo`——就完成了，不用改一行 Java 代码，不用重新编译 Dubbo。每一个网络协议、每一个注册中心、每一个负载均衡策略、每一个 Filter……全部通过 SPI 插拔。

### 5.2 核心思想二：代理模式 — 让远程调用"透明"

这是 Dubbo 对开发者最友好的设计。

**没有代理模式的世界（开发者需要手写的代码）：**

```java
// 噩梦：每次调用远程服务都要写这些
public User getUserById(Long id) {
    // 1. 从注册中心获取 Provider 地址列表
    List<String> providers = zkClient.getChildren("/dubbo/UserService/providers");
    // 2. 负载均衡选一个
    String target = loadBalance.select(providers);
    // 3. 建立 TCP 连接
    Socket socket = new Socket(target, 20880);
    // 4. 构造 Dubbo 协议帧
    byte[] request = buildDubboFrame("getUserById", new Object[]{id});
    // 5. 发送
    socket.getOutputStream().write(request);
    // 6. 读取响应
    byte[] response = readAll(socket.getInputStream());
    // 7. 反序列化
    return (User) hessianDeserialize(response);
    // 😱 每次都写这么多？！
}
```

**有了代理模式之后：**

```java
// 开发者只需要声明接口 + 注解
@DubboReference
private UserService userService;

// 然后像调本地方法一样调用！
User user = userService.getUserById(1L);
```

**背后的秘密——Dubbo 生成的动态代理（原理）：**

```java
// Dubbo 生成的代理类（概念上的等价代码）
public class UserServiceProxy implements UserService {

    @Override
    public User getUserById(Long id) {
        // 以下逻辑由 Dubbo 框架完成，开发者无感知：

        // 1. 从本地缓存获取 Provider 地址列表（已通过 Registry 同步）
        List<Invoker<UserService>> invokers = directory.list(invocation);

        // 2. 执行路由过滤（条件路由、标签路由等）
        invokers = routerChain.route(invokers, invocation);

        // 3. 负载均衡选择一个 Provider
        Invoker<UserService> invoker = loadBalance.select(invokers, invocation);

        // 4. 执行 Consumer 端 Filter 链（监控、限流、日志等）
        Result result = filterChain.invoke(invoker, invocation);

        // 5. 反序列化返回结果
        return (User) result.getValue();
    }
}
```

**Demo 代码对应：**

```java
// UserController.java
@DubboReference(version = "1.0.0", group = "demo", loadbalance = "random")
private UserService userService;  // ← 这个字段的实际类型是 Dubbo 生成的 UserServiceProxy

// 调用时，表面上是调用接口方法，实际上是走了一遍 RPC 全链路
User user = userService.getUserById(1L);
```

### 5.3 核心思想三：责任链模式（Filter 链）— 可编排的 AOP

Dubbo 的 Filter 链是典型的**责任链模式**，与 Servlet Filter、Spring Interceptor 如出一辙。

**为什么不用硬编码实现切面？**

```java
// 反模式：把所有横切逻辑硬编码在业务方法里
@Override
public User getUserById(Long id) {
    log.info("收到请求");                      // ← 日志
    if (!checkToken()) throw new AuthException(); // ← 鉴权
    if (concurrentCount > MAX) throw ...;          // ← 限流
    long start = System.currentTimeMillis();
    User user = doGetUserById(id);                 // ← 业务
    long elapsed = System.currentTimeMillis() - start;
    monitor.record("getUserById", elapsed);        // ← 监控
    log.info("返回结果");                           // ← 日志
    return user;
    // 三个问题：
    // 1. 业务代码被污染
    // 2. 每个方法都要重复写
    // 3. 想关掉某个功能？改每个方法
}
```

**责任链模式的解法：**

```
RPC 请求进入
      │
      ▼
┌─────────────────┐
│ AccessLogFilter │ ← 记录访问日志
└────────┬────────┘
         ▼
┌─────────────────┐
│ TokenFilter     │ ← 验证 Token
└────────┬────────┘
         ▼
┌─────────────────┐
│ ExecuteLimitFilter│ ← 并发控制
└────────┬────────┘
         ▼
┌─────────────────┐
│ ProviderLogFilter│ ← 我们的自定义 Filter!
└────────┬────────┘
         ▼
┌─────────────────┐
│ 业务方法        │ ← UserServiceImpl.getUserById()
└────────┬────────┘
         │ (响应沿链返回，每个 Filter 可做后置处理)
         ▼
     返回 Consumer
```

**Demo 代码对应：**

```java
// ProviderLogFilter.java — 我们的自定义 Filter
@Activate(group = CommonConstants.PROVIDER, order = 100)
public class ProviderLogFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        // ---- 前置处理 ----
        long startTime = System.currentTimeMillis();
        String consumerIp = RpcContext.getContext().getRemoteHost();

        log.info("[Filter-前置] 收到调用: service={}, method={}, from={}",
                invocation.getServiceName(),
                invocation.getMethodName(),
                consumerIp);

        // ---- 调用下一个 Filter / 业务方法 ----
        Result result = invoker.invoke(invocation);

        // ---- 后置处理 ----
        long elapsed = System.currentTimeMillis() - startTime;
        log.info("[Filter-后置] 调用完成: 耗时={}ms", elapsed);

        return result;
    }
}
```

---

## 六、RPC 调用全链路追踪

让我们以 `GET /api/users/1` 为例，追踪一次完整的 Dubbo RPC 调用：

```
┌──────────────────────────────────────────────────────────────────────────┐
│ 步骤 1: 用户发起 HTTP 请求                                                 │
│ ─────────────────────────                                                │
│ $ curl http://localhost:8090/api/users/1                                 │
│     │                                                                    │
│     ▼ Spring MVC DispatcherServlet 路由到 UserController.getUserById()   │
└──────────────────────────────────────────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼──────────────────────────────────────┐
│ 步骤 2: Consumer 端 — 进入代理对象                                         │
│ ───────────────────────────────                                          │
│ userService.getUserById(1L)                                              │
│     │                                                                    │
│     │ 实际上调用的是 Dubbo 生成的 UserServiceProxy                         │
│     ▼                                                                    │
│ ┌──────────────────────────────┐                                         │
│ │ ① 从 Directory 获取 Provider 列表:                                     │
│ │    [192.168.1.100:20880, 192.168.1.101:20880]                         │
│ │ ② Router 链过滤（条件路由、标签路由）                                     │
│ │ ③ LoadBalance.select(): 随机选 .100                                    │
│ │ ④ 构造 RpcInvocation {                                                 │
│ │      methodName: "getUserById",                                        │
│ │      parameterTypes: [Long.class],                                     │
│ │      arguments: [1L],                                                  │
│ │      attachments: {consumerApp: "dubbo-demo-consumer"}  ← 隐式传参      │
│ │    }                                                                   │
│ └──────────────────────────────┘                                         │
└──────────────────────────────────────────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼──────────────────────────────────────┐
│ 步骤 3: Consumer 端 — 协议编码（Dubbo Protocol）                            │
│ ───────────────────────────────────────                                  │
│ DubboCodec.encode(request)                                               │
│     │                                                                    │
│     ▼ 将 RpcInvocation 序列化为 Dubbo 协议帧的二进制字节                     │
│ ┌────────────────────────────┐                                           │
│ │ Magic: 0xdabb              │ ← 2 字节，标识 Dubbo 协议                     │
│ │ Flag: 0x80 (request)      │ ← 1 字节                                     │
│ │ Status: 0x00               │ ← 1 字节                                     │
│ │ Request ID: 123456789      │ ← 8 字节，唯一请求 ID                          │
│ │ Body Length: 256           │ ← 4 字节                                     │
│ │ Body: [Hessian2 序列化的   │ ← 变长                                       │
│ │   RpcInvocation 二进制]    │                                              │
│ └────────────────────────────┘                                           │
│     │                                                                    │
│     ▼ NettyClient.send(request) — TCP 发送                               │
└──────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │  TCP 网络传输 (局域网 < 1ms)
                                    ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ 步骤 4: Provider 端 — 协议解码                                             │
│ ───────────────────────────────                                          │
│ NettyServer.receive() — Netty NIO 线程接收字节流                           │
│     │                                                                    │
│     ▼ DubboCodec.decode() — 解析协议头 + 反序列化 Body                     │
│ ┌────────────────────────────┐                                           │
│ │ ① 校验 Magic: 0xdabb ✓     │                                           │
│ │ ② 读取 Request ID           │                                           │
│ │ ③ Hessian2 反序列化 Body    │                                           │
│ │    → 恢复 RpcInvocation     │                                           │
│ └────────────────────────────┘                                           │
│     │                                                                    │
│     ▼ 派发到线程池 (dispatcher=all)                                        │
└──────────────────────────────────────────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼──────────────────────────────────────┐
│ 步骤 5: Provider 端 — Filter 链 + 业务方法                                  │
│ ─────────────────────────────────────                                    │
│ ProviderLogFilter.invoke()                                               │
│     │  [前置] log.info("收到调用: getUserById")                            │
│     ▼                                                                    │
│ UserServiceImpl.getUserById(1L)                                          │
│     │  从 ConcurrentHashMap 查询                                         │
│     ▼  返回 User{id=1, username="张三", ...}                              │
│ ProviderLogFilter.invoke()                                               │
│     │  [后置] log.info("调用完成: 耗时=2ms")                               │
│     ▼                                                                    │
│ Result 对象封装返回值                                                      │
└──────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │  响应沿原路返回: 序列化 → TCP → 反序列化
                                    ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ 步骤 6: Consumer 收到响应                                                  │
│ ───────────────────────                                                  │
│ UserServiceProxy: 反序列化响应 Body → User 对象                            │
│     │                                                                    │
│     ▼ 返回给 UserController.getUserById()                                │
│                                                                          │
│ UserController: 将 User 封装为 JSON                                      │
│     │                                                                    │
│     ▼ HTTP 200 OK                                                       │
│ {                                                                        │
│   "success": true,                                                       │
│   "data": {                                                              │
│     "id": 1,                                                             │
│     "username": "张三",                                                   │
│     "email": "zhangsan@example.com",                                     │
│     "age": 28                                                            │
│   }                                                                      │
│ }                                                                        │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 七、项目代码走读 — 每个文件对应什么概念

### 7.1 项目结构总览

```
dubbo-demo/
│
├── pom.xml                              ← 父 POM，统一管理依赖版本
│
├── dubbo-demo-api/                      ← 【服务契约层】
│   └── src/main/java/.../api/
│       ├── model/
│       │   ├── User.java                ← 领域模型（Serializable）
│       │   └── Order.java               ← 订单模型
│       └── service/
│           ├── UserService.java         ← 服务接口（Provider 实现 → Consumer 引用）
│           └── OrderService.java        ← 第二个服务接口
│
├── dubbo-demo-provider/                 ← 【Provider 模块】
│   └── src/main/
│       ├── java/.../provider/
│       │   ├── bootstrap/
│       │   │   └── ProviderBootstrap.java  ← 启动入口（SpringApplication.run）
│       │   ├── config/
│       │   │   └── DubboProviderConfig.java ← @EnableDubbo 开关
│       │   ├── filter/
│       │   │   └── ProviderLogFilter.java   ← 自定义 Filter（SPI 扩展）
│       │   └── service/impl/
│       │       ├── UserServiceImpl.java     ← UserService 实现（@DubboService）
│       │       └── OrderServiceImpl.java    ← OrderService 实现
│       └── resources/
│           ├── application.yml            ← Dubbo YAML 配置
│           └── META-INF/dubbo/
│               └── org.apache.dubbo.rpc.Filter ← SPI 扩展点声明
│
├── dubbo-demo-consumer/                 ← 【Consumer 模块】
│   └── src/main/
│       ├── java/.../consumer/
│       │   ├── bootstrap/
│       │   │   └── ConsumerBootstrap.java  ← 启动入口
│       │   ├── config/
│       │   │   └── DubboConsumerConfig.java ← @EnableDubbo 开关
│       │   └── controller/
│       │       └── UserController.java      ← HTTP → Dubbo 门面
│       └── resources/
│           └── application.yml            ← Dubbo YAML 配置
│
└── DUBBO_GUIDE.md                       ← 本文档
```

### 7.2 关键注解速查

| 注解 | 作用 | 所在文件 |
|------|------|---------|
| `@EnableDubbo` | 启动 Dubbo 自动配置 | `DubboProviderConfig` / `DubboConsumerConfig` |
| `@DubboService` | 将实现类暴露为 Dubbo 服务（Provider 端） | `UserServiceImpl` / `OrderServiceImpl` |
| `@DubboReference` | 创建远程服务代理（Consumer 端） | `UserController` 中的 `userService` / `orderService` 字段 |
| `@Activate` | 标记 Filter 在 SPI 加载时的激活条件 | `ProviderLogFilter` |

### 7.3 配置方式展示

本项目展示了三种配置方式：

| 方式 | 文件 | 说明 |
|------|------|------|
| **YAML 文件配置** | `application.yml` | 主要方式，`dubbo.*` 层级自动映射，中文注释无需转义 |
| **注解配置** | `@DubboService` / `@DubboReference` | 方法级和引用级配置 |
| **Java API 配置** | `DubboProviderConfig.java` 中注释 | 通过 `@Bean` 创建 `ServiceConfig` 等（注释展示） |

---

## 八、启动与验证

### 8.1 前置条件

确保 Nacos 在 `192.168.3.100:8848` 运行，且可使用 `nacos/nacos` 登录控制台。

如果没有，可以通过 Docker 快速启动：

```bash
# Docker 方式（单机模式）
docker run -d --name nacos \
  -e MODE=standalone \
  -p 8848:8848 \
  nacos/nacos-server:2.2.3

# 然后访问 http://localhost:8848/nacos
# 用户名/密码：nacos/nacos
```

> **注意：** 演示项目配置的地址是 `192.168.3.100:8848`，
> 如果 Nacos 部署在其他地址，请同步修改 `application.yml` 中的 `dubbo.registry.address`。

### 8.2 编译项目

```bash
cd dubbo-demo
mvn clean package -DskipTests
```

### 8.3 启动 Provider

```bash
cd dubbo-demo-provider
mvn spring-boot:run

# 看到以下日志表示启动成功：
#   Dubbo Provider 启动完成！
#   等待 Consumer 调用...
```

### 8.4 启动 Consumer（另开终端）

```bash
cd dubbo-demo-consumer
mvn spring-boot:run

# 看到以下日志表示启动成功：
#   Dubbo Consumer 启动完成！
#   HTTP API 就绪: http://localhost:8090/api/users
```

### 8.5 验证调用

```bash
# 查询用户详情
curl http://localhost:8090/api/users/1

# 预期输出
# {"success":true,"data":{"id":1,"username":"张三","email":"zhangsan@example.com","age":28}}

# 查询所有用户
curl http://localhost:8090/api/users

# 查询用户订单
curl http://localhost:8090/api/users/1/orders

# 查询用户订单总额
curl http://localhost:8090/api/users/1/orders/total

# 创建新用户
curl -X POST http://localhost:8090/api/users \
  -H "Content-Type: application/json" \
  -d '{"username":"赵六","email":"zhaoliu@example.com","age":30}'
```

### 8.6 观察日志

在 Provider 终端中，你会看到自定义 Filter 的日志输出：

```
[Filter-前置] 收到调用: service=UserService, method=getUserById, consumerIp=192.168.x.x, consumerApp=dubbo-demo-consumer
[Provider] 收到 RPC 请求: getUserById(1)
[Provider] 返回结果: User[id=1, username='张三', email='zhangsan@example.com', age=28]
[Filter-后置] 调用完成: method=getUserById, 耗时=2ms, 结果=User[id=1, ...]
```

---

## 总结

通过这个 demo 项目，你应该已经理解了：

1. **Dubbo 基于什么** — Netty（网络）+ Hessian2（序列化）+ Nacos（注册中心 & 配置中心）+ Spring（IoC）+ 自定义 SPI（插件化）
2. **为什么出现** — 阿里业务从单体拆分为分布式服务后，缺乏统一的 RPC 框架来处理服务通信
3. **解决什么问题** — 服务发现（Registry）、负载均衡（LoadBalance）、容错（Cluster）、协议编解码（Protocol）
4. **什么架构** — 五大角色（Provider / Consumer / Registry / Monitor / Container）+ 三层体系（Business / RPC / Transport）
5. **设计思想** — 微内核+SPI（可插拔）、代理模式（透明调用）、责任链模式（AOP Filter 链）
6. **完整调用链路** — 从 HTTP 请求 → Dubbo 代理 → 负载均衡 → 协议编码 → Netty 传输 → Filter 链 → 业务方法 → 原路返回

建议下一步：尝试修改配置（换负载均衡策略、加超时、改序列化方式），观察行为变化，加深理解。
