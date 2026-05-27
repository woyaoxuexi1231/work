package com.example.springqa.Q18_InterceptorFilter;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * <h1>Q18：拦截器与过滤器 — HandlerInterceptor vs Filter</h1>
 *
 * <h2>面试点</h2>
 * <ul>
 *   <li>HandlerInterceptor 和 Filter 的区别？执行顺序？</li>
 *   <li>如果需要在 Controller 方法执行前统一校验 token，用哪个更合适？</li>
 * </ul>
 *
 * <h2>核心对比</h2>
 * <pre>
 * | 特性      | Filter (Servlet 规范)  | HandlerInterceptor (Spring MVC) |
 * |----------|------------------------|--------------------------------|
 * | 规范     | javax.servlet          | Spring MVC 框架                 |
 * | 容器     | Servlet 容器管理        | Spring IOC 容器管理              |
 * | 依赖注入  | 需要额外配置            | 可以 @Autowired                 |
 * | 拦截范围  | 所有请求（含静态资源）  | 只拦截进入 DispatcherServlet 的   |
 * | 细粒度   | 只能按 URL 模式         | 可以按 HandlerMethod 控制        |
 * </pre>
 *
 * <h2>执行顺序</h2>
 * <pre>
 * Filter.doFilter() 前置
 *   → DispatcherServlet
 *     → HandlerInterceptor.preHandle()
 *       → Controller 方法
 *     → HandlerInterceptor.postHandle()
 *     → View 渲染
 *     → HandlerInterceptor.afterCompletion()
 * Filter.doFilter() 后置
 * </pre>
 *
 * <h2>Token 校验用哪个？</h2>
 * <p>推荐 HandlerInterceptor：可以注入 Spring Bean、知道哪个方法被调用、
 * 可以做细粒度权限控制。</p>
 *
 * <h2>Spring 为什么同时保留两者？</h2>
 * <p>不同层次：Filter 是 Servlet 容器层（编码、CORS、安全头），
 * Interceptor 是 Spring MVC 层（认证、权限、日志）。</p>
 *
 * @author Spring Interview QA
 */
public class InterceptorFilterDemo {

    public static void main(String[] args) {
        System.out.println("========== Q18: 拦截器 vs 过滤器 Demo ==========\n");

        System.out.println("执行顺序示意图:");
        System.out.println("============================================================");
        System.out.println("|  Filter.preHandle()");
        System.out.println("|    ├── 可以修改 request/response（如设置编码）");
        System.out.println("|    ├── 可以决定是否放行（chain.doFilter）");
        System.out.println("|    └── DispatcherServlet");
        System.out.println("|          ├── HandlerInterceptor.preHandle(request, response, handler)");
        System.out.println("|          │     ├── handler 参数提供 Controller 方法信息");
        System.out.println("|          │     ├── return false → 中断请求");
        System.out.println("|          │     └── return true  → 继续");
        System.out.println("|          ├── Controller.method()");
        System.out.println("|          ├── HandlerInterceptor.postHandle(request, response, handler, mv)");
        System.out.println("|          │     └── 可以修改 ModelAndView");
        System.out.println("|          ├── View.render()");
        System.out.println("|          └── HandlerInterceptor.afterCompletion(request, response, handler, ex)");
        System.out.println("|                └── 无论成功/异常都会执行（类似 finally）");
        System.out.println("|  Filter.postHandle()");
        System.out.println("============================================================");

        System.out.println("\n--- Token 校验推荐实现（HandlerInterceptor） ---");
        System.out.println("@Component");
        System.out.println("class TokenInterceptor implements HandlerInterceptor {");
        System.out.println("    @Autowired");
        System.out.println("    private TokenService tokenService;  // 可以注入 Spring Bean");
        System.out.println();
        System.out.println("    @Override");
        System.out.println("    public boolean preHandle(HttpServletRequest request,");
        System.out.println("                             HttpServletResponse response,");
        System.out.println("                             Object handler) {");
        System.out.println("        // 1. 跳过非 Controller 方法（静态资源）");
        System.out.println("        if (!(handler instanceof HandlerMethod)) return true;");
        System.out.println("        // 2. 跳过不需要鉴权的方法");
        System.out.println("        HandlerMethod hm = (HandlerMethod) handler;");
        System.out.println("        if (hm.hasMethodAnnotation(NoAuth.class)) return true;");
        System.out.println("        // 3. 校验 token");
        System.out.println("        String token = request.getHeader(\"Authorization\");");
        System.out.println("        if (token == null || !tokenService.validate(token)) {");
        System.out.println("            response.setStatus(401);");
        System.out.println("            response.getWriter().write(\"{\\\"error\\\":\\\"Unauthorized\\\"}\");");
        System.out.println("            return false;  // 中断请求");
        System.out.println("        }");
        System.out.println("        request.setAttribute(\"currentUser\", tokenService.getUser(token));");
        System.out.println("        return true;  // 放行");
        System.out.println("    }");
        System.out.println("}");

        System.out.println("\n--- Filter 的适用场景 ---");
        System.out.println("@Component");
        System.out.println("class CorsFilter implements Filter {");
        System.out.println("    @Override");
        System.out.println("    public void doFilter(ServletRequest req, ServletResponse res,");
        System.out.println("                         FilterChain chain) {");
        System.out.println("        HttpServletResponse response = (HttpServletResponse) res;");
        System.out.println("        response.setHeader(\"Access-Control-Allow-Origin\", \"*\");");
        System.out.println("        chain.doFilter(req, res);");
        System.out.println("    }");
        System.out.println("}");
        System.out.println();
        System.out.println("Filter 适合: 编码设置、CORS、XSS过滤、安全头等\"基础设施\"层面");

        System.out.println("\n========== Demo 结束 ==========");
    }
}
