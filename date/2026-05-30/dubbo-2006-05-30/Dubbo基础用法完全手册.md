# Apache Dubbo 基础用法完全手册

> 目标：一天内掌握 Dubbo 所有核心基础用法。
> 每个知识点都有**代码示例 + 配置方式 + 关键原理**。
> 基于 Dubbo 2.7.x / 3.x，Spring Boot 环境。

---

## 目录

- [一、一句话理解 Dubbo](#一一句话理解-dubbo)
- [二、配置方式速查](#二配置方式速查)
- [三、服务提供者 @DubboService 全参数](#三服务提供者-dubboservice-全参数)
- [四、服务消费者 @DubboReference 全参数](#四服务消费者-dubboreference-全参数)
- [五、注册中心：Nacos / Zookeeper 配置](#五注册中心nacos--zookeeper-配置)
- [六、多协议支持](#六多协议支持)
- [七、序列化方式](#七序列化方式)
- [八、集群容错策略（7 种）](#八集群容错策略7-种)
- [九、负载均衡策略（4 种）](#九负载均衡策略4-种)
- [十、超时与重试](#十超时与重试)
- [十一、服务分组与多版本](#十一服务分组与多版本)
- [十二、同步 / 异步调用](#十二同步--异步调用)
- [十三、本地存根 Stub 与服务降级 Mock](#十三本地存根-stub-与服务降级-mock)
- [十四、隐式参数传递 RpcContext](#十四隐式参数传递-rpccontext)
- [十五、泛化调用 GenericService](#十五泛化调用-genericservice)
- [十六、参数验证](#十六参数验证)
- [十七、结果缓存](#十七结果缓存)
- [十八、并发与连接控制](#十八并发与连接控制)
- [十九、令牌验证](#十九令牌验证)
- [二十、自定义 Filter（SPI）](#二十自定义-filterspi)
- [二十一、优雅停机](#二十一优雅停机)
- [二十二、Telnet 运维命令](#二十二telnet-运维命令)
- [二十三、常见问题排查](#二十三常见问题排查)

---

## 一、一句话理解 Dubbo

```
Consumer 调用接口方法
    ↓
@DubboReference 生成的动态代理
    ↓
负载均衡 → 选一台 Provider
    ↓
序列化参数 (Hessian2/Kryo)
    ↓
Netty TCP 发送到 Provider
    ↓
Provider 反序列化 → 反射调用实现类
    ↓
序列化结果 → 返回 Consumer
```

**关键角色：**
| 角色 | 做什么 | 典型代码 |
|------|--------|---------|
| **Provider** | 实现接口，暴露出服务 | `@DubboService` |
| **Consumer** | 引用接口，远程调用 | `@DubboReference` |
| **Registry** | 服务地址簿（Nacos/ZK） | `dubbo.registry.address` |

---

## 二、配置方式速查

Dubbo 支持 **4 种配置方式**，优先级从高到低：

| 方式 | 示例 | 适用场景 |
|------|------|---------|
| **① JVM 参数** | `-Ddubbo.protocol.port=20881` | 临时调试、容器化覆盖 |
| **② YAML 文件** | `application.yml` 中的 `dubbo.*` | 主配置方式 |
| **③ 注解** | `@DubboService(version="1.0.0")` | 方法级/接口级覆盖 |
| **④ Java API** | `new ServiceConfig<>()` | 非 Spring 环境、动态注册 |

### 2.1 YAML 全量配置模板

```yaml
# application.yml — Provider 端
dubbo:
  scan:
    base-packages: com.example.service.impl    # 扫描 @DubboService

  application:
    name: demo-provider                         # 应用名（Nacos 中显示）
    owner: my-team

  registry:
    address: "nacos://192.168.1.100:8848?username=nacos&password=nacos"
    # 或 ZK: "zookeeper://192.168.1.100:2181"
    timeout: 10000
    check: false                                # 启动时不检查注册中心

  protocol:
    name: dubbo                                 # dubbo / tri / rest / hessian
    port: 20880
    serialization: hessian2                      # hessian2 / kryo / protobuf
    threadpool: fixed
    threads: 200
    payload: 8388608                            # 最大数据包 8MB

  provider:
    timeout: 3000                               # Provider 端超时
    retries: 2                                  # 失败重试次数
    weight: 100                                 # 负载均衡权重
```

```yaml
# application.yml — Consumer 端
dubbo:
  application:
    name: demo-consumer

  registry:
    address: "nacos://192.168.1.100:8848?username=nacos&password=nacos"
    check: false

  consumer:
    timeout: 5000
    retries: 1
    check: false                                # 启动时不检查 Provider
    loadbalance: random                         # 全局默认负载均衡
```

### 2.2 注解方式（覆盖全局配置）

```java
// 方法级别覆盖，优先级高于 YAML
@DubboReference(
    version = "1.0.0",
    timeout = 2000,            // 覆盖全局 5000ms
    retries = 0,               // 覆盖全局 1 次
    loadbalance = "roundrobin" // 覆盖全局 random
)
private UserService userService;
```

### 2.3 Java API 方式（纯 Spring 非 Boot 场景）

```java
// 等价于 YAML 中的 dubbo.* 配置
ApplicationConfig app = new ApplicationConfig("demo-provider");
RegistryConfig reg = new RegistryConfig("nacos://192.168.1.100:8848");
ProtocolConfig proto = new ProtocolConfig("dubbo", 20880);
ServiceConfig<UserService> service = new ServiceConfig<>();
service.setApplication(app);
service.setRegistry(reg);
service.setProtocol(proto);
service.setInterface(UserService.class);
service.setRef(new UserServiceImpl());
service.export();   // 暴露服务
```

---

## 三、服务提供者 @DubboService 全参数

```java
import org.apache.dubbo.config.annotation.DubboService;

// 注意：旧版 (Dubbo 2.7.0-2.7.6) 使用 @Service，2.7.7+ 使用 @DubboService
@DubboService(
    version = "1.0.0",          // 版本号，多版本共存时使用
    group = "demo",             // 分组，同接口不同实现隔离
    timeout = 3000,             // 方法调用超时（毫秒）
    retries = 2,                // 失败重试次数（不含首次）
    weight = 100,               // 负载均衡权重（默认 100）
    loadbalance = "random",     // 负载均衡策略
    cluster = "failover",       // 集群容错策略
    actives = 0,                // 每连接最大并发数（0=不限制）
    executes = 0,               // 服务端最大并发执行数（0=不限制）
    connections = 0,            // 每个 Consumer 的最大连接数
    delay = -1,                 // 延迟暴露（毫秒，-1=Spring 容器初始化后）
    validation = "true",        // 启用参数验证（需 JSR 303 注解）
    cache = "lru",              // 结果缓存：lru / threadlocal / jcache
    mock = "force:return null", // 服务降级 Mock
    stub = "com.example.UserServiceStub",  // 本地存根
    token = "true",             // 令牌验证（自动生成）
    tag = "gray",               // 标签路由（Dubbo 3）
    protocol = "dubbo"          // 指定协议
)
@Component
public class UserServiceImpl implements UserService { ... }
```

### 注解 + YAML 优先级规则

```
@DubboService(version = "1.0.0")     ★ 最高（方法级）
    │
dubbo.provider.timeout=3000          ★ 次高（Provider 全局默认）
    │
dubbo.protocol.name=dubbo            ★ 最低（协议级）
```

---

## 四、服务消费者 @DubboReference 全参数

```java
import org.apache.dubbo.config.annotation.DubboReference;

@DubboReference(
    version = "1.0.0",           // 必须与 Provider 匹配
    group = "demo",              // 必须与 Provider 匹配
    timeout = 5000,              // Consumer 端超时（覆盖 Provider 端）
    retries = 1,                 // 失败重试次数
    loadbalance = "random",      // 负载均衡
    cluster = "failover",        // 集群容错
    check = false,               // 启动时不检查 Provider
    url = "dubbo://192.168.1.10:20880", // 直连（绕过注册中心）
    lazy = true,                 // 延迟连接（真正调用时才建立连接）
    sticky = false,              // 粘滞连接（尽量发往同一 Provider）
    sent = true,                 // 是否异步发送（true=等待结果）
    protocol = "dubbo",          // 指定协议
    mock = "force:return null",  // 服务降级
    stub = "com.example.UserServiceStub",  // 本地存根
    validation = "true",         // 参数验证
    cache = "lru",               // 结果缓存
    async = false,               // 是否异步调用（true 时返回 null）
    connections = 1,             // 连接数限制
    actives = 20,                // 每方法最大并发调用数
    tag = "gray"                 // 标签路由
)
private UserService userService;
```

**特别提醒：**

```java
// check = false 的用途
// 1. 开发时让 Consumer 先启动，Provider 后启动也不报错
// 2. 生产环境建议 check = true（快速发现 Provider 不可用）

// check = false 的代价
// 调用时 Provider 不可用 → 调用失败抛 RpcException
// 只是把检查从"启动时"推迟到了"第一次调用时"
```

---

## 五、注册中心 Nacos / Zookeeper 配置

### 5.1 Nacos（推荐）

```yaml
dubbo:
  registry:
    address: "nacos://192.168.1.100:8848?username=nacos&password=nacos"
    # 或带命名空间
    # address: "nacos://192.168.1.100:8848?username=nacos&password=nacos&namespace=dev"
```

**Nacos 控制台能看到什么？**

| 菜单 | 看到的内容 |
|------|-----------|
| **服务管理 → 服务列表** | 每个 `@DubboService` 对应一个服务，命名如 `providers:com.example.UserService:1.0.0` |
| **服务详情** | 具体 IP:Port、权重、健康状态 |
| **订阅者** | 有多少 Consumer 在订阅此服务 |

### 5.2 Zookeeper

```yaml
dubbo:
  registry:
    address: "zookeeper://192.168.1.100:2181"
    # 集群用逗号分隔
    # address: "zookeeper://192.168.1.100:2181?backup=192.168.1.101:2181,192.168.1.102:2181"
```

**Zookeeper 节点结构：**

```
/dubbo
  └── com.example.UserService        ← 服务接口全限定名
        ├── providers                 ← 提供者列表（临时节点）
        │   ├── dubbo://192.168.1.10:20880
        │   └── dubbo://192.168.1.11:20880
        ├── consumers                 ← 消费者列表（临时节点）
        │   └── consumer://192.168.1.20
        ├── routers                   ← 路由规则
        └── configurators             ← 动态配置
```

### 5.3 多注册中心

```yaml
dubbo:
  registries:
    nacos:
      address: "nacos://192.168.1.100:8848"
    zk:
      address: "zookeeper://192.168.1.200:2181"

# 指定某个服务注册到特定注册中心
@DubboService(registry = "nacos")
```

---

## 六、多协议支持

### 6.1 各协议对比

| 协议 | 标识 | 连接方式 | 序列化 | 适用场景 |
|------|------|---------|--------|---------|
| **Dubbo** | `dubbo://` | TCP 长连接 | Hessian2(默认) | 内部 Java ↔ Java，性能最高 |
| **Triple** | `tri://` | HTTP/2 | Protobuf | 跨语言、gRPC 互通、流式 |
| **gRPC** | 基于 Triple | HTTP/2 | Protobuf | 与原生 gRPC 互通 |
| **HTTP/REST** | `rest://` | HTTP 短连接 | JSON | 对外 API、异构系统、浏览器 |
| **Hessian** | `hessian://` | HTTP 短连接 | Hessian | 轻量级跨语言 |
| **Thrift** | `thrift://` | TCP | Thrift | 对接 Thrift 服务 |

### 6.2 多协议暴露同一服务

```yaml
dubbo:
  protocols:
    dubbo:
      name: dubbo
      port: 20880
    tri:
      name: tri
      port: 50051
    rest:
      name: rest
      port: 8081
      server: netty
```

```java
// Service 指定协议（默认用第一个）
@DubboService(protocol = "rest")
```

### 6.3 Triple 协议示例（Dubbo 3 重点）

```yaml
dubbo:
  protocol:
    name: tri
    port: 50051
```

```java
// 接口定义（与原生 gRPC 兼容）
@DubboService(protocol = "tri")
public class GreeterImpl implements GreeterService { ... }
```

**Triple 特性：**
- 基于 HTTP/2，支持流式 `StreamObserver<T>`
- 兼容 gRPC（可互调）
- Protobuf 序列化，支持跨语言（Go / Rust / Python）

---

## 七、序列化方式

| 序列化 | 配置值 | 特点 | 推荐场景 |
|--------|-------|------|---------|
| **Hessian2** | `hessian2` | Dubbo 默认，跨语言，Java 兼容性好 | 通用场景 |
| **Kryo** | `kryo` | 性能极高（比 Hessian2 快 5-10 倍） | 纯 Java 内部调用 |
| **FST** | `fst` | 类似 Kryo，更快的序列化/反序列化 | 纯 Java 内部调用 |
| **Protobuf** | `protobuf` | 跨语言，结构化，配合 Triple | 跨语言场景 |
| **FastJson** | `fastjson` | JSON 格式，人类可读 | 调试、简单场景 |
| **Java** | `java` | JDK 原生，安全性差 | 不推荐 |

```yaml
# 全局指定
dubbo:
  protocol:
    serialization: kryo
```

```java
// 或单服务指定
@DubboService(protocol = "dubbo")
public class UserServiceImpl implements UserService {
    // 使用协议默认的序列化方式
}
```

**性能参考（纯 Java 场景）：Kryo ≈ FST > Hessian2 > FastJson > Java**

---

## 八、集群容错策略（7 种）

当 Consumer 调用 Provider **失败时**，Dubbo 按配置的 `cluster` 策略处理。

```java
// 在 @DubboReference 或 @DubboService 上指定
@DubboReference(cluster = "failover")
```

```yaml
# 或 YAML 全局指定
dubbo:
  consumer:
    cluster: failover
```

| 策略 | 配置值 | 行为 | 适用场景 |
|------|-------|------|---------|
| **① 失败重试** | `failover` **(默认)** | 重试其他 Provider（默认 2 次） | 读操作，幂等 |
| **② 快速失败** | `failfast` | 立即报错，不重试 | 写操作，防重复 |
| **③ 安全失败** | `failsafe` | 吞掉异常，返回空结果 | 不重要日志写入 |
| **④ 失败恢复** | `failback` | 后台记录，定时重试 | 消息通知等场景 |
| **⑤ 并行调用** | `forking` | 同时调多个 Provider，最快返回 | 高实时性读操作 |
| **⑥ 广播** | `broadcast` | 调所有 Provider，任一失败报错 | 缓存刷新、状态同步 |
| **⑦ 可用** | `available` | 遍历找到第一个可用 | 简单旁路场景 |

```java
// 经典组合：读操作 failfast，写操作 failfast
@DubboReference(cluster = "failover", retries = 2)   // 读：重试
@DubboReference(cluster = "failfast")                 // 写：不重试
```

---

## 九、负载均衡策略（4 种）

Consumer 端选择**哪一台 Provider** 来调用。

```java
@DubboReference(loadbalance = "random")
```

```yaml
dubbo:
  consumer:
    loadbalance: random  # 全局默认
```

| 策略 | 配置值 | 原理 | 适用场景 |
|------|-------|------|---------|
| **① 随机** | `random` **(默认)** | 按权重随机 | 通用，请求均匀 |
| **② 轮询** | `roundrobin` | 按权重轮询 | 请求量均衡 |
| **③ 最少活跃** | `leastactive` | 挑正在处理的请求数最少的 | 耗时不均的场景 |
| **④ 一致性哈希** | `consistenthash` | 相同参数 → 同一台 Provider | 本地缓存命中 |

```java
// 一致性哈希典型用法
@DubboReference(loadbalance = "consistenthash")
// 相同 userId 的请求始终打到同一台 Provider
User user = userService.getUserById(123L);
```

---

## 十、超时与重试

### 10.1 超时配置

```yaml
dubbo:
  consumer:
    timeout: 3000     # Consumer 端全局默认（毫秒）
  provider:
    timeout: 5000     # Provider 端全局默认
```

```java
// 方法级覆盖（优先级最高）
@DubboReference(timeout = 1000)     // 1 秒超时
```

**超时判断规则：**

```
Consumer 发起调用 → 开始计时
                      │
                      ├─ 网络传输（序列化 + 发送 + 反序列化）
                      ├─ Provider 执行业务逻辑
                      └─ 网络返回
                         │
                         └─ 超时 ← Consumer 抛出 TimeoutException
```

### 10.2 重试

```java
@DubboReference(retries = 2)     // 重试 2 次（总共最多 3 次调用）
```

**重试注意事项：**

```
⚠ 非幂等操作（insert/update/delete）要小心：
  - 使用 cluster = "failfast" 避免重试
  - 或在业务层做防重（唯一索引、幂等表）

✔ 幂等操作（select、纯查询）可放心重试
```

---

## 十一、服务分组与多版本

### 11.1 分组 — 同接口多实现

```java
// Provider 端
@DubboService(group = "vip")
public class VipUserServiceImpl implements UserService { ... }

@DubboService(group = "normal")
public class NormalUserServiceImpl implements UserService { ... }

// Consumer 端
@DubboReference(group = "vip")
private UserService vipUserService;       // 只调 VIP 实现

@DubboReference(group = "*")
private List<UserService> allUserServices; // 调所有分组
```

### 11.2 多版本 — 灰度发布

```java
// Provider 端 — 旧版本
@DubboService(version = "1.0.0")

// Provider 端 — 新版本（灰度上线，少数机器）
@DubboService(version = "2.0.0")

// Consumer 端 — 逐步切换
@DubboReference(version = "1.0.0")   // 大部分调用旧版
@DubboReference(version = "2.0.0")   // 小部分调用新版
@DubboReference(version = "*")        // 随机调任一版本（不推荐生产）
```

---

## 十二、同步 / 异步调用

### 12.1 同步调用（默认）

```java
// 最常用——线程阻塞直到结果返回
User user = userService.getUserById(1L);
```

### 12.2 异步调用 CompletableFuture

接口方法返回 `CompletableFuture`：

```java
// API 接口定义
public interface UserService {
    CompletableFuture<User> getUserById(Long id);
}

// Consumer 调用
CompletableFuture<User> future = userService.getUserById(1L);
future.thenAccept(user -> System.out.println(user));
```

### 12.3 异步回调

```java
// Consumer 端
@DubboReference(async = true)
private UserService userService;

// 调用
userService.getUserById(1L);
// 立即返回 null，结果通过 Future 获取
Future<User> future = RpcContext.getContext().getFuture();
User user = future.get();  // 同步等待
```

---

## 十三、本地存根 Stub 与服务降级 Mock

### 13.1 本地存根 Stub

在 Consumer 端添加**本地代理**，在远程调用之前/之后执行逻辑。

```java
// 接口定义（API 模块）
public interface UserService {
    User getUserById(Long id);
}

// Stub 类（Consumer 端，必须要有接口的构造参数）
// 类名：接口名 + "Stub"，放在 Consumer 端
public class UserServiceStub implements UserService {
    private final UserService userService;

    // Dubbo 自动传入远程代理
    public UserServiceStub(UserService userService) {
        this.userService = userService;
    }

    @Override
    public User getUserById(Long id) {
        // 前置处理：参数校验
        if (id == null || id <= 0) {
            return null;
        }
        // 前置处理：日志、权限检查、缓存穿透
        System.out.println("准备查询用户: " + id);

        // 调用远程
        User user = userService.getUserById(id);

        // 后置处理：结果转换、补充
        return user;
    }
}
```

```java
// Consumer 端引用
@DubboReference(stub = "com.example.consumer.stub.UserServiceStub")
private UserService userService;
```

### 13.2 服务降级 Mock

Provider 不可用或调用失败时，返回**本地模拟数据**。

```java
// Mock 类（Consumer 端）
// 类名：接口名 + "Mock"
public class UserServiceMock implements UserService {
    @Override
    public User getUserById(Long id) {
        // 返回默认降级数据
        return new User(id, "【降级用户】", "unreachable@mock.com");
    }
}
```

```java
@DubboReference(mock = "com.example.consumer.mock.UserServiceMock")
private UserService userService;
```

**Mock 的两种 force 模式：**

```java
// 强制 Mock：不发起远程调用，直接返回 Mock 数据
@DubboReference(mock = "force:return null")

// 失败 Mock：远程调用失败后才用 Mock 数据（推荐）
@DubboReference(mock = "fail:return null")

// 直接返回固定值
@DubboReference(mock = "fail:return {\"id\":1,\"username\":\"mock\"}")
// 或抛异常
@DubboReference(mock = "fail:throw com.example.MyException")
```

---

## 十四、隐式参数传递 RpcContext

在 Consumer 端设置额外的参数，随 RPC 请求一起发送到 Provider。

```java
// Consumer 端
RpcContext.getContext().setAttachment("traceId", "abc-123");
RpcContext.getContext().setAttachment("source", "mobile-app");

User user = userService.getUserById(1L);  // 参数随请求传输

// Provider 端
String traceId = RpcContext.getContext().getAttachment("traceId");
String source = RpcContext.getContext().getAttachment("source");
```

**注意：**
- `RpcContext` 是**线程绑定**的，每次调用后需重新设置（异步场景注意）
- Dubbo 3 中建议用 `RpcServiceContext` 代替
- 隐式参数不超过 8KB（payload 限制）

---

## 十五、泛化调用 GenericService

Consumer 端**不需要引入 API 接口 JAR**，通过服务名+方法名+参数类型字符串调用。

```java
// Consumer 端（无需引入 API 依赖）
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.rpc.service.GenericService;

@DubboReference(
    version = "1.0.0",
    group = "demo",
    generic = true          // 开启泛化调用
)
private GenericService userService;

// 调用
Object result = userService.$invoke(
    "getUserById",              // 方法名
    new String[]{"java.lang.Long"},  // 参数类型数组
    new Object[]{1L}               // 参数值数组
);
```

**适用场景：**
- **API 网关**：不知道具体接口，但知道方法名和参数
- **测试工具**：动态调用任意服务
- **跨语言桥接**：非 Java 服务通过 HTTP + 泛化调用

---

## 十六、参数验证

在接口方法参数上标注 JSR 303 (`javax.validation`) 注解，由 Dubbo 自动验证。

```xml
<!-- Maven 依赖 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

```java
// API 接口
public interface UserService {
    User saveUser(
        @NotNull(message = "用户对象不能为空")
        @Valid User user
    );
}

// User model
public class User implements Serializable {
    @NotNull(message = "用户名不能为空")
    @Size(min = 2, max = 32, message = "用户名长度 2-32")
    private String username;

    @Email(message = "邮箱格式不正确")
    private String email;

    @Min(value = 0, message = "年龄不能小于 0")
    @Max(value = 150, message = "年龄不能大于 150")
    private Integer age;
    // getter/setter...
}
```

```java
// Provider 端开启验证
@DubboService(validation = "true")
public class UserServiceImpl implements UserService { ... }
```

验证失败时 Provider 端抛出 `ConstraintViolationException`。

---

## 十七、结果缓存

Provider 端对方法返回值做缓存，相同参数直接返回缓存结果。

```java
@DubboService(cache = "lru")
public class UserServiceImpl implements UserService {
    // 相同参数调用多次，只有第一次执行，后续返回缓存
    @Override
    public User getUserById(Long id) { ... }
}
```

| 缓存策略 | 配置值 | 行为 |
|---------|-------|------|
| **LRU** | `lru` | 最近最少使用淘汰，默认 1000 条 |
| **线程本地** | `threadlocal` | 当前线程内缓存，线程结束清除 |
| **JCache** | `jcache` | JCache (JSR 107) 实现，可配置分布式缓存 |

```java
// Consumer 端也可开启缓存
@DubboReference(cache = "lru")
private UserService userService;
```

---

## 十八、并发与连接控制

### 18.1 服务端并发控制

```java
// Provider 端
@DubboService(
    executes = 10,    // 该服务所有方法最大并发执行数
    actives = 5       // 每个 Consumer 对该方法的并发数
)
public class UserServiceImpl implements UserService { ... }
```

### 18.2 消费端并发控制

```java
// Consumer 端
@DubboReference(
    actives = 20,      // 每方法最大并发调用
    connections = 1    // 对每个 Provider 建立 1 个连接
)
private UserService userService;
```

### 18.3 线程池配置

```yaml
dubbo:
  protocol:
    threadpool: fixed         # fixed / cached / limited / eager
    threads: 200              # 核心线程数（fixed 池大小）
    queues: 0                 # 等待队列大小（0 = 无界，慎用）
    iothreads: 16             # IO 线程池大小（默认 = CPU + 1）
```

---

## 十九、令牌验证

防止未授权的 Consumer 访问 Provider。

```java
// Provider 端
@DubboService(token = "true")       // 自动生成令牌
// 或指定固定令牌
@DubboService(token = "mySecretKey123")
```

```java
// Consumer 端
@DubboReference(token = "mySecretKey123")  // 必须与 Provider 匹配
private UserService userService;
```

**原理：** Consumer 在调用时带上 token，Provider 校验 token 是否匹配。不匹配则拒绝调用。

---

## 二十、自定义 Filter（SPI）

### 20.1 创建 Filter

```java
package com.example.provider.filter;

import org.apache.dubbo.rpc.*;

@Activate(group = {CommonConstants.PROVIDER, CommonConstants.CONSUMER}, order = 100)
public class LogFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        long start = System.currentTimeMillis();
        System.out.println("[Filter] 调用: " + invocation.getMethodName());

        Result result = invoker.invoke(invocation);

        long elapsed = System.currentTimeMillis() - start;
        System.out.println("[Filter] 返回: " + result.getValue() + ", 耗时: " + elapsed + "ms");
        return result;
    }
}
```

### 20.2 SPI 声明文件

```
# src/main/resources/META-INF/dubbo/org.apache.dubbo.rpc.Filter
myLogFilter=com.example.provider.filter.LogFilter
```

### 20.3 内置 Filter 速查

| Filter | 配置 | 作用 |
|--------|------|------|
| `ExceptionFilter` | 默认 | Provider 端异常处理 |
| `TimeoutFilter` | 默认 | 超时日志 |
| `AccessLogFilter` | `accesslog="true"` | 记录访问日志 |
| `ActiveLimitFilter` | `actives` 限制 | 消费端并发控制 |
| `ExecuteLimitFilter` | `executes` 限制 | 服务端并发控制 |
| `TokenFilter` | `token` | 令牌验证 |
| `TpsLimitFilter` | `tps` | QPS 限流 |
| `CacheFilter` | `cache` | 结果缓存 |
| `ValidationFilter` | `validation` | 参数验证 |
| `MonitorFilter` | 监控 | 监控统计 |
| `ContextFilter` | 默认 | Provider 端 RpcContext 设置 |

---

## 二十一、优雅停机

Dubbo 通过 Spring 的 `ShutdownHook` 实现优雅停机，**默认开启**。

**关闭过程：**

```
应用收到 SIGTERM 信号
    │
    1. 标记服务为"只读"状态
    │   （注册中心通知 Consumer 不再分发新请求到此节点）
    │
    2. 等待已有请求处理完成（默认 10 秒超时）
    │
    3. 关闭线程池
    │
    4. 释放端口
    │
    进程退出
```

```yaml
# 自定义超时
dubbo:
  provider:
    timeout: 3000
  shutdown:
    wait: 30000     # 优雅停机最大等待时间（毫秒），默认 10000
```

```shell
# 手动触发优雅关闭
kill -15 <pid>      # SIGTERM → 触发 ShutdownHook（推荐）
kill -9 <pid>       # SIGKILL → 直接杀死，跳过低我停机（不推荐）
```

---

## 二十二、Telnet 运维命令

Dubbo 暴露 Telnet 端口（默认 2222），可在线运维。

```bash
# 连接
telnet 127.0.0.1 2222    # 或 telnet localhost 2222

# 查看服务列表
dubbo> ls
dubbo> ls com.example.UserService

# 调用服务方法（直接触发 RPC）
dubbo> invoke com.example.UserService.getUserById(1L)
# 返回 JSON 结果

# 查看连接状态
dubbo> status

# 切换当前服务
dubbo> cd com.example.UserService

# 查看当前目录
dubbo> pwd

# 查看端口与连接信息
dubbo> net

# 查看日志级别
dubbo> log --level DEBUG
```

```yaml
# 关闭 Telnet（生产环境建议关闭或限制 IP）
dubbo:
  qos:
    port: 2222
    accept.foreign.ip: false     # 仅本地可访问
    accept.foreign.ip.whitelist: 127.0.0.1
```

---

## 二十三、常见问题排查

### 23.1 No provider available

```
org.apache.dubbo.rpc.RpcException: No provider available from registry ...
```

**排查步骤：**
```
1. ❓ Provider 启动了吗？     → 看日志是否有 "Dubbo service server started"
2. ❓ Provider 注册成功了吗？  → 看 Nacos 控制台服务列表
3. ❓ group/version 匹配吗？   → Consumer 的 group + version 必须与 Provider 一致
4. ❓ Provider 被限流/降级了？ → 检查 executes / actives / mock 配置
5. ❓ 网络通吗？              → telnet ProviderIP ProviderPort
```

### 23.2 Consumer 调用超时

```
Caused by: org.apache.dubbo.remoting.TimeoutException...
```

**原因 & 解决：**
```yaml
dubbo:
  consumer:
    timeout: ${实际耗时峰值 × 3}   # 留余量
  provider:
    timeout: ${实际耗时峰值 × 2}   # Provider 端应小于 Consumer 端
```

### 23.3 序列化异常

```
java.lang.ClassNotFoundException: ... (NoClassDefFoundError)
```

**原因：** Provider 返回的对象类型在 Consumer 端不存在。
**解决：** API 模块（共享 JAR）中定义所有 POJO，Provider 和 Consumer 共同依赖。

### 23.4 启动慢 / 卡住

```
INFO ... Waiting for instance registration ...
```

**原因：** Nacos 服务注册阻塞启动。
**解决：**

```yaml
dubbo:
  registry:
    check: false   # 启动时不等待注册完成
```

---

## 附录：速查对照表

### A. YAML 配置 ↔ Dubbo Config 类

| YAML 路径 | Config 类 |
|-----------|-----------|
| `dubbo.application` | `ApplicationConfig` |
| `dubbo.registry` | `RegistryConfig` |
| `dubbo.protocol` | `ProtocolConfig` |
| `dubbo.provider` | `ProviderConfig` |
| `dubbo.consumer` | `ConsumerConfig` |
| `dubbo.monitor` | `MonitorConfig` |
| `dubbo.metadata-report` | `MetadataReportConfig` |
| `dubbo.config-center` | `ConfigCenterConfig` |

### B. 注解 → 对应哪个端

| 注解 | 端 | 作用 |
|------|----|------|
| `@DubboService` | Provider | 暴露服务 |
| `@DubboReference` | Consumer | 引用服务 |
| `@EnableDubbo` | 两者 | 启动 Dubbo 能力 |

### C. 常见端口

| 端口 | 用途 |
|------|------|
| 20880 | Dubbo 协议默认 |
| 50051 | Triple 协议默认 |
| 2222 | Telnet/QoS 运维端口 |
| 8848 | Nacos 注册中心 |

---

> **最后建议：**
> 先跑通一个最简单的 Provider + Consumer（5 个文件），确认 Nacos 注册成功。
> 然后逐个试验：改负载均衡、加超时、加 mock、改序列化……
> 每改一个配置，观察控制台输出和 Nacos 服务列表的变化。
> 理解"改了什么 → 发生了什么"比背参数更重要。
