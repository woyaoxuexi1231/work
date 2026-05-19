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
     * AES 密文（Base64）
     * - AES-ECB：ciphertext
     */
    private String encryptedData;
}
