# Spring Security 30天面试突击指南 (🔐 安全防御与权限专家版)

> **总纲**：本指南深度聚焦于 **Spring Security**。我们将从其核心的过滤器链（FilterChain）机制出发，全面攻克认证（Authentication）、授权（Authorization）、OAuth2/OIDC、JWT 集成、CSRF/XSS 防御以及生产级动态权限管理。
> 
> **学习路径**：
> 1. **Week 1 (1-7天)**：核心架构、过滤器链与认证流程全解。
> 2. **Week 2 (8-14天)**：授权管理、表达式控制与方法级安全。
> 3. **Week 3 (15-21天)**：OAuth2, OIDC 与 JWT 深度集成。
> 4. **Week 4 (22-26天)**：Web 安全防御：CSRF, CORS, XSS 与安全头。
> 5. **Week 5 (27-30天)**：动态权限实战、分布式 Session 管理与高级扩展调优。

---

## 📅 第一阶段：安全架构与认证核心 (筑基)

### 第1天：Spring Security 设计哲学：为什么是 Filter？
#### ### 面试题
1. **基础**：Spring Security 的核心原理是什么？（Servlet 过滤器链）。
2. **中级**：什么是 `DelegatingFilterProxy` 和 `FilterChainProxy`？
3. **高级**：Spring Security 的过滤器链（SecurityFilterChain）中有哪些关键过滤器？
4. **源码**：`SecurityContextHolder` 默认的存储策略是什么？（ThreadLocal）。
5. **地狱级**：在非 Servlet 环境（如 Spring WebFlux）下，Spring Security 是如何实现安全控制的？

#### ### 编程题
创建一个 Spring Boot 项目并集成 Spring Security，观察默认生成的密码并在日志中找到过滤器链的加载信息。

---

### 第2天：认证流程全解：从登录到上下文存储
#### ### 面试题
1. **基础**：简述 Spring Security 的认证流程（Authentication 对象的流转）。
2. **中级**：`AuthenticationManager`、`ProviderManager` 和 `AuthenticationProvider` 的关系。
3. **高级**：`UserDetails` 和 `UserDetailsService` 的作用是什么？
4. **源码**：`AbstractAuthenticationProcessingFilter` 的执行逻辑。
5. **地狱级**：如何实现一个多认证源（如同时支持用户名密码、短信验证码、第三方登录）的认证系统？

#### ### 编程题
实现一个自定义的 `UserDetailsService`，从 H2 数据库或内存 Map 中加载用户信息进行认证。

---

### 第3天：密码加密机制：PasswordEncoder
#### ### 面试题
1. **基础**：为什么不能明文存储密码？什么是盐值（Salt）？
2. **中级**：`BCryptPasswordEncoder` 的原理及其优势。
3. **高级**：什么是“密码升级策略”？Spring Security 如何处理旧加密算法的迁移？
4. **源码**：`DelegatingPasswordEncoder` 是如何根据前缀自动选择加密器的？
5. **地狱级**：如何实现自定义的密码验证逻辑（如对接旧系统的非标准 MD5 加密）？

#### ### 编程题
编写代码演示如何使用 BCrypt 进行加密和匹配，并验证即使原始密码相同，加密后的密文也不一致的特性。

---

### 第4天：自定义认证：短信验证码登录实战
#### ### 面试题
1. **基础**：如何自定义一个认证过滤器？
2. **中级**：如何自定义 `AuthenticationToken` 以携带额外信息（如验证码）？
3. **高级**：自定义 `AuthenticationProvider` 处理逻辑。
4. **源码**：`DaoAuthenticationProvider` 的核心验证逻辑。
5. **地狱级**：如何在认证失败后记录失败次数并实现“输错 5 次锁定账号”的功能？

#### ### 编程题
实现一个完整的短信验证码登录功能：包含自定义 Token、Filter 和 Provider，并挂载到安全配置中。

---

