package com.example.rsa.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

/**
 * Token 服务（核心防御点）：
 *
 * 新手版理解：
 * - version 是前端传来的，可能被篡改，不能相信
 * - 所以服务端给前端发一个“带签名的票据 token”
 * - token 内部绑定了：version + 公钥fingerprint + 过期时间
 * - 前端提交请求时必须带回 token
 * - 服务端验证 token 的签名，签名对不上就是篡改/伪造，直接拒绝
 */
@Service
@Slf4j
public class TokenService {
    private PrivateKey signingKey;
    private PublicKey verifyKey;

    @PostConstruct
    public void init() throws Exception {
        log.info("[Token服务] 初始化 ECDSA 签名系统 (secp256r1)");
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(256);
        KeyPair pair = gen.generateKeyPair();
        signingKey = pair.getPrivate();
        verifyKey = pair.getPublic();
    }

    public String issueToken(String version, String fingerprint) throws Exception {
        long expiry = System.currentTimeMillis() + 60 * 60 * 1000;
        String payload = version + ":" + fingerprint + ":" + expiry;

        Signature ecdsa = Signature.getInstance("SHA256withECDSA");
        ecdsa.initSign(signingKey);
        ecdsa.update(payload.getBytes(StandardCharsets.UTF_8));
        byte[] sig = ecdsa.sign();

        String token = Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "."
                + Base64.getEncoder().encodeToString(sig);

        log.debug("[Token服务] 签发 token: version=v{}, expiry={}", version, expiry);
        return token;
    }

    public boolean verifyToken(String token, String expectedVersion, String expectedFingerprint) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 2) {
                log.warn("[Token服务] token 格式错误");
                return false;
            }

            String payload = new String(Base64.getDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            byte[] sig = Base64.getDecoder().decode(parts[1]);

            String[] payloadParts = payload.split(":");
            if (payloadParts.length != 3) {
                log.warn("[Token服务] payload 格式错误");
                return false;
            }

            String versionInToken = payloadParts[0];
            String fingerprintInToken = payloadParts[1];
            long expiry = Long.parseLong(payloadParts[2]);

            if (System.currentTimeMillis() > expiry) {
                log.warn("[Token服务] token 已过期");
                return false;
            }

            if (!expectedVersion.equals(versionInToken) || !expectedFingerprint.equals(fingerprintInToken)) {
                log.error("[Token服务] token 绑定信息不匹配：token(v{},fp=...) vs request(v{},fp=...)",
                        versionInToken, expectedVersion);
                return false;
            }

            Signature ecdsa = Signature.getInstance("SHA256withECDSA");
            ecdsa.initVerify(verifyKey);
            ecdsa.update(payload.getBytes(StandardCharsets.UTF_8));
            boolean ok = ecdsa.verify(sig);

            if (!ok) log.error("[Token服务] 签名校验失败（疑似伪造/篡改）");
            return ok;
        } catch (Exception e) {
            log.error("[Token服务] 校验异常: {}", e.getMessage());
            return false;
        }
    }
}

