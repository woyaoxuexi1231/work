package com.example.springqa.Q16_MvcRequestFlow;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * <h1>Q16：Spring MVC 请求处理流程</h1>
 *
 * <h2>面试点</h2>
 * <p>一个 HTTP 请求从 DispatcherServlet 到返回结果，经过了哪些组件？</p>
 *
 * <h2>完整流程图</h2>
 * <pre>
 * HTTP 请求
 *   ↓
 * 1. Filter 链（Servlet 规范层面 —— 详见 Q18）
 *   ↓
 * 2. DispatcherServlet.doDispatch()
 *   ↓
 * 3. HandlerMapping.getHandler(request)
 *    → 根据 URL 找到 HandlerMethod（Controller 方法）
 *    → 返回 HandlerExecutionChain（Handler + Interceptors）
 *   ↓
 * 4. HandlerAdapter.supports(handler) → handle(request, response, handler)
 *    → 适配不同的 Handler 类型（Controller / HttpRequestHandler / Servlet）
 *    → 调用 Controller 方法
 *   ↓
 * 5. 参数解析（HandlerMethodArgumentResolver）
 *    → @RequestParam / @RequestBody / @PathVariable 等
 *   ↓
 * 6. Controller 方法执行
 *   ↓
 * 7. 返回值处理（HandlerMethodReturnValueHandler）
 *    → @ResponseBody → HttpMessageConverter 序列化
 *    → View 名称 → ViewResolver 解析
 *   ↓
 * 8. ViewResolver.resolveViewName()
 *    → View.render()  (JSP / Thymeleaf / ...)
 *   ↓
 * 9. Interceptor.postHandle()
 *   ↓
 * 10. HTTP 响应返回
 * </pre>
 *
 * <h2>三大核心组件</h2>
 * <pre>
 * HandlerMapping  — "谁来处理？"
 *   URL → HandlerMethod 的映射。
 *
 * HandlerAdapter  — "怎么调用？"
 *   Handler 的类型是多样的，HandlerAdapter 负责"适配"不同的 Handler。
 *
 * ViewResolver     — "怎么渲染？"
 *   逻辑视图名 → 实际 View 对象。
 * </pre>
 *
 * <h2>Spring 为什么这样设计？</h2>
 *
 * <h3>为什么需要 HandlerAdapter？</h3>
 * <p>这是"适配器模式"的经典应用。不同 Handler（Controller 接口、@Controller 注解、
 * HttpRequestHandler）有不同的调用方式。如果没有 HandlerAdapter，
 * DispatcherServlet 就需要知道所有 Handler 类型的调用方式——违反"开闭原则"。</p>
 *
 * <h3>为什么需要 ViewResolver？</h3>
 * <p>解耦"控制器返回什么"和"怎么渲染"。
 * 切换视图技术只需更换 ViewResolver。</p>
 *
 * @author Spring Interview QA
 */
public class MvcRequestFlowDemo {

    public static void main(String[] args) {
        System.out.println("========== Q16: MVC 请求流程 Demo ==========\n");

        System.out.println("Spring MVC 请求处理流程概述：\n");

        System.out.println(
            "HTTP 请求 → Tomcat\n" +
            "  ↓\n" +
            "Filter 链（Servlet 规范）\n" +
            "  ↓\n" +
            "DispatcherServlet.doDispatch(request, response)\n" +
            "  │\n" +
            "  ├── ① HandlerMapping.getHandler(request)\n" +
            "  │    └── 返回 HandlerExecutionChain {\n" +
            "  │          handler: HandlerMethod(Controller.method)\n" +
            "  │          interceptors: [HandlerInterceptor1, ...]\n" +
            "  │        }\n" +
            "  │\n" +
            "  ├── ② HandlerAdapter.supports(handler) + handle(...)\n" +
            "  │    └── RequestMappingHandlerAdapter {\n" +
            "  │          argumentResolvers: [\n" +
            "  │            RequestParamMethodArgumentResolver,\n" +
            "  │            PathVariableMethodArgumentResolver,\n" +
            "  │            RequestResponseBodyMethodProcessor,\n" +
            "  │            ...\n" +
            "  │          ]\n" +
            "  │          returnValueHandlers: [\n" +
            "  │            RequestResponseBodyMethodProcessor (@ResponseBody),\n" +
            "  │            ViewNameMethodReturnValueHandler (返回视图名),\n" +
            "  │            ...\n" +
            "  │          ]\n" +
            "  │        }\n" +
            "  │\n" +
            "  ├── ③ 调用 Controller 方法（反射）\n" +
            "  │    └── Object result = method.invoke(controller, resolvedArgs)\n" +
            "  │\n" +
            "  ├── ④ 处理返回值\n" +
            "  │    ├── @ResponseBody → HttpMessageConverter 序列化\n" +
            "  │    └── String → ViewResolver → View.render()\n" +
            "  │\n" +
            "  └── ⑤ 响应返回"
        );

        System.out.println("\nSpring 这样设计的原因：");
        System.out.println("  • HandlerMapping + HandlerAdapter 双抽象层");
        System.out.println("    → 适配器模式：让 DispatcherServlet 对 Handler 类型无感知");
        System.out.println("  • 策略模式：HandlerMapping / HandlerAdapter / ViewResolver");
        System.out.println("    都是策略接口，可以自由切换实现");
        System.out.println("  • 单一职责：每个组件只做一件事");
        System.out.println("    DispatcherServlet 只负责\"调度\"，不负责\"执行\"");

        System.out.println("\n========== Demo 结束 ==========");
    }
}
