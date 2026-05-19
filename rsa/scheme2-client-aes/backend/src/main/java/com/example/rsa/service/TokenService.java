package com.example.rsa.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.*;
import java.util.Base64;

@Service
@Slf4j
public class TokenService {
    // ECDSA 签名密钥对。注意：这是服务端的全局签名密钥，与业务 RSA 密钥解耦。
    // 它的作用是证明“某个 RSA 公钥版本是经过服务端授权分发的”。
    private PrivateKey signingKey;
    private PublicKey verificationKey;

    @PostConstruct
    public void init() throws Exception {
        log.info("[Token服务] 正在初始化 ECDSA 签名系统 (secp256r1)...");
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(256); 
        KeyPair pair = gen.generateKeyPair();
        this.signingKey = pair.getPrivate();
        this.verificationKey = pair.getPublic();
        log.info("[Token服务] 签名系统初始化完成");
    }

    /**
     * 生成防篡改 Token
     * 核心防御逻辑：将版本号(version)与公钥指纹(fingerprint)绑定并签名
     */
    public String generateToken(String version, String fingerprint) throws Exception {
        // Payload 包含：版本号、指纹、过期时间
        String payload = version + ":" + fingerprint + ":" + (System.currentTimeMillis() + 3600000); 
        
        // 使用 ECDSA 签名
        Signature ecdsa = Signature.getInstance("SHA256withECDSA");
        ecdsa.initSign(signingKey);
        ecdsa.update(payload.getBytes());
        byte[] signature = ecdsa.sign();
        
        String encodedPayload = Base64.getEncoder().encodeToString(payload.getBytes());
        String encodedSignature = Base64.getEncoder().encodeToString(signature);
        
        String token = encodedPayload + "." + encodedSignature;
        log.debug("[Token服务] 为 v{} 生成 Token: {}", version, token);
        return token;
    }

    /**
     * 校验 Token 是否合法且未被篡改
     * @param token 待校验 Token
     * @param expectedVersion 请求中声称的版本号
     * @param expectedFingerprint 该版本号对应的后端真实公钥指纹
     */
    public boolean verifyToken(String token, String expectedVersion, String expectedFingerprint) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 2) {
                log.warn("[Token服务] Token 格式错误");
                return false;
            }

            String payloadStr = new String(Base64.getDecoder().decode(parts[0]));
            byte[] signature = Base64.getDecoder().decode(parts[1]);

            String[] payloadParts = payloadStr.split(":");
            if (payloadParts.length != 3) return false;

            String version = payloadParts[0];
            String fingerprint = payloadParts[1];
            long expiry = Long.parseLong(payloadParts[2]);

            // 1. 检查过期时间
            if (System.currentTimeMillis() > expiry) {
                log.warn("[Token服务] Token 已过期");
                return false;
            }
            
            // 2. 检查版本和指纹是否与 Token 内绑定的信息一致
            // 如果攻击者篡改了 version 或试图用旧 version 对应的 Token 请求，这里会失败
            if (!version.equals(expectedVersion) || !fingerprint.equals(expectedFingerprint)) {
                log.error("[Token服务] 关键参数不一致! Token绑定的版本: {}, 请求版本: {}", version, expectedVersion);
                return false;
            }

            // 3. 密码学签名验证
            Signature ecdsa = Signature.getInstance("SHA256withECDSA");
            ecdsa.initVerify(verificationKey);
            ecdsa.update(payloadStr.getBytes());
            boolean isValid = ecdsa.verify(signature);
            
            if (!isValid) log.error("[Token服务] 签名验证失败，Token 可能已被伪造");
            return isValid;
        } catch (Exception e) {
            log.error("[Token服务] 校验过程出错: {}", e.getMessage());
            return false;
        }
    }
}
