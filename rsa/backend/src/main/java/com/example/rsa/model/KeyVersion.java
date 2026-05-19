package com.example.rsa.model;

import lombok.Builder;
import lombok.Data;
import java.security.PrivateKey;
import java.security.PublicKey;

@Data
@Builder
public class KeyVersion {
    private String version;
    private PublicKey publicKey;
    private PrivateKey privateKey;
    private String fingerprint;
    private Status status;

    public enum Status {
        ACTIVE, DEPRECATED, REVOKED
    }
}
