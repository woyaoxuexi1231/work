package com.example.rsa.dto;

import lombok.Data;

/**
 * 前端上报的加密请求（版本防篡改方案）：
 *
 * - version：前端声称使用了哪个版本的公钥（可能被攻击者篡改！）
 * - token：服务端签发的签名凭证（可防止 version 被篡改）
 * - encryptedKey：RSA 加密块（里面包含 version + timestamp + aesKey）
 * - iv：AES-GCM 的 iv
 * - encryptedData：AES-GCM 的密文（ciphertext + tag）
 */
@Data
public class DecryptRequest {
    private String version;
    private String token;
    private String encryptedKey;
    private String iv;
    private String encryptedData;
}

