package com.example.rsa.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class KeyResponse {
    private String publicKey; // PEM format
    private String algorithm; // 仅用于提示前端如何使用（学习用）
}
