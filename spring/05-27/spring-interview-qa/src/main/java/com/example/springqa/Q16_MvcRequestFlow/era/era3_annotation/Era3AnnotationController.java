package com.example.springqa.Q16_MvcRequestFlow.era.era3_annotation;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * <h1>第三代：Spring MVC 3.0 — 注解驱动（2009）</h1>
 *
 * <pre>
 * ┌──────────────────────────────────────────────────┐
 * │  涉及的 MVC 组件：                                 │
 * │                                                  │
 * │  HandlerMapping    ✅ RequestMappingHandlerMapping │
 * │                    → 启动时扫描 @RequestMapping → 建立 URL→HandlerMethod 映射表 │
 * │                                                  │
 * │  HandlerAdapter    ✅ RequestMappingHandlerAdapter │
 * │                    → 参数解析 + 反射调用 + 返回值处理 │
 * │                                                  │
 * │  参数解析           ✅ PathVariableMethodArgumentResolver │
 * │                    → 从 URL "/users/1" 提取 id=1，自动转 Long │
 * │                                                  │
 * │  返回值处理         ✅ ViewNameMethodReturnValueHandler │
 * │                    → 返回 String → 当视图名 → ViewResolver │
 * │                                                  │
 * │  ViewResolver      ✅ 参与                        │
 * │                    → 把 "era3-view" 解析为 View    │
 * │                                                  │
 * │  HttpMessageConverter ❌ 没有（返回视图，不是 JSON） │
 * └──────────────────────────────────────────────────┘
 * </pre>
 *
 * <p>访问: http://localhost:8080/era3/users/1</p>
 */
@Controller
@RequestMapping("/era3")
public class Era3AnnotationController {

    @GetMapping("/users/{id}")
    public String getUser(@PathVariable Long id, Model model) {
        System.out.println("  [Era3] RequestMappingHandlerAdapter → 参数解析 + 反射调用");

        String name = id == 1 ? "Alice" : "Unknown";

        model.addAttribute("id", id);
        model.addAttribute("name", name);
        model.addAttribute("handlerMapping", "RequestMappingHandlerMapping — 启动时扫描 @RequestMapping");
        model.addAttribute("handlerAdapter", "RequestMappingHandlerAdapter — 参数解析+反射调用+返回值处理");
        model.addAttribute("argumentResolver", "PathVariableMethodArgumentResolver — URL '{id}' → Long id");
        model.addAttribute("returnValueHandler", "ViewNameMethodReturnValueHandler — String → ViewResolver");
        model.addAttribute("viewResolver", "参与 — 把 'era3-view' 解析为 View");

        return "era3-view";  // 视图名 → ViewResolver
    }
}
