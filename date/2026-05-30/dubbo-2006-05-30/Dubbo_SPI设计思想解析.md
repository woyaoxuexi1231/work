# Dubbo SPI 设计思想深度解析

> 为什么 Dubbo 用配置文件式的 SPI，而不是 Spring 式的扫包发现？
> ——这不是技术落后，而是设计上的清醒选择。

---

## 一、问题的本质

不是「Dubbo 为什么不用 Spring」，而是：

**一个需要高度可扩展的 RPC 框架内核，为什么显式配置式 SPI 比扫包自动发现更合理？**

---

## 二、扫包在框架内核层面有几条硬伤

### 2.1 框架扩展点不能靠「猜」

对 Dubbo 内核来说，协议、序列化、负载均衡、注册中心——**每一个实现被加载的时机、使用的 key、是否启用，都必须精确掌控**。

**SPI 配置文件方式：**

```properties
# META-INF/dubbo/org.apache.dubbo.rpc.Protocol
dubbo=com.alibaba.dubbo.rpc.protocol.dubbo.DubboProtocol
tri=org.apache.dubbo.rpc.protocol.tri.TripleProtocol
```

框架读这个文件，立刻知道「有哪些可用的协议实现，分别叫什么名字」，不需要去扫描任何类，更不需要把类加载到 JVM 里验证。这是**声明式的注册表**，零歧义。

**如果用扫包思想：**

规定凡是 classpath 下实现了 `Protocol` 接口的类都算扩展实现。这会带来几个致命问题：

| 问题 | 后果 |
|------|------|
| 无意引入第三方 jar，里面有个 `Protocol` 实现 | 被意外加载，可能覆盖掉用户自己的配置 |
| 扫出来多个实现 | 怎么确定哪个是用户想用的？得靠优先级、条件注解、别名——很快又演变成一套「配置文件+注解」的混合方案，反而更复杂 |
| 必须加载所有候选类 | 至少读取元数据才能判断是不是扩展实现，启动成本大大提高 |

类比：**你家防盗门的钥匙孔，只应该接受特定形状的钥匙，而不是谁路过门口伸手试一下都算数。** 框架内核的扩展入口，必须极度确定。SPI 配置文件就是那把精确的钥匙。

---

### 2.2 按需加载 vs 全量扫描，性能不在一个级别

Dubbo 有上百个扩展点，如果每次启动都扫描全 classpath：

- 触发大量磁盘 IO、Jar 包遍历、类元数据解析
- 很多扩展实现（如 `hessian` 协议、`multicast` 注册中心）用户可能永远不用，但为了发现它们，类依然得被扫描

而 SPI 配置文件非常小，读取成本极低：

```
框架启动：
   读配置文件 → 拿到一列「类名字符串」            ← 耗时 0.1ms
   真正使用时：
     loadbalance = "random"
     → 实例化 RandomLoadBalance                  ← 才真正加载类
```

框架一开始只解析配置文件里的类名（字符串），真正实例化只在用户第一次真正使用时才发生。这是典型的**延迟加载 + 按需实例化**。

扫包思想则倾向于「尽早发现」，在框架层不是一个好策略。

---

### 2.3 扫包玩不转 Dubbo SPI 的高级特性

Dubbo SPI 不只是一个「发现实现类」的工具，它内置了一套组合用的增强机制——这些是扫包模型难以实现的。

#### Wrapper 自动包装

```java
// 如果有类的构造参数是接口类型，Dubbo SPI 自动把它当作包装器
class ProtocolFilterWrapper implements Protocol {
    public ProtocolFilterWrapper(Protocol protocol) {  // ← 构造参数是 Protocol
        this.protocol = protocol;
    }
}
```

Dubbo SPI 加载 `DubboProtocol` 时，发现 `ProtocolFilterWrapper` 的构造参数是 `Protocol`，自动包装：

```
DubboProtocol → ProtocolFilterWrapper(DubboProtocol) → 返回给调用方
```

这实现了类似 AOP 的效果，但完全不用注解，只靠构造器签名约定。扫包模式下要如何自动发现「哪些是包装器，哪些不是」？又得引入额外的标记注解，违背了简洁性。

#### 自适应扩展（@Adaptive）

