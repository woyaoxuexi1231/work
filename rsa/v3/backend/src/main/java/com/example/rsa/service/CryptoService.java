package com.example.rsa.service;

import com.example.rsa.dto.DecryptRequest;
import com.example.rsa.dto.EncryptResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.Base64;

@Service
@Slf4j
public class CryptoService {
    public DecryptResult decryptRequest(DecryptRequest request, PrivateKey rsaPrivateKey) throws Exception {
        log.info("[解密服务] (v3) RSA 解密 AES key + AES-CBC 解密业务数据（需要 iv）");

        byte[] aesKey = rsaDecryptPkcs1(request.getEncryptedKey(), rsaPrivateKey);
        byte[] plaintextBytes = aesDecryptCbc(request.getEncryptedData(), aesKey, request.getIv());
        String plaintext = new String(plaintextBytes, StandardCharsets.UTF_8);

        log.info("[解密服务] (v3) 解密成功，明文长度={} 字符", plaintext.length());
        log.debug("[解密服务] (v3) 明文内容（仅学习用，生产不要打）: {}", plaintext);
        return new DecryptResult(aesKey, plaintext);
    }

    public EncryptResponse encryptResponse(String responsePlaintext, byte[] aesKey, PrivateKey signingKey) throws Exception {
        log.info("[加密服务] (v3) AES-CBC 加密响应 + RSA 私钥签名 (iv + encryptedData)");

        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);

        Cipher aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        aesCipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new IvParameterSpec(iv));
        byte[] encrypted = aesCipher.doFinal(responsePlaintext.getBytes(StandardCharsets.UTF_8));

        byte[] toSign = concat(iv, encrypted);
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(signingKey);
        signature.update(toSign);
        byte[] sig = signature.sign();

        return new EncryptResponse(
                Base64.getEncoder().encodeToString(iv),
                Base64.getEncoder().encodeToString(encrypted),
                Base64.getEncoder().encodeToString(sig)
        );
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

    private static byte[] aesDecryptCbc(String encryptedDataB64, byte[] aesKey, String ivB64) throws Exception {
        if (ivB64 == null || ivB64.isBlank()) {
            throw new IllegalArgumentException("v3 缺少 iv");
        }
        byte[] iv = Base64.getDecoder().decode(ivB64);
        Cipher aes = Cipher.getInstance("AES/CBC/PKCS5Padding");
        aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new IvParameterSpec(iv));
        return aes.doFinal(Base64.getDecoder().decode(encryptedDataB64));
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
