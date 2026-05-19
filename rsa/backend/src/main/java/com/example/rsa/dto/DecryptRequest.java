package com.example.rsa.dto;

import lombok.Data;

@Data
public class DecryptRequest {
    private String version;
    private String token;
    private String encryptedKey; // RSA encrypted key material
    private String encryptedData; // AES-GCM encrypted data
    private String iv; // AES IV
}
