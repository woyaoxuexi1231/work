package com.example.rsa.service;

import com.example.rsa.dto.DecryptRequest;
import com.example.rsa.model.KeyVersion;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class CryptoService {

    public String decrypt(DecryptRequest request, KeyVersion kv) throws Exception {
        // 1. RSA Decrypt the key material
        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        rsaCipher.init(Cipher.DECRYPT_MODE, kv.getPrivateKey());
        byte[] keyMaterial = rsaCipher.doFinal(Base64.getDecoder().decode(request.getEncryptedKey()));

        // keyMaterial structure: version(4 bytes) | timestamp(8 bytes) | SK(16 bytes)
        ByteBuffer buffer = ByteBuffer.wrap(keyMaterial);
        int versionInMaterial = buffer.getInt();
        long timestamp = buffer.getLong();
        byte[] aesKey = new byte[16];
        buffer.get(aesKey);

        // 2. Validate version locking (Defense 2.3)
        if (!String.valueOf(versionInMaterial).equals(kv.getVersion())) {
            throw new SecurityException("Version locking mismatch! Possible downgrade attack.");
        }

        // 3. AES-GCM Decrypt the data with version as AAD
        Cipher aesCipher = Cipher.getInstance("AES/GCM/NoPadding");
        SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, Base64.getDecoder().decode(request.getIv()));
        aesCipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
        
        // Add AAD (version)
        aesCipher.updateAAD(kv.getVersion().getBytes(StandardCharsets.UTF_8));
        
        byte[] decryptedData = aesCipher.doFinal(Base64.getDecoder().decode(request.getEncryptedData()));
        return new String(decryptedData, StandardCharsets.UTF_8);
    }
}
