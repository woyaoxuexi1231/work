package com.example.springqa.Q16_MvcRequestFlow.era;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.config.annotation.ViewResolverRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.util.Map;

@Configuration
public class EraViewConfig implements WebMvcConfigurer {

    @Override
    public void configureViewResolvers(ViewResolverRegistry registry) {
        registry.viewResolver((viewName, locale) -> {
            System.out.println("  [EraViewResolver] 视图名='" + viewName + "' → 返回 View");
            return new View() {
                @Override
                public String getContentType() {
                    return "text/plain;charset=UTF-8";
                }

                @Override
                public void render(Map<String, ?> model, HttpServletRequest request,
                                   HttpServletResponse response) throws Exception {
                    response.setContentType("text/plain;charset=UTF-8");
                    PrintWriter w = response.getWriter();
                    w.println("══════════════════════════════════");
                    w.println("  ViewResolver 解析结果：'" + viewName + "'");
                    w.println("══════════════════════════════════");
                    w.println();
                    for (Map.Entry<String, ?> e : model.entrySet()) {
                        w.println("  " + e.getKey() + " = " + e.getValue());
                    }
                    w.println();
                    w.println("→ ViewResolver 把逻辑视图名 → View → View.render()");
                }
            };
        });
    }
}
