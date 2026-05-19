package com.example.rsa.service;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.security.*;
import java.util.Base64;

@Service
public class TokenService {
    private PrivateKey signingKey;
    private PublicKey verificationKey;

    @PostConstruct
    public void init() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(256); // secp256r1
        KeyPair pair = gen.generateKeyPair();
        this.signingKey = pair.getPrivate();
        this.verificationKey = pair.getPublic();
    }

    public String generateToken(String version, String fingerprint) throws Exception {
        String payload = version + ":" + fingerprint + ":" + (System.currentTimeMillis() + 3600000); // 1h expiry
        Signature ecdsa = Signature.getInstance("SHA256withECDSA");
        ecdsa.initSign(signingKey);
        ecdsa.update(payload.getBytes());
        byte[] signature = ecdsa.sign();
        
        String encodedPayload = Base64.getEncoder().encodeToString(payload.getBytes());
        String encodedSignature = Base64.getEncoder().encodeToString(signature);
        return encodedPayload + "." + encodedSignature;
    }

    public boolean verifyToken(String token, String expectedVersion, String expectedFingerprint) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 2) return false;

            String payloadStr = new String(Base64.getDecoder().decode(parts[0]));
            byte[] signature = Base64.getDecoder().decode(parts[1]);

            String[] payloadParts = payloadStr.split(":");
            if (payloadParts.length != 3) return false;

            String version = payloadParts[0];
            String fingerprint = payloadParts[1];
            long expiry = Long.parseLong(payloadParts[2]);

            if (System.currentTimeMillis() > expiry) return false;
            if (!version.equals(expectedVersion) || !fingerprint.equals(expectedFingerprint)) return false;

            Signature ecdsa = Signature.getInstance("SHA256withECDSA");
            ecdsa.initVerify(verificationKey);
            ecdsa.update(payloadStr.getBytes());
            return ecdsa.verify(signature);
        } catch (Exception e) {
            return false;
        }
    }
}
