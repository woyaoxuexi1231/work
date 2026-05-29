package com.example.springqa.Q16_MvcRequestFlow.era;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.Map;

/**
 * 为 era2 和 era3 提供一个简单的 ViewResolver——把视图名解析为纯文本输出。
 * era1 是纯 Servlet，不需要这个。era4 是 @RestController，也不需要。
 */
@Configuration
public class EraViewConfig {

    @Bean
    public ViewResolver eraViewResolver() {
        View view = (View) (model, request, response) -> {
            response.setContentType("text/plain;charset=UTF-8");
            PrintWriter w = response.getWriter();
            w.println("══════════════════════════════════");
            w.println("  ViewResolver 解析结果");
            w.println("══════════════════════════════════");
            w.println();
            for (Map.Entry<String, ?> e : model.entrySet()) {
                w.println("  " + e.getKey() + " = " + e.getValue());
            }
            w.println();
            w.println("→ ViewResolver 把逻辑视图名 → View → View.render() → 写入 response");
        };

        return (viewName, locale) -> {
            System.out.println("  [ViewResolver] 视图名='" + viewName + "' → 返回 View");
            return view;
        };
    }
}
