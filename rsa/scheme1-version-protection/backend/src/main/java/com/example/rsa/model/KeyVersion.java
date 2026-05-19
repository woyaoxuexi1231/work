package com.example.rsa.model;

import lombok.Builder;
import lombok.Data;

import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * 一把 RSA 密钥（一个版本）。
 *
 * 学习重点：
 * - version：让你知道“当前用的是哪一把密钥”
 * - fingerprint：公钥指纹（SHA-256），用于把 token 与“具体哪一把公钥”绑定起来
 * - status：可以模拟吊销旧密钥（被盗/弱算法等）
 */
@Data
@Builder
public class KeyVersion {
    private String version;
    private PublicKey publicKey;
    private PrivateKey privateKey;
    private String fingerprint;
    private Status status;

    public enum Status {
        ACTIVE, DEPRECATED, REVOKED
    }
}

