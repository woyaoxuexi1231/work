package com.example.springqa.Q16_MvcRequestFlow.era.era2_interface;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Properties;

/**
 * <h1>第二代：Spring MVC 2.x — Controller 接口（2004）</h1>
 *
 * <pre>
 * ┌─────────────────────────────────────────────────┐
 * │  涉及的 MVC 组件：                                │
 * │                                                 │
 * │  HandlerMapping    ✅ SimpleUrlHandlerMapping    │
 * │                    → URL "/era2/user" → Era2ControllerInterface │
 * │                                                 │
 * │  HandlerAdapter    ✅ SimpleControllerHandlerAdapter │
 * │                    → 调 handleRequest(req, resp) │
 * │                                                 │
 * │  参数解析           ❌ 没有 — 还是手写 getParameter │
 * │                                                 │
 * │  返回值处理         ✅ ModelAndView               │
 * │                    → ModelAndViewMethodReturnValueHandler │
 * │                                                 │
 * │  ViewResolver      ✅ 参与                       │
 * │                    → 把 "era2-view" 解析为 View   │
 * │                                                 │
 * │  HttpMessageConverter ❌ 没有                     │
 * └─────────────────────────────────────────────────┘
 * </pre>
 *
 * <p>访问: http://localhost:8080/era2/user?id=1</p>
 */
public class Era2ControllerInterface implements Controller {

    @Override
    public ModelAndView handleRequest(HttpServletRequest req, HttpServletResponse resp) {
        System.out.println("  [Era2] SimpleControllerHandlerAdapter → handleRequest(req, resp)");

        String idStr = req.getParameter("id");  // ← 还是手写 getParameter！
        Long id = idStr != null ? Long.parseLong(idStr) : 0L;
        String name = id == 1 ? "Alice" : "Unknown";

        ModelAndView mav = new ModelAndView("era2-view");
        mav.addObject("id", id);
        mav.addObject("name", name);
        mav.addObject("handlerMapping", "SimpleUrlHandlerMapping");
        mav.addObject("handlerAdapter", "SimpleControllerHandlerAdapter");
        mav.addObject("params", "手写 req.getParameter() — 没有自动解析");
        mav.addObject("returnValue", "ModelAndView → ModelAndViewMethodReturnValueHandler → ViewResolver");
        return mav;
    }

    @Configuration
    public static class Config {
        @Bean
        public Era2ControllerInterface era2Controller() { return new Era2ControllerInterface(); }

        @Bean
        public SimpleUrlHandlerMapping era2Mapping(Era2ControllerInterface controller) {
            SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
            Properties urls = new Properties();
            urls.setProperty("/era2/user", "era2Controller");
            mapping.setMappings(urls);
            mapping.setOrder(0);
            return mapping;
        }
    }
}
