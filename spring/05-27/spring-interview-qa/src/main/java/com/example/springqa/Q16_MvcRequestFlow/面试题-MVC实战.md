# Spring MVC 面试：从真实开发场景反推理解深度

> 不考"DispatcherServlet 有哪些组件"——那是培训班的问题。
> 考的是你**排查过什么实际问题**，以及**做技术决策时考虑了哪些因素**。

---

## 1. @RequestBody 突然读不到了

**场景：** 你写了一个 Filter 记录请求日志，把 request body 读出来打了一行 log。上线后所有 Controller 的 `@RequestBody` 全部拿到 null。为什么？怎么修？

**考察点：** 知不知道 HttpServletRequest 的 InputStream **只能读一次**。Filter 读完了，流到末尾，Spring 再读就是空的。修法：`ContentCachingRequestWrapper` 包一层，但要注意大文件上传会撑爆内存——所以实际项目里通常只对 `Content-Type: application/json` 且 Content-Length < 阈值（如 4KB）的请求才缓存 body。

**延伸：** 为什么不用 Interceptor 而要在 Filter 里做？因为 Interceptor 在 DispatcherServlet 之后执行，此时 body 可能已经被 ArgumentResolver 消费了。**拿到最原始的请求数据必须在 Filter 层。**

---

## 2. 全局异常处理漏了两种情况

**场景：** 你写了一个 `@ControllerAdvice` + `@ExceptionHandler(Exception.class)`，以为能兜住所有异常。但线上发现有两种异常就是捕获不到——返回的是 Tomcat 默认的 500 错误页，不是你的 JSON 格式。是哪两种？为什么？

**考察点：**
- **第一种：** 异常在进入 DispatcherServlet 之前就抛了——比如 Filter 里的异常。此时请求还没到 DispatcherServlet，`@ControllerAdvice` 根本不会被触发。
- **第二种：** 响应已经 commit 了才抛异常——比如下载大文件时磁盘满了抛 IOException，但 HTTP 响应头（状态码 200）已经发出去了。`@ControllerAdvice` 能捕获异常，但改不了已提交的响应。

**修法：** Filter 层异常 → Filter 内部 try-catch 手动写 JSON 错误。已 commit 的异常 → 加监控告警，没法靠代码修复。

---

## 3. 接口突然返回了 XML

**场景：** 你的项目前后端分离，Controller 一直返回 JSON。某天前端突然说 `/users/1` 返回了 XML。排查发现前端新同事在请求里加了个 header：`Accept: application/xml`。为什么 Spring 就乖乖返回了 XML？

**考察点：** Spring MVC 的**内容协商（Content Negotiation）**。`HttpMessageConverter` 链选择时不仅看返回值类型，还看请求的 `Accept` header。如果 classpath 里有 `jackson-dataformat-xml`（可能是不相关的依赖带进来的），Spring Boot 自动注册了 `MappingJackson2XmlHttpMessageConverter`。它发现 Accept 里有 `application/xml` ——它就胜出了。

**修法：** 全局关掉内容协商 `spring.mvc.contentnegotiation.favor-parameter=false`。关键接口加 `@RequestMapping(produces = "application/json")` 做防御。

---

## 4. 统一响应包装——某个接口想跳过

**场景：** 你写了一个全局的 `ResponseBodyAdvice`，把 Controller 的返回值统一包成 `{code: 200, message: "success", data: ...}`。后来有个第三方回调接口，要求固定格式——不能被包装。你怎么让这个接口跳过全局包装？

**考察点：** `ResponseBodyAdvice` 的 `supports(MethodParameter returnType, ...)` 方法。它能拿到 Controller 方法的反射信息。自定义一个 `@NoWrapper` 注解，标在不需要包装的方法上，`supports()` 里检查这个方法有没有 `@NoWrapper`——有就返回 false 跳过。

**为什么不用 URL 白名单？** 因为加新接口的人不知道有白名单这回事——注解是自描述的，谁加谁知道。

---

## 5. 前端多传了一个字段，后端就 400

**场景：** Controller 接收 `@RequestBody CreateUserRequest req`。前端传的 JSON 里多了一个 `"extraField": "xxx"`——这个字段在后端类里不存在。结果后端直接返回 400。为什么？你倾向怎么处理——拒绝还是忽略？

**考察点：** Jackson 默认 `FAIL_ON_UNKNOWN_PROPERTIES = true`——JSON 里出现未知字段就抛 `UnrecognizedPropertyException`，最终变成 400。

**选忽略：** `spring.jackson.deserialization.fail-on-unknown-properties=false`。理由：**前后端版本不一致是常态**。后端先上线加了新字段，前端还没更新——如果拒绝，旧版本前端全线 400。忽略未知字段让 API 向后兼容。这就是鲁棒性原则：对自己发送的东西要严格，对接收的东西要宽容。

---

## 6. @ResponseBody 忘了加——返回值被当成视图名

**场景：** Controller 类上写了 `@Controller`（不是 `@RestController`），方法没写 `@ResponseBody`，返回了一个 String。你期望返回纯文本，但实际返回了 404。为什么？

**考察点：** 没有 `@ResponseBody` 时，Spring 的 `ViewNameMethodReturnValueHandler` 把 String 当成**视图名**，交给 `ViewResolver` 去 `/WEB-INF/views/xxx.jsp` 找 JSP 文件——找不到 → 404。

