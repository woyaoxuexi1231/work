package com.example.rsa.service;

import com.example.rsa.model.KeyVersion;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class KeyManager {
    // 使用 ConcurrentHashMap 存储不同版本的密钥对，模拟生产环境中的密钥库
    private final Map<String, KeyVersion> keys = new ConcurrentHashMap<>();
    private String latestVersion;

    @PostConstruct
    public void init() throws Exception {
        log.info("[密钥管理] 正在初始化系统默认密钥对...");
        // 预生成 v1 和 v2 版本，模拟密钥更迭场景
        generateNewKey("1");
        generateNewKey("2");
        log.info("[密钥管理] 初始化完成，当前最新版本: v{}", latestVersion);
    }

    /**
     * 生成指定版本的 RSA 2048位密钥对
     * @param version 版本号
     */
    public void generateNewKey(String version) throws Exception {
        log.debug("[密钥管理] 正在为版本 v{} 生成新的 RSA 2048 密钥对...", version);
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();

        // 计算公钥指纹，用于后续 Token 绑定校验，防止攻击者更换相同版本号下的公钥
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
        log.info("[密钥管理] 版本 v{} 密钥对已生成，指纹: {}", version, fingerprint);
    }

    public KeyVersion getLatestKey() {
        return keys.get(latestVersion);
    }

    public KeyVersion getKey(String version) {
        return keys.get(version);
    }

    /**
     * 计算公钥的 SHA-256 指纹
     * 作用：确保 Token 绑定的不仅仅是一个数字，而是特定公钥的数字摘要
     */
    private String calculateFingerprint(PublicKey publicKey) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(publicKey.getEncoded());
        return Base64.getEncoder().encodeToString(hash);
    }

    /**
     * 将公钥转换为标准的 PEM 格式字符串，方便前端解析
     */
    public String getPublicKeyPem(PublicKey publicKey) {
        String base64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" +
                base64.replaceAll("(.{64})", "$1\n") +
                "\n-----END PUBLIC KEY-----";
    }
}
