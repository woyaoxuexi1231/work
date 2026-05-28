package com.example.springqa.Q18_InterceptorFilter;

import org.springframework.stereotype.Component;

/**
 * <h1>Q18：拦截器与过滤器 — HandlerInterceptor vs Filter</h1>
 */
@Component
public class InterceptorFilterDemo {

    public String runDemo() {
        return "=== Q18: 拦截器 vs 过滤器 ===\n\n" +
            "执行顺序:\n" +
            "  Filter.doFilter() 前置\n" +
            "    → DispatcherServlet\n" +
            "      → HandlerInterceptor.preHandle()\n" +
            "        → Controller 方法\n" +
            "      → HandlerInterceptor.postHandle()\n" +
            "      → View 渲染\n" +
            "      → HandlerInterceptor.afterCompletion()\n" +
            "  Filter.doFilter() 后置\n\n" +
            "对比:\n" +
            "  | 特性     | Filter (Servlet 规范) | HandlerInterceptor (Spring MVC) |\n" +
            "  | 依赖注入  | 需要额外配置            | 可以 @Autowired                 |\n" +
            "  | 拦截范围  | 所有请求（含静态资源）  | 只拦截 DispatcherServlet 中的    |\n" +
            "  | 细粒度   | 只能按 URL 模式         | 可以按 HandlerMethod 控制        |\n\n" +
            "Token 校验用哪个？\n" +
            "推荐 HandlerInterceptor:\n" +
            "  1. 可注入 TokenService\n" +
            "  2. 知道哪个 Controller 方法被调用\n" +
            "  3. 可通过注解标记排除（如 @NoAuth）\n" +
            "  4. 校验失败可直接写 JSON 错误响应\n\n" +
            "【Spring 为什么同时保留两者？】\n" +
            "Filter 是 Servlet 容器层（编码/CORS/安全头），Interceptor 是 MVC 层（认证/权限/日志）。\n" +
            "关注点分离——基础设施不依赖 Spring，业务逻辑享受 Spring 便利。\n";
    }
}
