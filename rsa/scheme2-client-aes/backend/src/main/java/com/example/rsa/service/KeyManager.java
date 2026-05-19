package com.example.rsa.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.*;
import java.util.Base64;

@Service
@Slf4j
public class KeyManager {
    /**
     * 为了便于新手学习，这里只维护一套 RSA 密钥对：
     * - 公钥：给前端，用于加密（更准确地说：加密 AES 密钥）
     * - 私钥：留在服务端，用于解密 AES 密钥；也用于对响应做“签名”（很多人误以为是私钥加密）
     *
     * 生产环境通常会接入 KMS/HSM 并做密钥轮换/版本管理，本项目先把流程讲清楚。
     */
    private KeyPair keyPair;

    @PostConstruct
    public void init() throws Exception {
        log.info("[密钥管理] 正在生成 RSA 2048 密钥对...");
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        this.keyPair = gen.generateKeyPair();
        log.info("[密钥管理] RSA 密钥对生成完成，公钥指纹: {}", calculateFingerprint(keyPair.getPublic()));
    }

    /**
     * 计算公钥的 SHA-256 指纹
     * 作用：帮助你在日志里区分“是不是同一把公钥”，仅用于学习与排错。
     */
    private String calculateFingerprint(PublicKey publicKey) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(publicKey.getEncoded());
        return Base64.getEncoder().encodeToString(hash);
    }

    public PublicKey getPublicKey() {
        return keyPair.getPublic();
    }

    public PrivateKey getPrivateKey() {
        return keyPair.getPrivate();
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

    public String getPublicKeyPem() {
        return getPublicKeyPem(getPublicKey());
    }
}
