package com.example.rsa.service;

import com.example.rsa.model.KeyVersion;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * “版本密钥库”演示：
 * - 服务端同时持有多套 RSA 密钥对（v1, v2, ...）
 * - 前端获取最新公钥时，会得到 version + token
 * - 解密请求时，服务端会按 version 选私钥，但绝对不会“只信 version”
 */
@Service
@Slf4j
public class KeyManager {
    private final Map<String, KeyVersion> keys = new ConcurrentHashMap<>();
    private String latestVersion;

    @PostConstruct
    public void init() throws Exception {
        log.info("[密钥管理] 初始化 v1/v2（用于演示轮换与降级攻击）");
        generateNewKey("1");
        generateNewKey("2");
        log.info("[密钥管理] 初始化完成，当前最新版本=v{}", latestVersion);
    }

    public KeyVersion getLatestKey() {
        return keys.get(latestVersion);
    }

    public KeyVersion getKey(String version) {
        return keys.get(version);
    }

    private void generateNewKey(String version) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();

        String fingerprint = calculateFingerprint(pair.getPublic());
        KeyVersion kv = KeyVersion.builder()
                .version(version)
                .publicKey(pair.getPublic())
                .privateKey(pair.getPrivate())
                .fingerprint(fingerprint)
                .status(KeyVersion.Status.ACTIVE)
                .build();

        keys.put(version, kv);
        latestVersion = version;
        log.info("[密钥管理] 生成 v{} RSA 密钥对，fingerprint={}", version, fingerprint);
    }

    public String getPublicKeyPem(PublicKey publicKey) {
        String base64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" +
                base64.replaceAll("(.{64})", "$1\n") +
                "\n-----END PUBLIC KEY-----";
    }

    private String calculateFingerprint(PublicKey publicKey) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return Base64.getEncoder().encodeToString(digest.digest(publicKey.getEncoded()));
    }
}

