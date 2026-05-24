package com.gateway.controller;

import com.gateway.JwtUtil;
import com.gateway.dto.ApiResult;
import com.gateway.entity.AuthUser;
import com.gateway.service.AuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;

    public AuthController(AuthService authService, JwtUtil jwtUtil) {
        this.authService = authService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ApiResult<Map<String, Object>> login(@RequestParam String username,
                                                 @RequestParam String password) {
        AuthUser user = authService.login(username, password);
        if (user == null) {
            return ApiResult.fail(401, "用户名或密码错误");
        }

        String token = jwtUtil.create(Map.of(
            "sub", user.getId().toString(),
            "username", user.getUsername(),
            "role", user.getRole()
        ));

        return ApiResult.ok(Map.of(
            "token", token,
            "userId", user.getId(),
            "username", user.getUsername(),
            "role", user.getRole()
        ));
    }

    @PostMapping("/api/auth/me")
    public ApiResult<Map<String, Object>> me(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ApiResult.fail(401, "未登录");
        }
        Map<String, Object> claims = jwtUtil.verify(authHeader.substring(7));
        if (claims == null) {
            return ApiResult.fail(401, "令牌无效或已过期");
        }
        Long userId = Long.parseLong((String) claims.get("sub"));
        AuthUser user = authService.getById(userId);
        if (user == null) {
            return ApiResult.fail(401, "用户不存在");
        }
        return ApiResult.ok(Map.of(
            "id", user.getId(),
            "username", user.getUsername(),
            "role", user.getRole()
        ));
    }

    @PostMapping("/logout")
    public ApiResult<Void> logout() {
        return ApiResult.ok(null);
    }
}
