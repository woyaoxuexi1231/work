package com.example.rsa.dto;

import lombok.Data;

/**
 * 客户端 → 服务端 的请求体。
 *
 * 相比 v1/v2 新增了 {@code iv} 字段：
 * - v1/v2 用 AES-ECB（无需 IV），所以请求里只有 encryptedKey + encryptedData
 * - v3 升级到 AES-CBC 后，客户端每次加密都生成随机 IV，必须随请求一起上传
 * - 服务端用这个 IV 才能正确解密第一个密文块（CBC 模式：P₁ = D(C₁) ⊕ IV）
 * - IV 不保密，可以明文传输，但它的完整性在响应方向由签名保护
 */
@Data
public class DecryptRequest {
    /** 客户端用 RSA 公钥加密后的 AES key（Base64） */
    private String encryptedKey;

    /**
     * 客户端加密时生成的随机 IV（16 字节，Base64 编码）。
     * AES-CBC 解密必需；如果缺失或长度不对，服务端直接拒绝。
     */
    private String iv;

    /** 客户端用 AES-CBC 加密后的业务数据（Base64） */
    private String encryptedData;
}
