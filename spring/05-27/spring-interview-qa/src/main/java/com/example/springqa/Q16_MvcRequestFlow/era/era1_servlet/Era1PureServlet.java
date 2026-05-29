package com.example.springqa.Q16_MvcRequestFlow.era.era1_servlet;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * <h1>第一代：纯 Servlet（1998）</h1>
 *
 * <pre>
 * ┌─────────────────────────────────────────┐
 * │  涉及的 MVC 组件：                        │
 * │                                         │
 * │  HandlerMapping    ❌ 没有               │
 * │  HandlerAdapter    ❌ 没有               │
 * │  参数解析           ❌ 没有 — 手写 getParameter │
 * │  返回值处理         ❌ 没有 — 手写 out.println │
 * │  ViewResolver      ❌ 没有               │
 * │  HttpMessageConverter ❌ 没有             │
 * │                                         │
 * │  请求处理：Tomcat → Servlet.service() → doGet() → 手写一切  │
 * └─────────────────────────────────────────┘
 * </pre>
 *
 * <p>访问: http://localhost:8080/era1/user?id=1</p>
 */
public class Era1PureServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        System.out.println("  [Era1] 纯 Servlet — 没有任何 MVC 组件参与");

        String idStr = req.getParameter("id");
        if (idStr == null || idStr.isEmpty()) { resp.sendError(400); return; }
        Long id = Long.parseLong(idStr);
        String name = id == 1 ? "Alice" : "Unknown";

        resp.setContentType("text/html;charset=UTF-8");
        PrintWriter out = resp.getWriter();
        out.println("<!DOCTYPE html><html><body>");
        out.println("<h1>Era1: Pure Servlet (1998)</h1>");
        out.println("<p>ID: " + id + " | Name: " + name + "</p>");
        out.println("<p style='color:red'>没有 HandlerMapping、没有参数自动解析、没有 ViewResolver</p>");
        out.println("<p>一切全靠 <b>手写</b></p>");
        out.println("</body></html>");
    }

    @Configuration
    public static class Config {
        @Bean
        public ServletRegistrationBean<Era1PureServlet> era1Servlet() {
            ServletRegistrationBean<Era1PureServlet> bean =
                    new ServletRegistrationBean<>(new Era1PureServlet(), "/era1/user");
            bean.setLoadOnStartup(1);
            return bean;
        }
    }
}
