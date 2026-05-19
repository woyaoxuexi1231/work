package com.example.rsa.dto;

import lombok.Data;

/**
 * 前端上报的加密请求（版本防篡改方案）：
 *
 * - version：前端声称使用了哪个版本的公钥（可能被攻击者篡改！）
 * - token：服务端签发的签名凭证（可防止 version 被篡改）
 * - encryptedKey：RSA 加密后的 AES key（本次请求的对称密钥）
 * - encryptedData：AES 加密后的业务密文
 */
@Data
public class DecryptRequest {
    private String version;
    private String token;
    private String encryptedKey;
    private String encryptedData;
}
