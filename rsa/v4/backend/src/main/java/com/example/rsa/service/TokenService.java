package com.example.rsa.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

@Service
@Slf4j
public class TokenService {
    private PrivateKey signingKey;
    private PublicKey verifyKey;

    @PostConstruct
    public void init() throws Exception {
        log.info("[Token服务] 初始化 ECDSA 签名密钥（用于签发 key 包 token）");
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(256);
        KeyPair kp = gen.generateKeyPair();
        this.signingKey = kp.getPrivate();
        this.verifyKey = kp.getPublic();
        log.info("[Token服务] 初始化完成");
    }

    public String issueToken(String keyVersion, String fingerprint, long expireAtMs) throws Exception {
        String payload = keyVersion + ":" + fingerprint + ":" + expireAtMs;
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(signingKey);
        signer.update(payload.getBytes(StandardCharsets.UTF_8));
        byte[] sig = signer.sign();
        return Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "."
                + Base64.getEncoder().encodeToString(sig);
    }

    public boolean verifyToken(String token, String expectedKeyVersion, String expectedFingerprint, long nowMs) {
        try {
            if (StringUtils.hasLength(token)) return false;
            String[] parts = token.split("\\.");
            if (parts.length != 2) return false;

            String payload = new String(Base64.getDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            byte[] sig = Base64.getDecoder().decode(parts[1]);

            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(verifyKey);
            verifier.update(payload.getBytes(StandardCharsets.UTF_8));
            if (!verifier.verify(sig)) return false;

            String[] fields = payload.split(":");
            if (fields.length != 3) return false;
            String keyVersion = fields[0];
            String fingerprint = fields[1];
            long expireAt = Long.parseLong(fields[2]);

            if (!keyVersion.equals(expectedKeyVersion)) return false;
            if (!fingerprint.equals(expectedFingerprint)) return false;
            return nowMs <= expireAt;
        } catch (Exception e) {
            return false;
        }
    }
}

