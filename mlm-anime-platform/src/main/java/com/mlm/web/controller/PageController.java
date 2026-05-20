package com.mlm.web.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 页面路由控制器 — 返回 Thymeleaf 模板视图
 * <p>
 * 流水线不是独立页面，进入项目详情页即是流水线操作界面。
 */
@Controller
public class PageController {

    /** 首页 */
    @GetMapping("/")
    public String index() { return "index"; }

    /** 项目列表 */
    @GetMapping("/projects")
    public String projects() { return "projects"; }

    /** 项目详情（即该项目的流水线） */
    @GetMapping("/projects/{id}")
    public String projectDetail(@PathVariable Long id, Model model) {
        model.addAttribute("projectId", id);
        return "project-detail";
    }

    /** 资源库页 */
    @GetMapping("/resources")
    public String resources() { return "resources"; }

    /** 模型配置管理页 */
    @GetMapping("/models")
    public String models() { return "models"; }

    /** favicon 兜底 */
    @GetMapping("/favicon.ico")
    public void favicon(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
