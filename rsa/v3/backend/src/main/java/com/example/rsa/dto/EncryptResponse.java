package com.example.rsa.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EncryptResponse {
    private String iv;
    private String encryptedData;
    private String signature;
}
