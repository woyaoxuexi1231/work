package com.example.rsa.service;

import com.example.rsa.dto.DecryptRequest;
import com.example.rsa.dto.EncryptResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;

@Service
@Slf4j
public class CryptoService {
    /**
     * 解密客户端请求：
     * 1) RSA 私钥解密 encryptedKey 得到 AES key
     * 2) 用 AES 解密 encryptedData 得到业务明文
     */
    public DecryptResult decryptRequest(DecryptRequest request, PrivateKey rsaPrivateKey) throws Exception {
        log.info("[解密服务] 收到加密请求：encryptedKey(Base64)长度={}, encryptedData(Base64)长度={}",
                safeLen(request.getEncryptedKey()), safeLen(request.getEncryptedData()));

        log.info("[解密服务] 使用基础方案：RSA(PKCS1Padding) 解密 AES Key + AES(ECB/PKCS5Padding) 解密业务数据");
        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        rsaCipher.init(Cipher.DECRYPT_MODE, rsaPrivateKey);
        byte[] aesKey = rsaCipher.doFinal(Base64.getDecoder().decode(request.getEncryptedKey()));
        validateAesKey(aesKey);

        Cipher aesCipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        aesCipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"));
        byte[] decryptedData = aesCipher.doFinal(Base64.getDecoder().decode(request.getEncryptedData()));
        String plaintext = new String(decryptedData, StandardCharsets.UTF_8);

        log.info("[解密服务] 请求解密成功，明文长度={} 字符", plaintext.length());
        log.debug("[解密服务] 请求明文内容（仅学习用，生产环境不要这样打日志）: {}", plaintext);

        return new DecryptResult(aesKey, plaintext);
    }

    /**
     * 加密服务端响应：
     * 1) 使用“请求里解出来的 AES key”加密响应明文
     * 2) 使用 RSA 私钥对 encryptedData 做数字签名，让前端能验签防篡改
     */
    public EncryptResponse encryptResponse(String responsePlaintext, byte[] aesKey, PrivateKey rsaSigningKey) throws Exception {
        log.info("[加密服务] 正在加密响应，明文长度={} 字符", responsePlaintext.length());
        log.debug("[加密服务] 响应明文内容（仅学习用，生产环境不要这样打日志）: {}", responsePlaintext);

        Cipher aesCipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        aesCipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"));
        byte[] encryptedData = aesCipher.doFinal(responsePlaintext.getBytes(StandardCharsets.UTF_8));

        byte[] toSign = encryptedData;
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(rsaSigningKey);
        signature.update(toSign);
        byte[] sig = signature.sign();

        log.info("[加密服务] 响应加密完成：encryptedData={} bytes, signature={} bytes",
                encryptedData.length, sig.length);

        return new EncryptResponse(
                Base64.getEncoder().encodeToString(encryptedData),
                Base64.getEncoder().encodeToString(sig)
        );
    }

    public record DecryptResult(byte[] aesKey, String plaintext) {}

    private static void validateAesKey(byte[] aesKey) {
        if (aesKey.length != 16 && aesKey.length != 24 && aesKey.length != 32) {
            throw new IllegalArgumentException("Invalid AES key length: " + aesKey.length);
        }
    }

    private static int safeLen(String s) {
        return s == null ? 0 : s.length();
    }

    
}
