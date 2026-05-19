package com.example.rsa.service;

import com.example.rsa.dto.DecryptRequest;
import com.example.rsa.model.KeyVersion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;

/**
 * 解密核心（版本防篡改方案）：
 *
 * 关键设计（新手版）：
 * 1) RSA 密文里不是只有 AES Key，还塞了 version 和 timestamp（让“密文自己描述它属于哪个版本”）
 * 2) AES-GCM 解密时把 version 作为 AAD，确保“篡改 version 就会解密失败”
 *
 * keyMaterial 结构（28 bytes）：
 * - version: int32 (4 bytes, Big Endian)
 * - timestamp: int64 (8 bytes, Big Endian)
 * - aesKey: 16 bytes
 */
@Service
@Slf4j
public class CryptoService {

    public String decrypt(DecryptRequest request, KeyVersion kv) throws Exception {
        log.info("[解密服务] 开始解密：request.version=v{}", request.getVersion());

        // 1) RSA 解密 keyMaterial（注意 OAEP 参数要和前端一致：SHA-256 + MGF1(SHA-256)）
        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
        OAEPParameterSpec oaepParams = new OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                new MGF1ParameterSpec("SHA-256"),
                PSource.PSpecified.DEFAULT
        );
        rsaCipher.init(Cipher.DECRYPT_MODE, kv.getPrivateKey(), oaepParams);
        byte[] keyMaterial = rsaCipher.doFinal(Base64.getDecoder().decode(request.getEncryptedKey()));

        // 2) 解析 keyMaterial
        ByteBuffer buf = ByteBuffer.wrap(keyMaterial);
        int versionInCipher = buf.getInt();
        long timestamp = buf.getLong();
        byte[] aesKey = new byte[16];
        buf.get(aesKey);

        log.info("[解密服务] RSA 解密成功：cipher.version=v{}, timestamp={}", versionInCipher, timestamp);

        // 3) 版本锁定校验：密文内部版本必须与“选用的私钥版本”一致
        if (!String.valueOf(versionInCipher).equals(kv.getVersion())) {
            log.error("[安全警报] 版本锁定失败：cipher.v{} vs serverKey.v{}", versionInCipher, kv.getVersion());
            throw new SecurityException("版本锁定失败：疑似降级/篡改攻击");
        }

        // 4) AES-GCM 解密业务数据（version 作为 AAD，篡改 version 会导致 GCM 校验失败）
        Cipher aesCipher = Cipher.getInstance("AES/GCM/NoPadding");
        SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, Base64.getDecoder().decode(request.getIv()));
        aesCipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

        aesCipher.updateAAD(kv.getVersion().getBytes(StandardCharsets.UTF_8));

        byte[] plaintextBytes = aesCipher.doFinal(Base64.getDecoder().decode(request.getEncryptedData()));
        String plaintext = new String(plaintextBytes, StandardCharsets.UTF_8);

        log.info("[解密服务] AES-GCM 解密成功，明文长度={} 字符", plaintext.length());
        log.debug("[解密服务] 明文内容（仅学习用，生产不要打）: {}", plaintext);
        return plaintext;
    }
}

