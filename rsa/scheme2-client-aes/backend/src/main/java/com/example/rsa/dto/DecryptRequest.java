package com.example.rsa.dto;

import lombok.Data;

@Data
public class DecryptRequest {
    /**
     * RSA 加密后的 AES 密钥（Base64）
     *
     * 前端每次请求随机生成一个 AES 密钥，用它加密业务数据；
     * 然后用服务端公钥把 AES 密钥“包起来”发给服务端。
     */
    private String encryptedKey;

    /**
     * AES-GCM 的 IV / Nonce（Base64）
     * 推荐 12 字节（96-bit）。
     */
    private String iv;

    /**
     * AES-GCM 密文（Base64）
     * 注意：这里的密文一般包含 GCM Tag（Java 侧通常要求“密文 + Tag”拼在一起）。
     */
    private String encryptedData;
}
