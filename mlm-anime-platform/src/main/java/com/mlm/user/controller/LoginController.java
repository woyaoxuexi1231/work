package com.mlm.user.controller;

import com.mlm.common.dto.ApiResult;
import com.mlm.user.entity.User;
import com.mlm.user.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/**
 * 登录/鉴权控制器
 */
@Controller
public class LoginController {

    private static final Logger log = LoggerFactory.getLogger(LoginController.class);
    private static final String SESSION_USER_KEY = "loginUser";

    private final UserService userService;

    public LoginController(UserService userService) {
        this.userService = userService;
    }

    /** 登录页 */
    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    /** 提交登录 */
    @PostMapping("/login")
    public String doLogin(@RequestParam String username,
                          @RequestParam String password,
                          HttpSession session) {
        User user = userService.login(username, password);
        if (user != null) {
            session.setAttribute(SESSION_USER_KEY, user);
            return "redirect:/";
        }
        return "redirect:/login?error=1";
    }

    /** 当前用户信息 */
    @GetMapping("/api/auth/me")
    @ResponseBody
    public ApiResult<User> me(HttpSession session) {
        User user = (User) session.getAttribute(SESSION_USER_KEY);
        if (user == null) return ApiResult.fail(401, "未登录");
        return ApiResult.ok(user);
    }

    /** 登出 */
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}