### 第5天：认证结果处理：Success & Failure Handler
#### ### 面试题
1. **基础**：认证成功/失败后默认的行为是什么？
2. **中级**：如何自定义成功处理器实现登录后返回 JSON 格式数据（适用于前后端分离）？
3. **高级**：如何处理认证过程中的异常并返回自定义的状态码？
4. **源码**：`SimpleUrlAuthenticationSuccessHandler` 的实现。
5. **地狱级**：如何在成功处理器中实现多终端单点登录（SSO）的逻辑触发？

#### ### 编程题
编写自定义的成功和失败处理器，要求在前后端分离架构下返回标准的 `Result<T>` JSON 数据。

---

### 第6天：登出处理与 Session 管理基础
#### ### 面试题
1. **基础**：如何配置自定义的登出路径和处理器？
2. **中级**：`LogoutHandler` 和 `LogoutSuccessHandler` 的区别。
3. **高级**：Session 并发控制：如何限制同一个账号只能在一个地方登录？
4. **源码**：`SessionManagementFilter` 的工作原理。
5. **地狱级**：在集群环境下，如何保证 Session 的一致性？（Spring Session 简介）。

#### ### 编程题
配置 Session 并发控制，实现“新登录踢掉旧登录”或“限制最多 1 个连接”的功能。

---

### 第7天：第一周复盘：认证体系总结
#### ### 面试题
1. **综合**：请完整画出（或描述）一个用户名密码登录请求在过滤器链中的“旅行路径”。
2. **架构**：对比 Shiro 和 Spring Security 的优劣。
3. **地狱级**：如果你发现生产环境下 Security 上下文莫名其妙丢失，你会从哪些方面（异步线程、线程池隔离等）排查？

#### ### 编程题
总结一份 Spring Security 核心组件交互图。

---

## 📅 第二阶段：授权管理与方法安全 (进阶)

### 第8天：授权机制：谁能访问什么？
#### ### 面试题
1. **基础**：Spring Security 中的权限（Authority）和角色（Role）有什么区别？
2. **中级**：`hasRole` 和 `hasAuthority` 的底层判断逻辑。
3. **高级**：什么是 `AccessDecisionManager` 和 `AccessDecisionVoter`？
4. **源码**：权限拦截器 `FilterSecurityInterceptor` 的执行流程。
5. **地狱级**：如何实现基于层级关系的权限控制（如：管理员包含普通用户的所有权限）？

#### ### 编程题
在配置类中定义多条 URL 匹配规则，演示如何根据不同的角色限制访问。

---

### 第9天：SpEL 表达式在授权中的应用
#### ### 面试题
1. **基础**：什么是 SpEL（Spring Expression Language）？
2. **中级**：如何在配置中使用复杂的 SpEL 表达式？（如：`hasIpAddress`）。
3. **高级**：如何自定义 SpEL 权限表达式？
4. **源码**：`WebSecurityExpressionRoot` 提供的内置方法。
5. **地狱级**：如何实现在 SpEL 中直接调用 Spring Bean 的方法进行权限判断？

#### ### 编程题
实现一个自定义 SpEL 方法 `checkPermission(String code)`，并在配置中通过该方法判断当前用户是否有权访问某个接口。

---

### 第10天：方法级安全：注解驱动
#### ### 面试题
1. **基础**：常用的方法级安全注解有哪些？（@PreAuthorize, @PostAuthorize, @Secured）。
2. **中级**：如何开启方法级安全支持？（@EnableGlobalMethodSecurity）。
3. **高级**：@PreAuthorize 和 @PostAuthorize 的应用场景区别。
4. **源码**：方法级安全是如何通过 Spring AOP 实现的？
5. **地狱级**：@PreFilter 和 @PostFilter 如何对集合类型的入参和出参进行过滤？

#### ### 编程题
在一个 Service 方法上使用 `@PreAuthorize`，限制只有拥有 `OP_DELETE` 权限的用户才能调用，并编写单元测试验证。

---

