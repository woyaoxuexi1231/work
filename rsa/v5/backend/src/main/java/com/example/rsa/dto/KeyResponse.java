package com.example.rsa.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class KeyResponse {
    private String keyVersion;
    private String publicKey;
    private String algorithm;
    private String token;
    private Long replayWindowMs;
}
