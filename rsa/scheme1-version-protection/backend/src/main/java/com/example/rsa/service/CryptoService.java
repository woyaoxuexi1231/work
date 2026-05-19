package com.example.rsa.service;

import com.example.rsa.dto.DecryptRequest;
import com.example.rsa.model.KeyVersion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 解密核心（基础版）：
 * - RSA 私钥解密出 AES Key
 * - AES 解密出业务明文
 * - 版本防篡改由 Controller 的 token 校验负责
 */
@Service
@Slf4j
public class CryptoService {
    public String decrypt(DecryptRequest request, KeyVersion kv) throws Exception {
        log.info("[解密服务] 基础解密：token 防篡改（在 Controller 里校验），这里专注做解密");

        // 1) RSA 解密得到 AES Key（最基础：RSA/PKCS1Padding）
        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        rsaCipher.init(Cipher.DECRYPT_MODE, kv.getPrivateKey());
        byte[] aesKey = rsaCipher.doFinal(Base64.getDecoder().decode(request.getEncryptedKey()));
        if (aesKey.length != 16 && aesKey.length != 24 && aesKey.length != 32) {
            throw new IllegalArgumentException("Invalid AES key length: " + aesKey.length);
        }

        // 2) AES-ECB 解密业务数据（最基础：AES/ECB/PKCS5Padding）
        Cipher aesCipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        aesCipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"));
        byte[] plaintextBytes = aesCipher.doFinal(Base64.getDecoder().decode(request.getEncryptedData()));

        String plaintext = new String(plaintextBytes, StandardCharsets.UTF_8);
        log.info("[解密服务] 解密成功，明文长度={} 字符", plaintext.length());
        log.debug("[解密服务] 明文内容（仅学习用，生产不要打）: {}", plaintext);
        return plaintext;
    }
}