### 第11天：动态权限控制：从数据库加载资源权限
#### ### 面试题
1. **基础**：为什么通常不建议将权限硬编码在代码或配置中？
2. **中级**：如何实现自定义的 `FilterInvocationSecurityMetadataSource`？
3. **高级**：如何实时动态刷新权限配置（不重启服务）？
4. **地狱级**：在百万级 URL 和权限关系的场景下，如何优化权限匹配的性能？

#### ### 编程题
实现一个动态权限系统：从数据库读取 URL 与 Role 的映射关系，并自定义 `AccessDecisionManager` 进行决策。

---

### 第12天：异常处理：AccessDeniedHandler & AuthenticationEntryPoint
#### ### 面试题
1. **基础**：当未登录用户访问受限资源时，谁来处理？（EntryPoint）。
2. **中级**：当已登录用户权限不足时，谁来处理？（AccessDeniedHandler）。
3. **高级**：如何区分 AJAX 请求和普通页面请求并返回不同的错误信息？
4. **源码**：`ExceptionTranslationFilter` 的捕获与分发逻辑。
5. **地狱级**：如何处理在 Filter 链之外（如拦截器、AOP 层）抛出的安全异常？

#### ### 编程题
编写自定义的 EntryPoint 和 AccessDeniedHandler，要求在异常时返回统一的业务错误码。

---

### 第13天：忽略安全检查的正确姿势
#### ### 面试题
1. **基础**：`web.ignoring()` 和 `http.permitAll()` 的区别。
2. **中级**：静态资源应该如何配置忽略？
3. **高级**：为什么配置了 `permitAll()` 后，过滤器链依然会执行？
4. **地狱级**：如何配置一个完全不经过 Spring Security 过滤器的路径？这对性能有何提升？

#### ### 编程题
在配置中分别演示三种不同的路径放行策略，并说明各自的适用场景。

---

### 第14天：第二周复盘：授权与动态管理总结
#### ### 面试题
1. **综合**：设计一个通用的 RBAC（基于角色的访问控制）数据库模型。
2. **架构**：如何在微服务网关层实现统一的授权校验？
3. **地狱级**：如果你的系统需要支持“数据权限”（如：经理只能看本部门数据），Spring Security 能提供什么支持？（ACL 模块简介）。

#### ### 编程题
总结一份 Spring Security 授权逻辑的流转图。

---

## 📅 第三阶段：OAuth2 与 JWT 深度集成 (核心内核)

### 第15天：OAuth2.0 协议精讲与四种模式
#### ### 面试题
1. **基础**：OAuth2.0 解决了什么问题？它的核心角色有哪些？
2. **中级**：详细描述授权码模式（Authorization Code）的完整流程。
3. **高级**：密码模式、客户端模式和隐式模式的适用场景。
4. **地狱级**：OAuth2.1 相比 2.0 做了哪些重大改进？为什么建议弃用隐式模式和密码模式？

#### ### 编程题
画出授权码模式的时序图，并标注每一步传递的关键参数。

---

### 第16天：Spring Security OAuth2 核心架构
#### ### 面试题
1. **基础**：认证服务器（Authorization Server）和资源服务器（Resource Server）的区别。
2. **中级**：什么是 `ClientDetailsService`？
3. **高级**：`TokenStore` 的常见实现有哪些？（内存, JDBC, Redis, JWT）。
4. **源码**：Spring Security OAuth2（旧版）与新版 Spring Authorization Server 的关系。
5. **地狱级**：如何自定义 Token 的增强信息（如在 JWT 中加入用户 ID、部门信息）？

#### ### 编程题
搭建一个简单的认证服务器，配置一个客户端，并使用 Postman 模拟获取 Token。

---

### 第17天：JWT (JSON Web Token) 深度解析
#### ### 面试题
1. **基础**：JWT 的组成部分：Header, Payload, Signature。
2. **中级**：JWT 是如何保证防篡改的？
3. **高级**：JWT 的优缺点对比（无状态 vs 无法即时撤销）。
4. **源码**：Spring Security 是如何利用 `JwtDecoder` 解析 Token 的？
5. **地狱级**：如何解决 JWT 的续期问题？（双 Token 机制：Access Token + Refresh Token）。

