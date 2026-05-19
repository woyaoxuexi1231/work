package com.example.rsa.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 服务端返回的加密响应：
 * 1) encryptedData: 用“请求里带来的 AES 密钥”加密后的业务数据
 * 2) signature: 服务端用 RSA 私钥对 encryptedData 做的数字签名
 *
 * 注意：signature 不是“为了让前端解密而用私钥加密”，而是为了让前端验证“响应确实来自服务端且未被篡改”。（学习重点）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EncryptResponse {
    private String encryptedData;
    private String signature;
}
