package com.example.springqa.Q16_MvcRequestFlow;

import org.springframework.stereotype.Component;

/**
 * <h1>Q16：Spring MVC 请求处理流程</h1>
 */
@Component
public class MvcRequestFlowDemo {

    public String runDemo() {
        return "=== Q16: MVC 请求流程 ===\n\n" +
            "HTTP 请求 → Filter 链 → DispatcherServlet.doDispatch()\n" +
            "  │\n" +
            "  ├── ① HandlerMapping.getHandler(request)\n" +
            "  │    └── 返回 HandlerExecutionChain (Handler + Interceptors)\n" +
            "  │\n" +
            "  ├── ② HandlerAdapter.handle(request, response, handler)\n" +
            "  │    └── 适配不同 Handler 类型（Controller/HttpRequestHandler/Servlet）\n" +
            "  │\n" +
            "  ├── ③ 参数解析（HandlerMethodArgumentResolver）\n" +
            "  │    → @RequestParam / @RequestBody / @PathVariable\n" +
            "  │\n" +
            "  ├── ④ Controller 方法执行（反射）\n" +
            "  │\n" +
            "  ├── ⑤ 返回值处理（HandlerMethodReturnValueHandler）\n" +
            "  │    → @ResponseBody → HttpMessageConverter 序列化\n" +
            "  │    → String → ViewResolver → View.render()\n" +
            "  │\n" +
            "  └── ⑥ 响应返回\n\n" +
            "【为什么需要 HandlerAdapter？】\n" +
            "适配器模式。不同 Handler 有不同调用方式，DispatcherServlet 不应感知这些细节。\n" +
            "新增 Handler 类型只需增加 Adapter——开闭原则。\n\n" +
            "【为什么需要 ViewResolver？】\n" +
            "解耦\"返回什么\"和\"怎么渲染\"。Controller 只返回逻辑名，切换视图技术只需换 Resolver。\n";
    }
}
