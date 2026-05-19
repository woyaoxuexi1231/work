package com.example.rsa.controller;

import com.example.rsa.dto.DecryptRequest;
import com.example.rsa.dto.KeyResponse;
import com.example.rsa.model.KeyVersion;
import com.example.rsa.service.CryptoService;
import com.example.rsa.service.KeyManager;
import com.example.rsa.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*") // For demo simplicity
public class RsaController {

    private final KeyManager keyManager;
    private final TokenService tokenService;
    private final CryptoService cryptoService;

    @GetMapping("/key/latest")
    public ResponseEntity<KeyResponse> getLatestKey() throws Exception {
        KeyVersion kv = keyManager.getLatestKey();
        String token = tokenService.generateToken(kv.getVersion(), kv.getFingerprint());
        String pem = keyManager.getPublicKeyPem(kv.getPublicKey());
        return ResponseEntity.ok(new KeyResponse(kv.getVersion(), pem, token));
    }

    @PostMapping("/decrypt")
    public ResponseEntity<?> decrypt(@RequestBody DecryptRequest request) {
        try {
            // 1. Get key version
            KeyVersion kv = keyManager.getKey(request.getVersion());
            if (kv == null) {
                return ResponseEntity.badRequest().body("Invalid key version");
            }

            // 2. Check status (Defense 2.2)
            if (kv.getStatus() == KeyVersion.Status.REVOKED) {
                return ResponseEntity.status(403).body("Key version revoked");
            }

            // 3. Verify token (Defense 2.1)
            if (!tokenService.verifyToken(request.getToken(), request.getVersion(), kv.getFingerprint())) {
                return ResponseEntity.status(401).body("Invalid or expired token");
            }

            // 4. Decrypt (includes Defense 2.3 version locking)
            String plaintext = cryptoService.decrypt(request, kv);
            
            return ResponseEntity.ok(plaintext);
        } catch (SecurityException e) {
            log.error("Security violation: {}", e.getMessage());
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (Exception e) {
            log.error("Decryption failed", e);
            return ResponseEntity.badRequest().body("Decryption failed: " + e.getMessage());
        }
    }
}