#### ### 编程题
实现一个简单的 JWT 工具类，包含生成、解析和校验逻辑（使用 jjwt 或 nimbus-jose-jwt）。

---

### 第18天：资源服务器集成 JWT 校验
#### ### 面试题
1. **基础**：如何配置资源服务器以支持 JWT 认证？
2. **中级**：什么是 JWS 和 JWE？
3. **高级**：资源服务器如何从认证服务器动态获取公钥进行校验？（JWK 机制）。
4. **源码**：`BearerTokenAuthenticationFilter` 的执行过程。
5. **地狱级**：如何在资源服务器层实现 JWT 的“黑名单”机制（如用户注销后 Token 立即失效）？

#### ### 编程题
将之前的 Spring Boot 项目改造为资源服务器，配置 JWT 校验逻辑，并能从 Token 中解析出权限。

---

### 第19天：单点登录 (SSO) 与 OIDC 原理
#### ### 面试题
1. **基础**：什么是 SSO？什么是 OIDC (OpenID Connect)？
2. **中级**：ID Token 和 Access Token 的区别。
3. **高级**：Spring Security OAuth2 Client 模块如何实现 SSO 登录？
4. **源码**：`OAuth2LoginAuthenticationFilter` 的处理流程。
5. **地狱级**：如何在多个不同域名的系统间实现单点登录和单点注销？

#### ### 编程题
利用 Spring Security OAuth2 Client 整合 GitHub 登录，实现一个社交登录功能。

---

### 第20天：分布式 Session 与认证同步
#### ### 面试题
1. **基础**：为什么分布式环境下需要 Session 共享？
2. **中级**：Spring Session + Redis 的工作原理。
3. **高级**：在分布式环境下，如何处理并发 Session 限制？
4. **地狱级**：如果认证中心返回的 UserDetails 对象非常大，如何优化 Session 存储开销？

#### ### 编程题
集成 Spring Session Data Redis，实现多实例部署下的登录状态共享。

---

### 第21天：第三周复盘：OAuth2 与 JWT 总结
#### ### 面试题
1. **综合**：设计一个微服务架构下的统一身份认证与授权方案（网关鉴权 vs 服务内部鉴权）。
2. **架构**：JWT 泄露了怎么办？有哪些安全防护措施？
3. **地狱级**：如何实现一个支持多租户（Multi-tenancy）的 OAuth2 认证服务器？

#### ### 编程题
总结一份 OAuth2 常见攻击方式及防御策略。

---

## 📅 第四阶段：Web 安全防御与进阶 (进阶)

### 第22天：CSRF (跨站请求伪造) 防御
#### ### 面试题
1. **基础**：CSRF 攻击的原理是什么？
2. **中级**：Spring Security 默认是如何防御 CSRF 的？（CSRF Token）。
3. **高级**：为什么前后端分离/无状态 API 通常可以关闭 CSRF？有哪些潜在风险？
4. **源码**：`CsrfFilter` 的校验逻辑。
5. **地狱级**：如何在不关闭 CSRF 的情况下，为非浏览器客户端提供访问支持？

#### ### 编程题
编写一个简单的 CSRF 攻击页面，观察 Spring Security 如何拦截该请求，并演示如何正确传递 CSRF Token。

---

### 第23天：CORS (跨域资源共享) 配置
#### ### 面试题
1. **基础**：什么是同源策略？什么是 CORS？
2. **中级**：Spring Security 配置 CORS 的正确姿势。
3. **高级**：CORS 预检请求（Preflight/OPTIONS）在 Security 过滤器链中的处理。
4. **地狱级**：为什么在网关层配置了 CORS，后端服务还需要配置吗？

