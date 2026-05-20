package com.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * 自实现 JWT（HS256），不依赖外部 JWT 库
 */
@Component
public class JwtUtil {

    private final String secret;
    private final long expiration;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtUtil(JwtProperties props) {
        this.secret = props.getSecret();
        this.expiration = props.getExpiration();
    }

    /** 签发 JWT */
    @SuppressWarnings("unchecked")
    public String create(Map<String, Object> claims) {
        try {
            long now = System.currentTimeMillis();
            claims.put("iat", now / 1000);
            claims.put("exp", (now + expiration) / 1000);

            String headerJson = objectMapper.writeValueAsString(Map.of("alg", "HS256", "typ", "JWT"));
            String payloadJson = objectMapper.writeValueAsString(claims);

            String headerB64 = base64UrlEncode(headerJson.getBytes(StandardCharsets.UTF_8));
            String payloadB64 = base64UrlEncode(payloadJson.getBytes(StandardCharsets.UTF_8));

            String signature = sign(headerB64 + "." + payloadB64);

            return headerB64 + "." + payloadB64 + "." + signature;
        } catch (Exception e) {
            throw new RuntimeException("JWT 签发失败", e);
        }
    }

    /** 验证并返回 payload */
    @SuppressWarnings("unchecked")
    public Map<String, Object> verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return null;

            String expectedSig = sign(parts[0] + "." + parts[1]);
            if (!expectedSig.equals(parts[2])) return null;

            byte[] payloadBytes = base64UrlDecode(parts[1]);
            Map<String, Object> claims = objectMapper.readValue(payloadBytes, Map.class);

            long exp = ((Number) claims.get("exp")).longValue();
            if (System.currentTimeMillis() / 1000 > exp) return null;

            return claims;
        } catch (Exception e) {
            return null;
        }
    }

    private String sign(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] sig = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return base64UrlEncode(sig);
    }

    private String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private byte[] base64UrlDecode(String data) {
        return Base64.getUrlDecoder().decode(data);
    }
}
