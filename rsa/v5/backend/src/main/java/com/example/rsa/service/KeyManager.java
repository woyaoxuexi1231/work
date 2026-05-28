package com.example.rsa.service;

import com.example.rsa.model.KeyVersion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
public class KeyManager {
    private final Map<String, KeyVersion> keys = new LinkedHashMap<>();
    private String latestKeyVersion;

    @PostConstruct
    public void init() throws Exception {
        log.info("[密钥管理] (v5) 初始化 keyVersion=1/2（演示轮换）");
        generateKey("1", KeyVersion.Status.DEPRECATED);
        generateKey("2", KeyVersion.Status.ACTIVE);
        this.latestKeyVersion = "2";
        log.info("[密钥管理] (v5) 初始化完成，latestKeyVersion={}", latestKeyVersion);
    }

    public KeyVersion getLatestKey() {
        return keys.get(latestKeyVersion);
    }

    public KeyVersion getKey(String keyVersion) {
        return keys.get(keyVersion);
    }

    public String getPublicKeyPem(PublicKey publicKey) {
        String base64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" +
                base64.replaceAll("(.{64})", "$1\n") +
                "\n-----END PUBLIC KEY-----";
    }

    private void generateKey(String version, KeyVersion.Status status) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();
        String fingerprint = calculateFingerprint(pair.getPublic());
        keys.put(version, KeyVersion.builder()
                .version(version)
                .publicKey(pair.getPublic())
                .privateKey(pair.getPrivate())
                .fingerprint(fingerprint)
                .status(status)
                .build());
        log.info("[密钥管理] (v5) 生成 keyVersion={}, status={}, fingerprint={}", version, status, fingerprint);
    }

    private String calculateFingerprint(PublicKey publicKey) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return Base64.getEncoder().encodeToString(digest.digest(publicKey.getEncoded()));
    }
}
