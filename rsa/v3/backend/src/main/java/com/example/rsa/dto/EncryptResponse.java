package com.example.rsa.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 服务端 → 客户端 的响应体。
 *
 * 相比 v1/v2 新增了 {@code iv} 字段：
 * - 服务端加密响应时生成一个新的随机 IV（和请求 IV 无关）
 * - 服务端签名时签的是 {@code concat(ivBytes, encryptedBytes)}，不是只签密文
 * - 前端验签时必须先按同样顺序拼接 IV+密文，再调 {@code publicKey.verify()}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EncryptResponse {
    /**
     * 服务端加密响应时生成的新 IV（16 字节随机，Base64 编码）。
     * 不保密，但参与签名——前端验签前如果 IV 被篡改，验签会失败。
     */
    private String iv;

    /** AES-CBC 加密后的响应业务数据（Base64） */
    private String encryptedData;

    /**
     * RSA 私钥对 {@code concat(ivBytes, encryptedBytes)} 的 SHA256withRSA 签名（Base64）。
     * 前端用公钥验签，确保 IV 和密文都未被篡改。
     */
    private String signature;
}
