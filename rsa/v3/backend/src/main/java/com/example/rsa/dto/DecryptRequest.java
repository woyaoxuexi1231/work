package com.example.rsa.dto;

import lombok.Data;

@Data
public class DecryptRequest {
    private String encryptedKey;
    private String iv;
    private String encryptedData;
}
