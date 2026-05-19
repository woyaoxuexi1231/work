package com.example.rsa.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 服务端下发的“密钥包”：
 * - version：当前公钥版本号（明文）
 * - publicKey：PEM 格式公钥
 * - token：服务端签名的防篡改凭证，把 version 与公钥 fingerprint 绑定
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class KeyResponse {
    private String version;
    private String publicKey;
    private String token;
}

