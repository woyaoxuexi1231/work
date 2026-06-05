package com.mlm.common.util;

import com.mlm.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * 认证上下文工具 — 从请求中解析当前用户身份
 * <p>
 * 通过 {@code Authorization: Bearer <token>} 请求头调用外部认证网关
 * 获取当前用户 ID。认证网关地址通过 {@code auth.service.url} 配置。
 * <p>
 * 【设计说明】
 * 不将用户身份缓存到 ThreadLocal / Session 中，因为当前架构采用
 * 无状态设计 + 外部网关认证方式，每次请求独立鉴权。
 * 若需性能优化，可考虑引入 Redis 缓存 token→userId 映射。
 * <p>
 * 使用方式：
 * <pre>{@code
 * Long userId = AuthContext.currentUserId(request);
 * }</pre>
 *
 * @author mlm
 */
public final class AuthContext {

    private static final Logger log = LoggerFactory.getLogger(AuthContext.class);

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private AuthContext() {
        // 工具类，禁止实例化
    }

    /**
     * 从 HTTP 请求中解析当前用户 ID
     * <p>
     * 从请求头提取 Bearer token，调用认证网关 {@code /api/auth/me} 接口
     * 获取用户身份信息。认证失败时抛出 {@link BizException}。
     *
     * @param request 当前 HTTP 请求（必须包含 Authorization 头）
     * @param restTemplate 用于调用认证网关的 HTTP 客户端
     * @param authServiceUrl 认证网关基础 URL
     * @return 当前登录用户 ID
     * @throws BizException 未登录或认证失败时抛出
     */
    public static Long currentUserId(HttpServletRequest request,
                                     RestTemplate restTemplate,
                                     String authServiceUrl) {
        String authHeader = request.getHeader(AUTH_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("认证失败: 缺少有效的 Authorization 头");
            throw new BizException(401, "未登录");
        }
        return callAuthGateway(authHeader, restTemplate, authServiceUrl);
    }

    /**
     * 调用外部认证网关获取用户信息
     *
     * @param authHeader HTTP Authorization 头完整值
     * @param restTemplate HTTP 客户端
     * @param authServiceUrl 认证网关基础 URL
     * @return 用户 ID
     */
    private static Long callAuthGateway(String authHeader,
                                        RestTemplate restTemplate,
                                        String authServiceUrl) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(AUTH_HEADER, authHeader);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(new HashMap<>(), headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    authServiceUrl + "/api/auth/me",
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null || !Integer.valueOf(200).equals(responseBody.get("code"))) {
                log.warn("认证网关返回非200: {}", responseBody);
                throw new BizException(401, "未登录");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
            if (data == null || data.get("id") == null) {
                log.warn("认证网关返回数据缺少用户ID: {}", responseBody);
                throw new BizException(401, "用户信息获取失败");
            }

            Long userId = ((Number) data.get("id")).longValue();
            log.debug("用户认证成功: userId={}", userId);
            return userId;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用认证网关失败: {}", e.getMessage());
            throw new BizException(401, "鉴权失败: " + e.getMessage());
        }
    }
}
