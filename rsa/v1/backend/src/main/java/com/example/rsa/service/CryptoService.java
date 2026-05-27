package com.example.rsa.service;

import com.example.rsa.dto.DecryptRequest;
import com.example.rsa.dto.EncryptResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.Base64;

@Service
@Slf4j
public class CryptoService {
    public DecryptResult decryptRequest(DecryptRequest request, PrivateKey rsaPrivateKey) throws Exception {
        log.info("[解密服务] (v1) RSA 解密 AES key + AES-ECB 解密业务数据");

        byte[] aesKey = rsaDecryptPkcs1(request.getEncryptedKey(), rsaPrivateKey);
        byte[] plaintextBytes = aesDecryptEcb(request.getEncryptedData(), aesKey);
        String plaintext = new String(plaintextBytes, StandardCharsets.UTF_8);

        log.info("[解密服务] (v1) 解密成功，明文长度={} 字符", plaintext.length());
        log.debug("[解密服务] (v1) 明文内容（仅学习用，生产不要打）: {}", plaintext);
        return new DecryptResult(aesKey, plaintext);
    }

    public EncryptResponse encryptResponse(String responsePlaintext, byte[] aesKey) throws Exception {
        log.info("[加密服务] (v1) AES-ECB 加密响应（不做签名）");
        Cipher aesCipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        aesCipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"));
        byte[] encrypted = aesCipher.doFinal(responsePlaintext.getBytes(StandardCharsets.UTF_8));
        return new EncryptResponse(Base64.getEncoder().encodeToString(encrypted), null);
    }

    public static class DecryptResult {
        private final byte[] aesKey;
        private final String plaintext;

        public DecryptResult(byte[] aesKey, String plaintext) {
            this.aesKey = aesKey;
            this.plaintext = plaintext;
        }

        public byte[] getAesKey() { return aesKey; }
        public String getPlaintext() { return plaintext; }
    }

    private static byte[] rsaDecryptPkcs1(String encryptedKeyB64, PrivateKey privateKey) throws Exception {
        Cipher rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        rsa.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] aesKey = rsa.doFinal(Base64.getDecoder().decode(encryptedKeyB64));
        if (aesKey.length != 16 && aesKey.length != 24 && aesKey.length != 32) {
            throw new IllegalArgumentException("Invalid AES key length: " + aesKey.length);
        }
        return aesKey;
    }

    private static byte[] aesDecryptEcb(String encryptedDataB64, byte[] aesKey) throws Exception {
        Cipher aes = Cipher.getInstance("AES/ECB/PKCS5Padding");
        aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"));
        return aes.doFinal(Base64.getDecoder().decode(encryptedDataB64));
    }
}