**`@ResponseBody` 和 `ViewResolver` 不是二选一，是链式处理：** `RequestResponseBodyMethodProcessor` 先检查有没有 `@ResponseBody` 注解——有就处理并标记 `mavContainer.setRequestHandled(true)`。后面的 `ViewNameMethodReturnValueHandler` 看到这个标记直接跳过。如果没标记，就按视图名处理。

**教训：** 用 `@RestController`（= `@Controller` + `@ResponseBody`），整个类的所有方法默认都走 JSON——永远不会犯"返回值被当成视图名"的错误。

---

## 7. HandlerInterceptor 拦截了不该拦截的请求

**场景：** 你写了一个 Token 校验 Interceptor，注册为全局拦截。结果登录接口也被拦截了。怎么让登录接口跳过校验？

**考察点：** 两种方式，各有利弊——
- **URL 模式排除：** `excludePathPatterns("/login", "/register")`。简单直接，但路径多了不好维护。路径改了容易漏改。
- **注解排除：** 自定义 `@NoAuth` 注解。在 `preHandle()` 里检查 handler 是不是 `HandlerMethod`，是就检查方法上有没有 `@NoAuth` 注解。优点是自描述——加新接口的人自己决定要不要鉴权。缺点是比 URL 排除稍复杂。

**实际项目我倾向注解排除。** 因为权限是业务逻辑的一部分，应该跟着代码走，不应该放在全局配置里。

---

## 8. 大文件上传导致 OOM

**场景：** 用户上传一个 500MB 的文件，你的 Controller 用 `@RequestBody MultipartFile file` 接收。结果服务直接 OOM 了。为什么？怎么处理大文件上传？

**考察点：** `@RequestBody` 和 `HttpMessageConverter` 会把整个请求体读进内存。500MB 文件 → 500MB 堆内存 → OOM。

**修法：** Spring Boot 配置 `spring.servlet.multipart.max-file-size=10MB` 限制大小。对于超大文件：不用 `@RequestBody`，用 `HttpServletRequest.getInputStream()` 流式读取，边读边写磁盘或对象存储。或者用 `@RequestParam MultipartFile`（它背后有临时文件机制，超过阈值自动写磁盘）。

**更深一层：** Spring Boot 的 `spring.servlet.multipart.file-size-threshold` 控制多大之后写临时文件——默认 0 即全部写临时文件。`multipart.max-file-size` 控制单个文件上限。`multipart.max-request-size` 控制整个请求上限。

---

## 9. Controller 方法里的参数有时候绑定不上

**场景：** Controller 方法签名是 `getUser(@RequestParam Long id)`。前端用 POST JSON 传 `{"id": 1}`，绑定不上——id 是 null。为什么？

**考察点：** `@RequestParam` 从**查询参数或表单**取值（`request.getParameter("id")`），不读 request body。POST JSON 的 body 需要 `@RequestBody` 来读。混淆了这两种注解是新人最常见的错误。

**规则：** URL 上的参数（`/users?name=xxx`）→ `@RequestParam`。URL 路径里的参数（`/users/{id}`）→ `@PathVariable`。JSON body 里的参数 → `@RequestBody`。表单提交 → `@RequestParam`（也可以不写注解，Spring 自动按名称匹配）。

---

## 10. 项目同时引入了 JSON 和 Protobuf 的 Converter——选哪个？

**场景：** 你的项目需要同时支持 JSON 和 Protobuf 格式。你引入了两个 Converter。当一个请求的 Accept 是 `*/*` 时，Spring 会选哪个？怎么控制优先级？

**考察点：** HttpMessageConverter 链是按**注册顺序**遍历的——第一个 `canWrite()` 返回 true 的胜出。注册顺序由 Spring Boot 的自动配置决定，通常 JSON 的 Converter 排在前面。但你不应该依赖隐式顺序。

**控制方式：** 用 `@RequestMapping(produces = "application/json")` 精确指定。或者重写 `WebMvcConfigurer.configureMessageConverters()` 手动控制 Converter 列表的顺序。如果需要根据请求动态选择——实现自定义的 `HttpMessageConverter`，在 `canWrite()` 里写你的选择逻辑。

---

## 总结：面试官到底在考什么？

| 表面问题 | 实际考察 |
|---------|---------|
| @RequestBody 读不到数据 | 你知道 InputStream 只能读一次吗？知道 ContentCachingRequestWrapper 吗？ |
| 全局异常漏了两种情况 | 你知道 Filter → DispatcherServlet → Controller 的执行顺序吗？知道 response committed 是什么意思吗？ |
| 接口突然返回 XML | 你知道内容协商吗？知道 Accept header 会触发不同的 Converter 吗？ |
| 统一包装跳过某个接口 | 你读过 ResponseBodyAdvice 的源码吗？知道 supports() 能拿到什么信息吗？ |
| 多传字段就 400 | 你知道 Jackson 的 FAIL_ON_UNKNOWN_PROPERTIES 吗？你理解前后端版本不一致是常态吗？ |
| 忘了 @ResponseBody 就 404 | 你知道 String 返回值的两种含义吗？知道 @RestController 和 @Controller 的区别不只是少写一个注解吗？ |
| 大文件 OOM | 你知道 multipart 配置吗？知道 InputStream 流式读取吗？ |