#### ### 编程题
配置 Spring Security 允许跨域，并解决 OPTIONS 请求被拦截导致前端报错的问题。

---

### 第24天：HTTP 安全头与 XSS 防御
#### ### 面试题
1. **基础**：Spring Security 默认开启了哪些 HTTP 安全头？（X-Frame-Options, X-Content-Type-Options 等）。
2. **中级**：什么是内容安全策略 (CSP)？如何配置？
3. **高级**：如何通过 Security 配置防御简单的 XSS 攻击？
4. **地狱级**：如何防御点击劫持（Clickjacking）和中间人攻击（MITM）？

#### ### 编程题
在配置中增加自定义的 Content-Security-Policy，限制脚本只能从受信任的域名加载。

---

### 第25天：记住我 (Remember-Me) 机制
#### ### 面试题
1. **基础**：Remember-Me 的工作原理。
2. **中级**：基于 Cookie 的 Remember-Me 和基于持久化数据库的 Remember-Me 有何区别？
3. **高级**：什么是“Token 序列号”机制？它如何防止 Remember-Me 被盗用？
4. **源码**：`RememberMeAuthenticationFilter` 的执行流程。
5. **地狱级**：Remember-Me 登录后，如何限制某些敏感操作必须重新输入密码？（`isFullyAuthenticated()` 的使用）。

#### ### 编程题
实现基于持久化数据库（JDBC）的 Remember-Me 功能，并观察数据库中 Token 表的变化。

---

### 第26天：第四周复盘：安全防御实战总结
#### ### 面试题
1. **综合**：总结 Spring Security 默认提供的全套防御措施。
2. **架构**：如何对生产环境的 Spring Security 配置进行安全性审计？
3. **地狱级**：如果你的网站遭到了“撞库”攻击，你该如何从 Security 层或业务层进行拦截和预警？

---

## 📅 第五阶段：调优、源码与高级实战 (终极)

### 第27天：Spring Security 性能调优实战
#### ### 面试题
1. **高级**：过滤器链过长对请求耗时的影响如何评估？
2. **地狱级**：如何通过优化权限匹配算法（如使用 Aho-Corasick 算法）提升大规模权限系统的性能？
#### ### 编程题
编写压测脚本对比开启和关闭 Security 对简单接口响应时间的影响。

---

### 第28天：Spring Security 常见生产故障案例分析
#### ### 面试题
1. **高级**：解决 `AuthenticationManager` 无法自动注入的问题。
2. **地狱级**：排查由于上下文传递失败导致的 `@Async` 异步任务权限丢失。
#### ### 编程题
演示如何使用 `DelegatingSecurityContextAsyncTaskExecutor` 解决异步线程池中的上下文丢失问题。

---

### 第29天：高级扩展：自定义权限元数据与投票逻辑
#### ### 面试题
1. **高级**：如何自定义 `AccessDecisionVoter` 实现复杂的业务投票逻辑？
2. **地狱级**：如何实现一个类似于 AWS IAM 的基于资源、动作、条件的细粒度策略引擎？
#### ### 编程题
实现一个“一票否决”制或“多数赞成”制的自定义投票器。

---

### 第30天：终极实战：设计一个金融级统一身份认证与授权中心
#### ### 任务描述
1. **综合设计**：设计一个全能型安全系统：支持多租户、多认证源、动态权限、双向 SSL、全量审计、异地登录预警、多因子认证 (MFA)。要求给出详细的技术架构图。
2. **总教官面试**：回答终极难题：“安全和便捷永远是矛盾的。作为架构师，你如何在‘极致的安全防御’和‘流畅的用户体验’之间寻找平衡点？”

---

**总教官寄语**：恭喜你，安全专家！30 天的时间，你已经建立起了一道坚不可摧的安全长城。Spring Security 并不难，难的是对 Web 安全漏洞的深刻理解和对过滤器链精妙设计的把握。记住，安全无小事，守住代码的最后一道防线，是你作为高级架构师的最高荣耀！
