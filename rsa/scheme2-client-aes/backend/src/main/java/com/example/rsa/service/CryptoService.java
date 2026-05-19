package com.example.rsa.service;

import com.example.rsa.dto.DecryptRequest;
import com.example.rsa.dto.EncryptResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;

@Service
@Slf4j
public class CryptoService {

    /**
     * 解密客户端请求：
     * 1) RSA 私钥解密 encryptedKey 得到 AES key
     * 2) 用 AES-GCM 解密 encryptedData 得到业务明文
     */
    public DecryptResult decryptRequest(DecryptRequest request, PrivateKey rsaPrivateKey) throws Exception {
        log.info("[解密服务] 收到加密请求：encryptedKey(Base64)长度={}, iv(Base64)长度={}, encryptedData(Base64)长度={}",
                safeLen(request.getEncryptedKey()), safeLen(request.getIv()), safeLen(request.getEncryptedData()));

        // 1) RSA 解密得到 AES 密钥
        // 关键坑：Java 默认 OAEP 参数和 JS node-forge 可能不一致，必须明确指定 (SHA-256 + MGF1(SHA-256))
        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
        OAEPParameterSpec oaepParams = new OAEPParameterSpec(
                "SHA-256", 
                "MGF1", 
                new MGF1ParameterSpec("SHA-256"), 
                PSource.PSpecified.DEFAULT
        );
        rsaCipher.init(Cipher.DECRYPT_MODE, rsaPrivateKey, oaepParams);

        byte[] aesKey = rsaCipher.doFinal(Base64.getDecoder().decode(request.getEncryptedKey()));
        if (aesKey.length != 16 && aesKey.length != 24 && aesKey.length != 32) {
            log.error("[解密服务] 解密出的 AES Key 长度异常: {} bytes", aesKey.length);
            throw new IllegalArgumentException("Invalid AES key length");
        }

        log.debug("[解密服务] RSA 解密 AES Key 成功，keyLength={} bytes", aesKey.length);

        // 2) AES-GCM 解密业务数据
        Cipher aesCipher = Cipher.getInstance("AES/GCM/NoPadding");
        SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, Base64.getDecoder().decode(request.getIv()));
        aesCipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

        byte[] decryptedData = aesCipher.doFinal(Base64.getDecoder().decode(request.getEncryptedData()));
        String plaintext = new String(decryptedData, StandardCharsets.UTF_8);

        log.info("[解密服务] 请求解密成功，明文长度={} 字符", plaintext.length());
        log.debug("[解密服务] 请求明文内容（仅学习用，生产环境不要这样打日志）: {}", plaintext);

        return new DecryptResult(aesKey, plaintext);
    }

    /**
     * 加密服务端响应：
     * 1) 使用“请求里解出来的 AES key”加密响应明文
     * 2) 使用 RSA 私钥对 (iv + encryptedData) 做数字签名，让前端能验签防篡改
     */
    public EncryptResponse encryptResponse(String responsePlaintext, byte[] aesKey, PrivateKey rsaSigningKey) throws Exception {
        log.info("[加密服务] 正在加密响应，明文长度={} 字符", responsePlaintext.length());
        log.debug("[加密服务] 响应明文内容（仅学习用，生产环境不要这样打日志）: {}", responsePlaintext);

        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);

        Cipher aesCipher = Cipher.getInstance("AES/GCM/NoPadding");
        SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
        aesCipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

        byte[] encryptedData = aesCipher.doFinal(responsePlaintext.getBytes(StandardCharsets.UTF_8));

        byte[] toSign = concat(iv, encryptedData);
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(rsaSigningKey);
        signature.update(toSign);
        byte[] sig = signature.sign();

        log.info("[加密服务] 响应加密完成：iv={} bytes, encryptedData={} bytes, signature={} bytes",
                iv.length, encryptedData.length, sig.length);

        return new EncryptResponse(
                Base64.getEncoder().encodeToString(iv),
                Base64.getEncoder().encodeToString(encryptedData),
                Base64.getEncoder().encodeToString(sig)
        );
    }

    public record DecryptResult(byte[] aesKey, String plaintext) {}

    private static int safeLen(String s) {
        return s == null ? 0 : s.length();
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
