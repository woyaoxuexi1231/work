package com.example.rsa.dto;

import lombok.Data;

@Data
public class DecryptRequest {
    private String keyVersion;
    private String token;
    private Long timestamp;
    private String nonce;
    private String encryptedKey;
    private String encryptedData;
    private String requestSignature;
}