```java
@Adaptive
public interface Protocol {
    // Dubbo 会在运行时生成一个代理类
    // 根据 URL 中的协议名动态选择具体实现
}

// URL: dubbo://192.168.1.1:20880
// 代理类自动解析协议名为 "dubbo" → 调 DubboProtocol

// URL: tri://192.168.1.1:50051
// 代理类自动解析协议名为 "tri" → 调 TripleProtocol
```

这种「根据运行时上下文动态路由到不同实现」的能力，扫包机制完全无法提供——扫包只解决「有什么」，不解决「怎么选」。

#### 扩展点之间的依赖注入

Dubbo SPI 支持在扩展实现里用 setter 注入其他扩展点，而且是注入 SPI 代理（可能包含包装器）。这对框架内部组装非常关键。如果全靠扫包注入，就退化成了「需要一个完整 IoC 容器」——而 Dubbo 内核偏偏不希望依赖 Spring 那种重型容器。

---

## 三、Dubbo 不采用扫包的真正原因

### 3.1 显式注册永远比隐式发现更可靠

| | SPI 配置文件 | 扫包自动发现 |
|---|---|---|
| 扩展点清单 | 写死的，清晰可见 | 靠扫描，运行才知道 |
| 覆盖优先级 | 文件顺序 + key 覆盖，确定性强 | 类路径顺序 + 扫描时机，不确定 |
| 加载代价 | 读一个文件 O(1) | 扫全 classpath O(n) |
| 是否延迟加载 | 是，只用才加载 | 否，扫到就要检查 |

**在框架基础层，「拿着名册找人」比「广撒网搜人」靠谱得多。**

### 3.2 那时候有「扫描」思想吗？有，但被刻意避开了

Dubbo 最初研发在 2008 年前后，Spring 2.5 的 `@Component` 和类路径扫描刚出现不久，还没有成为主流。但 Dubbo 团队其实很熟悉 OSGi、Eclipse 插件体系那一套——那些也都是基于 **manifest 文件显式声明** 的模式。

更早的 Java SPI（JDK 1.6 的 `ServiceLoader`）就是靠 `META-INF/services/` 下的文件来声明的。Dubbo 顺着这个思路，把 Java SPI 的缺点（一次性全加载、无 AOP/IOC）改掉，造了一个增强版。

**他们从头到尾就没想过用扫包管理内核扩展点，因为在框架基础层，显式注册永远比隐式发现更可靠。**

---

## 四、Dubbo 不是不懂扫描——它分清了场景

Dubbo 在后来的 Spring 集成中，为了让开发者方便暴露服务，引入了 `@Service` 注解和 `@EnableDubbo`，**在应用层实现了扫描**：

```java
@SpringBootApplication
@EnableDubbo(scanBasePackages = "com.example.dubbo.demo.provider")
public class ProviderBootstrap { ... }
```

这个时候扫包就非常合理了：业务 Bean 本来就是给 Spring 管的，Dubbo 借 Spring 的扫描能力发现标了 `@DubboService` 的 Bean，自动注册到注册中心，完美衔接。

但内核扩展点——协议、序列化、负载均衡、Filter、注册中心——依旧铁打不动地用 SPI 配置文件。

```
Dubbo 的分层：
┌──────────────────────────────────────────────────────┐
│  应用层（你的代码）                                    │
│  @DubboService / @DubboReference / @EnableDubbo      │
│  通过 Spring 扫描 → 业务 Bean                        │
├──────────────────────────────────────────────────────┤
│  内核层（Dubbo 框架自身）                              │
│  Protocol / Serialization / LoadBalance / Filter     │
│  通过 SPI 配置文件 → 插件化扩展                        │
└──────────────────────────────────────────────────────┘
```

因为内核不能绑定 Spring，也不能用「扫出来哪个用哪个」这种不确定方式。

---

## 五、一句话总结

```
扫包是「广撒网」的发现机制 → 适合应用层，环境可控
SPI 是「拿着名册找人」的机制 → 适合框架内核，必须精确

Dubbo 两个都用：
  内核扩展点 → SPI（铁打不动）
  业务 Bean   → Spring 扫描（通过 dubbo-spring-boot-starter）

不是设计师没跟上时代，
而是对于微内核框架而言，
显式、确定、高性能的 SPI 插件体系是更优解。
这个分界，Dubbo 从一开始就拿捏得很清楚。
```
